package tw.cute.wlbot.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PluginSettingsTest {
    private static final URI TELEGRAM_API = URI.create("https://api.telegram.org");

    @Test
    void exposesTheDefaultTelegramApiBase() {
        assertEquals(TELEGRAM_API, PluginSettings.DEFAULT_API_BASE);
    }

    @Test
    void acceptsAValidPrivateChatConfiguration() {
        assertDoesNotThrow(() -> new PluginSettings(
                "123:telegram-token", TELEGRAM_API, 42L, 100L,
                Duration.ofSeconds(15), Path.of("queue.db"), 20));
    }

    @Test
    void acceptsACustomHttpApiBaseUrlForLocalTesting() {
        assertDoesNotThrow(() -> settings(URI.create("http://127.0.0.1:18080")));
        assertDoesNotThrow(() -> settings(URI.create("http://127.0.0.1:18080/")));
    }

    @Test
    void rejectsApiBaseUrlThatIsMissingRelativeOrNonHttp() {
        assertThrows(IllegalArgumentException.class, () -> settings(null));
        assertThrows(IllegalArgumentException.class, () -> settings(URI.create("/bot-api")));
        assertThrows(IllegalArgumentException.class, () -> settings(URI.create("ftp://example.com")));
        assertThrows(IllegalArgumentException.class,
                () -> settings(URI.create("telegram://bot")));
    }

    @Test
    void rejectsMissingCredentialsUnsafeDatabasePathsAndInvalidDurations() {
        assertThrows(IllegalArgumentException.class,
                () -> settings(" ", TELEGRAM_API, 42L, 100L, Duration.ofSeconds(15), Path.of("queue.db"), 20));
        assertThrows(IllegalArgumentException.class,
                () -> settings("token", TELEGRAM_API, 0L, 100L, Duration.ofSeconds(15), Path.of("queue.db"), 20));
        assertThrows(IllegalArgumentException.class,
                () -> settings("token", TELEGRAM_API, 42L, 0L, Duration.ofSeconds(15), Path.of("queue.db"), 20));
        assertThrows(IllegalArgumentException.class,
                () -> settings("token", TELEGRAM_API, 42L, 100L, Duration.ZERO, Path.of("queue.db"), 20));
        assertThrows(IllegalArgumentException.class,
                () -> settings("token", TELEGRAM_API, 42L, 100L, Duration.ofSeconds(15), Path.of("..", "queue.db"), 20));
        assertThrows(IllegalArgumentException.class,
                () -> settings("token", TELEGRAM_API, 42L, 100L, Duration.ofSeconds(15), Path.of("queue.db"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> settings("token", TELEGRAM_API, 42L, 100L, Duration.ofSeconds(15), Path.of("queue.db"), 51));
    }

    private static PluginSettings settings(URI apiBaseUrl) {
        return settings("token", apiBaseUrl, 42L, 100L,
                Duration.ofSeconds(15), Path.of("queue.db"), 20);
    }

    private static PluginSettings settings(
            String token,
            URI apiBaseUrl,
            long adminUserId,
            long adminChatId,
            Duration cooldown,
            Path databaseFile,
            int pollingTimeoutSeconds) {
        return new PluginSettings(
                token, apiBaseUrl, adminUserId, adminChatId, cooldown, databaseFile, pollingTimeoutSeconds);
    }
}
