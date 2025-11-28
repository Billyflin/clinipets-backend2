FROM gradle:jdk21-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle bootJar --no-daemon && rm -f build/libs/*-plain.jar

FROM eclipse-temurin:21-jre-alpine
EXPOSE 8080

RUN apk add --no-cache curl && mkdir /app

COPY --from=build /home/gradle/src/build/libs/*.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]