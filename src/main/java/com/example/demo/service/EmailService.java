package com.example.demo.service;

import java.io.UnsupportedEncodingException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final ResourceLoader resourceLoader;

    @Value("${spring.mail.username:noreply@primearena.com}")
    private String fromEmail;

    // Friendly display name and reply-to can be configured
    @Value("${app.mail.from.name:PrimeArena}")
    private String fromName;

    @Value("${app.mail.replyTo:}")
    private String replyTo;

    // Optional logo to embed in email (classpath:/ or file:/ or http(s)://)
    @Value("${app.mail.logo.path:}")
    private String logoPath;

    // Comma-separated list of admin emails to notify on important events
    @Value("${app.admin.emails:}")
    private String adminEmails;

    /**
     * Send OTP email for withdrawal verification
     */
    @Async
    public void sendWithdrawalOtp(String toEmail, String otpCode, String userName, String amount) {
        log.info("Attempting to send OTP email to: {} with fromEmail: {}", toEmail, fromEmail);

        try {
            MimeMessage mime = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(toEmail);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setSubject("PrimeArena - Withdrawal OTP Verification");

            String displayName = (userName != null && !userName.isBlank()) ? userName : "User";
            String logoCid = maybeAttachLogo(helper);
            String otpHtml = String.format(""
                    + "<div style='font-family:Inter,Segoe UI,Arial,sans-serif;color:#1f2937'>"
                    + "  <div style='text-align:center;margin-bottom:18px'>%s</div>"
                    + "  <h2 style='margin:0 0 12px;font-size:20px'>Withdrawal OTP Verification</h2>"
                    + "  <p style='margin:6px 0'>Dear %s,</p>"
                    + "  <p style='margin:6px 0'>You requested to withdraw <strong>₹%s</strong> from your PrimeArena wallet.</p>"
                    + "  <p style='margin:10px 0'>Your OTP is:</p>"
                    + "  <div style='font-size:28px;font-weight:800;letter-spacing:4px;background:#111827;color:#f5c518;padding:10px 14px;border-radius:10px;display:inline-block'>%s</div>"
                    + "  <p style='margin:14px 0;color:#6b7280;font-size:13px'>Valid for 5 minutes. Do not share this code with anyone.</p>"
                    + "  <hr style='border:none;border-top:1px solid #e5e7eb;margin:18px 0'>"
                    + "  <p style='margin:4px 0;font-size:13px;color:#6b7280'>If you didn’t request this, contact support immediately.</p>"
                    + "  <p style='margin:18px 0 0;font-size:13px;color:#6b7280'>Thank you for using PrimeArena!</p>"
                    + "  <p style='margin:2px 0 0;font-size:13px;color:#6b7280'>Best regards,</p>"
                    + "  <p style='margin:0;font-size:13px;color:#6b7280'>PrimeArena Team</p>"
                    + "</div>",
                    logoCid != null ? "<img alt='PrimeArena' src='cid:" + logoCid + "' style='height:40px'/>" : "",
                    escape(displayName),
                    escape(String.valueOf(amount)),
                    escape(otpCode)
            );

            helper.setText(otpHtml, true);
            javaMailSender.send(mime);
            log.info("Withdrawal OTP email sent successfully to: {}", toEmail);

        } catch (org.springframework.mail.MailAuthenticationException e) {
            log.error("Email authentication failed. Check your email username and app password. Error: {}", e.getMessage());
            throw new RuntimeException("Email authentication failed. Please check email configuration.");
        } catch (org.springframework.mail.MailSendException e) {
            log.error("Failed to send email due to SMTP error: {}", e.getMessage());
            throw new RuntimeException("Failed to send email. SMTP error occurred.");
        } catch (UnsupportedEncodingException e) {
            log.error("Unsupported encoding when building from address: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send OTP email: Unsupported encoding.");
        } catch (MessagingException e) {
            log.error("Failed to send withdrawal OTP email to: {} - Error: {}", toEmail, e.getMessage(), e);
            log.error("Exception type: {}", e.getClass().getSimpleName());
            throw new RuntimeException("Failed to send OTP email: " + e.getMessage());
        }
    }

    /**
     * Send withdrawal rejection notification with optional admin notes
     */
    @Async
    public void sendWithdrawalRejectedNotification(String toEmail, String userName, String amount,
            String withdrawalMethod, String reason) {
        try {
            MimeMessage mime = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(toEmail);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setSubject("PrimeArena - Withdrawal Request Rejected");

            String displayName = (userName != null && !userName.isBlank()) ? userName : "User";
            String logoCid = maybeAttachLogo(helper);
            String html = String.format(""
                    + "<div style='font-family:Inter,Segoe UI,Arial,sans-serif;color:#1f2937'>"
                    + "  <div style='text-align:center;margin-bottom:18px'>%s</div>"
                    + "  <h2 style='margin:0 0 10px;font-size:20px'>Withdrawal Request Rejected</h2>"
                    + "  <p style='margin:6px 0'>Dear %s,</p>"
                    + "  <p style='margin:6px 0'>Unfortunately, your withdrawal request was rejected. The withdrawn amount has been refunded to your wallet.</p>"
                    + "  <div style='margin:12px 0;padding:12px;border:1px solid #e5e7eb;border-radius:10px;background:#0f172a;color:#e5e7eb'>"
                    + "    <div><strong>Amount:</strong> ₹%s</div>"
                    + "    <div><strong>Method:</strong> %s</div>"
                    + "    <div><strong>Date:</strong> %s</div>"
                    + "  </div>"
                    + "  %s"
                    + "  <p style='margin:14px 0 0;font-size:13px;color:#6b7280'>You can initiate a new withdrawal at any time.</p>"
                    + "  <p style='margin:2px 0 0;font-size:13px;color:#6b7280'>Best regards,</p>"
                    + "  <p style='margin:0;font-size:13px;color:#6b7280'>PrimeArena Team</p>"
                    + "</div>",
                    logoCid != null ? "<img alt='PrimeArena' src='cid:" + logoCid + "' style='height:40px'/>" : "",
                    escape(displayName),
                    escape(String.valueOf(amount)),
                    escape(String.valueOf(withdrawalMethod)),
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")),
                    (reason != null && !reason.isBlank())
                    ? ("<div style='margin:8px 0;padding:10px;border-left:4px solid #ef4444;background:#fee2e2;color:#991b1b'>"
                    + "<strong>Reason:</strong> " + escape(reason) + "</div>")
                    : "");

            helper.setText(html, true);
            javaMailSender.send(mime);
            log.info("Withdrawal rejection notification sent to: {}", toEmail);

        } catch (UnsupportedEncodingException e) {
            log.error("Unsupported encoding when building from address: {}", e.getMessage(), e);
        } catch (MessagingException e) {
            log.error("Failed to send withdrawal rejection notification to: {}", toEmail, e);
        }
    }

    /**
     * Send withdrawal success notification
     */
    @Async
    public void sendWithdrawalSuccessNotification(String toEmail, String userName, String amount,
            String withdrawalMethod, String referenceId) {
        try {
            MimeMessage mime = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(toEmail);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setSubject("PrimeArena - Withdrawal Request Successful");

            String displayName = (userName != null && !userName.isBlank()) ? userName : "User";
            String logoCid = maybeAttachLogo(helper);
            String html = String.format(""
                    + "<div style='font-family:Inter,Segoe UI,Arial,sans-serif;color:#1f2937'>"
                    + "  <div style='text-align:center;margin-bottom:18px'>%s</div>"
                    + "  <h2 style='margin:0 0 10px;font-size:20px'>Withdrawal Request Successful</h2>"
                    + "  <p style='margin:6px 0'>Dear %s,</p>"
                    + "  <p style='margin:6px 0'>Your withdrawal request has been processed successfully.</p>"
                    + "  <div style='margin:12px 0;padding:12px;border:1px solid #e5e7eb;border-radius:10px;background:#0f172a;color:#e5e7eb'>"
                    + "    <div><strong>Amount:</strong> ₹%s</div>"
                    + "    <div><strong>Method:</strong> %s</div>"
                    + "    <div><strong>Reference ID:</strong> %s</div>"
                    + "    <div><strong>Date:</strong> %s</div>"
                    + "  </div>"
                    + "  <p style='margin:6px 0;color:#6b7280;font-size:13px'>The amount will be credited within 1–3 business days.</p>"
                    + "  <p style='margin:14px 0 0;font-size:13px;color:#6b7280'>Thank you for using PrimeArena!</p>"
                    + "  <p style='margin:2px 0 0;font-size:13px;color:#6b7280'>Best regards,</p>"
                    + "  <p style='margin:0;font-size:13px;color:#6b7280'>PrimeArena Team</p>"
                    + "</div>",
                    logoCid != null ? "<img alt='PrimeArena' src='cid:" + logoCid + "' style='height:40px'/>" : "",
                    escape(displayName),
                    escape(String.valueOf(amount)),
                    escape(String.valueOf(withdrawalMethod)),
                    escape(String.valueOf(referenceId)),
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
            );

            helper.setText(html, true);
            javaMailSender.send(mime);
            log.info("Withdrawal success notification sent to: {}", toEmail);

        } catch (UnsupportedEncodingException e) {
            log.error("Unsupported encoding when building from address: {}", e.getMessage(), e);
        } catch (MessagingException e) {
            log.error("Failed to send withdrawal success notification to: {}", toEmail, e);
            // Don't throw exception here as withdrawal is already processed
        }
    }

    /**
     * Notify admins: a new withdrawal request has been created (pending
     * review). Subject: "withdraw request" as requested by user.
     */
    @Async
    public void notifyAdminsWithdrawRequest(String userEmail,
            String userName,
            String amount,
            String method,
            String upiId,
            String accountNumber,
            String ifscCode,
            String accountHolderName,
            String referenceId) {
        String[] recipients = parseAdminEmails();
        if (recipients.length == 0) {
            log.warn("No admin emails configured (app.admin.emails). Skipping admin withdraw notification.");
            return;
        }
        try {
            MimeMessage mime = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(recipients);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setSubject("withdraw request");

            String logoCid = maybeAttachLogo(helper);
            StringBuilder details = new StringBuilder();
            details.append(String.format("<div><strong>User:</strong> %s (%s)</div>",
                    escape(nullSafe(userName)), escape(nullSafe(userEmail))));
            details.append("<div><strong>Amount:</strong> ₹").append(escape(nullSafe(amount))).append("</div>");
            details.append("<div><strong>Method:</strong> ").append(escape(nullSafe(method))).append("</div>");
            if (upiId != null && !upiId.isBlank()) {
                details.append("<div><strong>UPI:</strong> ").append(escape(upiId)).append("</div>");
            }
            if (accountNumber != null && !accountNumber.isBlank()) {
                details.append("<div><strong>Account:</strong> ").append(escape(accountNumber)).append("</div>");
            }
            if (ifscCode != null && !ifscCode.isBlank()) {
                details.append("<div><strong>IFSC:</strong> ").append(escape(ifscCode)).append("</div>");
            }
            if (accountHolderName != null && !accountHolderName.isBlank()) {
                details.append("<div><strong>Account Holder:</strong> ").append(escape(accountHolderName)).append("</div>");
            }
            if (referenceId != null && !referenceId.isBlank()) {
                details.append("<div><strong>Reference:</strong> ").append(escape(referenceId)).append("</div>");
            }

            String html = String.format(""
                    + "<div style='font-family:Inter,Segoe UI,Arial,sans-serif;color:#111827'>"
                    + "  <div style='text-align:center;margin-bottom:14px'>%s</div>"
                    + "  <h2 style='margin:0 0 10px;font-size:18px'>New Withdrawal Request</h2>"
                    + "  <div style='margin:10px 0;padding:12px;border:1px solid #e5e7eb;border-radius:10px;background:#f8fafc'>%s</div>"
                    + "  <div style='color:#6b7280;font-size:12px'>Sent automatically by backend</div>"
                    + "</div>",
                    logoCid != null ? "<img alt='PrimeArena' src='cid:" + logoCid + "' style='height:36px'/>" : "",
                    details.toString());
            helper.setText(html, true);
            javaMailSender.send(mime);
            log.info("Admin withdraw notification sent to {}", String.join(", ", recipients));
        } catch (UnsupportedEncodingException | MessagingException e) {
            log.error("Failed to send admin withdraw notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Notify admins: a new add-money UPI credit request (UTR submitted) is
     * pending. Subject: "credit request" as requested by user.
     */
    @Async
    public void notifyAdminsCreditRequest(String userEmail,
            String userName,
            String amount,
            String paymentApp,
            String payerUpiId,
            String utr,
            String referenceId) {
        String[] recipients = parseAdminEmails();
        if (recipients.length == 0) {
            log.warn("No admin emails configured (app.admin.emails). Skipping admin credit notification.");
            return;
        }
        try {
            MimeMessage mime = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(recipients);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setSubject("credit request");

            String logoCid = maybeAttachLogo(helper);
            StringBuilder details = new StringBuilder();
            details.append(String.format("<div><strong>User:</strong> %s (%s)</div>",
                    escape(nullSafe(userName)), escape(nullSafe(userEmail))));
            details.append("<div><strong>Amount:</strong> ₹").append(escape(nullSafe(amount))).append("</div>");
            if (paymentApp != null && !paymentApp.isBlank()) {
                details.append("<div><strong>App:</strong> ").append(escape(paymentApp)).append("</div>");
            }
            if (payerUpiId != null && !payerUpiId.isBlank()) {
                details.append("<div><strong>Payer UPI:</strong> ").append(escape(payerUpiId)).append("</div>");
            }
            if (utr != null && !utr.isBlank()) {
                details.append("<div><strong>UTR:</strong> ").append(escape(utr)).append("</div>");
            }
            if (referenceId != null && !referenceId.isBlank()) {
                details.append("<div><strong>Reference:</strong> ").append(escape(referenceId)).append("</div>");
            }

            String html = String.format(""
                    + "<div style='font-family:Inter,Segoe UI,Arial,sans-serif;color:#111827'>"
                    + "  <div style='text-align:center;margin-bottom:14px'>%s</div>"
                    + "  <h2 style='margin:0 0 10px;font-size:18px'>New Credit Request (UPI)</h2>"
                    + "  <div style='margin:10px 0;padding:12px;border:1px solid #e5e7eb;border-radius:10px;background:#f8fafc'>%s</div>"
                    + "  <div style='color:#6b7280;font-size:12px'>Sent automatically by backend</div>"
                    + "</div>",
                    logoCid != null ? "<img alt='PrimeArena' src='cid:" + logoCid + "' style='height:36px'/>" : "",
                    details.toString());
            helper.setText(html, true);
            javaMailSender.send(mime);
            log.info("Admin credit notification sent to {}", String.join(", ", recipients));
        } catch (UnsupportedEncodingException | MessagingException e) {
            log.error("Failed to send admin credit notification: {}", e.getMessage(), e);
        }
    }

    private String[] parseAdminEmails() {
        if (adminEmails == null || adminEmails.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays.stream(adminEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }

    /**
     * Notify admins: Support contact from user with optional image attachment
     * (single). Subject: "support request" (as per style of other
     * notifications)
     */
    @Async
    public void notifyAdminsSupport(String userEmail, String phone, String message, java.util.List<MultipartFile> images) {
        String[] recipients = parseAdminEmails();
        if (recipients.length == 0) {
            log.warn("No admin emails configured (app.admin.emails). Skipping support notification.");
            return;
        }
        try {
            MimeMessage mime = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(recipients);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setSubject("support request");

            String logoCid = maybeAttachLogo(helper);
            String html = String.format(""
                    + "<div style='font-family:Inter,Segoe UI,Arial,sans-serif;color:#111827'>"
                    + "  <div style='text-align:center;margin-bottom:14px'>%s</div>"
                    + "  <h2 style='margin:0 0 10px;font-size:18px'>New Support Request</h2>"
                    + "  <div style='margin:10px 0;padding:12px;border:1px solid #e5e7eb;border-radius:10px;background:#f8fafc'>"
                    + "    <div><strong>Email:</strong> %s</div>"
                    + "    <div><strong>Phone:</strong> %s</div>"
                    + "    <div style='margin-top:8px'><strong>Message:</strong><br/>%s</div>"
                    + "  </div>"
                    + "  <div style='color:#6b7280;font-size:12px'>Sent automatically by backend</div>"
                    + "</div>",
                    logoCid != null ? "<img alt='PrimeArena' src='cid:" + logoCid + "' style='height:36px'/>" : "",
                    escape(nullSafe(userEmail)),
                    escape(nullSafe(phone)),
                    escape(nullSafe(message)).replace("\n", "<br/>")
            );
            helper.setText(html, true);

            // Attach at most 1 image (UI restricts to one)
            if (images != null) {
                int limit = Math.min(1, images.size());
                for (int i = 0; i < limit; i++) {
                    MultipartFile mf = images.get(i);
                    if (mf == null || mf.isEmpty()) {
                        continue;
                    }
                    String filename = mf.getOriginalFilename() != null ? mf.getOriginalFilename() : ("image_" + (i + 1) + ".jpg");
                    String contentType = mf.getContentType() != null ? mf.getContentType() : "application/octet-stream";
                    helper.addAttachment(filename, new org.springframework.core.io.ByteArrayResource(mf.getBytes()) {
                        @Override
                        public String getFilename() {
                            return filename;
                        }
                    }, contentType);
                }
            }

            javaMailSender.send(mime);
            log.info("Support notification sent to {}", String.join(", ", recipients));
        } catch (UnsupportedEncodingException | jakarta.mail.MessagingException e) {
            log.error("Failed to send support notification: {}", e.getMessage(), e);
        } catch (java.io.IOException io) {
            log.error("Failed to attach images for support notification: {}", io.getMessage(), io);
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private String maybeAttachLogo(MimeMessageHelper helper) {
        try {
            if (logoPath == null || logoPath.isBlank()) {
                return null;
            }
            Resource res = resourceLoader.getResource(logoPath);
            if (!res.exists()) {
                log.warn("Logo resource not found at {}", logoPath);
                return null;
            }
            String cid = "logo";
            helper.addInline(cid, res);
            return cid;
        } catch (MessagingException e) {
            log.warn("Could not attach logo inline: {}", e.getMessage());
            return null;
        }
    }

    private static String escape(String in) {
        if (in == null) {
            return "";
        }
        return in.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
