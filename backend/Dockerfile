# Build stage: Maven + JDK, only to produce the jar. Not shipped.
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
# Dependencies first so they're cached across builds unless pom.xml actually changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package

# Runtime stage: JRE only, non-root, nothing else from the build toolchain.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
