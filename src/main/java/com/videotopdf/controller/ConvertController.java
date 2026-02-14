package com.videotopdf.controller;

import com.videotopdf.model.User;
import com.videotopdf.model.UserRepository;
import com.videotopdf.service.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.Principal;
import java.util.concurrent.TimeUnit;

@Controller
public class ConvertController {

    @Autowired private YouTubeService youTubeService;
    @Autowired private TranslationService translationService;
    @Autowired private PdfService pdfService;
    @Autowired private EmailService emailService;
    @Autowired private UserRepository userRepository;

    private static final String PYTHON =
        "/usr/local/bin/python3.10";
    private static final String TRANSCRIBE =
        System.getProperty("user.home")
        + "/VideoToPdf/transcribe.py";

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        if (principal != null) {
            userRepository.findByUsername(principal.getName())
                .ifPresent(u -> {
                    model.addAttribute("username", u.getUsername());
                    model.addAttribute("email", u.getEmail());
                });
        }
        return "dashboard";
    }

    @PostMapping("/convert/youtube")
    public String convertYoutube(
            @RequestParam String youtubeUrl,
            @RequestParam String recipientEmail,
            Principal principal, Model model) {

        model.addAttribute("username",
            principal != null ? principal.getName() : "User");

        try {
            String videoId =
                youTubeService.extractVideoId(youtubeUrl);
            if (videoId == null || videoId.isEmpty()) {
                model.addAttribute("error",
                    "Invalid YouTube URL. Please check and try again.");
                return "dashboard";
            }

            JSONObject details =
                youTubeService.getVideoDetails(videoId);
            String title       = details.getString("title");
            String channel     = details.getString("channelTitle");
            String thumbnail   = details.getString("thumbnailUrl");
            String language    = details.optString("language", "en");
            String publishedAt = details.getString("publishedAt");

            String transcript =
                youTubeService.getTranscript(videoId);
            if (transcript == null || transcript.isBlank()) {
                model.addAttribute("error",
                    "Could not extract subtitles. "
                    + "Try a video with captions enabled.");
                return "dashboard";
            }

            String langName =
                translationService.detectLanguage(language);
            String finalText = language.startsWith("en")
                ? transcript
                : translationService.translateToEnglish(
                    transcript, language);

            byte[] pdf = pdfService.generatePdf(
                title, channel, publishedAt,
                thumbnail, finalText, langName);

            emailService.sendPdfEmail(
                recipientEmail, title, pdf);

            model.addAttribute("success",
                "✅ PDF sent to " + recipientEmail
                + " | " + title);

        } catch (Exception e) {
            model.addAttribute("error",
                "Error: " + e.getMessage());
        }
        return "dashboard";
    }

    @PostMapping("/convert/file")
    public String convertFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam String recipientEmail,
            Principal principal, Model model) {

        model.addAttribute("username",
            principal != null ? principal.getName() : "User");

        Path tempFile = null;
        try {
            if (file.isEmpty()) {
                model.addAttribute("error", "Please select a file.");
                return "dashboard";
            }

            String origName = file.getOriginalFilename();
            String ext = (origName != null && origName.contains("."))
                ? origName.substring(
                    origName.lastIndexOf('.') + 1).toLowerCase()
                : "";

            if (!ext.equals("mp4") && !ext.equals("mp3")) {
                model.addAttribute("error",
                    "Only MP4 and MP3 files are supported.");
                return "dashboard";
            }

            // Save uploaded file temporarily
            tempFile = Files.createTempFile("vtp_upload_", "." + ext);
            file.transferTo(tempFile.toFile());

            long sizeMB = tempFile.toFile().length() / 1024 / 1024;
            System.out.println("File saved: " + tempFile
                + " (" + sizeMB + " MB)");

            // Run Python transcription
            System.out.println("Running transcription...");
            String transcript = runScript(tempFile.toString());

            if (transcript == null || transcript.isBlank()) {
                model.addAttribute("error",
                    "Could not detect speech in your file. "
                    + "Make sure it has clear spoken audio in English "
                    + "and try again.");
                return "dashboard";
            }

            System.out.println("Transcript: "
                + transcript.length() + " chars");

            // Translate to English if needed
            String finalText =
                translationService.translateToEnglish(
                    transcript, "auto");

            // Generate PDF
            String title = origName.substring(
                0, origName.lastIndexOf('.'));

            byte[] pdf = pdfService.generatePdf(
                title, "Uploaded File",
                java.time.LocalDate.now() + "T00:00:00Z",
                "", finalText, "Audio/Video");

            emailService.sendPdfEmail(recipientEmail, title, pdf);

            model.addAttribute("success",
                "✅ PDF sent to " + recipientEmail
                + " | File: " + origName);

        } catch (Exception e) {
            System.err.println("Upload error: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error",
                "Error: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); }
                catch (Exception ignored) {}
            }
        }
        return "dashboard";
    }

    private String runScript(String filePath) throws Exception {
        System.out.println("Calling: " + PYTHON
            + " " + TRANSCRIBE + " " + filePath);

        ProcessBuilder pb = new ProcessBuilder(
            PYTHON, TRANSCRIBE, filePath);
        pb.redirectErrorStream(false);
        Process proc = pb.start();

        // Print Python logs to console
        Thread errThread = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(
                        proc.getErrorStream()))) {
                String line;
                while ((line = r.readLine()) != null)
                    System.out.println("[py] " + line);
            } catch (Exception ignored) {}
        });
        errThread.setDaemon(true);
        errThread.start();

        // Read transcript from stdout
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null)
                out.append(line).append(" ");
        }

        // Wait up to 10 minutes for large files
        boolean done = proc.waitFor(10, TimeUnit.MINUTES);
        if (!done) {
            proc.destroyForcibly();
            throw new Exception("Transcription timed out.");
        }

        System.out.println("Script exit code: "
            + proc.exitValue());
        return out.toString().trim();
    }
}
