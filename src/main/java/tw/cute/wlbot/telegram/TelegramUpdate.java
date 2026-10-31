package tw.cute.wlbot.telegram;

import java.util.Objects;
import java.util.Optional;

public record TelegramUpdate(long updateId, Optional<TelegramCallback> callback) {
    public TelegramUpdate {
        if (updateId < 0) {
            throw new IllegalArgumentException("Update ID must not be negative");
        }
        Objects.requireNonNull(callback, "callback");
    }
}
