package tw.cute.wlbot.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.cute.wlbot.domain.PlayerIdentity;
import tw.cute.wlbot.queue.PendingApplication;
import tw.cute.wlbot.queue.QueueDecision;
import tw.cute.wlbot.queue.QueueRepository;
import tw.cute.wlbot.queue.QueueStorageException;
import tw.cute.wlbot.queue.SqliteQueueRepository;

class PendingNotifierTest {
    @TempDir
    Path temporaryDirectory;

    private SqliteQueueRepository repository;

    @AfterEach
    void closeRepository() {
        if (repository != null) {
            repository.close();
        }
    }

    @Test
    void attachesMessageIdAndDoesNotSendTheSameApplicationTwice() throws Exception {
        repository = SqliteQueueRepository.open(temporaryDirectory.resolve("queue.db"));
        UUID playerUuid = UUID.fromString("d9f45006-2843-4b80-a65a-d326b9c4a001");
        repository.request(new PlayerIdentity(playerUuid, "CutePlayer"), Instant.parse("2026-10-31T10:00:00Z"));
        RecordingGateway gateway = new RecordingGateway();
        PendingNotifier notifier = new PendingNotifier(repository, gateway, (message, error) -> { });

        notifier.dispatch();
        notifier.dispatch();

        assertEquals(1, gateway.sendCount);
        assertEquals(701L, repository.findPending(playerUuid).orElseThrow()
                .telegramMessageId().orElseThrow());
    }

    @Test
    void leavesApplicationUnnotifiedWhenSendFailsSoANextDispatchRetriesIt() throws Exception {
        repository = SqliteQueueRepository.open(temporaryDirectory.resolve("queue.db"));
        UUID playerUuid = UUID.fromString("d9f45006-2843-4b80-a65a-d326b9c4a002");
        repository.request(new PlayerIdentity(playerUuid, "RetryPlayer"), Instant.parse("2026-10-31T10:00:00Z"));
        RecordingGateway gateway = new RecordingGateway();
        gateway.failSend = true;
        java.util.ArrayList<Throwable> errors = new java.util.ArrayList<>();
        PendingNotifier notifier = new PendingNotifier(repository, gateway, (message, error) -> errors.add(error));

        notifier.dispatch();
        assertTrue(repository.findPending(playerUuid).orElseThrow().telegramMessageId().isEmpty());

        gateway.failSend = false;
        notifier.dispatch();

        assertEquals(2, gateway.sendCount);
        assertEquals(1, errors.size());
        assertEquals(701L, repository.findPending(playerUuid).orElseThrow()
                .telegramMessageId().orElseThrow());
    }

    @Test
    void deletesSentMessageWhenAttachingItsIdFails() {
        UUID playerUuid = UUID.fromString("d9f45006-2843-4b80-a65a-d326b9c4a003");
        PendingApplication application = new PendingApplication(
                3L,
                new PlayerIdentity(playerUuid, "StorageFailurePlayer"),
                Instant.parse("2026-10-31T10:00:00Z"),
                OptionalLong.empty());
        RecordingGateway gateway = new RecordingGateway();
        java.util.ArrayList<Throwable> errors = new java.util.ArrayList<>();
        PendingNotifier notifier = new PendingNotifier(
                new FailingAttachRepository(application), gateway, (message, error) -> errors.add(error));

        notifier.dispatch();

        assertEquals(1, gateway.sendCount);
        assertEquals(List.of(new TelegramMessage(100L, 701L)), gateway.deletedMessages);
        assertEquals(1, errors.size());
    }

    private static final class RecordingGateway implements TelegramGateway {
        private int sendCount;
        private boolean failSend;
        private final List<TelegramMessage> deletedMessages = new java.util.ArrayList<>();

        @Override
        public TelegramMessage sendApplication(PendingApplication application) {
            sendCount++;
            if (failSend) {
                throw new TelegramApiException("simulated send failure");
            }
            return new TelegramMessage(100L, 701L);
        }

        @Override
        public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) {
            return List.of();
        }

        @Override
        public void answerCallback(String callbackId, String text, boolean showAlert) {
        }

        @Override
        public void deleteMessage(long chatId, long messageId) {
            deletedMessages.add(new TelegramMessage(chatId, messageId));
        }
    }

    private static final class FailingAttachRepository implements QueueRepository {
        private final PendingApplication application;

        private FailingAttachRepository(PendingApplication application) {
            this.application = application;
        }

        @Override
        public List<PendingApplication> findUnnotified(int limit) {
            return List.of(application);
        }

        @Override
        public boolean attachTelegramMessage(UUID playerUuid, long messageId) {
            throw new QueueStorageException("simulated attach failure", new IllegalStateException());
        }

        @Override
        public QueueDecision request(PlayerIdentity player, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PendingApplication> findPending(UUID playerUuid) {
            return Optional.of(application);
        }

        @Override
        public boolean approve(UUID playerUuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean reject(UUID playerUuid, Instant cooldownUntil) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }
}
