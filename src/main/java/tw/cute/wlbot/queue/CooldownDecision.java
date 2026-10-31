package tw.cute.wlbot.queue;

import java.time.Duration;
import java.util.Objects;

public record CooldownDecision(Duration remaining) implements QueueDecision {
    public CooldownDecision {
        Objects.requireNonNull(remaining, "remaining");
        if (remaining.isNegative() || remaining.isZero()) {
            throw new IllegalArgumentException("Remaining cooldown must be positive");
        }
    }
}
