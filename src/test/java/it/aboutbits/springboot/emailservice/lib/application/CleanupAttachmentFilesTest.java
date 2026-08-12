package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import it.aboutbits.springboot.emailservice.lib.model.EmailAttachment;
import it.aboutbits.springboot.emailservice.support.database.WithPostgres;
import it.aboutbits.springboot.emailservice.support.database.factory.EmailFactory;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "aboutbits.emailservice.scheduling.batch-size=5")
@WithPostgres
@NullMarked
class CleanupAttachmentFilesTest {
    private static final int BATCH_SIZE = 5;

    @MockitoBean
    AttachmentDataSource attachmentDataSource;

    @MockitoSpyBean
    JavaMailSender javaMailSender;

    @Autowired
    EmailRepository emailRepository;

    @Autowired
    CleanupAttachmentFiles cleanupAttachmentFiles;

    private ConcurrentHashMap<Long, AtomicInteger> releaseCallsByFileReference = new ConcurrentHashMap<>();

    @BeforeEach
    void setup() throws AttachmentException {
        releaseCallsByFileReference = new ConcurrentHashMap<>();
        doAnswer(inv -> {
            releaseCallsByFileReference.computeIfAbsent(inv.getArgument(0), k -> new AtomicInteger()).incrementAndGet();
            return null;
        }).when(attachmentDataSource).releaseAttachment(anyLong());
    }

    @Test
    void givenSentEmailsWithAttachments_claimAndCleanupAttachments_shouldReleasePayloadsAndMarkCleaned() {
        seedSentEmailsWithAttachments(3);

        cleanupAttachmentFiles.claimAndCleanupAttachments();

        assertThat(emailRepository.findAll())
                .hasSize(3)
                .allMatch(Email::isAttachmentsCleaned);
        assertThat(releaseCallsByFileReference).hasSize(3);
        assertThat(releaseCallsByFileReference.values())
                .allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
    }

    @Test
    void givenMoreEmailsThanBatchSize_claimAndCleanupAttachments_shouldCleanOnlyBatchSizePerPass() {
        var pending = 3 * BATCH_SIZE;
        seedSentEmailsWithAttachments(pending);

        cleanupAttachmentFiles.claimAndCleanupAttachments();

        var cleaned = emailRepository.findAll().stream()
                .filter(Email::isAttachmentsCleaned)
                .count();
        assertThat(cleaned).isEqualTo(BATCH_SIZE);
        assertThat(releaseCallsByFileReference).hasSize(BATCH_SIZE);
    }

    @Test
    void givenConcurrentPasses_claimAndCleanupAttachments_shouldReleaseEachAttachmentExactlyOnce() throws Exception {
        var totalEmails = 25;
        var workerThreads = 4;
        var passesPerWorker = 10;
        seedSentEmailsWithAttachments(totalEmails);

        var startGate = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(workerThreads);
        try {
            for (var t = 0; t < workerThreads; t++) {
                executor.submit(() -> {
                    startGate.await();
                    for (var i = 0; i < passesPerWorker; i++) {
                        cleanupAttachmentFiles.claimAndCleanupAttachments();
                    }
                    return null;
                });
            }
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
        assertThat(releaseCallsByFileReference).hasSize(totalEmails);
        assertThat(releaseCallsByFileReference.values())
                .allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
    }

    private void seedSentEmailsWithAttachments(int count) {
        var fileReferenceCounter = new AtomicInteger(1000);
        var emails = EmailFactory.many(count).stream()
                .map(builder -> {
                    var email = builder.state(EmailState.SENT).attachmentsCleaned(false).build();
                    var attachment = new EmailAttachment();
                    attachment.setEmail(email);
                    attachment.setFileName("payload.bin");
                    attachment.setContentType("application/octet-stream");
                    attachment.setFileReference(fileReferenceCounter.getAndIncrement());
                    var attachments = new HashSet<EmailAttachment>();
                    attachments.add(attachment);
                    email.setAttachments(attachments);
                    return email;
                })
                .toList();
        emailRepository.saveAll(emails);
    }
}
