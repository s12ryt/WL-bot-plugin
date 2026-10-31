package tw.cute.wlbot.telegram;

public record TelegramMessage(long chatId, long messageId) {
    public TelegramMessage {
        if (chatId == 0 || messageId <= 0) {
            throw new IllegalArgumentException("Telegram message identifiers are invalid");
        }
    }
}
