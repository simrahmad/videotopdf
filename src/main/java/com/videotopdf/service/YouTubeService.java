package com.videotopdf.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
public class YouTubeService {

    @Value("${youtube.api.key}")
    private String apiKey;

    private static final String PYTHON =
        System.getenv("PYTHON_PATH") != null
        ? System.getenv("PYTHON_PATH")
        : "/usr/bin/python3.10";

    private static final String TRANSCRIPT_SCRIPT =
        System.getenv("SCRIPTS_PATH") != null
        ? System.getenv("SCRIPTS_PATH") + "/get_transcript.py"
        : System.getProperty("user.home") + "/VideoToPdf/get_transcript.py";

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
            throw new Exception("Video not found or is private.");

        JSONObject snippet =
            items.getJSONObject(0).getJSONObject("snippet");
        JSONObject result = new JSONObject();
        result.put("title", snippet.getString("title"));
        result.put("channelTitle", snippet.getString("channelTitle"));
        result.put("description", snippet.optString("description", ""));
        result.put("thumbnailUrl",
            snippet.getJSONObject("thumbnails")
                   .getJSONObject("high")
                   .getString("url"));
        result.put("language",
            snippet.optString("defaultAudioLanguage",
                snippet.optString("defaultLanguage", "en")));
        result.put("publishedAt", snippet.getString("publishedAt"));
        return result;
    }

    public String getTranscript(String videoId) throws Exception {
        System.out.println("=== Fetching transcript: " + videoId + " ===");

        String transcript = getTranscriptViaPython(videoId);
        if (isValid(transcript)) {
            System.out.println("Got transcript via python API!");
            return transcript;
        }

        System.out.println("Using description fallback");
        return getDescriptionFallback(videoId);
    }

    private String getTranscriptViaPython(String videoId) {
        try {
            System.out.println("Python path: " + PYTHON);
            System.out.println("Script path: " + TRANSCRIPT_SCRIPT);

            ProcessBuilder pb = new ProcessBuilder(
                PYTHON, TRANSCRIPT_SCRIPT, videoId);
            pb.redirectErrorStream(false);
            pb.environment().put("PATH",
                "/usr/bin:/usr/local/bin:/bin:"
                + System.getenv("PATH"));

            Process proc = pb.start();

            Thread errThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(proc.getErrorStream()))) {
                    String line;
                    while ((line = r.readLine()) != null)
                        System.out.println("[transcript] " + line);
                } catch (Exception ignored) {}
            });
            errThread.setDaemon(true);
            errThread.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(
                        proc.getInputStream(),
                        StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null)
                    out.append(line).append(" ");
            }

            proc.waitFor(60, TimeUnit.SECONDS);

            String result = out.toString().trim();
            if (result.equals("NO_TRANSCRIPT_AVAILABLE")
                    || result.isEmpty()) {
                return null;
            }
            return result;

        } catch (Exception e) {
            System.out.println("Python transcript error: " + e.getMessage());
            return null;
        }
    }

    private String getDescriptionFallback(String videoId) {
        try {
            JSONObject details = getVideoDetails(videoId);
            String description = details.optString("description", "");
            String title = details.optString("title", "");

            StringBuilder sb = new StringBuilder();
            sb.append("VIDEO TITLE: ").append(title).append("\n\n");
            sb.append("NOTE: This video does not have captions available.\n");
            sb.append("The following is the video description:\n\n");

            if (!description.isEmpty()) {
                for (String line : description.split("\n")) {
                    if (!line.trim().isEmpty())
                        sb.append(line.trim()).append("\n");
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

    public String makeGetRequest(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn =
            (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
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
