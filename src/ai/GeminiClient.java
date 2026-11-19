package ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Sends a prompt to Google Gemini's generateContent REST endpoint using Java's
 * built-in HttpClient (no external libraries) and returns the model's text.
 *
 * This call is slow (network), so the game must never call it on the Swing UI thread.
 * Every failure is reported as an AiException so the caller can fall back.
 */
public class GeminiClient {

    private static final int MAX_OUTPUT_TOKENS = 120;

    private final AiConfig config;
    private final HttpClient http;

    public GeminiClient(AiConfig config) {
        this.config = Objects.requireNonNull(config, "Config must not be null.");
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.getTimeout())
                .build();
    }

    public AiConfig getConfig() {
        return config;
    }

    /** Asks Gemini to judge one typed answer and returns its raw one-line reply. */
    public String checkAnswer(AnswerCheckRequest request) throws AiException {
        return generate(PromptBuilder.systemInstruction(), PromptBuilder.userMessage(request));
    }

    /** Sends one system instruction and one user message, and returns the reply text. */
    public String generate(String systemText, String userText) throws AiException {
        if (!config.hasApiKey()) {
            throw new AiException("No Gemini API key. Set the " + AiConfig.KEY_VARIABLE + " environment variable.");
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(config.getEndpoint()))
                    .timeout(config.getTimeout())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("x-goog-api-key", config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(systemText, userText), StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException error) {
            // For example a key with a hidden line break from copy-paste. The key itself is never shown.
            throw new AiException("The Gemini API key contains invalid characters. Set "
                    + AiConfig.KEY_VARIABLE + " again.", error);
        }

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException error) {
            throw new AiException("Gemini did not answer within " + describe(config.getTimeout()) + ".", error);
        } catch (IOException error) {
            throw new AiException("Could not reach Gemini. Check your internet connection.", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AiException("The Gemini request was interrupted.", error);
        }
        return readReply(response.statusCode(), response.body());
    }

    /** "8 seconds", "1.5 seconds" or "500 ms", so short timeouts are not shown as "0 seconds". */
    static String describe(Duration timeout) {
        long ms = timeout.toMillis();
        if (ms < 1000) {
            return ms + " ms";
        }
        return ms % 1000 == 0 ? (ms / 1000) + " seconds" : String.format(Locale.ROOT, "%.1f seconds", ms / 1000.0);
    }

    /** Builds the generateContent JSON body. */
    static String requestBody(String systemText, String userText) {
        return "{"
                + "\"systemInstruction\":{\"parts\":[{\"text\":" + MiniJson.quote(systemText) + "}]},"
                + "\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":" + MiniJson.quote(userText) + "}]}],"
                + "\"generationConfig\":{\"temperature\":0,\"maxOutputTokens\":" + MAX_OUTPUT_TOKENS + "}"
                + "}";
    }

    /** Turns an HTTP status and JSON body into reply text, or explains why there is none. */
    static String readReply(int status, String body) throws AiException {
        Object json;
        try {
            json = MiniJson.parse(body);
        } catch (IllegalArgumentException error) {
            // Proxies and outages often send an HTML page instead of JSON.
            throw new AiException(status == 200
                    ? "Gemini sent a reply that is not valid JSON."
                    : "Gemini server error HTTP " + status + " (the reply was not JSON).", error);
        }

        if (status != 200) {
            Object message = MiniJson.get(json, "error", "message");
            String detail = message instanceof String text ? text : "no details";
            throw new AiException(switch (status) {
                case 400 -> "Gemini rejected the request: " + detail;
                case 401, 403 -> "Gemini refused the API key. Check " + AiConfig.KEY_VARIABLE + ". (" + detail + ")";
                case 404 -> "Gemini model not found: " + detail;
                case 429 -> "Gemini rate limit or free-tier quota reached. Try again later.";
                default -> "Gemini error HTTP " + status + ": " + detail;
            });
        }

        Object blockReason = MiniJson.get(json, "promptFeedback", "blockReason");
        if (blockReason != null) {
            throw new AiException("Gemini blocked the prompt: " + blockReason);
        }

        Object parts = MiniJson.get(json, "candidates", 0, "content", "parts");
        StringBuilder text = new StringBuilder();
        if (parts instanceof List<?> list) {
            for (Object part : list) {
                // Skip the model's "thinking" parts; only the final answer is wanted.
                if (Boolean.TRUE.equals(MiniJson.get(part, "thought"))) {
                    continue;
                }
                if (MiniJson.get(part, "text") instanceof String piece) {
                    text.append(piece);
                }
            }
        }
        if (text.toString().isBlank()) {
            Object finish = MiniJson.get(json, "candidates", 0, "finishReason");
            throw new AiException("Gemini returned no text" + (finish != null ? " (finishReason " + finish + ")." : "."));
        }
        return text.toString().strip();
    }
}
