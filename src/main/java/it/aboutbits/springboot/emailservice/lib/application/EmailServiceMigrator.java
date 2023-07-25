package it.aboutbits.springboot.emailservice.lib.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
public class EmailServiceMigrator {
    private final JdbcTemplate jdbcTemplate;

    public EmailServiceMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void migrate() {
        log.info("EmailService: running DB migrations...");

        jdbcTemplate.execute("""

                 create table if not exists email_service_emails
                 (
                     id                   bigint generated by default as identity
                         primary key,
                     state                text                     default 'PENDING'::text not null,
                     subject              text                                             not null,
                     recipients           jsonb                                            not null,
                     from_address         text                                             not null,
                     from_name            text                                             not null,
                     text_body            text                                             not null,
                     html_body            text                                             not null,
                     attachments          jsonb                    default '[]'::jsonb,
                     attachments_cleaned  bool                     default false           not null,
                     scheduled_at timestamp with time zone default now()                   not null,
                     sent_at              timestamp with time zone,
                     error_at             timestamp with time zone,
                     error_message        text,
                     created_at           timestamp with time zone default now()           not null,
                     updated_at           timestamp with time zone default now()           not null,
                     constraint email_service_emails_one_body_not_empty
                         check ((text_body <> ''::text) OR (html_body <> ''::text))
                 );

                 create index if not exists email_service_emails_state_index
                     on email_service_emails (state);

                 create index if not exists email_service_emails_scheduled_at_index
                     on email_service_emails (scheduled_at);
                     
                 create index if not exists email_service_emails_attachments_cleaned_index
                     on email_service_emails (attachments_cleaned);

                 create table if not exists email_service_email_attachments
                 (
                     id           bigint generated by default as identity
                         primary key,
                     email_id     bigint not null
                         constraint email_service_email_attachments_email_id
                             references email_service_emails,
                     file_name    text   not null,
                     content_type text   not null,
                     reference    text   not null
                 );

                 create index if not exists email_service_email_attachments_email_id_index
                     on email_service_email_attachments (email_id);

                """);

        log.info("EmailService: migrations done!");
    }
}
