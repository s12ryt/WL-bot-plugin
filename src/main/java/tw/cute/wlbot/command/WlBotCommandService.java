package tw.cute.wlbot.command;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Core behaviour behind {@code /wl-bot}: reloading configuration and removing
 * whitelist entries, plus the matching rules for tab completion.
 */
public final class WlBotCommandService {
    static final List<String> SUBCOMMANDS = List.of("reload", "del");
    private static final String USAGE = "用法：/wl-bot <reload | del <玩家名>>";

    private final ReloadAction reloadAction;
    private final WhitelistNames whitelistNames;
    private final WhitelistRemoval removal;

    public WlBotCommandService(
            ReloadAction reloadAction,
            WhitelistNames whitelistNames,
            WhitelistRemoval removal) {
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
        this.whitelistNames = Objects.requireNonNull(whitelistNames, "whitelistNames");
        this.removal = Objects.requireNonNull(removal, "removal");
    }

    public String execute(String[] args) {
        Objects.requireNonNull(args, "args");
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            return reload();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("del")) {
            return removePlayer(args[1]);
        }
        return USAGE;
    }

    public List<String> suggest(String[] args) {
        Objects.requireNonNull(args, "args");
        if (args.length == 1) {
            return filterByPrefix(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("del")) {
            return filterByPrefix(whitelistNames.names(), args[1]);
        }
        return List.of();
    }

    private String reload() {
        try {
            reloadAction.reload();
            return "WLBotPlugin 設定已重載。";
        } catch (RuntimeException exception) {
            return "重載失敗，已保留舊設定：" + exception.getMessage();
        }
    }

    private String removePlayer(String argument) {
        String matched = whitelistNames.names().stream()
                .filter(name -> name.equalsIgnoreCase(argument))
                .findFirst()
                .orElse(null);
        if (matched == null) {
            return argument + " 不在白名單中。";
        }
        try {
            removal.remove(matched);
            return "已將 " + matched + " 從白名單移除。";
        } catch (RuntimeException exception) {
            return "移除失敗：" + exception.getMessage();
        }
    }

    private static List<String> filterByPrefix(List<String> candidates, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted(Comparator.comparing((String candidate) -> candidate.toLowerCase(Locale.ROOT))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }
}
