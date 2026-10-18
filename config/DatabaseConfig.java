package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class DatabaseConfig {

    private static final Map<String, String> ENV_FILE = loadEnvFile();

    private static final String URL =
            get("DB_URL", "jdbc:postgresql://localhost:5433/helpdesk_db");
    private static final String USER = get("DB_USER", "helpdesk_app");
    private static final String PASSWORD = get("DB_PASSWORD", null);

    private DatabaseConfig() {
    }

    public static Connection getConnection() throws SQLException {
        if (PASSWORD == null || PASSWORD.isBlank()) {
            throw new IllegalStateException(
                    "Не задан DB_PASSWORD: ни в переменных окружения, "
                            + "ни в файле .env. Рабочая папка программы: "
                            + Path.of("").toAbsolutePath()
            );
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private static String get(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = ENV_FILE.get(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return defaultValue;
    }

    private static Map<String, String> loadEnvFile() {
        Map<String, String> result = new HashMap<>();
        Path path = Path.of(".env");
        if (!Files.exists(path)) {
            return result;
        }
        try {
            for (String line : Files.readAllLines(path)) {
                line = line.replace("\uFEFF", "").trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                if (val.length() >= 2
                        && (val.startsWith("\"") && val.endsWith("\"")
                        || val.startsWith("'") && val.endsWith("'"))) {
                    val = val.substring(1, val.length() - 1);
                }
                result.put(key, val);
            }
        } catch (IOException e) {
            throw new RuntimeException("Не удалось прочитать файл .env", e);
        }
        return result;
    }
}