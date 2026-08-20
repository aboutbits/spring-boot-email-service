package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.EmailDto;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    List<Email> readyToCleanup() {
        return emailRepository.findReadyToCleanup();
    }

    public Optional<EmailDto> byId(long id) {
        return emailRepository.findById(id).map(emailMapper::toDto);
    }
}
