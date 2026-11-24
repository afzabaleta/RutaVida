# Build the Java application in an isolated stage.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -f backend/pom.xml dependency:go-offline
COPY backend/src backend/src
RUN mvn -B -f backend/pom.xml -DskipTests package

# Render exposes uploaded secret files to group 1000. Keep the service unprivileged while
# allowing Oracle wallet files under /etc/secrets to be read at runtime.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN getent group 1000 || groupadd --gid 1000 render \
    && useradd --uid 10001 --gid 1000 --create-home rutavida
COPY --from=build /workspace/backend/target/rutavida.jar /app/rutavida.jar
COPY --from=build /workspace/backend/target/lib /app/lib
USER 10001
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/rutavida.jar"]
