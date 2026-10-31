package tw.cute.wlbot.domain;

import java.util.Objects;
import java.util.UUID;

public record PlayerIdentity(UUID uuid, String name) {
    public PlayerIdentity {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
    }
}
