package com.videotopdf.service;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfService {

    private static final float MARGIN = 60;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    private static final PDFont FONT_BOLD =
        new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont FONT_REGULAR =
        new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont FONT_OBLIQUE =
        new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    public byte[] generatePdf(String title, String channelTitle,
            String publishedAt, String thumbnailUrl,
            String transcript, String sourceLang) throws Exception {

        try (PDDocument doc = new PDDocument()) {

            // ── COVER PAGE ──────────────────────────────────────────
            PDPage coverPage = new PDPage(PDRectangle.A4);
            doc.addPage(coverPage);

            try (PDPageContentStream cs =
                    new PDPageContentStream(doc, coverPage)) {

                // White background
                cs.setNonStrokingColor(1f, 1f, 1f);
                cs.addRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
                cs.fill();

                // Navy top bar
                cs.setNonStrokingColor(0.05f, 0.1f, 0.3f);
                cs.addRect(0, PAGE_HEIGHT - 100, PAGE_WIDTH, 100);
                cs.fill();

                // Gold line under navy bar
                cs.setNonStrokingColor(0.83f, 0.68f, 0.21f);
                cs.addRect(0, PAGE_HEIGHT - 103, PAGE_WIDTH, 3);
                cs.fill();

                // App name in white on navy
                drawCenteredText(cs, "VideoToPdf",
                        FONT_BOLD, 32,
                        1f, 1f, 1f,
                        PAGE_HEIGHT - 55);

                // Tagline
                drawCenteredText(cs,
                        "YouTube Video Transcript",
                        FONT_REGULAR, 13,
                        0.83f, 0.68f, 0.21f,
                        PAGE_HEIGHT - 78);

                // Thumbnail
                float thumbY = PAGE_HEIGHT - 310;
                try {
                    if (thumbnailUrl != null
                            && !thumbnailUrl.isEmpty()) {
                        byte[] imgBytes =
                            downloadImage(thumbnailUrl);
                        PDImageXObject img =
                            PDImageXObject.createFromByteArray(
                                doc, imgBytes, "thumb");
                        float imgW = 350, imgH = 197;
                        float imgX = (PAGE_WIDTH - imgW) / 2;

                        // Light shadow box
                        cs.setNonStrokingColor(
                            0.85f, 0.85f, 0.85f);
                        cs.addRect(imgX + 4, thumbY - 4,
                            imgW, imgH);
                        cs.fill();

                        cs.drawImage(img, imgX, thumbY,
                            imgW, imgH);

                        // Navy border
                        cs.setStrokingColor(
                            0.05f, 0.1f, 0.3f);
                        cs.setLineWidth(2f);
                        cs.addRect(imgX, thumbY, imgW, imgH);
                        cs.stroke();
                    }
                } catch (Exception e) {
                    // skip thumbnail
                }

                // Divider line
                float divY = thumbY - 30;
                cs.setStrokingColor(0.83f, 0.68f, 0.21f);
                cs.setLineWidth(1.5f);
                cs.moveTo(MARGIN, divY);
                cs.lineTo(PAGE_WIDTH - MARGIN, divY);
                cs.stroke();

                // Video title - black text
                float titleY = divY - 30;
                List<String> titleLines = wrapText(
                    title, FONT_BOLD, 16, CONTENT_WIDTH);
                for (String line : titleLines) {
                    drawCenteredText(cs, line,
                            FONT_BOLD, 16,
                            0.05f, 0.1f, 0.3f, titleY);
                    titleY -= 24;
                }

                // Channel - dark grey
                drawCenteredText(cs,
                        "Channel:  " + channelTitle,
                        FONT_REGULAR, 12,
                        0.3f, 0.3f, 0.3f,
                        titleY - 12);

                // Date
                String date = publishedAt.length() >= 10
                    ? publishedAt.substring(0, 10)
                    : publishedAt;
                drawCenteredText(cs,
                        "Published:  " + date,
                        FONT_REGULAR, 11,
                        0.5f, 0.5f, 0.5f,
                        titleY - 32);

                // Language
                if (!sourceLang.equalsIgnoreCase("English")
                        && !sourceLang.equalsIgnoreCase("en")) {
                    drawCenteredText(cs,
                        "Translated from: " + sourceLang
                        + "  to  English",
                        FONT_OBLIQUE, 10,
                        0.4f, 0.4f, 0.7f,
                        titleY - 50);
                }

                // Navy footer bar
                cs.setNonStrokingColor(0.05f, 0.1f, 0.3f);
                cs.addRect(0, 0, PAGE_WIDTH, 40);
                cs.fill();

                drawCenteredText(cs,
                        "Generated by VideoToPdf  |  "
                        + java.time.LocalDate.now(),
                        FONT_REGULAR, 9,
                        0.83f, 0.68f, 0.21f, 14);
            }

            // ── TRANSCRIPT PAGES ─────────────────────────────────────
            // Clean transcript — remove timestamps
            String cleanTranscript = removeTimestamps(transcript);

            // Split into paragraphs for better readability
            List<String> paragraphs =
                buildParagraphs(cleanTranscript);

            float lineHeight = 18f;
            float paraSpacing = 10f;
            float headerHeight = 55f;
            float footerHeight = 35f;

            PDPage page = null;
            PDPageContentStream cs = null;
            float y = 0;
            int pageNum = 1;

            for (String para : paragraphs) {
                List<String> wrapped = wrapText(
                    para, FONT_REGULAR, 11, CONTENT_WIDTH);

                // Check if this paragraph fits
                float neededHeight = wrapped.size() * lineHeight
                    + paraSpacing;

                if (page == null
                        || y - neededHeight
                           < MARGIN + footerHeight) {
                    if (cs != null) {
                        drawTranscriptFooter(cs, pageNum);
                        cs.close();
                        pageNum++;
                    }
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);

                    // White background
                    cs.setNonStrokingColor(1f, 1f, 1f);
                    cs.addRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
                    cs.fill();

                    drawTranscriptHeader(cs, title, pageNum);
                    y = PAGE_HEIGHT - MARGIN - headerHeight;
                }

                // Draw each wrapped line
                for (String wline : wrapped) {
                    cs.beginText();
                    cs.setFont(FONT_REGULAR, 11);
                    // BLACK text
                    cs.setNonStrokingColor(0f, 0f, 0f);
                    cs.newLineAtOffset(MARGIN, y);
                    cs.showText(sanitize(wline));
                    cs.endText();
                    y -= lineHeight;
                }
                y -= paraSpacing;
            }

            if (cs != null) {
                drawTranscriptFooter(cs, pageNum);
                cs.close();
            }

            ByteArrayOutputStream baos =
                new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    // Remove all [00:00] timestamps from transcript
    private String removeTimestamps(String transcript) {
        if (transcript == null) return "";
        // Remove patterns like [00:00], [00:00:00]
        return transcript
            .replaceAll("\\[\\d{2}:\\d{2}:\\d{2}\\]\\s*", "")
            .replaceAll("\\[\\d{2}:\\d{2}\\]\\s*", "")
            .trim();
    }

    // Group lines into readable paragraphs
    private List<String> buildParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder current = new StringBuilder();
        int lineCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            current.append(trimmed).append(" ");
            lineCount++;

            // Every 4 lines make a paragraph
            if (lineCount >= 4) {
                paragraphs.add(current.toString().trim());
                current = new StringBuilder();
                lineCount = 0;
            }
        }

        if (current.length() > 0) {
            paragraphs.add(current.toString().trim());
        }

        return paragraphs;
    }

    private void drawTranscriptHeader(PDPageContentStream cs,
            String title, int pageNum) throws Exception {

        // Navy header bar
        cs.setNonStrokingColor(0.05f, 0.1f, 0.3f);
        cs.addRect(0, PAGE_HEIGHT - 50, PAGE_WIDTH, 50);
        cs.fill();

        // Gold accent line
        cs.setNonStrokingColor(0.83f, 0.68f, 0.21f);
        cs.addRect(0, PAGE_HEIGHT - 53, PAGE_WIDTH, 3);
        cs.fill();

        // App name
        cs.beginText();
        cs.setFont(FONT_BOLD, 12);
        cs.setNonStrokingColor(0.83f, 0.68f, 0.21f);
        cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 32);
        cs.showText("VideoToPdf");
        cs.endText();

        // Short title
        String shortTitle = title.length() > 55
            ? title.substring(0, 52) + "..." : title;
        cs.beginText();
        cs.setFont(FONT_REGULAR, 9);
        cs.setNonStrokingColor(0.8f, 0.8f, 0.8f);
        cs.newLineAtOffset(MARGIN + 100, PAGE_HEIGHT - 32);
        cs.showText(sanitize(shortTitle));
        cs.endText();

        // Transcript label
        cs.beginText();
        cs.setFont(FONT_BOLD, 11);
        cs.setNonStrokingColor(0.05f, 0.1f, 0.3f);
        cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 75);
        cs.showText("TRANSCRIPT");
        cs.endText();

        // Gold underline for TRANSCRIPT
        cs.setStrokingColor(0.83f, 0.68f, 0.21f);
        cs.setLineWidth(1f);
        cs.moveTo(MARGIN, PAGE_HEIGHT - 78);
        cs.lineTo(MARGIN + 90, PAGE_HEIGHT - 78);
        cs.stroke();
    }

    private void drawTranscriptFooter(PDPageContentStream cs,
            int pageNum) throws Exception {

        // Light separator line
        cs.setStrokingColor(0.8f, 0.8f, 0.8f);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, 38);
        cs.lineTo(PAGE_WIDTH - MARGIN, 38);
        cs.stroke();

        // Page number - black
        cs.beginText();
        cs.setFont(FONT_REGULAR, 9);
        cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
        cs.newLineAtOffset(MARGIN, 20);
        cs.showText("VideoToPdf  |  Page " + pageNum);
        cs.endText();

        // Right side
        cs.beginText();
        cs.setFont(FONT_REGULAR, 9);
        cs.setNonStrokingColor(0.6f, 0.6f, 0.6f);
        cs.newLineAtOffset(PAGE_WIDTH - MARGIN - 80, 20);
        cs.showText("Transcript PDF");
        cs.endText();
    }

    private void drawCenteredText(PDPageContentStream cs,
            String text, PDFont font, float fontSize,
            float r, float g, float b, float y) throws Exception {
        String safe = sanitize(text);
        float textWidth = font.getStringWidth(safe) / 1000 * fontSize;
        float x = (PAGE_WIDTH - textWidth) / 2;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.setNonStrokingColor(r, g, b);
        cs.newLineAtOffset(x, y);
        cs.showText(safe);
        cs.endText();
    }

    private List<String> wrapText(String text, PDFont font,
            float fontSize, float maxWidth) throws Exception {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String test = current.length() == 0
                ? word : current + " " + word;
            float width = font.getStringWidth(sanitize(test))
                / 1000 * fontSize;
            if (width > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(test);
            }
        }
        if (current.length() > 0)
            lines.add(current.toString());
        return lines;
    }

    private byte[] downloadImage(String imageUrl) throws Exception {
        URL url = new URL(imageUrl);
        try (InputStream in = url.openStream();
             ByteArrayOutputStream baos =
                 new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1)
                baos.write(buf, 0, n);
            return baos.toByteArray();
        }
    }

    private String sanitize(String text) {
        if (text == null) return "";
        return text.replaceAll("[^\\x20-\\x7E]", " ").trim();
    }
}
