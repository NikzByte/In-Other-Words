package mechanics;

import data.GameProgress;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Controls four stages using the same GameProgress as DialogueManager.
 * Start/load a stage, configure its questions, finish dialogue, complete it,
 * unlock the next stage, then start that stage. The UI calls these operations.
 * Existing saves resume at the start of their current stage; they do not store dialogue position.
 * Create a new set of managers after loading a save or resetting GameProgress for a new game.
 */
public class StageManager {

    private final GameProgress progress;
    private final DialogueManager dialogue;
    private final Map<Integer, String> stageCharacters = new HashMap<>();
    private final Set<String> loadedConversations = new HashSet<>();
    private int highestUnlockedStage;
    private int loadedStage;
    private String loadedCharacter = "";
    private boolean stageComplete;

    public StageManager(GameProgress progress, DialogueManager dialogue) {
        this.progress = Objects.requireNonNull(progress, "Progress must not be null.");
        this.dialogue = Objects.requireNonNull(dialogue, "Dialogue manager must not be null.");
        if (dialogue.getProgress() != progress) {
            throw new IllegalArgumentException("Stage and dialogue must share the same GameProgress.");
        }
        highestUnlockedStage = progress.getCurrentStage();
    }

    /** File loading succeeds before the shared stage number is changed. */
    public void startStage(int stageNumber) throws IOException {
        requireStage(stageNumber);
        if (stageNumber > highestUnlockedStage || stageNumber < getCurrentStage()) {
            throw new IllegalStateException("That stage is locked or has already been passed.");
        }
        if (loadedStage == stageNumber) {
            throw new IllegalStateException("This stage is already loaded; use resetStage() to rewind it.");
        }
        requirePartner(stageNumber);
        String character = characterFor(stageNumber);
        dialogue.loadDialogue(character, stageNumber);
        progress.setCurrentStage(stageNumber);
        loadedStage = stageNumber;
        loadedCharacter = character;
        loadedConversations.add(conversationKey(character, stageNumber));
        stageComplete = false;
    }

    public void completeStage() {
        requireLoadedStage();
        if (!dialogue.isDialogueComplete()) {
            throw new IllegalStateException("Finish the current dialogue before completing the stage.");
        }
        if (getCurrentStage() >= 2 && progress.getSelectedPartner().isEmpty()) {
            throw new IllegalStateException("Select a partner before completing Stage 2 or later.");
        }
        stageComplete = true;
    }

    public boolean isStageComplete() {
        return loadedStage == getCurrentStage() && stageComplete && dialogue.isDialogueComplete();
    }

    public int getCurrentStage() {
        return progress.getCurrentStage();
    }

    /** Returns false after Stage 4 because there is no fifth stage. */
    public boolean unlockNextStage() {
        if (!isStageComplete()) {
            throw new IllegalStateException("Complete the current stage before unlocking the next one.");
        }
        if (getCurrentStage() == 4) {
            return false;
        }
        highestUnlockedStage = Math.max(highestUnlockedStage, getCurrentStage() + 1);
        return true;
    }

    /**
     * Loads the current stage, including when resuming a save. After selecting a partner
     * in Stage 2, this can load that partner's conversation when the common dialogue ends.
     */
    public void loadStageDialogue() throws IOException {
        int stage = getCurrentStage();
        if (loadedStage != stage) {
            startStage(stage);
            return;
        }
        String character = characterFor(stage);
        String key = conversationKey(character, stage);
        if (loadedConversations.contains(key) || loadedCharacter.equalsIgnoreCase(character)) {
            throw new IllegalStateException("This conversation is already loaded; use resetStage() to rewind it.");
        }
        if (!dialogue.isDialogueComplete()) {
            throw new IllegalStateException("Finish the current conversation before loading the partner dialogue.");
        }
        requirePartner(stage);
        dialogue.loadDialogue(character, stage);
        loadedCharacter = character;
        loadedConversations.add(key);
        stageComplete = false;
    }

    /** Optional story mapping, for example the shared introduction before choosing a partner. */
    public void setStageDialogueCharacter(int stage, String character) {
        requireStage(stage);
        if (character == null || character.isBlank()) {
            throw new IllegalArgumentException("Dialogue character must not be blank.");
        }
        if (stage < getCurrentStage() || stage == loadedStage) {
            throw new IllegalStateException("Configure the stage character before loading its dialogue.");
        }
        stageCharacters.put(stage, character.strip());
    }

    /** Stages 1-2 use multiple-choice; Stages 3-4 use typed answers. */
    public void setInputMode(String mode) {
        String requiredMode = getCurrentStage() <= 2 ? DialogueManager.MULTIPLE_CHOICE : DialogueManager.TYPED;
        if (!requiredMode.equals(mode)) {
            throw new IllegalArgumentException("This stage requires " + requiredMode + " input.");
        }
        dialogue.setInputMode(mode);
    }

    public String getInputMode() {
        return getCurrentStage() <= 2 ? DialogueManager.MULTIPLE_CHOICE : DialogueManager.TYPED;
    }

    /** Rewinds the loaded stage while preserving scores and already submitted answers. */
    public void resetStage() {
        requireLoadedStage();
        dialogue.resetDialogue();
        stageComplete = false;
        highestUnlockedStage = getCurrentStage();
    }

    private String characterFor(int stage) {
        if (stageCharacters.containsKey(stage)) {
            return stageCharacters.get(stage);
        }
        return stage >= 2 && !progress.getSelectedPartner().isEmpty()
                ? progress.getSelectedPartner() : "common";
    }

    private String conversationKey(String character, int stage) {
        return character.strip().toLowerCase(Locale.ROOT) + "\n" + stage;
    }

    private void requirePartner(int stage) {
        if (stage >= 3 && progress.getSelectedPartner().isEmpty()) {
            throw new IllegalStateException("Stages 3 and 4 require a selected partner.");
        }
    }

    private void requireLoadedStage() {
        if (loadedStage != getCurrentStage()) {
            throw new IllegalStateException("Load the current stage's dialogue first.");
        }
    }

    private void requireStage(int stage) {
        if (stage < 1 || stage > 4) {
            throw new IllegalArgumentException("Stage must be between 1 and 4.");
        }
    }
}
