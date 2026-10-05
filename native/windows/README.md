# Fase 3A: prototipo aislado de hooks Windows

## Alcance

Este directorio contiene un helper `.exe` nativo e independiente. No carga la
JVM, no usa JNI/JNA/JNativeHook, no abre sockets y no modifica el protocolo de
Absolute Control. El prototipo sólo valida captura, supresión local y
recuperación segura en Windows.

No se usa `BlockInput`: la supresión se decide evento por evento devolviendo un
valor distinto de cero desde `WH_KEYBOARD_LL` o `WH_MOUSE_LL`.

## Arquitectura

```text
Windows
  │ eventos low-level
  ▼
hilo de hooks + message loop ──► cola SPSC fija ──► hilo reporter ──► consola
  │                                (sin esperas)       (logs fuera del callback)
  │
  ├─ decisión LOCAL/REMOTO y ownership press/release
  ├─ Escape físico: REMOTO → LOCAL inmediatamente
  └─ CallNextHookEx o supresión

hilo de lease ──► deadline REMOTO (1500 ms) ◄── hilo watchdog (25 ms)
                                                        │
draining ───────► deadline DRAINING (750 ms) ───────────┤
                                                        └─ fail-open atómico
```

El helper separado fue elegido frente a una DLL JNI porque un fallo o cierre
del proceso hace que Windows retire sus hooks. Además, el message loop, el
watchdog y el estado crítico no dependen de pausas de la JVM.

El callback no realiza red, disco, esperas ni salida de consola. Sólo clasifica
el evento, actualiza estado pequeño, intenta copiar una estructura POD a una
cola acotada y retorna. El reporter agrega los movimientos de mouse cada 250 ms
para que imprimirlos no produzca backpressure en el hook.

## Modos y política de eventos

La máquina de estados tiene tres estados inequívocos:

| Estado | Eventos físicos nuevos | Ownership remoto anterior | Telemetría |
|---|---|---|---|
| `LOCAL` | Pasan todos. | Ninguno. | `supresion_armada=NO`, pendientes `0`. |
| `REMOTO` | Se capturan y suprimen. | Se crea con cada press físico. | `supresion_armada=SI`. |
| `LOCAL_DRAINING` | Pasan todos. | Sólo releases/repeticiones anteriores pueden suprimirse. | `supresion_armada=LIMITADA`. |

No se envían eventos a otra PC. Al publicar `LOCAL`, la compuerta efectiva está
desarmada y el contador remoto es cero. La consola obtiene modo, compuerta y
contador de un único snapshot atómico, por lo que no combina lecturas de
transiciones distintas.

“Físico” significa, para este prototipo, que Windows **no** marcó el evento con
`LLKHF_INJECTED` o `LLMHF_INJECTED`. Un hook no puede demostrar el origen de
hardware más allá de esas marcas. Los eventos marcados como inyectados:

- se observan y etiquetan `INYECTADO`;
- siempre pasan a Windows, incluso en `REMOTO`;
- no adquieren ownership;
- no activan Escape de emergencia;
- nunca se reinyectan, por lo que el helper no puede crear un bucle propio.

El comando `inject-test` usa `SendInput` únicamente como prueba local. El
criterio de clasificación sigue siendo la marca del hook, no el valor privado
de `dwExtraInfo`.

## Escape y ownership press/release

Un Escape físico presionado en `REMOTO` cambia la política a `LOCAL_DRAINING`
dentro del callback, consume ese press y reserva el release correspondiente
para consumirlo también. El reporter sólo informa la decisión ya tomada; no
participa en la recuperación. En draining, una tecla o click nuevos pertenecen
a LOCAL; movimiento y wheel también pasan inmediatamente.

Cada tecla y cada botón (izquierdo, derecho, medio, X1 y X2) mantiene owner:

- press iniciado en `LOCAL`: sus repeticiones y release pasan a `LOCAL`, aunque
  se active `REMOTO` entre ambos;
- press iniciado en `REMOTO`: sus repeticiones y release se consumen durante
  `LOCAL_DRAINING`;
- al entrar en `REMOTO`, `GetAsyncKeyState` marca como locales las teclas y
  botones que ya estaban físicamente presionados;
- movimiento y wheel no tienen pareja press/release y usan el modo del evento.

Cuando se consume el último release remoto —incluido el release pendiente de
Escape— el mismo callback publica `LOCAL`, contador cero y
`supresion_armada=NO`. Una recuperación por error es distinta: limpia todo
ownership inmediatamente y permite los eventos posteriores. Fail-open tiene
prioridad sobre conservar una pareja remota.

## Lease y fail-open

