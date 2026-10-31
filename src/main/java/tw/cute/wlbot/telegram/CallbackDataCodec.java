package tw.cute.wlbot.telegram;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import tw.cute.wlbot.review.ReviewAction;

public final class CallbackDataCodec {
    private static final int MAX_CALLBACK_BYTES = 64;

    public String encode(ReviewAction action, UUID playerUuid) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(playerUuid, "playerUuid");
        String verb = switch (action) {
            case APPROVE -> "approve";
            case REJECT -> "reject";
        };
        String encoded = verb + ":" + playerUuid;
        if (encoded.getBytes(StandardCharsets.UTF_8).length > MAX_CALLBACK_BYTES) {
            throw new IllegalArgumentException("Callback data exceeds Telegram's 64-byte limit");
        }
        return encoded;
    }

    public Optional<CallbackCommand> decode(String data) {
        if (data == null || data.isBlank()
                || data.getBytes(StandardCharsets.UTF_8).length > MAX_CALLBACK_BYTES) {
            return Optional.empty();
        }
        int separator = data.indexOf(':');
        if (separator < 1 || separator != data.lastIndexOf(':')) {
            return Optional.empty();
        }
        ReviewAction action = switch (data.substring(0, separator)) {
            case "approve" -> ReviewAction.APPROVE;
            case "reject" -> ReviewAction.REJECT;
            default -> null;
        };
        if (action == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CallbackCommand(action, UUID.fromString(data.substring(separator + 1))));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
