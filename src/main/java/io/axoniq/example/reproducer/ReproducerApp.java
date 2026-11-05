package io.axoniq.example.reproducer;

import io.axoniq.framework.workflow.configuration.WorkflowConfigurer;
import io.axoniq.framework.workflow.configuration.WorkflowModule;
import io.axoniq.framework.workflow.dsl.simple.SimpleWorkflowContext;
import io.axoniq.framework.workflow.runtime.execution.WorkflowEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.eventhandling.gateway.EventGateway;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ReproducerApp {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    static String time(Instant instant) {
        return TIME.format(instant);
    }

    static void log(String message) {
        System.out.println(time(Instant.now()) + " " + message);
    }

    static boolean report(Instant expected, Instant actual) {
        long earlyMillis = Duration.between(actual, expected).toMillis();
        boolean reproduced = earlyMillis > 1_000;
        log(String.format(Locale.ROOT, "%s: timeout occurred %.3f seconds early (expected=%s actual=%s)",
                          reproduced ? "BUG REPRODUCED" : "BUG NOT REPRODUCED",
                          earlyMillis / 1_000.0, time(expected), time(actual)));
        return reproduced;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || (!args[0].equals("schedule") && !args[0].equals("resume")) || args[1].isBlank()) {
            throw new IllegalArgumentException("Usage: schedule TIMER_ID SECONDS | resume TIMER_ID PERSISTED_DEADLINE");
        }
        boolean schedule = args[0].equals("schedule");
        long seconds = schedule ? Long.parseLong(args[2]) : 0;
        if (schedule && (seconds <= 0 || seconds > 3_600)) {
            throw new IllegalArgumentException("Timer duration must be between 1 and 3600 seconds.");
        }
        Instant expected = schedule ? null : Instant.parse(args[2]);
        String timerId = args[1];
        var started = new CompletableFuture<Instant>();
        var timedOut = new CompletableFuture<Instant>();
        var completed = new CompletableFuture<Instant>();
        var module = WorkflowModule.defaults("sleep-restart-reproducer", SimpleWorkflowContext.class)
                .definition(d -> d.autodetected(c -> new SleepWorkflow()));
        var configuration = WorkflowConfigurer.create().registerWorkflowModule(module).build();
        try {
            var subscription = configuration.getComponent(EventStore.class).subscribe((events, context) -> {
                for (var event : events) {
                    if (!timerId.equals(event.metadata().get("workflowId"))) {
                        continue;
                    }
                    Runnable record = () -> {
                        if ("STARTED".equals(event.metadata().get("stepType"))) {
                            if (!(event.payload() instanceof Map<?, ?> payload)) {
                                throw new IllegalStateException("Unexpected sleep payload: " + event.payload());
                            }
                            started.complete(Instant.parse(Objects.requireNonNull(payload.get("timeoutTime")).toString()));
                        } else if ("TIMED_OUT".equals(event.metadata().get("stepType"))) {
                            var actual = Instant.parse(event.payload().toString());
                            log("TIMED_OUT actual=" + time(actual));
                            timedOut.complete(actual);
                        } else if ("COMPLETED".equals(event.metadata().get("workflowStatus"))) {
                            completed.complete(event.timestamp());
                        } else if ("FAILED".equals(event.metadata().get("workflowStatus"))
                                || "FAILED".equals(event.metadata().get("stepType"))) {
                            var failure = new IllegalStateException("Workflow failed: " + event.payload());
                            log(failure.getMessage());
                            started.completeExceptionally(failure);
                            completed.completeExceptionally(failure);
                        }
                    };
                    if (context == null || context.isCommitted()) {
                        record.run();
                    } else {
                        context.runOnAfterCommit(ignored -> record.run());
                    }
                }
                return CompletableFuture.completedFuture(null);
            });
            try {
                configuration.start();
                if (schedule) {
                    var deadline = Instant.now().plusSeconds(seconds);
                    configuration.getComponent(EventGateway.class)
                            .publish(List.of(new SleepScheduled(timerId, deadline.toString()))).join();
                    log("SCHEDULED timer=%s target=%s".formatted(timerId, time(deadline)));
                    var persistedDeadline = started.get(30, TimeUnit.SECONDS);
                    Files.writeString(Path.of("target/reproducer-deadline.txt"), persistedDeadline.toString());
                    log("SLEEP_PERSISTED deadline=" + time(persistedDeadline));
                    new CountDownLatch(1).await();
                } else {
                    log("RESUMING timer=%s persistedDeadline=%s".formatted(timerId, time(expected)));
                    var completionTime = completed.get(90, TimeUnit.SECONDS);
                    log("WORKFLOW_COMPLETED at=" + time(completionTime));
                    // The committed event precedes driver termination; wait before shutting down the engine.
                    var engine = configuration.getModuleConfiguration(module.name())
                            .orElseThrow(() -> new IllegalStateException("Workflow module is missing."))
                            .getComponent(WorkflowEngine.class, "WorkflowEngine[" + module.name() + "]");
                    long terminationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                    while (engine.workflowExecutions().stream()
                            .anyMatch(execution -> timerId.equals(execution.workflowId()))) {
                        if (System.nanoTime() >= terminationDeadline) {
                            throw new TimeoutException("Workflow driver did not terminate after completion.");
                        }
                        Thread.sleep(10);
                    }
                    if (!report(expected, timedOut.get(5, TimeUnit.SECONDS))) {
                        throw new IllegalStateException("Expected a timeout more than one second early.");
                    }
                }
            } finally {
                subscription.cancel();
            }
        } finally {
            configuration.shutdown();
        }
    }
}
