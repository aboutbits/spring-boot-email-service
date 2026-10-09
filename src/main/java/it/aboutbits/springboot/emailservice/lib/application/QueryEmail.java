package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.EmailDto;
import it.aboutbits.springboot.emailservice.lib.EmailMetrics;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toMap;

@RequiredArgsConstructor
@NullMarked
public class QueryEmail {
    private final EmailRepository emailRepository;
    private final EmailMapper emailMapper;

    public Page<EmailDto> paginatedByState(EmailState state, PageRequest pageParameter) {
        var pageRequest = PageRequest.of(
                pageParameter.getPageNumber(),
                pageParameter.getPageSize(),
                Sort.by("updatedAt")
        );

        return emailMapper.toDto(emailRepository.findByState(state, pageRequest));
    }

    public List<EmailDto> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return emailMapper.toDto(emailRepository.findByIdIn(ids));
    }

    List<Long> candidateIdsToSend(OffsetDateTime staleSendingBefore) {
        return emailRepository.findCandidateIdsToSend(
                OffsetDateTime.now(),
                staleSendingBefore
        );
    }

    List<Long> candidateIdsToCleanup(OffsetDateTime staleCleanupBefore) {
        return emailRepository.findCandidateIdsToCleanup(staleCleanupBefore);
    }

    // Only PENDING and SENDING are counted; see EmailMetrics.QueueSnapshot for why the terminal states are not.
    EmailMetrics.QueueSnapshot queueSnapshot() {
        var now = OffsetDateTime.now();

        var counts = emailRepository.countByStateIn(List.of(EmailState.PENDING, EmailState.SENDING)).stream()
                .collect(toMap(EmailRepository.StateCount::getState, EmailRepository.StateCount::getTotal));

        return new EmailMetrics.QueueSnapshot(
                counts.getOrDefault(EmailState.PENDING, 0L),
                counts.getOrDefault(EmailState.SENDING, 0L),
                emailRepository.findOldestDueScheduledAt(now)
                        .map(scheduledAt -> Duration.between(scheduledAt, now))
                        .orElse(null)
        );
    }

    public Optional<EmailDto> byId(long id) {
        return emailRepository.findById(id).map(emailMapper::toDto);
    }
}
