package story;

import data.GameProgress;
import mechanics.AffectionManager;
import mechanics.AnswerValidator;
import mechanics.EndingManager;
import mechanics.PartnerManager;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Runs authored scenes, including mixed input modes and exact save/resume positions. */
public final class StoryManager {
    public static final String VERSION = "1";
    private final StoryScript script;
    private final GameProgress progress;
    private final AffectionManager affection;
    private final AnswerValidator validator = new AnswerValidator();

    public StoryManager(StoryScript script, GameProgress progress) {
        this.script = script;
        this.progress = progress;
        affection = new AffectionManager(progress);
    }
    public StoryScript script() { return script; }
    public boolean started() { return !value("node").isEmpty(); }
    public StoryScript.Node current() { return script.node(value("node")); }
    public StoryScript.Scene scene() { return script.scene(current().scene); }
    public boolean completed() { return started() && current().kind.equals("END"); }
    public String value(String key) { return progress.getStoryValue(key); }
    public boolean isLearnedWord(String text) { return progress.getLearnedWords().contains(text); }
    public boolean knowsWord(String text) {
        return isLearnedWord(text) || script.words().stream()
                .anyMatch(word -> word.text().equals(text) && condition("heard." + word.flag()));
    }
    public long knownWordCount() { return script.words().stream().map(StoryScript.Word::text).distinct().filter(this::knowsWord).count(); }
    private void put(String key, String value) { progress.setStoryValue(key, value); }
    private int number(String key) {
        String value = value(key);
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }
    public void start() {
        progress.resetProgress();
        put("version", VERSION);
        move("M01.0");
    }
    /** Validate before replacing the active playthrough. Legacy saves remain untouched. */
    public void load(GameProgress saved) {
        if (!VERSION.equals(saved.getStoryValue("version")) || saved.getStoryValue("node").isEmpty())
            throw new IllegalArgumentException("This save predates the storyline. Choose New Game to begin the story.");
        StoryScript.Node savedNode = script.node(saved.getStoryValue("node"));
        String partner = saved.getSelectedPartner();
        if (!partner.isEmpty() && !List.of("Jessa", "Inna", "Nikka").contains(partner))
            throw new IllegalArgumentException("This save uses an unknown story partner.");
        for (String key : saved.getStoryState().keySet()) {
            if (key.startsWith("attempt.") || key.startsWith("rapport_") || key.startsWith("count.")) {
                String text = saved.getStoryValue(key);
                if (!text.isEmpty() && (!text.matches("[0-9]+") || text.length() > 6))
                    throw new IllegalArgumentException("Invalid saved story counter.");
            }
        }
        if (!saved.getStoryValue("returnNode").isEmpty()) script.node(saved.getStoryValue("returnNode"));
        String practiceScene = saved.getStoryValue("practiceScene");
        if (practiceScene.isEmpty() != saved.getStoryValue("returnNode").isEmpty()
                || (!practiceScene.isEmpty() && !savedNode.scene.equals(practiceScene)))
            throw new IllegalArgumentException("This save has an incomplete study session.");
        StoryScript.Node mainNode = practiceScene.isEmpty() ? savedNode : script.node(saved.getStoryValue("returnNode"));
        if (script.scene(mainNode.scene).stage() != saved.getCurrentStage())
            throw new IllegalArgumentException("The saved chapter does not match the story position.");
        if (mainNode.scene.matches("[JIN].*") && (partner.isEmpty()
                || mainNode.scene.charAt(0) != partner.charAt(0)))
            throw new IllegalArgumentException("The saved route does not match the chosen partner.");
        progress.copyFrom(saved);
        move(value("node"));
    }
    public boolean answered() { return "true".equals(value("done." + current().id)); }
    public String feedback() { return value("feedback." + current().id); }
    public boolean acceptsText() { return !answered() && List.of("TYPED", "INPUT").contains(current().kind); }

    public void choose(int choice) {
        StoryScript.Node node = current();
        if (answered() || !List.of("CHOICE", "QUIZ").contains(node.kind)) return;
        if (choice < 0 || choice >= node.options.size()) return;
        StoryScript.Option option = node.options.get(choice);
        put("pick_" + node.scene, Character.toString((char) ('A' + choice)));
        put("selection." + node.id, Integer.toString(choice));
        boolean correct = !node.kind.equals("QUIZ") || choice == Integer.parseInt(node.answers);
        put("ok_" + node.id, Boolean.toString(correct));
        applyOnce(node.id + ".option" + choice, option.effects());
        if (node.kind.equals("QUIZ") && correct) applyOnce(node.id + ".answer", node.effects);
        put("feedback." + node.id, node.kind.equals("QUIZ")
                ? (correct ? "Correct!" : "Answer: " + node.options.get(Integer.parseInt(node.answers)).text())
                : "You chose: " + format(option.text()));
        put("done." + node.id, "true");
    }

