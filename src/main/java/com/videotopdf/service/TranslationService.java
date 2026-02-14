package com.videotopdf.service;

import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class TranslationService {

    private static final int CHUNK_SIZE = 400;

    public String translateToEnglish(String text, String sourceLang)
            throws Exception {
        if (sourceLang == null || sourceLang.startsWith("en")) {
            return text;
        }

        // Split into chunks to respect API limits
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();

        StringBuilder chunk = new StringBuilder();
        for (String line : lines) {
            if (chunk.length() + line.length() > CHUNK_SIZE) {
                result.append(translateChunk(chunk.toString(), sourceLang));
                result.append("\n");
                chunk = new StringBuilder();
            }
            chunk.append(line).append("\n");
        }

        if (chunk.length() > 0) {
            result.append(translateChunk(chunk.toString(), sourceLang));
        }

        return result.toString();
    }

    private String translateChunk(String text, String sourceLang)
            throws Exception {
        if (text.trim().isEmpty()) return text;

        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String langPair = sourceLang + "|en";
        String urlStr = "https://api.mymemory.translated.net/get"
                + "?q=" + encoded
                + "&langpair=" + langPair;

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();

        JSONObject json = new JSONObject(sb.toString());
        int responseStatus = json.optInt("responseStatus", 0);

        if (responseStatus == 200) {
            return json.getJSONObject("responseData")
                       .getString("translatedText");
        }

        return text; // Return original if translation fails
    }

    public String detectLanguage(String langCode) {
        if (langCode == null || langCode.isEmpty()) return "Unknown";
        return switch (langCode.substring(0, 2).toLowerCase()) {
            case "en" -> "English";
            case "ar" -> "Arabic";
            case "fr" -> "French";
            case "de" -> "German";
            case "es" -> "Spanish";
            case "zh" -> "Chinese";
            case "hi" -> "Hindi";
            case "ur" -> "Urdu";
            case "tr" -> "Turkish";
            case "ru" -> "Russian";
            case "pt" -> "Portuguese";
            case "ja" -> "Japanese";
            case "ko" -> "Korean";
            default -> langCode.toUpperCase();
        };
    }
}
