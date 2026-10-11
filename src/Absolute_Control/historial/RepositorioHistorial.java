package Absolute_Control.historial;

import java.util.List;

public interface RepositorioHistorial {
    List<EventoHistorial> cargar() throws HistorialException;
    void guardar(List<EventoHistorial> eventos) throws HistorialException;
}
