package tw.cute.wlbot.command;

import java.util.List;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Bukkit adapter that wires {@code /wl-bot} to {@link WlBotCommandService}. */
public final class WlBotCommandExecutor implements CommandExecutor, TabCompleter {
    static final String PERMISSION = "wlbot.admin";

    private final WlBotCommandService service;

    public WlBotCommandExecutor(WlBotCommandService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Objects.requireNonNull(sender, "sender");
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("你沒有權限執行此指令。");
            return true;
        }
        sender.sendMessage(service.execute(args));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        Objects.requireNonNull(sender, "sender");
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        return service.suggest(args);
    }
}
