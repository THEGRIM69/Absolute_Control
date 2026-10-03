# Fase 1: conexiones LAN

## Objetivo

Establecer el control de una PC secundaria desde la principal utilizando la misma red local. Este documento explica la base de conexión según el código actual; el endurecimiento posterior se detalla en [Fase 1.1](Fase-1.1-Estabilidad-Concurrencia.md).

## Arquitectura

```text
PC PRINCIPAL
     |
JNativeHook -> MouseHandler / KeyboardHandler
     |
   Cliente
     |
 TCP: eventos y control de sesión
     |
  Servidor
     |
java.awt.Robot
     |
PC SECUNDARIA (con su propio monitor)
```

Discovery utiliza UDP para encontrar el servidor y conocer su puerto TCP. También existe entrada manual de IP. No hay un canal de video.

## Funcionamiento

1. Se inicia el servidor en la secundaria y se activa el modo cliente en la principal. La interfaz los denomina Esclavo y Rey, respectivamente.
2. Activar el cliente registra los handlers; todavía no implica tener una conexión TCP.
3. Un cruce válido del borde configurado solicita la conexión. La conexión se abre en segundo plano y espera las limpiezas anteriores cuando corresponde.
4. El cliente transmite la entrada inicial y después los eventos. El servidor los reproduce con `Robot`.
5. Escape solicita el cierre desde la principal. El borde de regreso de la secundaria produce `REGRESAR` y termina el control remoto.
6. Un nuevo cruce válido puede solicitar otra conexión. No existe reconexión automática por una caída.

## Componentes principales

Las rutas enlazadas pertenecen a la aplicación integrada:

| Clase | Responsabilidad |
|---|---|
| [Main.java](../src/Absolute_Control/Main.java) | Interfaz Swing, modos, configuración, búsqueda y coordinación de inicio/parada. |
| [Cliente.java](../src/Absolute_Control/core/Cliente.java) | TCP desde la principal, sesiones, envío, recepción, heartbeat y limpieza. |
| [Servidor.java](../src/Absolute_Control/core/Servidor.java) | Escucha TCP en la secundaria, reproducción con Robot, borde de regreso y liberación de entradas. |
| [Discovery.java](../src/Absolute_Control/core/Discovery.java) | Búsqueda por broadcast UDP y respuesta del servidor. |
| [MouseHandler.java](../src/Absolute_Control/input/MouseHandler.java) | Cruce del borde, deltas, botones, rueda y anclaje del cursor local. |
| [KeyboardHandler.java](../src/Absolute_Control/input/KeyboardHandler.java) | Eventos de teclado, caracteres, mapas existentes y Escape. |

## Comunicación

| Canal | Puerto | Uso |
|---|---|---|
| TCP | Configurable; normalmente `8080`, sugerido por la interfaz | Eventos y mensajes de la sesión. |
| UDP Discovery | `8079` | Descubrir la IP del servidor y su puerto TCP. |

Discovery envía `ABSOLUTE_CONTROL_DISCOVER` y recibe `ABSOLUTE_CONTROL_HERE,<puertoTCP>`. No establece por sí mismo una sesión de control.

TCP utiliza líneas UTF-8. El código actual reconoce estos mensajes:

| Dirección | Sintaxis | Efecto |
|---|---|---|
| Cliente → servidor | `ENTRAR,DERECHA,0.7` o `ENTRAR,IZQUIERDA,0.7` | Colocar el cursor según lado y altura relativa; añadido en Fase 2. |
| Cliente → servidor | `D,dx,dy` | Movimiento relativo normal. |
| Cliente → servidor | `A,x,y` | Posición absoluta aceptada por el servidor; el movimiento normal no usa esta ruta. |
| Cliente → servidor | `C,PRESIONAR,b` / `C,LIBERAR,b` | Botones enviados actualmente: `1` izquierdo y `3` derecho. |
| Cliente → servidor | `W,delta` | Rueda del mouse. |
| Cliente → servidor | `K,PRESIONAR,codigo` / `K,LIBERAR,codigo` | Tecla mediante el mapa existente del servidor. |
| Cliente → servidor | `T,caracter` | Reproducción de un carácter; no es un protocolo general de texto con escape de delimitadores. |
| Cliente → servidor | `PING` | Solicitar respuesta de heartbeat. |
| Servidor → cliente | `PONG` | Actualizar la comprobación de conexión del cliente. |
| Servidor → cliente | `REGRESAR,altura` o `REGRESAR` | Recuperar control local y cerrar sesión. |
| Cliente → servidor | `LIBERAR` | Mensaje aceptado por el servidor para terminar la sesión. |

El cliente integrado actual cierra TCP al desconectar o usar Escape; no necesita enviar `LIBERAR`. Se aceptan variantes sin altura para compatibilidad, pero no hay negociación de versiones: ambas PCs deben usar la implementación integrada compatible.
