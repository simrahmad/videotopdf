package com.videotopdf.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendPdfEmail(String toEmail, String videoTitle,
            byte[] pdfBytes) throws MessagingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(toEmail);
        helper.setSubject("📄 Your PDF is Ready — " + videoTitle);

        String html = buildEmailHtml(videoTitle);
        helper.setText(html, true);

        // Attach PDF
        String fileName = videoTitle
                .replaceAll("[^a-zA-Z0-9\\s]", "")
                .replaceAll("\\s+", "_")
                .substring(0, Math.min(50,
                        videoTitle.replaceAll("[^a-zA-Z0-9\\s]", "")
                                  .replaceAll("\\s+", "_").length()))
                + ".pdf";

        helper.addAttachment(fileName,
                new ByteArrayResource(pdfBytes),
                "application/pdf");

        mailSender.send(message);
    }

    private String buildEmailHtml(String videoTitle) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <style>
                body { margin:0; padding:0;
                       font-family: Arial, sans-serif;
                       background:#f4f4f4; }
                .container { max-width:600px; margin:30px auto;
                             background:#fff;
                             border-radius:8px;
                             overflow:hidden;
                             box-shadow:0 2px 10px rgba(0,0,0,0.1); }
                .header { background:#0d1b4b; padding:30px;
                          text-align:center; }
                .header h1 { color:#d4af37; margin:0;
                             font-size:28px; }
                .header p { color:#aaa; margin:5px 0 0; font-size:13px; }
                .body { padding:30px; }
                .body h2 { color:#0d1b4b; font-size:18px; }
                .body p { color:#444; line-height:1.6; }
                .highlight { background:#f9f3dc;
                             border-left:4px solid #d4af37;
                             padding:12px 16px;
                             border-radius:4px;
                             margin:20px 0; }
                .highlight p { margin:0; color:#333;
                               font-weight:bold; }
                .footer { background:#0d1b4b; padding:16px;
                          text-align:center; }
                .footer p { color:#666; margin:0; font-size:12px; }
                .badge { display:inline-block;
                         background:#d4af37;
                         color:#0d1b4b;
                         padding:4px 10px;
                         border-radius:12px;
                         font-size:12px;
                         font-weight:bold; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>VideoToPdf</h1>
                  <p>YouTube Video Transcript Converter</p>
                </div>
                <div class="body">
                  <h2>Your PDF is Ready! 🎉</h2>
                  <p>Hello,</p>
                  <p>Your YouTube video has been successfully converted
                     to a PDF transcript. Please find your document
                     attached to this email.</p>
                  <div class="highlight">
                    <p>📹 """ + videoTitle + """
                    </p>
                  </div>
                  <p>The transcript has been:</p>
                  <p>
                    ✅ Extracted from YouTube<br>
                    ✅ Translated to English (if needed)<br>
                    ✅ Formatted into a professional PDF<br>
                    ✅ Includes timestamps for easy reference
                  </p>
                  <p style="color:#888; font-size:12px;">
                    This email was generated automatically by VideoToPdf.
                    Please do not reply to this email.
                  </p>
                </div>
                <div class="footer">
                  <p>© 2024 VideoToPdf &nbsp;|&nbsp;
                     <span class="badge">Free Service</span>
                  </p>
                </div>
              </div>
            </body>
            </html>
            """;
    }
}
