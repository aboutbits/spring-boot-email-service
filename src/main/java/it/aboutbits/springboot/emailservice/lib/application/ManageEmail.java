package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.EmailDto;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import it.aboutbits.springboot.emailservice.lib.exception.EmailException;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import it.aboutbits.springboot.emailservice.lib.model.EmailAttachment;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Validated
@Slf4j
@NullMarked
public class ManageEmail {
    record SendBatchOutcome(int sent, int errors) {
        int total() {
            return sent + errors;
        }
    }

    record CleanupBatchOutcome(int cleaned, int errors) {
        int total() {
            return cleaned + errors;
        }
    }

    private final EmailRepository emailRepository;
    private final JavaMailSender mailSender;
    private final AttachmentDataSource attachmentDataSource;
    private final EmailMapper emailMapper;

    public ManageEmail(
            EmailRepository emailRepository,
            JavaMailSender mailSender,
            AttachmentDataSource attachmentDataSource,
            EmailMapper emailMapper
    ) {
        this.emailRepository = emailRepository;
        this.mailSender = mailSender;
        this.attachmentDataSource = attachmentDataSource;
        this.emailMapper = emailMapper;
    }

    public EmailDto schedule(@Valid EmailParameter parameter) throws EmailException {
        Email email;
        try {
            email = fromParameter(parameter);
        } catch (AttachmentException e) {
            throw new EmailException(e);
        }

        var savedEmail = emailRepository.save(email);

        return emailMapper.toDto(savedEmail);
    }

    public EmailDto sendOrFail(@Valid EmailParameter parameter) throws EmailException {
        Email email;
        try {
            email = fromParameter(parameter);
        } catch (AttachmentException e) {
            throw new EmailException(e);
        }

        var savedEmail = send(email);

        if (savedEmail.hasFailed()) {
            throw new EmailException("Failed to send email [id=%s, providerMessage=%s]"
                                             .formatted(savedEmail.getId(), savedEmail.getErrorMessage()));
        }

        return emailMapper.toDto(savedEmail);
    }

    // Sends the emails identified by the given ids and persists SENT/ERROR state for each
    @Transactional
    SendBatchOutcome sendBatch(List<Long> ids) {
        if (ids.isEmpty()) {
            return new SendBatchOutcome(0, 0);
        }
        var emails = emailRepository.findByIdIn(ids);
        var sent = 0;
        var errors = 0;
        for (var email : emails) {
            try {
                var updated = send(email);
                if (updated.hasFailed()) {
                    errors++;
                } else {
                    sent++;
                }
            } catch (RuntimeException e) {
                // A single misbehaving email should not roll back the whole batch's committed state
                // (which would risk duplicate delivery for siblings that already left the SMTP relay).
                log.error("Unexpected failure while sending email: {}", email.getId(), e);
                errors++;
            }
        }
        return new SendBatchOutcome(sent, errors);
    }

    // Releases attachment payloads for the given emails and marks them cleaned
    @Transactional
    CleanupBatchOutcome cleanupBatch(List<Long> ids) {
        if (ids.isEmpty()) {
            return new CleanupBatchOutcome(0, 0);
        }
        var emails = emailRepository.findByIdIn(ids);
        var cleaned = 0;
        var errors = 0;
        for (var email : emails) {
            try {
                cleanupAttachments(email);
                cleaned++;
            } catch (AttachmentException | RuntimeException e) {
                log.warn("Failed to cleanup attachments for email: {}", email.getId(), e);
                errors++;
            }
        }
        return new CleanupBatchOutcome(cleaned, errors);
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
        } catch (MailException | MessagingException | AttachmentException | IOException e) {
            log.error("Failed to send email: " + email.getId(), e);
            email.setErrorMessage(e.getMessage());
            email.setState(EmailState.ERROR);
        }

        return emailRepository.save(email);
    }

    void cleanupAttachments(final Email email) throws AttachmentException {
        for (var attachment : email.getAttachments()) {
            attachmentDataSource.releaseAttachment(attachment.getFileReference());
        }
        email.setAttachmentsCleaned(true);
        emailRepository.save(email);
    }

    private Email fromParameter(EmailParameter parameter) throws AttachmentException {
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
        email.setReplyToAddress(emailData.replyToAddress());
        email.setReplyToName(emailData.replyToName());

        var attachments = new HashSet<EmailAttachment>();
        for (var attachment : parameter.email().attachments()) {
            var reference = attachmentDataSource.storeAttachmentPayload(attachment.payload());

            var emailAttachment = new EmailAttachment();
            emailAttachment.setEmail(email);
            emailAttachment.setContentType(attachment.contentType());
            emailAttachment.setFileName(attachment.fileName());
            emailAttachment.setFileReference(reference);

            attachments.add(emailAttachment);
        }

        email.setAttachments(attachments);

        return email;
    }

    private void sendMail(Email email) throws MessagingException, IOException, AttachmentException {
        sendMail(
                email.getFromAddress(),
                email.getFromName(),
                email.getReplyToAddress(),
                email.getReplyToName(),
                email.getRecipients(),
                email.getSubject(),
                email.getHtmlBody(),
                email.getTextBody(),
                email.getAttachments()
        );
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void sendMail(
            String fromAddress,
            String fromName,
            @Nullable
            String replyToAddress,
            @Nullable
            String replyToName,
            List<String> recipients,
            String subject,
            String htmlBody,
            String plainTextBody,
            Set<EmailAttachment> attachments
    ) throws MessagingException, IOException, AttachmentException {
        var message = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromAddress, fromName);
        helper.setTo(recipients.toArray(String[]::new));
        helper.setSubject(subject);

        if (replyToAddress != null) {
            if (replyToName != null) {
                helper.setReplyTo(replyToAddress, replyToName);
            } else {
                helper.setReplyTo(replyToAddress);
            }
        }

        if (!htmlBody.isBlank()) {
            helper.setText(plainTextBody, htmlBody);
        } else {
            helper.setText(plainTextBody);
        }

        for (var attachment : attachments) {
            var payload = attachmentDataSource.getAttachmentPayload(attachment.getFileReference());
            helper.addAttachment(attachment.getFileName(), new ByteArrayResource(payload.readAllBytes()));
            payload.close();
        }


        mailSender.send(message);
    }
}
