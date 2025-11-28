# Multi-stage: build + runtime

# 1) Build stage: compila el JAR con Gradle Wrapper o Gradle standalone si falta wrapper
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

RUN apk add --no-cache unzip curl

# Copiamos todo (incluye gradlew y gradle/wrapper si existen)
COPY . .

# Ejecutar build: usar wrapper si está completo (script + jar); si no, usar Gradle standalone
RUN if [ -f gradlew ] && [ -f gradle/wrapper/gradle-wrapper.jar ]; then \
      sed -i 's/\r$//' gradlew && chmod +x gradlew && \
      ./gradlew --no-daemon bootJar -x test; \
    else \
      echo 'Wrapper ausente o incompleto; usando Gradle standalone'; \
      GRADLE_VERSION=8.10.2; \
      curl -fsSL https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -o gradle.zip && \
      unzip -q gradle.zip -d /opt && rm gradle.zip && \
      /opt/gradle-${GRADLE_VERSION}/bin/gradle --no-daemon bootJar -x test; \
    fi

# 2) Runtime-only image para Spring Boot (JRE)
FROM eclipse-temurin:21-jre-alpine

# Instala curl para healthcheck y crea usuario no-root
RUN apk add --no-cache curl \
    && addgroup -S app && adduser -S app -G app

WORKDIR /app

# Copia el JAR desde el builder (elige el primero *.jar que no sea -plain)
# Nota: si generas múltiples jars, puedes ajustar el patrón
COPY --from=builder /workspace/build/libs/*.jar /app/
RUN set -e; \
    JAR=$(ls -1 /app/*.jar | grep -v '\-plain\\.jar$' | head -n1); \
    mv "$JAR" /app/app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 CMD sh -c 'curl -fsS http://127.0.0.1:8080/actuator/health || curl -kfsS https://127.0.0.1:8080/actuator/health || exit 1'

USER app

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
