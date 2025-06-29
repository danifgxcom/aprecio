# AVD Optimization Guide

## Configuración Recomendada del AVD

### Especificaciones del Dispositivo Virtual
- **RAM**: 4GB (mínimo 3GB)
- **VM Heap**: 512MB
- **Internal Storage**: 8GB
- **SD Card**: No necesaria para esta app
- **Graphics**: Hardware - GLES 2.0 (si tu GPU lo soporta)
- **Multi-Core CPU**: 4 cores máximo

### Configuración Avanzada
1. **Enable Hardware Acceleration**:
   - Intel HAXM (Intel processors)
   - AMD Hypervisor (AMD processors)
   - Hyper-V (Windows con virtualización habilitada)

2. **En AVD Manager → Advanced Settings**:
   - Boot option: Cold boot
   - Emulated Performance → Graphics: Hardware - GLES 2.0
   - Memory and Storage → RAM: 4096 MB
   - Memory and Storage → VM heap: 512 MB

### Optimizaciones del Sistema Host

#### Para IntelliJ/Android Studio
```
# En Help → Edit Custom VM Options, añadir:
-Xmx8g
-XX:ReservedCodeCacheSize=1g
-XX:+UseConcMarkSweepGC
-XX:SoftRefLRUPolicyMSPerMB=50
-ea
-XX:CICompilerCount=2
-Dsun.io.useCanonPrefixCache=false
-Djdk.http.auth.tunneling.disabledSchemes=""
-XX:+HeapDumpOnOutOfMemoryError
-XX:-OmitStackTraceInFastThrow
-Djb.vmOptionsFile=studio64.vmoptions
-Djava.net.preferIPv4Stack=true
```

#### Configuración del Sistema
1. **Cerrar aplicaciones innecesarias** antes de iniciar el AVD
2. **Aumentar la memoria virtual** del sistema si es necesario
3. **Usar SSD** si es posible para mejor rendimiento de I/O
4. **Verificar que la virtualización** esté habilitada en BIOS

### Profile Específico para Aprecio App
Debido a que la app usa:
- CameraX
- ML Kit
- Procesamiento de imágenes

**Configuración recomendada**:
- Device: Pixel 6 o similar (API 31+)
- RAM: 4GB
- Enable Camera: Sí (Front + Back)
- Graphics: Hardware
- Use Host GPU: Sí

### Comandos Útiles para Debugging
```bash
# Verificar estado del emulador
adb devices

# Monitorear memoria del emulador
adb shell dumpsys meminfo

# Logs específicos de la app
adb logcat -s "MainActivity" "PriceAnalyzer"

# Reiniciar ADB si hay problemas de conexión
adb kill-server && adb start-server
```

### Alternativas si el AVD sigue dando problemas
1. **Usar dispositivo físico** conectado por USB
2. **Genymotion** como alternativa al AVD de Android Studio
3. **Usar el emulador desde línea de comandos**:
   ```bash
   emulator -avd YourAVDName -memory 4096 -cores 2
   ```

### Troubleshooting Común
- Si se cuelga al iniciar: Reducir RAM a 3GB y cores a 2
- Si la cámara no funciona: Verificar que "Use Host GPU" esté habilitado
- Si ML Kit falla: Verificar que Google Play Services estén actualizados en el AVD