package it.aboutbits.springboot.emailservice;

import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.application.EmailParameter;
import it.aboutbits.springboot.emailservice.lib.application.ManageEmail;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import it.aboutbits.springboot.emailservice.lib.exception.EmailException;
import it.aboutbits.springboot.emailservice.support.database.WithPostgres;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest
@WithPostgres
@NullMarked
class EmailServiceConfigurationTest {
    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    ManageEmail manageEmail;

    @Test
    void givenNoAttachmentDataSourceBean_context_shouldStart() {
        assertThat(applicationContext.getBeanNamesForType(AttachmentDataSource.class)).isEmpty();
        assertThat(manageEmail).isNotNull();
    }

    @Test
    void givenNoAttachmentDataSourceBean_scheduleWithoutAttachment_shouldSucceed() throws EmailException {
        var result = manageEmail.schedule(getValidParameterWithoutAttachment());

        assertThat(result.id()).isPositive();
        assertThat(result.state()).isEqualTo(EmailState.PENDING);
    }

    @Test
    void givenNoAttachmentDataSourceBean_scheduleWithAttachment_shouldFail() {
        var parameter = getValidParameterWithAttachment();

        assertThatExceptionOfType(EmailException.class).isThrownBy(
                () -> manageEmail.schedule(parameter)
        ).withCauseInstanceOf(AttachmentException.class);
    }

    private static EmailParameter getValidParameterWithoutAttachment() {
        return EmailParameter.builder()
                .scheduledAt(OffsetDateTime.now())
                .email(EmailParameter.Email.builder()
                               .subject("Example email subject")
                               .textBody("Email body")
                               .htmlBody("<h1>Html email body</h1>")
                               .recipient("person1@example.com")
                               .fromAddress("somebody@aboutbits.it")
                               .fromName("somebody")
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
    }
}
