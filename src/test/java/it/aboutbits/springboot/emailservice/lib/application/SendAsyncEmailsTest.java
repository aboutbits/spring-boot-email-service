package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.EmailDto;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.exception.EmailException;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.support.database.WithPostgres;
import jakarta.mail.internet.MimeMessage;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@NullMarked
class SendAsyncEmailsTest {
    @Test
    void givenNonPositiveThreads_constructor_shouldFail() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SendAsyncEmails(mock(ManageEmail.class), 0, 100)
        ).withMessageContaining("threads");
    }

    @Test
    void givenNonPositiveQueueCapacity_constructor_shouldFail() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SendAsyncEmails(mock(ManageEmail.class), 1, 0)
        ).withMessageContaining("queue-capacity");
    }

    @Test
    void givenSendsStillQueuedAfterTheGracePeriod_shutdown_shouldDropThemForTheScheduler() throws Exception {
        // Hold the worker inside its first claim, so the second send stays queued past a one-second grace period
        var release = new CountDownLatch(1);
        var manageEmail = mock(ManageEmail.class);
        when(manageEmail.tryClaimPendingForSend(anyLong())).thenAnswer(_ -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        });
        var dispatcher = new SendAsyncEmails(manageEmail, 1, 10, 1);

        try {
            dispatcher.onQueued(new AsyncEmailQueued(1L));
            dispatcher.onQueued(new AsyncEmailQueued(2L));

            dispatcher.shutdown();
        } finally {
            release.countDown();
        }

        // The queued send was dropped before it could claim its row; the row stays PENDING for the scheduler
        verify(manageEmail).tryClaimPendingForSend(1L);
        verify(manageEmail, never()).tryClaimPendingForSend(2L);
    }

    @Nested
    @SpringBootTest(properties = {
            "aboutbits.emailservice.async-dispatch.threads=1",
            "aboutbits.emailservice.async-dispatch.queue-capacity=1"
    })
    @WithPostgres
    class OnQueued {
        @MockitoSpyBean
        JavaMailSender javaMailSender;

        @Autowired
        ManageEmail manageEmail;

        @Autowired
        EmailRepository emailRepository;

        @Autowired
        SendScheduledEmails sendScheduledEmails;

        @Autowired
        PlatformTransactionManager transactionManager;

        @Test
        void givenFullQueue_sendAsync_shouldLeaveEmailToTheScheduler() throws Exception {
            // Hold the SMTP call open, so the single worker stays busy and the queue of one fills up
            var release = new CountDownLatch(1);
            doAnswer(_ -> {
                release.await(10, TimeUnit.SECONDS);
                return null;
            }).when(javaMailSender).send(any(MimeMessage.class));

            EmailDto inFlight;
            EmailDto queued;
            EmailDto rejected;
            try {
                inFlight = sendAsyncInTransaction(manageEmail, transactionManager);
                queued = sendAsyncInTransaction(manageEmail, transactionManager);
                rejected = sendAsyncInTransaction(manageEmail, transactionManager);

                await().atMost(Duration.ofSeconds(10)).untilAsserted(
                        () -> assertThat(stateOf(emailRepository, inFlight)).isEqualTo(EmailState.SENDING)
                );
                assertThat(stateOf(emailRepository, queued)).isEqualTo(EmailState.PENDING);
                assertThat(stateOf(emailRepository, rejected)).isEqualTo(EmailState.PENDING);
            } finally {
                release.countDown();
            }

            // The worker drains what it holds ...
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(stateOf(emailRepository, inFlight)).isEqualTo(EmailState.SENT);
                assertThat(stateOf(emailRepository, queued)).isEqualTo(EmailState.SENT);
            });
            // ... but the rejected hand-off is gone for good: nothing dispatches that email
            assertThat(stateOf(emailRepository, rejected)).isEqualTo(EmailState.PENDING);
            verify(javaMailSender, times(2)).send(any(MimeMessage.class));

            // The scheduler sends it on its next pass
            sendScheduledEmails.sendEmails();

            assertThat(stateOf(emailRepository, rejected)).isEqualTo(EmailState.SENT);
            verify(javaMailSender, times(3)).send(any(MimeMessage.class));
        }
    }

    // sendAsync() requires an active transaction (MANDATORY); run it in one and commit, as an application would.
    private static EmailDto sendAsyncInTransaction(
            ManageEmail manageEmail,
            PlatformTransactionManager transactionManager
    ) throws EmailException {
        var transaction = transactionManager.getTransaction(TransactionDefinition.withDefaults());
        try {
            var result = manageEmail.sendAsync(validEmail());
            transactionManager.commit(transaction);
            return result;
        } catch (EmailException | RuntimeException e) {
            transactionManager.rollback(transaction);
            throw e;
        }
    }

    private static EmailState stateOf(EmailRepository emailRepository, EmailDto email) {
        return emailRepository.findById(email.id()).orElseThrow().getState();
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
