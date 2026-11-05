package io.axoniq.example.reproducer;

import io.axoniq.framework.workflow.annotation.Workflow;
import io.axoniq.framework.workflow.dsl.simple.SimpleWorkflowContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class SleepWorkflow {
    private final Clock clock;

    public SleepWorkflow() {
        this(Clock.systemUTC());
    }

    SleepWorkflow(Clock clock) {
        this.clock = clock;
    }

    @Workflow(workflowName = "SleepRestartReproducer", idProperty = "timerId", startOnEventClass = SleepScheduled.class)
    public void execute(SimpleWorkflowContext context) {
        var deadline = Instant.parse((String) context.workflowPayload().get("deadline"));
        var now = clock.instant();
        var remaining = Duration.between(now, deadline);
        var timeout = remaining.isNegative() ? Duration.ZERO : remaining;
        ReproducerApp.log("CALCULATED now=%s target=%s sleep=%s".formatted(
                ReproducerApp.time(now), ReproducerApp.time(deadline), timeout));
        context.sleep("wait", step -> step.timeout(timeout)).await();
    }
}
