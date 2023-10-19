package it.aboutbits.springboot.emailservice.support.database;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@ExtendWith({PostgresTestcontainer.class})
@ResourceLock(value = "Database", mode = ResourceAccessMode.READ_WRITE)
public @interface WithPostgres {
}
