package data;

/**
 * Stores a vocabulary word or phrase and its learning information.
 */
public class VocabularyEntry {

    private String word;
    private String meaning;
    private String dialect;
    private String difficulty;

    public VocabularyEntry(String word, String meaning, String dialect, String difficulty) {
        setWord(word);
        setMeaning(meaning);
        setDialect(dialect);
        setDifficulty(difficulty);
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = requireText(word, "Word");
    }

    public String getMeaning() {
        return meaning;
    }

    public void setMeaning(String meaning) {
        this.meaning = requireText(meaning, "Meaning");
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = requireText(dialect, "Dialect");
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = requireText(difficulty, "Difficulty");
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be empty.");
        }
        return value.strip().replaceAll("\\s+", " ");
    }
}
