package tw.cute.wlbot.minecraft;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import tw.cute.wlbot.domain.PlayerIdentity;
import tw.cute.wlbot.review.WhitelistGateway;

public final class ScheduledWhitelistGateway implements WhitelistGateway {
    private final Consumer<Runnable> scheduler;
    private final Consumer<PlayerIdentity> whitelistMutation;

    public ScheduledWhitelistGateway(
            Consumer<Runnable> scheduler,
            Consumer<PlayerIdentity> whitelistMutation) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.whitelistMutation = Objects.requireNonNull(whitelistMutation, "whitelistMutation");
    }

    @Override
    public CompletionStage<Void> add(PlayerIdentity player) {
        Objects.requireNonNull(player, "player");
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            scheduler.accept(() -> mutate(player, completion));
        } catch (RuntimeException exception) {
            completion.completeExceptionally(exception);
        }
        return completion;
    }

    private void mutate(PlayerIdentity player, CompletableFuture<Void> completion) {
        try {
            whitelistMutation.accept(player);
            completion.complete(null);
        } catch (RuntimeException exception) {
            completion.completeExceptionally(exception);
        }
    }
}
