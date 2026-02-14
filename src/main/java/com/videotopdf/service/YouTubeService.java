package com.videotopdf.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

@Service
public class YouTubeService {

    @Value("${youtube.api.key}")
    private String apiKey;

    // Path to yt-dlp and python3.10
    private static final String YTDLP_PATH =
        "/usr/local/bin/yt-dlp";
    private static final String PYTHON_PATH =
        "/usr/local/bin/python3.10";

    public String extractVideoId(String youtubeUrl) {
        String videoId = null;
        if (youtubeUrl.contains("v=")) {
            videoId = youtubeUrl.split("v=")[1];
            if (videoId.contains("&"))
                videoId = videoId.split("&")[0];
        } else if (youtubeUrl.contains("youtu.be/")) {
            videoId = youtubeUrl.split("youtu.be/")[1];
            if (videoId.contains("?"))
                videoId = videoId.split("\\?")[0];
        } else if (youtubeUrl.contains("shorts/")) {
            videoId = youtubeUrl.split("shorts/")[1];
            if (videoId.contains("?"))
                videoId = videoId.split("\\?")[0];
        }
        return videoId != null ? videoId.trim() : null;
    }

    public JSONObject getVideoDetails(String videoId)
            throws Exception {
        String urlStr =
            "https://www.googleapis.com/youtube/v3/videos"
            + "?id=" + videoId
            + "&key=" + apiKey
            + "&part=snippet,contentDetails";

        String response = makeGetRequest(urlStr);
        JSONObject json = new JSONObject(response);
        JSONArray items = json.getJSONArray("items");

        if (items.length() == 0)
            throw new Exception(
                "Video not found or is private.");

        JSONObject snippet =
            items.getJSONObject(0).getJSONObject("snippet");
        JSONObject result = new JSONObject();
        result.put("title", snippet.getString("title"));
        result.put("channelTitle",
            snippet.getString("channelTitle"));
        result.put("description",
            snippet.optString("description", ""));
        result.put("thumbnailUrl",
            snippet.getJSONObject("thumbnails")
                   .getJSONObject("high")
                   .getString("url"));
        result.put("language",
            snippet.optString("defaultAudioLanguage",
                snippet.optString("defaultLanguage", "en")));
        result.put("publishedAt",
            snippet.getString("publishedAt"));
        return result;
    }

    public String getTranscript(String videoId) throws Exception {
        System.out.println("=== Fetching transcript: "
            + videoId + " ===");

        // Method 1: Auto-generated subtitles via yt-dlp
        String transcript = getSubtitlesViaYtDlp(
            videoId, true);
        if (isValid(transcript)) {
            System.out.println("Got auto-subtitles!");
            return transcript;
        }

        // Method 2: Manual subtitles via yt-dlp
        transcript = getSubtitlesViaYtDlp(videoId, false);
        if (isValid(transcript)) {
            System.out.println("Got manual subtitles!");
            return transcript;
        }

        // Method 3: Try all languages
        transcript = getSubtitlesAllLanguages(videoId);
        if (isValid(transcript)) {
            System.out.println("Got subtitles in other language!");
            return transcript;
        }

        // Method 4: Description fallback
        System.out.println("Using description fallback");
        return getDescriptionFallback(videoId);
    }

    private String getSubtitlesViaYtDlp(
            String videoId, boolean autoSub) {
        String outputPath = "/tmp/vtp_" + videoId
            + "_" + System.currentTimeMillis();
        try {
            String videoUrl =
                "https://www.youtube.com/watch?v=" + videoId;

            // Clean old files
            cleanFiles(outputPath);

            ProcessBuilder pb;
            if (autoSub) {
                pb = new ProcessBuilder(
                    PYTHON_PATH, YTDLP_PATH,
                    "--write-auto-sub",
                    "--skip-download",
                    "--sub-format", "vtt",
                    "--sub-langs", "en",
                    "--no-warnings",
                    "--quiet",
                    "-o", outputPath,
                    videoUrl
                );
            } else {
                pb = new ProcessBuilder(
                    PYTHON_PATH, YTDLP_PATH,
                    "--write-sub",
                    "--skip-download",
                    "--sub-format", "vtt",
                    "--sub-langs", "en",
                    "--no-warnings",
                    "--quiet",
                    "-o", outputPath,
                    videoUrl
                );
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println("yt-dlp: " + line);
            }
            process.waitFor(90, TimeUnit.SECONDS);

            // Look for subtitle files
            String result = findAndReadSubtitleFile(
                outputPath);
            if (result != null) return result;

        } catch (Exception e) {
            System.out.println("yt-dlp error: "
                + e.getMessage());
        } finally {
            cleanFiles(outputPath);
        }
        return null;
    }

