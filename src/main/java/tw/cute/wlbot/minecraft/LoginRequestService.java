package tw.cute.wlbot.minecraft;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import tw.cute.wlbot.domain.PlayerIdentity;
import tw.cute.wlbot.presentation.QueueMessageFormatter;
import tw.cute.wlbot.queue.QueueRepository;

public final class LoginRequestService {
    private final QueueRepository repository;
    private final QueueMessageFormatter formatter;

    public LoginRequestService(QueueRepository repository, QueueMessageFormatter formatter) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    public Optional<String> handle(
            boolean rejectedByNativeWhitelist,
            PlayerIdentity player,
            Instant now) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(now, "now");
        if (!rejectedByNativeWhitelist) {
            return Optional.empty();
        }
        return Optional.of(formatter.format(repository.request(player, now)));
    }
}
