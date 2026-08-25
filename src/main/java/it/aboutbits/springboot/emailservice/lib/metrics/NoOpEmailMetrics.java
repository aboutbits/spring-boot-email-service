package it.aboutbits.springboot.emailservice.lib.metrics;

import it.aboutbits.springboot.emailservice.lib.EmailMetrics;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.util.function.Supplier;

/*
 * Used when the consuming application has no MeterRegistry. Recording stays a call into an
 * empty method, so the schedulers can instrument themselves unconditionally.
 */
@NullMarked
public class NoOpEmailMetrics implements EmailMetrics {
    @Override
    public void sendAttempt(SendMode mode, SendOutcome outcome, Duration duration) {
        // no metrics backend available
    }

    @Override
    public void cleanupAttempt(CleanupOutcome outcome, Duration duration) {
        // no metrics backend available
    }

    @Override
    public void pass(Job job, PassStatus status, Duration duration) {
        // no metrics backend available
    }

    @Override
    public void queue(Supplier<QueueSnapshot> snapshot) {
        // no metrics backend available, so the snapshot is deliberately never read
    }
}
