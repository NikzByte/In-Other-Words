package ai;

import java.util.Objects;

/**
 * The AI's judgement of one typed answer, after AiResponseParser has checked it.
 *
 * @param result   CORRECT, CLOSE or WRONG
 * @param feedback one short English sentence for the player (never blank)
 */
public record AiVerdict(Result result, String feedback) {

    public enum Result { CORRECT, CLOSE, WRONG }

    public AiVerdict {
        Objects.requireNonNull(result, "Result must not be null.");
        if (feedback == null || feedback.isBlank()) {
            throw new IllegalArgumentException("Feedback must not be blank.");
        }
    }

    public boolean isCorrect() {
        return result == Result.CORRECT;
    }

    public boolean isClose() {
        return result == Result.CLOSE;
    }
}
