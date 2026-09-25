package example.property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static Object parse(String source) { return new Parser(source).parse(); }

    static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return '"' + escape(text) + '"';
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean comma = false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (comma) out.append(',');
                out.append(stringify(String.valueOf(entry.getKey()))).append(':').append(stringify(entry.getValue()));
                comma = true;
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("[");
            boolean comma = false;
            for (Object item : values) {
                if (comma) out.append(',');
                out.append(stringify(item));
                comma = true;
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final class Parser {
        private final String text;
        private int at;
        Parser(String text) { this.text = text; }
        Object parse() { Object value = value(); whitespace(); if (at != text.length()) fail(); return value; }
        private Object value() {
            whitespace();
            if (at >= text.length()) return fail();
            return switch (text.charAt(at)) {
                case '{' -> object(); case '[' -> array(); case '"' -> string();
                case 't' -> literal("true", true); case 'f' -> literal("false", false); case 'n' -> literal("null", null);
                default -> number();
            };
        }
        private Map<String, Object> object() {
            Map<String, Object> out = new LinkedHashMap<>(); at++; whitespace();
            if (take('}')) return out;
            do { whitespace(); String key = string(); whitespace(); expect(':'); out.put(key, value()); whitespace(); } while (take(','));
            expect('}'); return out;
        }
        private List<Object> array() {
            List<Object> out = new ArrayList<>(); at++; whitespace();
            if (take(']')) return out;
            do { out.add(value()); whitespace(); } while (take(','));
            expect(']'); return out;
        }
        private String string() {
            expect('"'); StringBuilder out = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (at >= text.length()) return fail();
                char e = text.charAt(at++);
                if (e == 'u') { out.append((char) Integer.parseInt(text.substring(at, at + 4), 16)); at += 4; }
                else out.append(switch (e) { case '"' -> '"'; case '\\' -> '\\'; case '/' -> '/'; case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; default -> throw new IllegalArgumentException("Invalid JSON escape"); });
            }
            return fail();
        }
        private Object number() {
            int start = at;
            while (at < text.length() && "-+0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
            if (start == at) return fail();
            String token = text.substring(start, at);
            try { return token.contains(".") || token.contains("e") || token.contains("E") ? Double.valueOf(token) : Long.valueOf(token); }
            catch (NumberFormatException error) { return fail(); }
        }
        private Object literal(String token, Object value) { if (!text.startsWith(token, at)) return fail(); at += token.length(); return value; }
        private void whitespace() { while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++; }
        private boolean take(char c) { if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
        private void expect(char c) { if (!take(c)) fail(); }
        private <T> T fail() { throw new IllegalArgumentException("Invalid JSON at offset " + at); }
    }
}
