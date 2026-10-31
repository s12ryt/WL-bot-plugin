package tw.cute.wlbot.telegram;

@FunctionalInterface
public interface ErrorReporter {
    void report(String message, Throwable error);
}
