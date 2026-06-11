# KVM Network Share

Software KVM por red local — comparte teclado y mouse entre dos PCs sin hardware adicional.

## ¿Qué hace?

Permite controlar dos computadoras con un solo teclado y mouse. Al llevar el cursor al **borde derecho** de la pantalla, el control pasa automáticamente a la segunda PC. Al llegar al **borde izquierdo** de la segunda PC, el control regresa.

```
[PC Cliente] ──── borde derecho ────► [PC Servidor]
                                       borde izquierdo ◄──── regresa
```

## Requisitos

- Java JDK 17 o superior en ambas PCs
- Librería jnativehook 2.2.1 (incluida en `kvm-client/lib/`)
- Ambas PCs en la misma red local (WiFi o Ethernet)

## Estructura del proyecto

```
KVM-Network-Share/
├── kvm-client/
│   ├── lib/
│   │   └── jnativehook-2.2.1.jar
│   ├── src/
│   │   ├── VentanaClienteWindow.java   ← Cliente para Windows
│   │   └── VentanaClienteLinux.java    ← Cliente para Linux (Arch)
│   └── out/
├── kvm-server/
│   └── src/
│       └── ServidorKVM.java            ← Servidor (Windows/Linux)
└── .vscode/
    └── settings.json
```

## Cómo usar

### 1. PC Servidor (la que recibirá el control)

```cmd
cd kvm-server\src
javac ServidorKVM.java
java ServidorKVM
```

Verás: `=== SERVIDOR KVM ENCENDIDO (puerto 8080) ===`

### 2. PC Cliente (la que controla)

**Windows:**
```cmd
cd kvm-client
javac -cp lib\jnativehook-2.2.1.jar -d out src\VentanaClienteWindow.java
java -cp "out;lib\jnativehook-2.2.1.jar" VentanaClienteWindow
```

**Linux (Arch):**
```bash
cd kvm-client
javac -d out src/VentanaClienteLinux.java
java -cp out VentanaClienteLinux
```

### 3. Conectar

1. En la ventana del cliente, ingresa la IP del servidor
2. Lleva el mouse al **borde derecho** para conectar automáticamente
3. El control pasa a la PC servidor
4. Lleva el mouse al **borde izquierdo** del servidor para regresar
5. Presiona **ESC** para liberar el control manualmente

## Funciones disponibles

| Función | Estado |
|---------|--------|
| Mouse (movimiento) | ✅ |
| Clics izquierdo y derecho | ✅ |
| Scroll | ✅ |
| Teclado (letras y números) | ✅ |
| Teclas especiales (F1-F12, flechas) | ✅ |
| Modificadores (Shift, Ctrl, Alt) | ✅ |
| Símbolos especiales (ñ, á, é...) | ✅ via clipboard |
| Paso automático por borde | ✅ |
| Regreso automático por borde | ✅ |

## Notas

- El mapeo de teclado está optimizado para **teclado español (QWERTY)**
- Puerto por defecto: **8080**
- El cliente ancla el cursor al centro de su pantalla mientras controla el servidor
- Para Linux se requiere pertenecer al grupo `input`: `sudo usermod -aG input $USER`

## Pendiente

- Interfaz unificada cliente/servidor en una sola app
- Mapeo completo de símbolos con AltGr
- Soporte para resoluciones diferentes entre PCs