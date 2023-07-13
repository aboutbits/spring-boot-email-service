package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import it.aboutbits.springboot.emailservice.lib.model.EmailAttachment;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Validated
@Log4j2
public class ManageEmail {
    private final EmailRepository emailNotificationRepository;
    private final JavaMailSender mailSender;
    private final AttachmentDataSource attachmentDataSource;

    public ManageEmail(
            EmailRepository emailNotificationRepository,
            JavaMailSender mailSender,
            AttachmentDataSource attachmentDataSource
    ) {

        this.emailNotificationRepository = emailNotificationRepository;
        this.mailSender = mailSender;
        this.attachmentDataSource = attachmentDataSource;
    }

    public Email schedule(@NonNull @Valid EmailParameter parameter) {
        var emailData = parameter.email();

        var email = new Email();
        email.setState(EmailState.PENDING);
        email.setSendingScheduledAt(parameter.scheduleAt());
        email.setReference(parameter.reference());
        email.setSubject(emailData.subject());
        email.setTextBody(emailData.textBody());
        email.setHtmlBody(emailData.htmlBody());
        email.setRecipients(emailData.recipients());
        email.setFromAddress(emailData.fromAddress());
        email.setFromName(emailData.fromName());
        email.setAttachments(emailData.attachments().stream()
                .map(a -> {
                    var attachment = new EmailAttachment();
                    attachment.setReference(a.reference());
                    attachment.setContentType(a.contentType());
                    attachment.setFileName(a.fileName());
                    return attachment;
                })
                .collect(Collectors.toSet())
        );

        email = emailNotificationRepository.save(email);

        return email;
    }

    Email send(Email notification) {
        if (EmailState.SENT.equals(notification.getState())) {
            return notification;
        }

        try {
            sendMail(notification);
            notification.setState(EmailState.SENT);
            notification.setErrorMessage("");
            notification.setSentAt(OffsetDateTime.now());
        } catch (MessagingException | IOException e) {
            log.error("Failed to send email", e);
            notification.setErrorMessage(e.getMessage());
            notification.setState(EmailState.ERROR);
        }

        var updatedNotification = emailNotificationRepository.save(notification);

        if (updatedNotification.isSent()) {
            cleanupAttachments(notification);
        }

        return updatedNotification;
    }

    private void cleanupAttachments(final Email notification) {
        notification.getAttachments().forEach(a -> attachmentDataSource.releaseAttachment(a.getReference()));
    }


    private void sendMail(Email notification) throws MessagingException, IOException {
        sendMail(
                notification.getFromAddress(),
                notification.getFromName(),
                notification.getRecipients(),
                notification.getSubject(),
                notification.getHtmlBody(),
                notification.getTextBody(),
                notification.getAttachments()
        );
    }

    private void sendMail(String fromAddress, String fromName, List<String> recipients, String subject, String htmlBody, String plainTextBody, Set<EmailAttachment> attachments) throws MessagingException, IOException {
        var message = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromAddress, fromName);
        helper.setTo(recipients.toArray(String[]::new));
        helper.setSubject(subject);

        if (!htmlBody.isBlank()) {
            helper.setText(plainTextBody, htmlBody);
        } else {
            helper.setText(plainTextBody);
        }

        if (!attachments.isEmpty()) {
            for (var attachment : attachments) {
                var payload = attachmentDataSource.getAttachmentPayload(attachment.getReference());
                helper.addAttachment(attachment.getFileName(), new ByteArrayResource(payload.readAllBytes()));
                payload.close();
            }
        }

        mailSender.send(message);
    }
}
