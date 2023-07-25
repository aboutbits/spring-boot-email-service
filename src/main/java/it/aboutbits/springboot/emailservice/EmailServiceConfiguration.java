package it.aboutbits.springboot.emailservice;

import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.EmailSchedulerCallback;
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
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

@AutoConfigurationPackage
public class EmailServiceConfiguration {
    @Bean(initMethod = "init")
    @ConditionalOnProperty(value = "aboutbits.emailservice.migrations.enabled", matchIfMissing = true)
    public EmailServiceMigrator springLiquibase(JdbcTemplate jdbcTemplate) {
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
    public QueryEmail queryEmail(EmailRepository emailRepository, EmailMapper emailMapper, EntityManager entityManager) {
        return new QueryEmail(emailRepository, emailMapper, entityManager);
    }

    @Bean
    public ManageEmail manageEmail(EmailRepository emailRepository, JavaMailSender javaMailSender, AttachmentDataSource attachmentDataSource, EmailMapper emailMapper) {
        return new ManageEmail(emailRepository, javaMailSender, attachmentDataSource, emailMapper);
    }

    @Bean
    @ConditionalOnProperty(value = "aboutbits.emailservice.scheduling.enabled", matchIfMissing = true)
    public SendScheduledEmails sendScheduledEmails(QueryEmail queryEmail, ManageEmail manageEmail, List<EmailSchedulerCallback> callbacks) {
        return new SendScheduledEmails(queryEmail, manageEmail, callbacks);
    }

    @Bean
    @ConditionalOnMissingBean(AttachmentDataSource.class)
    public AttachmentDataSource attachmentDataSource() {
        return new UnavailableAttachmentDataSource();
    }
}
