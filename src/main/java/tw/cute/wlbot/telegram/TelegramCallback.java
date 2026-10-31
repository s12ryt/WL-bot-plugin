package tw.cute.wlbot.telegram;

import java.util.Objects;

public record TelegramCallback(
        String callbackId,
        long actorUserId,
        long chatId,
        long messageId,
        String data) {
    public TelegramCallback {
        if (callbackId == null || callbackId.isBlank()) {
            throw new IllegalArgumentException("Callback ID must not be blank");
        }
        if (actorUserId <= 0 || chatId == 0 || messageId <= 0) {
            throw new IllegalArgumentException("Telegram identifiers are invalid");
        }
        Objects.requireNonNull(data, "data");
    }
}
