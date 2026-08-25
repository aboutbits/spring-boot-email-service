package it.aboutbits.springboot.emailservice.lib;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.function.Supplier;

/*
 * Sink for everything the schedulers learn while running. Implemented by the library against
 * Micrometer when the consuming application provides a MeterRegistry, and by a no-op otherwise,
 * so that neither the schedulers nor their callers need to know whether metrics are enabled.
 */
@NullMarked
public interface EmailMetrics {
    void sendAttempt(SendMode mode, SendOutcome outcome, Duration duration);

    void cleanupAttempt(CleanupOutcome outcome, Duration duration);

    void pass(Job job, PassStatus status, Duration duration);

    // Takes a supplier because the snapshot costs database queries: a sink that has no backend to
    // record into is expected to never read it.
    void queue(Supplier<QueueSnapshot> snapshot);

    /*
     * Runs a whole scheduler pass and records it, so that a database outage - the one failure the pass
     * itself cannot report per email - still shows up as a failed pass before it propagates to the
     * scheduler. Recorded in a finally block on purpose: an Error is a fired pass as much as an
     * exception is, and last_run is defined as the time the scheduler last fired.
     */
    default void timedPass(Job job, Runnable pass) {
        var startNanos = System.nanoTime();
        var status = PassStatus.FAILED;

        try {
            pass.run();
            status = PassStatus.SUCCESS;
        } finally {
            pass(job, status, Duration.ofNanos(System.nanoTime() - startNanos));
        }
    }

    enum SendMode {
        SCHEDULED,
        DIRECT
    }

    enum SendOutcome {
        SENT,
        // The attempt failed but the email is scheduled for another one, so it is not an error yet.
        RETRY,
        ERROR
    }

    enum CleanupOutcome {
        CLEANED,
        ERROR
    }

    enum Job {
        SEND,
        CLEANUP
    }

    enum PassStatus {
        SUCCESS,
        FAILED
    }

    /*
     * State of the queue as a whole, read once per scheduler pass. Since every pod reads the same
     * rows, the resulting series are identical across pods and must be aggregated with max(), not sum().
     *
     * Only the two states an email passes through are counted. SENT and ERROR are terminal, so their
     * counts only ever grow and say nothing about how the queue is doing right now; how many emails
     * end in an error is already answered by the outcome tag of the send attempts.
     */
    record QueueSnapshot(
            long pending,
            long sending,
            // How long the oldest email that is already due has been waiting; null if nothing is due.
            @Nullable Duration oldestDueAge
    ) {
    }
}