    public void submit(String text) {
        if (!acceptsText()) return;
        StoryScript.Node node = current();
        String reply = text == null ? "" : text.strip();
        if (reply.isBlank()) {
            put("feedback." + node.id, "Please enter a response first.");
            return;
        }
        if (node.kind.equals("INPUT")) {
            if (reply.length() > 100) {
                put("feedback." + node.id, "Please use 100 characters or fewer.");
                return;
            }
            put(node.answers, reply.replaceAll("\\s+", " "));
            put("done." + node.id, "true");
            put("feedback." + node.id, "Recorded: " + reply);
            return;
        }
        List<String> accepted = Arrays.stream(node.answers.split("\\|"))
                .map(StoryManager::normalizeTyped).toList();
        validator.setAcceptedAnswers(accepted);
        boolean correct = validator.checkTypedResponse(normalizeTyped(reply));
        int attempts = number("attempt." + node.id) + 1;
        put("attempt." + node.id, Integer.toString(attempts));
        put("reply." + node.id, reply);
        if (correct) {
            put("ok_" + node.id, "true");
            applyOnce(node.id + ".answer", node.effects);
            put("done." + node.id, "true");
            put("feedback." + node.id, "Correct! " + node.answers.split("\\|")[0]);
        } else if (attempts < 2) {
            put("feedback." + node.id, "Try once more. " + hint());
        } else revealAnswer();
    }

