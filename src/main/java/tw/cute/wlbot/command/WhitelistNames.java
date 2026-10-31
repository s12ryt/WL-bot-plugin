package tw.cute.wlbot.command;

import java.util.List;

/** Provides the current whitelist player names for completion and lookup. */
@FunctionalInterface
public interface WhitelistNames {
    List<String> names();
}
