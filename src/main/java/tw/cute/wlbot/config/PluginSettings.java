package tw.cute.wlbot.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

public record PluginSettings(
        String botToken,
        URI apiBaseUrl,
        long adminUserId,
        long adminChatId,
        Duration rejectionCooldown,
        Path databaseFile,
        int pollingTimeoutSeconds) {
    public static final URI DEFAULT_API_BASE = URI.create("https://api.telegram.org");

    public PluginSettings {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("Telegram bot token must not be blank");
        }
        if (apiBaseUrl == null || !apiBaseUrl.isAbsolute() || !isHttpScheme(apiBaseUrl)) {
            throw new IllegalArgumentException(
                    "Telegram API base URL must be an absolute http(s) URI");
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

    private static boolean isHttpScheme(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        String normalized = scheme.toLowerCase(Locale.ROOT);
        return normalized.equals("http") || normalized.equals("https");
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
