package mechanics;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

/** Checks scripted answers. It does not infer grammar or ask an AI to grade answers. */
public class AnswerValidator {

    private final ArrayList<String> acceptedAnswers = new ArrayList<>();
    private final ArrayList<String> choices = new ArrayList<>();
    private int correctChoice = -1;

    public AnswerValidator() {
    }

    public AnswerValidator(Collection<String> answers) {
        setAcceptedAnswers(answers);
    }

    /** The story author supplies all accepted variations of a typed answer. */
    public void setAcceptedAnswers(Collection<String> answers) {
        if (answers == null || answers.isEmpty()) {
            throw new IllegalArgumentException("At least one accepted answer is required.");
        }
        ArrayList<String> validated = new ArrayList<>();
        for (String answer : answers) {
            if (answer == null || answer.isBlank()) {
                throw new IllegalArgumentException("Accepted answers must not be blank.");
            }
            validated.add(answer.strip());
        }
        acceptedAnswers.clear();
        acceptedAnswers.addAll(validated);
        choices.clear();
        correctChoice = -1;
    }

    /** All choice indexes are zero-based: the first choice is 0. */
    public void setMultipleChoice(Collection<String> options, int correctIndex) {
        if (options == null || options.isEmpty() || correctIndex < 0 || correctIndex >= options.size()) {
            throw new IllegalArgumentException("Provide choices and a valid correct choice index.");
        }
        ArrayList<String> validated = new ArrayList<>();
        for (String option : options) {
            if (option == null || option.isBlank()) {
                throw new IllegalArgumentException("Choices must not be blank.");
            }
            validated.add(option);
        }
        choices.clear();
        choices.addAll(validated);
        correctChoice = correctIndex;
        acceptedAnswers.clear();
    }

    public boolean checkAnswer(String answer) {
        return isCorrectAnswer(answer);
    }

    /** Ignores case and extra whitespace, but preserves accents and punctuation. */
    public String normalizeAnswer(String answer) {
        if (answer == null) {
            return "";
        }
        return Normalizer.normalize(answer, Normalizer.Form.NFC)
                .strip().replaceAll("(?U)\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public boolean isCorrectAnswer(String answer) {
        requireConfigured();
        String normalized = normalizeAnswer(answer);
        if (normalized.isEmpty()) {
            return false;
        }
        if (correctChoice >= 0) {
            return normalized.equals(normalizeAnswer(choices.get(correctChoice)));
        }
        for (String accepted : acceptedAnswers) {
            if (normalized.equals(normalizeAnswer(accepted))) {
                return true;
            }
        }
        return false;
    }

    public boolean checkMultipleChoice(int choice) {
        if (correctChoice < 0) {
            throw new IllegalStateException("Multiple-choice answers have not been configured.");
        }
        return choice >= 0 && choice < choices.size() && choice == correctChoice;
    }

    public boolean checkTypedResponse(String answer) {
        if (acceptedAnswers.isEmpty()) {
            throw new IllegalStateException("Typed answers have not been configured.");
        }
        return isCorrectAnswer(answer);
    }

    public String getCorrectAnswer() {
        requireConfigured();
        return correctChoice >= 0 ? choices.get(correctChoice) : acceptedAnswers.get(0);
    }

    public String getFeedback(boolean correct) {
        return correct ? "Correct!" : "That answer does not match an accepted response.";
    }

    private void requireConfigured() {
        if (acceptedAnswers.isEmpty() && correctChoice < 0) {
            throw new IllegalStateException("Configure accepted answers before checking a response.");
        }
    }
}
