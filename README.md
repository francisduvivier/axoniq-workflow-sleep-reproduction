# Axon Workflow example

An executable companion to the [Axon Workflow Engine getting-started guide](https://docs.axoniq.io/axon-workflow-getting-started/5.4/), using Java 21, Maven and Axoniq Framework 5.4.1.

## Run

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

The guide also introduces `PaymentConfirmed` and payment, shipping and notification stubs. These are included for experimentation but are not connected to the workflow: the currently published guide ends after the first stock-reservation workflow. There is no standalone server or persistent event store in this example. The in-memory fixture is for learning and tests, not crash recovery across process restarts.

The workflow specifies an explicit name and uses `startOnEventClass`: the guide's default workflow name is rejected by Axoniq Framework 5.4.1, and its unqualified event name does not match the Java record's qualified event name.
