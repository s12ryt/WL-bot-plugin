package tw.cute.wlbot.telegram;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TelegramBotRuntime implements AutoCloseable {
    private static final long ERROR_BACKOFF_MILLIS = 1_000L;

    private final Runnable notificationTask;
    private final TelegramPollingWorker pollingWorker;
    private final int pollingTimeoutSeconds;
    private final Duration notificationInterval;
    private final ErrorReporter errorReporter;
    private final ScheduledExecutorService notificationExecutor;
    private final ExecutorService pollingExecutor;
    private final AtomicBoolean running = new AtomicBoolean();

    public TelegramBotRuntime(
            Runnable notificationTask,
            TelegramPollingWorker pollingWorker,
            int pollingTimeoutSeconds,
            Duration notificationInterval,
            ErrorReporter errorReporter) {
        this.notificationTask = Objects.requireNonNull(notificationTask, "notificationTask");
        this.pollingWorker = Objects.requireNonNull(pollingWorker, "pollingWorker");
        if (pollingTimeoutSeconds < 1 || pollingTimeoutSeconds > 50) {
            throw new IllegalArgumentException("Polling timeout must be between 1 and 50 seconds");
        }
        this.pollingTimeoutSeconds = pollingTimeoutSeconds;
        this.notificationInterval = Objects.requireNonNull(notificationInterval, "notificationInterval");
        if (notificationInterval.isZero() || notificationInterval.isNegative()) {
            throw new IllegalArgumentException("Notification interval must be positive");
        }
        this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
        this.notificationExecutor = Executors.newSingleThreadScheduledExecutor(
                runnable -> daemonThread(runnable, "wlbot-notifier"));
        this.pollingExecutor = Executors.newSingleThreadExecutor(
                runnable -> daemonThread(runnable, "wlbot-poller"));
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Telegram runtime has already started");
        }
        notificationExecutor.scheduleWithFixedDelay(
                this::notifySafely,
                0L,
                notificationInterval.toMillis(),
                TimeUnit.MILLISECONDS);
        pollingExecutor.execute(this::pollLoop);
    }

    @Override
    public void close() {
        running.set(false);
        notificationExecutor.shutdownNow();
        pollingExecutor.shutdownNow();
        awaitTermination(notificationExecutor);
        awaitTermination(pollingExecutor);
    }

    private void notifySafely() {
        if (!running.get()) {
            return;
        }
        try {
            notificationTask.run();
        } catch (RuntimeException exception) {
            if (running.get()) {
                errorReporter.report("Telegram 申請通知工作失敗", exception);
            }
        }
    }

    private void pollLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                pollingWorker.pollOnce(pollingTimeoutSeconds);
            } catch (RuntimeException exception) {
                if (!running.get()) {
                    break;
                }
                errorReporter.report("Telegram long polling 失敗", exception);
                if (!backoff()) {
                    break;
                }
            }
        }
    }

    private boolean backoff() {
        try {
            Thread.sleep(ERROR_BACKOFF_MILLIS);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Thread daemonThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void awaitTermination(ExecutorService executor) {
        try {
            executor.awaitTermination(5L, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
