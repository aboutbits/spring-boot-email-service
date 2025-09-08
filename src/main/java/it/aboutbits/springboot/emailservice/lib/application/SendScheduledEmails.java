package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.EmailSchedulerCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
@Log4j2
public class SendScheduledEmails {
    private static final String JOB_DESCRIPTION = "Sending open and failed email notifications.";

    private final QueryEmail queryEmail;
    private final ManageEmail manageEmail;
    private final List<EmailSchedulerCallback> callbacks;

    private long lastInfoLogMillis = System.currentTimeMillis();
    private long silentRuns = 0;
    private boolean firstRun = true;

    @Scheduled(initialDelayString = "${aboutbits.emailservice.scheduling.interval:30000}", fixedDelayString = "${aboutbits.emailservice.scheduling.interval:30000}")
    void sendEmails() {
        logStartOfPass();

        var emailsToSend = queryEmail.readyToSend();

        var countSent = 0;
        var countError = 0;
        for (var email : emailsToSend) {
            var updatedEmail = manageEmail.send(email);
            switch (updatedEmail.getState()) {
                case ERROR -> countError++;
                case SENT -> countSent++;
                default -> log.warn(
                        JOB_DESCRIPTION + " | Job produced an invalid notification result state: {}.",
                        updatedEmail.getState().name()
                );
            }
        }

        logEndOfPass(countSent, countError);

        for (var callback : callbacks) {
            callback.report(new EmailSchedulerCallback.Report(
                    emailsToSend.size(),
                    countSent,
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

    private void logEndOfPass(int countSent, int countError) {
        log.debug(JOB_DESCRIPTION + " | Finished");
        if (countSent > 0 || countError > 0) {
            lastInfoLogMillis = System.currentTimeMillis();
            silentRuns = 0;
            log.info(JOB_DESCRIPTION + " | Sent: {}, Errors: {}", countSent, countError);
        } else {
            log.debug(JOB_DESCRIPTION + " | Sent: {}, Errors: {}", countSent, countError);
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
