import Absolute_Control.equipos.EquipoGuardado;
import Absolute_Control.equipos.PersistenciaEquiposException;
import Absolute_Control.equipos.RepositorioEquiposJson;
import Absolute_Control.equipos.ServicioEquipos;
import Absolute_Control.equipos.ValidacionEquipoException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public final class EquipmentPersistenceCheck {
    private static int pruebas;

    public static void main(String[] args) throws Exception {
        Path raiz = Files.createTempDirectory("AbsoluteControl-equipos-");
        Clock reloj = Clock.fixed(Instant.parse("2026-10-11T18:00:00Z"), ZoneOffset.UTC);

        Path principal = raiz.resolve("principal/equipos.json");
        ServicioEquipos servicio = servicio(principal, reloj);
        comprobar(servicio.listar().isEmpty(), "lista inicialmente vacía y archivo inexistente");

        EquipoGuardado laptop = servicio.guardar("Laptop", "192.168.1.198", 8080);
        comprobar(Files.isRegularFile(principal) && servicio.listar().size() == 1,
                "guardado manual crea el archivo");

        ServicioEquipos recargado = servicio(principal, reloj);
        comprobar(recargado.listar().equals(servicio.listar()),
                "persistencia después de recrear el servicio");

        EquipoGuardado renombrado = recargado.editar(
                laptop.getId(), "Portátil trabajo", laptop.getIp(), laptop.getPuerto());
        comprobar(renombrado.getId().equals(laptop.getId())
                        && renombrado.getCreadoEn().equals(laptop.getCreadoEn())
                        && renombrado.getNombre().equals("Portátil trabajo"),
                "edición de nombre conserva identidad y creación");

        EquipoGuardado direccionEditada = recargado.editar(
                laptop.getId(), renombrado.getNombre(), "10.0.0.24", 65535);
        comprobar(direccionEditada.getIp().equals("10.0.0.24")
                        && direccionEditada.getPuerto() == 65535,
                "edición de IPv4 y puerto");

        EquipoGuardado segundo = recargado.guardar("Equipo 2", "172.16.0.2", 9000);
        comprobar(!segundo.getId().equals(laptop.getId()), "identificadores únicos");

        EquipoGuardado segundoAntes = recargado.listar().get(1);
        recargado.editar(laptop.getId(), "Equipo principal", "10.0.0.25", 8081);
        comprobar(recargado.listar().get(1).equals(segundoAntes),
                "edición conserva registros no modificados");

        comprobar(recargado.eliminar(laptop.getId())
                        && recargado.listar().size() == 1
                        && recargado.listar().get(0).getId().equals(segundo.getId())
                        && !recargado.eliminar(UUID.randomUUID()),
                "eliminación por identificador");

        esperarValidacion(() -> recargado.guardar("IP mala", "999.1.2.3", 8080),
                "rechazo de IPv4 inválida");
        esperarValidacion(() -> recargado.guardar("Puerto malo", "127.0.0.1", 0),
                "rechazo de puerto inválido");
        esperarValidacion(() -> recargado.guardar("   ", "127.0.0.1", 8080),
                "rechazo de nombre vacío");

        Path escapado = raiz.resolve("escapado/equipos.json");
        ServicioEquipos servicioEscapado = servicio(escapado, reloj);
        EquipoGuardado especial = servicioEscapado.guardar("PC \"Casa\" \\ Norte", "127.0.0.1", 1);
        comprobar(servicio(escapado, reloj).listar().get(0).equals(especial),
                "JSON conserva caracteres escapados");

        Path corrupto = raiz.resolve("corrupto/equipos.json");
        Files.createDirectories(corrupto.getParent());
        String contenidoCorrupto = "{ \"version\": 1, \"equipos\": [";
        Files.writeString(corrupto, contenidoCorrupto, StandardCharsets.UTF_8);
        esperarPersistencia(() -> servicio(corrupto, reloj), "rechazo de JSON corrupto");
        comprobar(Files.readString(corrupto, StandardCharsets.UTF_8).equals(contenidoCorrupto),
                "JSON corrupto no se sobrescribe ni elimina");

        Path bloqueador = raiz.resolve("no-es-directorio");
        Files.writeString(bloqueador, "bloqueo", StandardCharsets.UTF_8);
        ServicioEquipos sinEscritura = servicio(bloqueador.resolve("equipos.json"), reloj);
        esperarPersistencia(() -> sinEscritura.guardar("No guardar", "127.0.0.1", 8080),
                "fallo de escritura se reporta");
        comprobar(sinEscritura.listar().isEmpty(),
                "fallo de escritura no informa éxito ni altera memoria");

        Path duplicados = raiz.resolve("duplicados/equipos.json");
        Files.createDirectories(duplicados.getParent());
        String id = UUID.randomUUID().toString();
        String registro = "{\"id\":\"" + id + "\",\"nombre\":\"Duplicado\","
                + "\"ip\":\"127.0.0.1\",\"puerto\":8080,"
                + "\"creadoEn\":\"2026-10-11T18:00:00Z\","
                + "\"modificadoEn\":\"2026-10-11T18:00:00Z\"}";
        Files.writeString(duplicados,
                "{\"version\":1,\"equipos\":[" + registro + "," + registro + "]}",
                StandardCharsets.UTF_8);
        esperarPersistencia(() -> servicio(duplicados, reloj),
                "prevención de identificadores duplicados al cargar");

        System.out.println("PASS " + pruebas + " comprobaciones de persistencia aisladas");
        System.out.println("Directorio temporal: " + raiz);
    }

    private static ServicioEquipos servicio(Path archivo, Clock reloj)
            throws PersistenciaEquiposException {
        return new ServicioEquipos(new RepositorioEquiposJson(archivo), reloj);
    }

    private static void comprobar(boolean condicion, String descripcion) {
        if (!condicion) throw new AssertionError("FAIL " + descripcion);
        pruebas++;
        System.out.println("PASS " + descripcion);
    }

    private static void esperarValidacion(Accion accion, String descripcion) throws Exception {
        try {
            accion.ejecutar();
            throw new AssertionError("FAIL " + descripcion);
        } catch (ValidacionEquipoException esperado) {
            comprobar(true, descripcion);
        }
    }

    private static void esperarPersistencia(Accion accion, String descripcion) throws Exception {
        try {
            accion.ejecutar();
            throw new AssertionError("FAIL " + descripcion);
        } catch (PersistenciaEquiposException esperado) {
            comprobar(true, descripcion);
        }
    }

    @FunctionalInterface
    private interface Accion {
        void ejecutar() throws Exception;
    }
}
