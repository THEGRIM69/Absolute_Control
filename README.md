# Absolute Control

Software KVM por red local para compartir **teclado y mouse entre dos PCs** sin hardware KVM adicional y sin transmitir el escritorio.

> **Plataforma actual: Windows.**  
> El proyecto nació con una variante experimental para Linux/Wayland, pero el desarrollo activo y las pruebas actuales están centrados en Windows.

## ¿Qué hace?

Absolute Control permite utilizar el teclado y mouse conectados físicamente a una PC principal para controlar otra computadora dentro de la misma red local.

Cada computadora continúa utilizando:

- su propio monitor;
- su propio escritorio;
- sus propias aplicaciones.

**No se transmite video ni se duplica la pantalla.**

La idea es generar una sensación similar a tener dos pantallas colocadas una junto a la otra:

```text
[ PC Rey / Cliente ] ───── cruzar borde ─────► [ PC Esclavo / Servidor ]
        ▲                                              │
        └──────────── regresar por borde ──────────────┘
```

- **Rey (Cliente):** PC que tiene físicamente el teclado y mouse y envía los eventos.
- **Esclavo (Servidor):** PC que recibe los eventos y los reproduce mediante `java.awt.Robot`.

Actualmente se puede configurar la PC secundaria a la **izquierda o derecha** de la principal.

---

## Estado actual del desarrollo

| Fase | Estado |
|---|---|
| Fase 1 — Conexiones LAN | ✅ Implementada |
| Fase 1.1 — Estabilidad y concurrencia | ✅ Implementada y probada localmente |
| Fase 2 — Transición entre pantallas | 🟡 Implementada y probada automáticamente; validación física final pendiente |

La documentación detallada está disponible en:

- [Fase 1 — Conexiones](docs/Fase-1-Conexiones.md)
- [Fase 1.1 — Estabilidad y concurrencia](docs/Fase-1.1-Estabilidad-Concurrencia.md)
- [Fase 2 — Transición entre pantallas](docs/Fase-2-Transicion-Pantallas.md)
- [Roadmap](docs/Roadmap.md)
- [Índice de documentación](docs/README.md)

---

## Funciones disponibles

| Función | Estado |
|---|---|
| Movimiento de mouse por red | ✅ |
| Clic izquierdo y derecho | ✅ |
| Scroll | ✅ |
| Teclado | ✅ |
| Teclas especiales | ✅ |
| Modificadores Shift, Ctrl y Alt | ✅ |
| Caracteres especiales mediante mecanismo auxiliar de clipboard | ✅ |
| Cambio de control por borde | ✅ |
| Regreso automático por borde | ✅ |
| Regreso mediante Escape | ✅ |
| PC secundaria a izquierda o derecha | ✅ |
| Centro del mouse dinámico | ✅ |
| Resoluciones diferentes entre PCs | ✅ |
| Conservación aproximada de altura al cruzar | ✅ |
| Autodiscovery en LAN | ✅ |
| Heartbeat PING/PONG | ✅ |
| Recuperación ante pérdida de conexión | ✅ |
| Reconexión mediante un nuevo cruce | ✅ |
| Reinicio de servidor | ✅ |
| Reinicio de Discovery | ✅ |
| Liberación de teclas/botones tras desconexión | ✅ |
| Aislamiento entre sesiones | ✅ |
| Pruebas automáticas de estabilidad | ✅ |
| Pruebas automáticas de transición | ✅ |
| Mapeo completo de AltGr | 🚧 Pendiente |
| Clipboard compartido entre PCs | 🚧 Pendiente |
| Transferencia de archivos | 🚧 Pendiente |
| Multi-monitor | 🚧 Pendiente |
| Soporte de más de 2 PCs | 🚧 Pendiente |
| Empaquetado `.exe` revalidado | 🚧 Pendiente |
| Transmisión de pantalla | 🚧 Futuro |
| Acceso remoto por Internet | 🚧 Futuro |
| Cliente Android | 🚧 Futuro |

> El uso del clipboard para algunos caracteres especiales **no significa que exista todavía sincronización de portapapeles entre las dos PCs**.

---

## Arquitectura actual

```text
PC REY
│
├── MouseHandler
├── KeyboardHandler
│
▼
Cliente
│
│ TCP
▼
Servidor
│
▼
java.awt.Robot
│
▼
PC ESCLAVO
```

El descubrimiento funciona de manera separada:

```text
Cliente ───── UDP Broadcast :8079 ─────► Discovery del servidor
```

El servidor encontrado devuelve la información necesaria para establecer posteriormente la sesión TCP.

