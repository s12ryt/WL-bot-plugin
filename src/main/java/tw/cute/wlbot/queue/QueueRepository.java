package tw.cute.wlbot.queue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tw.cute.wlbot.domain.PlayerIdentity;

public interface QueueRepository extends AutoCloseable {
    QueueDecision request(PlayerIdentity player, Instant now);

    Optional<PendingApplication> findPending(UUID playerUuid);

    List<PendingApplication> findUnnotified(int limit);

    boolean attachTelegramMessage(UUID playerUuid, long messageId);

    boolean approve(UUID playerUuid);

    boolean reject(UUID playerUuid, Instant cooldownUntil);

    @Override
    void close();
}
