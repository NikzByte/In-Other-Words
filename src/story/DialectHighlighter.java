package story;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Highlights only the specific regional dialect words/phrases inside dialogue text
 * using each Love Interest's signature color:
 * - Jessa (Cebuano): Red
 * - Inna (Ilokano): Blue
 * - Nikka (Central Bikol): Yellow
 */
final class DialectHighlighter {

    enum DialectStyle {
        JESSA_RED(new Color(255, 105, 105, 85), new Color(175, 22, 22)),
        INNA_BLUE(new Color(100, 165, 255, 85), new Color(18, 78, 168)),
        NIKKA_YELLOW(new Color(255, 220, 50, 165), new Color(115, 78, 0));

        final Color background;
        final Color foreground;

        DialectStyle(Color background, Color foreground) {
            this.background = background;
            this.foreground = foreground;
        }
    }

    private record Entry(String phrase, DialectStyle style, boolean caseSensitive) {}
    private record Span(int start, int end, DialectStyle style) {}
    record StyledLine(String text, int sourceStart) {}

    private final List<Entry> entries;

    DialectHighlighter(StoryScript script) {
        Map<String, Entry> map = new LinkedHashMap<>();
        if (script != null) {
            for (StoryScript.Word word : script.words()) {
                DialectStyle style = styleForDialect(word.dialect());
                if (style == null) continue;
                registerWordVariants(map, word.text(), style);
            }
        }
        // Additional inflected/short forms used directly in dialogue
        add(map, "Gimingaw ko kang Lola", DialectStyle.JESSA_RED, false);
        add(map, "Gimingaw ko", DialectStyle.JESSA_RED, false);
        add(map, "Gimingaw", DialectStyle.JESSA_RED, false);
        add(map, "Kaon", DialectStyle.JESSA_RED, false);
        add(map, "Lami", DialectStyle.JESSA_RED, false);
        add(map, "Gutom ko", DialectStyle.JESSA_RED, false);
        add(map, "Unsa man diay ni", DialectStyle.JESSA_RED, false);
        add(map, "Walay sapayan", DialectStyle.JESSA_RED, false);
        add(map, "Way sapayan", DialectStyle.JESSA_RED, false);
        add(map, "Hinay-hinay lang", DialectStyle.JESSA_RED, false);
        add(map, "Hinay", DialectStyle.JESSA_RED, false);

        add(map, "Agyamanak unay, Apong", DialectStyle.INNA_BLUE, false);
        add(map, "Agyamanak unay, apo", DialectStyle.INNA_BLUE, false);
        add(map, "Agyamanak, apo", DialectStyle.INNA_BLUE, false);
        add(map, "Ay, apay a kasta", DialectStyle.INNA_BLUE, false);
        add(map, "Apay a kasta", DialectStyle.INNA_BLUE, false);
        add(map, "Kayat ko", DialectStyle.INNA_BLUE, false);
        add(map, "Bay-am lattan", DialectStyle.INNA_BLUE, false);

        add(map, "Taano man", DialectStyle.NIKKA_YELLOW, false);
        add(map, "Taga-sain", DialectStyle.NIKKA_YELLOW, false);
        add(map, "Tabi po", DialectStyle.NIKKA_YELLOW, false);
        add(map, "Dios mabalos po", DialectStyle.NIKKA_YELLOW, false);

        List<Entry> sorted = new ArrayList<>(map.values());
        sorted.sort(Comparator.comparingInt((Entry e) -> e.phrase.length()).reversed());
        this.entries = List.copyOf(sorted);
    }

    private static DialectStyle styleForDialect(String dialect) {
        return switch (dialect) {
            case "Cebuano" -> DialectStyle.JESSA_RED;
            case "Ilokano" -> DialectStyle.INNA_BLUE;
            case "Central Bikol" -> DialectStyle.NIKKA_YELLOW;
            default -> null;
        };
    }

    private static void registerWordVariants(Map<String, Entry> map, String raw, DialectStyle style) {
        for (String slashPart : raw.split("/")) {
            String cleaned = stripTrailingPunctuation(slashPart.trim());
            if (cleaned.isEmpty()) continue;
            // "Ate" collides with English past-tense "ate" ("I just ate"), so keep it case-sensitive.
            boolean caseSensitive = cleaned.equals("Ate");
            add(map, cleaned, style, caseSensitive);

            if (cleaned.contains(",")) {
                for (String commaPart : cleaned.split(",")) {
                    String item = stripTrailingPunctuation(commaPart.trim());
                    if (!item.isEmpty()) add(map, item, style, item.equals("Ate"));
                }
            }
        }
    }

    private static void add(Map<String, Entry> map, String phrase, DialectStyle style, boolean caseSensitive) {
        String key = (caseSensitive ? phrase : phrase.toLowerCase(Locale.ROOT)) + "|" + style.name();
        map.putIfAbsent(key, new Entry(phrase, style, caseSensitive));
    }

