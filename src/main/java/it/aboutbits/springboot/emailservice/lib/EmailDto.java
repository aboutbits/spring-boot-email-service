package it.aboutbits.springboot.emailservice.lib;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public record EmailDto(
        long id,

        EmailState state,

        String subject,

        String fromAddress,
        String fromName,

        List<String> recipients,

        String textBody,
        String htmlBody,

        Set<EmailAttachmentDto> attachments,

        String reference,

        OffsetDateTime sendingScheduledAt,
        OffsetDateTime sentAt,

        OffsetDateTime errorAt,
        String errorMessage,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
