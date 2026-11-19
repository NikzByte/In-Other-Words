package ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the AI needs to judge one typed answer from a TYPED story node.
 *
 * @param question        the story prompt shown to the player, e.g. Write "What?" in Cebuano.
 * @param playerReply     what the player typed
 * @param acceptedAnswers the author's accepted answers from story.tsv (never empty)
 * @param language        the regional language being practised, e.g. Cebuano
 */
public record AnswerCheckRequest(String question, String playerReply,
        List<String> acceptedAnswers, String language) {

    public static final int MAX_REPLY_LENGTH = 200;

    public AnswerCheckRequest {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question must not be blank.");
        }
        if (playerReply == null || playerReply.isBlank()) {
            throw new IllegalArgumentException("Player reply must not be blank.");
        }
        if (acceptedAnswers == null || acceptedAnswers.isEmpty()) {
            throw new IllegalArgumentException("At least one accepted answer is required.");
        }
        List<String> cleanAnswers = new ArrayList<>();
        for (String answer : acceptedAnswers) {
            if (answer == null || answer.isBlank()) {
                throw new IllegalArgumentException("Accepted answers must not be null or blank.");
            }
            cleanAnswers.add(answer.strip());
        }
        question = question.strip();
        playerReply = playerReply.strip();
        if (playerReply.length() > MAX_REPLY_LENGTH) {
            int cut = MAX_REPLY_LENGTH;
            // Do not split an emoji (a surrogate pair) in half.
            if (Character.isHighSurrogate(playerReply.charAt(cut - 1))) {
                cut--;
            }
            playerReply = playerReply.substring(0, cut);
        }
        acceptedAnswers = List.copyOf(cleanAnswers);
        language = language == null || language.isBlank() ? "the regional language" : language.strip();
    }
}
