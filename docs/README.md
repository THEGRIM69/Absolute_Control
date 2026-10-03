# Estado actual del proyecto

KVM-Network-Share es una aplicación KVM por red local: permite usar el teclado y el mouse de una PC principal para controlar una PC secundaria que tiene su propio monitor. **Actualmente no captura ni transmite pantalla.**

Esta documentación describe la aplicación integrada de `src/Absolute_Control/`. El código antiguo de `kvm-client/src/` y `kvm-server/src/` permanece separado y no recibió los cambios de estas fases.

La aplicación utiliza Java, Swing para la interfaz, JNativeHook para recibir eventos locales y `java.awt.Robot` para reproducirlos en la secundaria. La compilación actual se validó con target Java 17.

## Estado actual

- ✅ Control de mouse por red local, mediante desplazamientos relativos.
- ✅ Control de teclado por red local, con los mapas existentes.
- ✅ Cambio de control mediante bordes de pantalla.
- ✅ Descubrimiento del servidor mediante UDP.
- ✅ Comunicación de eventos mediante TCP.
- ✅ Recuperación del control local ante desconexiones.
- ✅ Heartbeat de conexión con PING/PONG.
- ✅ Reinicio de servidor y Discovery después de terminar su limpieza.
- ✅ Transición con secundaria a izquierda o derecha.
- ✅ Geometría de una pantalla por PC, calculada según su resolución.
- ✅ Conservación aproximada de la altura al cruzar y regresar.
- ✅ Suites automáticas de estabilidad y transición.

## Todavía no implementado

- ❌ Portapapeles compartido real.
- ❌ Transferencia de archivos.
- ❌ Multi-monitor.
- ❌ Transmisión de pantalla.
- ❌ Acceso remoto por Internet.
- ❌ Aplicación Android.
- ❌ Emparejamiento, autenticación y cifrado avanzado.

El servidor utiliza temporalmente su portapapeles local para reproducir ciertos caracteres. Eso no sincroniza los portapapeles de las dos PCs.

## Validación registrada

En la validación final del **2026-10-03** se compilaron los 13 archivos Java de aplicación, pruebas y código antiguo con `--release 17`. Ambas suites se ejecutaron sobre los archivos actuales y terminaron con código 0 y **0 hilos `kvm-` vivos** al finalizar. Los hashes de los archivos antes y después de la validación coincidieron.

🟡 La validación definitiva en dos PCs físicas sigue pendiente. Las pruebas locales no certifican todos los teclados, escalados de pantalla o condiciones de una LAN real.

## Índice

- [Fase 1: conexiones LAN](Fase-1-Conexiones.md)
- [Fase 1.1: estabilidad y concurrencia](Fase-1.1-Estabilidad-Concurrencia.md)
- [Fase 2: transición entre pantallas](Fase-2-Transicion-Pantallas.md)
- [Roadmap: implementado y planeado](Roadmap.md)
