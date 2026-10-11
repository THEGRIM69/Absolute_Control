package Absolute_Control.equipos;

/** Reglas de validación compartidas por servicio y persistencia. */
public final class ValidadorEquipo {
    private ValidadorEquipo() {}

    public static void validar(String nombre, String ip, int puerto)
            throws ValidacionEquipoException {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new ValidacionEquipoException("El nombre del equipo no puede estar vacío");
        }
        if (!esIpv4Valida(ip)) {
            throw new ValidacionEquipoException("La dirección IPv4 no es válida");
        }
        if (puerto < 1 || puerto > 65535) {
            throw new ValidacionEquipoException("El puerto debe estar entre 1 y 65535");
        }
    }

    public static boolean esIpv4Valida(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        int octetos = 0;
        int valor = 0;
        int digitos = 0;
        for (int i = 0; i <= ip.length(); i++) {
            char caracter = i < ip.length() ? ip.charAt(i) : '.';
            if (caracter == '.') {
                if (digitos == 0 || valor > 255) return false;
                octetos++;
                valor = 0;
                digitos = 0;
            } else if (caracter >= '0' && caracter <= '9') {
                if (++digitos > 3) return false;
                valor = valor * 10 + (caracter - '0');
            } else {
                return false;
            }
        }
        return octetos == 4;
    }
}
