package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.AttachmentCleanerCallback;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
@Log4j2
public class CleanupAttachmentFiles {
    private static final String JOB_DESCRIPTION = "Cleanup attachments of sent Emails.";

    private final QueryEmail queryEmail;
    private final ManageEmail manageEmail;
    private final List<AttachmentCleanerCallback> callbacks;

    private long lastInfoLogMillis = System.currentTimeMillis();
    private long silentRuns = 0;
    private boolean firstRun = true;

    @Scheduled(initialDelayString = "${aboutbits.emailservice.scheduling.interval:30000}", fixedDelayString = "${aboutbits.emailservice.scheduling.interval:30000}")
    void cleanupAttachments() {
        logStartOfPass();

        var emailsToCleanup = queryEmail.readyToCleanup();

        var countCleaned = 0;
        var countError = 0;
        for (var email : emailsToCleanup) {
            try {
                manageEmail.cleanupAttachments(email);
                countCleaned++;
            } catch (AttachmentException e) {
                countError++;
            }
        }

        logEndOfPass(countCleaned, countError);

        for (var callback : callbacks) {
            callback.report(new AttachmentCleanerCallback.Report(
                    emailsToCleanup.size(),
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
