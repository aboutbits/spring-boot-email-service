package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
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

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "aboutbits.emailservice.scheduling.max-attempts=3",
        "aboutbits.emailservice.scheduling.stuck-sending-recovery-threshold=PT5M",
        "aboutbits.emailservice.scheduling.interval=30000"
})
@WithPostgres
@NullMarked
class SendScheduledEmailsTest {
    private static final int MAX_ATTEMPTS = 3;
    private static final int SCHEDULER_INTERVAL_SECONDS = 30;

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
    void givenPendingEmails_sendEmails_shouldMarkAllAsSent() {
        emailRepository.saveAll(IntStream.range(0, 3).mapToObj(_ -> EmailFactory.once().build()).toList());

        sendScheduledEmails.sendEmails();

        assertThat(emailRepository.findAll())
                .hasSize(3)
                .allMatch(email -> email.getState() == EmailState.SENT)
                .allMatch(email -> email.getExecutionStartTime() != null)
                .allMatch(email -> email.getExecutionEndTime() != null)
                .allMatch(email -> email.getAttempts() == 1);
        verify(javaMailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void givenRetryableFailure_sendEmails_shouldRescheduleWithBackoffAndIncrementAttempts() {
        doThrow(new MailSendException("smtp blip")).when(javaMailSender).send(any(MimeMessage.class));
        emailRepository.save(EmailFactory.once().build());

        var beforePass = OffsetDateTime.now();
        sendScheduledEmails.sendEmails();

        assertThat(emailRepository.findAll())
                .singleElement()
                .satisfies(email -> {
                    assertThat(email.getState()).isEqualTo(EmailState.PENDING);
                    assertThat(email.getAttempts()).isEqualTo(1);
                    // First retry backoff: 2^attempts * scheduler interval = 2 * scheduler interval
                    assertThat(email.getScheduledAt())
                            .isAfterOrEqualTo(beforePass.plusSeconds(2L * SCHEDULER_INTERVAL_SECONDS));
                    assertThat(email.getErrorMessage()).contains("smtp blip");
                    assertThat(email.getExecutionEndTime()).isNotNull();
                });
    }

    @Test
    void givenAttemptsAlreadyAtBudget_sendEmails_shouldEscalateToError() {
        doThrow(new MailSendException("smtp down")).when(javaMailSender).send(any(MimeMessage.class));
        emailRepository.save(
                EmailFactory.once()
                        .attempts(MAX_ATTEMPTS - 1)
                        .scheduledAt(OffsetDateTime.now().minusSeconds(30))
                        .build()
        );

        sendScheduledEmails.sendEmails();

        assertThat(emailRepository.findAll())
                .singleElement()
                .satisfies(email -> {
                    // The atomic claim UPDATE incremented attempts from MAX_ATTEMPTS-1 to MAX_ATTEMPTS,
                    // which is equal to the threshold, so the failure escalates to ERROR.
                    assertThat(email.getState()).isEqualTo(EmailState.ERROR);
                    assertThat(email.getAttempts()).isEqualTo(MAX_ATTEMPTS);
                    assertThat(email.getErrorMessage()).contains("smtp down");
                });
    }

    @Test
    void givenErrorRow_sendEmails_shouldNotRetry() {
        emailRepository.save(
                EmailFactory.once()
                        .state(EmailState.ERROR)
                        .attempts(MAX_ATTEMPTS)
                        .scheduledAt(OffsetDateTime.now().minusMinutes(1))
                        .errorMessage("previous permanent failure")
                        .build()
        );

        sendScheduledEmails.sendEmails();

        assertThat(emailRepository.findAll())
                .singleElement()
                .satisfies(email -> {
                    assertThat(email.getState()).isEqualTo(EmailState.ERROR);
                    assertThat(email.getAttempts()).isEqualTo(MAX_ATTEMPTS);
                });
        verify(javaMailSender, times(0)).send(any(MimeMessage.class));
    }

    @Test
    void givenStuckSendingRow_sendEmails_shouldRecoverAndResend() {
        var staleStart = OffsetDateTime.now().minusMinutes(10);
        emailRepository.save(
                EmailFactory.once()
                        .state(EmailState.SENDING)
                        .attempts(1)
                        .executionStartTime(staleStart)
                        .scheduledAt(OffsetDateTime.now().minusSeconds(60))
                        .build()
        );

        sendScheduledEmails.sendEmails();

        assertThat(emailRepository.findAll())
                .singleElement()
                .satisfies(email -> {
                    assertThat(email.getState()).isEqualTo(EmailState.SENT);
                    assertThat(email.getAttempts()).isEqualTo(2);
                    assertThat(email.getExecutionStartTime()).isAfter(staleStart);
                });
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void givenFreshSendingRowWithinThreshold_sendEmails_shouldNotStealFromOtherPod() {
        // executionStartTime within the 5-minute threshold means another pod is legitimately
        // sending this right now; we must not re-claim it.
        var recentStart = OffsetDateTime.now().minusSeconds(30).truncatedTo(ChronoUnit.MICROS);
        emailRepository.save(
                EmailFactory.once()
                        .state(EmailState.SENDING)
                        .attempts(1)
                        .executionStartTime(recentStart)
                        .scheduledAt(OffsetDateTime.now().minusSeconds(60))
                        .build()
        );

        sendScheduledEmails.sendEmails();

        assertThat(emailRepository.findAll())
                .singleElement()
                .satisfies(email -> {
                    assertThat(email.getState()).isEqualTo(EmailState.SENDING);
                    assertThat(email.getAttempts()).isEqualTo(1);
                    assertThat(email.getExecutionStartTime()).isEqualTo(recentStart);
                });
        verify(javaMailSender, times(0)).send(any(MimeMessage.class));
    }

    @Test
    void givenConcurrentPasses_sendEmails_shouldSendEachEmailExactlyOnce() throws Exception {
        var totalEmails = 25;
        var workerThreads = 4;
        var passesPerWorker = 10;
        emailRepository.saveAll(IntStream.range(0, totalEmails).mapToObj(_ -> EmailFactory.once().build()).toList());

        var startGate = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(workerThreads);
        try {
            for (var t = 0; t < workerThreads; t++) {
                executor.submit(() -> {
                    startGate.await();
                    for (var i = 0; i < passesPerWorker; i++) {
                        sendScheduledEmails.sendEmails();
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
                .allMatch(email -> email.getState() == EmailState.SENT)
                .allMatch(email -> email.getAttempts() == 1);
        verify(javaMailSender, times(totalEmails)).send(any(MimeMessage.class));
    }
}
