package tw.cute.wlbot.telegram;

import java.util.Objects;

public final class TelegramPollingWorker {
    private final TelegramGateway gateway;
    private final TelegramReviewProcessor processor;
    private final ErrorReporter errorReporter;
    private long nextOffset;

    public TelegramPollingWorker(
            TelegramGateway gateway,
            TelegramReviewProcessor processor,
            ErrorReporter errorReporter) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.processor = Objects.requireNonNull(processor, "processor");
        this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
    }

    public void pollOnce(int timeoutSeconds) {
        for (TelegramUpdate update : gateway.getUpdates(nextOffset, timeoutSeconds)) {
            try {
                update.callback().ifPresent(processor::process);
            } catch (RuntimeException exception) {
                errorReporter.report("無法處理 Telegram update " + update.updateId(), exception);
            } finally {
                nextOffset = Math.max(nextOffset, update.updateId() + 1L);
            }
        }
    }

    public long nextOffset() {
        return nextOffset;
    }
}
