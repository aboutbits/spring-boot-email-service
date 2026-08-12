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

If you want to use attachments, you will have to create a bean implementing this interface: [AttachmentDataSource.java](src%2Fmain%2Fjava%2Fit%2Faboutbits%2Fspringboot%2Femailservice%2Flib%2FAttachmentDataSource.java)  
This step is optional.

## Usage

### Sending an Email

Use the `ManageEmail` service to schedule an email.

#### Example

```java
// @formatter:off
public void sendMail(final String to,final String subject,final String htmlBody,final String plainTextBody) {
        manageEmail.schedule(
                EmailParameter.builder()
                        .scheduleAt(OffsetDateTime.now())
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

### Configuration

To enable this service just add `@EnableEmailService` to your main class. You must also enable `@EnableScheduling` to allow the email queue to be processed.

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

| Name                                            | Default | Description                                                                                                    |
|-------------------------------------------------|---------|----------------------------------------------------------------------------------------------------------------|
| `aboutbits.emailservice.migrations.enabled`     | true    | Enables database migrations.                                                                                   |
| `aboutbits.emailservice.scheduling.enabled`     | true    | Enables the scheduler sending the emails.                                                                      |
| `aboutbits.emailservice.scheduling.cleanup.enabled` | true    | Enables cleanup of attachment files after sending.                                                             |
| `aboutbits.emailservice.scheduling.interval`    | 30000   | Specifies the milliseconds delay between runs of the scheduler.                                                |
| `aboutbits.emailservice.scheduling.batch-size`  | 50      | Maximum number of emails a single pod claims per scheduler pass (see [Multi-pod deployments](#multi-pod-deployments)). |

## Multi-pod deployments

The scheduler is safe to run in every pod concurrently. Each ready email is
claimed by exactly one pod using Postgres row-level `SELECT ... FOR UPDATE SKIP LOCKED`,
so pods work on disjoint rows in parallel and no email is ever sent twice by
different pods. The same guarantee applies to the attachment cleanup scheduler.

`aboutbits.emailservice.scheduling.batch-size` caps the number of emails one
pod processes per pass. With the default 30s interval and 50 emails per pass,
a single pod can take up to 100 emails per minute; For higher-throughput deployments increase the batch size or
lower the interval.

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
