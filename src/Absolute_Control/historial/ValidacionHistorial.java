package Absolute_Control.historial;

import Absolute_Control.equipos.ValidadorEquipo;

/** Reglas de integridad de los eventos persistidos. */
public final class ValidacionHistorial {
    private ValidacionHistorial() {}

    public static void validar(EventoHistorial event) throws HistorialException {
        if (event == null) throw new HistorialException("El evento no puede ser nulo");
        if (event.getNombreEquipo().trim().isEmpty()) {
            throw new HistorialException("El nombre histórico no puede estar vacío");
        }
        if (!ValidadorEquipo.esIpv4Valida(event.getIp())) {
            throw new HistorialException("La IPv4 histórica no es válida");
        }
        if (event.getPuerto() < 1 || event.getPuerto() > 65535) {
            throw new HistorialException("El puerto histórico no es válido");
        }
        if (event.getDetalle().length() > 500) {
            throw new HistorialException("El detalle histórico es demasiado largo");
        }
    }
}
