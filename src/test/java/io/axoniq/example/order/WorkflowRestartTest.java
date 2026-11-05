package io.axoniq.example.order;

import io.axoniq.framework.workflow.annotation.Workflow;
import io.axoniq.framework.workflow.configuration.WorkflowModule;
import io.axoniq.framework.workflow.dsl.simple.SimpleWorkflowContext;
import io.axoniq.framework.workflow.runtime.test.fixture.WorkflowTestDriver;
import io.axoniq.framework.workflow.runtime.test.utils.ManualWorkflowScheduler;
import io.axoniq.framework.workflow.runtime.test.utils.TestClock;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.TerminalEventMessage;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkflowRestartTest {
    public static class SleepingWorkflow {
        @Workflow(workflowName = "RestartExample", idProperty = "orderId", startOnEventClass = OrderPlaced.class)
        public void execute(SimpleWorkflowContext context) {
            context.awaitSleep("longSleep", step -> step.timeout(Duration.ofSeconds(10)));
        }
    }

    @Test
    void resumesSleepAfterEngineRestart() throws InterruptedException {
        var storage = new InMemoryEventStorageEngine();
        var tokens = new InMemoryTokenStore();
        var clock = new TestClock();
        var firstScheduler = new ObservedScheduler();
        var first = start(storage, tokens, clock, firstScheduler);
        try {
            first.publishEvent(new OrderPlaced("restart-1", "customer-1", "joe@example.com", 99.95));
            first.executionExists();
            first.awaitEventually("Sleep should start", () -> first.testingState().waitingIn("longSleep"));
            assertTrue(firstScheduler.scheduled.await(10, TimeUnit.SECONDS));
            System.out.println(">>> Engine 1: sleep started; advancing 3 seconds");
            first.timePasses(Duration.ofSeconds(3));
            first.testingState().waitingIn("longSleep");
        } finally {
            first.shutdown();
        }

        System.out.println(">>> Restarting engine after 3 seconds");
        var secondScheduler = new ObservedScheduler();
        var restarted = start(storage, tokens, clock, secondScheduler);
        try {
            assertTrue(secondScheduler.scheduled.await(10, TimeUnit.SECONDS), "Restored sleep was not scheduled");
            System.out.println(">>> Engine 2: sleep restored; advancing 6 seconds");
            restarted.timePasses(Duration.ofSeconds(6));
            assertTrue(restarted.workflowTestServices().workflowEngine().workflowExecutions().stream()
                    .anyMatch(execution -> execution.workflowId().equals("restart-1")));
            assertFalse(workflowEvents(storage).stream()
                    .anyMatch(event -> event.type().qualifiedName().toString().endsWith(".LongSleepTimedOut")));
            System.out.println(">>> Advancing the final second");
            restarted.timePasses(Duration.ofSeconds(1));
            restarted.noExecution();
            var events = workflowEvents(storage);
            assertTrue(events.stream().anyMatch(event -> event.type().qualifiedName().toString().endsWith(".LongSleepTimedOut")),
                    "Stored events: " + events);
            boolean completed = events.stream()
                    .anyMatch(event -> event.type().qualifiedName().toString().endsWith(".RestartExampleCompleted"));
            System.out.println(">>> Stored workflow events after restart:");
            events.stream().filter(event -> !(event instanceof TerminalEventMessage)).forEach(event ->
                    System.out.printf("    type=%s id=%s timestamp=%s payload=%s metadata=%s%n",
                            event.type(), event.identifier(), event.timestamp(), event.payload(), event.metadata()));
            System.out.println(">>> Workflow completed: " + completed);
        } finally {
            restarted.shutdown();
        }
    }

    private List<EventMessage> workflowEvents(InMemoryEventStorageEngine storage) {
        return storage.source(SourcingCondition.conditionFor(EventCriteria.havingTags("workflowId", "restart-1")))
                .collect(ArrayList<EventMessage>::new, List::add)
                .join();
    }

    private WorkflowTestDriver start(InMemoryEventStorageEngine storage, TokenStore tokens, TestClock clock,
                                     ManualWorkflowScheduler scheduler) {
        var module = WorkflowModule.defaults("restart-example", SimpleWorkflowContext.class)
                .definition(d -> d.autodetected(c -> new SleepingWorkflow()));
        return WorkflowTestDriver.stepper(module, false, configurer -> configurer.componentRegistry(registry -> {
            registry.registerComponent(EventStorageEngine.class, c -> storage);
            registry.registerComponent(TokenStore.class, c -> tokens);
            registry.registerComponent(TestClock.class, c -> clock);
            registry.registerComponent(ManualWorkflowScheduler.class, c -> scheduler);
            StoredEventLogger.register(registry);
        }));
    }

    private static class ObservedScheduler extends ManualWorkflowScheduler {
        private final CountDownLatch scheduled = new CountDownLatch(1);

        @Override
        public ScheduledTask schedule(Instant deadline) {
            var task = super.schedule(deadline);
            scheduled.countDown();
            return task;
        }
    }
}
