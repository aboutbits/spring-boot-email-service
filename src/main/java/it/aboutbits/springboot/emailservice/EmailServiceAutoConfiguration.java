package it.aboutbits.springboot.emailservice;

import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.EmailSchedulerCallback;
import it.aboutbits.springboot.emailservice.lib.application.ManageEmail;
import it.aboutbits.springboot.emailservice.lib.application.QueryEmail;
import it.aboutbits.springboot.emailservice.lib.application.SendScheduledEmails;
import it.aboutbits.springboot.emailservice.lib.application.UnavailableAttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import jakarta.persistence.EntityManager;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.mail.javamail.JavaMailSender;

import javax.sql.DataSource;
import java.util.List;

@Configuration
public class EmailServiceAutoConfiguration {
    @Bean(name = "emailServiceLiquibase")
    public SpringLiquibase emailServiceLiquibase(DataSource dataSource) {
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:/db/changelog/emailservice/main.xml");
        liquibase.setShouldRun(true);
        return liquibase;
    }

    @Bean
    public EmailRepository emailRepository(EntityManager entityManager) {
        var factory = new JpaRepositoryFactory(entityManager);
        return factory.getRepository(EmailRepository.class);
    }

    @Bean
    public QueryEmail queryEmail(EmailRepository emailRepository) {
        return new QueryEmail(emailRepository);
    }

    @Bean
    public ManageEmail manageEmail(EmailRepository emailRepository, JavaMailSender javaMailSender, AttachmentDataSource attachmentDataSource) {
        return new ManageEmail(emailRepository, javaMailSender, attachmentDataSource);
    }

    @Bean
    @ConditionalOnProperty(value = "lib.emailservice.scheduling.enabled", matchIfMissing = true)
    public SendScheduledEmails sendScheduledEmails(QueryEmail queryEmail, ManageEmail manageEmail, List<EmailSchedulerCallback> callbacks) {
        return new SendScheduledEmails(queryEmail, manageEmail, callbacks);
    }

    @Bean
    @ConditionalOnMissingBean(AttachmentDataSource.class)
    public AttachmentDataSource attachmentDataSource() {
        return new UnavailableAttachmentDataSource();
    }
}
