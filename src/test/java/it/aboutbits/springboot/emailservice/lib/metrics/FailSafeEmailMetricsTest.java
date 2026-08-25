package it.aboutbits.springboot.emailservice.lib.metrics;

import it.aboutbits.springboot.emailservice.lib.EmailMetrics;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@NullMarked
class FailSafeEmailMetricsTest {
    private final EmailMetrics delegate = mock(EmailMetrics.class);
    private final FailSafeEmailMetrics sut = new FailSafeEmailMetrics(delegate);

    @Test
    void givenAThrowingDelegate_sendAttempt_shouldNotPropagateTheFailure() {
        doThrow(new IllegalStateException("registry rejected the meter"))
                .when(delegate).sendAttempt(any(), any(), any());

        assertThatCode(() -> sut.sendAttempt(
                EmailMetrics.SendMode.SCHEDULED,
                EmailMetrics.SendOutcome.SENT,
                Duration.ofMillis(10)
        )).doesNotThrowAnyException();
    }

    @Test
    void givenAFailingSnapshot_queue_shouldNotPropagateTheFailure() {
        doThrow(new IllegalStateException("database unreachable"))
                .when(delegate).queue(any());

        assertThatCode(() -> sut.queue(() -> new EmailMetrics.QueueSnapshot(0, 0, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void givenAWorkingDelegate_queue_shouldPassTheSnapshotThroughUnread() {
        Supplier<EmailMetrics.QueueSnapshot> snapshot = () -> new EmailMetrics.QueueSnapshot(1, 2, null);

        sut.queue(snapshot);

        verify(delegate).queue(snapshot);
    }

    @Test
    void givenAFailingPass_timedPass_shouldRecordTheFailureAndStillPropagateIt() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(
                () -> sut.timedPass(EmailMetrics.Job.SEND, () -> {
                    throw new IllegalStateException("database unreachable");
                })
        );

        verify(delegate).pass(eq(EmailMetrics.Job.SEND), eq(EmailMetrics.PassStatus.FAILED), any());
    }

    @Test
    void givenAPassThatGetsThrough_timedPass_shouldRecordASuccess() {
        var passes = new int[1];

        sut.timedPass(EmailMetrics.Job.CLEANUP, () -> passes[0]++);

        assertThat(passes[0]).isEqualTo(1);
        verify(delegate).pass(eq(EmailMetrics.Job.CLEANUP), eq(EmailMetrics.PassStatus.SUCCESS), any());
    }
}
