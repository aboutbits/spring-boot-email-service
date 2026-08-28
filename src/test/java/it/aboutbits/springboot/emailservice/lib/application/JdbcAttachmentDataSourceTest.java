package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import it.aboutbits.springboot.emailservice.support.database.WithPostgres;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest
@WithPostgres
@NullMarked
class JdbcAttachmentDataSourceTest {
    @Autowired
    JdbcTemplate jdbcTemplate;

    JdbcAttachmentDataSource attachmentDataSource;

    @BeforeEach
    void setup() {
        attachmentDataSource = new JdbcAttachmentDataSource(jdbcTemplate);
    }

    @Test
    void givenPayload_store_shouldBeReadableAgain() throws Exception {
        var payload = new byte[]{1, 2, 3, 4, 5};

        var fileReference = attachmentDataSource.storeAttachmentPayload(new ByteArrayInputStream(payload));

        try (var stored = attachmentDataSource.getAttachmentPayload(fileReference)) {
            assertThat(stored.readAllBytes()).isEqualTo(payload);
        }
    }

    @Test
    void givenPayload_store_shouldClosePayloadStream() throws Exception {
        var payload = new TrackingInputStream(new byte[]{1, 2, 3});

        attachmentDataSource.storeAttachmentPayload(payload);

        assertThat(payload.closed).isTrue();
    }

    @Test
    void givenMultiplePayloads_store_shouldReturnDistinctReferences() throws Exception {
        var first = attachmentDataSource.storeAttachmentPayload(new ByteArrayInputStream(new byte[]{1}));
        var second = attachmentDataSource.storeAttachmentPayload(new ByteArrayInputStream(new byte[]{2}));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void givenUnknownReference_get_shouldFail() {
        assertThatExceptionOfType(AttachmentException.class).isThrownBy(
                () -> attachmentDataSource.getAttachmentPayload(1L)
        );
    }

    @Test
    void givenStoredPayload_release_shouldRemoveIt() throws Exception {
        var fileReference = attachmentDataSource.storeAttachmentPayload(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        attachmentDataSource.releaseAttachment(fileReference);

        assertThatExceptionOfType(AttachmentException.class).isThrownBy(
                () -> attachmentDataSource.getAttachmentPayload(fileReference)
        );
    }

    @Test
    void givenUnknownReference_release_shouldBeIdempotent() {
        assertThatCode(
                () -> attachmentDataSource.releaseAttachment(1L)
        ).doesNotThrowAnyException();
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed = false;

        private TrackingInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
