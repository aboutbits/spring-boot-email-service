package it.aboutbits.springboot.emailservice.support.database;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

@Log4j2
public class PostgresTestcontainer implements BeforeAllCallback, AfterEachCallback {
    public static final PostgreSQLContainer<?> POSTGRES_CONTAINER;
    private static final Set<String> TABLES_TO_IGNORE = Set.of(

    );

    static {
        POSTGRES_CONTAINER = new PostgreSQLContainer<>(
                DockerImageName.parse("public.ecr.aws/ubuntu/postgres:14-22.04_edge")
                        .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("app")
                // See https://www.postgresql.org/docs/current/non-durability.html for details
                .withCommand("postgres -c max_connections=1000 -c fsync=off -c synchronous_commit=off -c full_page_writes=off -c max_wal_size=2GB -c checkpoint_timeout=20min")
                .withUsername("admin")
                .withPassword("password")
                .withEnv("TZ", "UTC")       // Container's time zone
                .withEnv("PGTZ", "UTC")     // PostgreSQL's time zone
                .withReuse(true);

        POSTGRES_CONTAINER.start();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
        System.setProperty("spring.datasource.url", POSTGRES_CONTAINER.getJdbcUrl());
        System.setProperty("spring.datasource.username", POSTGRES_CONTAINER.getUsername());
        System.setProperty("spring.datasource.password", POSTGRES_CONTAINER.getPassword());
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        var connection = DriverManager.getConnection(
                POSTGRES_CONTAINER.getJdbcUrl(),
                POSTGRES_CONTAINER.getUsername(),
                POSTGRES_CONTAINER.getPassword()
        );

        try {
            connection.setAutoCommit(false);
            var tablesToClean = loadTablesToClean(connection);
            cleanTablesData(tablesToClean, connection);
            connection.commit();
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    private List<TableData> loadTablesToClean(final Connection connection) throws SQLException {
        var databaseMetaData = connection.getMetaData();
        var resultSet = databaseMetaData.getTables(
                connection.getCatalog(), null, null, new String[]{"TABLE"});

        var tablesToClean = new ArrayList<TableData>();
        while (resultSet.next()) {
            var table = new TableData(
                    resultSet.getString("TABLE_SCHEM"),
                    resultSet.getString("TABLE_NAME")
            );

            if (!TABLES_TO_IGNORE.contains(table.getName())) {
                tablesToClean.add(table);
            }
        }

        return tablesToClean;
    }

    private void cleanTablesData(List<TableData> tablesToClean, Connection connection) throws SQLException {
        if (tablesToClean.isEmpty()) {
            return;
        }

        log.debug("Cleaning Database tables {}", tablesToClean);

        var allTables = new StringJoiner(", ");
        for (TableData table : tablesToClean) {
            allTables.add(table.getFullyQualifiedName());
        }
        String statement = String.format("TRUNCATE %s RESTART IDENTITY CASCADE", allTables);
        connection.prepareStatement(statement).execute();
    }

    @Data
    private static class TableData {
        private final String schema;
        private final String name;

        public String getFullyQualifiedName() {
            return schema + "." + name;
        }
    }
}
