package it.aboutbits.springboot.emailservice.lib.metrics;

import it.aboutbits.springboot.emailservice.lib.EmailMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.util.function.Supplier;

/*
 * Keeps a failing sink from failing the work it observes. A registry configured to throw on
 * registration failures, a meter filter rejecting a tag, or a bug in an application-provided
 * EmailMetrics must never turn into an email that was delivered but not persisted as SENT, or into a
 * scheduler pass that is reported as failed although every send in it went through. Recording is
 * best-effort, so a failure is logged and the reading dropped.
 */
@RequiredArgsConstructor
@Slf4j
@NullMarked
public class FailSafeEmailMetrics implements EmailMetrics {
    private final EmailMetrics delegate;

    @Override
    public void sendAttempt(SendMode mode, SendOutcome outcome, Duration duration) {
        record("send attempt", () -> delegate.sendAttempt(mode, outcome, duration));
    }

    @Override
    public void cleanupAttempt(CleanupOutcome outcome, Duration duration) {
        record("cleanup attempt", () -> delegate.cleanupAttempt(outcome, duration));
    }

    @Override
    public void pass(Job job, PassStatus status, Duration duration) {
        record("pass", () -> delegate.pass(job, status, duration));
    }

    // Also covers the snapshot supplier: reading the queue is only ever done for the metrics, so a
    // database error while reading it is a lost reading, not a failed pass.
    @Override
    public void queue(Supplier<QueueSnapshot> snapshot) {
        record("queue snapshot", () -> delegate.queue(snapshot));
    }

    private static void record(String reading, Runnable recording) {
        try {
            recording.run();
        } catch (RuntimeException e) {
            log.warn("Recording the {} metric failed; the reading is dropped.", reading, e);
        }
    }
}
