# Spring Boot EMail Service

A reusable mailer service to send emails.

## Setup

Add the mailer service to the classpath by adding the following maven dependency. Versions can be found [here](../../packages)

```xml

<dependency>
    <groupId>it.aboutbits.springboot</groupId>
    <artifactId>emailservice</artifactId>
    <version>x.x.x</version>
</dependency>
```

### Attachments

If you want to use attachments, you will have to define a bean implementing this interface: [AttachmentDataSource.java](src%2Fmain%2Fjava%2Fit%2Faboutbits%2Fspringboot%2Femailservice%2Flib%2FAttachmentDataSource.java)  
This step is optional. Where the payloads are stored is a decision of the application: you can provide your own
implementation (e.g. S3), or register the ready-made `JdbcAttachmentDataSource` shipped with the library, which stores
payloads in the lib-owned table `email_service_attachment_payloads`:

```java

@Bean
public AttachmentDataSource attachmentDataSource(JdbcTemplate jdbcTemplate) {
    return new JdbcAttachmentDataSource(jdbcTemplate);
}
```

By default, `JdbcAttachmentDataSource` creates its table on startup. If you prefer to manage the table yourself (e.g. via Liquibase), disable the built-in migration using the constructor-flag and create the table in your own migrations.

#### Inline (CID) attachments

To embed an attachment inline, set a `contentId` on the attachment and reference it in the `htmlBody` via the `cid:` scheme.
This is the equivalent of `MimeMessageHelper.addInline(...)` and renders in all major email clients.

```java
// @formatter:off
EmailParameter.Email.builder()
        // ...
        .htmlBody("<img src=\"cid:header-logo\"><h1>Hello!</h1>")
        .attachment(EmailParameter.Email.Attachment.builder()
                .contentId("header-logo")
                .fileName("logo.png")
                .contentType("image/png")
                .payload(new ClassPathResource("/templates/mail/images/logo.png").getInputStream())
                .build())
        .build();
// @formatter:on
```

Attachments without a `contentId` are added as regular attachments. Each `contentId` must be unique and referenced
in the `htmlBody` as `cid:contentId`, otherwise validation fails.

## Usage

### Sending an Email

`ManageEmail` offers three ways to send an email. They differ in when the email goes out and in how the call relates
to the caller's transaction:

| Method                  | When the email goes out                                                                          | Caller waits for SMTP | Transaction            |
|-------------------------|--------------------------------------------------------------------------------------------------|-----------------------|------------------------|
| `sendAsync(email)`      | Right after the caller's transaction commits, as soon as a dispatcher thread is free; retried by the scheduler | No                    | Required (`MANDATORY`) |
| `schedule(parameter)`   | At `scheduledAt`, by the scheduler; retried                                                      | No                    | Required (`MANDATORY`) |
| `sendOrFail(parameter)` | Immediately, one attempt, `EmailException` to the caller                                         | Yes                   | Not allowed (fails inside one) |

**Pattern:** call `sendAsync` (or `schedule` for a later point in time) inside the `@Transactional` method that writes
the data the email is about (outbox pattern). The email row is written in the caller's transaction, so it commits and
rolls back together with that data, and nothing goes out for a rolled-back transaction. Calling either method without
an active transaction throws an `IllegalTransactionStateException`.

Use `sendOrFail` only outside any transaction, and only when the email is not tied to a database write (e.g. a "send test email" button).
Inside a transaction the SMTP round-trip would run while the caller's database connection is held
and a second connection would be needed to persist the outcome.
Hence, calling it within an active transaction throws an `IllegalTransactionStateException` as well.

Emails that still fail after `scheduling.max-attempts` attempts end up in state `ERROR` and are not retried anymore.
Monitor these rows via `QueryEmail`. The reporting callback described below covers scheduler passes only: emails sent
right after commit by `sendAsync` never appear in it, and an email that fails on that first attempt is reported only
once a later scheduler pass finishes it.

