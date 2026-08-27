package it.aboutbits.springboot.emailservice;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import it.aboutbits.springboot.emailservice.lib.EmailMetrics;
import it.aboutbits.springboot.emailservice.lib.application.CleanupAttachmentFiles;
import it.aboutbits.springboot.emailservice.lib.application.ManageEmail;
import it.aboutbits.springboot.emailservice.lib.application.SendScheduledEmails;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.lib.metrics.MicrometerEmailMetrics;
import it.aboutbits.springboot.emailservice.lib.metrics.NoOpEmailMetrics;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

/*
 * Wires the library the way an application does - through @EnableEmailService and nothing else. Nothing
 * here is component scanned, unlike in the @SpringBootTest suite, whose TestApplication happens to live in
 * the library's own package and would find any configuration class of the library on its own.
 */
@NullMarked
class EmailServiceConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            // What SpringApplication installs on its own and the library's Duration properties rely on.
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withPropertyValues("aboutbits.emailservice.migrations.enabled=false")
            .withBean(EmailRepository.class, () -> mock(EmailRepository.class))
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));

    @Test
    void givenOnlyEnableEmailService_context_shouldWireEverySchedulerAndService() {
        contextRunner
                .withUserConfiguration(Application.class)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBeansOfType(ManageEmail.class)).hasSize(1);
                    assertThat(context.getBeansOfType(SendScheduledEmails.class)).hasSize(1);
                    assertThat(context.getBeansOfType(CleanupAttachmentFiles.class)).hasSize(1);
                    assertThat(context.getBeansOfType(EmailMetrics.class)).hasSize(1);
                });
    }

    @Test
    void givenARegistry_emailMetrics_shouldRecordIntoMicrometer() {
        contextRunner
                .withUserConfiguration(Application.class)
                .withBean(SimpleMeterRegistry.class)
                .run(context -> assertThat(context.getBean(EmailMetrics.class))
                        .isInstanceOf(MicrometerEmailMetrics.class));
    }

    @Test
    void givenMicrometerWithoutARegistry_emailMetrics_shouldFallBackToTheNoOp() {
        contextRunner
                .withUserConfiguration(Application.class)
                .run(context -> assertThat(context.getBean(EmailMetrics.class))
                        .isInstanceOf(NoOpEmailMetrics.class));
    }

    @Test
    void givenSeveralRegistriesAndNoPrimaryOne_emailMetrics_shouldFallBackToTheNoOpInsteadOfFailing() {
        contextRunner
                .withUserConfiguration(Application.class)
                .withBean("first", SimpleMeterRegistry.class)
                .withBean("second", SimpleMeterRegistry.class)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBean(EmailMetrics.class)).isInstanceOf(NoOpEmailMetrics.class);
                });
    }

    @Test
    void givenNoMicrometerOnTheClasspath_emailMetrics_shouldFallBackToTheNoOp() {
        contextRunner
                .withUserConfiguration(Application.class)
                .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
                .run(context -> assertThat(context.getBean(EmailMetrics.class))
                        .isInstanceOf(NoOpEmailMetrics.class));
    }

    @Test
    void givenTheApplicationDeclaresItsOwnEmailMetrics_emailMetrics_shouldBeThatOneAlone() {
        contextRunner
                .withUserConfiguration(ApplicationWithOwnMetrics.class)
                .withBean(SimpleMeterRegistry.class)
                .run(context -> {
                    assertThat(context.getBeansOfType(EmailMetrics.class)).hasSize(1);
                    assertThat(mockingDetails(context.getBean(EmailMetrics.class)).isMock()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableEmailService
    static class Application {
    }

    // The replacement sits in the very class carrying @EnableEmailService, the place an application
    // would put it, and the one place a plain @Import would parse too late to see.
    @Configuration(proxyBeanMethods = false)
    @EnableEmailService
    static class ApplicationWithOwnMetrics {
        @Bean
        EmailMetrics ownEmailMetrics() {
            return mock(EmailMetrics.class);
        }
    }
}
