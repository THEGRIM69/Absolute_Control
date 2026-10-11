package Absolute_Control.equipos;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Repositorio JSON local con reemplazo atómico y acceso serializado. */
public final class RepositorioEquiposJson implements RepositorioEquipos {
    private static final int VERSION = 1;
    private static final ConcurrentMap<Path, Object> BLOQUEOS = new ConcurrentHashMap<>();
    private final Path archivo;
    private final Object bloqueoArchivo;

    public RepositorioEquiposJson(Path archivo) {
        if (archivo == null) throw new NullPointerException("archivo");
        this.archivo = archivo.toAbsolutePath().normalize();
        this.bloqueoArchivo = BLOQUEOS.computeIfAbsent(this.archivo, ignorada -> new Object());
    }

    public static RepositorioEquiposJson predeterminado() {
        return new RepositorioEquiposJson(rutaPredeterminada());
    }

    public static Path rutaPredeterminada() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.trim().isEmpty()) {
            return Path.of(appData, "AbsoluteControl", "equipos.json");
        }
        String home = System.getProperty("user.home");
        if (home == null || home.trim().isEmpty()) {
            throw new IllegalStateException("No se pudo determinar el directorio de datos del usuario");
        }
        return Path.of(home, ".absolute-control", "equipos.json");
    }

    public Path getArchivo() {
        return archivo;
    }

    @Override public List<EquipoGuardado> cargar()
            throws PersistenciaEquiposException {
        synchronized (bloqueoArchivo) {
            return cargarBloqueado();
        }
    }

    private List<EquipoGuardado> cargarBloqueado() throws PersistenciaEquiposException {
        try {
            if (Files.notExists(archivo)) return List.of();
        } catch (SecurityException error) {
            throw new PersistenciaEquiposException("No se pudo comprobar el archivo de equipos", error);
        }
        final String contenido;
        try {
            contenido = Files.readString(archivo, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException error) {
            throw new PersistenciaEquiposException("No se pudo leer el archivo de equipos", error);
        }

        try {
            Object raiz = new JsonReader(contenido).parse();
            Map<String, Object> objeto = comoObjeto(raiz, "La raíz JSON debe ser un objeto");
            long version = comoEntero(objeto.get("version"), "La versión JSON no es válida");
            if (version != VERSION) {
                throw new FormatoJsonException("Versión JSON no compatible: " + version);
            }
            List<Object> registros = comoLista(objeto.get("equipos"),
                    "La propiedad equipos debe ser una lista");
            List<EquipoGuardado> equipos = new ArrayList<>(registros.size());
            Set<UUID> ids = new HashSet<>();
            for (Object registro : registros) {
                EquipoGuardado equipo = leerEquipo(comoObjeto(registro,
                        "Cada equipo debe ser un objeto"));
                if (!ids.add(equipo.getId())) {
                    throw new FormatoJsonException("El archivo contiene identificadores duplicados");
                }
                equipos.add(equipo);
            }
            return List.copyOf(equipos);
        } catch (FormatoJsonException | ValidacionEquipoException
                 | IllegalArgumentException | DateTimeException error) {
            throw new PersistenciaEquiposException(
                    "El archivo de equipos contiene JSON inválido o datos corruptos", error);
        }
    }

    @Override public void guardar(List<EquipoGuardado> equipos)
            throws PersistenciaEquiposException {
        synchronized (bloqueoArchivo) {
            guardarBloqueado(equipos);
        }
    }

    private void guardarBloqueado(List<EquipoGuardado> equipos)
            throws PersistenciaEquiposException {
        if (equipos == null) throw new NullPointerException("equipos");
        validarParaEscritura(equipos);
        byte[] datos = serializar(equipos).getBytes(StandardCharsets.UTF_8);
        Path directorio = archivo.getParent();
        if (directorio == null) {
            throw new PersistenciaEquiposException("La ruta del archivo no tiene directorio padre");
        }

        Path temporal = null;
        try {
            Files.createDirectories(directorio);
            temporal = Files.createTempFile(directorio, "equipos-", ".tmp");
            try (FileChannel canal = FileChannel.open(temporal,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(datos);
                while (buffer.hasRemaining()) canal.write(buffer);
                canal.force(true);
            }
            try {
                Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException noAtomico) {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            }
            temporal = null;
        } catch (IOException | SecurityException error) {
            throw new PersistenciaEquiposException("No se pudo guardar el archivo de equipos", error);
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ignored) {
                    // El error principal ya se informa al llamador.
                }
            }
        }
    }

    private static EquipoGuardado leerEquipo(Map<String, Object> objeto)
            throws FormatoJsonException, ValidacionEquipoException {
        UUID id = UUID.fromString(comoTexto(objeto.get("id"), "Identificador inválido"));
        String nombre = comoTexto(objeto.get("nombre"), "Nombre inválido");
        String ip = comoTexto(objeto.get("ip"), "IPv4 inválida");
        long puertoLargo = comoEntero(objeto.get("puerto"), "Puerto inválido");
        if (puertoLargo < Integer.MIN_VALUE || puertoLargo > Integer.MAX_VALUE) {
            throw new FormatoJsonException("Puerto fuera de rango");
        }
        int puerto = (int) puertoLargo;
        Instant creadoEn = Instant.parse(comoTexto(objeto.get("creadoEn"),
                "Fecha de creación inválida"));
        Instant modificadoEn = Instant.parse(comoTexto(objeto.get("modificadoEn"),
                "Fecha de modificación inválida"));
        ValidadorEquipo.validar(nombre, ip, puerto);
        if (modificadoEn.isBefore(creadoEn)) {
            throw new FormatoJsonException("La modificación precede a la creación");
        }
        return new EquipoGuardado(id, nombre, ip, puerto, creadoEn, modificadoEn);
    }

    private static void validarParaEscritura(List<EquipoGuardado> equipos)
            throws PersistenciaEquiposException {
        Set<UUID> ids = new HashSet<>();
        for (EquipoGuardado equipo : equipos) {
            if (equipo == null || !ids.add(equipo.getId())) {
                throw new PersistenciaEquiposException("No se pueden guardar equipos nulos o duplicados");
            }
            try {
                ValidadorEquipo.validar(equipo.getNombre(), equipo.getIp(), equipo.getPuerto());
            } catch (ValidacionEquipoException error) {
                throw new PersistenciaEquiposException("No se puede guardar un equipo inválido", error);
            }
            if (equipo.getModificadoEn().isBefore(equipo.getCreadoEn())) {
                throw new PersistenciaEquiposException("Las fechas del equipo no son coherentes");
            }
        }
    }

    private static String serializar(List<EquipoGuardado> equipos) {
        StringBuilder json = new StringBuilder(160 + equipos.size() * 220);
        json.append("{\n  \"version\": ").append(VERSION).append(",\n  \"equipos\": [");
        for (int i = 0; i < equipos.size(); i++) {
            EquipoGuardado equipo = equipos.get(i);
            if (i > 0) json.append(',');
            json.append("\n    {\n")
                    .append("      \"id\": ").append(cadena(equipo.getId().toString())).append(",\n")
                    .append("      \"nombre\": ").append(cadena(equipo.getNombre())).append(",\n")
                    .append("      \"ip\": ").append(cadena(equipo.getIp())).append(",\n")
                    .append("      \"puerto\": ").append(equipo.getPuerto()).append(",\n")
                    .append("      \"creadoEn\": ").append(cadena(equipo.getCreadoEn().toString())).append(",\n")
                    .append("      \"modificadoEn\": ").append(cadena(equipo.getModificadoEn().toString())).append('\n')
                    .append("    }");
        }
        json.append("\n  ]\n}\n");
        return json.toString();
    }

    private static String cadena(String valor) {
        StringBuilder salida = new StringBuilder(valor.length() + 2).append('"');
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"': salida.append("\\\""); break;
                case '\\': salida.append("\\\\"); break;
                case '\b': salida.append("\\b"); break;
                case '\f': salida.append("\\f"); break;
                case '\n': salida.append("\\n"); break;
                case '\r': salida.append("\\r"); break;
                case '\t': salida.append("\\t"); break;
                default:
                    if (c < 0x20) salida.append(String.format("\\u%04x", (int) c));
                    else salida.append(c);
            }
        }
        return salida.append('"').toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> comoObjeto(Object valor, String mensaje)
            throws FormatoJsonException {
        if (!(valor instanceof Map)) throw new FormatoJsonException(mensaje);
        return (Map<String, Object>) valor;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> comoLista(Object valor, String mensaje)
            throws FormatoJsonException {
        if (!(valor instanceof List)) throw new FormatoJsonException(mensaje);
        return (List<Object>) valor;
    }

    private static String comoTexto(Object valor, String mensaje) throws FormatoJsonException {
        if (!(valor instanceof String)) throw new FormatoJsonException(mensaje);
        return (String) valor;
    }

    private static long comoEntero(Object valor, String mensaje) throws FormatoJsonException {
        if (!(valor instanceof Long)) throw new FormatoJsonException(mensaje);
        return (Long) valor;
    }

    private static final class FormatoJsonException extends Exception {
        private FormatoJsonException(String message) { super(message); }
    }

    /** Parser JSON de descenso recursivo; no usa regex ni evalúa código. */
    private static final class JsonReader {
        private final String texto;
        private int posicion;

        private JsonReader(String texto) { this.texto = texto; }

        private Object parse() throws FormatoJsonException {
            saltarEspacios();
            Object valor = leerValor();
            saltarEspacios();
            if (posicion != texto.length()) error("Contenido después del valor raíz");
            return valor;
        }

        private Object leerValor() throws FormatoJsonException {
            if (posicion >= texto.length()) return error("Valor incompleto");
            char c = texto.charAt(posicion);
            if (c == '{') return leerObjeto();
            if (c == '[') return leerLista();
            if (c == '"') return leerCadena();
            if (c == '-' || (c >= '0' && c <= '9')) return leerNumero();
            if (coincide("true")) return Boolean.TRUE;
            if (coincide("false")) return Boolean.FALSE;
            if (coincide("null")) return null;
            return error("Valor JSON no reconocido");
        }

        private Map<String, Object> leerObjeto() throws FormatoJsonException {
            posicion++;
            Map<String, Object> objeto = new LinkedHashMap<>();
            saltarEspacios();
            if (consumir('}')) return objeto;
            while (true) {
                saltarEspacios();
                if (posicion >= texto.length() || texto.charAt(posicion) != '"') {
                    return error("Se esperaba una propiedad de texto");
                }
                String clave = leerCadena();
                if (objeto.containsKey(clave)) return error("Propiedad JSON duplicada: " + clave);
                saltarEspacios();
                exigir(':');
                saltarEspacios();
                objeto.put(clave, leerValor());
                saltarEspacios();
                if (consumir('}')) return objeto;
                exigir(',');
            }
        }

        private List<Object> leerLista() throws FormatoJsonException {
            posicion++;
            List<Object> lista = new ArrayList<>();
            saltarEspacios();
            if (consumir(']')) return lista;
            while (true) {
                saltarEspacios();
                lista.add(leerValor());
                saltarEspacios();
                if (consumir(']')) return lista;
                exigir(',');
            }
        }

        private String leerCadena() throws FormatoJsonException {
            exigir('"');
            StringBuilder salida = new StringBuilder();
            while (posicion < texto.length()) {
                char c = texto.charAt(posicion++);
                if (c == '"') return salida.toString();
                if (c == '\\') {
                    if (posicion >= texto.length()) return error("Escape JSON incompleto");
                    char escape = texto.charAt(posicion++);
                    switch (escape) {
                        case '"': salida.append('"'); break;
                        case '\\': salida.append('\\'); break;
                        case '/': salida.append('/'); break;
                        case 'b': salida.append('\b'); break;
                        case 'f': salida.append('\f'); break;
                        case 'n': salida.append('\n'); break;
                        case 'r': salida.append('\r'); break;
                        case 't': salida.append('\t'); break;
                        case 'u': salida.append(leerUnicode()); break;
                        default: return error("Escape JSON inválido");
                    }
                } else {
                    if (c < 0x20) return error("Carácter de control dentro de una cadena");
                    salida.append(c);
                }
            }
            return error("Cadena JSON sin cierre");
        }

        private char leerUnicode() throws FormatoJsonException {
            if (posicion + 4 > texto.length()) return error("Escape Unicode incompleto");
            int valor = 0;
            for (int i = 0; i < 4; i++) {
                int digito = Character.digit(texto.charAt(posicion++), 16);
                if (digito < 0) return error("Escape Unicode inválido");
                valor = valor * 16 + digito;
            }
            return (char) valor;
        }

        private Long leerNumero() throws FormatoJsonException {
            int inicio = posicion;
            if (texto.charAt(posicion) == '-') posicion++;
            if (posicion >= texto.length()) return error("Número incompleto");
            if (texto.charAt(posicion) == '0') {
                posicion++;
                if (posicion < texto.length() && Character.isDigit(texto.charAt(posicion))) {
                    return error("Número con cero inicial");
                }
            } else {
                if (!Character.isDigit(texto.charAt(posicion))) return error("Número inválido");
                while (posicion < texto.length() && Character.isDigit(texto.charAt(posicion))) posicion++;
            }
            if (posicion < texto.length()) {
                char siguiente = texto.charAt(posicion);
                if (siguiente == '.' || siguiente == 'e' || siguiente == 'E') {
                    return error("Se esperaba un número entero");
                }
            }
            try {
                return Long.parseLong(texto.substring(inicio, posicion));
            } catch (NumberFormatException error) {
                return error("Número fuera de rango");
            }
        }

        private boolean coincide(String literal) {
            if (!texto.startsWith(literal, posicion)) return false;
            posicion += literal.length();
            return true;
        }

        private void saltarEspacios() {
            while (posicion < texto.length()) {
                char c = texto.charAt(posicion);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
                posicion++;
            }
        }

        private boolean consumir(char esperado) {
            if (posicion < texto.length() && texto.charAt(posicion) == esperado) {
                posicion++;
                return true;
            }
            return false;
        }

        private void exigir(char esperado) throws FormatoJsonException {
            if (!consumir(esperado)) error("Se esperaba '" + esperado + "'");
        }

        private <T> T error(String mensaje) throws FormatoJsonException {
            throw new FormatoJsonException(mensaje + " en posición " + posicion);
        }
    }
}
