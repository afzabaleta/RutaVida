# Build the Java application in an isolated stage.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -f backend/pom.xml dependency:go-offline
COPY backend/src backend/src
RUN mvn -B -f backend/pom.xml -DskipTests package

# Keep the service unprivileged. The Oracle wallet is decoded into a temporary
# writable directory at startup; Render's /etc/secrets mount itself is read-only.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/* \
    && (getent group 1000 || groupadd --gid 1000 render) \
    && useradd --uid 10001 --gid 1000 --create-home rutavida
COPY --from=build /workspace/backend/target/rutavida.jar /app/rutavida.jar
COPY --from=build /workspace/backend/target/lib /app/lib
COPY docker-entrypoint.sh /usr/local/bin/rutavida-entrypoint
RUN chmod 0555 /usr/local/bin/rutavida-entrypoint
USER 10001
EXPOSE 10000
ENTRYPOINT ["/usr/local/bin/rutavida-entrypoint"]
