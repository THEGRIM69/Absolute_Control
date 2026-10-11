package Absolute_Control.equipos;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Casos de uso para guardar, editar, listar y eliminar equipos. */
public final class ServicioEquipos {
    private final RepositorioEquipos repositorio;
    private final Clock reloj;
    private List<EquipoGuardado> equipos;

    public ServicioEquipos(RepositorioEquipos repositorio)
            throws PersistenciaEquiposException {
        this(repositorio, Clock.systemUTC());
    }

    public ServicioEquipos(RepositorioEquipos repositorio, Clock reloj)
            throws PersistenciaEquiposException {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.equipos = validarCarga(repositorio.cargar());
    }

    public synchronized List<EquipoGuardado> listar() {
        return List.copyOf(equipos);
    }

    /** Único punto de creación; debe invocarse desde una acción GUARDAR explícita. */
    public synchronized EquipoGuardado guardar(String nombre, String ip, int puerto)
            throws ValidacionEquipoException, PersistenciaEquiposException {
        String nombreNormalizado = normalizarNombre(nombre);
        String ipNormalizada = normalizarIp(ip);
        ValidadorEquipo.validar(nombreNormalizado, ipNormalizada, puerto);

        Set<UUID> existentes = new HashSet<>();
        for (EquipoGuardado equipo : equipos) existentes.add(equipo.getId());
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (existentes.contains(id));

        Instant ahora = reloj.instant();
        EquipoGuardado nuevo = new EquipoGuardado(
                id, nombreNormalizado, ipNormalizada, puerto, ahora, ahora);
        List<EquipoGuardado> actualizados = new ArrayList<>(equipos);
        actualizados.add(nuevo);
        persistirYConfirmar(actualizados);
        return nuevo;
    }

    public synchronized EquipoGuardado editar(UUID id, String nombre, String ip, int puerto)
            throws ValidacionEquipoException, PersistenciaEquiposException {
        Objects.requireNonNull(id, "id");
        String nombreNormalizado = normalizarNombre(nombre);
        String ipNormalizada = normalizarIp(ip);
        ValidadorEquipo.validar(nombreNormalizado, ipNormalizada, puerto);

        List<EquipoGuardado> actualizados = new ArrayList<>(equipos);
        for (int i = 0; i < actualizados.size(); i++) {
            EquipoGuardado anterior = actualizados.get(i);
            if (anterior.getId().equals(id)) {
                EquipoGuardado editado = new EquipoGuardado(id, nombreNormalizado,
                        ipNormalizada, puerto, anterior.getCreadoEn(), reloj.instant());
                actualizados.set(i, editado);
                persistirYConfirmar(actualizados);
                return editado;
            }
        }
        throw new IllegalArgumentException("No existe un equipo con el identificador indicado");
    }

    public synchronized boolean eliminar(UUID id) throws PersistenciaEquiposException {
        Objects.requireNonNull(id, "id");
        List<EquipoGuardado> actualizados = new ArrayList<>(equipos);
        boolean eliminado = actualizados.removeIf(equipo -> equipo.getId().equals(id));
        if (!eliminado) return false;
        persistirYConfirmar(actualizados);
        return true;
    }

    private void persistirYConfirmar(List<EquipoGuardado> actualizados)
            throws PersistenciaEquiposException {
        repositorio.guardar(List.copyOf(actualizados));
        equipos = actualizados;
    }

    private static List<EquipoGuardado> validarCarga(List<EquipoGuardado> cargados)
            throws PersistenciaEquiposException {
        if (cargados == null) {
            throw new PersistenciaEquiposException("El repositorio devolvió una lista nula");
        }
        Set<UUID> ids = new HashSet<>();
        for (EquipoGuardado equipo : cargados) {
            if (equipo == null || !ids.add(equipo.getId())) {
                throw new PersistenciaEquiposException("El archivo contiene identificadores duplicados o nulos");
            }
            try {
                ValidadorEquipo.validar(equipo.getNombre(), equipo.getIp(), equipo.getPuerto());
            } catch (ValidacionEquipoException error) {
                throw new PersistenciaEquiposException("El repositorio contiene un equipo inválido", error);
            }
            if (equipo.getModificadoEn().isBefore(equipo.getCreadoEn())) {
                throw new PersistenciaEquiposException("El repositorio contiene fechas incoherentes");
            }
        }
        return new ArrayList<>(cargados);
    }

    private static String normalizarNombre(String nombre) {
        return nombre == null ? null : nombre.trim();
    }

    private static String normalizarIp(String ip) {
        return ip == null ? null : ip.trim();
    }
}
