package tw.cute.wlbot.presentation;

import java.time.Duration;
import java.util.Objects;
import tw.cute.wlbot.queue.CooldownDecision;
import tw.cute.wlbot.queue.PendingDecision;
import tw.cute.wlbot.queue.QueueDecision;

public final class QueueMessageFormatter {
    public String format(QueueDecision decision) {
        Objects.requireNonNull(decision, "decision");
        return switch (decision) {
            case PendingDecision pending -> formatPending(pending);
            case CooldownDecision cooldown -> formatCooldown(cooldown.remaining());
        };
    }

    private static String formatPending(PendingDecision decision) {
        String heading = decision.created() ? "白名單申請已送出" : "白名單申請審核中";
        return heading
                + "\n目前順位：" + decision.position() + " / " + decision.total()
                + "\n請稍後重新連線查看進度。";
    }

    private static String formatCooldown(Duration remaining) {
        long seconds = remaining.toSeconds();
        if (!remaining.equals(Duration.ofSeconds(seconds))) {
            seconds++;
        }
        return "申請冷卻中\n請在 " + Math.max(1L, seconds) + " 秒後重新連線申請。";
    }
}
