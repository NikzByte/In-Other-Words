package ai;

import java.util.Objects;

/** An AiTutor backed by Google Gemini: sends the request, then parses and cleans the reply. */
public class GeminiTutor implements AiTutor {

    private final GeminiClient client;

    public GeminiTutor(GeminiClient client) {
        this.client = Objects.requireNonNull(client, "Client must not be null.");
    }

    /** Uses GEMINI_API_KEY and GEMINI_MODEL from the environment. */
    public static GeminiTutor fromEnvironment() {
        return new GeminiTutor(new GeminiClient(AiConfig.fromEnvironment()));
    }

    @Override
    public AiVerdict check(AnswerCheckRequest request) throws AiException {
        Objects.requireNonNull(request, "Request must not be null.");
        String raw = client.checkAnswer(request);
        return AiResponseParser.parse(raw, request.acceptedAnswers());
    }

    @Override
    public boolean isAvailable() {
        return client.getConfig().hasApiKey();
    }
}
