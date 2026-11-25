package mechanics;

import data.GameProgress;
import java.util.Objects;

/** Updates the shared progress; default answer scoring is +1 for correct and -1 for incorrect. */
public class AffectionManager {

    private final GameProgress progress;
    private final int correctPoints;
    private final int incorrectPenalty;

    public AffectionManager(GameProgress progress) {
        this(progress, 1, 1);
    }

    public AffectionManager(GameProgress progress, int correctPoints, int incorrectPenalty) {
        this.progress = Objects.requireNonNull(progress, "Progress must not be null.");
        if (correctPoints < 0 || incorrectPenalty < 0) {
            throw new IllegalArgumentException("Reward and penalty must be nonnegative.");
        }
        this.correctPoints = correctPoints;
        this.incorrectPenalty = incorrectPenalty;
    }

    public void increaseAffection(int points) {
        requireNonnegative(points);
        adjustAffection(points);
    }

    public void decreaseAffection(int points) {
        requireNonnegative(points);
        adjustAffection(-points);
    }

    /** Applies a signed score from a scripted choice, once per submitted answer. */
    public void adjustAffection(int points) {
        int updatedScore = Math.addExact(progress.getAffectionScore(), points);
        progress.setAffectionScore(updatedScore);
    }

    public int getAffectionScore() {
        return progress.getAffectionScore();
    }

    public void updateAffection(boolean correct) {
        adjustAffection(correct ? correctPoints : -incorrectPenalty);
    }

    /** Descriptive score state; ending thresholds are owned by EndingManager. */
    public String getAffectionLevel() {
        int score = getAffectionScore();
        return score > 0 ? "Positive" : score < 0 ? "Negative" : "Neutral";
    }

    public void resetAffection() {
        progress.setAffectionScore(0);
    }

    GameProgress getProgress() {
        return progress;
    }

    private void requireNonnegative(int points) {
        if (points < 0) {
            throw new IllegalArgumentException("Use a nonnegative number of points.");
        }
    }
}
