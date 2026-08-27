package it.aboutbits.springboot.emailservice;

import it.aboutbits.springboot.emailservice.lib.EmailMetrics;
import it.aboutbits.springboot.emailservice.lib.metrics.NoOpEmailMetrics;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Counterpart of MicrometerEmailMetricsConfiguration for applications without Micrometer on the classpath.
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingClass("io.micrometer.core.instrument.MeterRegistry")
@NullMarked
class NoOpEmailMetricsConfiguration {
    @Bean
    @ConditionalOnMissingBean(EmailMetrics.class)
    EmailMetrics emailMetrics() {
        return new NoOpEmailMetrics();
    }
}
