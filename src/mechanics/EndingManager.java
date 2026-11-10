package mechanics;

import data.GameProgress;
import java.util.Collection;
import java.util.Objects;

/**
 * Determines an ending from scripted confession answers and affection.
 * Initial policy (configurable): score >= 5 and a correct confession gives Romantic;
 * otherwise score >= 0 gives Classmates Only; lower scores give Rejection.
 * These thresholds are implementation defaults, not finalized narrative requirements.
 */
public class EndingManager {

    public static final String ROMANTIC = "Romantic";
    public static final String CLASSMATES_ONLY = "Classmates Only";
    public static final String REJECTION = "Rejection";

    private final GameProgress progress;
    private final AnswerValidator confessionValidator = new AnswerValidator();
    private final int romanticThreshold;
    private final int classmatesThreshold;
    private String confessionResponse;
    private boolean answersConfigured;

    public EndingManager(GameProgress progress) {
        this(progress, 5, 0);
    }

    public EndingManager(GameProgress progress, int romanticThreshold, int classmatesThreshold) {
        this.progress = Objects.requireNonNull(progress, "Progress must not be null.");
        if (romanticThreshold <= classmatesThreshold) {
            throw new IllegalArgumentException("The romantic threshold must exceed the classmates threshold.");
        }
        this.romanticThreshold = romanticThreshold;
        this.classmatesThreshold = classmatesThreshold;
    }

    public void setAcceptedConfessionAnswers(Collection<String> answers) {
        confessionValidator.setAcceptedAnswers(answers);
        answersConfigured = true;
        confessionResponse = null;
    }

    /** Records the final answer; scoring remains the responsibility of DialogueManager. */
    public void setConfessionResponse(String answer) {
        if (!answersConfigured) {
            throw new IllegalStateException("Configure the accepted confession answers first.");
        }
        requireFinalStage();
        confessionResponse = Objects.requireNonNull(answer, "Confession response must not be null.");
    }

    public String evaluateEnding() {
        return determineEnding();
    }

    public boolean checkAffectionRequirement() {
        return progress.getAffectionScore() >= romanticThreshold;
    }

    public boolean checkConfessionResponse() {
        return answersConfigured && confessionResponse != null
                && confessionValidator.checkTypedResponse(confessionResponse);
    }

    /** Call after the final conversation; reading an ending never changes affection. */
    public String determineEnding() {
        requireFinalStage();
        if (!answersConfigured || confessionResponse == null) {
            throw new IllegalStateException("Submit the final confession before determining an ending.");
        }
        if (checkAffectionRequirement() && checkConfessionResponse()) {
            return ROMANTIC;
        }
        return progress.getAffectionScore() >= classmatesThreshold ? CLASSMATES_ONLY : REJECTION;
    }

    /** Provides the ending label for the UI; it does not draw or print the ending screen. */
    public String showEnding() {
        return determineEnding();
    }

    /** Clears the response and its answer configuration for a new character/playthrough. */
    public void resetEnding() {
        confessionResponse = null;
        answersConfigured = false;
    }

    private void requireFinalStage() {
        if (progress.getCurrentStage() != 4 || progress.getSelectedPartner().isEmpty()) {
            throw new IllegalStateException("An ending requires Stage 4 and a selected partner.");
        }
    }
}
