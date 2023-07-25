package it.aboutbits.springboot.emailservice.lib.jpa;


import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EmailRepository extends JpaRepository<Email, Long> {
    Page<Email> findByState(EmailState state, PageRequest pageRequest);

    List<Email> findByIdIn(Collection<Long> ids);
}
