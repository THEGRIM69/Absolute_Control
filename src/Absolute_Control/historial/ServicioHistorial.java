package Absolute_Control.historial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Mantiene como máximo los 500 eventos más recientes. */
public final class ServicioHistorial {
    public static final int LIMITE = 500;
    private static final Comparator<EventoHistorial> MAS_RECIENTE_PRIMERO =
            Comparator.comparing(EventoHistorial::getFecha).reversed()
                    .thenComparing(event -> event.getId().toString());

    private final RepositorioHistorial repositorio;
    private List<EventoHistorial> eventos;

    public ServicioHistorial(RepositorioHistorial repositorio) throws HistorialException {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.eventos = normalizar(repositorio.cargar());
    }

    public synchronized List<EventoHistorial> listar() {
        return List.copyOf(eventos);
    }

    public synchronized void registrar(EventoHistorial event) throws HistorialException {
        ValidacionHistorial.validar(event);
        for (EventoHistorial existing : eventos) {
            if (existing.getId().equals(event.getId())) return;
        }
        List<EventoHistorial> updated = new ArrayList<>(eventos);
        updated.add(event);
        updated = normalizar(updated);
        repositorio.guardar(updated);
        eventos = updated;
    }

    public synchronized List<EventoHistorial> recargar() throws HistorialException {
        eventos = normalizar(repositorio.cargar());
        return List.copyOf(eventos);
    }

    private static List<EventoHistorial> normalizar(List<EventoHistorial> source)
            throws HistorialException {
        if (source == null) throw new HistorialException("El repositorio devolvió una lista nula");
        Set<UUID> ids = new HashSet<>();
        List<EventoHistorial> normalized = new ArrayList<>(source.size());
        for (EventoHistorial event : source) {
            ValidacionHistorial.validar(event);
            if (!ids.add(event.getId())) throw new HistorialException("Hay eventos duplicados");
            normalized.add(event);
        }
        normalized.sort(MAS_RECIENTE_PRIMERO);
        if (normalized.size() > LIMITE) {
            normalized = new ArrayList<>(normalized.subList(0, LIMITE));
        }
        return normalized;
    }
}
