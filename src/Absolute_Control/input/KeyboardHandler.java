package Absolute_Control.input;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

public class KeyboardHandler implements NativeKeyListener {

    private Consumer<String> onEnviar;
    private Runnable onDesconectar;
    private BooleanSupplier eventoActual = () -> true;

    private boolean controlando  = false;
    private boolean shiftActivo  = false;
    private boolean altGrActivo  = false;

    private final Set<Integer> teclasPresionadas = new HashSet<>();

    public KeyboardHandler(Consumer<String> onEnviar, Runnable onDesconectar) {
        this.onEnviar      = onEnviar;
        this.onDesconectar = onDesconectar;
    }

    public synchronized void setSesion(Consumer<String> enviar, Runnable desconectar) {
        onEnviar = enviar; onDesconectar = desconectar;
        setControlando(true);
    }

    public synchronized void setEventoActual(BooleanSupplier actual) { eventoActual = actual; }

    public synchronized void setControlando(boolean controlando) {
        this.controlando = controlando;
        if (!controlando) {
            teclasPresionadas.clear();
            shiftActivo = false;
            altGrActivo = false;
        }
    }

    private boolean esModificador(int raw) {
        return raw == 160 || raw == 161
            || raw == 162 || raw == 163
            || raw == 164 || raw == 165
            || raw == 91  || raw == 92;
    }

    private char oemRawToChar(int raw) {
        if (altGrActivo) {
            return switch (raw) {
                case 219 -> '[';
                case 221 -> ']';
                case 222 -> '{';
                case 220 -> '|';
                case 187 -> '~';
                case 50  -> '@';
                case 51  -> '#';
                case 53  -> '€';
                default  -> 0;
            };
        }
        if (shiftActivo) {
            return switch (raw) {
                case 192 -> 'Ñ';
                case 186 -> 'Ñ';
                case 187 -> '*';
                case 188 -> ';';
                case 189 -> '_';
                case 190 -> ':';
                case 191 -> '¡';
                case 219 -> '¿';
                case 220 -> '°';
                case 221 -> 'ª';
                case 222 -> '¨';
                case 161 -> '?';
                default  -> 0;
            };
        }
        return switch (raw) {
            case 192 -> 'ñ';
            case 186 -> 'ñ';
            case 187 -> '+';
            case 188 -> ',';
            case 189 -> '-';
            case 190 -> '.';
            case 191 -> ']';
            case 219 -> '?';
            case 220 -> '|';
            case 221 -> '¿';
            case 222 -> '´';
            case 161 -> ';';
            default  -> 0;
        };
    }

    private void procesarPresion(NativeKeyEvent e, List<String> mensajes) {
        int raw = e.getRawCode();

        if (raw == 160 || raw == 161) shiftActivo = true;
        if (raw == 165)               altGrActivo = true;

        if (!controlando) return;

        if (esModificador(raw)) {
            mensajes.add("K,PRESIONAR," + e.getKeyCode());
            return;
        }

        char c = e.getKeyChar();
        boolean tieneChar = (c != NativeKeyEvent.CHAR_UNDEFINED && !Character.isISOControl(c));

        if (tieneChar) {
            teclasPresionadas.add(raw);
        } else {
            char oemChar = oemRawToChar(raw);
            if (oemChar != 0) {
                mensajes.add("T," + oemChar);
            } else {
                mensajes.add("K,PRESIONAR," + e.getKeyCode());
            }
        }
    }

    private void procesarLiberacion(NativeKeyEvent e, List<String> mensajes) {
        int raw = e.getRawCode();

        if (raw == 160 || raw == 161) shiftActivo = false;
        if (raw == 165)               altGrActivo = false;

        if (!controlando) return;

        if (esModificador(raw)) {
            mensajes.add("K,LIBERAR," + e.getKeyCode());
            return;
        }

        char c = e.getKeyChar();
        boolean tieneChar = (c != NativeKeyEvent.CHAR_UNDEFINED && !Character.isISOControl(c));

        if (!tieneChar) {
            char oemChar = oemRawToChar(raw);
            if (oemChar == 0) mensajes.add("K,LIBERAR," + e.getKeyCode());
        }
        teclasPresionadas.remove(raw);
    }

    private void procesarCaracter(NativeKeyEvent e, List<String> mensajes) {
        if (!controlando) return;
        char c = e.getKeyChar();
        if (c == NativeKeyEvent.CHAR_UNDEFINED || Character.isISOControl(c)) return;
        if (teclasPresionadas.remove(e.getRawCode())) {
            mensajes.add("T," + c);
        }
    }
    // No invocar Cliente bajo este monitor: evita inversion de locks durante el cierre.
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        List<String> mensajes = new ArrayList<>();
        boolean escape;
        Consumer<String> enviar;
        Runnable desconectar;
        synchronized (this) {
            if (!eventoActual.getAsBoolean()) return;
            enviar = onEnviar; desconectar = onDesconectar;
            escape = controlando && e.getKeyCode() == NativeKeyEvent.VC_ESCAPE;
            if (!escape) procesarPresion(e, mensajes);
        }
        if (escape) desconectar.run();
        else mensajes.forEach(enviar);
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        List<String> mensajes = new ArrayList<>();
        Consumer<String> enviar;
        synchronized (this) {
            if (!eventoActual.getAsBoolean()) return;
            enviar = onEnviar; procesarLiberacion(e, mensajes);
        }
        mensajes.forEach(enviar);
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        List<String> mensajes = new ArrayList<>();
        Consumer<String> enviar;
        synchronized (this) {
            if (!eventoActual.getAsBoolean()) return;
            enviar = onEnviar; procesarCaracter(e, mensajes);
        }
        mensajes.forEach(enviar);
    }

}