    public String hint() {
        return current().hint.isBlank() ? "Use the phrase from the conversation." : format(current().hint);
    }
    public void revealAnswer() {
        if (!current().kind.equals("TYPED") || answered()) return;
        put("ok_" + current().id, "false");
        put("done." + current().id, "true");
        put("feedback." + current().id, "Model answer: " + current().answers.split("\\|")[0]
                + ". No affection points were deducted.");
    }
    public void advance() {
        StoryScript.Node node = current();
        if (node.kind.equals("END")) return;
        if (!node.kind.equals("LINE") && !answered()) return;
        if (node.scene.equals("S1-04") && node.kind.equals("CHOICE") && value("pick_S1-04").equals("B")) {
            clearResponses("S1-04");
            move("S1-04.0");
        } else move(node.next);
    }
    public void startPractice(boolean tutor) {
        if (!started() || !value("returnNode").isEmpty())
            throw new IllegalStateException("Finish the current study session first.");
        String target = "S1-04";
        if (!progress.getSelectedPartner().isEmpty()) {
            target = route() + (tutor ? "2-L" : progress.getCurrentStage() >= 3 ? "3-S" : "2-03");
        } else if (tutor) throw new IllegalStateException("Choose your project partner in Chapter 2 first.");
        if (target.equals(current().scene)) throw new IllegalStateException("You are already in this session.");
        put("returnNode", current().id);
        put("practiceScene", target);
        // Preserve the main story's choices and responses while replaying a lesson.
        // These values are serialized too, so saving during practice remains resumable.
        for (StoryScript.Node node : script.nodes()) if (node.scene.equals(target)) {
            for (String prefix : List.of("done.", "feedback.", "attempt.", "reply.", "selection.", "ok_"))
                put("practice.restore." + prefix + node.id, value(prefix + node.id));
        }
        put("practice.restore.pick_" + target, value("pick_" + target));
        put("practice.restore.skip_" + target, value("skip_" + target));
        clearResponses(target);
        move(target + ".0");
    }
    private void clearResponses(String scene) {
        for (StoryScript.Node node : script.nodes()) if (node.scene.equals(scene)) {
            for (String prefix : List.of("done.", "feedback.", "attempt.", "reply.", "selection.", "ok_"))
                put(prefix + node.id, "");
        }
        put("pick_" + scene, "");
        put("skip_" + scene, "false");
    }
    private String route() {
        return switch (progress.getSelectedPartner()) {
            case "Jessa" -> "J";
            case "Inna" -> "I";
            case "Nikka" -> "N";
            default -> throw new IllegalStateException("Choose a story partner first.");
        };
    }
    private String resolve(String next) {
        if (next.equals("@route")) return route() + "2-01.0";
        if (next.equals("@ending")) {
            String boundary = switch (route()) {
                case "J" -> "jessa_boundary_respected";
                case "I" -> "inna_boundary_respected";
                default -> "nikka_research_integrity";
            };
            String result = new EndingManager(progress).determineStoryEnding(value("confessionChoice"),
                    condition("keySceneResolved"), condition(boundary));
            return route() + "-" + (result.equals(EndingManager.ROMANTIC) ? "R"
                    : result.equals(EndingManager.CLASSMATES_ONLY) ? "F" : "N") + ".0";
        }
        return next;
    }
    private void move(String target) {
        String previousScene = started() ? current().scene : "";
        for (int guard = 0; guard < script.nodes().size() + 5; guard++) {
            target = resolve(target);
            StoryScript.Node node = script.node(target);
            if (previousScene.equals(value("practiceScene")) && !node.scene.equals(previousScene)
                    && !value("returnNode").isEmpty()) {
                target = value("returnNode");
                for (var entry : progress.getStoryState().entrySet()) {
                    if (entry.getKey().startsWith("practice.restore.")) {
                        put(entry.getKey().substring("practice.restore.".length()), entry.getValue());
                        progress.removeStoryValue(entry.getKey());
                    }
                }
                put("returnNode", ""); put("practiceScene", "");
                node = script.node(target);
            }
            put("node", target);
            if (value("practiceScene").isEmpty()) progress.setCurrentStage(script.scene(node.scene).stage());
            put("visited_" + node.scene, "true");
            previousScene = node.scene;
            if (!condition(node.condition)) { target = node.next; continue; }
            if (!List.of("TYPED", "QUIZ", "CHOICE", "INPUT").contains(node.kind))
                applyOnce(node.id, node.effects);
            if (!node.kind.equals("AUTO")) return;
            target = node.next;
        }
        throw new IllegalStateException("The story contains an automatic loop.");
    }
    public boolean condition(String expression) {
        if (expression.isBlank()) return true;
        for (String alternative : expression.split("\\|")) {
            boolean matches = true;
            for (String term : alternative.split("&")) {
                boolean negate = term.startsWith("!");
                if (negate) term = term.substring(1);
                String[] parts = term.split("=", 2);
                String actual = value(parts[0]);
                boolean result = parts.length == 2 ? actual.equals(parts[1]) : actual.equals("true");
                matches &= negate ? !result : result;
            }
            if (matches) return true;
        }
        return false;
    }
    private void applyOnce(String id, String effects) {
        if (effects.isBlank()) return;
        boolean firstTime = !value("applied." + id).equals("true");
        for (String effect : effects.split(";")) {
            if (effect.isBlank()) continue;
            String[] command = effect.split(":", 2);
            String argument = command.length > 1 ? command[1] : "";
            // Control flags must reflect this answer even when a lesson is replayed.
            // Scores and rapport still apply only on the first completion.
            if (!firstTime && List.of("add", "aff", "variant", "library", "partner").contains(command[0])) continue;
            switch (command[0]) {
                case "set" -> { String[] pair = argument.split("=", 2); put(pair[0], pair[1]); }
                case "add" -> { String[] pair = argument.split("=", 2); put(pair[0], Integer.toString(number(pair[0]) + Integer.parseInt(pair[1]))); }
                case "aff" -> affection.adjustAffection(Integer.parseInt(argument));
                case "variant", "library" -> {
                    String counter = "count." + command[0];
                    int cap = command[0].equals("variant") ? 3 : 2;
                    int amount = Math.min(Integer.parseInt(argument), Math.max(0, cap - number(counter)));
                    affection.adjustAffection(amount); put(counter, Integer.toString(number(counter) + amount));
                }
                case "learn" -> {
                    put(argument, "true");
                    StoryScript.Word word = script.word(argument);
                    if (word != null) progress.addLearnedWord(word.text());
                }
                case "hear" -> put("heard." + argument, "true");
                case "partner" -> {
                    new PartnerManager(progress, Map.of("Jessa", "Cebuano", "Inna", "Ilokano", "Nikka", "Central Bikol"))
                            .selectPartner(argument);
                    progress.setAffectionScore(Math.min(2, Math.max(0, number("rapport_" + route()))));
                }
                case "address" -> put("preferred_address", value(argument));
                default -> throw new IllegalArgumentException("Unknown story effect: " + effect);
            }
        }
        put("applied." + id, "true");
    }
    public String format(String text) {
        String name = value("preferred_address");
        if (name.isBlank()) name = value("nickname");
        if (name.isBlank()) name = "you";
        return text.replace("[FIRST NAME]", value("firstName")).replace("[LAST NAME]", value("lastName"))
                .replace("[NICKNAME]", value("nickname")).replace("[ADDRESS]", value("address"))
                .replace("[PLAYER NAME]", name).replace("[PLAYER]", name)
                .replace("[LOVE INTEREST'S NAME]", progress.getSelectedPartner());
    }
    public static String normalizeTyped(String text) {
        return text.replace('\u2019', '\'').replace('\u2018', '\'').strip()
                .replaceAll("[.!?]+$", "").strip();
    }
}
