package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.EmailAttachmentDto;
import it.aboutbits.springboot.emailservice.lib.model.EmailAttachment;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.mapstruct.AnnotateWith;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
@AnnotateWith(NullUnmarked.class)
@NullMarked
public interface EmailAttachmentMapper {
    EmailAttachmentDto toDto(EmailAttachment model);

    List<EmailAttachmentDto> toDto(List<EmailAttachment> model);
}
