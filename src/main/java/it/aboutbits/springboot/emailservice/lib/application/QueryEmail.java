package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class QueryEmail {
    private final EmailRepository emailNotificationRepository;

    public Page<Email> paginatedByState(final EmailState state, final PageRequest pageParameter) {
        var pageRequest = PageRequest.of(pageParameter.getPageNumber(), pageParameter.getPageSize(), Sort.by("updatedAt"));

        return emailNotificationRepository.findByState(state, pageRequest);
    }

    public List<Email> byIds(final Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return emailNotificationRepository.findByIdIn(ids);
    }

    public List<Email> readyToSend() {
        return emailNotificationRepository.findReadyToSend(OffsetDateTime.now());
    }

    public Optional<Email> byId(final long id) {
        return emailNotificationRepository.findById(id);
    }
}