    private static String stripTrailingPunctuation(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '?' || s.charAt(end - 1) == '!' || s.charAt(end - 1) == '.')) {
            end--;
        }
        return s.substring(0, end).trim();
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-';
    }

    private List<Span> findSpans(String text) {
        List<Span> spans = new ArrayList<>();
        if (text == null || text.isEmpty()) return spans;
        String lower = text.toLowerCase(Locale.ROOT);
        boolean[] occupied = new boolean[text.length()];

        for (Entry entry : entries) {
            String needle = entry.caseSensitive ? entry.phrase : entry.phrase.toLowerCase(Locale.ROOT);
            String haystack = entry.caseSensitive ? text : lower;
            int len = needle.length();
            if (len == 0) continue;

            int idx = 0;
            while ((idx = haystack.indexOf(needle, idx)) != -1) {
                int end = idx + len;
                boolean leftOk = idx == 0 || !isWordChar(text.charAt(idx - 1));
                boolean rightOk = end == text.length() || !isWordChar(text.charAt(end));
                if (leftOk && rightOk && isFree(occupied, idx, end)) {
                    spans.add(new Span(idx, end, entry.style));
                    for (int k = idx; k < end; k++) occupied[k] = true;
                }
                idx = idx + 1;
            }
        }
        spans.sort(Comparator.comparingInt(Span::start));
        return spans;
    }

    private static boolean isFree(boolean[] occupied, int start, int end) {
        for (int i = start; i < end; i++) {
            if (occupied[i]) return false;
        }
        return true;
    }

    /**
     * Wraps {@code text} using the same width rules as {@link StoryView#wrap} while
     * preserving each line's starting index in {@code text} for highlight rendering.
     */
    List<StyledLine> wrapWithOffsets(String text, FontMetrics metrics, int width) {
        List<StyledLine> result = new ArrayList<>();
        int paraStart = 0;
        for (String paragraph : text.split("\n", -1)) {
            String line = "";
            int lineStart = paraStart;
            int pos = paraStart;
            int i = 0;
            while (i < paragraph.length()) {
                while (i < paragraph.length() && Character.isWhitespace(paragraph.charAt(i))) {
                    i++;
                }
                if (i >= paragraph.length()) break;
                int wordStart = i;
                while (i < paragraph.length() && !Character.isWhitespace(paragraph.charAt(i))) {
                    i++;
                }
                String word = paragraph.substring(wordStart, i);
                if (!line.isEmpty() && metrics.stringWidth(line + " " + word) > width) {
                    result.add(new StyledLine(line, lineStart));
                    line = "";
                }
                while (metrics.stringWidth(word) > width) {
                    int split = word.length() - 1;
                    while (split > 1 && metrics.stringWidth(word.substring(0, split)) > width) split--;
                    if (!line.isEmpty()) {
                        result.add(new StyledLine(line, lineStart));
                        line = "";
                    }
                    result.add(new StyledLine(word.substring(0, split), paraStart + wordStart));
                    wordStart += split;
                    word = word.substring(split);
                }
                if (line.isEmpty()) {
                    lineStart = paraStart + wordStart;
                    line = word;
                } else {
                    line += " " + word;
                }
                pos = paraStart + i;
            }
            if (line.isEmpty()) {
                result.add(new StyledLine("", pos));
            } else {
                result.add(new StyledLine(line, lineStart));
            }
            paraStart += paragraph.length() + 1;
        }
        return result;
    }

    void drawLines(Graphics2D g, String fullText, List<StyledLine> lines,
                   int x, int y, int lineHeight, Color defaultColor) {
        List<Span> spans = findSpans(fullText);
        FontMetrics metrics = g.getFontMetrics();

        for (int i = 0; i < lines.size(); i++) {
            StyledLine styledLine = lines.get(i);
            String line = styledLine.text();
            int lineStart = styledLine.sourceStart();
            int lineEnd = lineStart + line.length();
            int baseline = y + i * lineHeight;
            int curX = x;
            int cursor = 0;

            for (Span span : spans) {
                if (span.end() <= lineStart) continue;
                if (span.start() >= lineEnd) break;

                int localStart = Math.max(0, span.start() - lineStart);
                int localEnd = Math.min(line.length(), span.end() - lineStart);
                if (localStart > cursor) {
                    String plain = line.substring(cursor, localStart);
                    g.setColor(defaultColor);
                    g.drawString(plain, curX, baseline);
                    curX += metrics.stringWidth(plain);
                }
                if (localEnd > localStart) {
                    String hit = line.substring(localStart, localEnd);
                    int hitWidth = metrics.stringWidth(hit);
                    g.setColor(span.style().background);
                    g.fillRoundRect(curX - 2, baseline - metrics.getAscent() + 1,
                            hitWidth + 4, metrics.getHeight() - 1, 6, 6);
                    g.setColor(span.style().foreground);
                    g.drawString(hit, curX, baseline);
                    curX += hitWidth;
                }
                cursor = Math.max(cursor, localEnd);
            }

            if (cursor < line.length()) {
                g.setColor(defaultColor);
                g.drawString(line.substring(cursor), curX, baseline);
            }
        }
    }
}
