package io.axoniq.example.timer;

import io.axoniq.framework.axonserver.connector.configuration.AxonServerConfigurationEnhancer;
import io.axoniq.framework.workflow.configuration.WorkflowModule;
import io.axoniq.framework.workflow.dsl.api.WorkflowStatus;
import io.axoniq.framework.workflow.dsl.simple.SimpleWorkflowContext;
import io.axoniq.framework.workflow.runtime.test.fixture.WorkflowTestFixture;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class TimerWorkflowTest {
    @Test
    void waitsForTheScheduledDuration() {
        var module = WorkflowModule.defaults("timer-test", SimpleWorkflowContext.class)
                .definition(d -> d.autodetected(c -> new TimerWorkflow()));
        var fixture = WorkflowTestFixture.of(module, configurer -> configurer.componentRegistry(
                registry -> registry.disableEnhancer(AxonServerConfigurationEnhancer.class)));
        try {
            fixture.when().publishEvent(new TimerScheduled("timer-1", 10)).executionExists();
            fixture.then().waitingIn("wait");
            fixture.when().timePasses(Duration.ofSeconds(9));
            fixture.then().waitingIn("wait");
            fixture.when().timePasses(Duration.ofSeconds(1));
            fixture.then().workflowFinished(WorkflowStatus.COMPLETED);
        } finally {
            fixture.then().stop();
        }
    }
}
