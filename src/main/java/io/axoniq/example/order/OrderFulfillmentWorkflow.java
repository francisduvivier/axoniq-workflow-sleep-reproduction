package io.axoniq.example.order;

import io.axoniq.framework.workflow.annotation.Workflow;
import io.axoniq.framework.workflow.dsl.simple.SimpleWorkflowContext;

import java.time.Duration;
import java.util.Map;

import static io.axoniq.framework.workflow.dsl.api.Payload.payload;

public class OrderFulfillmentWorkflow {
    @Workflow(workflowName = "OrderFulfillment", idProperty = "orderId", startOnEventClass = OrderPlaced.class)
    public void execute(SimpleWorkflowContext context) {
        var reservation = context.awaitExecute("reserveStock",
                payload("customerId", context.workflowPayload().get("customerId"),
                        "amount", context.workflowPayload().get("amount")).getValues(),
                (pc, input) -> Map.of("reserved", InventoryService.reserveStock(input)));
        context.sleep("mytestingwait", Duration.ofSeconds(2));
        if (!Boolean.TRUE.equals(reservation.get("reserved"))) {
            context.fail(new RuntimeException("Stock unavailable"));
        }
    }
}
