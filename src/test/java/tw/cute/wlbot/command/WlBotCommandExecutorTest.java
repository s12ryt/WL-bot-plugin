package tw.cute.wlbot.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class WlBotCommandExecutorTest {

    @Test
    void deniesSendersWithoutPermissionBeforeTouchingTheService() {
        List<String> relayed = new ArrayList<>();
        RecordingPorts ports = new RecordingPorts();
        WlBotCommandExecutor executor = new WlBotCommandExecutor(ports.service());

        boolean handled = executor.onCommand(
                sender(false, relayed), command(), "wl-bot", new String[] {"reload"});

        assertTrue(handled);
        assertEquals(List.of("你沒有權限執行此指令。"), relayed);
        assertTrue(ports.reloads.isEmpty());
        assertTrue(ports.removals.isEmpty());
    }

    @Test
    void relaysServiceRepliesToPermittedSenders() {
        List<String> relayed = new ArrayList<>();
        RecordingPorts ports = new RecordingPorts();
        ports.names = List.of("Alice");
        WlBotCommandExecutor executor = new WlBotCommandExecutor(ports.service());

        boolean handled = executor.onCommand(
                sender(true, relayed), command(), "wl-bot", new String[] {"del", "alice"});

        assertTrue(handled);
        assertEquals(List.of("已將 Alice 從白名單移除。"), relayed);
        assertEquals(List.of("Alice"), ports.removals);
    }

    @Test
    void tabCompletionDelegatesToTheServiceForPermittedSendersOnly() {
        List<String> relayed = new ArrayList<>();
        RecordingPorts ports = new RecordingPorts();
        ports.names = List.of("Alice", "Bob", "Charlie");
        WlBotCommandExecutor executor = new WlBotCommandExecutor(ports.service());

        List<String> suggestions = executor.onTabComplete(
                sender(true, relayed), command(), "wl-bot", new String[] {"del", "A"});
        List<String> deniedSuggestions = executor.onTabComplete(
                sender(false, relayed), command(), "wl-bot", new String[] {"del", "A"});
        List<String> subcommands = executor.onTabComplete(
                sender(true, relayed), command(), "wl-bot", new String[] {""});

        assertEquals(List.of("Alice"), suggestions);
        assertEquals(List.of(), deniedSuggestions);
        assertEquals(List.of("del", "reload"), subcommands);
    }

    private static Command command() {
        return new Command("wl-bot") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return false;
            }
        };
    }

    private static CommandSender sender(boolean permitted, List<String> messages) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[] {CommandSender.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> permitted;
                    case "sendMessage" -> {
                        Object payload = arguments[0];
                        if (payload instanceof String[] multiple) {
                            messages.addAll(List.of(multiple));
                        } else {
                            messages.add(String.valueOf(payload));
                        }
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "StubCommandSender";
                    default -> null;
                });
    }

    private static final class RecordingPorts {
        final List<String> reloads = new ArrayList<>();
        final List<String> removals = new ArrayList<>();
        List<String> names = List.of();

        WlBotCommandService service() {
            return new WlBotCommandService(
                    () -> {
                        reloads.add("called");
                        return "unused";
                    },
                    () -> names,
                    removals::add);
        }
    }
}
