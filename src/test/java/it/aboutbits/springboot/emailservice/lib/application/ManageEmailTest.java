package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.AttachmentReference;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import it.aboutbits.springboot.emailservice.lib.exception.EmailException;
import it.aboutbits.springboot.emailservice.support.database.WithPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@WithPostgres
class ManageEmailTest {
    @MockBean
    JavaMailSender javaMailSender;

    @MockBean
    AttachmentDataSource attachmentDataSource;

    @Autowired
    private ManageEmail manageEmail;

    @Test
    void givenRequiredParameters_schedule_shouldCreateNewNotification() throws EmailException {
        var parameter = EmailParameter.builder()
                .scheduledAt(OffsetDateTime.now())
                .email(EmailParameter.Email.builder()
                        .subject("Example email subject")
                        .textBody("Email body")
                        .htmlBody("<h1>Html email body</h1>")
                        .recipient("person1@example.com")
                        .recipient("person2@example.com")
                        .fromAddress("somebody@aboutbits.it")
                        .fromName("somebody")
                        .build()
                ).build();

        var result = manageEmail.schedule(parameter);

        assertThat(result.id()).isPositive();
        assertThat(result.state()).isEqualTo(EmailState.PENDING);
        assertThat(result.subject()).isEqualTo(parameter.email().subject());
        assertThat(result.fromAddress()).isEqualTo(parameter.email().fromAddress());
        assertThat(result.fromName()).isEqualTo(parameter.email().fromName());
        assertThat(result.recipients()).containsAll(parameter.email().recipients());
        assertThat(result.textBody()).isEqualTo(parameter.email().textBody());
        assertThat(result.htmlBody()).isEqualTo(parameter.email().htmlBody());
        assertThat(result.attachments()).isEmpty();
        assertThat(result.scheduledAt()).isEqualTo(parameter.scheduledAt());
    }

    @Test
    void givenRequiredParameterWithAttachedFiles_schedule_shouldCreateNewNotification() throws EmailException, AttachmentException {
        when(attachmentDataSource.storeAttachmentPayload(any())).thenReturn(new AttachmentReference("ref"));

        var parameter = EmailParameter.builder()
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
                        .build()
                ).build();

        var result = manageEmail.schedule(parameter);

        assertThat(result.id()).isPositive();
        assertThat(result.state()).isEqualTo(EmailState.PENDING);
        assertThat(result.subject()).isEqualTo(parameter.email().subject());
        assertThat(result.fromAddress()).isEqualTo(parameter.email().fromAddress());
        assertThat(result.fromName()).isEqualTo(parameter.email().fromName());
        assertThat(result.recipients()).containsAll(parameter.email().recipients());
        assertThat(result.textBody()).isEqualTo(parameter.email().textBody());
        assertThat(result.htmlBody()).isEqualTo(parameter.email().htmlBody());
        assertThat(result.attachments()).hasSize(1);
        assertThat(result.scheduledAt()).isEqualTo(parameter.scheduledAt());
    }
}
