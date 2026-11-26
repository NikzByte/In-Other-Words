package ai;

import java.time.Duration;
import java.util.Objects;

/**
 * Settings for the Gemini connection, read from environment variables so the
 * API key is never written in the source code or committed to GitHub.
 *
 * GEMINI_API_KEY  (required) your key from Google AI Studio
 * GEMINI_MODEL    (optional) defaults to gemini-3.5-flash-lite
 */
public final class AiConfig {

    public static final String KEY_VARIABLE = "GEMINI_API_KEY";
    public static final String MODEL_VARIABLE = "GEMINI_MODEL";
    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    public static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public AiConfig(String apiKey, String model, Duration timeout) {
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip();
        this.timeout = Objects.requireNonNull(timeout, "Timeout must not be null.");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be greater than zero.");
        }
        if (!isValidModelName(this.model)) {
            throw new IllegalArgumentException("Invalid Gemini model name: " + this.model);
        }
    }

    /**
     * Reads the key and model from the environment, with an 8 second timeout.
     * Never throws: a misspelled GEMINI_MODEL falls back to the default model instead of crashing the game.
     */
    public static AiConfig fromEnvironment() {
        String model = System.getenv(MODEL_VARIABLE);
        if (model != null && !model.isBlank() && !isValidModelName(model.strip())) {
            System.err.println("Ignoring invalid " + MODEL_VARIABLE + "; using " + DEFAULT_MODEL + ".");
            model = null;
        }
        return new AiConfig(System.getenv(KEY_VARIABLE), model, Duration.ofSeconds(8));
    }

    /** Letters, digits, dots, dashes and underscores only, so the name is safe inside the URL. */
    public static boolean isValidModelName(String model) {
        return model != null && model.matches("[A-Za-z0-9._-]+");
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    /** Package-private so other packages cannot read or print the key. */
    String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public String getEndpoint() {
        return BASE_URL + model + ":generateContent";
    }

    @Override
    public String toString() {
        return "AiConfig[model=" + model + ", apiKey=" + (hasApiKey() ? "set" : "missing")
                + ", timeout=" + timeout.toSeconds() + "s]";
    }
}
