package Absolute_Control.equipos;

/** Error comprobable de lectura, formato o escritura del almacén de equipos. */
public final class PersistenciaEquiposException extends Exception {
    public PersistenciaEquiposException(String message) {
        super(message);
    }

    public PersistenciaEquiposException(String message, Throwable cause) {
        super(message, cause);
    }
}