    private String getSubtitlesAllLanguages(String videoId) {
        String outputPath = "/tmp/vtp_all_" + videoId
            + "_" + System.currentTimeMillis();
        try {
            String videoUrl =
                "https://www.youtube.com/watch?v=" + videoId;

            cleanFiles(outputPath);

            // Download subtitles in ANY available language
            ProcessBuilder pb = new ProcessBuilder(
                PYTHON_PATH, YTDLP_PATH,
                "--write-auto-sub",
                "--write-sub",
                "--skip-download",
                "--sub-format", "vtt",
                "--sub-langs", "all",
                "--no-warnings",
                "--quiet",
                "-o", outputPath,
                videoUrl
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    process.getInputStream()));
            while (reader.readLine() != null) {}
            process.waitFor(90, TimeUnit.SECONDS);

            String result = findAndReadSubtitleFile(
                outputPath);
            if (result != null) return result;

        } catch (Exception e) {
            System.out.println("All langs error: "
                + e.getMessage());
        } finally {
            cleanFiles(outputPath);
        }
        return null;
    }

    private String findAndReadSubtitleFile(String basePath) {
        // Look for any .vtt file with this base path
        File tmpDir = new File("/tmp");
        String baseName = new File(basePath).getName();

        File[] files = tmpDir.listFiles((dir, name) ->
            name.startsWith(baseName) && name.endsWith(".vtt"));

        if (files != null && files.length > 0) {
            for (File f : files) {
                try {
                    if (f.length() > 100) {
                        String content = new String(
                            Files.readAllBytes(f.toPath()),
                            StandardCharsets.UTF_8);
                        f.delete();
                        String parsed = parseVtt(content);
                        if (isValid(parsed)) return parsed;
                    }
                    f.delete();
                } catch (Exception e) {
                    // skip this file
                }
            }
        }

        // Also check exact paths
        String[] extensions = {
            ".en.vtt", ".en-orig.vtt", ".en-US.vtt",
            ".ar.vtt", ".fr.vtt", ".de.vtt", ".es.vtt",
            ".hi.vtt", ".ur.vtt", ".zh-Hans.vtt"
        };

        for (String ext : extensions) {
            File f = new File(basePath + ext);
            if (f.exists() && f.length() > 100) {
                try {
                    String content = new String(
                        Files.readAllBytes(f.toPath()),
                        StandardCharsets.UTF_8);
                    f.delete();
                    String parsed = parseVtt(content);
                    if (isValid(parsed)) return parsed;
                } catch (Exception e) {
                    f.delete();
                }
            }
        }
        return null;
    }

    private String parseVtt(String vtt) {
        if (vtt == null || vtt.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        String[] lines = vtt.split("\n");
        String prevLine = "";

        for (String line : lines) {
            line = line.trim();

            // Skip VTT metadata
            if (line.isEmpty()
                    || line.equals("WEBVTT")
                    || line.contains("-->")
                    || line.matches("^\\d+$")
                    || line.startsWith("Kind:")
                    || line.startsWith("Language:")
                    || line.startsWith("NOTE")
                    || line.startsWith("STYLE")) {
                continue;
            }

            // Remove HTML/VTT tags
            line = line
                .replaceAll("<[^>]+>", "")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&#39;", "'")
                .replaceAll("&quot;", "\"")
                .replaceAll("\\{[^}]+\\}", "")
                .trim();

            // Skip empty or duplicate
            if (!line.isEmpty() && !line.equals(prevLine)) {
                sb.append(line).append("\n");
                prevLine = line;
            }
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private void cleanFiles(String basePath) {
        try {
            File tmpDir = new File("/tmp");
            String baseName = new File(basePath).getName();
            File[] files = tmpDir.listFiles((dir, name) ->
                name.startsWith(baseName));
            if (files != null) {
                for (File f : files) f.delete();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private String getDescriptionFallback(String videoId)
            throws Exception {
        try {
            JSONObject details = getVideoDetails(videoId);
            String description =
                details.optString("description", "");
            String title = details.optString("title", "");

            StringBuilder sb = new StringBuilder();
            sb.append("VIDEO TITLE: ")
              .append(title).append("\n\n");
            sb.append("NOTE: This video does not have ")
              .append("subtitles available.\n");
            sb.append("The following is the ")
              .append("video description:\n\n");

            if (!description.isEmpty()) {
                for (String line : description.split("\n")) {
                    if (!line.trim().isEmpty()) {
                        sb.append(line.trim()).append("\n");
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "Subtitles not available for this video.";
        }
    }

    private boolean isValid(String transcript) {
        return transcript != null
            && !transcript.trim().isEmpty()
            && transcript.trim().length() > 30;
    }

    public String formatTimestamp(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d",
                hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    public String makeGetRequest(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn =
            (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        int code = conn.getResponseCode();
        if (code != 200) return "";

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                conn.getInputStream(),
                StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null)
            sb.append(line);
        reader.close();
        return sb.toString();
    }
}
