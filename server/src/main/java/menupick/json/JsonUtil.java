package menupick.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonUtil {
    private JsonUtil() {
    }

    public static Map<String, String> parseFlatObject(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON body is empty");
        }

        String text = json.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("JSON body is empty");
        }
        if (text.charAt(0) != '{' || text.charAt(text.length() - 1) != '}') {
            throw new IllegalArgumentException("JSON object is required");
        }

        Map<String, String> result = new LinkedHashMap<>();
        Cursor cursor = new Cursor(1);

        while (true) {
            skipWhitespace(text, cursor);
            if (cursor.index >= text.length() - 1) {
                break;
            }
            if (text.charAt(cursor.index) == '}') {
                break;
            }

            String key = parseString(text, cursor);
            skipWhitespace(text, cursor);
            require(text, cursor, ':');
            skipWhitespace(text, cursor);
            String value = parseValue(text, cursor);
            result.put(key, value);
            skipWhitespace(text, cursor);

            if (cursor.index >= text.length()) {
                break;
            }
            char delimiter = text.charAt(cursor.index);
            if (delimiter == ',') {
                cursor.index++;
                continue;
            }
            if (delimiter == '}') {
                break;
            }
            throw new IllegalArgumentException("Unexpected character at position " + cursor.index + ": " + delimiter);
        }

        return result;
    }

    public static String stringify(Map<String, ?> values) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            parts.add(quote(entry.getKey()) + ":" + stringifyValue(entry.getValue()));
        }
        return "{" + String.join(",", parts) + "}";
    }

    private static String stringifyValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                parts.add(quote(String.valueOf(entry.getKey())) + ":" + stringifyValue(entry.getValue()));
            }
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> items = new ArrayList<>();
            for (Object item : iterable) {
                items.add(stringifyValue(item));
            }
            return "[" + String.join(",", items) + "]";
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder();
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }

    private static String parseValue(String text, Cursor cursor) {
        if (cursor.index >= text.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON value");
        }

        char ch = text.charAt(cursor.index);
        if (ch == '"') {
            return parseString(text, cursor);
        }

        int start = cursor.index;
        while (cursor.index < text.length()) {
            ch = text.charAt(cursor.index);
            if (ch == ',' || ch == '}') {
                break;
            }
            cursor.index++;
        }
        return text.substring(start, cursor.index).trim();
    }

    private static String parseString(String text, Cursor cursor) {
        require(text, cursor, '"');
        StringBuilder builder = new StringBuilder();
        while (cursor.index < text.length()) {
            char ch = text.charAt(cursor.index++);
            if (ch == '"') {
                return builder.toString();
            }
            if (ch == '\\') {
                if (cursor.index >= text.length()) {
                    throw new IllegalArgumentException("Invalid escape sequence");
                }
                char escaped = text.charAt(cursor.index++);
                switch (escaped) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        if (cursor.index + 4 > text.length()) {
                            throw new IllegalArgumentException("Invalid unicode escape");
                        }
                        String hex = text.substring(cursor.index, cursor.index + 4);
                        builder.append((char) Integer.parseInt(hex, 16));
                        cursor.index += 4;
                    }
                    default -> throw new IllegalArgumentException("Unsupported escape character: " + escaped);
                }
            } else {
                builder.append(ch);
            }
        }
        throw new IllegalArgumentException("Unterminated string literal");
    }

    private static void skipWhitespace(String text, Cursor cursor) {
        while (cursor.index < text.length() && Character.isWhitespace(text.charAt(cursor.index))) {
            cursor.index++;
        }
    }

    private static void require(String text, Cursor cursor, char expected) {
        if (cursor.index >= text.length() || text.charAt(cursor.index) != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "' at position " + cursor.index);
        }
        cursor.index++;
    }

    private static final class Cursor {
        private int index;

        private Cursor(int index) {
            this.index = index;
        }
    }
}

