package it.aboutbits.springboot.emailservice;

import it.aboutbits.springboot.emailservice.lib.application.CleanupAttachmentFiles;
import it.aboutbits.springboot.emailservice.lib.application.SendAsyncEmails;
import it.aboutbits.springboot.emailservice.lib.application.SendScheduledEmails;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

/**
 * Verifies at startup that the scheduler, which performs the retries and the crash recovery for {@code sendAsync}
 * and {@code schedule}, can actually run. Problems are logged, not thrown: a context that wires the scheduler bean
 * only to call it manually (as tests do) must still start.
 */
@Slf4j
@NullMarked
public class EmailServiceStartupCheck implements SmartInitializingSingleton {
    private final ListableBeanFactory beanFactory;

    public EmailServiceStartupCheck(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        check(
                hasBean(SendScheduledEmails.class),
                hasBean(CleanupAttachmentFiles.class),
                hasBean(SendAsyncEmails.class),
                // @EnableScheduling leaves no flag to read. What it does is register a ScheduledAnnotationBeanPostProcessor
                // under a fixed name, and that post processor is what makes @Scheduled methods run at all. Spring Boot's own
                // task scheduling auto-configuration is conditional on the same bean name, so its presence is the reliable
                // way to tell whether @EnableScheduling was applied anywhere in the context.
                beanFactory.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)
        );
    }

    private boolean hasBean(Class<?> type) {
        return beanFactory.getBeanNamesForType(type).length > 0;
    }

    static void check(
            boolean schedulerEnabled,
            boolean cleanupEnabled,
            boolean asyncDispatchEnabled,
            boolean schedulingActive
    ) {
        if ((schedulerEnabled || cleanupEnabled) && !schedulingActive) {
            log.error(
                    "EmailService: @EnableScheduling is missing, so the email scheduler and the attachment cleanup "
                            + "never run: scheduled emails are never sent, failed sendAsync emails are never retried, "
                            + "emails of crashed pods are never recovered and attachments are never cleaned up. Add "
                            + "@EnableScheduling to your application, or set aboutbits.emailservice.scheduling.enabled "
                            + "and aboutbits.emailservice.scheduling.cleanup.enabled to false if another pod "
                            + "runs them."
            );
        }
        if (asyncDispatchEnabled && !schedulerEnabled) {
            log.warn(
                    "EmailService: the after-commit dispatch is enabled but the scheduler is disabled on this pod "
                            + "(aboutbits.emailservice.scheduling.enabled=false). Emails that miss the after-commit send "
                            + "(saturated executor, SMTP failure, restart) are only retried, and abandoned SENDING rows "
                            + "only recovered, if another pod runs the scheduler against the same database."
            );
        }
    }
}
