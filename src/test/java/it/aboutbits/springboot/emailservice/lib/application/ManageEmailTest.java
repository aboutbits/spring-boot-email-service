package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.AttachmentReference;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.support.database.WithPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@WithPostgres
class ManageEmailTest {
    @MockBean
    JavaMailSender javaMailSender;

    @Autowired
    private ManageEmail manageEmail;

    @Test
    void givenRequiredParameters_schedule_shouldCreateNewNotification() {
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
    void givenRequiredParameterWithAttachedFiles_schedule_shouldCreateNewNotification() {
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
                                        .reference(new AttachmentReference("something"))
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
