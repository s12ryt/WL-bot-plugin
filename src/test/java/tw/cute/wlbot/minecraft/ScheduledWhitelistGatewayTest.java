package tw.cute.wlbot.minecraft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tw.cute.wlbot.domain.PlayerIdentity;

class ScheduledWhitelistGatewayTest {
    private static final PlayerIdentity PLAYER = new PlayerIdentity(
            UUID.fromString("70a9c77c-b896-4637-8ed3-9363933f1369"), "ApprovedPlayer");

    @Test
    void changesWhitelistOnlyWhenScheduledTaskRuns() {
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        AtomicBoolean changed = new AtomicBoolean();
        ScheduledWhitelistGateway gateway = new ScheduledWhitelistGateway(
                scheduled::set,
                player -> changed.set(true));

        var completion = gateway.add(PLAYER).toCompletableFuture();

        assertFalse(changed.get());
        assertFalse(completion.isDone());
        scheduled.get().run();
        assertTrue(changed.get());
        assertTrue(completion.isDone());
        assertFalse(completion.isCompletedExceptionally());
    }

    @Test
    void exposesSchedulingAndWhitelistMutationFailuresToTheReviewService() {
        ScheduledWhitelistGateway schedulingFailure = new ScheduledWhitelistGateway(
                task -> {
                    throw new IllegalStateException("scheduler stopped");
                }, player -> { });
        ScheduledWhitelistGateway mutationFailure = new ScheduledWhitelistGateway(
                Runnable::run,
                player -> {
                    throw new IllegalStateException("whitelist write failed");
                });

        assertThrows(Exception.class, () -> schedulingFailure.add(PLAYER).toCompletableFuture().get());
        assertThrows(Exception.class, () -> mutationFailure.add(PLAYER).toCompletableFuture().get());
    }
}
