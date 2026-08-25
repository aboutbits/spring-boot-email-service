package it.aboutbits.springboot.emailservice.lib.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Singular;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Builder
@NullMarked
public record EmailParameter(
        OffsetDateTime scheduledAt,
        @Valid
        Email email
) {
    @Builder
    public record Email(
            @NotBlank
            String subject,

            @Singular
            @NotEmpty
            List<String> recipients,

            String textBody,
            String htmlBody,

            @NotBlank
            String fromAddress,
            @NotBlank
            String fromName,

            @Nullable
            String replyToAddress,
            @Nullable
            String replyToName,

            @Singular
            @Valid
            Set<Attachment> attachments
    ) {
        @AssertTrue(message = "each inline attachment contentId must be referenced in the htmlBody as cid:contentId")
        public boolean isInlineAttachmentsValid() {
            return attachments.stream()
                    .map(Attachment::contentId)
                    .filter(Objects::nonNull)
                    .allMatch(contentId -> htmlBody.contains("cid:" + contentId));
        }

        @Builder
        public record Attachment(
                InputStream payload,
                @NotBlank
                String fileName,
                @NotBlank
                String contentType,
                // if set, the attachment is embedded inline and can be referenced in the htmlBody as "cid:contentId"
                @Nullable
                String contentId
        ) {
        }
    }
}
