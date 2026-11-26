package story;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The authored story graph. Text and branching data live outside the Swing UI. */
public final class StoryScript {
    public record Scene(String id, String title, String setting, int stage) {}
    public record Option(String text, String effects) {}
    public record Word(String flag, String text, String meaning, String dialect, String review) {}
    public static final class Node {
        public final String id, scene, kind, speaker, text, condition, effects, next, hint, answers;
        public final List<Option> options = new ArrayList<>();
        Node(String[] fields) {
            id = fields[1]; scene = fields[2]; kind = fields[3]; speaker = fields[4];
            text = fields[5]; condition = fields[6]; effects = fields[7]; next = fields[8];
            hint = fields[9]; answers = fields[10];
        }
    }
    private final Map<String, Scene> scenes = new LinkedHashMap<>();
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, Word> words = new LinkedHashMap<>();

    public static StoryScript load() throws IOException {
        StoryScript script = new StoryScript();
        try (BufferedReader reader = open("story.tsv")) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t", -1);
                for (int i = 0; i < f.length; i++) f[i] = unescape(f[i]);
                try {
                    switch (f[0]) {
                        case "SCENE" -> {
                            if (f.length != 5 || script.scenes.putIfAbsent(f[1],
                                    new Scene(f[1], f[2], f[3], Integer.parseInt(f[4]))) != null)
                                throw new IllegalArgumentException("Invalid or duplicate scene");
                        }
                        case "NODE" -> {
                            if (f.length != 11 || script.nodes.putIfAbsent(f[1], new Node(f)) != null)
                                throw new IllegalArgumentException("Invalid or duplicate node");
                        }
                        case "OPTION" -> {
                            if (f.length != 4 || !script.nodes.containsKey(f[1]))
                                throw new IllegalArgumentException("Option without a node");
                            script.nodes.get(f[1]).options.add(new Option(f[2], f[3]));
                        }
                        case "WORD" -> {
                            if (f.length != 6) throw new IllegalArgumentException("Invalid word");
                            if (script.words.putIfAbsent(f[1], new Word(f[1], f[2], f[3], f[4], f[5])) != null)
                                throw new IllegalArgumentException("Duplicate word flag");
                        }
                        default -> throw new IllegalArgumentException("Unknown record type");
                    }
                } catch (RuntimeException error) {
                    throw new IOException("Invalid story data at line " + number, error);
                }
            }
        }
        script.validate();
        return script;
    }

    private void validate() throws IOException {
        if (!nodes.containsKey("M01.0")) throw new IOException("Missing prologue");
        for (Scene scene : scenes.values()) {
            if (scene.stage() < 1 || scene.stage() > 4 || !nodes.containsKey(scene.id() + ".0"))
                throw new IOException("Invalid scene: " + scene.id());
        }
        for (Node node : nodes.values()) {
            if (!scenes.containsKey(node.scene)) throw new IOException("Unknown scene: " + node.scene);
            if (!List.of("LINE", "CHOICE", "QUIZ", "TYPED", "INPUT", "AUTO", "END").contains(node.kind))
                throw new IOException("Unknown node kind: " + node.kind);
            if (!node.next.isEmpty() && !node.next.startsWith("@") && !nodes.containsKey(node.next))
                throw new IOException("Missing next node: " + node.next);
            if ((node.kind.equals("CHOICE") || node.kind.equals("QUIZ"))
                    && (node.options.size() < 2 || node.options.size() > 3))
                throw new IOException("Expected two or three choices: " + node.id);
            if (node.kind.equals("TYPED") && node.answers.isBlank())
                throw new IOException("Missing accepted answer: " + node.id);
            if (node.kind.equals("QUIZ")) {
                try {
                    int answer = Integer.parseInt(node.answers);
                    if (answer < 0 || answer >= node.options.size()) throw new NumberFormatException();
                } catch (NumberFormatException error) { throw new IOException("Invalid quiz answer: " + node.id, error); }
            }
            if (node.next.isEmpty() && !node.kind.equals("END"))
                throw new IOException("Missing continuation: " + node.id);
            if (node.next.startsWith("@") && !List.of("@route", "@ending").contains(node.next))
                throw new IOException("Unknown story transition: " + node.next);
            if (node.kind.equals("INPUT") && !List.of("firstName", "lastName", "nickname", "address").contains(node.answers))
                throw new IOException("Unknown player input: " + node.id);
            validateEffects(node.effects, node.id);
            for (Option option : node.options) validateEffects(option.effects(), node.id);
        }
        for (String route : List.of("J", "I", "N"))
            for (String ending : List.of("R", "F", "N"))
                if (!nodes.containsKey(route + "-" + ending + ".0"))
                    throw new IOException("Missing ending " + route + "-" + ending);
    }

    private void validateEffects(String effects, String id) throws IOException {
        for (String effect : effects.split(";")) {
            if (effect.isBlank()) continue;
            String[] command = effect.split(":", 2);
            if (command.length != 2 || !List.of("set", "add", "aff", "variant", "library", "learn", "hear", "partner", "address").contains(command[0]))
                throw new IOException("Invalid effect at " + id + ": " + effect);
            if (List.of("learn", "hear").contains(command[0]) && !words.containsKey(command[1]))
                throw new IOException("Unknown vocabulary flag at " + id + ": " + command[1]);
        }
    }

    public Node node(String id) {
        Node result = nodes.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown story position: " + id);
        return result;
    }
    public Scene scene(String id) { return scenes.get(id); }
    public Word word(String flag) { return words.get(flag); }
    public List<Word> words() { return List.copyOf(words.values()); }
    public List<Node> nodes() { return List.copyOf(nodes.values()); }
    public List<Scene> scenes() { return List.copyOf(scenes.values()); }

    private static BufferedReader open(String name) throws IOException {
        InputStream stream = StoryScript.class.getResourceAsStream("/resources/story/" + name);
        if (stream != null) return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        return Files.newBufferedReader(Path.of("src", "resources", "story", name), StandardCharsets.UTF_8);
    }
    private static String unescape(String value) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                text.append(next == 'n' ? '\n' : next == 't' ? '\t' : next);
            } else text.append(c);
        }
        return text.toString();
    }
}
