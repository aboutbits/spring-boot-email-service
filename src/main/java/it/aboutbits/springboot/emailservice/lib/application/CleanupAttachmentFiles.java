package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.AttachmentCleanerCallback;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

@AllArgsConstructor
@Log4j2
public class CleanupAttachmentFiles {
    private static final String JOB_DESCRIPTION = "Cleanup attachments of sent Emails.";

    private final QueryEmail queryEmail;
    private final ManageEmail manageEmail;
    private final List<AttachmentCleanerCallback> callbacks;

    @Scheduled(initialDelayString = "${aboutbits.emailservice.scheduling.interval:30000}", fixedDelayString = "${aboutbits.emailservice.scheduling.interval:30000}")
    void cleanupAttachments() {
        log.info("Start: " + JOB_DESCRIPTION);

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

        log.info("Finished: " + JOB_DESCRIPTION);
        log.info("Cleaned: {}, Errors: {}", countCleaned, countError);

        for (var callback : callbacks) {
            callback.report(new AttachmentCleanerCallback.Report(
                    emailsToCleanup.size(),
                    countCleaned,
                    countError
            ));
        }
    }


}
