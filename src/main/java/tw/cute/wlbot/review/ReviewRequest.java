package tw.cute.wlbot.review;

import java.util.Objects;
import java.util.UUID;

public record ReviewRequest(
        long actorUserId,
        long chatId,
        UUID playerUuid,
        ReviewAction action) {
    public ReviewRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(action, "action");
    }
}
