# Absolute Control

Software KVM por red local — comparte teclado y mouse entre dos PCs sin hardware adicional y sin remote desktop.

> **Plataforma actual: Windows.** El proyecto nació con una variante experimental para Linux/Wayland, pero el desarrollo activo y probado hoy es exclusivamente en Windows.

## ¿Qué hace?

Permite controlar dos computadoras con un solo teclado y mouse, como si fueran un único monitor extendido. Al llevar el cursor al borde de la pantalla configurado, el control "cruza" automáticamente a la otra PC. Al llegar al borde opuesto en la otra máquina, el control regresa.

```
[PC Rey / Cliente] ──── borde activador ────► [PC Esclavo / Servidor]
                                                borde opuesto ◄──── regresa
```

- **Rey (Cliente)**: la PC que tiene físicamente el teclado y el mouse, y que envía el control a la otra.
- **Esclavo (Servidor)**: la PC que recibe y ejecuta los eventos de mouse/teclado.

## Requisitos

- Java JDK 17 o superior en ambas PCs
- Librería jnativehook 2.2.1 (captura de input global en Windows)
- Ambas PCs en la misma red local (mismo WiFi/switch) — el descubrimiento automático de IP usa broadcast UDP, que no atraviesa routers distintos
- Windows Defender / Firewall debe permitir la app en el puerto TCP configurado y en el puerto UDP 8079 (discovery)

## Estructura del proyecto

```
Absolute_Control/
└── src/
    └── Absolute_Control/
        ├── Main.java              ← GUI principal (modo Rey/Esclavo, IP, puerto, posición)
        ├── core/
        │   ├── Cliente.java       ← Lógica del modo Rey (envía eventos)
        │   ├── Servidor.java      ← Lógica del modo Esclavo (ejecuta eventos con Robot)
        │   └── Discovery.java     ← Autodiscovery por broadcast UDP
        └── input/
            ├── MouseHandler.java
            └── KeyboardHandler.java
```

## Cómo usar

### 1. Abrir el proyecto

Abrí la carpeta `Absolute_Control` en tu IDE de Java (NetBeans, IntelliJ, Eclipse) o compilá manualmente con `javac`/`java`, asegurándote de tener la librería jnativehook en el classpath.

### 2. PC Esclavo (la que va a ser controlada)

1. Ejecutá `Main.java`.
2. Seleccioná el modo **ESCLAVO (Servidor)**.
3. Definí el puerto (por defecto `8080`) y de qué lado está la PC Rey (izquierda/derecha).
4. Presioná **INICIAR SERVIDOR**.
5. El indicador de estado se pone en amarillo: "esperando cliente".

### 3. PC Rey (la que tiene el control)

1. Ejecutá `Main.java`.
2. Seleccioná el modo **REY (Cliente)**.
3. Presioná **Buscar** para que la app encuentre al Esclavo automáticamente en la red local (te completa la IP y el puerto solo). Si no lo encuentra, podés escribir la IP a mano.
4. Presioná **CONECTAR**.
5. El indicador se pone en amarillo: "Rey activo — lleva el mouse al borde para conectar".

### 4. Cruzar el control

1. Llevá el mouse al borde configurado en la PC Rey. El control pasa automáticamente a la PC Esclavo (el indicador se pone verde en ambos lados).
2. Llevá el mouse al borde opuesto en la PC Esclavo para que el control vuelva al Rey.
3. Para detener todo, presioná **DETENER** en la PC Rey, o **DETENER SERVIDOR** en la PC Esclavo.

## Indicador de estado

| Color | Significado |
|-------|--------------|
| 🔴 Rojo | Inactivo / detenido |
| 🟡 Amarillo | Activo pero sin control en curso (esperando cliente, esperando cruzar el borde, o buscando servidor) |
| 🟢 Verde | Control activo (el mouse/teclado de una PC está controlando a la otra ahora mismo) |

## Funciones disponibles

| Función | Estado |
|---------|--------|
| Mouse (movimiento relativo) | ✅ |
| Clics izquierdo y derecho | ✅ |
| Scroll | ✅ |
| Teclado (letras y números) | ✅ |
| Teclas especiales (F1-F12, flechas) | ✅ |
| Modificadores (Shift, Ctrl, Alt) | ✅ |
| Símbolos especiales (ñ, á, é...) | ✅ vía clipboard |
| Cruce automático por borde | ✅ |
| Regreso automático por borde | ✅ |
| Autodiscovery de IP en la red local | ✅ |
| Indicador visual de estado (semáforo) | ✅ |
| Botón Desconectar confiable | ✅ |
| Empaquetado como `.exe` | 🚧 pendiente |
| Mapeo completo de AltGr | 🚧 pendiente |
| Tecla Windows solo en la PC con control activo | 🚧 pendiente |
| Soporte multi-PC (más de 2 a la vez) | 🚧 pendiente, requiere rediseño |

## Notas

- El mapeo de teclado está optimizado para **teclado español (QWERTY)**.
- Puerto TCP por defecto: **8080**. Puerto UDP fijo para discovery: **8079**.
- El cliente ancla el cursor al centro de su pantalla mientras controla al servidor, para lograr movimiento relativo en modo "extendido" en vez de espejo.
- Hoy la app asume resoluciones iguales entre ambas PCs; soporte para resoluciones distintas está pendiente.
- La arquitectura actual es estrictamente 1 a 1 (un Rey, un Esclavo). Conectar 3 o más PCs a la vez no está soportado todavía.

## Pendiente

- Empaquetado como instalador `.exe` (jpackage), con firma de código y checksum SHA-256 del release.
- Mapeo completo de símbolos con AltGr (corchetes, llaves y otros especiales).
- Que la tecla Windows actúe solo en la PC que tiene el control activo.
- Soporte para resoluciones de pantalla distintas entre ambas PCs.
- Rediseño para soportar más de 2 PCs en topología KVM multi-pantalla.