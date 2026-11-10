package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.EmailDto;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import it.aboutbits.springboot.emailservice.lib.exception.EmailException;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.support.database.WithPostgres;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@WithPostgres
@NullMarked
class ManageEmailTest {
    @MockitoSpyBean
    JavaMailSender javaMailSender;

    @MockitoBean
    AttachmentDataSource attachmentDataSource;

    @Autowired
    private ManageEmail manageEmail;

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SendScheduledEmails sendScheduledEmails;

    @BeforeEach
    void setup() {
        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    @Test
    void givenRequiredParameters_schedule_shouldCreateNewNotification() throws EmailException {
        var parameter = getValidParameterWithoutAttachment();

        var result = scheduleInTransaction(parameter);

        assertThat(result.id()).isPositive();
        assertThat(result.state()).isEqualTo(EmailState.PENDING);
        assertThat(result.subject()).isEqualTo(parameter.email().subject());
        assertThat(result.fromAddress()).isEqualTo(parameter.email().fromAddress());
        assertThat(result.fromName()).isEqualTo(parameter.email().fromName());
        assertThat(result.replyToAddress()).isEqualTo(parameter.email().replyToAddress());
        assertThat(result.replyToName()).isEqualTo(parameter.email().replyToName());
        assertThat(result.recipients()).containsAll(parameter.email().recipients());
        assertThat(result.textBody()).isEqualTo(parameter.email().textBody());
        assertThat(result.htmlBody()).isEqualTo(parameter.email().htmlBody());
        assertThat(result.attachments()).isEmpty();
        assertThat(result.scheduledAt()).isEqualTo(parameter.scheduledAt());
    }

    @Test
    void givenRequiredParameterWithAttachedFiles_schedule_shouldCreateNewNotification() throws EmailException, AttachmentException {
        when(attachmentDataSource.storeAttachmentPayload(any())).thenReturn(33L);

        var parameter = getValidParameterWithAttachment();

        var result = scheduleInTransaction(parameter);

        assertThat(result.id()).isPositive();
        assertThat(result.state()).isEqualTo(EmailState.PENDING);
        assertThat(result.subject()).isEqualTo(parameter.email().subject());
        assertThat(result.fromAddress()).isEqualTo(parameter.email().fromAddress());
        assertThat(result.fromName()).isEqualTo(parameter.email().fromName());
        assertThat(result.replyToAddress()).isEqualTo(parameter.email().replyToAddress());
        assertThat(result.replyToName()).isEqualTo(parameter.email().replyToName());
        assertThat(result.recipients()).containsAll(parameter.email().recipients());
        assertThat(result.textBody()).isEqualTo(parameter.email().textBody());
        assertThat(result.htmlBody()).isEqualTo(parameter.email().htmlBody());
        assertThat(result.attachments()).hasSize(1);
        assertThat(result.scheduledAt()).isEqualTo(parameter.scheduledAt());
    }

    @Test
    void givenRequiredParameters_sendOrFail_shouldCreateNewNotification() throws EmailException {
        var parameter = getValidParameterWithoutAttachment();

        var result = manageEmail.sendOrFail(parameter);

        assertThat(result.id()).isPositive();
        assertThat(result.state()).isEqualTo(EmailState.SENT);
        assertThat(result.subject()).isEqualTo(parameter.email().subject());
        assertThat(result.fromAddress()).isEqualTo(parameter.email().fromAddress());
        assertThat(result.fromName()).isEqualTo(parameter.email().fromName());
        assertThat(result.replyToAddress()).isEqualTo(parameter.email().replyToAddress());
        assertThat(result.replyToName()).isEqualTo(parameter.email().replyToName());
        assertThat(result.recipients()).containsAll(parameter.email().recipients());
        assertThat(result.textBody()).isEqualTo(parameter.email().textBody());
        assertThat(result.htmlBody()).isEqualTo(parameter.email().htmlBody());
        assertThat(result.attachments()).isEmpty();
        assertThat(result.scheduledAt()).isEqualTo(parameter.scheduledAt());
    }

    @Test
    void givenRequiredParameterWithAttachedFiles_sendOrFail_shouldCreateNewNotification() throws EmailException, AttachmentException {
        when(attachmentDataSource.storeAttachmentPayload(any())).thenReturn(33L);
        when(attachmentDataSource.getAttachmentPayload(anyLong())).thenReturn(new ByteArrayInputStream(new byte[0]));

        var parameter = getValidParameterWithAttachment();

        var result = manageEmail.sendOrFail(parameter);

        assertThat(result.id()).isPositive();
        assertThat(result.state()).isEqualTo(EmailState.SENT);
        assertThat(result.subject()).isEqualTo(parameter.email().subject());
        assertThat(result.fromAddress()).isEqualTo(parameter.email().fromAddress());
        assertThat(result.fromName()).isEqualTo(parameter.email().fromName());
        assertThat(result.replyToAddress()).isEqualTo(parameter.email().replyToAddress());
        assertThat(result.replyToName()).isEqualTo(parameter.email().replyToName());
        assertThat(result.recipients()).containsAll(parameter.email().recipients());
        assertThat(result.textBody()).isEqualTo(parameter.email().textBody());
        assertThat(result.htmlBody()).isEqualTo(parameter.email().htmlBody());
        assertThat(result.attachments()).hasSize(1);
        assertThat(result.scheduledAt()).isEqualTo(parameter.scheduledAt());
    }

    @Test
    void givenRequiredParameters_sendOrFail_shouldSendImmediately() throws EmailException {
        EmailParameter parameter = getValidParameterWithoutAttachment();

        manageEmail.sendOrFail(parameter);

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void givenRequiredParameterWithAttachedFiles_sendOrFail_shouldSendImmediately() throws EmailException, AttachmentException {
        when(attachmentDataSource.storeAttachmentPayload(any())).thenReturn(33L);
        when(attachmentDataSource.getAttachmentPayload(anyLong())).thenReturn(new ByteArrayInputStream(new byte[0]));

        var parameter = getValidParameterWithAttachment();

        manageEmail.sendOrFail(parameter);

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void givenInlineAttachment_schedule_shouldPersistContentId() throws EmailException, AttachmentException {
        when(attachmentDataSource.storeAttachmentPayload(any())).thenReturn(33L);

        var parameter = getValidParameterWithInlineAttachment(
                "<h1>Html email body</h1><img src=\"cid:header-logo\">",
                inlineAttachment("header-logo")
        );

        var result = scheduleInTransaction(parameter);

        assertThat(result.id()).isPositive();
        assertThat(result.state()).isEqualTo(EmailState.PENDING);
        assertThat(result.attachments()).hasSize(1);
        assertThat(result.attachments().iterator().next().contentId()).isEqualTo("header-logo");
    }

    @Test
    void givenInlineAndRegularAttachment_sendOrFail_shouldAddInlineAndRegularMimeParts() throws Exception {
        when(attachmentDataSource.storeAttachmentPayload(any())).thenReturn(33L);
        when(attachmentDataSource.getAttachmentPayload(anyLong()))
                .thenAnswer(_ -> new ByteArrayInputStream(new byte[]{1, 2, 3}));

        var parameter = getValidParameterWithInlineAttachment(
                "<h1>Html email body</h1><img src=\"cid:header-logo\">",
                inlineAttachment("header-logo"),
                regularAttachment("x.png")
        );

        var result = manageEmail.sendOrFail(parameter);

        assertThat(result.state()).isEqualTo(EmailState.SENT);

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        var message = captor.getValue();
        message.saveChanges();

        assertThat(message.getContentType()).startsWith("multipart/mixed");

        var parts = flattenParts(message.getContent());

        var inlinePart = parts.stream()
                .filter(part -> hasDisposition(part, Part.INLINE))
                .findFirst()
                .orElseThrow();
        assertThat(inlinePart.getContentID()).isEqualTo("<header-logo>");
        assertThat(inlinePart.getContentType()).startsWith("image/png");
        assertThat(inlinePart.getFileName()).isEqualTo("logo.png");

        var attachmentPart = parts.stream()
                .filter(part -> hasDisposition(part, Part.ATTACHMENT))
                .findFirst()
                .orElseThrow();
        assertThat(attachmentPart.getFileName()).isEqualTo("x.png");
    }

    @Test
    void givenUppercaseCidReference_schedule_shouldSucceed() throws EmailException, AttachmentException {
        when(attachmentDataSource.storeAttachmentPayload(any())).thenReturn(33L);

        var parameter = getValidParameterWithInlineAttachment(
                "<h1>Html email body</h1><img src=\"CID:header-logo\">",
                inlineAttachment("header-logo")
        );

        var result = scheduleInTransaction(parameter);

        assertThat(result.state()).isEqualTo(EmailState.PENDING);
    }

    @ParameterizedTest
    @CsvSource({
            "'', header-logo",
            ", header-logo",
            "<h1>Html email body</h1>, header-logo",
            "<img src=cid:header-logo-big>, header-logo",
            "<h1>Html email body</h1>, ' '"
    })
    void givenInvalidInlineAttachment_schedule_shouldFail(@Nullable String htmlBody, String contentId) {
        var parameter = getValidParameterWithInlineAttachment(htmlBody, inlineAttachment(contentId));

        assertThatExceptionOfType(ConstraintViolationException.class).isThrownBy(
                () -> scheduleInTransaction(parameter)
        );
    }

    @Test
    void givenDuplicateContentIds_schedule_shouldFail() {
        var parameter = getValidParameterWithInlineAttachment(
                "<h1>Html email body</h1><img src=\"cid:header-logo\">",
                inlineAttachment("header-logo"),
                inlineAttachment("header-logo", "other.png")
        );

        assertThatExceptionOfType(ConstraintViolationException.class).isThrownBy(
                () -> scheduleInTransaction(parameter)
        );
    }

    @Test
    void givenNoActiveTransaction_schedule_shouldFail() {
        var parameter = getValidParameterWithoutAttachment();

        assertThatExceptionOfType(IllegalTransactionStateException.class).isThrownBy(
                () -> manageEmail.schedule(parameter)
        );

        verify(javaMailSender, never()).send(any(MimeMessage.class));
        assertThat(emailRepository.findAll()).isEmpty();
    }

    @Test
    void givenCommittingTransaction_schedule_shouldPersistEmail() throws EmailException {
        var result = scheduleInTransaction(getValidParameterWithoutAttachment());

        assertThat(emailRepository.findById(result.id())).isPresent();
    }

    @Test
    void givenRollingBackTransaction_schedule_shouldNotPersistEmail() throws EmailException {
        var transaction = transactionManager.getTransaction(TransactionDefinition.withDefaults());
        var result = manageEmail.schedule(getValidParameterWithoutAttachment());
        transactionManager.rollback(transaction);

        assertThat(emailRepository.findById(result.id())).isEmpty();
    }

    @Test
    void givenActiveTransaction_sendOrFail_shouldFail() {
        var parameter = getValidParameterWithoutAttachment();

        var transaction = transactionManager.getTransaction(TransactionDefinition.withDefaults());
        try {
            assertThatExceptionOfType(IllegalTransactionStateException.class).isThrownBy(
                    () -> manageEmail.sendOrFail(parameter)
            );
        } finally {
            transactionManager.rollback(transaction);
        }

        verify(javaMailSender, never()).send(any(MimeMessage.class));
        assertThat(emailRepository.findAll()).isEmpty();
    }

    @Test
    void givenNoActiveTransaction_sendAsync_shouldFail() {
        var email = getValidParameterWithoutAttachment().email();

        assertThatExceptionOfType(IllegalTransactionStateException.class).isThrownBy(
                () -> manageEmail.sendAsync(email)
        );

        verify(javaMailSender, never()).send(any(MimeMessage.class));
        assertThat(emailRepository.findAll()).isEmpty();
    }

    @Test
    void givenCommittingTransaction_sendAsync_shouldSendRightAfterCommit() throws EmailException {
        var transaction = transactionManager.getTransaction(TransactionDefinition.withDefaults());
        var result = manageEmail.sendAsync(getValidParameterWithoutAttachment().email());

        // Nothing leaves before the commit: the email is handed over only once the caller's data is committed
        assertThat(result.state()).isEqualTo(EmailState.PENDING);
        verify(javaMailSender, never()).send(any(MimeMessage.class));

        transactionManager.commit(transaction);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                                                                     assertThat(emailRepository.findById(result.id()))
                                                                             .hasValueSatisfying(email -> assertThat(
                                                                                     email.getState()).isEqualTo(
                                                                                     EmailState.SENT))
        );
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void givenRollingBackTransaction_sendAsync_shouldNeitherPersistNorSend() throws EmailException {
        var transaction = transactionManager.getTransaction(TransactionDefinition.withDefaults());
        var result = manageEmail.sendAsync(getValidParameterWithoutAttachment().email());
        transactionManager.rollback(transaction);

        assertThat(emailRepository.findById(result.id())).isEmpty();
        // The send would happen on another thread; make sure it does not merely happen later
        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                                                                                                   verify(
                                                                                                           javaMailSender,
                                                                                                           never()
                                                                                                   ).send(any(
                                                                                                           MimeMessage.class))
        );
    }

    @Test
    void givenMailSenderError_sendAsync_shouldLeaveEmailToTheScheduler() throws EmailException {
        doThrow(new MailSendException("any")).when(javaMailSender).send(any(MimeMessage.class));

        var result = sendAsyncInTransaction(getValidParameterWithoutAttachment().email());

        // One failed attempt, then back to PENDING with a backoff, exactly like a failed scheduler attempt
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                                                                     assertThat(emailRepository.findById(result.id())).hasValueSatisfying(
                                                                             email -> {
                                                                                 assertThat(email.getAttempts()).isEqualTo(
                                                                                         1);
                                                                                 assertThat(email.getState()).isEqualTo(
                                                                                         EmailState.PENDING);
                                                                                 assertThat(email.getScheduledAt()).isAfter(
                                                                                         OffsetDateTime.now());
                                                                                 assertThat(email.getErrorMessage()).isNotBlank();
                                                                             })
        );
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void givenAttachmentError_sendOrFail_shouldFail() throws EmailException, AttachmentException {
        when(attachmentDataSource.storeAttachmentPayload(any())).thenThrow(new AttachmentException());

        var parameter = getValidParameterWithAttachment();

        assertThatExceptionOfType(EmailException.class).isThrownBy(
                () -> manageEmail.sendOrFail(parameter)
        );
    }

    @Test
    void givenMailSenderError_sendOrFail_shouldFail() throws EmailException, AttachmentException {
        when(attachmentDataSource.storeAttachmentPayload(any())).thenReturn(33L);
        when(attachmentDataSource.getAttachmentPayload(anyLong())).thenReturn(new ByteArrayInputStream(new byte[0]));

        doThrow(new MailSendException("any")).when(javaMailSender).send(any(MimeMessage.class));

        var parameter = getValidParameterWithAttachment();

        assertThatExceptionOfType(EmailException.class).isThrownBy(
                () -> manageEmail.sendOrFail(parameter)
        );
    }

    // schedule() requires an active transaction (MANDATORY); run it in one and commit, as an application would.
    private EmailDto scheduleInTransaction(EmailParameter parameter) throws EmailException {
        var transaction = transactionManager.getTransaction(TransactionDefinition.withDefaults());
        try {
            var result = manageEmail.schedule(parameter);
            transactionManager.commit(transaction);
            return result;
        } catch (EmailException | RuntimeException e) {
            transactionManager.rollback(transaction);
            throw e;
        }
    }

    // sendAsync() requires an active transaction (MANDATORY); run it in one and commit, as an application would.
    private EmailDto sendAsyncInTransaction(EmailParameter.Email email) throws EmailException {
        var transaction = transactionManager.getTransaction(TransactionDefinition.withDefaults());
        try {
            var result = manageEmail.sendAsync(email);
            transactionManager.commit(transaction);
            return result;
        } catch (EmailException | RuntimeException e) {
            transactionManager.rollback(transaction);
            throw e;
        }
    }

    private static EmailParameter getValidParameterWithoutAttachment() {
        return EmailParameter.builder()
                .scheduledAt(OffsetDateTime.now())
                .email(EmailParameter.Email.builder()
                               .subject("Example email subject")
                               .textBody("Email body")
                               .htmlBody("<h1>Html email body</h1>")
                               .recipient("person1@example.com")
                               .recipient("person2@example.com")
                               .fromAddress("somebody@aboutbits.it")
                               .fromName("somebody")
                               .replyToAddress("somebodyElse@aboutbits.it")
                               .replyToName("somebodyElse")
                               .build()
                ).build();
    }

    private static EmailParameter getValidParameterWithAttachment() {
        return EmailParameter.builder()
                .scheduledAt(OffsetDateTime.now())
                .email(EmailParameter.Email.builder()
                               .subject("Example email subject")
                               .textBody("Email body")
                               .htmlBody("<h1>Html email body</h1>")
                               .recipient("person1@example.com")
                               .recipient("person2@example.com")
                               .attachment(
                                       EmailParameter.Email.Attachment.builder()
                                               .contentType("image/png")
                                               .fileName("x.png")
                                               .payload(new ByteArrayInputStream(new byte[0]))
                                               .build()
                               )
                               .fromAddress("somebody@aboutbits.it")
                               .fromName("somebody")
                               .replyToAddress("somebodyElse@aboutbits.it")
                               .replyToName("somebodyElse")
                               .build()
                ).build();
    }

    private static EmailParameter getValidParameterWithInlineAttachment(
            @Nullable String htmlBody,
            EmailParameter.Email.Attachment... attachments
    ) {
        var emailBuilder = EmailParameter.Email.builder()
                .subject("Example email subject")
                .textBody("Email body")
                .recipient("person1@example.com")
                .fromAddress("somebody@aboutbits.it")
                .fromName("somebody");

        if (htmlBody != null) {
            emailBuilder.htmlBody(htmlBody);
        }
        for (var attachment : attachments) {
            emailBuilder.attachment(attachment);
        }

        return EmailParameter.builder()
                .scheduledAt(OffsetDateTime.now())
                .email(emailBuilder.build())
                .build();
    }

    private static EmailParameter.Email.Attachment inlineAttachment(String contentId) {
        return inlineAttachment(contentId, "logo.png");
    }

    private static EmailParameter.Email.Attachment inlineAttachment(String contentId, String fileName) {
        return EmailParameter.Email.Attachment.builder()
                .contentType("image/png")
                .fileName(fileName)
                .contentId(contentId)
                .payload(new ByteArrayInputStream(new byte[]{1, 2, 3}))
                .build();
    }

    private static EmailParameter.Email.Attachment regularAttachment(String fileName) {
        return EmailParameter.Email.Attachment.builder()
                .contentType("image/png")
                .fileName(fileName)
                .payload(new ByteArrayInputStream(new byte[]{1, 2, 3}))
                .build();
    }

    private static List<MimeBodyPart> flattenParts(Object content) throws Exception {
        var parts = new ArrayList<MimeBodyPart>();
        if (content instanceof MimeMultipart multipart) {
            for (var i = 0; i < multipart.getCount(); i++) {
                var part = (MimeBodyPart) multipart.getBodyPart(i);
                var partContent = part.getContent();
                if (partContent instanceof MimeMultipart) {
                    parts.addAll(flattenParts(partContent));
                } else {
                    parts.add(part);
                }
            }
        }
        return parts;
    }

    private static boolean hasDisposition(MimeBodyPart part, String disposition) {
        try {
            return disposition.equalsIgnoreCase(part.getDisposition());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
