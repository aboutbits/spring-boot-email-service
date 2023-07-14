package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.EmailDto;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

@Mapper(uses = EmailAttachmentMapper.class)
public interface EmailMapper {
    EmailDto toDto(Email model);

    List<EmailDto> toDto(List<Email> model);

    default Page<EmailDto> toDto(Page<Email> model) {
        return new PageImpl<>(
                toDto(model.getContent()),
                model.getPageable(),
                model.getTotalElements()
        );
    }
}
