package tw.cute.wlbot.telegram;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import tw.cute.wlbot.review.ReviewOutcome;
import tw.cute.wlbot.review.ReviewRequest;
import tw.cute.wlbot.review.ReviewUseCase;

public final class TelegramReviewProcessor {
    private final ReviewUseCase reviewUseCase;
    private final TelegramGateway gateway;
    private final CallbackDataCodec codec;
    private final ErrorReporter errorReporter;
    private final Duration reviewTimeout;

    public TelegramReviewProcessor(
            ReviewUseCase reviewUseCase,
            TelegramGateway gateway,
            CallbackDataCodec codec,
            ErrorReporter errorReporter,
            Duration reviewTimeout) {
        this.reviewUseCase = Objects.requireNonNull(reviewUseCase, "reviewUseCase");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
        Objects.requireNonNull(reviewTimeout, "reviewTimeout");
        if (reviewTimeout.isZero() || reviewTimeout.isNegative()) {
            throw new IllegalArgumentException("reviewTimeout must be positive");
        }
        this.reviewTimeout = reviewTimeout;
    }

    public void process(TelegramCallback callback) {
        Objects.requireNonNull(callback, "callback");
        var command = codec.decode(callback.data());
        if (command.isEmpty()) {
            answerSafely(callback.callbackId(), "無效的審核操作。", true);
            return;
        }

        ReviewOutcome outcome;
        try {
            var decoded = command.orElseThrow();
            outcome = reviewUseCase.review(new ReviewRequest(
                            callback.actorUserId(),
                            callback.chatId(),
                            decoded.playerUuid(),
                            decoded.action()))
                    .toCompletableFuture()
                    .orTimeout(reviewTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .join();
        } catch (RuntimeException exception) {
            errorReporter.report("Telegram 白名單審核失敗", exception);
            answerSafely(callback.callbackId(), "審核處理失敗，請稍後重試。", true);
            return;
        }

        switch (outcome) {
            case APPROVED -> finish(callback, "已通過，玩家已加入白名單。", false);
            case REJECTED -> finish(callback, "已拒絕，玩家可在冷卻結束後重新申請。", false);
            case STALE -> finish(callback, "這筆申請已處理或已失效。", true);
            case UNAUTHORIZED -> answerSafely(callback.callbackId(), "你沒有審核權限。", true);
        }
    }

    private void finish(TelegramCallback callback, String answer, boolean showAlert) {
        answerSafely(callback.callbackId(), answer, showAlert);
        try {
            gateway.deleteMessage(callback.chatId(), callback.messageId());
        } catch (RuntimeException exception) {
            errorReporter.report("無法刪除已處理的 Telegram 審核訊息", exception);
        }
    }

    private void answerSafely(String callbackId, String text, boolean showAlert) {
        try {
            gateway.answerCallback(callbackId, text, showAlert);
        } catch (RuntimeException exception) {
            errorReporter.report("無法回覆 Telegram callback", exception);
        }
    }
}
