package data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Stores vocabulary by dialect and word, with case-insensitive lookups. */
public class Dictionary {

    private final Map<String, VocabularyEntry> words = new LinkedHashMap<>();

    /** Adding the same word in the same dialect replaces its previous entry. */
    public void addWord(VocabularyEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Vocabulary entry must not be null.");
        }
        VocabularyEntry storedEntry = copyEntry(entry);
        words.put(key(storedEntry.getWord(), storedEntry.getDialect()), storedEntry);
    }

    /** Returns the first matching meaning, or null when the word is unknown. */
    public String getMeaning(String word) {
        VocabularyEntry entry = searchWord(word);
        return entry == null ? null : entry.getMeaning();
    }

    public String getMeaning(String word, String dialect) {
        VocabularyEntry entry = searchWord(word, dialect);
        return entry == null ? null : entry.getMeaning();
    }

    /** Use the dialect overload when a word exists in more than one dialect. */
    public VocabularyEntry searchWord(String word) {
        String normalizedWord = normalize(word);
        for (VocabularyEntry entry : words.values()) {
            if (normalize(entry.getWord()).equals(normalizedWord)) {
                return copyEntry(entry);
            }
        }
        return null;
    }

    public VocabularyEntry searchWord(String word, String dialect) {
        VocabularyEntry entry = words.get(key(word, dialect));
        return entry == null ? null : copyEntry(entry);
    }

    /** Checks a complete word or phrase against the dictionary, not sentence grammar. */
    public boolean isCorrectUsage(String word) {
        return searchWord(word) != null;
    }

    public boolean isCorrectUsage(String word, String dialect) {
        return searchWord(word, dialect) != null;
    }

    public ArrayList<VocabularyEntry> getWordsByDialect(String dialect) {
        ArrayList<VocabularyEntry> matches = new ArrayList<>();
        String normalizedDialect = normalize(dialect);
        for (VocabularyEntry entry : words.values()) {
            if (normalize(entry.getDialect()).equals(normalizedDialect)) {
                matches.add(copyEntry(entry));
            }
        }
        return matches;
    }

    /** Returns independent copies so callers cannot accidentally alter stored entries. */
    public ArrayList<VocabularyEntry> getAllWords() {
        ArrayList<VocabularyEntry> entries = new ArrayList<>();
        for (VocabularyEntry entry : words.values()) {
            entries.add(copyEntry(entry));
        }
        return entries;
    }

    /** Removes this word from every dialect. Use the overload to remove just one. */
    public boolean removeWord(String word) {
        String normalizedWord = normalize(word);
        return words.values().removeIf(entry -> normalize(entry.getWord()).equals(normalizedWord));
    }

    public boolean removeWord(String word, String dialect) {
        return words.remove(key(word, dialect)) != null;
    }

    private String key(String word, String dialect) {
        return normalize(dialect) + "\n" + normalize(word);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Search text must not be empty.");
        }
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private VocabularyEntry copyEntry(VocabularyEntry entry) {
        return new VocabularyEntry(entry.getWord(), entry.getMeaning(),
                entry.getDialect(), entry.getDifficulty());
    }
}
