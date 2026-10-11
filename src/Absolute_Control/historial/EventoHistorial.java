package Absolute_Control.historial;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Copia inmutable de un hecho real de conexión KVM. */
public final class EventoHistorial {
    public enum Tipo { CONEXION_ESTABLECIDA, CONEXION_FALLIDA, SESION_FINALIZADA }
    public enum Resultado { EXITO, ERROR, CIERRE }

    private final UUID id;
    private final Instant fecha;
    private final String nombreEquipo;
    private final String ip;
    private final int puerto;
    private final Tipo tipo;
    private final Resultado resultado;
    private final String detalle;

    public EventoHistorial(UUID id, Instant fecha, String nombreEquipo, String ip, int puerto,
                           Tipo tipo, Resultado resultado, String detalle) {
        this.id = Objects.requireNonNull(id, "id");
        this.fecha = Objects.requireNonNull(fecha, "fecha");
        this.nombreEquipo = Objects.requireNonNull(nombreEquipo, "nombreEquipo");
        this.ip = Objects.requireNonNull(ip, "ip");
        this.puerto = puerto;
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.resultado = Objects.requireNonNull(resultado, "resultado");
        this.detalle = detalle == null ? "" : detalle;
    }

    public UUID getId() { return id; }
    public Instant getFecha() { return fecha; }
    public String getNombreEquipo() { return nombreEquipo; }
    public String getIp() { return ip; }
    public int getPuerto() { return puerto; }
    public Tipo getTipo() { return tipo; }
    public Resultado getResultado() { return resultado; }
    public String getDetalle() { return detalle; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EventoHistorial)) return false;
        EventoHistorial event = (EventoHistorial) other;
        return puerto == event.puerto && id.equals(event.id) && fecha.equals(event.fecha)
                && nombreEquipo.equals(event.nombreEquipo) && ip.equals(event.ip)
                && tipo == event.tipo && resultado == event.resultado
                && detalle.equals(event.detalle);
    }

    @Override public int hashCode() {
        return Objects.hash(id, fecha, nombreEquipo, ip, puerto, tipo, resultado, detalle);
    }
}
