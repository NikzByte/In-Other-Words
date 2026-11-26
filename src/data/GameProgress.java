package data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Holds progress for one playthrough. FileManager handles persistence. */
public class GameProgress {

    private int currentStage = 1;
    private String selectedPartner = "";
    private int affectionScore = 0;
    private final ArrayList<String> learnedWords = new ArrayList<>();
    private final Map<String, String> storyState = new LinkedHashMap<>();

    public String getStoryValue(String key) { return storyState.getOrDefault(key, ""); }

    public void setStoryValue(String key, String value) {
        if (key == null || key.isBlank() || value == null)
            throw new IllegalArgumentException("Story state needs a key and a value.");
        storyState.put(key, value);
    }

    public Map<String, String> getStoryState() { return new LinkedHashMap<>(storyState); }
    public void removeStoryValue(String key) { storyState.remove(key); }

    /** Copy into the shared instance so UI, scoring and file handling stay in sync. */
    public void copyFrom(GameProgress source) {
        if (source == this) return;
        resetProgress();
        setCurrentStage(source.getCurrentStage());
        setSelectedPartner(source.getSelectedPartner());
        setAffectionScore(source.getAffectionScore());
        for (String word : source.getLearnedWords()) addLearnedWord(word);
        storyState.putAll(source.getStoryState());
    }

    public int getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(int stage) {
        if (stage < 1 || stage > 4) {
            throw new IllegalArgumentException("Stage must be between 1 and 4.");
        }
        currentStage = stage;
    }

    /** An empty string means that no partner has been selected. */
    public String getSelectedPartner() {
        return selectedPartner;
    }

    public void setSelectedPartner(String partner) {
        if (partner == null) {
            throw new IllegalArgumentException("Partner must not be null; use an empty string to clear it.");
        }
        selectedPartner = partner.strip();
    }

    public int getAffectionScore() {
        return affectionScore;
    }

    /** Score limits and scoring rules belong to the mechanics classes. */
    public void setAffectionScore(int score) {
        affectionScore = score;
    }

    public ArrayList<String> getLearnedWords() {
        return new ArrayList<>(learnedWords);
    }

    public void addLearnedWord(String word) {
        if (word == null || word.isBlank()) {
            throw new IllegalArgumentException("Learned word must not be empty.");
        }
        String normalizedWord = word.strip().replaceAll("\\s+", " ");
        for (String learnedWord : learnedWords) {
            if (learnedWord.equalsIgnoreCase(normalizedWord)) {
                return;
            }
        }
        learnedWords.add(normalizedWord);
    }

    /**
     * Returns the rapport/affection score for a specific love interest.
     * @param initial 'J' for Jessa, 'I' for Inna, 'N' for Nikka
     */
    public int getAffectionFor(char initial) {
        String raw = storyState.getOrDefault("rapport_" + initial, "0");
        try { return Integer.parseInt(raw); } catch (NumberFormatException e) { return 0; }
    }

    /** Sets the rapport for a specific love interest (used by save/load round-trip). */
    public void setAffectionFor(char initial, int value) {
        storyState.put("rapport_" + initial, Integer.toString(value));
    }

    /** Returns the name of the partner initial, or empty string if unrecognised. */
    public static String partnerName(String partner) {
        if (partner == null) return "";
        return switch (partner.strip().toLowerCase()) {
            case "jessa", "j" -> "Jessa";
            case "inna",  "i" -> "Inna";
            case "nikka", "n" -> "Nikka";
            default -> partner.strip();
        };
    }

    /** Returns the initial character for a partner name ('J','I','N'), or '\0' if unknown. */
    public static char partnerInitial(String partner) {
        if (partner == null || partner.isBlank()) return '\0';
        return switch (partner.strip().toLowerCase().charAt(0)) {
            case 'j' -> 'J';
            case 'i' -> 'I';
            case 'n' -> 'N';
            default  -> '\0';
        };
    }

    /** Resets in-memory progress. Save it explicitly to replace an existing save file. */
    public void resetProgress() {
        currentStage = 1;
        selectedPartner = "";
        affectionScore = 0;
        learnedWords.clear();
        storyState.clear();
    }
}
