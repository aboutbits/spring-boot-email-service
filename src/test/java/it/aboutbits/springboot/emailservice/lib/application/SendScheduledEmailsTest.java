package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import it.aboutbits.springboot.emailservice.support.database.WithPostgres;
import it.aboutbits.springboot.emailservice.support.database.factory.EmailFactory;
import jakarta.mail.internet.MimeMessage;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "aboutbits.emailservice.scheduling.batch-size=5")
@WithPostgres
@NullMarked
class SendScheduledEmailsTest {
    private static final int BATCH_SIZE = 5;

    @MockitoSpyBean
    JavaMailSender javaMailSender;

    @Autowired
    EmailRepository emailRepository;

    @Autowired
    SendScheduledEmails sendScheduledEmails;

    @BeforeEach
    void setup() {
        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    @Test
    void givenPendingEmails_claimAndSendEmails_shouldMarkAllAsSent() {
        emailRepository.saveAll(EmailFactory.many(3).stream().map(Email.EmailBuilder::build).toList());

        sendScheduledEmails.claimAndSendEmails();

        assertThat(emailRepository.findAll())
                .hasSize(3)
                .allMatch(email -> email.getState() == EmailState.SENT);
        verify(javaMailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void givenMoreEmailsThanBatchSize_claimAndSendEmails_shouldSendOnlyBatchSizePerPass() {
        var pending = 3 * BATCH_SIZE;
        emailRepository.saveAll(EmailFactory.many(pending).stream().map(Email.EmailBuilder::build).toList());

        sendScheduledEmails.claimAndSendEmails();

        assertThat(emailRepository.findAll())
                .filteredOn(email -> email.getState() == EmailState.SENT)
                .hasSize(BATCH_SIZE);
        verify(javaMailSender, times(BATCH_SIZE)).send(any(MimeMessage.class));
    }

    @Test
    void givenPreviouslyFailedEmail_claimAndSendEmails_shouldRetryAndMarkSent() {
        var errored = EmailFactory.once().state(EmailState.ERROR).errorMessage("some SMTP hiccup").build();
        emailRepository.save(errored);

        sendScheduledEmails.claimAndSendEmails();

        assertThat(emailRepository.findAll())
                .singleElement()
                .satisfies(email -> {
                    assertThat(email.getState()).isEqualTo(EmailState.SENT);
                    assertThat(email.getSentAt()).isNotNull();
                    assertThat(email.getErrorMessage()).isEmpty();
                });
    }

    @Test
    void givenMailSenderThrows_claimAndSendEmails_shouldMarkEmailAsErrorAndPersistMessage() {
        doThrow(new MailSendException("smtp down")).when(javaMailSender).send(any(MimeMessage.class));
        emailRepository.save(EmailFactory.once().build());

        sendScheduledEmails.claimAndSendEmails();

        assertThat(emailRepository.findAll())
                .singleElement()
                .satisfies(email -> {
                    assertThat(email.getState()).isEqualTo(EmailState.ERROR);
                    assertThat(email.getErrorMessage()).contains("smtp down");
                });
    }

    @Test
    void givenConcurrentPasses_claimAndSendEmails_shouldSendEachEmailExactlyOnce() throws Exception {
        var totalEmails = 25;
        var workerThreads = 4;
        var passesPerWorker = 10;
        emailRepository.saveAll(EmailFactory.many(totalEmails).stream().map(Email.EmailBuilder::build).toList());

        var startGate = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(workerThreads);
        try {
            for (var t = 0; t < workerThreads; t++) {
                executor.submit(() -> {
                    startGate.await();
                    for (var i = 0; i < passesPerWorker; i++) {
                        sendScheduledEmails.claimAndSendEmails();
                    }
                    return null;
                });
            }
            startGate.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            if (!executor.isTerminated()) {
                executor.shutdownNow();
            }
        }

        assertThat(emailRepository.findAll())
                .hasSize(totalEmails)
                .allMatch(email -> email.getState() == EmailState.SENT);
        verify(javaMailSender, times(totalEmails)).send(any(MimeMessage.class));
    }
}
