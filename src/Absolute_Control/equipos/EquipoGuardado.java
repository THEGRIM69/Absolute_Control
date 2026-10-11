package Absolute_Control.equipos;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Modelo inmutable de un equipo remoto guardado explícitamente por el usuario. */
public final class EquipoGuardado {
    private final UUID id;
    private final String nombre;
    private final String ip;
    private final int puerto;
    private final Instant creadoEn;
    private final Instant modificadoEn;

    public EquipoGuardado(UUID id, String nombre, String ip, int puerto,
                          Instant creadoEn, Instant modificadoEn) {
        this.id = Objects.requireNonNull(id, "id");
        this.nombre = Objects.requireNonNull(nombre, "nombre");
        this.ip = Objects.requireNonNull(ip, "ip");
        this.puerto = puerto;
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
        this.modificadoEn = Objects.requireNonNull(modificadoEn, "modificadoEn");
    }

    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public String getIp() { return ip; }
    public int getPuerto() { return puerto; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getModificadoEn() { return modificadoEn; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EquipoGuardado)) return false;
        EquipoGuardado equipo = (EquipoGuardado) other;
        return puerto == equipo.puerto
                && id.equals(equipo.id)
                && nombre.equals(equipo.nombre)
                && ip.equals(equipo.ip)
                && creadoEn.equals(equipo.creadoEn)
                && modificadoEn.equals(equipo.modificadoEn);
    }

    @Override public int hashCode() {
        return Objects.hash(id, nombre, ip, puerto, creadoEn, modificadoEn);
    }
}
