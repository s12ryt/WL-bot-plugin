package tw.cute.wlbot.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.cute.wlbot.domain.PlayerIdentity;
import tw.cute.wlbot.presentation.QueueMessageFormatter;
import tw.cute.wlbot.queue.SqliteQueueRepository;

class LoginRequestServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void ignoresLoginFailuresThatWereNotCausedByTheNativeWhitelist() throws Exception {
        try (var repository = SqliteQueueRepository.open(temporaryDirectory.resolve("queue.db"))) {
            LoginRequestService service = new LoginRequestService(repository, new QueueMessageFormatter());

            var message = service.handle(
                    false,
                    new PlayerIdentity(UUID.randomUUID(), "BannedPlayer"),
                    Instant.parse("2026-10-31T10:00:00Z"));

            assertTrue(message.isEmpty());
            assertTrue(repository.findUnnotified(10).isEmpty());
        }
    }

    @Test
    void createsOnePendingRequestAndReturnsItsCurrentPosition() throws Exception {
        try (var repository = SqliteQueueRepository.open(temporaryDirectory.resolve("queue.db"))) {
            LoginRequestService service = new LoginRequestService(repository, new QueueMessageFormatter());
            PlayerIdentity player = new PlayerIdentity(
                    UUID.fromString("f050ee63-04ef-449a-a14c-422b4d81be22"), "WaitingPlayer");

            var first = service.handle(true, player, Instant.parse("2026-10-31T10:00:00Z"));
            var second = service.handle(true, player, Instant.parse("2026-10-31T10:00:01Z"));

            assertEquals("白名單申請已送出\n目前順位：1 / 1\n請稍後重新連線查看進度。", first.orElseThrow());
            assertEquals("白名單申請審核中\n目前順位：1 / 1\n請稍後重新連線查看進度。", second.orElseThrow());
            assertEquals(1, repository.findUnnotified(10).size());
        }
    }
}
