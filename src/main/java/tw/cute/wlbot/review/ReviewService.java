package tw.cute.wlbot.review;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import tw.cute.wlbot.queue.QueueRepository;

public final class ReviewService implements ReviewUseCase {
    private final QueueRepository repository;
    private final WhitelistGateway whitelist;
    private final long administratorUserId;
    private final long reviewChatId;
    private final Duration rejectionCooldown;
    private final Clock clock;

    public ReviewService(
            QueueRepository repository,
            WhitelistGateway whitelist,
            long administratorUserId,
            long reviewChatId,
            Duration rejectionCooldown,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.whitelist = Objects.requireNonNull(whitelist, "whitelist");
        this.administratorUserId = administratorUserId;
        this.reviewChatId = reviewChatId;
        this.rejectionCooldown = Objects.requireNonNull(rejectionCooldown, "rejectionCooldown");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (rejectionCooldown.isNegative()) {
            throw new IllegalArgumentException("Rejection cooldown must not be negative");
        }
    }

    @Override
    public CompletionStage<ReviewOutcome> review(ReviewRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.actorUserId() != administratorUserId || request.chatId() != reviewChatId) {
            return CompletableFuture.completedFuture(ReviewOutcome.UNAUTHORIZED);
        }

        var application = repository.findPending(request.playerUuid());
        if (application.isEmpty()) {
            return CompletableFuture.completedFuture(ReviewOutcome.STALE);
        }

        if (request.action() == ReviewAction.REJECT) {
            boolean rejected = repository.reject(
                    request.playerUuid(), clock.instant().plus(rejectionCooldown));
            return CompletableFuture.completedFuture(
                    rejected ? ReviewOutcome.REJECTED : ReviewOutcome.STALE);
        }

        return whitelist.add(application.orElseThrow().player()).thenApply(ignored ->
                repository.approve(request.playerUuid())
                        ? ReviewOutcome.APPROVED
                        : ReviewOutcome.STALE);
    }
}
