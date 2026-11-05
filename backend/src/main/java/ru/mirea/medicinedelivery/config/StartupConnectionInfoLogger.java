package ru.mirea.medicinedelivery.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Выводит безопасную памятку с локальными адресами сервиса после полной готовности Spring Boot.
 */
@Component
@Profile("!prod")
public final class StartupConnectionInfoLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupConnectionInfoLogger.class);

    private final int appPort;
    private final int postgresPort;
    private final String postgresDatabase;
    private final String postgresUser;

    public StartupConnectionInfoLogger(
            @Value("${APP_PORT:8080}") int appPort,
            @Value("${POSTGRES_PORT:55432}") int postgresPort,
            @Value("${POSTGRES_DB:medicine_delivery}") String postgresDatabase,
            @Value("${POSTGRES_USER:medicine_delivery_app}") String postgresUser) {
        this.appPort = appPort;
        this.postgresPort = postgresPort;
        this.postgresDatabase = postgresDatabase;
        this.postgresUser = postgresUser;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void printConnectionInfo() {
        String base = "http://localhost:" + appPort;
        String postgresJdbc = "jdbc:postgresql://localhost:" + postgresPort + "/" + postgresDatabase;

        log.info("""

                ============================================================
                 MEDICINE DELIVERY SERVICE IS READY
                ============================================================
                 Web UI:           {}/
                 Swagger UI:       {}/swagger-ui.html
                 REST API:         {}/api
                 OpenAPI JSON:     {}/v3/api-docs
                 Health:           {}/actuator/health
                 JavaFX API:       {}/api
                 Android emulator: http://10.0.2.2:{}/api
                 PostgreSQL JDBC:  {}
                 PostgreSQL user:  {}
                 Passwords/secrets are not printed.
                ============================================================
                """,
                base, base, base, base, base, base, appPort, postgresJdbc, postgresUser);
    }
}