Retries and crash recovery are the scheduler's job, so `sendAsync` and `schedule` need at least one pod that runs it
against the same database (`@EnableScheduling` plus `aboutbits.emailservice.scheduling.enabled=true`). At startup
the library logs an error when scheduling is enabled without `@EnableScheduling`, and a warning when the after-commit
dispatch is enabled while the scheduler is disabled on that pod.

The after-commit dispatch of `sendAsync` can be turned off with `aboutbits.emailservice.async-dispatch.enabled=false`.
`sendAsync` then behaves like `schedule` with "now", and the scheduler alone sends the email. Do this in your tests to
keep them deterministic.

#### Example

```java
// @formatter:off
@Transactional
public void inviteUser(final String to, final String subject, final String htmlBody, final String plainTextBody) {
    // ... write the data the email is about, in the same transaction ...

    manageEmail.sendAsync(
            EmailParameter.Email.builder()
                    .fromName(fromName)
                    .fromAddress(fromAddress)
                    .recipient(to)
                    .subject(subject)
                    .textBody(plainTextBody)
                    .htmlBody(htmlBody)
                    .build()
    );
}
// @formatter:on
```

To send at a later point in time, use `schedule` with an `EmailParameter` carrying the `scheduledAt`:

```java
// @formatter:off
@Transactional
public void remindLater(final String to, final String subject, final String htmlBody, final String plainTextBody) {
    manageEmail.schedule(
            EmailParameter.builder()
                    .scheduledAt(OffsetDateTime.now().plusDays(1))
                    .email(EmailParameter.Email.builder()
                            .fromName(fromName)
                            .fromAddress(fromAddress)
                            .recipient(to)
                            .subject(subject)
                            .textBody(plainTextBody)
                            .htmlBody(htmlBody)
                            .build())
                    .build()
    );
}
// @formatter:on
```

### Querying Emails

To read email datasets from the database use this class: [QueryEmail.java](src%2Fmain%2Fjava%2Fit%2Faboutbits%2Fspringboot%2Femailservice%2Flib%2Fapplication%2FQueryEmail.java)

### Reporting callback

If you want to receive a report after each run of the scheduler, create a Bean implementing [EmailSchedulerCallback.java](src%2Fmain%2Fjava%2Fit%2Faboutbits%2Fspringboot%2Femailservice%2Flib%2FEmailSchedulerCallback.java)

The report covers that scheduler pass only. Emails sent right after commit by `sendAsync` are not included. The
after-commit dispatch performs the first attempt only, every retry runs through the scheduler, so an email that fails
on that first attempt shows up once a scheduler pass finishes it. With the default `scheduling.max-attempts` every
`ERROR` therefore reaches the callback; with `scheduling.max-attempts=1` the `sendAsync` ones do not. Use `QueryEmail`
for a complete picture.

### Configuration

To enable this service just add `@EnableEmailService` to your main class. You must also enable `@EnableScheduling` to allow the email queue to be processed: without it the scheduler bean exists but never runs, and the library logs an error at startup.

