package com.agent.service;

import com.agent.model.VideoResult;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * EmailService — builds and sends the dark-themed HTML email digest.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${recipient.email:}")
    private String recipientEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ── Public Method ─────────────────────────────────────────────────────────

    /**
     * Sends the formatted HTML email containing the top AI YouTube videos.
     */
    public void sendEmail(List<VideoResult> videos) throws Exception {
        String to = (recipientEmail == null || recipientEmail.isBlank())
            ? senderEmail
            : recipientEmail;

        String subject = buildSubject(videos.size());
        String html    = buildEmailHtml(videos);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom("\"YouTube AI Agent 🤖\" <" + senderEmail + ">");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true); // true = HTML content

        mailSender.send(message);
        log.info("📧 Email sent to: {}", to);
    }

    // ── Private: Subject Line ─────────────────────────────────────────────────

    private String buildSubject(int count) {
        String today = ZonedDateTime.now(IST)
            .format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH));
        return "🤖 Top " + count + " AI YouTube Videos — " + today;
    }

    // ── Private: Full HTML Email ──────────────────────────────────────────────

    private String buildEmailHtml(List<VideoResult> videos) {
        String today = ZonedDateTime.now(IST)
            .format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH));

        StringBuilder cards = new StringBuilder();
        for (int i = 0; i < videos.size(); i++) {
            cards.append(buildVideoCard(videos.get(i), i + 1));
        }

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
          <title>YouTube AI Daily Digest</title>
        </head>
        <body style="margin:0;padding:0;background-color:#0f0f1a;">
          <table role="presentation" cellpadding="0" cellspacing="0" width="100%%"
            style="background-color:#0f0f1a;padding:32px 16px;">
            <tr><td align="center">
              <table role="presentation" cellpadding="0" cellspacing="0" width="680"
                style="background-color:#16162a;border-radius:20px;border:1px solid #2a2a4a;max-width:100%%;">
                <!-- Header -->
                <tr>
                  <td style="background:linear-gradient(135deg,#1e1b4b 0%%,#312e81 50%%,#1e1b4b 100%%);
                      padding:36px 40px;text-align:center;border-bottom:1px solid #3730a3;">
                    <p style="margin:0 0 6px;font-family:'Segoe UI',sans-serif;font-size:13px;
                       letter-spacing:3px;text-transform:uppercase;color:#818cf8;">
                      AI MONITORING AGENT
                    </p>
                    <h1 style="margin:0 0 10px;font-family:'Segoe UI',Arial,sans-serif;
                       font-size:28px;font-weight:800;color:#ffffff;">
                      🤖 YouTube AI Daily Digest
                    </h1>
                    <p style="margin:0;font-family:'Segoe UI',sans-serif;font-size:14px;color:#a5b4fc;">
                      %s
                    </p>
                    <p style="margin:12px 0 0;font-family:'Segoe UI',sans-serif;font-size:13px;color:#6366f1;">
                      Top %d AI videos from the last 48 hours &middot; Sorted by views &middot; India
                    </p>
                  </td>
                </tr>
                <!-- Video Cards -->
                <tr>
                  <td style="padding:8px 40px 24px;">
                    <table role="presentation" cellpadding="0" cellspacing="0" width="100%%">
                      %s
                    </table>
                  </td>
                </tr>
                <!-- Footer -->
                <tr>
                  <td style="border-top:1px solid #2a2a4a;padding:24px 40px;
                      text-align:center;background-color:#12122a;">
                    <p style="margin:0;font-family:'Segoe UI',sans-serif;font-size:12px;color:#4b5563;">
                      Automatically generated by the YouTube AI Monitoring Agent &middot; Runs daily at 6:00 AM IST
                    </p>
                    <p style="margin:8px 0 0;font-family:'Segoe UI',sans-serif;font-size:12px;color:#374151;">
                      Powered by YouTube Data API v3 &amp; GitHub Actions (Spring Boot)
                    </p>
                  </td>
                </tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
        """.formatted(today, videos.size(), cards.toString());
    }

    // ── Private: Single Video Card ────────────────────────────────────────────

    private String buildVideoCard(VideoResult v, int rank) {
        String safeTitle   = escapeHtml(v.getTitle());
        String safeChannel = escapeHtml(v.getChannelName());

        return """
          <tr>
            <td style="padding:18px 0;border-bottom:1px solid #2a2a3a;">
              <table role="presentation" cellpadding="0" cellspacing="0" width="100%%">
                <tr>
                  <!-- Rank Badge -->
                  <td width="46" valign="top" style="padding-right:16px;">
                    <div style="width:38px;height:38px;border-radius:50%%;
                        background:linear-gradient(135deg,#7c3aed,#4f46e5);
                        font-family:'Segoe UI',sans-serif;font-weight:700;font-size:15px;
                        color:#fff;text-align:center;line-height:38px;">#%d</div>
                  </td>
                  <!-- Thumbnail -->
                  <td width="160" valign="top" style="padding-right:20px;">
                    <a href="%s" target="_blank" style="display:block;">
                      <img src="%s" alt="%s" width="160"
                        style="border-radius:10px;display:block;border:2px solid #3a3a5c;"/>
                    </a>
                  </td>
                  <!-- Details -->
                  <td valign="top">
                    <a href="%s" target="_blank" style="font-family:'Segoe UI',Arial,sans-serif;
                        font-size:15px;font-weight:700;color:#a78bfa;text-decoration:none;
                        line-height:1.4;display:block;margin-bottom:8px;">%s</a>
                    <p style="margin:0 0 5px;font-family:'Segoe UI',sans-serif;font-size:13px;color:#94a3b8;">
                      📺 <strong style="color:#cbd5e1;">%s</strong>
                    </p>
                    <p style="margin:0 0 5px;font-family:'Segoe UI',sans-serif;font-size:13px;color:#94a3b8;">
                      👁️ <strong style="color:#34d399;">%s views</strong>
                    </p>
                    <p style="margin:0 0 5px;font-family:'Segoe UI',sans-serif;font-size:13px;color:#94a3b8;">
                      🕐 <strong style="color:#94a3b8;">%s</strong>
                    </p>
                    <p style="margin:0;font-family:'Segoe UI',sans-serif;font-size:13px;color:#94a3b8;">
                      📅 %s
                    </p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
        """.formatted(
            rank,
            v.getUrl(), v.getThumbnail(), safeTitle,
            v.getUrl(), safeTitle,
            safeChannel,
            v.getViewCountFormatted(),
            v.getDurationFormatted(),
            v.getPublishedAtFormatted()
        );
    }

    /** Escapes HTML special characters to prevent injection in titles/channel names. */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&",  "&amp;")
            .replace("<",  "&lt;")
            .replace(">",  "&gt;")
            .replace("\"", "&quot;")
            .replace("'",  "&#39;");
    }
}
