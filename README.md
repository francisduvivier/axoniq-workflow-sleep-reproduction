# Axon Workflow example

An executable companion to the [Axon Workflow Engine getting-started guide](https://docs.axoniq.io/axon-workflow-getting-started/5.4/), using Java 21, Maven and Axoniq Framework 5.4.1.

## Run

Install JDK 21 or newer and Maven 3.9, then run:

```sh
mvn test
```

The tests start a real workflow engine with an in-memory test fixture, publish an `OrderPlaced` event, and exercise both successful and unsuccessful stock reservation. No Axon Server is needed. The failure scenario deliberately logs a workflow failure.

## Explore

`OrderFulfillmentWorkflow` starts when an order is placed and runs a durable `reserveStock` step. A failed reservation fails the workflow. The fixture lets you inspect step execution and substitute a step result; try changing the order, the result, or the workflow.

The guide also introduces `PaymentConfirmed` and payment, shipping and notification stubs. These are included for experimentation but are not connected to the workflow: the currently published guide ends after the first stock-reservation workflow. There is no standalone server or persistent event store in this example. The in-memory fixture is for learning and tests, not crash recovery across process restarts.

The workflow specifies an explicit name and uses `startOnEventClass`: the guide's default workflow name is rejected by Axoniq Framework 5.4.1, and its unqualified event name does not match the Java record's qualified event name.
