package tw.cute.wlbot.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tw.cute.wlbot.queue.PendingApplication;
import tw.cute.wlbot.review.ReviewAction;
import tw.cute.wlbot.review.ReviewOutcome;
import tw.cute.wlbot.review.ReviewRequest;

class TelegramReviewProcessorTest {
    private static final UUID PLAYER_UUID = UUID.fromString("02411fa2-8ff2-4e96-80fd-26a9b438ff8f");

    @Test
    void approvedCallbackInvokesReviewAnswersAndDeletesOriginalMessage() {
        AtomicReference<ReviewRequest> capturedRequest = new AtomicReference<>();
        RecordingGateway gateway = new RecordingGateway();
        TelegramReviewProcessor processor = new TelegramReviewProcessor(
                request -> {
                    capturedRequest.set(request);
                    return CompletableFuture.completedFuture(ReviewOutcome.APPROVED);
                }, gateway, new CallbackDataCodec(), (message, error) -> { });

        processor.process(callback("approve:" + PLAYER_UUID));

        assertEquals(ReviewAction.APPROVE, capturedRequest.get().action());
        assertEquals(42L, capturedRequest.get().actorUserId());
        assertEquals(100L, capturedRequest.get().chatId());
        assertEquals(List.of(new Answer("callback-1", "已通過，玩家已加入白名單。", false)), gateway.answers);
        assertEquals(List.of(new DeletedMessage(100L, 501L)), gateway.deletedMessages);
    }

    @Test
    void unauthorizedCallbackIsAnsweredButMessageIsNotDeleted() {
        RecordingGateway gateway = new RecordingGateway();
        TelegramReviewProcessor processor = processorFor(ReviewOutcome.UNAUTHORIZED, gateway, new ArrayList<>());

        processor.process(callback("reject:" + PLAYER_UUID));

        assertEquals(1, gateway.answers.size());
        assertEquals("你沒有審核權限。", gateway.answers.getFirst().text());
        assertTrue(gateway.answers.getFirst().showAlert());
        assertTrue(gateway.deletedMessages.isEmpty());
    }

    @Test
    void staleCallbackCleansUpTheObsoleteMessage() {
        RecordingGateway gateway = new RecordingGateway();
        TelegramReviewProcessor processor = processorFor(ReviewOutcome.STALE, gateway, new ArrayList<>());

        processor.process(callback("approve:" + PLAYER_UUID));

        assertEquals("這筆申請已處理或已失效。", gateway.answers.getFirst().text());
        assertEquals(List.of(new DeletedMessage(100L, 501L)), gateway.deletedMessages);
    }

    @Test
    void deleteFailureIsReportedWithoutChangingSuccessfulReviewResult() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.failDelete = true;
        ArrayList<Throwable> errors = new ArrayList<>();
        TelegramReviewProcessor processor = processorFor(ReviewOutcome.REJECTED, gateway, errors);

        processor.process(callback("reject:" + PLAYER_UUID));

        assertEquals("已拒絕，玩家可在冷卻結束後重新申請。", gateway.answers.getFirst().text());
        assertFalse(gateway.answers.getFirst().showAlert());
        assertEquals(1, errors.size());
    }

    @Test
    void malformedCallbackIsAnsweredWithoutInvokingReview() {
        AtomicReference<ReviewRequest> capturedRequest = new AtomicReference<>();
        RecordingGateway gateway = new RecordingGateway();
        TelegramReviewProcessor processor = new TelegramReviewProcessor(
                request -> {
                    capturedRequest.set(request);
                    return CompletableFuture.completedFuture(ReviewOutcome.APPROVED);
                }, gateway, new CallbackDataCodec(), (message, error) -> { });

        processor.process(callback("broken-data"));

        assertEquals(null, capturedRequest.get());
        assertEquals("無效的審核操作。", gateway.answers.getFirst().text());
        assertTrue(gateway.answers.getFirst().showAlert());
        assertTrue(gateway.deletedMessages.isEmpty());
    }

    private static TelegramReviewProcessor processorFor(
            ReviewOutcome outcome, RecordingGateway gateway, List<Throwable> errors) {
        return new TelegramReviewProcessor(
                request -> CompletableFuture.completedFuture(outcome),
                gateway,
                new CallbackDataCodec(),
                (message, error) -> errors.add(error));
    }

    private static TelegramCallback callback(String data) {
        return new TelegramCallback("callback-1", 42L, 100L, 501L, data);
    }

    private record Answer(String callbackId, String text, boolean showAlert) {
    }

    private record DeletedMessage(long chatId, long messageId) {
    }

    private static final class RecordingGateway implements TelegramGateway {
        private final List<Answer> answers = new ArrayList<>();
        private final List<DeletedMessage> deletedMessages = new ArrayList<>();
        private boolean failDelete;

        @Override
        public TelegramMessage sendApplication(PendingApplication application) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) {
            return List.of();
        }

        @Override
        public void answerCallback(String callbackId, String text, boolean showAlert) {
            answers.add(new Answer(callbackId, text, showAlert));
        }

        @Override
        public void deleteMessage(long chatId, long messageId) {
            if (failDelete) {
                throw new TelegramApiException("simulated delete failure");
            }
            deletedMessages.add(new DeletedMessage(chatId, messageId));
        }
    }
}
