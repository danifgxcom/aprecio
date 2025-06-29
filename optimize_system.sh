#!/bin/bash

echo "🚀 Optimizando sistema para desarrollo Android..."

# Aumentar límites del sistema
echo "Configurando límites del sistema..."
sudo sysctl vm.max_map_count=262144
sudo sysctl fs.inotify.max_user_watches=524288

# Limpiar caché de Gradle
echo "Limpiando caché de Gradle..."
./gradlew clean
rm -rf ~/.gradle/caches/
rm -rf ~/.android/build-cache/

# Verificar HAXM/KVM
echo "Verificando virtualización..."
if lscpu | grep -q "vmx\|svm"; then
    echo "✅ Virtualización soportada"
else
    echo "❌ Virtualización no detectada - verifica BIOS"
fi

# Verificar memoria disponible
TOTAL_MEM=$(free -g | awk 'NR==2{print $2}')
if [ $TOTAL_MEM -lt 8 ]; then
    echo "⚠️  Memoria total: ${TOTAL_MEM}GB - Recomendado: 8GB+"
else
    echo "✅ Memoria suficiente: ${TOTAL_MEM}GB"
fi

echo "✨ Optimización completada!"
echo ""
echo "Próximos pasos:"
echo "1. Reinicia Android Studio/IntelliJ"
echo "2. Configura el AVD con las especificaciones del AVD_OPTIMIZATION.md"
echo "3. Si persisten problemas, usa un dispositivo físico"