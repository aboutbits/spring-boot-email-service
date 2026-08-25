package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import it.aboutbits.springboot.emailservice.lib.exception.EmailException;
import it.aboutbits.springboot.emailservice.support.database.WithPostgres;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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

    @BeforeEach
    void setup() {
        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    @Test
    void givenRequiredParameters_schedule_shouldCreateNewNotification() throws EmailException {
        var parameter = getValidParameterWithoutAttachment();

        var result = manageEmail.schedule(parameter);

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

        var result = manageEmail.schedule(parameter);

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

        var parameter = EmailParameter.builder()
                .scheduledAt(OffsetDateTime.now())
                .email(EmailParameter.Email.builder()
                               .subject("Example email subject")
                               .textBody("Email body")
                               .htmlBody("<h1>Html email body</h1><img src=\"cid:header-logo\">")
                               .recipient("person1@example.com")
                               .attachment(
                                       EmailParameter.Email.Attachment.builder()
                                               .contentType("image/png")
                                               .fileName("logo.png")
                                               .contentId("header-logo")
                                               .payload(new ByteArrayInputStream(new byte[]{1, 2, 3}))
                                               .build()
                               )
                               .fromAddress("somebody@aboutbits.it")
                               .fromName("somebody")
                               .build()
                ).build();

        var result = manageEmail.schedule(parameter);

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

        var parameter = EmailParameter.builder()
                .scheduledAt(OffsetDateTime.now())
                .email(EmailParameter.Email.builder()
                               .subject("Example email subject")
                               .textBody("Email body")
                               .htmlBody("<h1>Html email body</h1><img src=\"cid:header-logo\">")
                               .recipient("person1@example.com")
                               .attachment(
                                       EmailParameter.Email.Attachment.builder()
                                               .contentType("image/png")
                                               .fileName("logo.png")
                                               .contentId("header-logo")
                                               .payload(new ByteArrayInputStream(new byte[]{1, 2, 3}))
                                               .build()
                               )
                               .attachment(
                                       EmailParameter.Email.Attachment.builder()
                                               .contentType("image/png")
                                               .fileName("x.png")
                                               .payload(new ByteArrayInputStream(new byte[]{1, 2, 3}))
                                               .build()
                               )
                               .fromAddress("somebody@aboutbits.it")
                               .fromName("somebody")
                               .build()
                ).build();

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

        var attachmentPart = parts.stream()
                .filter(part -> hasDisposition(part, Part.ATTACHMENT))
                .findFirst()
                .orElseThrow();
        assertThat(attachmentPart.getFileName()).isEqualTo("x.png");
    }

    @Test
    void givenInlineAttachmentWithoutHtmlBody_schedule_shouldFail() {
        var parameter = EmailParameter.builder()
                .scheduledAt(OffsetDateTime.now())
                .email(EmailParameter.Email.builder()
                               .subject("Example email subject")
                               .textBody("Email body")
                               .htmlBody("")
                               .recipient("person1@example.com")
                               .attachment(
                                       EmailParameter.Email.Attachment.builder()
                                               .contentType("image/png")
                                               .fileName("logo.png")
                                               .contentId("header-logo")
                                               .payload(new ByteArrayInputStream(new byte[0]))
                                               .build()
                               )
                               .fromAddress("somebody@aboutbits.it")
                               .fromName("somebody")
                               .build()
                ).build();

        assertThatExceptionOfType(ConstraintViolationException.class).isThrownBy(
                () -> manageEmail.schedule(parameter)
        );
    }

    @Test
    void givenContentIdNotReferencedInHtmlBody_schedule_shouldFail() {
        var parameter = EmailParameter.builder()
                .scheduledAt(OffsetDateTime.now())
                .email(EmailParameter.Email.builder()
                               .subject("Example email subject")
                               .textBody("Email body")
                               .htmlBody("<h1>Html email body</h1>")
                               .recipient("person1@example.com")
                               .attachment(
                                       EmailParameter.Email.Attachment.builder()
                                               .contentType("image/png")
                                               .fileName("logo.png")
                                               .contentId("header-logo")
                                               .payload(new ByteArrayInputStream(new byte[0]))
                                               .build()
                               )
                               .fromAddress("somebody@aboutbits.it")
                               .fromName("somebody")
                               .build()
                ).build();

        assertThatExceptionOfType(ConstraintViolationException.class).isThrownBy(
                () -> manageEmail.schedule(parameter)
        );
    }

    @Test
    void givenBlankContentId_schedule_shouldFail() {
        var parameter = EmailParameter.builder()
                .scheduledAt(OffsetDateTime.now())
                .email(EmailParameter.Email.builder()
                               .subject("Example email subject")
                               .textBody("Email body")
                               .htmlBody("<h1>Html email body</h1>")
                               .recipient("person1@example.com")
                               .attachment(
                                       EmailParameter.Email.Attachment.builder()
                                               .contentType("image/png")
                                               .fileName("logo.png")
                                               .contentId(" ")
                                               .payload(new ByteArrayInputStream(new byte[0]))
                                               .build()
                               )
                               .fromAddress("somebody@aboutbits.it")
                               .fromName("somebody")
                               .build()
                ).build();

        assertThatExceptionOfType(ConstraintViolationException.class).isThrownBy(
                () -> manageEmail.schedule(parameter)
        );
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
