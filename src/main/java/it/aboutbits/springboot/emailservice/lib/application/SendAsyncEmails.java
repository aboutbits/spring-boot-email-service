package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

/**
 * Sends emails queued via {@link ManageEmail#sendAsync} right after the caller's transaction committed.
 * <p>
 * The hand-off happens on the caller's thread in the after-commit phase and only enqueues the email id; the SMTP
 * round-trip runs on a bounded executor owned by this class. Every path that loses an email (saturated executor,
 * lost claim, failed send, pod restart) leaves the row in a state the scheduler picks up, and the dispatch claims the
 * row with the same kind of atomic update as the scheduler, so an email is never sent twice.
 */
@Slf4j
@NullMarked
public class SendAsyncEmails {
    private static final String JOB_DESCRIPTION = "Sending emails after commit.";
    private static final int AWAIT_TERMINATION_SECONDS = 30;

    private final ManageEmail manageEmail;
    private final ThreadPoolTaskExecutor executor;
    private final int awaitTerminationSeconds;

    public SendAsyncEmails(ManageEmail manageEmail, int threads, int queueCapacity) {
        this(manageEmail, threads, queueCapacity, AWAIT_TERMINATION_SECONDS);
    }

    // Visible for tests, which use a short grace period on shutdown.
    SendAsyncEmails(ManageEmail manageEmail, int threads, int queueCapacity, int awaitTerminationSeconds) {
        if (threads < 1) {
            throw new IllegalArgumentException("async-dispatch.threads must be at least 1, got " + threads);
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("async-dispatch.queue-capacity must be at least 1, got " + queueCapacity);
        }
        this.manageEmail = manageEmail;
        this.awaitTerminationSeconds = awaitTerminationSeconds;

        // Bounded on purpose: when saturated, the hand-off is rejected and the scheduler sends the email instead.
        this.executor = new ThreadPoolTaskExecutor();
        this.executor.setCorePoolSize(threads);
        this.executor.setMaxPoolSize(threads);
        this.executor.setQueueCapacity(queueCapacity);
        this.executor.setThreadNamePrefix("email-async-");
        // Carry the caller's MDC onto the worker, so the send can be correlated with
        // the request that queued the email.
        this.executor.setTaskDecorator(task -> {
            var context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        });
        this.executor.setWaitForTasksToCompleteOnShutdown(true);
        this.executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        this.executor.initialize();
    }

    // Runs on the caller's thread once its transaction has committed. The commit is done at this point, so this
    // must never throw and must never block: it only hands the id over.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onQueued(AsyncEmailQueued event) {
        var id = event.emailId();
        try {
            executor.execute(() -> send(id));
        } catch (RejectedExecutionException e) {
            log.warn(
                    JOB_DESCRIPTION + " | Executor saturated; email {} will be sent by the scheduler instead.",
                    id
            );
        } catch (RuntimeException e) {
            log.error(
                    JOB_DESCRIPTION + " | Failed to hand over email {}; it will be sent by the scheduler instead.",
                    id,
                    e
            );
        }
    }

    private void send(long id) {
        // First attempt only: if this task was delayed and the scheduler got there first, there is nothing to do.
        Optional<Email> claimed;
        try {
            claimed = manageEmail.tryClaimPendingForSend(id);
        } catch (RuntimeException e) {
            log.warn(JOB_DESCRIPTION + " | Failed to claim email {}; it will be sent by the scheduler instead.", id, e);
            return;
        }

        if (claimed.isEmpty()) {
            // The scheduler (or another pod) got there first; nothing to do.
            log.debug(JOB_DESCRIPTION + " | Email {} was already claimed elsewhere.", id);
            return;
        }

        try {
            var updated = manageEmail.completeClaimedSend(claimed.get());
            if (updated.getState() == EmailState.SENT) {
                log.info(JOB_DESCRIPTION + " | Email {} sent.", id);
            } else {
                log.debug(JOB_DESCRIPTION + " | Email {} finished in state {}.", id, updated.getState());
            }
        } catch (Exception e) {
            // Best-effort visibility only: we cannot reliably recover here. The email may already have been
            // sent but persisting the result failed, so the row stays in SENDING and will be re-claimed and
            // re-sent after the recovery threshold (possible duplicate delivery).
            log.error(
                    JOB_DESCRIPTION + " | Failed to complete send for email: {}. It may have been sent already; "
                            + "if the result could not be persisted the row stays in SENDING and will be re-sent "
                            + "after the recovery threshold (possible duplicate delivery).",
                    id,
                    e
            );
        }
    }

    // Lets in-flight and queued sends finish, bounded by the grace period. Queued sends that have not started by then
    // are dropped on purpose: they stay PENDING and the scheduler sends them after the restart.
    public void shutdown() {
        executor.shutdown();

        var pool = executor.getThreadPoolExecutor();
        if (!pool.isTerminated()) {
            var dropped = pool.shutdownNow();
            log.warn(
                    JOB_DESCRIPTION + " | {} queued send(s) not started within {}s were dropped and stay PENDING for "
                            + "the scheduler. A send still in flight finishes if it can; otherwise its row stays "
                            + "SENDING until the recovery threshold.",
                    dropped.size(),
                    awaitTerminationSeconds
            );
        }
    }
}
