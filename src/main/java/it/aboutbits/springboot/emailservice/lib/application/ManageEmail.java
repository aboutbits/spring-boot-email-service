package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.AttachmentReference;
import it.aboutbits.springboot.emailservice.lib.EmailDto;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import it.aboutbits.springboot.emailservice.lib.exception.EmailException;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import it.aboutbits.springboot.emailservice.lib.model.EmailAttachment;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Validated
@Slf4j
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

    public EmailDto schedule(@NonNull @Valid EmailParameter parameter) throws EmailException {
        Email email;
        try {
            email = fromParameter(parameter);
        } catch (AttachmentException e) {
            throw new EmailException(e);
        }

        var savedEmail = emailRepository.save(email);

        return emailMapper.toDto(savedEmail);
    }

    public EmailDto sendOrFail(@NonNull @Valid EmailParameter parameter) throws EmailException {
        Email email;
        try {
            email = fromParameter(parameter);
        } catch (AttachmentException e) {
            throw new EmailException(e);
        }

        var savedEmail = send(email);

        if (savedEmail.hasFailed()) {
            throw new EmailException(savedEmail.getErrorMessage());
        }

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
        } catch (MailException | MessagingException | AttachmentException | IOException e) {
            log.error("Failed to send email: " + email.getId(), e);
            email.setErrorMessage(e.getMessage());
            email.setState(EmailState.ERROR);
        }

        return emailRepository.save(email);
    }

    void cleanupAttachments(final Email email) throws AttachmentException {
        for (var attachment : email.getAttachments()) {
            attachmentDataSource.releaseAttachment(new AttachmentReference(attachment.getReference()));
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

        var attachments = new HashSet<EmailAttachment>();
        for (var attachment : parameter.email().attachments()) {
            var reference = attachmentDataSource.storeAttachmentPayload(attachment.payload());

            var emailAttachment = new EmailAttachment();
            emailAttachment.setEmail(email);
            emailAttachment.setReference(reference.value());
            emailAttachment.setContentType(attachment.contentType());
            emailAttachment.setFileName(attachment.fileName());

            attachments.add(emailAttachment);
        }

        email.setAttachments(attachments);

        return email;
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

        for (var attachment : attachments) {
            var payload = attachmentDataSource.getAttachmentPayload(new AttachmentReference(attachment.getReference()));
            helper.addAttachment(attachment.getFileName(), new ByteArrayResource(payload.readAllBytes()));
            payload.close();
        }


        mailSender.send(message);
    }
}
