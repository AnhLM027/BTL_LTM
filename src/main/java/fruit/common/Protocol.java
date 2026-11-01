package fruit.common;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Giao thức dùng chung: mỗi dòng TCP chứa một object JSON UTF-8. Không cần thư viện JSON ngoài. */
public final class Protocol {
    public static final int PORT = 5555, MAX_MESSAGE = 65_536;

    private Protocol() {}

    public static Map<String, Object> obj(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Key/value pairs required");
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }

    public static String str(Map<?, ?> m, String k) {
        return str(m, k, "");
    }

    public static String str(Map<?, ?> m, String k, String fallback) {
        Object v = m.get(k);
        return v instanceof String ? (String) v : fallback;
    }

    public static int integer(Map<?, ?> m, String k) {
        return integer(m, k, 0);
    }

    public static int integer(Map<?, ?> m, String k, int fallback) {
        Object v = m.get(k);
        return v instanceof Number ? ((Number) v).intValue() : fallback;
    }

    public static double number(Map<?, ?> m, String k) {
        return number(m, k, 0);
    }

    public static double number(Map<?, ?> m, String k, double fallback) {
        Object v = m.get(k);
        return v instanceof Number ? ((Number) v).doubleValue() : fallback;
    }

    public static boolean bool(Map<?, ?> m, String k) {
        return Boolean.TRUE.equals(m.get(k));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object v) {
        return v instanceof List ? (List<Object>) v : List.of();
    }

    /** Bounds requests before allocating an arbitrarily large line. */
    public static String readLine(Reader reader) throws IOException {
        StringBuilder b = new StringBuilder();
        for (int c; (c = reader.read()) != -1; ) {
            if (c == '\n') return b.toString();
            if (b.length() >= MAX_MESSAGE) throw new IOException("Message exceeds 64 KiB");
            if (c != '\r') b.append((char) c);
        }
        return b.isEmpty() ? null : b.toString();
    }

    public static String json(Object value) {
        StringBuilder b = new StringBuilder();
        write(value, b, 0);
        return b.toString();
    }

    private static void write(Object v, StringBuilder b, int depth) {
        if (depth > 64) throw new IllegalArgumentException("JSON nesting too deep");
        if (v == null) b.append("null");
        else if (v instanceof String || v instanceof Character || v instanceof Enum<?>)
            quote(v.toString(), b);
        else if (v instanceof Boolean) b.append(v);
        else if (v instanceof Number n) {
            if (!Double.isFinite(n.doubleValue()))
                throw new IllegalArgumentException("Non-finite JSON number");
            b.append(n);
        } else if (v instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (var e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                quote(String.valueOf(e.getKey()), b);
                b.append(':');
                write(e.getValue(), b, depth + 1);
            }
            b.append('}');
        } else if (v instanceof Iterable<?> a) {
            b.append('[');
            boolean first = true;
            for (Object x : a) {
                if (!first) b.append(',');
                first = false;
                write(x, b, depth + 1);
            }
            b.append(']');
        } else if (v instanceof Object[] a) write(Arrays.asList(a), b, depth + 1);
        else
            throw new IllegalArgumentException("Unsupported JSON value: " + v.getClass().getName());
    }

    private static void quote(String s, StringBuilder b) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '\b' -> b.append("\\b");
                case '\f' -> b.append("\\f");
                default -> {
                    if (c < 32) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        b.append('"');
    }

    public static Object parse(String text) {
        if (text == null || text.length() > MAX_MESSAGE)
            throw new IllegalArgumentException("Invalid JSON size");
        Parser p = new Parser(text);
        Object result = p.value(0);
        p.space();
        if (p.i != text.length()) throw p.error();
        return result;
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error() {
            return new IllegalArgumentException("Invalid JSON at character " + i);
        }

        void space() {
            while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++;
        }

        boolean take(char c) {
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        void expect(char c) {
            if (!take(c)) throw error();
        }

        Object value(int depth) {
            if (depth > 64) throw error();
            space();
            if (i >= s.length()) throw error();
            char c = s.charAt(i);
            if (c == '"') return string();
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                space();
                if (take('}')) return m;
                do {
                    space();
                    String k = string();
                    space();
                    expect(':');
                    Object v = value(depth + 1);
                    if (m.containsKey(k)) throw error();
                    m.put(k, v);
                    space();
                    if (take('}')) return m;
                    expect(',');
                } while (true);
            }
            if (c == '[') {
                i++;
                List<Object> a = new ArrayList<>();
                space();
                if (take(']')) return a;
                do {
                    a.add(value(depth + 1));
                    space();
                    if (take(']')) return a;
                    expect(',');
                } while (true);
            }
            if (s.startsWith("true", i)) {
                i += 4;
                return true;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return false;
            }
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            int start = i;
            take('-');
            if (take('0')) {
                if (i < s.length() && Character.isDigit(s.charAt(i))) throw error();
            } else {
                if (i >= s.length() || s.charAt(i) < '1' || s.charAt(i) > '9') throw error();
                digits();
            }
            boolean floating = false;
            if (take('.')) {
                floating = true;
                int before = i;
                digits();
                if (i == before) throw error();
            }
            if (take('e') || take('E')) {
                floating = true;
                if (!take('+')) take('-');
                int before = i;
                digits();
                if (i == before) throw error();
            }
            try {
                String n = s.substring(start, i);
                if (!floating) return Long.parseLong(n);
                double d = Double.parseDouble(n);
                if (!Double.isFinite(d)) throw error();
                return d;
            } catch (NumberFormatException e) {
                throw error();
            }
        }

        void digits() {
            while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
        }

        String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c < 32) throw error();
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                if (i >= s.length()) throw error();
                char e = s.charAt(i++);
                switch (e) {
                    case '"', '\\', '/' -> b.append(e);
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw error();
                        try {
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error();
                        }
                        i += 4;
                    }
                    default -> throw error();
                }
            }
            throw error();
        }
    }
}
