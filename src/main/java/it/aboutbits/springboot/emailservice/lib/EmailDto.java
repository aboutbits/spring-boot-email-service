package it.aboutbits.springboot.emailservice.lib;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@NullMarked
public record EmailDto(
        long id,

        EmailState state,

        String subject,

        String fromAddress,
        String fromName,

        @Nullable
        String replyToAddress,
        @Nullable
        String replyToName,

        List<String> recipients,

        String textBody,
        String htmlBody,

        Set<EmailAttachmentDto> attachments,

        OffsetDateTime scheduledAt,
        @Nullable
        OffsetDateTime sentAt,

        @Nullable
        OffsetDateTime errorAt,
        @Nullable
        String errorMessage,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
