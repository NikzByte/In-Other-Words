package mechanics;

import data.FileManager;
import data.GameProgress;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Controls scripted dialogue without drawing UI or depending on unfinished dialogue classes.
 * Load text, then configure choices/accepted answers from the story's dialogue data.
 * Submit an answer once, read its feedback, then call nextDialogue() to advance.
 * All dialogue and choice indexes are zero-based; -1 is an explicit conversation end.
 */
public class DialogueManager {

    public static final String MULTIPLE_CHOICE = "multiple-choice";
    public static final String TYPED = "typed";

    private final GameProgress progress;
    private final FileManager files;
    private final AnswerValidator validator;
    private final AffectionManager affection;
    private final ArrayList<LineState> lines = new ArrayList<>();
    private final Map<String, String> dialogueFiles = new HashMap<>();
    private int currentIndex;
    private boolean loaded;
    private String inputMode = MULTIPLE_CHOICE;

    public DialogueManager(GameProgress progress, FileManager files) {
        this(progress, files, new AnswerValidator(), new AffectionManager(progress));
    }

    public DialogueManager(GameProgress progress, FileManager files,
            AnswerValidator validator, AffectionManager affection) {
        this.progress = Objects.requireNonNull(progress, "Progress must not be null.");
        this.files = Objects.requireNonNull(files, "File manager must not be null.");
        this.validator = Objects.requireNonNull(validator, "Validator must not be null.");
        this.affection = Objects.requireNonNull(affection, "Affection manager must not be null.");
        if (affection.getProgress() != progress) {
            throw new IllegalArgumentException("Dialogue and affection must share the same GameProgress.");
        }
    }

    /** Returns text for the UI and records vocabulary explicitly taught on this line. */
    public String displayDialogue() {
        if (isDialogueComplete()) {
            return "";
        }
        LineState line = currentLine();
        for (String word : line.learnedWords) {
            progress.addLearnedWord(word);
        }
        return line.text;
    }

    /** Peeks without advancing. Before a choice is submitted, this is the sequential next line. */
    public String getNextDialogue() {
        if (isDialogueComplete()) {
            return "";
        }
        LineState line = currentLine();
        int nextIndex = nextIndex(line);
        return nextIndex >= lines.size() ? "" : lines.get(nextIndex).text;
    }

    public void nextDialogue() {
        if (isDialogueComplete()) {
            return;
        }
        LineState line = currentLine();
        if (line.hasQuestion() && line.correct == null) {
            throw new IllegalStateException("Submit an answer before advancing this dialogue.");
        }
        currentIndex = nextIndex(line);
    }

    public ArrayList<String> getChoices() {
        return isDialogueComplete() ? new ArrayList<>() : new ArrayList<>(currentLine().choices);
    }

    /** Scores this question once. Invalid indexes and repeat submissions do not change affection. */
    public boolean processChoice(int choice) {
        LineState line = unansweredLine();
        if (!MULTIPLE_CHOICE.equals(inputMode) || line.choices.isEmpty()) {
            throw new IllegalStateException("The current dialogue does not accept multiple-choice input.");
        }
        if (choice < 0 || choice >= line.choices.size()) {
            throw new IllegalArgumentException("Choice index is outside the available choices.");
        }
        validator.setMultipleChoice(line.choices, line.correctChoice);
        boolean correct = validator.checkMultipleChoice(choice);
        if (line.choicePoints.containsKey(choice)) {
            affection.adjustAffection(line.choicePoints.get(choice));
        } else {
            affection.updateAffection(correct);
        }
        line.correct = correct;
        line.selectedChoice = choice;
        return correct;
    }

    /** Typed answers use the accepted variations supplied by the story author. */
    public boolean processTypedResponse(String answer) {
        LineState line = unansweredLine();
        if (!TYPED.equals(inputMode) || line.acceptedAnswers.isEmpty()) {
            throw new IllegalStateException("The current dialogue does not accept typed input.");
        }
        validator.setAcceptedAnswers(line.acceptedAnswers);
        boolean correct = validator.checkTypedResponse(answer);
        affection.updateAffection(correct);
        line.correct = correct;
        return correct;
    }

    public String getFeedback() {
        LineState line = currentLine();
        return line.correct == null ? "" : validator.getFeedback(line.correct);
    }

    public boolean isDialogueComplete() {
        return loaded && currentIndex >= lines.size();
    }

    /** Rewinds the current conversation but preserves answered flags to avoid duplicate scoring. */
    public void resetDialogue() {
        if (!loaded) {
            throw new IllegalStateException("Load a conversation before resetting it.");
        }
        currentIndex = 0;
    }

    /**
     * Uses an explicitly registered file, or resources/dialogues/<character>/stageN.txt.
     * Loading a new conversation clears its old question configuration and attempts.
     */
    public void loadDialogue(String character, int stage) throws IOException {
        String key = conversationKey(character, stage);
        String filename = dialogueFiles.getOrDefault(key, character.strip() + "/stage" + stage + ".txt");
        loadDialogue(files.loadDialogue(filename));
        setInputMode(stage <= 2 ? MULTIPLE_CHOICE : TYPED);
    }

