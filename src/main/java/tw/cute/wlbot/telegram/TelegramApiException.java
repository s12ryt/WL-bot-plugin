package tw.cute.wlbot.telegram;

public final class TelegramApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TelegramApiException(String message) {
        super(message);
    }

    public TelegramApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
