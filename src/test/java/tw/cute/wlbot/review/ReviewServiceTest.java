package tw.cute.wlbot.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.cute.wlbot.domain.PlayerIdentity;
import tw.cute.wlbot.queue.CooldownDecision;
import tw.cute.wlbot.queue.PendingDecision;
import tw.cute.wlbot.queue.SqliteQueueRepository;

class ReviewServiceTest {
    private static final long ADMIN_ID = 42L;
    private static final long CHAT_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    private static final PlayerIdentity ALICE = new PlayerIdentity(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), "Alice");

    @TempDir
    Path temporaryDirectory;

    private SqliteQueueRepository repository;
    private RecordingWhitelist whitelist;
    private ReviewService service;

    @BeforeEach
    void setUp() throws Exception {
        repository = SqliteQueueRepository.open(temporaryDirectory.resolve("review.db"));
        repository.request(ALICE, NOW);
        whitelist = new RecordingWhitelist();
        service = new ReviewService(
                repository,
                whitelist,
                ADMIN_ID,
                CHAT_ID,
                Duration.ofSeconds(15),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void onlyConfiguredAdministratorAndChatCanReview() {
        var wrongUser = service.review(new ReviewRequest(99L, CHAT_ID, ALICE.uuid(), ReviewAction.APPROVE))
                .toCompletableFuture().join();
        var wrongChat = service.review(new ReviewRequest(ADMIN_ID, 999L, ALICE.uuid(), ReviewAction.REJECT))
                .toCompletableFuture().join();

        assertEquals(ReviewOutcome.UNAUTHORIZED, wrongUser);
        assertEquals(ReviewOutcome.UNAUTHORIZED, wrongChat);
        assertTrue(whitelist.added.isEmpty());
        assertTrue(repository.findPending(ALICE.uuid()).isPresent());
    }

    @Test
    void approvalAddsWhitelistBeforeRemovingPendingRequest() {
        var outcome = service.review(new ReviewRequest(ADMIN_ID, CHAT_ID, ALICE.uuid(), ReviewAction.APPROVE))
                .toCompletableFuture().join();

        assertEquals(ReviewOutcome.APPROVED, outcome);
        assertEquals(List.of(ALICE), whitelist.added);
        assertTrue(repository.findPending(ALICE.uuid()).isEmpty());
    }

    @Test
    void failedWhitelistWriteKeepsRequestPending() {
        whitelist.failure = new IllegalStateException("server rejected whitelist write");

        var future = service.review(new ReviewRequest(ADMIN_ID, CHAT_ID, ALICE.uuid(), ReviewAction.APPROVE))
                .toCompletableFuture();

        assertTrue(future.isCompletedExceptionally());
        assertTrue(repository.findPending(ALICE.uuid()).isPresent());
    }

    @Test
    void rejectionStartsFifteenSecondCooldownAndStaleCallbacksDoNothing() {
        var rejected = service.review(new ReviewRequest(ADMIN_ID, CHAT_ID, ALICE.uuid(), ReviewAction.REJECT))
                .toCompletableFuture().join();
        var stale = service.review(new ReviewRequest(ADMIN_ID, CHAT_ID, ALICE.uuid(), ReviewAction.REJECT))
                .toCompletableFuture().join();

        assertEquals(ReviewOutcome.REJECTED, rejected);
        assertEquals(ReviewOutcome.STALE, stale);
        var decision = repository.request(ALICE, NOW);
        var cooldown = assertInstanceOf(CooldownDecision.class, decision);
        assertEquals(Duration.ofSeconds(15), cooldown.remaining());
    }

    private static final class RecordingWhitelist implements WhitelistGateway {
        private final List<PlayerIdentity> added = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public CompletableFuture<Void> add(PlayerIdentity player) {
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            added.add(player);
            return CompletableFuture.completedFuture(null);
        }
    }
}
