package io.axoniq.example.timer;

import io.axoniq.framework.workflow.annotation.Workflow;
import io.axoniq.framework.workflow.dsl.simple.SimpleWorkflowContext;

import java.time.Duration;

public class TimerWorkflow {
    @Workflow(workflowName = "Timer", idProperty = "timerId", startOnEventClass = TimerScheduled.class)
    public void execute(SimpleWorkflowContext context) {
        long seconds = ((Number) context.workflowPayload().get("seconds")).longValue();
        var res = context.sleep("wait", step -> step.timeout(Duration.ofSeconds(seconds)));
        res.await();
    }
}
