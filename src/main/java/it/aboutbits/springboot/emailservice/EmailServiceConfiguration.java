package it.aboutbits.springboot.emailservice;

import it.aboutbits.springboot.emailservice.lib.AttachmentCleanerCallback;
import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.EmailMetrics;
import it.aboutbits.springboot.emailservice.lib.EmailSchedulerCallback;
import it.aboutbits.springboot.emailservice.lib.application.CleanupAttachmentFiles;
import it.aboutbits.springboot.emailservice.lib.application.EmailAttachmentMapper;
import it.aboutbits.springboot.emailservice.lib.application.EmailAttachmentMapperImpl;
import it.aboutbits.springboot.emailservice.lib.application.EmailMapper;
import it.aboutbits.springboot.emailservice.lib.application.EmailMapperImpl;
import it.aboutbits.springboot.emailservice.lib.application.EmailServiceMigrator;
import it.aboutbits.springboot.emailservice.lib.application.ManageEmail;
import it.aboutbits.springboot.emailservice.lib.application.QueryEmail;
import it.aboutbits.springboot.emailservice.lib.application.SendScheduledEmails;
import it.aboutbits.springboot.emailservice.lib.application.UnavailableAttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.lib.metrics.FailSafeEmailMetrics;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.List;

/*
 * Imported through EmailServiceImportSelector, never directly: see there for why the import is deferred
 * and where the EmailMetrics bean comes from.
 */
@AutoConfigurationPackage
@NullMarked
public class EmailServiceConfiguration {
    @Bean(initMethod = "migrate")
    @ConditionalOnProperty(value = "aboutbits.emailservice.migrations.enabled", matchIfMissing = true)
    public EmailServiceMigrator emailServiceMigrator(JdbcTemplate jdbcTemplate) {
        return new EmailServiceMigrator(jdbcTemplate);
    }

    @Bean
    public EmailAttachmentMapper emailAttachmentMapper() {
        return new EmailAttachmentMapperImpl();
    }

    @Bean
    public EmailMapper emailMapper() {
        return new EmailMapperImpl();
    }

    @Bean
    public QueryEmail queryEmail(EmailRepository emailRepository, EmailMapper emailMapper) {
        return new QueryEmail(emailRepository, emailMapper);
    }

    @Bean
    @SuppressWarnings("checkstyle:ParameterNumber")
    public ManageEmail manageEmail(
            EmailRepository emailRepository,
            JavaMailSender javaMailSender,
            AttachmentDataSource attachmentDataSource,
            EmailMapper emailMapper,
            EmailMetrics emailMetrics,
            @Value("${aboutbits.emailservice.scheduling.max-attempts:3}") int maxAttempts,
            @Value("${aboutbits.emailservice.scheduling.interval:30000}") long schedulerIntervalMillis,
            PlatformTransactionManager transactionManager
    ) {
        return new ManageEmail(
                emailRepository,
                javaMailSender,
                attachmentDataSource,
                emailMapper,
                failSafe(emailMetrics),
                maxAttempts,
                Duration.ofMillis(schedulerIntervalMillis),
                transactionManager
        );
    }

    @Bean
    @ConditionalOnProperty(value = "aboutbits.emailservice.scheduling.enabled", matchIfMissing = true)
    public SendScheduledEmails sendScheduledEmails(
            QueryEmail queryEmail,
            ManageEmail manageEmail,
            List<EmailSchedulerCallback> callbacks,
            EmailMetrics emailMetrics,
            @Value("${aboutbits.emailservice.scheduling.stuck-sending-recovery-threshold:PT30M}") Duration stuckSendingRecoveryThreshold
    ) {
        return new SendScheduledEmails(
                queryEmail,
                manageEmail,
                callbacks,
                failSafe(emailMetrics),
                stuckSendingRecoveryThreshold
        );
    }

    @Bean
    @ConditionalOnProperty(value = "aboutbits.emailservice.scheduling.cleanup.enabled", matchIfMissing = true)
    public CleanupAttachmentFiles cleanupAttachments(
            QueryEmail queryEmail,
            ManageEmail manageEmail,
            List<AttachmentCleanerCallback> callbacks,
            EmailMetrics emailMetrics,
            @Value("${aboutbits.emailservice.scheduling.stuck-cleanup-recovery-threshold:PT30M}") Duration stuckCleanupRecoveryThreshold
    ) {
        return new CleanupAttachmentFiles(
                queryEmail,
                manageEmail,
                callbacks,
                failSafe(emailMetrics),
                stuckCleanupRecoveryThreshold
        );
    }

    @Bean
    @ConditionalOnMissingBean(AttachmentDataSource.class)
    public AttachmentDataSource attachmentDataSource() {
        return new UnavailableAttachmentDataSource();
    }

    // The EmailMetrics bean itself stays whatever the application sees - the library's own or a
    // replacement - but nothing inside the library talks to it unguarded: an email that went out over
    // SMTP has to be persisted as SENT no matter what a metrics backend does.
    private static EmailMetrics failSafe(EmailMetrics emailMetrics) {
        return new FailSafeEmailMetrics(emailMetrics);
    }
}
