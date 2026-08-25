package it.aboutbits.springboot.emailservice;

import io.micrometer.core.instrument.MeterRegistry;
import it.aboutbits.springboot.emailservice.lib.EmailMetrics;
import it.aboutbits.springboot.emailservice.lib.metrics.MicrometerEmailMetrics;
import it.aboutbits.springboot.emailservice.lib.metrics.NoOpEmailMetrics;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * Micrometer is an optional dependency, so this configuration and NoOpEmailMetricsConfiguration are
 * guarded by mutually exclusive class conditions rather than by bean ordering: exactly one of them ever
 * applies, and this one, the only one referencing MeterRegistry, is only loaded once that class exists.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@NullMarked
class MicrometerEmailMetricsConfiguration {
    // Micrometer on the classpath without a registry bean - micrometer-core without actuator - still
    // leaves nothing to record into, hence the fallback rather than a required dependency. The same goes
    // for several registries without a primary one: a library that only records "if a registry happens
    // to be there" must not fail the whole context over an ambiguity it cannot resolve.
    @Bean
    @ConditionalOnMissingBean(EmailMetrics.class)
    EmailMetrics emailMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
        var registry = meterRegistry.getIfUnique();

        if (registry == null) {
            return new NoOpEmailMetrics();
        }

        return new MicrometerEmailMetrics(registry);
    }
}
