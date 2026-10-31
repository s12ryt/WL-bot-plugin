package tw.cute.wlbot.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PluginSettingsTest {
    @Test
    void acceptsAValidPrivateChatConfiguration() {
        assertDoesNotThrow(() -> new PluginSettings(
                "123:telegram-token", 42L, 100L, Duration.ofSeconds(15), Path.of("queue.db"), 20));
    }

    @Test
    void rejectsMissingCredentialsUnsafeDatabasePathsAndInvalidDurations() {
        assertThrows(IllegalArgumentException.class,
                () -> settings(" ", 42L, 100L, Duration.ofSeconds(15), Path.of("queue.db"), 20));
        assertThrows(IllegalArgumentException.class,
                () -> settings("token", 0L, 100L, Duration.ofSeconds(15), Path.of("queue.db"), 20));
        assertThrows(IllegalArgumentException.class,
                () -> settings("token", 42L, 0L, Duration.ofSeconds(15), Path.of("queue.db"), 20));
        assertThrows(IllegalArgumentException.class,
                () -> settings("token", 42L, 100L, Duration.ZERO, Path.of("queue.db"), 20));
        assertThrows(IllegalArgumentException.class,
                () -> settings("token", 42L, 100L, Duration.ofSeconds(15), Path.of("..", "queue.db"), 20));
        assertThrows(IllegalArgumentException.class,
                () -> settings("token", 42L, 100L, Duration.ofSeconds(15), Path.of("queue.db"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> settings("token", 42L, 100L, Duration.ofSeconds(15), Path.of("queue.db"), 51));
    }

    private static PluginSettings settings(
            String token,
            long adminUserId,
            long adminChatId,
            Duration cooldown,
            Path databaseFile,
            int pollingTimeoutSeconds) {
        return new PluginSettings(
                token, adminUserId, adminChatId, cooldown, databaseFile, pollingTimeoutSeconds);
    }
}
