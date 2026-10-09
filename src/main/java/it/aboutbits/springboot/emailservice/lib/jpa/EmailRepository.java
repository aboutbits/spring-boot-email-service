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

    // Backlog readings for the metrics, one round trip for all states asked for. States without a
    // single row are simply absent from the result.
    @Query("""
            select e.state as state, count(e) as total from Email e
                where e.state in :states
                group by e.state
            """)
    List<StateCount> countByStateIn(@Param("states") Collection<EmailState> states);

    // How far behind the queue is: the schedule time of the oldest email that is already due.
    // Empty if nothing is waiting.
    @Query("""
            select min(e.scheduledAt) from Email e
                where e.state = it.aboutbits.springboot.emailservice.lib.EmailState.PENDING
                    and e.scheduledAt < :now
            """)
    Optional<OffsetDateTime> findOldestDueScheduledAt(@Param("now") OffsetDateTime now);

    // Plain read, no locking -> two pods may see overlapping candidate sets.
    // The atomic UPDATE in claimForCleanup arbitrates the actual claim.
    // Includes rows whose cleanup was abandoned by a crashed pod (past the stale threshold).
    @Query("""
            select e.id from Email e
                where e.attachmentsCleaned = false
                    and e.state = it.aboutbits.springboot.emailservice.lib.EmailState.SENT
                    and (
                        e.cleanupStartTime is null
                        or e.cleanupStartTime < :staleCleanupBefore
                    )
            """)
    List<Long> findCandidateIdsToCleanup(@Param("staleCleanupBefore") OffsetDateTime staleCleanupBefore);

    // Atomic compare-and-set claim: marks a single not-yet-cleaned SENT row as cleanup-in-progress
    // by stamping cleanupStartTime, if it is claimable (never started, or abandoned by a crashed pod past the stale threshold).
    // Concurrent updates are serialized against the same row, so EXACTLY ONE caller gets returned 1.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Email e
               set e.cleanupStartTime = :now
               where e.id = :id
                   and e.attachmentsCleaned = false
                   and e.state = it.aboutbits.springboot.emailservice.lib.EmailState.SENT
                   and (
                       e.cleanupStartTime is null
                       or e.cleanupStartTime < :staleCleanupBefore
                   )
            """)
    int claimForCleanup(
            @Param("id") long id,
            @Param("now") OffsetDateTime now,
            @Param("staleCleanupBefore") OffsetDateTime staleCleanupBefore
    );

    interface StateCount {
        EmailState getState();

        long getTotal();
    }
}
