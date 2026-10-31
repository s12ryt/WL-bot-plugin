package tw.cute.wlbot.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tw.cute.wlbot.domain.PlayerIdentity;
import tw.cute.wlbot.queue.CooldownDecision;
import tw.cute.wlbot.queue.PendingApplication;
import tw.cute.wlbot.queue.PendingDecision;

class QueueMessageFormatterTest {
    private final QueueMessageFormatter formatter = new QueueMessageFormatter();

    @Test
    void formatsNewAndExistingQueuePositionsForTheNativeLoginScreen() {
        PendingApplication application = new PendingApplication(
                12L,
                new PlayerIdentity(UUID.randomUUID(), "QueuedPlayer"),
                Instant.parse("2026-10-31T10:00:00Z"),
                OptionalLong.empty());

        assertEquals(
                "白名單申請已送出\n目前順位：2 / 5\n請稍後重新連線查看進度。",
                formatter.format(new PendingDecision(application, 2, 5, true)));
        assertEquals(
                "白名單申請審核中\n目前順位：2 / 5\n請稍後重新連線查看進度。",
                formatter.format(new PendingDecision(application, 2, 5, false)));
    }

    @Test
    void roundsCooldownUpSoTheScreenNeverShowsZeroSeconds() {
        assertEquals(
                "申請冷卻中\n請在 2 秒後重新連線申請。",
                formatter.format(new CooldownDecision(Duration.ofMillis(1_001))));
        assertEquals(
                "申請冷卻中\n請在 1 秒後重新連線申請。",
                formatter.format(new CooldownDecision(Duration.ofMillis(1))));
    }
}
