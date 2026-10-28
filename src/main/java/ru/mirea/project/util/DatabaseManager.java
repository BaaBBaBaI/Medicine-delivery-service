package ru.mirea.project.util;

import ru.mirea.project.exception.DatabaseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Менеджер подключения к базе данных PostgreSQL через JDBC (Singleton).
 * Читает параметры подключения из src/main/resources/database.properties (и application.properties).
 */
public class DatabaseManager {
    private static final Logger log = Logger.getLogger(DatabaseManager.class.getName());
    private static volatile DatabaseManager instance;

    private String url;
    private String user;
    private String password;
    private String driver;

    private DatabaseManager() {
        loadProperties();
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    private void loadProperties() {
        Properties props = new Properties();

        // 1. Загрузка из classpath: сначала application.properties, затем приоритетный database.properties
        loadFromClasspath(props, "application.properties");
        loadFromClasspath(props, "database.properties");

        // 2. Чтение из локальных файлов при наличии
        loadFromFile(props, new File("src/main/resources/application.properties"));
        loadFromFile(props, new File("src/main/resources/database.properties"));
        loadFromFile(props, new File("application.properties"));
        loadFromFile(props, new File("database.properties"));

        this.driver = System.getProperty("db.driver", props.getProperty("db.driver", "org.postgresql.Driver"));
        this.url = System.getProperty("db.url", props.getProperty("db.url"));
        this.user = System.getProperty("db.user", props.getProperty("db.user"));
        this.password = System.getProperty("db.password", props.getProperty("db.password"));

        try {
            Class.forName(this.driver);
        } catch (Exception e) {
            throw new DatabaseException("Ошибка загрузки JDBC-драйвера PostgreSQL: " + e.getMessage(), e);
        }
    }

    private void loadFromClasspath(Properties props, String resourceName) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in != null) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Не удалось прочитать ресурс " + resourceName, e);
        }
    }

    private void loadFromFile(Properties props, File file) {
        if (file.exists() && file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.log(Level.WARNING, "Не удалось прочитать файл " + file.getPath(), e);
            }
        }
    }

    public Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            if ("3D000".equals(e.getSQLState()) && url != null && url.contains("/pharmacy_db")) {
                createDatabaseIfNotExists();
                return DriverManager.getConnection(url, user, password);
            }
            throw e;
        }
    }

    private synchronized void createDatabaseIfNotExists() {
        String adminUrl = url.replace("/pharmacy_db", "/postgres");
        try (Connection adminConn = DriverManager.getConnection(adminUrl, user, password);
             Statement stmt = adminConn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE pharmacy_db");
            log.info("База данных 'pharmacy_db' была автоматически создана.");
        } catch (Exception e) {
            log.log(Level.FINE, "Автосоздание БД pharmacy_db пропущено: " + e.getMessage());
        }
    }

    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            log.warning("Не удалось подключиться к PostgreSQL (" + url + "): " + e.getMessage());
            return false;
        }
    }

    public void initializeDatabaseWithDemoData() {
        executeScript("schema.sql");
        executeScript("data.sql");
    }

    public void executeScript(String resourcePath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new DatabaseException("SQL-скрипт не найден в ресурсах: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                 Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                StringBuilder sqlBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                        continue;
                    }
                    sqlBuilder.append(line).append(" ");
                    if (trimmed.endsWith(";")) {
                        String sql = sqlBuilder.toString().trim();
                        if (!sql.isEmpty()) {
                            stmt.execute(sql);
                        }
                        sqlBuilder.setLength(0);
                    }
                }
            }
        } catch (Exception e) {
            throw new DatabaseException("Ошибка выполнения скрипта " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public void setPassword(String password) { this.password = password; }
}
