package it.aboutbits.springboot.emailservice.lib;

public interface AttachmentCleanerCallback {
    void report(Report report);

    record Report(
            int total,
            int cleaned,
            int errors
    ) {
    }
}