---

## Tecnologías

- **Java**
- Compatibilidad objetivo: **Java 17**
- **Swing** — interfaz gráfica.
- **AWT** — geometría de pantalla y eventos.
- **java.awt.Robot** — reproducción de mouse y teclado.
- **JNativeHook 2.2.1** — captura global de entrada.
- **TCP** — transmisión de eventos.
- **UDP Broadcast** — descubrimiento automático.
- **PowerShell** — scripts de compilación y pruebas.

Actualmente el proyecto no utiliza Maven ni Gradle.

---

## Requisitos

- Windows.
- Java JDK 17 o superior.
- JNativeHook 2.2.1.
- Ambas PCs conectadas a la misma red local.
- Firewall de Windows permitiendo la aplicación.

Puertos actuales:

| Servicio | Puerto |
|---|---:|
| TCP de control | Configurable, normalmente `8080` |
| UDP Discovery | `8079` |

El Discovery utiliza broadcast UDP, por lo que está pensado principalmente para equipos dentro de la misma LAN.

---

## Estructura del proyecto

```text
KVM-Network-Share/
├── src/
│   └── Absolute_Control/
│       ├── Main.java
│       ├── core/
│       │   ├── Cliente.java
│       │   ├── Servidor.java
│       │   ├── Discovery.java
│       │   └── GeometriaPantalla.java
│       └── input/
│           ├── MouseHandler.java
│           └── KeyboardHandler.java
│
├── tests/
│   ├── StabilityCheck.java
│   ├── MouseTransitionCheck.java
│   ├── run-stability.ps1
│   └── run-mouse-transition.ps1
│
├── docs/
│   ├── README.md
│   ├── Fase-1-Conexiones.md
│   ├── Fase-1.1-Estabilidad-Concurrencia.md
│   ├── Fase-2-Transicion-Pantallas.md
│   └── Roadmap.md
│
├── kvm-client/
├── kvm-server/
├── build.ps1
└── README.md
```

La implementación integrada y activa se encuentra principalmente en:

```text
src/Absolute_Control/
```

Las carpetas `kvm-client` y `kvm-server` contienen implementaciones anteriores, herramientas auxiliares y dependencias históricas del proyecto.

---

## Cómo usar

### 1. PC Esclavo

1. Ejecutar `Main.java`.
2. Seleccionar **ESCLAVO (Servidor)**.
3. Configurar el puerto TCP.
4. Configurar la posición relativa entre las PCs.
5. Presionar **INICIAR SERVIDOR**.

El servidor queda esperando una conexión y responde también a las búsquedas UDP de Discovery.

---

### 2. PC Rey

1. Ejecutar `Main.java`.
2. Seleccionar **REY (Cliente)**.
3. Utilizar **Buscar** para localizar automáticamente la otra PC o ingresar manualmente su IP.
4. Presionar **CONECTAR**.

Esto habilita el modo cliente.

La conexión TCP no necesariamente se abre inmediatamente: se realiza cuando el usuario intenta cruzar el borde configurado.

---

### 3. Cruzar entre las PCs

Si la PC secundaria está configurada a la derecha:

```text
[ REY ] ─────► [ ESCLAVO ]
```

El usuario cruza por el borde derecho del Rey y entra por el borde izquierdo del Esclavo.

Para regresar:

```text
[ REY ] ◄───── [ ESCLAVO ]
```

Se utiliza el borde izquierdo del Esclavo.

Si la secundaria está configurada a la izquierda, el comportamiento se invierte.

La aplicación intenta conservar aproximadamente la posición vertical del cursor entre ambas pantallas incluso cuando tienen resoluciones diferentes.

---

## Geometría de pantalla

Versiones anteriores utilizaban un centro fijo pensado para una resolución concreta.

Actualmente:

- ancho y alto se obtienen dinámicamente;
- el centro se calcula según la resolución actual;
- la entrada se coloca algunos píxeles dentro del borde remoto;
- la altura de salida se normaliza y adapta a la pantalla destino.

Esto permite utilizar resoluciones diferentes entre ambas PCs.

> Por ahora se utiliza una sola pantalla por computadora. El soporte multi-monitor todavía no está implementado.

---

## Conexión y recuperación

La conexión fue reforzada durante la Fase 1.1.

Ante eventos como:

- cierre del servidor;
- EOF;
- reset TCP;
- pérdida de red;
- heartbeat vencido;

el cliente invalida la sesión remota y recupera el control local.

Después se realiza la limpieza de:

