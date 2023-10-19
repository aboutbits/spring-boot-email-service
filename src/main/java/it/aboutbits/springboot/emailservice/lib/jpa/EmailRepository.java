package it.aboutbits.springboot.emailservice.lib.jpa;


import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmailRepository extends JpaRepository<Email, Long> {
    @Override
    @NonNull
    @EntityGraph(value = Email.DEFAULT_ENTITY_GRAPH)
    Optional<Email> findById(@NonNull Long id);

    @Override
    @NonNull
    @EntityGraph(value = Email.DEFAULT_ENTITY_GRAPH)
    List<Email> findAllById(@NonNull Iterable<Long> ids);

    @Override
    @NonNull
    @EntityGraph(value = Email.DEFAULT_ENTITY_GRAPH)
    List<Email> findAll();

    @EntityGraph(value = Email.DEFAULT_ENTITY_GRAPH)
    Page<Email> findByState(EmailState state, PageRequest pageRequest);

    @EntityGraph(value = Email.DEFAULT_ENTITY_GRAPH)
    List<Email> findByIdIn(Collection<Long> ids);
}
