# Workflow sleep restart reproducer

Reproduces an early sleep timeout in **Axoniq/Axon Framework 5.4.1** when a workflow recalculates `deadline - now` after a JVM restart. This is intentionally nondeterministic workflow code, not a recommended timer implementation.

## Reproduce

Requires JDK 21+, Maven 3.9+, Docker Compose, Bash, and curl. Ports 8024 and 8124 must be available to the included Axon Server.

```sh
./reproduce.sh
```

The script starts Axon Server 2026.1.7, waits for its DCB context, builds the app, and schedules a 10-second timer. Three seconds after the sleep is committed, it kills that JVM with `SIGKILL` and starts a new JVM using the same persistent event store.

UTC times are displayed as `HH:mm:ss`, without dates or fractional seconds. Deadline storage and timing calculations retain full precision. Logs show the original persisted deadline, recalculated sleep duration, actual timeout, and workflow completion:

```text
BUG REPRODUCED: timeout occurred 4.352 seconds early (expected=16:55:57 actual=16:55:52)
```

The script exits successfully only if the timeout is more than one second early. Startup failures, missing events, and a non-reproduction fail explicitly. Each run uses a fresh timer ID and manages only its own app processes.

The restarted app must resume before the original deadline; a slow restart fails rather than reporting a false reproduction.

Axon Server and its event volumes are left intact. Use `docker compose stop` to stop the server without deleting events. The cluster template creates a DCB `default` context only on first initialization; an existing non-DCB context will fail the readiness check.

Axon Server and the workflow engine are subject to [Axoniq licensing terms](https://www.axoniq.io/pricing). This local setup has access control disabled and ports bound to localhost.

## Tests and ticket

`mvn test` runs the focused in-memory workflow and result-reporting tests without Axon Server. The real process-restart check is `./reproduce.sh`.

See [WORKFLOW-SLEEP-RESTART-BUG.md](WORKFLOW-SLEEP-RESTART-BUG.md) for the ticket description.
