package com.example.liqiubasejakartademo.liquibase;


import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jboss.logging.Logger;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Startup
@Singleton
public class LiquibaseStartupUpdate {

    private static final Logger LOG = Logger.getLogger(LiquibaseStartupUpdate.class);

    private PostgreSQLContainer postgreSQLContainer = null;

    @Resource(lookup = "java:jboss/datasources/liquibase")
    private DataSource dataSource;

    @PostConstruct
    void init() throws SQLException {
        if (dataSource == null) {
            startDatabase();
        }

        Connection conn;
        if (this.postgreSQLContainer != null) {
            conn = DriverManager.getConnection(
                postgreSQLContainer.getJdbcUrl(), "postgres", "postgres");

            if (conn != null) {
                LOG.info("Connected to the database!");
            } else {
                LOG.info("Failed to make connection!");
            }
        } else {
            LOG.info("Getting connection from Datasource");
            conn = dataSource.getConnection();
        }

        try {
            Map<String, Object> config = new HashMap<>();
            Scope.child(config, () -> {
                Database database = DatabaseFactory
                        .getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(conn));
                try (Liquibase liquibase = new Liquibase("changelog.xml", new ClassLoaderResourceAccessor(), database)) {
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            });
        } catch (SQLException e) {
            LOG.error(String.format("SQL State: %s\n%s", e.getSQLState(), e.getMessage()), e);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (this.postgreSQLContainer != null) {
                conn.close();
            }
        }
    }

    private void startDatabase() {
        postgreSQLContainer = new PostgreSQLContainer<>("postgres:15.0")
                .withDatabaseName("postgres")
                .withUsername("postgres")
                .withPassword("postgres")
                .withExposedPorts(5432)
                .withCreateContainerCmdModifier(cmd -> cmd.withHostConfig(
                        new HostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(5434), new ExposedPort(5434)))
                ));
        postgreSQLContainer.start();
        LOG.info("Postgresql started at " + postgreSQLContainer.getJdbcUrl());
    }

    @PreDestroy
    public void destroy() {
        this.postgreSQLContainer.stop();
    }

}
