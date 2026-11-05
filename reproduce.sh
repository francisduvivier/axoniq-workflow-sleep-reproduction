#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

app_pid=
build_log=$(mktemp)
cleanup() {
    if [[ -n "$app_pid" ]] && kill -0 "$app_pid" 2>/dev/null; then
        kill -KILL "$app_pid"
        wait "$app_pid" 2>/dev/null || true
    fi
    rm -f "$build_log"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

log() {
    printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

log "Starting Axon Server..."
docker compose up -d
log "Waiting for Axon Server and the DCB default context..."
ready=0
deadline=$((SECONDS + 120))
while (( SECONDS < deadline )); do
    if health=$(curl -fsS --connect-timeout 2 --max-time 3 http://localhost:8024/actuator/health 2>/dev/null) \
        && grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"[[:space:]]*}[[:space:]]*$' <<< "$health" \
        && contexts=$(curl -fsS --connect-timeout 2 --max-time 3 http://localhost:8024/v2/contexts 2>/dev/null) \
        && grep -Eq '"contextName"[[:space:]]*:[[:space:]]*"default"[[:space:]]*,[[:space:]]*"dcbContext"[[:space:]]*:[[:space:]]*true' <<< "$contexts"; then
        ready=1
        break
    fi
    sleep 1
done
if (( ! ready )); then
    log "ERROR: Axon Server or its DCB default context was not ready within 120 seconds." >&2
    docker compose logs --tail=50 axonserver >&2
    exit 1
fi

log "Building the reproducer..."
if ! mvn -q clean package dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/classpath.txt \
    >"$build_log" 2>&1; then
    cat "$build_log" >&2
    exit 1
fi
classpath="target/classes:$(cat target/classpath.txt)"
timer_id="sleep-restart-$(date +%s)-$$"
start_log="target/reproducer-start.log"
java -cp "$classpath" io.axoniq.example.reproducer.ReproducerApp schedule "$timer_id" 60 >"$start_log" 2>&1 &
app_pid=$!

deadline=$((SECONDS + 45))
while ! grep -q ' SLEEP_PERSISTED deadline=' "$start_log"; do
    if ! kill -0 "$app_pid" 2>/dev/null || (( SECONDS >= deadline )); then
        cat "$start_log" >&2
        log "ERROR: The app did not persist its sleep within 45 seconds." >&2
        exit 1
    fi
    sleep 0.1
done
cat "$start_log"
persisted_deadline=$(sed -n 's/.* SLEEP_PERSISTED deadline=//p' "$start_log")
sleep 3
log "KILLING app pid=$app_pid three seconds after the sleep was persisted."
kill -KILL "$app_pid"
if wait "$app_pid" 2>/dev/null; then
    log "ERROR: The scheduled app exited normally instead of being killed." >&2
    exit 1
else
    status=$?
    if (( status != 137 )); then
        log "ERROR: Unexpected app exit status: $status" >&2
        exit 1
    fi
fi
app_pid=

log "RESTARTING app with the same timer and persistent event store."
java -cp "$classpath" io.axoniq.example.reproducer.ReproducerApp resume "$timer_id" "$persisted_deadline" &
app_pid=$!
wait "$app_pid"
app_pid=