    /** Accepts text supplied by a future adapter for Loraine's dialogue classes. */
    public void loadDialogue(List<String> dialogueLines) {
        if (dialogueLines == null || dialogueLines.isEmpty()) {
            throw new IllegalArgumentException("A conversation must have at least one line.");
        }
        ArrayList<LineState> validated = new ArrayList<>();
        for (String text : dialogueLines) {
            validated.add(new LineState(Objects.requireNonNull(text, "Dialogue lines must not be null.")));
        }
        lines.clear();
        lines.addAll(validated);
        currentIndex = 0;
        loaded = true;
    }

    public void registerDialogueFile(String character, int stage, String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Dialogue filename must not be blank.");
        }
        dialogueFiles.put(conversationKey(character, stage), filename);
    }

    public void setChoices(int dialogueIndex, Collection<String> choices, int correctChoice) {
        LineState line = editableLine(dialogueIndex);
        // Validate the entire configuration before replacing existing state.
        new AnswerValidator().setMultipleChoice(choices, correctChoice);
        line.choices.clear();
        line.choices.addAll(choices);
        line.correctChoice = correctChoice;
        line.acceptedAnswers.clear();
        line.choiceNextLines.clear();
        line.choicePoints.clear();
    }

    public void setAcceptedAnswers(int dialogueIndex, Collection<String> answers) {
        LineState line = editableLine(dialogueIndex);
        new AnswerValidator(answers);
        line.acceptedAnswers.clear();
        line.acceptedAnswers.addAll(answers);
        line.choices.clear();
        line.choiceNextLines.clear();
        line.choicePoints.clear();
        line.correctChoice = -1;
    }

    /** Allows forward branches or -1 to end the conversation, without replaying scored questions. */
    public void setChoiceNextDialogue(int dialogueIndex, int choice, int nextDialogueIndex) {
        LineState line = editableChoice(dialogueIndex, choice);
        if (nextDialogueIndex != -1 && (nextDialogueIndex <= dialogueIndex || nextDialogueIndex >= lines.size())) {
            throw new IllegalArgumentException("A branch must target a later dialogue index, or -1 to end.");
        }
        line.choiceNextLines.put(choice, nextDialogueIndex);
    }

    /** Overrides default answer scoring for this choice, using a signed point value. */
    public void setChoiceAffectionPoints(int dialogueIndex, int choice, int points) {
        editableChoice(dialogueIndex, choice).choicePoints.put(choice, points);
    }

    public void setLearnedWords(int dialogueIndex, Collection<String> words) {
        LineState line = editableLine(dialogueIndex);
        if (words == null) {
            throw new IllegalArgumentException("Learned words must not be null.");
        }
        GameProgress validation = new GameProgress();
        for (String word : words) {
            validation.addLearnedWord(word);
        }
        line.learnedWords.clear();
        line.learnedWords.addAll(validation.getLearnedWords());
    }

    public int getCurrentDialogueIndex() {
        return currentIndex;
    }

    public int getDialogueCount() {
        return lines.size();
    }

    public String getInputMode() {
        return inputMode;
    }

    public void setInputMode(String mode) {
        if (!MULTIPLE_CHOICE.equals(mode) && !TYPED.equals(mode)) {
            throw new IllegalArgumentException("Input mode must be multiple-choice or typed.");
        }
        inputMode = mode;
    }

    GameProgress getProgress() {
        return progress;
    }

    private LineState currentLine() {
        if (!loaded || isDialogueComplete()) {
            throw new IllegalStateException("There is no active dialogue line.");
        }
        return lines.get(currentIndex);
    }

    private LineState unansweredLine() {
        LineState line = currentLine();
        if (line.correct != null) {
            throw new IllegalStateException("This question has already been answered.");
        }
        return line;
    }

    private LineState editableLine(int index) {
        if (!loaded || index < 0 || index >= lines.size()) {
            throw new IllegalArgumentException("Dialogue index is outside the loaded conversation.");
        }
        LineState line = lines.get(index);
        if (index < currentIndex || line.correct != null) {
            throw new IllegalStateException("Cannot change an already visited question.");
        }
        return line;
    }

    private LineState editableChoice(int index, int choice) {
        LineState line = editableLine(index);
        if (choice < 0 || choice >= line.choices.size()) {
            throw new IllegalArgumentException("Configure the choices before their points or branches.");
        }
        return line;
    }

    private int nextIndex(LineState line) {
        int next = line.choiceNextLines.getOrDefault(line.selectedChoice, currentIndex + 1);
        return next == -1 ? lines.size() : next;
    }

    private String conversationKey(String character, int stage) {
        if (character == null || character.isBlank() || stage < 1 || stage > 4) {
            throw new IllegalArgumentException("Provide a character and a stage from 1 to 4.");
        }
        return character.strip().toLowerCase(Locale.ROOT) + "\n" + stage;
    }

    /** Private runtime state, not a replacement for the team's public dialogue model. */
    private static class LineState {
        final String text;
        final ArrayList<String> choices = new ArrayList<>();
        final ArrayList<String> acceptedAnswers = new ArrayList<>();
        final ArrayList<String> learnedWords = new ArrayList<>();
        final Map<Integer, Integer> choiceNextLines = new HashMap<>();
        final Map<Integer, Integer> choicePoints = new HashMap<>();
        int correctChoice = -1;
        int selectedChoice = -1;
        Boolean correct;

        LineState(String text) {
            this.text = text;
        }

        boolean hasQuestion() {
            return !choices.isEmpty() || !acceptedAnswers.isEmpty();
        }
    }
}
