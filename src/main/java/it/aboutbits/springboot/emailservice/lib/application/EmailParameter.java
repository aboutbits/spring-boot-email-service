package it.aboutbits.springboot.emailservice.lib.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Singular;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Builder
@NullMarked
public record EmailParameter(
        OffsetDateTime scheduledAt,
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
            Set<Attachment> attachments
    ) {
        @Builder
        public record Attachment(
                InputStream payload,
                @NotBlank
                String fileName,
                @NotBlank
                String contentType
        ) {
        }
    }
}
