package ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A very small JSON reader and string escaper, because the project uses no external libraries.
 * Objects become Map<String, Object>, arrays become List<Object>, numbers become Double,
 * and true/false/null become Boolean/null.
 */
public final class MiniJson {

    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    /** Parses a complete JSON document, or throws IllegalArgumentException. */
    public static Object parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON text is null.");
        }
        MiniJson reader = new MiniJson(json);
        Object value = reader.readValue();
        reader.skipSpace();
        if (reader.pos != json.length()) {
            throw reader.error("Unexpected text after JSON value");
        }
        return value;
    }

    /** Returns a quoted JSON string with the needed escapes. */
    public static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /** Follows a path like "candidates", 0, "content" and returns null if any step is missing. */
    public static Object get(Object root, Object... path) {
        Object current = root;
        for (Object step : path) {
            if (step instanceof String key && current instanceof Map<?, ?> map) {
                current = map.get(key);
            } else if (step instanceof Integer index && current instanceof List<?> list) {
                current = index >= 0 && index < list.size() ? list.get(index) : null;
            } else {
                return null;
            }
        }
        return current;
    }

    private Object readValue() {
        skipSpace();
        if (pos >= text.length()) {
            throw error("Unexpected end of JSON");
        }
        char c = text.charAt(pos);
        if (c == '{') return readObject();
        if (c == '[') return readArray();
        if (c == '"') return readString();
        if (text.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
        if (text.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
        if (text.startsWith("null", pos)) { pos += 4; return null; }
        return readNumber();
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipSpace();
        if (peek('}')) { pos++; return map; }
        while (true) {
            skipSpace();
            if (!peek('"')) throw error("Expected a field name");
            String key = readString();
            skipSpace();
            expect(':');
            map.put(key, readValue());
            skipSpace();
            if (peek(',')) { pos++; continue; }
            expect('}');
            return map;
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipSpace();
        if (peek(']')) { pos++; return list; }
        while (true) {
            list.add(readValue());
            skipSpace();
            if (peek(',')) { pos++; continue; }
            expect(']');
            return list;
        }
    }

    private String readString() {
        StringBuilder out = new StringBuilder();
        pos++; // opening quote
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            if (pos >= text.length()) break;
            char e = text.charAt(pos++);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (pos + 4 > text.length()) throw error("Bad unicode escape");
                    out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> out.append(e); // \" \\ \/
            }
        }
        throw error("Unterminated string");
    }

    private Double readNumber() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
        if (start == pos) throw error("Unexpected character '" + text.charAt(pos) + "'");
        return Double.parseDouble(text.substring(start, pos));
    }

    private void skipSpace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
    }

    private boolean peek(char c) {
        return pos < text.length() && text.charAt(pos) == c;
    }

    private void expect(char c) {
        if (!peek(c)) throw error("Expected '" + c + "'");
        pos++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + pos);
    }
}
