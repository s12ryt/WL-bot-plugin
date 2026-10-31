package tw.cute.wlbot.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import tw.cute.wlbot.queue.PendingApplication;
import tw.cute.wlbot.review.ReviewOutcome;

class TelegramPollingWorkerTest {
    @Test
    void advancesOffsetAcrossCallbackAndNonCallbackUpdates() {
        UUID playerUuid = UUID.fromString("5a0e5ed0-9588-47f6-9034-81dd340c3ed8");
        RecordingGateway gateway = new RecordingGateway();
        gateway.firstResult = List.of(
                new TelegramUpdate(6L, Optional.of(new TelegramCallback(
                        "cb-1", 42L, 100L, 501L, "approve:" + playerUuid))),
                new TelegramUpdate(7L, Optional.empty()));
        ArrayList<ReviewOutcome> reviews = new ArrayList<>();
        TelegramReviewProcessor processor = new TelegramReviewProcessor(
                request -> {
                    reviews.add(ReviewOutcome.UNAUTHORIZED);
                    return CompletableFuture.completedFuture(ReviewOutcome.UNAUTHORIZED);
                }, gateway, new CallbackDataCodec(), (message, error) -> { }, java.time.Duration.ofSeconds(10));
        TelegramPollingWorker worker = new TelegramPollingWorker(gateway, processor, (message, error) -> { });

        worker.pollOnce(20);
        worker.pollOnce(20);

        assertEquals(List.of(0L, 8L), gateway.requestedOffsets);
        assertEquals(8L, worker.nextOffset());
        assertEquals(1, reviews.size());
    }

    private static final class RecordingGateway implements TelegramGateway {
        private final List<Long> requestedOffsets = new ArrayList<>();
        private List<TelegramUpdate> firstResult = List.of();

        @Override
        public TelegramMessage sendApplication(PendingApplication application) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) {
            requestedOffsets.add(offset);
            if (requestedOffsets.size() == 1) {
                return firstResult;
            }
            return List.of();
        }

        @Override
        public void answerCallback(String callbackId, String text, boolean showAlert) {
        }

        @Override
        public void deleteMessage(long chatId, long messageId) {
        }
    }
}
