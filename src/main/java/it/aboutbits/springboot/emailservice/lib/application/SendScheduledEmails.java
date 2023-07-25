package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.EmailSchedulerCallback;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

@AllArgsConstructor
@Log4j2
public class SendScheduledEmails {
    private static final String JOB_DESCRIPTION = "Sending open and failed email notifications.";

    private final QueryEmail queryEmail;
    private final ManageEmail manageEmail;
    private final List<EmailSchedulerCallback> callbacks;

    @Scheduled(initialDelayString = "${aboutbits.emailservice.scheduling.interval:30000}", fixedDelayString = "${aboutbits.emailservice.scheduling.interval:30000}")
    void sendEmails() {
        log.info("Start: " + JOB_DESCRIPTION);

        var emailsToSend = queryEmail.readyToSend();

        var countSent = 0;
        var countError = 0;
        for (var email : emailsToSend) {
            var updatedEmail = manageEmail.send(email);
            switch (updatedEmail.getState()) {
                case ERROR -> countError++;
                case SENT -> countSent++;
                default -> log.warn(
                        "Email job produced an invalid notification result state: {}.",
                        updatedEmail.getState().name());
            }
        }

        log.info("Finished: " + JOB_DESCRIPTION);
        log.info("Sent: {}, Errors: {}", countSent, countError);

        for (var callback : callbacks) {
            callback.report(new EmailSchedulerCallback.Report(
                    emailsToSend.size(),
                    countSent,
                    countError
            ));
        }
    }
}