La lease REMOTO dura **1500 ms**, se renueva cada **250 ms** y el watchdog
comprueba deadlines cada **25 ms**. `LOCAL_DRAINING` tiene un timeout separado
de **750 ms**. Es suficiente para releases humanos normales, pero acota a menos
de un segundo un owner cuyo release se perdió. Al vencer, limpia ownership y
Escape pendiente, desarma la compuerta y publica `LOCAL` directamente.

En este prototipo, un hilo interno representa al futuro
controlador. `remote-timeout` omite deliberadamente la renovación para simular
que Java dejó de responder. La integración futura deberá reemplazar esa
renovación interna por mensajes IPC autenticados al proceso helper; no debe usar
el heartbeat TCP existente.

Ante lease vencida, excepción, error del message loop, EOF/cierre del
controlador o cola llena, no se usa draining: primero se desarma la supresión
mediante un atomic y se publica `LOCAL`; después se pide al hilo del hook que
limpie ownership. Si la propia cola de mensajes está retrasada, el atomic ya
hace que los callbacks devuelvan la entrada a Windows. Al finalizar o matar el
proceso, Windows retira los hooks del proceso.

## Compilar y ejecutar pruebas

Requiere Windows x64 y MSVC con headers/librerías Win32. Desde la raíz del repo:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\native\windows\build-and-test.ps1 -Configuration Release
```

El script:

1. audita APIs requeridas/prohibidas y que los cinco Java protegidos no tengan
   cambios;
2. compila con C++17, `/W4` y `/WX`;
3. ejecuta las pruebas de `InputPolicy` y `BoundedSpscQueue`;
4. compila `out\Release\absolute-control-input-helper.exe`.

También se incluye `CMakeLists.txt` para un entorno CMake/MSVC convencional.
`out/` ya queda cubierto por el patrón `**/out/` del `.gitignore` del proyecto.

Ejecutar el helper:

```powershell
.\native\windows\out\Release\absolute-control-input-helper.exe
```

Comandos disponibles:

| Comando | Efecto |
|---|---|
| `remote` | Cuenta atrás de 3 s y activa `REMOTO` con renovación automática. |
| `remote-timeout` | Activa `REMOTO` sin renovar; vuelve solo en ≤1500 ms. |
| `inject-test` | Activa `REMOTO`, inyecta F24 y dos movimientos neto cero, informa clasificación y vuelve a `LOCAL`. |
| `local` | Transición controlada a `LOCAL_DRAINING` si hay owners pendientes; si no, a `LOCAL`. En `REMOTO` no puede teclearse físicamente; use Escape. |
| `status` | Muestra modo, hooks, lease y contadores. |
| `quit` | Desarma supresión, quita hooks y sale. |

## Plan exacto de prueba manual

Preparación común: guarde su trabajo, cierre aplicaciones sensibles, abra
Notepad con varias líneas y deje visibles Notepad y la consola del helper. No
ejecute la primera prueba sobre una aplicación elevada. Si algo inesperado
ocurre, suelte todas las teclas/botones y pulse Escape físico; cerrar o matar el
helper también elimina sus hooks.

### 1. LOCAL

1. Inicie el helper y confirme `Estado inicial: LOCAL`.
2. En Notepad, escriba `local-123`, mueva el cursor, seleccione texto con click y
   use la rueda.
3. Espere: texto, movimiento, clicks y scroll funcionan normalmente; la consola
   muestra/agrupa copias `LOCAL FISICO`.
4. Si falla, guarde salida completa, versión de Windows, nivel de privilegio de
   helper/Notepad y qué dispositivo/evento no pasó.

### 2. REMOTO

1. En la consola escriba `remote`.
2. Durante la cuenta atrás enfoque Notepad.
3. Mueva el mouse, haga click sobre texto, use wheel y escriba `blocked-123`.
4. Espere: el cursor/selección/scroll/texto de Notepad no cambia; la consola
   informa eventos `REMOTO FISICO` y movimientos con `remotos > 0`.
5. No intente volver a la consola con mouse/teclado: pulse Escape.
6. Si falla, guarde consola completa, acción que atravesó, modo mostrado por
   `status` después de recuperar y privilegios de ambos procesos.

### 3. Escape inmediato

1. Active `remote`, enfoque Notepad y presione y suelte Escape una sola vez.
2. Espere: aparece `REMOTO -> LOCAL_DRAINING`; Notepad no recibe ni press ni
   release de Escape, pero otros inputs nuevos funcionan inmediatamente.
3. Tras soltar Escape ejecute `status`. Debe indicar exactamente
   `modo=LOCAL`, `supresion_armada=NO` y
   `ownership_remoto_pendiente=0`.
4. Escriba `after-escape` en Notepad.
5. Si falla, anote si falló press, release, draining o retorno de input y guarde
   el aviso y `status`.

### 4. Ownership a través de transiciones

1. Durante la cuenta atrás de `remote`, mantenga Shift presionado antes de que
   aparezca `MODO REMOTO`; suéltelo después. Espere: su release llega a LOCAL.
2. Repita manteniendo un botón de mouse antes de activar `REMOTO`; su release no
   debe quedar bloqueado.
3. Ya en `REMOTO`, mantenga Ctrl, pulse y suelte Escape sin soltar Ctrl. En ese
   momento el estado es `LOCAL_DRAINING` con Ctrl pendiente. Suelte Ctrl: su
   release se consume y el estado pasa a `LOCAL` con supresión `NO`.
4. Escriba normalmente y ejecute `status`; debe mostrar pendientes `0`.
5. Repita el paso 3 con un botón de mouse mantenido.
6. Si falla, guarde el orden exacto press/transición/release y las líneas KEY o
   MOUSE correspondientes.

### 5. Watchdog

1. Ejecute `remote-timeout` y enfoque Notepad durante la cuenta atrás.
2. Compruebe brevemente que la entrada queda suprimida.
3. No pulse Escape; espere 1.5 s.
4. Espere: aparece `FAIL-OPEN -> LOCAL: lease vencida` y la entrada funciona de
   nuevo. Verifique escribiendo `after-watchdog`.
5. Si falla, guarde timestamps aproximados, salida completa y `status`.

### 6. Eventos inyectados

1. No toque teclado ni mouse durante esta prueba y ejecute `inject-test`.
2. Espere la cuenta atrás y el retorno automático a `LOCAL`.
3. Espere: `SendInput=4/4`, al menos cuatro eventos observados como inyectados y
   cero físicos remotos adicionales. Los eventos aparecen `INYECTADO`, no como
   captura remota; Escape sintético no se usa.
4. Si falla, guarde el resumen `inject-test`, logs `INYECTADO`, antivirus/EDR y
   nivel de integridad del proceso.

### 7. Cierre normal

1. En `LOCAL`, ejecute `quit`.
2. Espere: aparece `Hooks retirados` y Windows conserva toda la entrada.
3. Repita cerrando la ventana de consola mientras está en `LOCAL`.
4. Si falla, guarde la última salida visible y el código de proceso.

### 8. Muerte forzada durante REMOTO

Use dos PowerShell. En la segunda prepare una terminación con retardo:

```powershell
Start-Sleep -Seconds 8
Stop-Process -Name absolute-control-input-helper -Force
```

Inicie ese comando, vuelva a la primera consola, ejecute `remote` y enfoque
Notepad. Espere: tras morir el helper, Windows retira ambos hooks y recupera
teclado/mouse sin Escape. Si falla, guarde hora del kill, procesos restantes,
Event Viewer y versión/build de Windows. No use esta prueba con trabajo sin
guardar.

## Qué falta antes de integrar

La siguiente etapa necesitaría un IPC local entre Java y el helper con framing,
identidad de proceso/sesión, comandos idempotentes (`LOCAL`, `REMOTO`, lease),
entrega ordenada de eventos y backpressure. Java tendría que arrancar/supervisar
el helper y renovar la lease; el helper debe seguir siendo la autoridad de
Escape y fail-open. Sólo después conviene mapear la copia de eventos al protocolo
principal, sin meter red en los callbacks.

## Riesgos y límites conocidos

- El reporter muestra virtual-key codes y, por diseño de laboratorio, observa
  también LOCAL. No deje el prototipo ejecutándose al escribir contraseñas ni
  conserve logs con información sensible.
- Las pruebas automáticas validan política y cola; la supresión global, Escape y
  recuperación al matar el proceso requieren las pruebas manuales anteriores.
- Las flags de inyección son metadata de Windows, no una prueba criptográfica de
  hardware. Software con suficiente privilegio podría producir eventos que el
  hook no distinga como se desea.
- Integridad/UIPI, escritorios seguros (UAC), sesiones RDP y software EDR pueden
  cambiar qué eventos ve un hook global. Este prototipo no pretende capturar el
  escritorio seguro.
- Windows puede retirar silenciosamente un low-level hook que exceda su timeout.
  Los callbacks son deliberadamente mínimos; si aun así ocurre, la consecuencia
  segura es que la entrada vuelve a Windows, aunque el helper podría conservar
  un estado mostrado desactualizado.
- No hay todavía IPC ni autenticación. La lease interna sólo simula la futura
  vida del controlador Java.
- Una salida abrupta permite input porque el sistema elimina los hooks, pero no
  garantiza que una aplicación local reciba parejas sintéticas para presses que
  nunca vio; por eso el usuario debe soltar teclas/botones tras recuperar.
