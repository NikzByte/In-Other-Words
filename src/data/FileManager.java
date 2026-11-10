package data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Properties;

/**
 * Loads UTF-8 resources and saves progress locally without external libraries.
 * Read/write failures are reported as IOException for the caller to handle.
 * Default saves go in the working directory's saves folder, outside game resources.
 */
public class FileManager {

    private final Path saveDirectory;
    private GameProgress currentProgress;

    public FileManager() {
        this(Path.of("saves"), new GameProgress());
    }

    public FileManager(Path saveDirectory) {
        this(saveDirectory, new GameProgress());
    }

    public FileManager(GameProgress progress) {
        this(Path.of("saves"), progress);
    }

    /** The supplied progress is used by saveVocabulary() until a save/load replaces it. */
    public FileManager(Path saveDirectory, GameProgress progress) {
        this.saveDirectory = Objects.requireNonNull(saveDirectory, "Save directory must not be null.");
        currentProgress = Objects.requireNonNull(progress, "Progress must not be null.");
    }

    /**
     * Returns dialogue lines in order, including blank lines.
     * Conversion to Loraine's dialogue classes belongs to the integration step.
     */
    public ArrayList<String> loadDialogue(String filename) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        try (BufferedReader reader = openResource(filename, "dialogues")) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(lines.isEmpty() ? removeBom(line) : line);
            }
        }
        return lines;
    }

    /**
     * Reads four tab-separated columns: word, meaning, dialect, difficulty.
     * An optional header uses those names. Blank lines and lines starting with #
     * are ignored. Tabs/newlines inside a field are not supported.
     */
    public ArrayList<VocabularyEntry> loadVocabulary(String filename) throws IOException {
        ArrayList<VocabularyEntry> entries = new ArrayList<>();
        try (BufferedReader reader = openResource(filename, "vocabulary")) {
            String line;
            int lineNumber = 0;
            boolean firstRecord = true;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1) {
                    line = removeBom(line);
                }
                if (line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (firstRecord && isVocabularyHeader(fields)) {
                    firstRecord = false;
                    continue;
                }
                firstRecord = false;
                if (fields.length != 4) {
                    throw new IOException("Expected four tab-separated fields in " + filename
                            + " at line " + lineNumber + ".");
                }
                try {
                    entries.add(new VocabularyEntry(fields[0], fields[1], fields[2], fields[3]));
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid vocabulary in " + filename
                            + " at line " + lineNumber + ": " + e.getMessage(), e);
                }
            }
        }
        return entries;
    }

    public void saveProgress(GameProgress progress) throws IOException {
        Objects.requireNonNull(progress, "Progress must not be null.");
        Properties properties = new Properties();
        properties.setProperty("currentStage", Integer.toString(progress.getCurrentStage()));
        properties.setProperty("selectedPartner", progress.getSelectedPartner());
        properties.setProperty("affectionScore", Integer.toString(progress.getAffectionScore()));
        ArrayList<String> learnedWords = progress.getLearnedWords();
        properties.setProperty("learnedWordCount", Integer.toString(learnedWords.size()));
        for (int i = 0; i < learnedWords.size(); i++) {
            properties.setProperty("learnedWord." + i, learnedWords.get(i));
        }
        StringWriter writer = new StringWriter();
        properties.store(writer, "In Other Words - game progress");
        writeSaveFile("progress.properties", writer.toString());
        currentProgress = progress;
    }

    /** A missing save starts a new game; malformed or unreadable saves raise IOException. */
    public GameProgress loadProgress() throws IOException {
        Path saveFile = saveDirectory.resolve("progress.properties");
        GameProgress loadedProgress = new GameProgress();
        if (Files.notExists(saveFile)) {
            currentProgress = loadedProgress;
            return loadedProgress;
        }

        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(saveFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
            loadedProgress.setCurrentStage(Integer.parseInt(required(properties, "currentStage")));
            loadedProgress.setSelectedPartner(required(properties, "selectedPartner"));
            loadedProgress.setAffectionScore(Integer.parseInt(required(properties, "affectionScore")));
            int wordCount = Integer.parseInt(required(properties, "learnedWordCount"));
            if (wordCount < 0 || wordCount > properties.size() - 4) {
                throw new IllegalArgumentException("Invalid learned word count.");
            }
            for (int i = 0; i < wordCount; i++) {
                loadedProgress.addLearnedWord(required(properties, "learnedWord." + i));
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid progress file " + saveFile + ": " + e.getMessage(), e);
        }
        currentProgress = loadedProgress;
        return loadedProgress;
    }

    /** Exports learned words, one per line; this is separate from the four-column dictionary. */
    public void saveVocabulary() throws IOException {
        ArrayList<String> learnedWords = currentProgress.getLearnedWords();
        String content = String.join(System.lineSeparator(), learnedWords);
        if (!learnedWords.isEmpty()) {
            content += System.lineSeparator();
        }
        writeSaveFile("learned-vocabulary.txt", content);
    }

    public void handleFileError(Exception e) {
        Objects.requireNonNull(e, "Exception must not be null.");
        System.err.println("Unable to read or save game data: " + e.getMessage());
    }

    /** Accepts an existing file path or a name under resources/<folder>. */
    private BufferedReader openResource(String filename, String folder) throws IOException {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename must not be empty.");
        }
        Path directPath = Path.of(filename);
        if (Files.exists(directPath)) {
            return Files.newBufferedReader(directPath, StandardCharsets.UTF_8);
        }
        if (directPath.isAbsolute()) {
            throw new IOException("Resource file not found: " + filename);
        }

        String resourceName = filename.replace('\\', '/');
        if (!resourceName.startsWith("resources/")) {
            resourceName = "resources/" + folder + "/" + resourceName;
        }
        InputStream stream = FileManager.class.getResourceAsStream("/" + resourceName);
        if (stream != null) {
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        // Allows resource loading while developing from the project root.
        Path sourcePath = Path.of("src").resolve(resourceName);
        if (Files.exists(sourcePath)) {
            return Files.newBufferedReader(sourcePath, StandardCharsets.UTF_8);
        }
        throw new IOException("Resource file not found: " + filename
                + " (also checked " + resourceName + " and " + sourcePath + ").");
    }

    private boolean isVocabularyHeader(String[] fields) {
        return fields.length == 4
                && fields[0].strip().equalsIgnoreCase("word")
                && fields[1].strip().equalsIgnoreCase("meaning")
                && fields[2].strip().equalsIgnoreCase("dialect")
                && fields[3].strip().equalsIgnoreCase("difficulty");
    }

    private String removeBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }

    /** Writes a temporary sibling first to avoid truncating the previous save on write failure. */
    private void writeSaveFile(String filename, String content) throws IOException {
        Files.createDirectories(saveDirectory);
        Path temporaryFile = Files.createTempFile(saveDirectory, "save-", ".tmp");
        try {
            Files.writeString(temporaryFile, content, StandardCharsets.UTF_8);
            Path target = saveDirectory.resolve(filename);
            try {
                Files.move(temporaryFile, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }
}
