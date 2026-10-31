package tw.cute.wlbot.telegram;

import java.util.Objects;
import tw.cute.wlbot.queue.PendingApplication;
import tw.cute.wlbot.queue.QueueRepository;

public final class PendingNotifier {
    private static final int BATCH_SIZE = 50;

    private final QueueRepository repository;
    private final TelegramGateway gateway;
    private final ErrorReporter errorReporter;

    public PendingNotifier(
            QueueRepository repository,
            TelegramGateway gateway,
            ErrorReporter errorReporter) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
    }

    public void dispatch() {
        for (PendingApplication application : repository.findUnnotified(BATCH_SIZE)) {
            notify(application);
        }
    }

    private void notify(PendingApplication application) {
        TelegramMessage sent;
        try {
            sent = gateway.sendApplication(application);
        } catch (RuntimeException exception) {
            errorReporter.report(
                    "無法傳送白名單申請通知：" + application.player().uuid(), exception);
            return;
        }

        try {
            if (!repository.attachTelegramMessage(application.player().uuid(), sent.messageId())) {
                deleteOrphan(sent, application);
            }
        } catch (RuntimeException exception) {
            deleteOrphan(sent, application);
            errorReporter.report(
                    "無法記錄 Telegram 申請訊息：" + application.player().uuid(), exception);
        }
    }

    private void deleteOrphan(TelegramMessage message, PendingApplication application) {
        try {
            gateway.deleteMessage(message.chatId(), message.messageId());
        } catch (RuntimeException exception) {
            errorReporter.report(
                    "無法刪除重複的 Telegram 申請訊息：" + application.player().uuid(), exception);
        }
    }
}
