# Spring Boot EMail Service

A reusable mailer service to send emails.

## Setup

Add the mailer service to the classpath by adding the following maven dependency. Versions haven be found [here](../../packages)

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

You will have to enable `EntityScanning` for the package as well as your main package. You must also add it to the base packages for Spring to scan.

```java
@SpringBootApplication(scanBasePackages = {"the.main.package", "it.aboutbits.springboot.emailservice"})
@EntityScan({"the.main.package", "it.aboutbits.springboot.emailservice"})
```

The following configuration options are available:

| Name                                       | Default     | Description                                                             |
|--------------------------------------------|-------------|-------------------------------------------------------------------------|
| `lib.emailservice.scheduling.enabled`      | true        | Enables the scheduler sending the emails.                               |
| `lib.emailservice.scheduling.interval`     | 30000       | Specifies the milliseconds delay between runs of the scheduler.         |

## Building and releasing a new version:

To create a new version of this package and push it to the maven registry, you will have to use the GitHub actions workflow and manually trigger it.

## Information

About Bits is a company based in South Tyrol, Italy. You can find more information about us on [our website](https://aboutbits.it).

### Support

For support, please contact [info@aboutbits.it](mailto:info@aboutbits.it).

### Credits

- [All Contributors](../../contributors)

### License

The MIT License (MIT). Please see the [license file](license.md) for more information.