```java

@SpringBootApplication
@EnableEmailService
@EnableScheduling
public class App {
    public static void main(final String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

The following configuration options are available:

| Name                                                                    | Default | Description                                                                                                            |
|-------------------------------------------------------------------------|---------|------------------------------------------------------------------------------------------------------------------------|
| `aboutbits.emailservice.migrations.enabled`                             | true    | Enables database migrations.                                                                                           |
| `aboutbits.emailservice.scheduling.enabled`                             | true    | Enables the scheduler sending the emails. The scheduler also performs the retries and crash recovery for `sendAsync`, so when it is disabled on a pod, another pod must run it against the same database (the library warns at startup when the after-commit dispatch is enabled without it). |
| `aboutbits.emailservice.scheduling.cleanup.enabled`                     | true    | Enables cleanup of attachment files after sending.                                                                     |
| `aboutbits.emailservice.scheduling.interval`                            | 30000   | Milliseconds delay between runs of the scheduler.                                                                      |
| `aboutbits.emailservice.scheduling.stuck-sending-recovery-threshold`    | PT30M   | How long an email may stay in `SENDING` before being considered abandoned (crashed pod) and eligible to be re-claimed. Must comfortably exceed the worst-case SMTP send duration: JavaMail's default connect/read/write timeouts are infinite, so configure `spring.mail.properties.mail.smtp.connectiontimeout`, `spring.mail.properties.mail.smtp.timeout` and `spring.mail.properties.mail.smtp.writetimeout` well below this threshold, otherwise a slow in-flight send can be re-claimed by another pod and delivered twice. This applies to sends of the after-commit dispatch as well. |
| `aboutbits.emailservice.scheduling.stuck-cleanup-recovery-threshold`    | PT30M   | How long an email may keep its attachment-cleanup lock before the cleanup is considered abandoned (crashed pod) and eligible to be re-claimed. Must comfortably exceed the worst-case duration of releasing all attachments of a single email, otherwise a slow in-flight cleanup can be re-claimed by another pod and its attachments released twice (harmless only if `AttachmentDataSource.releaseAttachment` is idempotent). |
| `aboutbits.emailservice.async-dispatch.enabled`                         | true    | Sends emails queued via `sendAsync` right after the caller's transaction committed, on a bounded executor. When disabled, such emails are sent by the scheduler only (recommended for tests). On shutdown the library waits up to 30 seconds for queued and in-flight sends. Queued sends that have not started by then are dropped and stay `PENDING` for the scheduler; a send still in flight finishes if it can, otherwise its row stays `SENDING` until the recovery threshold. |
| `aboutbits.emailservice.async-dispatch.threads`                         | 1       | Number of threads of the after-commit executor, i.e. how many SMTP sends run in parallel per pod. The default of one matches the scheduler, which sends sequentially. Must be at least 1. |
| `aboutbits.emailservice.async-dispatch.queue-capacity`                  | 100     | Maximum number of emails waiting for a thread of the after-commit executor. When the queue is full, the hand-off is dropped and the scheduler sends the email instead. Must be at least 1. |
| `aboutbits.emailservice.scheduling.max-attempts`                        | 3       | Maximum number of send attempts before an email is marked as `ERROR`. Applies to `sendAsync` and `schedule`; the attempt right after commit counts as the first one. `sendOrFail` makes exactly one attempt and is never retried. Failed attempts are retried with exponential backoff (`scheduling.interval` × 2^attempts); with the defaults a persistently failing email runs attempt 1 → +60s → attempt 2 → +120s → attempt 3 → `ERROR`. |

## Multi-pod deployments

Both schedulers are safe to run on every pod concurrently: the database arbitrates which pod handles each email.
The after-commit dispatch of `sendAsync` takes part in the same arbitration, so it never collides with a scheduler pass.
If the scheduler is disabled on some pods (e.g. web pods), at least one pod with access to the same database must run it, because it performs the retries and crash recovery for `sendAsync` as well.
Delivery is at-least-once - if a pod crashes after the SMTP server accepted the message but before the result was persisted, the email may be sent again on recovery.
Attachment Cleanup is at-least-once - if a pod crashes after releasing the attachments but before the result was persisted, the cleanup may be retried on recovery.

## Local development:

To use this library as a local development dependency, you can simply refer to the version `BUILD-SNAPSHOT`.

Check out this repository and run the maven goal `install`. This will build and install this library as version `BUILD-SNAPSHOT` into your local maven cache.

Note that you may have to tell your IDE to reload your main maven project each time you build the library.

## Build & Publish

To build and publish the chart, visit the GitHub Actions page of the repository and trigger the workflow "Release Package" manually.

## Information

About Bits is a company based in South Tyrol, Italy. You can find more information about us on [our website](https://aboutbits.it).

### Support

For support, please contact [info@aboutbits.it](mailto:info@aboutbits.it).

### Credits

- [All Contributors](../../contributors)

### License

The MIT License (MIT). Please see the [license file](license.md) for more information.
