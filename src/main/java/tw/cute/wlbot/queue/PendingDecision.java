package tw.cute.wlbot.queue;

public record PendingDecision(
        PendingApplication application,
        int position,
        int total,
        boolean created) implements QueueDecision {
    public PendingDecision {
        if (position < 1 || total < position) {
            throw new IllegalArgumentException("Invalid queue position");
        }
    }
}
