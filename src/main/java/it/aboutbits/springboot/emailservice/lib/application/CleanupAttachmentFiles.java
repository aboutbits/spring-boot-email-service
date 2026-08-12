package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.AttachmentCleanerCallback;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Log4j2
@NullMarked
public class CleanupAttachmentFiles {
    private static final String JOB_DESCRIPTION = "Cleanup attachments of sent Emails.";

    private final QueryEmail queryEmail;
    private final ManageEmail manageEmail;
    private final List<AttachmentCleanerCallback> callbacks;
    private final int batchSize;

    private long lastInfoLogMillis = System.currentTimeMillis();
    private long silentRuns = 0;
    private boolean firstRun = true;

    public CleanupAttachmentFiles(
            QueryEmail queryEmail,
            ManageEmail manageEmail,
            List<AttachmentCleanerCallback> callbacks,
            int batchSize
    ) {
        this.queryEmail = queryEmail;
        this.manageEmail = manageEmail;
        this.callbacks = callbacks;
        this.batchSize = batchSize;
    }

    @Scheduled(initialDelayString = "${aboutbits.emailservice.scheduling.interval:30000}", fixedDelayString = "${aboutbits.emailservice.scheduling.interval:30000}")
    @Transactional
    void claimAndCleanupAttachments() {
        logStartOfPass();

        var ids = queryEmail.claimReadyToCleanupIds(batchSize);
        var outcome = manageEmail.cleanupBatch(ids);

        logEndOfPass(outcome.cleaned(), outcome.errors());

        for (var callback : callbacks) {
            callback.report(new AttachmentCleanerCallback.Report(
                    outcome.total(),
                    outcome.cleaned(),
                    outcome.errors()
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
