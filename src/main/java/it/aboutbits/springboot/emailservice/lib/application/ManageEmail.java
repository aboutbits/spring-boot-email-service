package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.AttachmentReference;
import it.aboutbits.springboot.emailservice.lib.EmailDto;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
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
    private final EmailRepository emailRepository;
    private final JavaMailSender mailSender;
    private final AttachmentDataSource attachmentDataSource;
    private final EmailMapper emailMapper;

    public ManageEmail(
            EmailRepository emailRepository,
            JavaMailSender mailSender,
            AttachmentDataSource attachmentDataSource,
            final EmailMapper emailMapper) {

        this.emailRepository = emailRepository;
        this.mailSender = mailSender;
        this.attachmentDataSource = attachmentDataSource;
        this.emailMapper = emailMapper;
    }

    public EmailDto schedule(@NonNull @Valid EmailParameter parameter) {
        var emailData = parameter.email();

        final var email = new Email();
        email.setState(EmailState.PENDING);
        email.setScheduledAt(parameter.scheduledAt());
        email.setSubject(emailData.subject());
        email.setTextBody(emailData.textBody());
        email.setHtmlBody(emailData.htmlBody());
        email.setRecipients(emailData.recipients());
        email.setFromAddress(emailData.fromAddress());
        email.setFromName(emailData.fromName());
        email.setAttachments(emailData.attachments().stream()
                .map(a -> {
                    var attachment = new EmailAttachment();
                    attachment.setEmail(email);
                    attachment.setReference(a.reference().value());
                    attachment.setContentType(a.contentType());
                    attachment.setFileName(a.fileName());
                    return attachment;
                })
                .collect(Collectors.toSet())
        );

        var savedEmail = emailRepository.save(email);

        return emailMapper.toDto(savedEmail);
    }

    Email send(Email email) {
        if (EmailState.SENT.equals(email.getState())) {
            return email;
        }

        try {
            sendMail(email);
            email.setState(EmailState.SENT);
            email.setErrorMessage("");
            email.setSentAt(OffsetDateTime.now());
        } catch (MessagingException | AttachmentException | IOException e) {
            log.error("Failed to send email", e);
            email.setErrorMessage(e.getMessage());
            email.setState(EmailState.ERROR);
        }

        var updatedEmail = emailRepository.save(email);

        if (updatedEmail.isSent()) {
            try {
                cleanupAttachments(email);
            } catch (AttachmentException e) {
                throw new IllegalStateException(e);
            }
        }

        return updatedEmail;
    }

    private void cleanupAttachments(final Email email) throws AttachmentException {
        for (var attachment : email.getAttachments()) {
            attachmentDataSource.releaseAttachment(new AttachmentReference(attachment.getReference()));
        }
    }


    private void sendMail(Email notification) throws MessagingException, IOException, AttachmentException {
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

    private void sendMail(String fromAddress, String fromName, List<String> recipients, String subject, String htmlBody, String plainTextBody, Set<EmailAttachment> attachments) throws MessagingException, IOException, AttachmentException {
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
                var payload = attachmentDataSource.getAttachmentPayload(new AttachmentReference(attachment.getReference()));
                helper.addAttachment(attachment.getFileName(), new ByteArrayResource(payload.readAllBytes()));
                payload.close();
            }
        }

        mailSender.send(message);
    }
}
