package it.aboutbits.springboot.emailservice.lib.metrics;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import it.aboutbits.springboot.emailservice.lib.EmailMetrics;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Asserts the scrape output character for character, because the series names are the contract
 * between this library and the dashboards and alerts querying it. Renaming a meter has to break
 * a test here rather than a panel that silently stops drawing.
 */
@NullMarked
class MicrometerEmailMetricsTest {
    private PrometheusMeterRegistry meterRegistry;
    private MicrometerEmailMetrics sut;

    @BeforeEach
    void setup() {
        meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        sut = new MicrometerEmailMetrics(meterRegistry);
    }

    @Test
    void givenAFailedScheduledSend_sendAttempt_shouldExposeTheAttemptByModeAndOutcome() {
        sut.sendAttempt(
                EmailMetrics.SendMode.SCHEDULED,
                EmailMetrics.SendOutcome.ERROR,
                Duration.ofMillis(120)
        );

        var scrape = meterRegistry.scrape();

        assertThat(scrape).contains("app_email_send_duration_seconds_count");
        assertThat(scrape).contains("mode=\"scheduled\"");
        assertThat(scrape).contains("outcome=\"error\"");
    }

    @Test
    void givenARetriedSend_sendAttempt_shouldSeparateRetriesFromErrors() {
        sut.sendAttempt(
                EmailMetrics.SendMode.SCHEDULED,
                EmailMetrics.SendOutcome.RETRY,
                Duration.ofMillis(120)
        );

        var scrape = meterRegistry.scrape();

        assertThat(scrape).contains("outcome=\"retry\"");
        assertThat(scrape).doesNotContain("outcome=\"error\"");
    }

    @Test
    void givenACleanedAttachment_cleanupAttempt_shouldExposeTheAttemptByOutcome() {
        sut.cleanupAttempt(EmailMetrics.CleanupOutcome.CLEANED, Duration.ofMillis(30));

        var scrape = meterRegistry.scrape();

        assertThat(scrape).contains("app_email_cleanup_duration_seconds_count");
        assertThat(scrape).contains("outcome=\"cleaned\"");
    }

    @Test
    void givenASuccessfulPass_pass_shouldExposeTheDurationAndBothTimestamps() {
        sut.pass(EmailMetrics.Job.SEND, EmailMetrics.PassStatus.SUCCESS, Duration.ofMillis(400));

        var scrape = meterRegistry.scrape();

        assertThat(scrape).contains("app_email_pass_duration_seconds_count");
        assertThat(scrape).contains("job=\"send\"");
        assertThat(scrape).contains("status=\"success\"");
        // The staleness alerts read these as "time() - max by (job) (...)", so no suffix may be appended.
        assertThat(scrape).contains("app_email_last_run_timestamp_seconds{");
        assertThat(scrape).contains("app_email_last_success_timestamp_seconds{");
    }

    @Test
    void givenOnlyFailedPasses_pass_shouldStampLastRunButNotLastSuccess() {
        sut.pass(EmailMetrics.Job.SEND, EmailMetrics.PassStatus.FAILED, Duration.ofMillis(400));

        var scrape = meterRegistry.scrape();

        assertThat(scrape).contains("app_email_last_run_timestamp_seconds{");
        assertThat(scrape).doesNotContain("app_email_last_success_timestamp_seconds");
    }

    @Test
    void givenNoPassHasRun_pass_shouldNotRegisterTimestampsAtAll() {
        var scrape = meterRegistry.scrape();

        // Absent rather than zero: a zero starting value would read as decades of staleness to an alert.
        assertThat(scrape).doesNotContain("app_email_last_run_timestamp_seconds");
        assertThat(scrape).doesNotContain("app_email_last_success_timestamp_seconds");
    }

    @Test
    void givenABacklog_queue_shouldExposeOneSeriesPerStateAndTheOldestAge() {
        sut.queue(() -> new EmailMetrics.QueueSnapshot(12, 3, Duration.ofMinutes(5)));

        var scrape = meterRegistry.scrape();

        assertThat(scrape).contains("app_email_queue{");
        assertThat(scrape).contains("app_email_queue_oldest_due_age_seconds{");

        assertThat(gauge("app_email_queue", "pending")).isEqualTo(12d);
        assertThat(gauge("app_email_queue", "sending")).isEqualTo(3d);
        assertThat(gauge("app_email_queue_oldest_due_age_seconds", "pending")).isEqualTo(300d);
    }

    @Test
    void givenNothingIsDue_queue_shouldReportAnAgeOfZeroInsteadOfNoReading() {
        sut.queue(() -> new EmailMetrics.QueueSnapshot(0, 0, null));

        assertThat(gauge("app_email_queue_oldest_due_age_seconds", "pending")).isEqualTo(0d);
    }

    @Test
    void givenRepeatedSnapshots_queue_shouldKeepOneSeriesPerStateAndUpdateItInPlace() {
        sut.queue(() -> new EmailMetrics.QueueSnapshot(12, 0, Duration.ofMinutes(5)));
        sut.queue(() -> new EmailMetrics.QueueSnapshot(4, 0, Duration.ofMinutes(1)));

        assertThat(meterRegistry.find("app_email_queue").tag("state", "pending").gauges()).hasSize(1);
        assertThat(gauge("app_email_queue", "pending")).isEqualTo(4d);
    }

    @Test
    void givenASecondInstanceOnTheSameRegistry_queue_shouldBeReadFromTheNewestInstance() {
        sut.queue(() -> new EmailMetrics.QueueSnapshot(12, 0, Duration.ofMinutes(5)));

        // A DevTools restart or a registry shared between contexts: the old instance is gone, its
        // gauge must not keep serving its last values as if the queue had frozen.
        var replacement = new MicrometerEmailMetrics(meterRegistry);
        replacement.queue(() -> new EmailMetrics.QueueSnapshot(4, 0, Duration.ofMinutes(1)));

        assertThat(meterRegistry.find("app_email_queue").tag("state", "pending").gauges()).hasSize(1);
        assertThat(gauge("app_email_queue", "pending")).isEqualTo(4d);
    }

    private double gauge(String name, String state) {
        return meterRegistry.get(name).tag("state", state).gauge().value();
    }
}
