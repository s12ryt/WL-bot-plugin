package tw.cute.wlbot.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WlBotCommandServiceTest {
    private final FakeWhitelist whitelist = new FakeWhitelist();

    @Test
    void suggestsSubcommandsByPrefix() {
        WlBotCommandService service = service(() -> "unused");

        assertEquals(List.of("del", "reload"), service.suggest(new String[] {""}));
        assertEquals(List.of("reload"), service.suggest(new String[] {"r"}));
        assertEquals(List.of("reload"), service.suggest(new String[] {"RE"}));
        assertEquals(List.of("del"), service.suggest(new String[] {"del"}));
        assertEquals(List.of(), service.suggest(new String[] {"xyz"}));
    }

    @Test
    void suggestsWhitelistedPlayersForDelByCaseInsensitivePrefix() {
        whitelist.names = List.of("Alice", "bob", "Charlie");
        WlBotCommandService service = service(() -> "unused");

        assertEquals(List.of("Alice", "bob", "Charlie"), service.suggest(new String[] {"del", ""}));
        assertEquals(List.of("Alice"), service.suggest(new String[] {"del", "a"}));
        assertEquals(List.of("Alice"), service.suggest(new String[] {"del", "AL"}));
        assertEquals(List.of("bob"), service.suggest(new String[] {"del", "B"}));
        assertEquals(List.of(), service.suggest(new String[] {"del", "zz"}));
    }

    @Test
    void suggestsNothingForUnknownSubcommandsOrExtraArguments() {
        WlBotCommandService service = service(() -> "unused");

        assertEquals(List.of(), service.suggest(new String[] {"reload", ""}));
        assertEquals(List.of(), service.suggest(new String[] {"del", "Alice", ""}));
        assertEquals(List.of(), service.suggest(new String[] {}));
    }

    @Test
    void reloadDelegatesToTheActionAndReportsSuccess() {
        AtomicInteger reloadCount = new AtomicInteger();
        WlBotCommandService service = service(() -> {
            reloadCount.incrementAndGet();
            return "reloaded";
        });

        String reply = service.execute(new String[] {"reload"});

        assertEquals("WLBotPlugin 設定已重載。", reply);
        assertEquals(1, reloadCount.get());
    }

    @Test
    void reloadFailureReportsAFriendlyMessageWithoutThrowing() {
        WlBotCommandService service = service(() -> {
            throw new IllegalArgumentException("telegram.bot-token 未設定");
        });

        String reply = service.execute(new String[] {"reload"});

        assertTrue(reply.contains("重載失敗"));
        assertTrue(reply.contains("telegram.bot-token 未設定"));
        assertTrue(reply.contains("保留舊設定"));
    }

    @Test
    void delRemovesExactlyMatchedPlayerCaseInsensitively() {
        whitelist.names = List.of("Alice", "Bob");
        WlBotCommandService service = service(() -> "unused");

        String reply = service.execute(new String[] {"del", "alice"});

        assertEquals("已將 Alice 從白名單移除。", reply);
        assertEquals(List.of("Alice"), whitelist.removed);
    }

    @Test
    void delReportsUnknownPlayerWithoutTouchingTheWhitelist() {
        whitelist.names = List.of("Alice");
        WlBotCommandService service = service(() -> "unused");

        String reply = service.execute(new String[] {"del", "bob"});

        assertTrue(reply.contains("bob 不在白名單中"));
        assertTrue(whitelist.removed.isEmpty());
    }

    @Test
    void delFailureReportsTheErrorWithoutThrowing() {
        whitelist.names = List.of("Alice");
        whitelist.failRemovalWith = new IllegalStateException("region scheduler 關閉中");
        WlBotCommandService service = service(() -> "unused");

        String reply = service.execute(new String[] {"del", "Alice"});

        assertTrue(reply.contains("移除失敗"));
        assertTrue(reply.contains("region scheduler 關閉中"));
    }

    @Test
    void missingOrUnknownArgumentsPrintUsage() {
        WlBotCommandService service = service(() -> "unused");

        String noArgs = service.execute(new String[] {});
        String unknown = service.execute(new String[] {"frobnicate"});
        String delWithoutPlayer = service.execute(new String[] {"del"});
        String delWithExtra = service.execute(new String[] {"del", "Alice", "now"});

        assertTrue(noArgs.contains("/wl-bot"));
        assertTrue(unknown.contains("/wl-bot"));
        assertTrue(delWithoutPlayer.contains("/wl-bot"));
        assertTrue(delWithExtra.contains("/wl-bot"));
        assertEquals(noArgs, unknown);
        assertTrue(whitelist.removed.isEmpty());
    }

    private WlBotCommandService service(ReloadAction reloadAction) {
        return new WlBotCommandService(reloadAction, whitelist::names, whitelist::remove);
    }

    private static final class FakeWhitelist {
        List<String> names = List.of();
        final List<String> removed = new ArrayList<>();
        RuntimeException failRemovalWith;

        List<String> names() {
            return names;
        }

        void remove(String playerName) {
            if (failRemovalWith != null) {
                throw failRemovalWith;
            }
            removed.add(playerName);
        }
    }
}
