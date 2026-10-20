# X360 Touch

Capa de controles táctiles para el port de Gears of War (Xbox 360) en Android. Funciona como app independiente para diseñar y probar el layout. En el port se conecta al juego mediante `NativeInputSink`.

## Qué hace
- **Gamepad virtual completo de Xbox 360**: 2 sticks, D-pad, A/B/X/Y, LB/RB, LT/RT, START/BACK, L3/R3. Multitáctil.
- **Editor de layout**: "Editar" → arrastra para mover, pellizca para cambiar tamaño, ajusta opacidad y oculta o muestra cada control. Se guarda automáticamente.
- **Giroscopio**: apuntar moviendo el teléfono. Modos: apagado / siempre / solo con LT (apuntar) / con el botón GIRO. Sensibilidad 0.1–4.0 e inversión de ejes. Se suma al stick derecho.
- **Cheats**: menú con activar/desactivar; los valores se reaplican en cada frame. Se definen en `app/src/main/assets/cheats.json`.
- HUD con el estado del mando en tiempo real, para comprobar que todo responde.

## Descargar el APK
Cada push a `main` compila en GitHub Actions y publica el APK en **Releases** (`X360Touch-<n>.apk`).

## Formato de cheats
```json
[
  {
    "id": "inf_ammo",
    "name": "Munición infinita",
    "description": "Congela el contador de munición",
    "writes": [{ "address": "0x82000000", "value": "0x63", "size": 4 }]
  }
]
```
`address` es la dirección de memoria del Xbox 360 (big-endian, la convierte el port). `size`: 1, 2 o 4 bytes. Sin el port, los cheats solo se registran en el log.

## Integración con el port
```kotlin
// MainActivity: reemplazar el sink de demo
private val sink = NativeInputSink()
// cheats.writer = MemoryWriter { addr, value, size -> nativeWrite(addr, value, size) }
```
Del lado nativo (C/C++), con SDL3:
```c
JNIEXPORT void JNICALL
Java_com_essence_x360touch_bridge_NativeInputSink_nativeSend(JNIEnv*, jobject,
    jint buttons, jint lx, jint ly, jint rx, jint ry, jint lt, jint rt) {
    // Guardar en el estado que lee XInputGetState del runtime (XINPUT_GAMEPAD).
    // Valores: sticks -32768..32767 (Y positivo = arriba), gatillos 0..255.
}
```

## Compilar localmente
JDK 17 + Android SDK 34 + Gradle 8.9: `gradle :app:assembleDebug`
