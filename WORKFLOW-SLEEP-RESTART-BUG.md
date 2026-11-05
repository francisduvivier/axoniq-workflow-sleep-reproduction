# Workflow sleep times out early after restart when its duration is recalculated

### Basic information

* Axoniq Framework version: **5.4.1**
* Axon Framework version (when different from Axoniq Framework version): **5.4.1** (same)
* JDK version: **Eclipse Temurin OpenJDK 21.0.6+7-LTS**
* Complete executable reproducer if available (e.g. GitHub Repo): This repository; run `./reproduce.sh` (requires JDK 21+, Maven 3.9+, Docker Compose, Bash, and curl).
* Setup: Axon Server **2026.1.7**, DCB-enabled `default` context, persistent Docker volumes.

### Steps to reproduce

The scheduling event stores an absolute deadline (`Instant.now().plusSeconds(seconds)`). The workflow calculates its sleep duration as follows:

```java
var deadline = Instant.parse((String) context.workflowPayload().get("deadline"));
var remaining = Duration.between(Instant.now(), deadline);
var timeout = remaining.isNegative() ? Duration.ZERO : remaining;
context.sleep("wait", step -> step.timeout(timeout)).await();
```

Run `./reproduce.sh`. It starts Axon Server, schedules a 10-second timer, kills the JVM with `SIGKILL` three seconds after the sleep is persisted, and restarts the app against the same event store. Compare the logged persisted deadline with the actual timeout.

### Expected behaviour

An already-started sleep should preserve its original timeout deadline after restart, even when the workflow recalculates the remaining duration during replay.

### Actual behaviour

Reproduced on **5.4.1** with the 10-second scenario: the sleep timed out approximately **4.35 seconds early**, followed by workflow completion before the deadline. Observed timestamps on 2026-11-05, in UTC:

| Observation | Timestamp/value |
|---|---|
| Persisted `WaitStarted.timeoutTime` | `16:55:57.302478Z` |
| Current time during replay | `16:55:51.882247Z` |
| Recalculated sleep duration | `PT5.418199S` |
| `WaitTimedOut` payload timestamp | `16:55:52.950080Z` |
| Workflow completion event timestamp | `16:55:52.992864Z` |

Source inspection of `WaitForDelegate` in 5.4.1 suggests the cause: replay computes `original step timestamp + newly supplied duration`, rather than using the persisted `timeoutTime`.
