package Absolute_Control.equipos;

import java.util.List;

/** Abstracción de persistencia, separada de la futura interfaz Swing. */
public interface RepositorioEquipos {
    List<EquipoGuardado> cargar() throws PersistenciaEquiposException;
    void guardar(List<EquipoGuardado> equipos) throws PersistenciaEquiposException;
}
