package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.EmailAttachmentDto;
import it.aboutbits.springboot.emailservice.lib.model.EmailAttachment;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface EmailAttachmentMapper {
    EmailAttachmentDto toDto(EmailAttachment model);

    List<EmailAttachmentDto> toDto(List<EmailAttachment> model);
}