- sockets;
- streams;
- threads;
- teclas presionadas;
- botones presionados;
- estado de la sesión.

La limpieza puede continuar en segundo plano si inicialmente supera el tiempo de espera y posteriormente puede revalidarse.

Una nueva sesión no reutiliza el socket anterior.

---

## Heartbeat

Durante una sesión activa existe un mecanismo ligero:

```text
Cliente ─── PING ───► Servidor
Cliente ◄── PONG ─── Servidor
```

Su función es detectar conexiones que aparentemente permanecen abiertas aunque la otra computadora haya desaparecido o dejado de responder.

PING/PONG no inicializa ni modifica la posición del cursor.

Este mecanismo **no realiza reconexión automática**.

Cuando una sesión muere:

```text
control remoto
      ↓
recuperar control local
      ↓
limpiar sesión
      ↓
nuevo cruce del borde
      ↓
nueva conexión
```

---

## Indicador de estado

| Color | Significado |
|---|---|
| 🔴 Rojo | Inactivo o detenido |
| 🟡 Amarillo | Activo pero sin control remoto en curso |
| 🟢 Verde | Sesión de control remoto activa |

La aplicación también diferencia internamente estados como conexión pendiente, control local y cierre.

---

## Pruebas

El proyecto cuenta actualmente con dos suites principales.

### StabilityCheck

```text
tests/StabilityCheck.java
tests/run-stability.ps1
```

Comprueba, entre otros:

- conexión;
- EOF;
- reset TCP;
- heartbeat;
- limpieza tardía;
- cierre concurrente;
- aislamiento entre sesiones;
- callbacks antiguos;
- liberación de entradas;
- Discovery;
- reinicios del servidor;
- múltiples conexiones consecutivas.

La ejecución actual finaliza con:

```text
Exit code: 0
0 hilos kvm- activos
```

---

### MouseTransitionCheck

```text
tests/MouseTransitionCheck.java
tests/run-mouse-transition.ps1
```

Comprueba:

- secundaria a izquierda;
- secundaria a derecha;
- distintas resoluciones;
- cálculo dinámico del centro;
- altura relativa;
- margen de entrada;
- movimiento mediante deltas;
- regreso de control;
- múltiples ciclos cliente/servidor.

Las pruebas actuales han completado correctamente **54 ciclos cliente/servidor**.

---

### Ejecutar las pruebas

Desde PowerShell y desde la raíz del repositorio:

```powershell
.\tests\run-stability.ps1
```

```powershell
.\tests\run-mouse-transition.ps1
```

Estas pruebas son principalmente automatizadas y locales.

La Fase 2 todavía necesita validación física completa utilizando dos PCs reales.

---

## Empaquetado

Existe:

```text
build.ps1
```

con lógica para utilizar herramientas como `javac`, `jar` y `jpackage`.

Sin embargo, el empaquetado actual necesita ser **revalidado** antes de considerarlo parte estable del proyecto.

Existe una diferencia histórica entre la ruta de JNativeHook que espera `build.ps1` y la ubicación actual de la dependencia.

Por ese motivo:

**Empaquetado `.exe`: 🟡 pendiente de revalidación.**

---

## Limitaciones actuales

- Solo una PC Rey y una PC Esclavo.
- Una pantalla por computadora.
- AltGr todavía necesita consolidación.
- El comportamiento de la tecla Windows todavía requiere trabajo.
- No existe clipboard compartido entre equipos.
- No existe transferencia de archivos.
- No existe autenticación avanzada.
- No existe cifrado propio del protocolo.
- No existe transmisión de pantalla.
- No existe acceso remoto a través de Internet.

---

## Próximos pasos

La evolución prevista actualmente es:

1. Validación física completa de la transición entre dos PCs.
2. Clipboard compartido de texto.
3. Clipboard con imágenes.
4. Transferencia de archivos.
5. Multi-monitor.
6. Emparejamiento y seguridad.
7. Transmisión de pantalla.
8. Acceso remoto a través de Internet.
9. Cliente Android.

Estos puntos son un **roadmap**, no funcionalidades actuales.

Más información:

[Roadmap completo](docs/Roadmap.md)

---

## Visión a futuro

Absolute Control comenzó como una solución sencilla para utilizar el mismo teclado y mouse entre dos computadoras de una red local.

La meta inmediata continúa siendo mantener esa experiencia rápida, estable y sencilla.

A largo plazo, el proyecto podría evolucionar hacia un sistema propio de control remoto que incorpore transferencia de datos, visualización remota y clientes para otras plataformas, manteniendo la arquitectura actual como base del control local.
