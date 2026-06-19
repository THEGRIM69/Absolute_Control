package Absolute_Control.core;

import java.net.*;
import java.util.function.Consumer;

/**
 * Autodiscovery por broadcast UDP en la red local.
 *
 * Protocolo simple de 1 puerto fijo conocido (DISCOVERY_PORT):
 *  - El Cliente (Rey) manda un broadcast con el mensaje DISCOVER_MSG.
 *  - El Servidor (Esclavo) escucha ese puerto; al recibir DISCOVER_MSG,
 *    responde por unicast directo al que preguntó con:
 *        "ABSOLUTE_CONTROL_HERE,<puertoTcpReal>"
 *  - El Cliente lee esa respuesta y obtiene la IP real (de la cabecera
 *    del paquete UDP recibido) y el puerto TCP a usar, sin que el
 *    usuario haya escrito ninguna IP a mano.
 *
 * Limitación esperada: el broadcast UDP no cruza routers/subredes
 * distintas. Solo funciona entre PCs en la misma red local (mismo
 * WiFi/switch). Para conexiones entre redes distintas, sigue
 * existiendo el campo de IP manual como respaldo.
 */
public class Discovery {

    private static final int    DISCOVERY_PORT = 8079;
    private static final String DISCOVER_MSG   = "ABSOLUTE_CONTROL_DISCOVER";
    private static final String RESPONSE_PREFIX = "ABSOLUTE_CONTROL_HERE,";

    // ── Lado Servidor: escucha y responde ──────────────────────────

    private DatagramSocket   socketEscucha;
    private volatile boolean escuchando = false;

    /**
     * Arranca un hilo daemon que escucha broadcasts de descubrimiento
     * y responde con el puerto TCP real del servidor.
     *
     * @param puertoTcp puerto TCP real en el que está el ServerSocket
     * @param logger    callback de log (puede ser null)
     */
    public void iniciarResponder(int puertoTcp, Consumer<String> logger) {
        Thread hilo = new Thread(() -> {
            try {
                socketEscucha = new DatagramSocket(DISCOVERY_PORT);
                socketEscucha.setBroadcast(true);
                escuchando = true;
                if (logger != null) logger.accept("Discovery UDP escuchando en puerto " + DISCOVERY_PORT);

                byte[] buffer = new byte[256];
                while (escuchando) {
                    DatagramPacket recibido = new DatagramPacket(buffer, buffer.length);
                    try {
                        socketEscucha.receive(recibido);
                    } catch (SocketException se) {
                        break; // socket cerrado al detener
                    }

                    String mensaje = new String(
                            recibido.getData(), 0, recibido.getLength()).trim();

                    if (mensaje.equals(DISCOVER_MSG)) {
                        InetAddress origen = recibido.getAddress();
                        int         puertoOrigen = recibido.getPort();

                        String respuesta = RESPONSE_PREFIX + puertoTcp;
                        byte[] datosResp = respuesta.getBytes();
                        DatagramPacket paqueteResp = new DatagramPacket(
                                datosResp, datosResp.length, origen, puertoOrigen);
                        socketEscucha.send(paqueteResp);

                        if (logger != null) {
                            logger.accept("Discovery: respondido a " + origen.getHostAddress());
                        }
                    }
                }
            } catch (Exception e) {
                if (escuchando && logger != null) {
                    logger.accept("Discovery error: " + e.getMessage());
                }
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Detiene el listener de discovery del lado servidor. */
    public void detenerResponder() {
        escuchando = false;
        if (socketEscucha != null) {
            socketEscucha.close();
        }
    }

    // ── Lado Cliente: pregunta y espera respuesta ──────────────────

    /**
     * Resultado de una búsqueda de servidor exitosa.
     */
    public static class ServidorEncontrado {
        public final String ip;
        public final int    puertoTcp;

        public ServidorEncontrado(String ip, int puertoTcp) {
            this.ip        = ip;
            this.puertoTcp = puertoTcp;
        }
    }

    /**
     * Manda un broadcast a la red local preguntando por un servidor y
     * espera una respuesta hasta el timeout indicado. Llamado de forma
     * BLOQUEANTE — el llamador debe invocarlo desde un hilo de fondo,
     * nunca desde el Event Dispatch Thread de Swing.
     *
     * @param timeoutMs milisegundos máximos a esperar una respuesta
     * @return el servidor encontrado, o null si no hubo respuesta a tiempo
     */
    public static ServidorEncontrado buscarServidor(int timeoutMs) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(timeoutMs);

            byte[] datos = DISCOVER_MSG.getBytes();

            // Mandamos a la dirección de broadcast limitado (255.255.255.255)
            // y también a la de broadcast de cada interfaz local, porque en
            // Windows el broadcast genérico a veces no sale por todos los
            // adaptadores de red activos.
            for (InetAddress destino : direccionesBroadcast()) {
                try {
                    DatagramPacket paquete = new DatagramPacket(
                            datos, datos.length, destino, DISCOVERY_PORT);
                    socket.send(paquete);
                } catch (Exception ignored) {
                    // Si falla un adaptador en particular, seguimos con los demás
                }
            }

            byte[] buffer = new byte[256];
            DatagramPacket respuesta = new DatagramPacket(buffer, buffer.length);
            socket.receive(respuesta); // bloquea hasta timeoutMs

            String texto = new String(respuesta.getData(), 0, respuesta.getLength()).trim();
            if (texto.startsWith(RESPONSE_PREFIX)) {
                int puertoTcp = Integer.parseInt(texto.substring(RESPONSE_PREFIX.length()));
                return new ServidorEncontrado(respuesta.getAddress().getHostAddress(), puertoTcp);
            }
            return null;

        } catch (SocketTimeoutException e) {
            return null; // nadie respondió a tiempo
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Junta las direcciones de broadcast de todas las interfaces de red
     * activas, más 255.255.255.255 como respaldo genérico.
     */
    private static java.util.List<InetAddress> direccionesBroadcast() {
        java.util.List<InetAddress> resultado = new java.util.ArrayList<>();
        try {
            resultado.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) {}

        try {
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InterfaceAddress dir : ni.getInterfaceAddresses()) {
                    InetAddress broadcast = dir.getBroadcast();
                    if (broadcast != null) resultado.add(broadcast);
                }
            }
        } catch (Exception ignored) {}

        return resultado;
    }
}