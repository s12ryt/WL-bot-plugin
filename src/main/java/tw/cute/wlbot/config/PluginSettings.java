package tw.cute.wlbot.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public record PluginSettings(
        String botToken,
        long adminUserId,
        long adminChatId,
        Duration rejectionCooldown,
        Path databaseFile,
        int pollingTimeoutSeconds) {
    public PluginSettings {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("Telegram bot token must not be blank");
        }
        if (adminUserId <= 0) {
            throw new IllegalArgumentException("Admin user ID must be positive");
        }
        if (adminChatId == 0) {
            throw new IllegalArgumentException("Admin chat ID must not be zero");
        }
        Objects.requireNonNull(rejectionCooldown, "rejectionCooldown");
        if (rejectionCooldown.isZero() || rejectionCooldown.isNegative()) {
            throw new IllegalArgumentException("Rejection cooldown must be positive");
        }
        Objects.requireNonNull(databaseFile, "databaseFile");
        if (databaseFile.isAbsolute() || containsParentTraversal(databaseFile)) {
            throw new IllegalArgumentException("Database file must stay inside the plugin data directory");
        }
        if (pollingTimeoutSeconds < 1 || pollingTimeoutSeconds > 50) {
            throw new IllegalArgumentException("Polling timeout must be between 1 and 50 seconds");
        }
    }

    private static boolean containsParentTraversal(Path path) {
        for (Path segment : path) {
            if (segment.toString().equals("..")) {
                return true;
            }
        }
        return false;
    }
}
