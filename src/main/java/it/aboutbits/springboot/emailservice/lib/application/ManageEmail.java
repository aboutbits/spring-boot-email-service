package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.EmailDto;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import it.aboutbits.springboot.emailservice.lib.exception.EmailException;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import it.aboutbits.springboot.emailservice.lib.model.EmailAttachment;
import it.aboutbits.springboot.emailservice.lib.model.EmailContent;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@Validated
@Slf4j
@NullMarked
public class ManageEmail {
    private final EmailRepository emailRepository;
    private final JavaMailSender mailSender;
    private final AttachmentDataSource attachmentDataSource;
    private final EmailMapper emailMapper;
    private final int maxAttempts;
    private final Duration schedulerInterval;
    private final TransactionTemplate transactionTemplate;

    public ManageEmail(
            EmailRepository emailRepository,
            JavaMailSender mailSender,
            AttachmentDataSource attachmentDataSource,
            EmailMapper emailMapper,
            int maxAttempts,
            Duration schedulerInterval,
            PlatformTransactionManager transactionManager
    ) {
        this.emailRepository = emailRepository;
        this.mailSender = mailSender;
        this.attachmentDataSource = attachmentDataSource;
        this.emailMapper = emailMapper;
        this.maxAttempts = maxAttempts;
        this.schedulerInterval = schedulerInterval;
        // Persist the final email state in its own, independent transaction to never make it roll back
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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

        email.setExecutionStartTime(OffsetDateTime.now());
        email.incrementAttempts();

        try {
            sendMail(email);
            email.setState(EmailState.SENT);
            email.setExecutionEndTime(OffsetDateTime.now());
        } catch (MessagingException | AttachmentException | IOException | RuntimeException e) {
            log.error("Failed to send email: {}", email.getId(), e);
            email.setState(EmailState.ERROR);
            email.setExecutionEndTime(OffsetDateTime.now());
            email.setErrorMessage(e.getMessage());
        }

        var savedEmail = transactionTemplate.execute(_ -> emailRepository.save(email));

        if (savedEmail.getState() == EmailState.ERROR) {
            throw new EmailException("Failed to send email [id=%s, providerMessage=%s]"
                                             .formatted(savedEmail.getId(), savedEmail.getErrorMessage()));
        }

        return emailMapper.toDto(savedEmail);
    }

    // Atomically transitions the row from PENDING/stale-SENDING into SENDING
    @Transactional
    Optional<Email> tryClaimForSend(long id, OffsetDateTime staleSendingBefore) {
        var claimed = emailRepository.claimForSend(id, OffsetDateTime.now(), staleSendingBefore);
        return claimed == 0 ? Optional.empty() : emailRepository.findById(id);
    }

    // Actually try sending the claimed email
    Email completeClaimedSend(Email email) {
        try {
            sendMail(email);
            email.setState(EmailState.SENT);
            email.setExecutionEndTime(OffsetDateTime.now());
            email.setErrorMessage(null);
        } catch (Exception e) {
            email.setExecutionEndTime(OffsetDateTime.now());
            email.setErrorMessage(e.getMessage());
            if (email.getAttempts() >= maxAttempts) {
                log.error("Failed to send email: {}", email.getId(), e);
                email.setState(EmailState.ERROR);
            } else {
                log.warn("Failed to send email: {}; Will be tried again", email.getId(), e);
                email.setState(EmailState.PENDING);
                email.setScheduledAt(
                        OffsetDateTime.now()
                                .plus(schedulerInterval.multipliedBy((long) Math.pow(2, email.getAttempts())))
                );
            }
        }
        return transactionTemplate.execute(_ -> emailRepository.save(email));
    }

    // Atomically marks a not-yet-cleaned SENT row as cleanup-in-progress
    @Transactional
    Optional<Email> tryClaimForCleanup(long id, OffsetDateTime staleCleanupBefore) {
        var claimed = emailRepository.claimForCleanup(id, OffsetDateTime.now(), staleCleanupBefore);
        return claimed == 0 ? Optional.empty() : emailRepository.findById(id);
    }

    // Actually release the attachment payloads of the claimed email.
    void completeClaimedCleanup(final Email email) throws AttachmentException {
        for (var attachment : email.getAttachments()) {
            attachmentDataSource.releaseAttachment(attachment.getFileReference());
        }
        email.setAttachmentsCleaned(true);
        transactionTemplate.execute(_ -> emailRepository.save(email));
    }

    private void sendMail(Email email) throws MessagingException, IOException, AttachmentException {
        var content = email.getContent();
        sendMail(
                content.fromAddress(),
                content.fromName(),
                content.replyToAddress(),
                content.replyToName(),
                content.recipients(),
                content.subject(),
                content.htmlBody(),
                content.textBody(),
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
            ByteArrayResource resource;
            try (var payload = attachmentDataSource.getAttachmentPayload(attachment.getFileReference())) {
                resource = new ByteArrayResource(payload.readAllBytes());
            }

            var contentId = attachment.getContentId();
            if (contentId != null) {
                helper.addInline(contentId, resource, attachment.getContentType());
            } else {
                helper.addAttachment(attachment.getFileName(), resource);
            }
        }

        mailSender.send(message);
    }

    private Email fromParameter(EmailParameter parameter) throws AttachmentException {
        var emailData = parameter.email();

        var email = new Email();
        email.setState(EmailState.PENDING);
        email.setScheduledAt(parameter.scheduledAt());
        email.setContent(new EmailContent(
                emailData.subject(),
                emailData.fromAddress(),
                emailData.fromName(),
                emailData.replyToAddress(),
                emailData.replyToName(),
                emailData.recipients(),
                emailData.textBody(),
                emailData.htmlBody()
        ));

        var attachments = new HashSet<EmailAttachment>();
        for (var attachment : parameter.email().attachments()) {
            var reference = attachmentDataSource.storeAttachmentPayload(attachment.payload());

            var emailAttachment = new EmailAttachment();
            emailAttachment.setEmail(email);
            emailAttachment.setContentType(attachment.contentType());
            emailAttachment.setContentId(attachment.contentId());
            emailAttachment.setFileName(attachment.fileName());
            emailAttachment.setFileReference(reference);

            attachments.add(emailAttachment);
        }

        email.setAttachments(attachments);

        return email;
    }
}
