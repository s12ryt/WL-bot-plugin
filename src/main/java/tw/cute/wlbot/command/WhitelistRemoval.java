package tw.cute.wlbot.command;

/** Removes a player from the whitelist using the exact whitelisted name. */
@FunctionalInterface
public interface WhitelistRemoval {
    void remove(String playerName);
}
