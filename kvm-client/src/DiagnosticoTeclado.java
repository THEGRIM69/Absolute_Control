import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Herramienta de diagnóstico — muestra raw code, keyCode y keyChar
 * de cada tecla que presiones. Úsalo para mapear tu teclado exacto.
 */
public class DiagnosticoTeclado extends JFrame implements NativeKeyListener {

    private JTextArea log;
    private boolean shiftActivo = false;
    private boolean altGrActivo = false;

    public DiagnosticoTeclado() {
        setTitle("Diagnostico de Teclado");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        log = new JTextArea();
        log.setFont(new Font("Consolas", Font.PLAIN, 13));
        log.setEditable(false);
        log.append("Presiona teclas para ver sus codigos...\n");
        log.append(String.format("%-10s %-10s %-10s %-15s %-10s %-10s%n",
                "RAW", "KEYCODE", "CHAR_INT", "CHAR", "SHIFT", "ALTGR"));
        log.append("─".repeat(65) + "\n");

        add(new JScrollPane(log));
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        int raw    = e.getRawCode();
        int kc     = e.getKeyCode();
        char c     = e.getKeyChar();
        int charInt = (int) c;

        if (raw == 160 || raw == 161) shiftActivo = true;
        if (raw == 165)               altGrActivo = true;

        String charStr = (c == NativeKeyEvent.CHAR_UNDEFINED || Character.isISOControl(c))
                ? "(none)" : "'" + c + "'";

        String linea = String.format("%-10d %-10d %-10d %-15s %-10s %-10s%n",
                raw, kc, charInt, charStr,
                shiftActivo ? "SI" : "no",
                altGrActivo ? "SI" : "no");

        SwingUtilities.invokeLater(() -> {
            log.append(linea);
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        int raw = e.getRawCode();
        if (raw == 160 || raw == 161) shiftActivo = false;
        if (raw == 165)               altGrActivo = false;
    }

    @Override public void nativeKeyTyped(NativeKeyEvent e) {}

    public static void main(String[] args) {
        try {
            Logger hookLog = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            hookLog.setLevel(Level.WARNING);
            hookLog.setUseParentHandlers(false);

            GlobalScreen.registerNativeHook();

            DiagnosticoTeclado diag = new DiagnosticoTeclado();
            GlobalScreen.addNativeKeyListener(diag);

            SwingUtilities.invokeLater(() -> diag.setVisible(true));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}