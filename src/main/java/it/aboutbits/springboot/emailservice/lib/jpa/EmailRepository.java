package it.aboutbits.springboot.emailservice.lib.jpa;


import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@NullMarked
public interface EmailRepository extends JpaRepository<Email, Long> {
    @Override
    @EntityGraph(value = Email.DEFAULT_ENTITY_GRAPH)
    Optional<Email> findById(Long id);

    @Override
    @EntityGraph(value = Email.DEFAULT_ENTITY_GRAPH)
    List<Email> findAllById(Iterable<Long> ids);

    @Override
    @EntityGraph(value = Email.DEFAULT_ENTITY_GRAPH)
    List<Email> findAll();

    @EntityGraph(value = Email.DEFAULT_ENTITY_GRAPH)
    Page<Email> findByState(EmailState state, PageRequest pageRequest);

    @EntityGraph(value = Email.DEFAULT_ENTITY_GRAPH)
    List<Email> findByIdIn(Collection<Long> ids);

    // Plain read, no locking -> two pods may see overlapping candidate sets.
    // The atomic UPDATE in claimForSend arbitrates the actual claim.
    // Includes SENDING rows abandoned by crashed pods (past the stale threshold).
    @Query("""
            select e.id from Email e
                where e.scheduledAt < :now
                    and (
                        e.state = it.aboutbits.springboot.emailservice.lib.EmailState.PENDING
                        or (
                            e.state = it.aboutbits.springboot.emailservice.lib.EmailState.SENDING
                            and e.executionStartTime < :staleSendingBefore
                        )
                    )
            order by e.scheduledAt
            limit :limit
            """)
    List<Long> findCandidateIdsToSend(
            @Param("now") OffsetDateTime now,
            @Param("staleSendingBefore") OffsetDateTime staleSendingBefore
    );

    // Atomic compare-and-set claim: transitions a single row into SENDING if its
    // current state is claimable (PENDING, or SENDING abandoned by a crashed pod past the stale threshold).
    // Concurrent updates are serialized against the same row, so EXACTLY ONE caller gets returned 1.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Email e
               set e.state = it.aboutbits.springboot.emailservice.lib.EmailState.SENDING,
                   e.executionStartTime = :now,
                   e.executionEndTime = null,
                   e.errorMessage = null,
                   e.attempts = e.attempts + 1
               where e.id = :id
                   and e.scheduledAt < :now
                   and (
                       e.state = it.aboutbits.springboot.emailservice.lib.EmailState.PENDING
                       or (
                          e.state = it.aboutbits.springboot.emailservice.lib.EmailState.SENDING
                          and e.executionStartTime < :staleSendingBefore
                       )
                   )
            """)
    int claimForSend(
            @Param("id") long id,
            @Param("now") OffsetDateTime now,
            @Param("staleSendingBefore") OffsetDateTime staleSendingBefore
    );

    @EntityGraph(value = Email.DEFAULT_ENTITY_GRAPH)
    @Query("""
            select e from Email e
                where e.attachmentsCleaned = false
                    and e.state = it.aboutbits.springboot.emailservice.lib.EmailState.SENT
            """)
    List<Email> findReadyToCleanup();
}
