package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.exception.EmailException;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.support.database.WithPostgres;
import jakarta.mail.internet.MimeMessage;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "aboutbits.emailservice.async-dispatch.enabled=false")
@WithPostgres
@NullMarked
class SendAsyncEmailsTest {
    @MockitoSpyBean
    JavaMailSender javaMailSender;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    ManageEmail manageEmail;

    @Autowired
    EmailRepository emailRepository;

    @Autowired
    SendScheduledEmails sendScheduledEmails;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void setup() {
        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    @Test
    void givenAsyncDispatchDisabled_context_shouldNotCreateDispatcher() {
        assertThat(applicationContext.getBeanNamesForType(SendAsyncEmails.class)).isEmpty();
    }

    @Test
    void givenAsyncDispatchDisabled_sendAsync_shouldLeaveEmailToTheScheduler() throws EmailException {
        var transaction = transactionManager.getTransaction(TransactionDefinition.withDefaults());
        var result = manageEmail.sendAsync(validEmail());
        transactionManager.commit(transaction);

        // Nothing is dispatched after the commit
        assertThat(emailRepository.findById(result.id()))
                .hasValueSatisfying(email -> assertThat(email.getState()).isEqualTo(EmailState.PENDING));
        verify(javaMailSender, never()).send(any(MimeMessage.class));

        // The scheduler sends it on its next pass
        sendScheduledEmails.sendEmails();

        assertThat(emailRepository.findById(result.id()))
                .hasValueSatisfying(email -> assertThat(email.getState()).isEqualTo(EmailState.SENT));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    private static EmailParameter.Email validEmail() {
        return EmailParameter.Email.builder()
                .subject("Example email subject")
                .textBody("Email body")
                .htmlBody("<h1>Html email body</h1>")
                .recipient("person1@example.com")
                .fromAddress("somebody@aboutbits.it")
                .fromName("somebody")
                .build();
    }
}
