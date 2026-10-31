package tw.cute.wlbot.telegram;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tw.cute.wlbot.queue.PendingApplication;
import tw.cute.wlbot.review.ReviewOutcome;

class TelegramBotRuntimeTest {
    @Test
    void closeInterruptsBlockingLongPollAndStopsBothWorkers() throws Exception {
        BlockingGateway gateway = new BlockingGateway();
        TelegramReviewProcessor processor = new TelegramReviewProcessor(
                request -> CompletableFuture.completedFuture(ReviewOutcome.STALE),
                gateway,
                new CallbackDataCodec(),
                (message, error) -> { },
                Duration.ofSeconds(10));
        TelegramPollingWorker worker = new TelegramPollingWorker(gateway, processor, (message, error) -> { });
        CountDownLatch notified = new CountDownLatch(1);
        TelegramBotRuntime runtime = new TelegramBotRuntime(
                notified::countDown,
                worker,
                20,
                Duration.ofMillis(10),
                (message, error) -> { });

        runtime.start();
        assertTrue(notified.await(1, TimeUnit.SECONDS));
        assertTrue(gateway.pollStarted.await(1, TimeUnit.SECONDS));

        runtime.close();

        assertTrue(gateway.pollInterrupted.await(1, TimeUnit.SECONDS));
    }

    private static final class BlockingGateway implements TelegramGateway {
        private final CountDownLatch pollStarted = new CountDownLatch(1);
        private final CountDownLatch pollInterrupted = new CountDownLatch(1);

        @Override
        public TelegramMessage sendApplication(PendingApplication application) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) {
            pollStarted.countDown();
            try {
                new CountDownLatch(1).await(30, TimeUnit.SECONDS);
                return List.of();
            } catch (InterruptedException exception) {
                pollInterrupted.countDown();
                Thread.currentThread().interrupt();
                throw new TelegramApiException("interrupted", exception);
            }
        }

        @Override
        public void answerCallback(String callbackId, String text, boolean showAlert) {
        }

        @Override
        public void deleteMessage(long chatId, long messageId) {
        }
    }
}
