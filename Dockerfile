# ==========================================
# Etapa 1: Builder (Con caché de dependencias)
# ==========================================
FROM gradle:jdk21-alpine AS builder
WORKDIR /home/gradle/src

# 1. COPIADO INTELIGENTE: Solo archivos de configuración primero
# Al hacer esto antes de copiar el código fuente, Docker puede cachear esta capa.
COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts gradle.properties gradlew ./
COPY --chown=gradle:gradle gradle ./gradle

# Aseguramos permisos de ejecución por si vienes de Windows
RUN chmod +x gradlew

# 2. DESCARGA DE DEPENDENCIAS
# Esta es la capa que tarda tiempo. Docker la reutilizará si no tocas el build.gradle.kts
RUN ./gradlew dependencies --no-daemon

# 3. COPIAR CÓDIGO FUENTE
# Ahora sí copiamos tu código. Si cambias algo en 'src', Docker invalida desde aquí hacia abajo,
# pero ya tiene las librerías descargadas del paso anterior.
COPY --chown=gradle:gradle src ./src

# 4. COMPILAR
RUN ./gradlew bootJar --no-daemon -x test

# ==========================================
# Etapa 2: Extractor de Capas (Igual que antes)
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /app
# Buscamos el jar generado (el nombre puede variar, el *.jar lo encuentra)
COPY --from=builder /home/gradle/src/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ==========================================
# Etapa 3: Imagen Final Ejecutable
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Herramienta para Healthcheck
RUN apk add --no-cache curl

# Copiamos las capas extraídas
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]