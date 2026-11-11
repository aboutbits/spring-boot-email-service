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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileCopyUtils;
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
    private final ApplicationEventPublisher eventPublisher;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public ManageEmail(
            EmailRepository emailRepository,
            JavaMailSender mailSender,
            AttachmentDataSource attachmentDataSource,
            EmailMapper emailMapper,
            int maxAttempts,
            Duration schedulerInterval,
            PlatformTransactionManager transactionManager,
            ApplicationEventPublisher eventPublisher
    ) {
        this.emailRepository = emailRepository;
        this.mailSender = mailSender;
        this.attachmentDataSource = attachmentDataSource;
        this.emailMapper = emailMapper;
        this.maxAttempts = maxAttempts;
        this.schedulerInterval = schedulerInterval;
        this.eventPublisher = eventPublisher;
        // Persist the final email state in its own, independent transaction to never make it roll back
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Persists the email as {@link EmailState#PENDING} to be sent by the scheduler at {@code parameter.scheduledAt()}.
     * <p>
     * Must be called from within an active transaction: the email row is written in the caller's transaction, so
     * it commits and rolls back together with the caller's own data. The same holds for the attachment payloads
     * with the default JDBC {@link AttachmentDataSource}; a custom one must take part in the transaction itself.
     * Calling this method without an active transaction fails with an
     * {@link org.springframework.transaction.IllegalTransactionStateException}. An {@link EmailException} marks the
     * caller's transaction rollback-only: the caller's data is never committed without the email.
     *
     * @return the stored email, in state {@link EmailState#PENDING}
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = EmailException.class)
    public EmailDto schedule(@Valid EmailParameter parameter) throws EmailException {
        Email email;
        try {
            email = fromParameter(parameter.scheduledAt(), parameter.email());
        } catch (AttachmentException e) {
            throw new EmailException(e);
        }

        var savedEmail = emailRepository.save(email);

        return emailMapper.toDto(savedEmail);
    }

    /**
     * Persists the email as {@link EmailState#PENDING} in the caller's transaction and sends it right after that
     * transaction commits. The caller never waits for the SMTP round-trip.
     * <p>
     * Must be called from within an active transaction: the email row is written in the caller's transaction, so
     * it commits and rolls back together with the caller's own data. The same holds for the attachment payloads
     * with the default JDBC {@link AttachmentDataSource}; a custom one must take part in the transaction itself.
     * Calling this method without an active transaction fails with an
     * {@link org.springframework.transaction.IllegalTransactionStateException}. An {@link EmailException} marks the
     * caller's transaction rollback-only: the caller's data is never committed without the email.
     * <p>
     * After the commit, the email id is handed to a bounded executor that claims the row with the same kind of
     * atomic update as the scheduler, so it is never sent twice. If the hand-off is lost (saturated executor, pod
     * restart) or the send fails, the scheduler picks the email up and retries it like any scheduled email. With
     * {@code aboutbits.emailservice.async-dispatch.enabled=false} (recommended for tests) nothing is dispatched and
     * the scheduler alone sends the email.
     *
     * @return the stored email, in state {@link EmailState#PENDING}
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = EmailException.class)
    public EmailDto sendAsync(@Valid EmailParameter.Email email) throws EmailException {
        Email entity;
        try {
            entity = fromParameter(OffsetDateTime.now(), email);
        } catch (AttachmentException e) {
            throw new EmailException(e);
        }

        var savedEmail = emailRepository.save(entity);

        // Consumed by SendAsyncEmails after the commit; ignored when the async dispatch is disabled.
        eventPublisher.publishEvent(new AsyncEmailQueued(savedEmail.getId()));

        return emailMapper.toDto(savedEmail);
    }

    /**
     * Sends the email at once, in a single attempt, and persists the outcome. The caller waits for the SMTP round-trip.
     * If the send fails, the email is stored as {@link EmailState#ERROR},
     * is not retried, and an {@link EmailException} is thrown.
     * <p>
     * Must be called outside any transaction. Inside one, the SMTP round-trip would run while the caller's database
     * connection is held, and persisting the outcome would need a second connection at the same time.
     * Calling this method within an active transaction fails with an
     * {@link org.springframework.transaction.IllegalTransactionStateException}.
     * <p>
     * Use this only when the email is not tied to a database write,
     * i.e. when the email and the caller's data need not stay consistent with each other.
     * Otherwise, use {@link #sendAsync(EmailParameter.Email)} or {@link #schedule(EmailParameter)} inside the
     * transaction that writes the data.
     *
     * @return the stored email, in state {@link EmailState#SENT}
     */
    @Transactional(propagation = Propagation.NEVER)
    public EmailDto sendOrFail(@Valid EmailParameter parameter) throws EmailException {
        Email email;
        try {
            email = fromParameter(parameter.scheduledAt(), parameter.email());
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

    // Like tryClaimForSend, but never takes over a stale SENDING row: the after-commit dispatch only claims the row
    // it just inserted, and recovering rows abandoned by a crashed pod is the scheduler's job.
    @Transactional
    Optional<Email> tryClaimPendingForSend(long id) {
        var claimed = emailRepository.claimPendingForSend(id, OffsetDateTime.now());
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
            @Nullable
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

        if (htmlBody != null && !htmlBody.isBlank()) {
            helper.setText(plainTextBody, htmlBody);
        } else {
            helper.setText(plainTextBody);
        }

        for (var attachment : attachments) {
            var resource = new ByteArrayResource(FileCopyUtils.copyToByteArray(
                    attachmentDataSource.getAttachmentPayload(attachment.getFileReference())
            ));

            var contentId = attachment.getContentId();
            if (contentId != null) {
                helper.addInline(contentId, attachment.getFileName(), resource, attachment.getContentType());
            } else {
                helper.addAttachment(attachment.getFileName(), resource, attachment.getContentType());
            }
        }

        mailSender.send(message);
    }

    private Email fromParameter(OffsetDateTime scheduledAt, EmailParameter.Email emailData) throws AttachmentException {
        var email = new Email();
        email.setState(EmailState.PENDING);
        email.setScheduledAt(scheduledAt);
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
        for (var attachment : emailData.attachments()) {
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
