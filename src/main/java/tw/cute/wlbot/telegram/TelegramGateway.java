package tw.cute.wlbot.telegram;

import java.util.List;
import tw.cute.wlbot.queue.PendingApplication;

public interface TelegramGateway {
    TelegramMessage sendApplication(PendingApplication application);

    List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds);

    void answerCallback(String callbackId, String text, boolean showAlert);

    void deleteMessage(long chatId, long messageId);
}
