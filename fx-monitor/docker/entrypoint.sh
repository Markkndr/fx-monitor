#!/usr/bin/env bash
# Boots the GUI stack (virtual display -> window manager -> VNC -> noVNC),
# then launches the JavaFX app in the foreground so the container's lifecycle
# is tied to the app process.
set -euo pipefail

: "${DISPLAY:=:0}"
: "${SCREEN_GEOMETRY:=1280x800x24}"
export DISPLAY

cleanup() { pkill -P $$ 2>/dev/null || true; }
trap cleanup EXIT INT TERM

# 1. Virtual framebuffer — gives JavaFX a display to render into.
Xvfb "$DISPLAY" -screen 0 "$SCREEN_GEOMETRY" -nolisten tcp &

# Wait until the X server is accepting connections.
for _ in $(seq 1 40); do
  if xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
    break
  fi
  sleep 0.25
done

# 2. Lightweight window manager so windows are decorated/manageable.
fluxbox >/dev/null 2>&1 &

# 3. VNC server exporting the virtual display on 5900.
#    No password: the port is only reachable inside the container; the browser
#    reaches it via noVNC, which compose binds to localhost only.
x11vnc -display "$DISPLAY" -forever -shared -nopw -quiet -rfbport 5900 &

# 4. noVNC web front-end (websockify) on 6080.
websockify --web=/usr/share/novnc 6080 localhost:5900 >/dev/null 2>&1 &

echo "-------------------------------------------------------------"
echo " FX Monitor UI:  http://localhost:6080/vnc.html"
echo " REST API:       http://localhost:8080"
echo "-------------------------------------------------------------"

# 5. The app. Main-Class is Spring Boot's JarLauncher, which invokes MainApp,
#    so JavaFX launches cleanly from the fat jar without a module path.
exec java ${JAVA_OPTS:-} -jar /app/app.jar
