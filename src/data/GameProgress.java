package data;

import java.util.ArrayList;

/** Holds progress for one playthrough. FileManager handles persistence. */
public class GameProgress {

    private int currentStage = 1;
    private String selectedPartner = "";
    private int affectionScore = 0;
    private final ArrayList<String> learnedWords = new ArrayList<>();

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

    /** Resets in-memory progress. Save it explicitly to replace an existing save file. */
    public void resetProgress() {
        currentStage = 1;
        selectedPartner = "";
        affectionScore = 0;
        learnedWords.clear();
    }
}
