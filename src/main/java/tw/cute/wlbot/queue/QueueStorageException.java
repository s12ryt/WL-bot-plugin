package tw.cute.wlbot.queue;

public final class QueueStorageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public QueueStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
