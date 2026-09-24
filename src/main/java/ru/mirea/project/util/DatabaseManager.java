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

/**
 * Менеджер подключения к базе данных (Singleton-утилита).
 * Управляет жизненным циклом соединений через JDBC, читает настройки из application.properties.
 */
public class DatabaseManager {
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
        // 1. Попытка загрузить из classpath
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {}

        // 2. Чтение из локального файла (если изменен в проекте или рядом с jar)
        File localFile = new File("application.properties");
        if (!localFile.exists()) {
            localFile = new File("src/main/resources/application.properties");
        }
        if (localFile.exists()) {
            try (InputStream in = new FileInputStream(localFile)) {
                props.load(in);
            } catch (Exception ignored) {}
        }

        this.driver = System.getProperty("db.driver", props.getProperty("db.driver", "org.postgresql.Driver"));
        this.url = System.getProperty("db.url", props.getProperty("db.url", "jdbc:postgresql://localhost:5432/pharmacy_db"));
        this.user = System.getProperty("db.user", props.getProperty("db.user", "postgres"));
        this.password = System.getProperty("db.password", props.getProperty("db.password", "postgres"));

        try {
            Class.forName(this.driver);
        } catch (Exception e) {
            throw new DatabaseException("Ошибка загрузки конфигурации базы данных: " + e.getMessage(), e);
        }
    }

    /**
     * Получить новое JDBC-соединение
     */
    public Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            // Код 3D000 - база данных не существует в PostgreSQL
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
            System.out.println("ℹ️  База данных 'pharmacy_db' была автоматически создана.");
        } catch (Exception ignored) {}
    }

    /**
     * Проверка доступности подключения
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            System.out.println("   [Диагностика] URL: " + url + ", Пользователь: " + user);
            System.out.println("   [Диагностика] Детали ошибки: " + e.getMessage());
            return false;
        }
    }

    /**
     * Исполнение SQL-скрипта из ресурсов classpath (например, schema.sql, data.sql)
     */
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
                    // Пропуск комментариев
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

    // Геттеры и сеттеры для гибкой настройки в рантайме
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public void setPassword(String password) { this.password = password; }
}
