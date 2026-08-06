package it.aboutbits.springboot.emailservice.lib;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface AttachmentCleanerCallback {
    void report(Report report);

    record Report(
            int total,
            int cleaned,
            int errors
    ) {
    }
}
