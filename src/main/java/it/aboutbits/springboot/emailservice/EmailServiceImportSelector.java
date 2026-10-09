package it.aboutbits.springboot.emailservice;

import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.type.AnnotationMetadata;

/*
 * Registers the library the way an auto-configuration is registered: only after every configuration
 * class of the application has been parsed. That is what lets the @ConditionalOnMissingBean fallbacks
 * (AttachmentDataSource, EmailMetrics) see a replacement the application declares, even one declared
 * in the very class that carries @EnableEmailService - a plain @Import is processed before the
 * importing class, so such a replacement would only be found after the fallback has already been
 * registered.
 *
 * The metrics configurations are listed here instead of being nested in EmailServiceConfiguration:
 * Spring only picks up nested configuration classes below a class annotated @Configuration, and
 * EmailServiceConfiguration is not one.
 */
@NullMarked
public class EmailServiceImportSelector implements DeferredImportSelector, Ordered {
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[]{
                EmailServiceConfiguration.class.getName(),
                MicrometerEmailMetricsConfiguration.class.getName(),
                NoOpEmailMetricsConfiguration.class.getName()
        };
    }

    // Spring Boot's auto-configuration runs at LOWEST_PRECEDENCE - 1 and expects the
    // @AutoConfigurationPackage of EmailServiceConfiguration to be registered by then, since that is
    // where it finds the entities and repositories of this library.
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 2;
    }
}
