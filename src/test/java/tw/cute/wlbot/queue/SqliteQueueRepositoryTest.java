package tw.cute.wlbot.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.cute.wlbot.domain.PlayerIdentity;

class SqliteQueueRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    private static final PlayerIdentity ALICE = new PlayerIdentity(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), "Alice");
    private static final PlayerIdentity BOB = new PlayerIdentity(
            UUID.fromString("22222222-2222-2222-2222-222222222222"), "Bob");

    @TempDir
    Path temporaryDirectory;

    @Test
    void duplicateRequestsKeepOneEntryAndPositionsShiftAfterApproval() throws Exception {
        try (var repository = repository()) {
            var first = assertInstanceOf(PendingDecision.class, repository.request(ALICE, NOW));
            assertTrue(first.created());
            assertEquals(1, first.position());
            assertEquals(1, first.total());

            var duplicate = assertInstanceOf(PendingDecision.class, repository.request(ALICE, NOW.plusSeconds(2)));
            assertFalse(duplicate.created());
            assertEquals(first.application().sequence(), duplicate.application().sequence());

            var second = assertInstanceOf(PendingDecision.class, repository.request(BOB, NOW.plusSeconds(3)));
            assertEquals(2, second.position());
            assertEquals(2, second.total());

            assertTrue(repository.approve(ALICE.uuid()));
            var shifted = assertInstanceOf(PendingDecision.class, repository.request(BOB, NOW.plusSeconds(4)));
            assertEquals(1, shifted.position());
            assertEquals(1, shifted.total());
            assertFalse(repository.approve(ALICE.uuid()));
        }
    }

    @Test
    void rejectionEnforcesCooldownAndCreatesANewRequestAtExpiry() throws Exception {
        try (var repository = repository()) {
            var original = assertInstanceOf(PendingDecision.class, repository.request(ALICE, NOW));
            assertTrue(repository.reject(ALICE.uuid(), NOW.plusSeconds(15)));

            var coolingDown = assertInstanceOf(
                    CooldownDecision.class,
                    repository.request(ALICE, NOW.plusSeconds(14)));
            assertEquals(Duration.ofSeconds(1), coolingDown.remaining());

            var retried = assertInstanceOf(
                    PendingDecision.class,
                    repository.request(ALICE, NOW.plusSeconds(15)));
            assertTrue(retried.created());
            assertTrue(retried.application().sequence() > original.application().sequence());
        }
    }

    @Test
    void telegramMessageStateSurvivesRepositoryReopen() throws Exception {
        long sequence;
        try (var repository = repository()) {
            var pending = assertInstanceOf(PendingDecision.class, repository.request(ALICE, NOW));
            sequence = pending.application().sequence();
            assertEquals(1, repository.findUnnotified(10).size());
            assertTrue(repository.attachTelegramMessage(ALICE.uuid(), 9876L));
            assertFalse(repository.attachTelegramMessage(ALICE.uuid(), 9999L));
        }

        try (var repository = repository()) {
            var restored = repository.findPending(ALICE.uuid()).orElseThrow();
            assertEquals(sequence, restored.sequence());
            assertEquals(9876L, restored.telegramMessageId().orElseThrow());
            assertTrue(repository.findUnnotified(10).isEmpty());
        }
    }

    private SqliteQueueRepository repository() throws Exception {
        return SqliteQueueRepository.open(temporaryDirectory.resolve("queue.db"));
    }
}
