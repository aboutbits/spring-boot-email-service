package it.aboutbits.springboot.emailservice.lib;

import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

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
        OffsetDateTime sentAt,

        OffsetDateTime errorAt,
        String errorMessage,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
