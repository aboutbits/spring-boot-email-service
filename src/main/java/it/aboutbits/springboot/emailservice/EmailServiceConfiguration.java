package it.aboutbits.springboot.emailservice;

import it.aboutbits.springboot.emailservice.lib.AttachmentCleanerCallback;
import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
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
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.List;

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
    public ManageEmail manageEmail(
            EmailRepository emailRepository,
            JavaMailSender javaMailSender,
            AttachmentDataSource attachmentDataSource,
            EmailMapper emailMapper,
            @Value("${aboutbits.emailservice.scheduling.max-attempts:3}") int maxAttempts,
            @Value("${aboutbits.emailservice.scheduling.interval:30000}") long schedulerIntervalMillis
    ) {
        return new ManageEmail(
                emailRepository,
                javaMailSender,
                attachmentDataSource,
                emailMapper,
                maxAttempts,
                Duration.ofMillis(schedulerIntervalMillis)
        );
    }

    @Bean
    @ConditionalOnProperty(value = "aboutbits.emailservice.scheduling.enabled", matchIfMissing = true)
    public SendScheduledEmails sendScheduledEmails(
            QueryEmail queryEmail,
            ManageEmail manageEmail,
            List<EmailSchedulerCallback> callbacks,
            @Value("${aboutbits.emailservice.scheduling.stuck-sending-recovery-threshold:PT5M}") Duration stuckSendingRecoveryThreshold
    ) {
        return new SendScheduledEmails(queryEmail, manageEmail, callbacks, stuckSendingRecoveryThreshold);
    }

    @Bean
    @ConditionalOnProperty(value = "aboutbits.emailservice.scheduling.cleanup.enabled", matchIfMissing = true)
    public CleanupAttachmentFiles cleanupAttachments(
            QueryEmail queryEmail,
            ManageEmail manageEmail,
            List<AttachmentCleanerCallback> callbacks
    ) {
        return new CleanupAttachmentFiles(queryEmail, manageEmail, callbacks);
    }

    @Bean
    @ConditionalOnMissingBean(AttachmentDataSource.class)
    public AttachmentDataSource attachmentDataSource() {
        return new UnavailableAttachmentDataSource();
    }
}
