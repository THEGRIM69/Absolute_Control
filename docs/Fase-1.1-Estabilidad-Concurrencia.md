# Fase 1.1: estabilidad y concurrencia

## Objetivo

Evitar que una desconexión deje al usuario atrapado en control remoto, con entradas presionadas o recursos de una sesión anterior interfiriendo con otra.

## Problemas que se corrigieron

- ✅ EOF, reset TCP, conexión rechazada y pérdida de comunicación.
- ✅ Detección de un servidor silencioso mediante heartbeat.
- ✅ Cierre concurrente y limpieza que termina después del timeout inicial.
- ✅ Sesiones consecutivas y eventos antiguos A → B.
- ✅ Reinicio del servidor y de Discovery después de terminar la parada.
- ✅ Liberación de teclas y botones pendientes, con reintentos ante fallos transitorios.
- ✅ Espera y comprobación de terminación de hilos.

## Comportamiento actual

```text
Control remoto
      |
Se detecta pérdida o se solicita cierre
      |
Se invalida la sesión y su generación
      |
Mouse y teclado dejan de enviar; control local
      |
Un propietario de limpieza cierra recursos
      |
Si hay demora, la limpieza continúa en segundo plano
      |
Se comprueba de nuevo su terminación real
      |
Un nuevo cruce puede activar otra sesión
```

**Cierre iniciado y limpieza terminada son estados distintos.** El cliente marca `cerrada` y el servidor usa `cierreSolicitado`; `limpiezaTerminada` se publica cuando finalizan sus tareas. Un `CountDownLatch` notifica la salida del limpiador, y las rutas de espera también comprueban el resultado y la terminación del hilo.

Cada sesión tiene un único propietario de limpieza. Primero se cierra el socket para desbloquear I/O, después se interrumpen y esperan los hilos y finalmente se cierran los streams. En el servidor también se liberan las entradas registradas.

`esperarDetenido()` hace una comprobación acotada: devolver `false` no abandona la limpieza. El propietario sigue esperando y reintentando; una comprobación posterior puede confirmar su finalización. Una nueva petición del cliente puede quedar esperando, pero no activa control remoto hasta que termine la limpieza anterior. El servidor tampoco atiende una nueva sesión mientras esa limpieza siga activa.

La parada del servidor cubre cliente activo, ServerSocket, Discovery, monitor de borde y los hilos relacionados. El reinicio comprueba que los hilos anteriores hayan terminado. Main coordina las esperas de parada en un trabajador de fondo; no hace esos joins en el hilo de Swing.

## Heartbeat y tiempos actuales

Constantes verificadas en [Cliente.java](../src/Absolute_Control/core/Cliente.java), [Servidor.java](../src/Absolute_Control/core/Servidor.java) y [MouseHandler.java](../src/Absolute_Control/input/MouseHandler.java):

| Constante | Valor | Uso |
|---|---|---|
| `CONNECT_TIMEOUT_MS` | 2000 ms | Intento de conexión TCP. |
| `PING_INTERVAL_MS` | 1000 ms | Periodicidad programada de PING. |
| `HEARTBEAT_TIMEOUT_MS` | 3000 ms | Límite sin PONG antes de recuperar control local. |
| `WATCHDOG_INTERVAL_MS` | 100 ms | Intervalo de comprobación del cliente. |
| `SESSION_IDLE_TIMEOUT_MS` | 4000 ms | Timeout de lectura del servidor. |
| `JOIN_TIMEOUT_MS` | 1500 ms | Esperas acotadas; el limpiador continúa si vencen. |
| `LOCAL_SETTLE_MS` | 200 ms | Enfriamiento local tras desactivar control remoto. |
| `EXIT_DWELL_MS` | 120 ms | Tiempo fuera del borde requerido para rearmarlo tras una caída. |

El escritor de la sesión da prioridad temporal a PING frente a los mensajes encolados. PONG actualiza `ultimoPong`; el vigilante cierra esa sesión si se agota el tiempo. Estos hilos se crean para una sesión activa y se detienen con su limpieza. Los valores son umbrales de programación, no garantías de latencia exacta del sistema.

En la integración final de Fase 2, PING no toma `entradaLock` ni inicializa el mouse: responde PONG bajo el bloqueo de salida de la sesión. La posición remota se inicializa mediante `ENTRAR` o, para compatibilidad con emisores antiguos, mediante el primer comando de entrada real.

Después de una caída se descartan eventos de enfriamiento, reposicionamientos propios y coordenadas atrasadas. Se exige un nuevo cruce válido; no hay reconexión automática. El regreso intencional con `REGRESAR` tiene la recolocación y el rearme descritos en [Fase 2](Fase-2-Transicion-Pantallas.md).

## Aislamiento de sesiones

`InputDispatcher` marca generación e instante al encolar los callbacks nativos. Cambiar o cerrar la sesión invalida la generación y descarta la cola anterior. Los handlers capturan el emisor de su sesión; el envío y Escape vuelven a comprobar que esa sesión y generación siguen vigentes.

Los bloqueos `synchronized` protegen cambios de sesión y de handlers; `AtomicLong` identifica generaciones, `AtomicBoolean` coordina el cierre único del servidor y campos `volatile` publican señales entre hilos. Una sesión antigua no debe enviar ni desconectar la nueva.

## Liberación de entradas

El servidor registra teclas y botones por sesión bajo `entradaLock`. Al terminar intenta liberar únicamente las entradas registradas en esa sesión. Si una liberación falla, el registro permanece pendiente y la limpieza vuelve a intentarla. No se habilita otra sesión antes de completar esa liberación.

Esto protege las entradas remotas registradas por la aplicación; no representa una limpieza indiscriminada de todo el teclado del sistema.

## Pruebas

[StabilityCheck.java](../tests/StabilityCheck.java) se ejecuta mediante [run-stability.ps1](../tests/run-stability.ps1), desde PowerShell en la raíz:

```powershell
.\tests\run-stability.ps1
```

✅ Validación final registrada el **2026-10-03**, sobre los archivos actuales:

- Compilación con `--release 17` y suite terminada con código **0**.
- Cierre concurrente, incluidos 30 ciclos repetidos.
- Limpieza tardía, revalidación y cierre de streams.
- Aislamiento A → B, incluso callbacks ya iniciados.
- Heartbeat, EOF, reset TCP y recuperación local.
- Discovery, reinicios y liberación de entradas.
- **0 hilos `kvm-` vivos al finalizar.**

Son pruebas locales con sockets y dobles de entrada/Robot donde corresponde. La ejecución usa un entorno gráfico para las partes que crean Robot. 🟡 No sustituye una prueba de uso entre dos PCs físicas ni certifica todas las distribuciones de teclado.
