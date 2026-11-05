# Workflow sleep times out early after restart when its duration is recalculated

### Basic information

* Axoniq Framework version: **5.4.1**
* Axon Framework version (when different from Axoniq Framework version): **5.4.1** (same)
* JDK version: **Eclipse Temurin OpenJDK 21.0.6+7-LTS**
* Complete executable reproducer if available (e.g. GitHub Repo): Local `axon-workflow-example` Maven project; not yet published.
* Setup: Axon Server **2026.1.7**, DCB-enabled `default` context, persistent Docker volumes.

### Steps to reproduce

The scheduling event stores an absolute deadline (`Instant.now().plusSeconds(seconds)`). The workflow calculates its sleep duration as follows:

```java
var deadline = Instant.parse((String) context.workflowPayload().get("deadline"));
var remaining = Duration.between(Instant.now(), deadline);
var timeout = remaining.isNegative() ? Duration.ZERO : remaining;
context.sleep("wait", step -> step.timeout(timeout)).await();
```

1. Run `docker compose up -d`, wait for Axon Server to be ready, then start one application instance:

   ```sh
   mvn -q compile exec:java -Dexec.mainClass=io.axoniq.example.timer.TimerApp
   ```

2. Enter `deadline 60`. About three seconds after the sleep starts, enter `q`.
3. Restart the application before the deadline, leaving Axon Server running. Compare the original deadline with the persisted `WaitTimedOut` event.

### Expected behaviour

An already-started sleep should preserve its original timeout deadline after restart, even when the workflow recalculates the remaining duration during replay.

### Actual behaviour

Reproduced on **5.4.1**: the sleep timed out and the workflow completed approximately **5.6 seconds early**. Observed timestamps on 2026-11-05, in UTC:

| Observation | Timestamp/value |
|---|---|
| Persisted `WaitStarted.timeoutTime` | `16:23:47.038449Z` |
| Current time during replay | `16:22:52.950229Z` |
| Recalculated sleep duration | `PT54.080272S` |
| `WaitTimedOut` payload timestamp | `16:23:41.420177Z` |
| Workflow completion event timestamp | `16:23:41.519Z` |

The verification used the same production workflow body under a test-only event/workflow name to avoid interference from another running application.

Source inspection of `WaitForDelegate` in 5.4.1 suggests the cause: replay computes `original step timestamp + newly supplied duration`, rather than using the persisted `timeoutTime`.
