package tw.cute.wlbot.telegram;

import java.util.Objects;
import java.util.UUID;
import tw.cute.wlbot.review.ReviewAction;

public record CallbackCommand(ReviewAction action, UUID playerUuid) {
    public CallbackCommand {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(playerUuid, "playerUuid");
    }
}
