# syntax=docker/dockerfile:1

# ---------- Build stage ----------
# Build the Spring Boot + JavaFX fat jar with a full JDK 21 + Maven toolchain.
# Building on linux/amd64 makes Maven resolve the linux-native JavaFX artifacts.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached across source-only changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Build the executable jar (tests are run in CI, skipped here for image builds).
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- Runtime stage ----------
# JavaFX renders through GTK on Linux and needs a real display, so we ship a
# headless X server (Xvfb) + window manager (fluxbox) and expose the UI over
# noVNC so it can be viewed in any browser — no host X server required.
FROM eclipse-temurin:21-jre AS runtime

RUN apt-get update && apt-get install -y --no-install-recommends \
      xvfb x11vnc fluxbox x11-utils \
      novnc websockify \
      libgtk-3-0 libglu1-mesa libgl1 libxtst6 libxrender1 \
      fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/fx-monitor-*.jar /app/app.jar
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

ENV DISPLAY=:0 \
    SCREEN_GEOMETRY=1280x800x24 \
    JAVA_OPTS=""

# 6080 = noVNC web UI, 8080 = REST API (Spring MVC)
EXPOSE 6080 8080

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
