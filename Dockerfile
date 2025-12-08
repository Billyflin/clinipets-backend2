# Dockerfile Ligero para Desarrollo
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Herramienta para Healthcheck (necesaria para tu docker-compose)
RUN apk add --no-cache curl

# Copiamos el JAR que compilaste en tu máquina
# El contexto de build debe tener el jar generado en build/libs/
COPY build/libs/*.jar app.jar

# Ejecutamos
ENTRYPOINT ["java", "-jar", "app.jar"]