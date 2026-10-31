package tw.cute.wlbot.queue;

public sealed interface QueueDecision permits PendingDecision, CooldownDecision {}
