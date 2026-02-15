package com.videotopdf.controller;

import com.videotopdf.model.User;
import com.videotopdf.model.UserRepository;
import com.videotopdf.service.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
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
    @Autowired private UserRepository userRepository;

    private static final String PYTHON =
        System.getenv("PYTHON_PATH") != null
        ? System.getenv("PYTHON_PATH")
        : "/usr/local/bin/python3.10";
    private static final String TRANSCRIBE =
        System.getenv("SCRIPTS_PATH") != null
        ? System.getenv("SCRIPTS_PATH") + "/transcribe.py"
        : System.getProperty("user.home")
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
    public ResponseEntity<byte[]> convertYoutube(
            @RequestParam String youtubeUrl) {

        try {
            String videoId = youTubeService.extractVideoId(youtubeUrl);
            if (videoId == null || videoId.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body("Invalid YouTube URL".getBytes());
            }

            JSONObject details = youTubeService.getVideoDetails(videoId);
            String title = details.getString("title");
            String channel = details.getString("channelTitle");
            String thumbnail = details.getString("thumbnailUrl");
            String language = details.optString("language", "en");
            String publishedAt = details.getString("publishedAt");

            String transcript = youTubeService.getTranscript(videoId);
            if (transcript == null || transcript.isBlank()) {
                return ResponseEntity.status(500)
                    .body("Could not extract captions. Try a video with captions enabled.".getBytes());
            }

            String langName = translationService.detectLanguage(language);
            String finalText = language.startsWith("en")
                ? transcript
                : translationService.translateToEnglish(
                    transcript, language);

            byte[] pdf = pdfService.generatePdf(
                title, channel, publishedAt,
                thumbnail, finalText, langName);

            // Create safe filename
            String filename = title
                .replaceAll("[^a-zA-Z0-9-_\\s]", "")
                .replaceAll("\\s+", "_");
            if (filename.length() > 50) {
                filename = filename.substring(0, 50);
            }
            filename += ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                ContentDisposition.attachment()
                    .filename(filename)
                    .build());

            System.out.println("✅ PDF generated: " + title);

            return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);

        } catch (Exception e) {
            System.err.println("YouTube conversion error: " + e.getMessage());
            e.printStackTrace();
            String errorMsg = "Error: " + e.getMessage();
            return ResponseEntity.status(500)
                .body(errorMsg.getBytes());
        }
    }

    @PostMapping("/convert/file")
    public ResponseEntity<byte[]> convertFile(
            @RequestParam("file") MultipartFile file) {

        Path tempFile = null;
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body("Please select a file".getBytes());
            }

            String origName = file.getOriginalFilename();
            String ext = (origName != null && origName.contains("."))
                ? origName.substring(
                    origName.lastIndexOf('.') + 1).toLowerCase()
                : "";

            if (!ext.equals("mp4") && !ext.equals("mp3")) {
                return ResponseEntity.badRequest()
                    .body("Only MP4 and MP3 files are supported".getBytes());
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
                return ResponseEntity.status(500)
                    .body("Could not detect speech. Make sure file has clear spoken audio.".getBytes());
            }

            System.out.println("Transcript: "
                + transcript.length() + " chars");

            // Translate to English if needed
            String finalText = translationService.translateToEnglish(
                transcript, "auto");

            // Generate PDF
            String title = origName.substring(
                0, origName.lastIndexOf('.'));

            byte[] pdf = pdfService.generatePdf(
                title, "Uploaded File",
                java.time.LocalDate.now() + "T00:00:00Z",
                "", finalText, "Audio/Video");

            // Create safe filename
            String filename = title
                .replaceAll("[^a-zA-Z0-9-_\\s]", "")
                .replaceAll("\\s+", "_");
            if (filename.length() > 50) {
                filename = filename.substring(0, 50);
            }
            filename += ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                ContentDisposition.attachment()
                    .filename(filename)
                    .build());

            System.out.println("✅ PDF generated: " + origName);

            return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);

        } catch (Exception e) {
            System.err.println("File conversion error: " + e.getMessage());
            e.printStackTrace();
            String errorMsg = "Error: " + e.getMessage();
            return ResponseEntity.status(500)
                .body(errorMsg.getBytes());
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); }
                catch (Exception ignored) {}
            }
        }
    }

    private String runScript(String filePath) throws Exception {
        System.out.println("Calling: " + PYTHON
            + " " + TRANSCRIBE + " " + filePath);

        ProcessBuilder pb = new ProcessBuilder(
            PYTHON, TRANSCRIBE, filePath);
        pb.redirectErrorStream(false);
        pb.environment().put("PATH",
            "/usr/bin:/usr/local/bin:/bin:"
            + System.getenv("PATH"));

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
