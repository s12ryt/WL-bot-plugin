package tw.cute.wlbot.command;

/** Reloads the plugin configuration and rebuilds the runtime. */
@FunctionalInterface
public interface ReloadAction {
    String reload();
}
