package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.AttachmentCleanerCallback;
import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import it.aboutbits.springboot.emailservice.lib.model.EmailAttachment;
import it.aboutbits.springboot.emailservice.support.database.WithPostgres;
import it.aboutbits.springboot.emailservice.support.database.factory.EmailFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "aboutbits.emailservice.scheduling.stuck-cleanup-recovery-threshold=PT5M",
        "aboutbits.emailservice.scheduling.interval=30000"
})
@WithPostgres
@NullMarked
class CleanupAttachmentFilesTest {
    @MockitoBean
    AttachmentDataSource attachmentDataSource;

    @MockitoBean
    AttachmentCleanerCallback attachmentCleanerCallback;

    @Autowired
    EmailRepository emailRepository;

    @Autowired
    CleanupAttachmentFiles cleanupAttachmentFiles;

    @Test
    void givenSentUncleanedEmails_cleanupAttachments_shouldReleaseAndMarkCleaned() throws AttachmentException {
        persistCleanableEmail(100L);
        persistCleanableEmail(101L);
        persistCleanableEmail(102L);

        cleanupAttachmentFiles.cleanupAttachments();

        assertThat(emailRepository.findAll())
                .hasSize(3)
                .allMatch(Email::isAttachmentsCleaned);
        verify(attachmentDataSource, times(1)).releaseAttachment(100L);
        verify(attachmentDataSource, times(1)).releaseAttachment(101L);
        verify(attachmentDataSource, times(1)).releaseAttachment(102L);
        verify(attachmentCleanerCallback).report(new AttachmentCleanerCallback.Report(3, 3, 0));
    }

    @Test
    void givenNonSentOrAlreadyCleanedRows_cleanupAttachments_shouldSkip() throws AttachmentException {
        persistEmail(200L, EmailState.PENDING, false, null);
        persistEmail(201L, EmailState.SENDING, false, null);
        persistEmail(202L, EmailState.ERROR, false, null);
        persistEmail(203L, EmailState.SENT, true, null);

        cleanupAttachmentFiles.cleanupAttachments();

        verify(attachmentDataSource, times(0)).releaseAttachment(anyLong());
        assertThat(emailRepository.findAll())
                .filteredOn(email -> email.getState() != EmailState.SENT)
                .allMatch(email -> !email.isAttachmentsCleaned());
    }

    @Test
    void givenReleaseFails_cleanupAttachments_shouldLeaveClaimForStaleRecovery() throws AttachmentException {
        var email = persistCleanableEmail(300L);
        doThrow(new AttachmentException()).when(attachmentDataSource).releaseAttachment(300L);

        cleanupAttachmentFiles.cleanupAttachments();

        // Files were not released, and the claim is left in place so a later pass recovers it
        assertThat(emailRepository.findById(email.getId()))
                .get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.isAttachmentsCleaned()).isFalse();
                    assertThat(reloaded.getCleanupStartTime()).isNotNull();
                });
        verify(attachmentCleanerCallback).report(new AttachmentCleanerCallback.Report(1, 0, 1));
    }

    @Test
    void givenStuckCleanup_cleanupAttachments_shouldRecoverAndClean() throws AttachmentException {
        var staleStart = OffsetDateTime.now().minusMinutes(10);
        var email = persistEmail(400L, EmailState.SENT, false, staleStart);

        cleanupAttachmentFiles.cleanupAttachments();

        assertThat(emailRepository.findById(email.getId()))
                .get()
                .satisfies(reloaded -> assertThat(reloaded.isAttachmentsCleaned()).isTrue());
        verify(attachmentDataSource, times(1)).releaseAttachment(400L);
    }

    @Test
    void givenFreshCleanupInProgress_cleanupAttachments_shouldNotStealFromOtherPod() throws AttachmentException {
        // cleanupStartTime within the 5-minute threshold -> another pod is cleaning this right now -> do not re-claim
        var recentStart = OffsetDateTime.now().minusSeconds(30).truncatedTo(ChronoUnit.MICROS);
        var email = persistEmail(500L, EmailState.SENT, false, recentStart);

        cleanupAttachmentFiles.cleanupAttachments();

        assertThat(emailRepository.findById(email.getId()))
                .get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.isAttachmentsCleaned()).isFalse();
                    assertThat(reloaded.getCleanupStartTime()).isEqualTo(recentStart);
                });
        verify(attachmentDataSource, times(0)).releaseAttachment(anyLong());
    }

    @Test
    void givenConcurrentPasses_cleanupAttachments_shouldReleaseEachEmailExactlyOnce() throws Exception {
        var totalEmails = 25;
        var workerThreads = 4;
        var passesPerWorker = 10;
        for (var i = 0; i < totalEmails; i++) {
            persistCleanableEmail(1000L + i);
        }

        var startGate = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(workerThreads);
        try {
            IntStream.range(0, workerThreads).forEach(_ -> executor.submit(() -> {
                startGate.await();
                for (var i = 0; i < passesPerWorker; i++) {
                    cleanupAttachmentFiles.cleanupAttachments();
                }
                return null;
            }));
            startGate.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            if (!executor.isTerminated()) {
                executor.shutdownNow();
            }
        }

        assertThat(emailRepository.findAll())
                .hasSize(totalEmails)
                .allMatch(Email::isAttachmentsCleaned);
        // Exactly one release per file reference
        verify(attachmentDataSource, times(totalEmails)).releaseAttachment(anyLong());
    }

    private Email persistCleanableEmail(long fileReference) {
        return persistEmail(fileReference, EmailState.SENT, false, null);
    }

    private Email persistEmail(
            long fileReference,
            EmailState state,
            boolean attachmentsCleaned,
            @Nullable OffsetDateTime cleanupStartTime
    ) {
        var email = EmailFactory.once()
                .state(state)
                .attachmentsCleaned(attachmentsCleaned)
                .cleanupStartTime(cleanupStartTime)
                .build();

        var attachment = new EmailAttachment();
        attachment.setEmail(email);
        attachment.setFileName("file.png");
        attachment.setContentType("image/png");
        attachment.setFileReference(fileReference);
        email.setAttachments(new HashSet<>(Set.of(attachment)));

        return emailRepository.save(email);
    }
}
