package Absolute_Control.historial;

import java.time.Clock;
import java.util.UUID;

/** Convierte la secuencia confiable de estados de Cliente en eventos sin duplicarlos. */
public final class RegistroSesiones {
    @FunctionalInterface
    public interface Receptor {
        void aceptar(EventoHistorial evento);
    }

    private final Receptor receptor;
    private final Clock clock;
    private Destino destino;
    private boolean intentando;
    private boolean conectada;

    public RegistroSesiones(Receptor receptor) {
        this(receptor, Clock.systemUTC());
    }

    public RegistroSesiones(Receptor receptor, Clock clock) {
        this.receptor = receptor;
        this.clock = clock;
    }

    public synchronized void prepararDestino(String nombre, String ip, int puerto) {
        destino = new Destino(nombre, ip, puerto);
        intentando = false;
        conectada = false;
    }

    public synchronized void conectando() {
        if (destino != null && !intentando && !conectada) intentando = true;
    }

    public synchronized void conexionConfirmada() {
        if (destino == null || conectada || !intentando) return;
        conectada = true;
        intentando = false;
        emit(EventoHistorial.Tipo.CONEXION_ESTABLECIDA, EventoHistorial.Resultado.EXITO,
                "Conexión TCP confirmada");
    }

    public synchronized void error() {
        if (destino == null) return;
        if (conectada) {
            emit(EventoHistorial.Tipo.SESION_FINALIZADA, EventoHistorial.Resultado.ERROR,
                    "La conexión activa terminó con error");
        } else if (intentando) {
            emit(EventoHistorial.Tipo.CONEXION_FALLIDA, EventoHistorial.Resultado.ERROR,
                    "No se pudo establecer la conexión TCP");
        } else {
            return;
        }
        intentando = false;
        conectada = false;
    }

    public synchronized void regresoLocal() {
        if (destino == null || !conectada) return;
        emit(EventoHistorial.Tipo.SESION_FINALIZADA, EventoHistorial.Resultado.CIERRE,
                "Control regresó al equipo principal");
        intentando = false;
        conectada = false;
    }

    public synchronized void finalizarModo() {
        if (destino != null && conectada) {
            emit(EventoHistorial.Tipo.SESION_FINALIZADA, EventoHistorial.Resultado.CIERRE,
                    "Modo Rey detenido por el usuario");
        }
        destino = null;
        intentando = false;
        conectada = false;
    }

    private void emit(EventoHistorial.Tipo type, EventoHistorial.Resultado result, String detail) {
        receptor.aceptar(new EventoHistorial(UUID.randomUUID(), clock.instant(),
                destino.nombre, destino.ip, destino.puerto, type, result, detail));
    }

    private static final class Destino {
        final String nombre;
        final String ip;
        final int puerto;

        Destino(String nombre, String ip, int puerto) {
            this.nombre = nombre;
            this.ip = ip;
            this.puerto = puerto;
        }
    }
}
