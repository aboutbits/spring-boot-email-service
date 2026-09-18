package it.aboutbits.springboot.emailservice.lib.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import it.aboutbits.springboot.emailservice.lib.EmailMetrics;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/*
 * Series names are written out in Prometheus notation instead of Micrometer's dotted convention,
 * so that the name in this file is character for character the name a dashboard or an alert queries.
 *
 * The scheduler is tagged "scheduler" rather than job: job is a label Prometheus reserves for the
 * scrape target - or, on the OTLP path, for the service name - and a metric label of that name is
 * overwritten or renamed to exported_job on the way in.
 */
@NullMarked
public class MicrometerEmailMetrics implements EmailMetrics {
    private static final String SEND_METER = "app_email_send_duration";
    // Counters are scraped with a _total suffix appended, the same way the timers get _seconds.
    private static final String CLEANUP_METER = "app_email_cleanup_attempts";
    private static final String ATTACHMENT_ERROR_METER = "app_email_attachment_errors";
    private static final String PASS_METER = "app_email_pass_duration";
    private static final String LAST_RUN_METER = "app_email_last_run_timestamp_seconds";
    private static final String LAST_SUCCESS_METER = "app_email_last_success_timestamp_seconds";
    private static final String QUEUE_METER = "app_email_queue";
    private static final String QUEUE_AGE_METER = "app_email_queue_oldest_due_age_seconds";

    private final MeterRegistry meterRegistry;
    private final Map<GaugeId, AtomicLong> gauges = new ConcurrentHashMap<>();

    public MicrometerEmailMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        registerCountersUpFront();
    }

    /*
     * Meters are otherwise created on first record, so a series a pod has never had a reason to write -
     * every failure series, in healthy operation - is absent rather than zero, which a dashboard draws as
     * "no data" and an alert cannot count. Unlike the timestamp gauges below, zero is a true reading here:
     * nothing has failed yet. The gauges stay lazy on purpose, see setGauge.
     */
    private void registerCountersUpFront() {
        for (var mode : SendMode.values()) {
            for (var outcome : SendOutcome.values()) {
                // Direct sends are never retried; the series would sit at zero forever and read as a lie.
                if (mode == SendMode.DIRECT && outcome == SendOutcome.RETRY) {
                    continue;
                }
                meterRegistry.timer(SEND_METER, "mode", tag(mode), "outcome", tag(outcome));
            }
        }

        for (var outcome : CleanupOutcome.values()) {
            meterRegistry.counter(CLEANUP_METER, "outcome", tag(outcome));
        }

        for (var operation : AttachmentOperation.values()) {
            meterRegistry.counter(ATTACHMENT_ERROR_METER, "operation", tag(operation));
        }

        for (var job : Job.values()) {
            for (var status : PassStatus.values()) {
                meterRegistry.timer(PASS_METER, "scheduler", tag(job), "status", tag(status));
            }
        }
    }

    @Override
    public void sendAttempt(SendMode mode, SendOutcome outcome, Duration duration) {
        meterRegistry.timer(SEND_METER, "mode", tag(mode), "outcome", tag(outcome))
                .record(duration);
    }

    @Override
    public void cleanupAttempt(CleanupOutcome outcome) {
        meterRegistry.counter(CLEANUP_METER, "outcome", tag(outcome))
                .increment();
    }

    @Override
    public void attachmentError(AttachmentOperation operation) {
        meterRegistry.counter(ATTACHMENT_ERROR_METER, "operation", tag(operation))
                .increment();
    }

    @Override
    public void pass(Job job, PassStatus status, Duration duration) {
        meterRegistry.timer(PASS_METER, "scheduler", tag(job), "status", tag(status))
                .record(duration);

        var now = Instant.now().getEpochSecond();

        // A pass that found nothing to do still stamps last_run: the scheduler did fire, which is what
        // last_run answers. Only last_success says the pass got through without hitting the database wall.
        setGauge(LAST_RUN_METER, "scheduler", tag(job), now);
        if (status == PassStatus.SUCCESS) {
            setGauge(LAST_SUCCESS_METER, "scheduler", tag(job), now);
        }
    }

    @Override
    public void queue(Supplier<QueueSnapshot> snapshot) {
        var reading = snapshot.get();

        setGauge(QUEUE_METER, "state", "pending", reading.pending());
        setGauge(QUEUE_METER, "state", "sending", reading.sending());

        // Nothing due means nothing is waiting, which is an age of zero rather than a missing reading.
        var oldestDueAge = reading.oldestDueAge();
        setGauge(QUEUE_AGE_METER, "state", "pending", oldestDueAge == null ? 0 : oldestDueAge.toSeconds());
    }

    /*
     * Registered on first use rather than eagerly, so a pod that has never run a pass since start has no
     * series at all. That reads the same to an alert as a NaN starting value would, and avoids the false
     * alarm a 0 starting value causes after every deploy.
     */
    private void setGauge(String meter, String tagKey, String tagValue, long value) {
        gauges.computeIfAbsent(new GaugeId(meter, Tags.of(tagKey, tagValue)), this::registerGauge)
                .set(value);
    }

    private AtomicLong registerGauge(GaugeId id) {
        // A gauge left behind by another instance on the same registry - a DevTools restart, a registry
        // shared between contexts - keeps reading that instance's frozen holder for good: Micrometer
        // returns the existing gauge on re-registration and silently ignores the new holder. Replacing
        // it makes the newest instance the one that is read.
        var leftBehind = meterRegistry.find(id.meter()).tags(id.tags()).gauge();
        if (leftBehind != null) {
            meterRegistry.remove(leftBehind);
        }

        var holder = new AtomicLong();
        Gauge.builder(id.meter(), holder, AtomicLong::doubleValue)
                .tags(id.tags())
                .register(meterRegistry);

        return holder;
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private record GaugeId(String meter, Tags tags) {
    }
}
