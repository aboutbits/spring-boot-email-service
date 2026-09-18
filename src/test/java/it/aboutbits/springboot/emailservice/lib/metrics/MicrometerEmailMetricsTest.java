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

        assertThat(meterRegistry.scrape()).contains("outcome=\"retry\"");

        // Both series exist from the start, so the two are told apart by their count, not by presence.
        assertThat(sendCount("scheduled", "retry")).isEqualTo(1);
        assertThat(sendCount("scheduled", "error")).isZero();
    }

    /*
     * The counterpart of givenNoPassHasRun below: an absent series reads as "no data" to a dashboard and
     * cannot be counted by an alert, and for a counter that is a lie - nothing has failed yet is a zero.
     * The timestamp gauges are the opposite case and stay absent.
     */
    @Test
    void givenNothingHasFailedYet_theFailureSeries_shouldAlreadyBeExposedAsZero() {
        var scrape = meterRegistry.scrape();

        assertThat(scrape).contains("app_email_send_duration_seconds_count");
        assertThat(scrape).contains("app_email_cleanup_attempts_total");
        assertThat(scrape).contains("app_email_pass_duration_seconds_count");
        assertThat(scrape).contains("app_email_attachment_errors_total");

        assertThat(sendCount("scheduled", "sent")).isZero();
        assertThat(sendCount("scheduled", "retry")).isZero();
        assertThat(sendCount("scheduled", "error")).isZero();
        assertThat(sendCount("direct", "sent")).isZero();
        assertThat(sendCount("direct", "error")).isZero();
    }

    @Test
    void givenDirectSendsAreNeverRetried_theirRetrySeries_shouldNotBeRegisteredAtAll() {
        // A series that can only ever read zero would be a permanent flat line claiming to mean something.
        assertThat(meterRegistry.find("app_email_send_duration")
                           .tags("mode", "direct", "outcome", "retry")
                           .timer()).isNull();
    }

    @Test
    void givenACleanedAttachment_cleanupAttempt_shouldExposeTheAttemptByOutcome() {
        sut.cleanupAttempt(EmailMetrics.CleanupOutcome.CLEANED);

        var scrape = meterRegistry.scrape();

        assertThat(scrape).contains("app_email_cleanup_attempts_total");
        assertThat(scrape).contains("outcome=\"cleaned\"");
    }

    @Test
    void givenAFailingAttachmentStore_attachmentError_shouldExposeTheFailedOperation() {
        sut.attachmentError(EmailMetrics.AttachmentOperation.FETCH);

        var scrape = meterRegistry.scrape();

        // Counters are scraped with _total appended, which is why the constant carries no suffix itself.
        assertThat(scrape).contains("app_email_attachment_errors_total");
        assertThat(scrape).contains("operation=\"fetch\"");

        assertThat(attachmentErrors("fetch")).isEqualTo(1d);
        assertThat(attachmentErrors("store")).isZero();
        assertThat(attachmentErrors("release")).isZero();
    }

    @Test
    void givenASuccessfulPass_pass_shouldExposeTheDurationAndBothTimestamps() {
        sut.pass(EmailMetrics.Job.SEND, EmailMetrics.PassStatus.SUCCESS, Duration.ofMillis(400));

        var scrape = meterRegistry.scrape();

        assertThat(scrape).contains("app_email_pass_duration_seconds_count");
        assertThat(scrape).contains("scheduler=\"send\"");
        assertThat(scrape).contains("status=\"success\"");
        // The staleness alerts read these as "time() - max by (scheduler) (...)", so no suffix may be appended.
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

    private long sendCount(String mode, String outcome) {
        return meterRegistry.get("app_email_send_duration")
                .tags("mode", mode, "outcome", outcome)
                .timer()
                .count();
    }

    private double attachmentErrors(String operation) {
        return meterRegistry.get("app_email_attachment_errors")
                .tag("operation", operation)
                .counter()
                .count();
    }
}
