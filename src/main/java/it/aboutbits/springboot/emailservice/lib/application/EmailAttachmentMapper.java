package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.AttachmentReference;
import it.aboutbits.springboot.emailservice.lib.EmailAttachmentDto;
import it.aboutbits.springboot.emailservice.lib.model.EmailAttachment;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface EmailAttachmentMapper {
    EmailAttachmentDto toDto(EmailAttachment model);

    default AttachmentReference map(String value) {
        return new AttachmentReference(value);
    }

    List<EmailAttachmentDto> toDto(List<EmailAttachment> model);
}
