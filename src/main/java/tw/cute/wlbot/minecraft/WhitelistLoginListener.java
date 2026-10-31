package tw.cute.wlbot.minecraft;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import java.time.Clock;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import tw.cute.wlbot.domain.PlayerIdentity;

public final class WhitelistLoginListener implements Listener {
    private final LoginRequestService requestService;
    private final Clock clock;
    private final Logger logger;

    public WhitelistLoginListener(LoginRequestService requestService, Clock clock, Logger logger) {
        this.requestService = Objects.requireNonNull(requestService, "requestService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWhitelistCheck(ProfileWhitelistVerifyEvent event) {
        boolean rejectedByWhitelist = event.isWhitelistEnabled()
                && !event.isWhitelisted()
                && !event.isOp();
        try {
            PlayerProfile profile = event.getPlayerProfile();
            requestService.handle(
                            rejectedByWhitelist,
                            new PlayerIdentity(
                                    profile.getId(),
                                    profile.getName()),
                            clock.instant())
                    .ifPresent(message -> event.kickMessage(Component.text(message)));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "無法建立或查詢白名單申請", exception);
        }
    }
}
