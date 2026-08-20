package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.AttachmentCleanerCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@RequiredArgsConstructor
@Log4j2
@NullMarked
public class CleanupAttachmentFiles {
    private static final String JOB_DESCRIPTION = "Cleanup attachments of sent Emails.";

    private final QueryEmail queryEmail;
    private final ManageEmail manageEmail;
    private final List<AttachmentCleanerCallback> callbacks;
    private final Duration stuckCleanupRecoveryThreshold;

    private long lastInfoLogMillis = System.currentTimeMillis();
    private long silentRuns = 0;
    private boolean firstRun = true;

    @Scheduled(initialDelayString = "${aboutbits.emailservice.scheduling.interval:30000}", fixedDelayString = "${aboutbits.emailservice.scheduling.interval:30000}")
    void cleanupAttachments() {
        logStartOfPass();

        var staleCleanupBefore = OffsetDateTime.now().minus(stuckCleanupRecoveryThreshold);
        var candidateIds = queryEmail.candidateIdsToCleanup(staleCleanupBefore);

        var countClaimed = 0;
        var countCleaned = 0;
        var countError = 0;
        for (var id : candidateIds) {
            var claimed = manageEmail.tryClaimForCleanup(id, staleCleanupBefore);

            if (claimed.isEmpty()) {
                // Lost race to another pod; Skip
                continue;
            }
            countClaimed++;

            try {
                manageEmail.completeClaimedCleanup(claimed.get());
                countCleaned++;
            } catch (Exception _) {
                countError++;
            }
        }

        logEndOfPass(countCleaned, countError);

        for (var callback : callbacks) {
            callback.report(new AttachmentCleanerCallback.Report(
                    countClaimed,
                    countCleaned,
                    countError
            ));
        }
    }

    private void logStartOfPass() {
        if (firstRun) {
            log.info(JOB_DESCRIPTION + " | Job enabled.");
            firstRun = false;
        }
        log.debug(JOB_DESCRIPTION + " | Start");
    }

    private void logEndOfPass(int countCleaned, int countError) {
        log.debug(JOB_DESCRIPTION + " | Finished");
        if (countCleaned > 0 || countError > 0) {
            lastInfoLogMillis = System.currentTimeMillis();
            silentRuns = 0;
            log.info(JOB_DESCRIPTION + " | Cleaned: {}, Errors: {}", countCleaned, countError);
        } else {
            log.debug(JOB_DESCRIPTION + " | Cleaned: {}, Errors: {}", countCleaned, countError);
            silentRuns++;
        }

        if (lastInfoLogMillis + Duration.ofHours(1).toMillis() < System.currentTimeMillis() && !log.isDebugEnabled()) {
            log.info(
                    JOB_DESCRIPTION + " | Ran silently {} times. Enable debug logging to see all hidden passes.",
                    silentRuns
            );
            lastInfoLogMillis = System.currentTimeMillis();
            silentRuns = 0;
        }
    }
}
