package tw.cute.wlbot.queue;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import tw.cute.wlbot.domain.PlayerIdentity;

public record PendingApplication(
        long sequence,
        PlayerIdentity player,
        Instant requestedAt,
        OptionalLong telegramMessageId) {
    public PendingApplication {
        if (sequence < 1) {
            throw new IllegalArgumentException("Sequence must be positive");
        }
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(telegramMessageId, "telegramMessageId");
    }
}
