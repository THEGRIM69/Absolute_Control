package Absolute_Control.historial;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** JSON independiente de equipos.json, con reemplazo seguro y parser estructural. */
public final class RepositorioHistorialJson implements RepositorioHistorial {
    private static final int VERSION = 1;
    private static final ConcurrentMap<Path, Object> LOCKS = new ConcurrentHashMap<>();
    private final Path file;
    private final Object fileLock;

    public RepositorioHistorialJson(Path file) {
        this.file = file.toAbsolutePath().normalize();
        this.fileLock = LOCKS.computeIfAbsent(this.file, ignored -> new Object());
    }

    public static RepositorioHistorialJson predeterminado() {
        return new RepositorioHistorialJson(rutaPredeterminada());
    }

    public static Path rutaPredeterminada() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.trim().isEmpty()) {
            return Path.of(appData, "AbsoluteControl", "historial.json");
        }
        String home = System.getProperty("user.home");
        if (home == null || home.trim().isEmpty()) {
            throw new IllegalStateException("No se pudo determinar el directorio del usuario");
        }
        return Path.of(home, ".absolute-control", "historial.json");
    }

    @Override public List<EventoHistorial> cargar() throws HistorialException {
        synchronized (fileLock) {
            try {
                if (Files.notExists(file)) return List.of();
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Map<String, Object> root = object(new Reader(content).parse(), "Raíz JSON inválida");
                if (integer(root.get("version"), "Versión inválida") != VERSION) {
                    throw new FormatException("Versión de historial no compatible");
                }
                List<Object> records = array(root.get("eventos"), "eventos debe ser una lista");
                List<EventoHistorial> events = new ArrayList<>(records.size());
                Set<UUID> ids = new HashSet<>();
                for (Object value : records) {
                    Map<String, Object> record = object(value, "Evento inválido");
                    EventoHistorial event = new EventoHistorial(
                            UUID.fromString(text(record.get("id"), "ID inválido")),
                            Instant.parse(text(record.get("fecha"), "Fecha inválida")),
                            text(record.get("nombreEquipo"), "Nombre inválido"),
                            text(record.get("ip"), "IPv4 inválida"),
                            Math.toIntExact(integer(record.get("puerto"), "Puerto inválido")),
                            EventoHistorial.Tipo.valueOf(text(record.get("tipo"), "Tipo inválido")),
                            EventoHistorial.Resultado.valueOf(
                                    text(record.get("resultado"), "Resultado inválido")),
                            text(record.get("detalle"), "Detalle inválido"));
                    ValidacionHistorial.validar(event);
                    if (!ids.add(event.getId())) throw new FormatException("ID histórico duplicado");
                    events.add(event);
                }
                return List.copyOf(events);
            } catch (HistorialException error) {
                throw error;
            } catch (IOException | SecurityException error) {
                throw new HistorialException("No se pudo leer historial.json", error);
            } catch (FormatException | IllegalArgumentException error) {
                throw new HistorialException("historial.json está corrupto", error);
            }
        }
    }

    @Override public void guardar(List<EventoHistorial> events) throws HistorialException {
        synchronized (fileLock) {
            Set<UUID> ids = new HashSet<>();
            for (EventoHistorial event : events) {
                ValidacionHistorial.validar(event);
                if (!ids.add(event.getId())) throw new HistorialException("ID histórico duplicado");
            }
            Path parent = file.getParent();
            if (parent == null) throw new HistorialException("Ruta histórica sin directorio");
            Path temporary = null;
            try {
                Files.createDirectories(parent);
                temporary = Files.createTempFile(parent, "historial-", ".tmp");
                byte[] bytes = serialize(events).getBytes(StandardCharsets.UTF_8);
                try (FileChannel channel = FileChannel.open(temporary,
                        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException error) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
                temporary = null;
            } catch (IOException | SecurityException error) {
                throw new HistorialException("No se pudo escribir historial.json", error);
            } finally {
                if (temporary != null) {
                    try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
                }
            }
        }
    }

    private static String serialize(List<EventoHistorial> events) {
        StringBuilder json = new StringBuilder(100 + events.size() * 260);
        json.append("{\n  \"version\": 1,\n  \"eventos\": [");
        for (int i = 0; i < events.size(); i++) {
            EventoHistorial event = events.get(i);
            if (i > 0) json.append(',');
            json.append("\n    {\n")
                    .append("      \"id\": ").append(quote(event.getId().toString())).append(",\n")
                    .append("      \"fecha\": ").append(quote(event.getFecha().toString())).append(",\n")
                    .append("      \"nombreEquipo\": ").append(quote(event.getNombreEquipo())).append(",\n")
                    .append("      \"ip\": ").append(quote(event.getIp())).append(",\n")
                    .append("      \"puerto\": ").append(event.getPuerto()).append(",\n")
                    .append("      \"tipo\": ").append(quote(event.getTipo().name())).append(",\n")
                    .append("      \"resultado\": ").append(quote(event.getResultado().name())).append(",\n")
                    .append("      \"detalle\": ").append(quote(event.getDetalle())).append('\n')
                    .append("    }");
        }
        return json.append("\n  ]\n}\n").toString();
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\b': result.append("\\b"); break;
                case '\f': result.append("\\f"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (c < 0x20) result.append(String.format("\\u%04x", (int) c));
                    else result.append(c);
            }
        }
        return result.append('"').toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String message) throws FormatException {
        if (!(value instanceof Map)) throw new FormatException(message);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value, String message) throws FormatException {
        if (!(value instanceof List)) throw new FormatException(message);
        return (List<Object>) value;
    }

    private static String text(Object value, String message) throws FormatException {
        if (!(value instanceof String)) throw new FormatException(message);
        return (String) value;
    }

    private static long integer(Object value, String message) throws FormatException {
        if (!(value instanceof Long)) throw new FormatException(message);
        return (Long) value;
    }

    private static final class FormatException extends Exception {
        private FormatException(String message) { super(message); }
    }

    /** Parser JSON de descenso recursivo, sin expresiones regulares. */
    private static final class Reader {
        private final String source;
        private int position;

        private Reader(String source) { this.source = source; }

        private Object parse() throws FormatException {
            whitespace();
            Object value = value();
            whitespace();
            if (position != source.length()) fail("Contenido posterior a la raíz");
            return value;
        }

        private Object value() throws FormatException {
            if (position >= source.length()) return fail("Valor incompleto");
            char c = source.charAt(position);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == '-' || Character.isDigit(c)) return number();
            if (literal("true")) return Boolean.TRUE;
            if (literal("false")) return Boolean.FALSE;
            if (literal("null")) return null;
            return fail("Valor desconocido");
        }

        private Map<String, Object> object() throws FormatException {
            position++;
            Map<String, Object> map = new LinkedHashMap<>();
            whitespace();
            if (take('}')) return map;
            while (true) {
                whitespace();
                if (position >= source.length() || source.charAt(position) != '"') {
                    return fail("Propiedad inválida");
                }
                String key = string();
                if (map.containsKey(key)) return fail("Propiedad duplicada");
                whitespace(); require(':'); whitespace();
                map.put(key, value());
                whitespace();
                if (take('}')) return map;
                require(',');
            }
        }

        private List<Object> array() throws FormatException {
            position++;
            List<Object> list = new ArrayList<>();
            whitespace();
            if (take(']')) return list;
            while (true) {
                whitespace(); list.add(value()); whitespace();
                if (take(']')) return list;
                require(',');
            }
        }

        private String string() throws FormatException {
            require('"');
            StringBuilder result = new StringBuilder();
            while (position < source.length()) {
                char c = source.charAt(position++);
                if (c == '"') return result.toString();
                if (c == '\\') {
                    if (position >= source.length()) return fail("Escape incompleto");
                    char escape = source.charAt(position++);
                    switch (escape) {
                        case '"': result.append('"'); break;
                        case '\\': result.append('\\'); break;
                        case '/': result.append('/'); break;
                        case 'b': result.append('\b'); break;
                        case 'f': result.append('\f'); break;
                        case 'n': result.append('\n'); break;
                        case 'r': result.append('\r'); break;
                        case 't': result.append('\t'); break;
                        case 'u': result.append(unicode()); break;
                        default: return fail("Escape inválido");
                    }
                } else {
                    if (c < 0x20) return fail("Control inválido en cadena");
                    result.append(c);
                }
            }
            return fail("Cadena sin cierre");
        }

        private char unicode() throws FormatException {
            if (position + 4 > source.length()) return fail("Unicode incompleto");
            int result = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(source.charAt(position++), 16);
                if (digit < 0) return fail("Unicode inválido");
                result = result * 16 + digit;
            }
            return (char) result;
        }

        private Long number() throws FormatException {
            int start = position;
            if (source.charAt(position) == '-') position++;
            if (position >= source.length()) return fail("Número incompleto");
            if (source.charAt(position) == '0') {
                position++;
                if (position < source.length() && Character.isDigit(source.charAt(position))) {
                    return fail("Cero inicial inválido");
                }
            } else {
                if (!Character.isDigit(source.charAt(position))) return fail("Número inválido");
                while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
            }
            if (position < source.length()) {
                char next = source.charAt(position);
                if (next == '.' || next == 'e' || next == 'E') return fail("Entero requerido");
            }
            try { return Long.parseLong(source.substring(start, position)); }
            catch (NumberFormatException error) { return fail("Número fuera de rango"); }
        }

        private boolean literal(String literal) {
            if (!source.startsWith(literal, position)) return false;
            position += literal.length();
            return true;
        }

        private void whitespace() {
            while (position < source.length()) {
                char c = source.charAt(position);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
                position++;
            }
        }

        private boolean take(char expected) {
            if (position < source.length() && source.charAt(position) == expected) {
                position++; return true;
            }
            return false;
        }

        private void require(char expected) throws FormatException {
            if (!take(expected)) fail("Se esperaba '" + expected + "'");
        }

        private <T> T fail(String message) throws FormatException {
            throw new FormatException(message + " en posición " + position);
        }
    }
}
