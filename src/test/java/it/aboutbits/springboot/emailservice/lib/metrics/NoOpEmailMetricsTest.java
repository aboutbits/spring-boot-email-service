package it.aboutbits.springboot.emailservice.lib.metrics;

import it.aboutbits.springboot.emailservice.lib.EmailMetrics;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
class NoOpEmailMetricsTest {
    private final NoOpEmailMetrics sut = new NoOpEmailMetrics();

    // The snapshot costs database queries, and without a backend they would be queries for nothing.
    @Test
    void givenNoBackend_queue_shouldNeverReadTheSnapshot() {
        var reads = new int[1];

        sut.queue(() -> {
            reads[0]++;
            return new EmailMetrics.QueueSnapshot(0, 0, null);
        });

        assertThat(reads[0]).isZero();
    }
}
