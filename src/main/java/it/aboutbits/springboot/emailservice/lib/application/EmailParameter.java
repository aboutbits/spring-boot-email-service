package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.AttachmentReference;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Builder
public record EmailParameter(
        @NonNull
        OffsetDateTime scheduledAt,
        @NonNull
        Email email
) {

    @Builder
    public record Email(
            @NonNull
            @NotBlank
            String subject,

            @Singular
            @NonNull
            @NotEmpty
            List<String> recipients,

            @NonNull String textBody,
            @NonNull String htmlBody,

            @NonNull
            @NotBlank
            String fromAddress,
            @NonNull
            @NotBlank
            String fromName,

            @Singular
            @NonNull
            Set<Attachment> attachments
    ) {
        @Builder
        public record Attachment(
                @NonNull
                AttachmentReference reference,
                @NonNull
                @NotBlank
                String fileName,
                @NonNull
                @NotBlank
                String contentType
        ) {
        }
    }
}
