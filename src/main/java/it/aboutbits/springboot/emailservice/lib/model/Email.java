package it.aboutbits.springboot.emailservice.lib.model;

import it.aboutbits.springboot.emailservice.lib.EmailState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import static it.aboutbits.springboot.emailservice.lib.model.Email.DEFAULT_ENTITY_GRAPH;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "email_service_emails")
@NamedEntityGraph(name = DEFAULT_ENTITY_GRAPH, attributeNodes = @NamedAttributeNode("attachments"))
@NullUnmarked
public class Email {
    public static final String DEFAULT_ENTITY_GRAPH = "graph.EmailServiceEmail.default";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private EmailState state;

    @Embedded
    private EmailContent content;

    @Builder.Default
    @OneToMany(cascade = CascadeType.PERSIST, mappedBy = "email", orphanRemoval = true)
    private Set<EmailAttachment> attachments = new HashSet<>();

    @Builder.Default
    private boolean attachmentsCleaned = false;

    private OffsetDateTime scheduledAt;
    @Nullable
    private OffsetDateTime executionStartTime;
    @Nullable
    private OffsetDateTime executionEndTime;

    private int attempts = 0;

    @Nullable
    private String errorMessage;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;

    public void incrementAttempts() {
        this.attempts++;
    }
}
