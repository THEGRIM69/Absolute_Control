package Absolute_Control.equipos;

/** Indica que los datos suministrados para un equipo no son válidos. */
public final class ValidacionEquipoException extends Exception {
    public ValidacionEquipoException(String message) {
        super(message);
    }
}
