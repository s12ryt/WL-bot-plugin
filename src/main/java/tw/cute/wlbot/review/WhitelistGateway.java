package tw.cute.wlbot.review;

import java.util.concurrent.CompletionStage;
import tw.cute.wlbot.domain.PlayerIdentity;

@FunctionalInterface
public interface WhitelistGateway {
    CompletionStage<Void> add(PlayerIdentity player);
}
