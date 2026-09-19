package io.vidocq.tools.lc4jcdi.mcptasks;

import io.vidocq.mansart.data.core.MansartData;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;

/**
 * A fresh in-memory H2 database migrated by Flyway with the application's own scripts (which proves them), and
 * the Mansart-generated repositories on top of it, without any container: Mansart standalone, as its own tests
 * run. No transaction manager is involved, so every repository call autocommits.
 */
final class TasksDatabase implements AutoCloseable {

    final TaskRepository tasks;
    final TaskEventRepository events;
    final int migrationsApplied;
    private final JdbcDataSource dataSource;

    TasksDatabase() {
        String url = "jdbc:h2:mem:tasks-test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        migrationsApplied = Flyway.configure()
                .dataSource(url, "sa", "")
                .load()
                .migrate()
                .migrationsExecuted;
        dataSource = new JdbcDataSource();
        dataSource.setURL(url);
        dataSource.setUser("sa");
        MansartData mansart = MansartData.builder().dataSource(dataSource).build();
        tasks = mansart.repository(TaskRepository.class);
        events = mansart.repository(TaskEventRepository.class);
    }

    TaskService service() {
        TaskService service = new TaskService();
        service.tasks = tasks;
        service.events = events;
        return service;
    }

    TaskQueries queries() {
        TaskQueries queries = new TaskQueries();
        queries.tasks = tasks;
        queries.events = events;
        return queries;
    }

    @Override
    public void close() throws SQLException {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        }
    }
}
