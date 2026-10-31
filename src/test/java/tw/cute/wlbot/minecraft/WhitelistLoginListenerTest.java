package tw.cute.wlbot.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.cute.wlbot.presentation.QueueMessageFormatter;
import tw.cute.wlbot.queue.SqliteQueueRepository;

class WhitelistLoginListenerTest {
    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    private static final UUID PLAYER_UUID = UUID.fromString("69783aee-5399-4556-a78d-4c10e35c2327");
    private static final Component DEFAULT_KICK = Component.text("Not whitelisted");

    @TempDir
    Path temporaryDirectory;

    @Test
    void replacesNativeWhitelistKickMessageWithQueuePosition() throws Exception {
        try (var repository = SqliteQueueRepository.open(temporaryDirectory.resolve("queue.db"))) {
            WhitelistLoginListener listener = listener(repository);
            ProfileWhitelistVerifyEvent event = event(true, false, false);

            listener.onWhitelistCheck(event);

            assertEquals(Component.text(
                    "白名單申請已送出\n目前順位：1 / 1\n請稍後重新連線查看進度。"), event.kickMessage());
            assertTrue(repository.findPending(PLAYER_UUID).isPresent());
        }
    }

    @Test
    void ignoresProfilesThatNativeWhitelistWouldAllow() throws Exception {
        try (var repository = SqliteQueueRepository.open(temporaryDirectory.resolve("queue.db"))) {
            WhitelistLoginListener listener = listener(repository);
            ProfileWhitelistVerifyEvent whitelistDisabled = event(false, false, false);
            ProfileWhitelistVerifyEvent whitelisted = event(true, true, false);
            ProfileWhitelistVerifyEvent operator = event(true, false, true);

            listener.onWhitelistCheck(whitelistDisabled);
            listener.onWhitelistCheck(whitelisted);
            listener.onWhitelistCheck(operator);

            assertEquals(DEFAULT_KICK, whitelistDisabled.kickMessage());
            assertEquals(DEFAULT_KICK, whitelisted.kickMessage());
            assertEquals(DEFAULT_KICK, operator.kickMessage());
            assertTrue(repository.findPending(PLAYER_UUID).isEmpty());
        }
    }

    private WhitelistLoginListener listener(SqliteQueueRepository repository) {
        return new WhitelistLoginListener(
                new LoginRequestService(repository, new QueueMessageFormatter()),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Logger.getAnonymousLogger());
    }

    private static ProfileWhitelistVerifyEvent event(
            boolean whitelistEnabled,
            boolean whitelisted,
            boolean operator) {
        return new ProfileWhitelistVerifyEvent(
                profile(), whitelistEnabled, whitelisted, operator, DEFAULT_KICK);
    }

    private static PlayerProfile profile() {
        return (PlayerProfile) Proxy.newProxyInstance(
                PlayerProfile.class.getClassLoader(),
                new Class<?>[] {PlayerProfile.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getId" -> PLAYER_UUID;
                    case "getName" -> "QueuePlayer";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
