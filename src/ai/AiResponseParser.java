package ai;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns Gemini's raw text into a safe AiVerdict.
 *
 * Expected format: VERDICT: CORRECT|CLOSE|WRONG | FEEDBACK: one sentence
 * - Tolerates markdown (**, `), extra spaces, other lines and lower case.
 * - Rejects anything without a clear verdict by throwing AiException (the caller falls back).
 * - Cleans the feedback and limits it to MAX_FEEDBACK_LENGTH characters.
 * - For CLOSE and WRONG, feedback that reveals an accepted answer is replaced with a
 *   general hint, because the game's own hint and "Show model answer" button do that job.
 */
public final class AiResponseParser {

    public static final int MAX_FEEDBACK_LENGTH = 140;

    /** The verdict must START the line (after an optional bullet), so a sentence that only mentions it is ignored. */
    private static final Pattern FORMAT = Pattern.compile(
            "^(?:[-*>]\\s*)?VERDICT\\s*:\\s*(CORRECT|CLOSE|WRONG)\\b\\s*(?:\\|\\s*FEEDBACK\\s*:\\s*(.*))?",
            Pattern.CASE_INSENSITIVE);

    /** Feedback written on its own line, right after the verdict line. */
    private static final Pattern FEEDBACK_LINE = Pattern.compile(
            "^(?:[-*>]\\s*)?FEEDBACK\\s*:\\s*(.*)", Pattern.CASE_INSENSITIVE);

    private AiResponseParser() {
    }

    public static AiVerdict parse(String raw, List<String> acceptedAnswers) throws AiException {
        if (raw == null || raw.isBlank()) {
            throw new AiException("The AI reply was empty.");
        }
        String text = raw.replace("*", "").replace("`", "");
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            Matcher match = FORMAT.matcher(lines[i].strip());
            if (match.find()) {
                AiVerdict.Result result = AiVerdict.Result.valueOf(match.group(1).toUpperCase(Locale.ROOT));
                String feedback = cleanFeedback(match.group(2));
                if (feedback.isEmpty()) {
                    feedback = cleanFeedback(feedbackOnNextLine(lines, i + 1));
                }
                if (feedback.isEmpty()) {
                    feedback = defaultFeedback(result);
                } else if (result != AiVerdict.Result.CORRECT && revealsAnswer(feedback, acceptedAnswers)) {
                    feedback = defaultFeedback(result);
                }
                return new AiVerdict(result, feedback);
            }
        }
        throw new AiException("The AI reply did not contain a verdict.");
    }

    /** Returns the text of the first non-blank line after the verdict if it starts with FEEDBACK:, else null. */
    private static String feedbackOnNextLine(String[] lines, int start) {
        for (int i = start; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            Matcher match = FEEDBACK_LINE.matcher(lines[i].strip());
            return match.find() ? match.group(1) : null;
        }
        return null;
    }

    /** Removes control characters and extra spaces, and shortens long feedback at a word boundary. */
    static String cleanFeedback(String feedback) {
        if (feedback == null) {
            return "";
        }
        String clean = feedback.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
        if (clean.length() > MAX_FEEDBACK_LENGTH) {
            int cut = clean.lastIndexOf(' ', MAX_FEEDBACK_LENGTH - 1);
            clean = clean.substring(0, cut > 40 ? cut : MAX_FEEDBACK_LENGTH - 1).strip() + "…";
        }
        return clean;
    }

    /** True when any accepted answer appears as whole words inside the feedback. */
    static boolean revealsAnswer(String feedback, List<String> acceptedAnswers) {
        if (acceptedAnswers == null) {
            return false;
        }
        String haystack = " " + simplify(feedback) + " ";
        for (String answer : acceptedAnswers) {
            String needle = simplify(answer);
            if (!needle.isEmpty() && haystack.contains(" " + needle + " ")) {
                return true;
            }
        }
        return false;
    }

    /** Lower case, accents removed, punctuation turned into single spaces. */
    static String simplify(String text) {
        String plain = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return plain.replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    static String defaultFeedback(AiVerdict.Result result) {
        return switch (result) {
            case CORRECT -> "Nice! That works too.";
            case CLOSE -> "So close! Check your words against the conversation and try again.";
            case WRONG -> "Not quite. Think about the phrase you heard in the conversation.";
        };
    }
}
