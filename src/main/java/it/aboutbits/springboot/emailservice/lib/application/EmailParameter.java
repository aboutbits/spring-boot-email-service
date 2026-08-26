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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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

            @Nullable
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
        // the "cid:" scheme is case-insensitive, the contentId itself is not
        private static final Pattern CID_REFERENCE_PATTERN = Pattern.compile(
                "cid:([^\\s\"'<>]+)",
                Pattern.CASE_INSENSITIVE
        );

        @AssertTrue(message = "each inline attachment contentId must be referenced in the htmlBody as cid:contentId")
        public boolean isInlineAttachmentsValid() {
            var contentIds = inlineContentIds();
            if (contentIds.isEmpty()) {
                return true;
            }

            if (htmlBody == null) {
                return false;
            }

            var referencedContentIds = new HashSet<String>();
            var matcher = CID_REFERENCE_PATTERN.matcher(htmlBody);
            while (matcher.find()) {
                referencedContentIds.add(matcher.group(1));
            }

            return referencedContentIds.containsAll(contentIds);
        }

        @AssertTrue(message = "each inline attachment contentId must be unique")
        public boolean isInlineAttachmentContentIdsUnique() {
            var contentIds = inlineContentIds();
            return contentIds.size() == new HashSet<>(contentIds).size();
        }

        private List<String> inlineContentIds() {
            return attachments.stream()
                    .map(Attachment::contentId)
                    .filter(Objects::nonNull)
                    .toList();
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
