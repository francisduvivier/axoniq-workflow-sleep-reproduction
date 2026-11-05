# Axon Workflow example

An executable companion to the [Axon Workflow Engine getting-started guide](https://docs.axoniq.io/axon-workflow-getting-started/5.4/), using Java 21, Maven and Axoniq Framework 5.4.1.

## Run the terminal app

Start Axon Server with Docker Compose:

```sh
docker compose up -d
```

The container starts before Axon Server is ready. On first startup, wait for the
`default: context default created` message:

```sh
docker compose logs -f axonserver
```

Press Ctrl+C to stop following the logs (the server keeps running), then run:

```sh
mvn -q compile exec:java -Dexec.mainClass=io.axoniq.example.timer.TimerApp
```

Enter a number of seconds to start a timer. The app prints each workflow and step event with a local-time timestamp, payload and metadata; enter `q` to quit. The workflow uses `sleep(...).await()` to wait for the deadline and can resume after the app restarts. The Axon Server web interface is at <http://localhost:8024>.

To experiment with recalculating a sleep duration on restart, enter `deadline 60`.
This separate workflow stores an absolute deadline and logs the duration calculated
from the current time. Quit the app about three seconds after scheduling, then start
it again without stopping Axon Server. Shutdown and startup can take several seconds,
so a longer duration leaves time to restart before the deadline. Compare the logged
deadline and `WaitTimedOut` event: the recalculated duration can make the wait end early.
This intentionally nondeterministic workflow is an experiment, not a recommended
timer implementation. A plain `10` uses the original fixed-duration workflow.

The Compose setup creates a DCB-enabled `default` context on a fresh server and stores events in Docker volumes. `docker compose stop` stops the server without deleting its data. The cluster template is applied only on first initialization; an existing non-DCB server needs a DCB context before the app can connect. This setup is for local learning, with access control disabled and ports bound to localhost.

Axon Server and the workflow engine are subject to Axoniq's licensing terms. The server reports a 12-hour unregistered trial; see [Axoniq pricing](https://www.axoniq.io/pricing) for free individual evaluation and registration.

## Run the tests

Install JDK 21 or newer and Maven 3.9, then run:

```sh
mvn test
```

The tests start a real workflow engine with an in-memory test fixture, publish an `OrderPlaced` event, and exercise both successful and unsuccessful stock reservation. No Axon Server is needed. The failure scenario deliberately logs a workflow failure.

Each committed event is printed to stdout with its type and version, ID, timestamp, payload, metadata, and storage tags. Look for `Stored event:` lines to follow the order and workflow lifecycle and step events. Payloads use their Java string representation. These logs include all metadata and may contain personal or sensitive data; use them only where that is appropriate.

`StoredEventLogger` is a reusable decorator for Axon's `EventStorageEngine`. To enable it in an application using a storage-engine-backed event store, add `configurer.componentRegistry(StoredEventLogger::register)` to its configuration. It logs new successful commits, not existing events or rolled-back appends. A custom event store that does not use the configured `EventStorageEngine` needs its own integration.

## Explore

`OrderFulfillmentWorkflow` starts when an order is placed and runs a durable `reserveStock` step. A failed reservation fails the workflow. The fixture lets you inspect step execution and substitute a step result; try changing the order, the result, or the workflow.

To simulate an engine restart during a ten-second sleep, run:

```sh
mvn -Dtest=WorkflowRestartTest test
```

The test uses a separate workflow and advances a test clock: three seconds before shutdown and seven seconds after restart, with no simulated downtime. It does not wait ten seconds in real time. Both engines share the in-memory event and token stores; this does not simulate a JVM or machine restart. Look for `>>>` markers and the stored events after restart. In the current setup, the timeout is recorded at its original deadline, but the subsequent workflow completion is rejected with an append conflict; the test prints whether completion was stored rather than treating timeout as successful recovery.

The current `OrderFulfillmentWorkflow` uses `sleep(String, Duration)`, which starts a sleep step without waiting for it in this framework version. The restart example uses `awaitSleep` to block until the deadline.

The guide also introduces `PaymentConfirmed` and payment, shipping and notification stubs. These are included for experimentation but are not connected to the workflow: the currently published guide ends after the first stock-reservation workflow. The original order tests use an in-memory fixture; only the terminal app connects to Axon Server.

The workflow specifies an explicit name and uses `startOnEventClass`: the guide's default workflow name was rejected by Axoniq Framework 5.4.1, and its unqualified event name did not match the Java record's qualified event name.
