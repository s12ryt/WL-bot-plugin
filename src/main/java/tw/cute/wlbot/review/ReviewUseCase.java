package tw.cute.wlbot.review;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ReviewUseCase {
    CompletionStage<ReviewOutcome> review(ReviewRequest request);
}
