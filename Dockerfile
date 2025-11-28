# Etapa 1: Builder
FROM gradle:jdk21-alpine AS builder
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .
# Build sin correr tests para agilizar, asumimos que ya corrieron en el pipeline
RUN gradle bootJar --no-daemon -x test

# Etapa 2: Extractor de Capas (Optimización tipo "Tree Shaking" para Docker)
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /app
COPY --from=builder /home/gradle/src/build/libs/*.jar app.jar
# Spring Boot 3.x permite extraer las carpetas internas para optimizar caché
RUN java -Djarmode=layertools -jar app.jar extract

# Etapa 3: Imagen Final Ejecutable
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Instalar curl para healthchecks
RUN apk add --no-cache curl

# Copiamos las capas en orden de frecuencia de cambio (de menos a más)
# 1. Dependencias (Libs de terceros, cambian poco)
COPY --from=layers /app/dependencies/ ./
# 2. Loader de Spring
COPY --from=layers /app/spring-boot-loader/ ./
# 3. Dependencias Snapshot (Librerías propias en desarrollo)
COPY --from=layers /app/snapshot-dependencies/ ./
# 4. Tu código (Cambia siempre, es lo único que Docker redescargará)
COPY --from=layers /app/application/ ./

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]