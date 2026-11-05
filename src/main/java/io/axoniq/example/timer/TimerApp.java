package io.axoniq.example.timer;

import io.axoniq.framework.workflow.configuration.WorkflowConfigurer;
import io.axoniq.framework.workflow.configuration.WorkflowModule;
import io.axoniq.framework.workflow.dsl.simple.SimpleWorkflowContext;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.eventhandling.gateway.EventGateway;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TimerApp {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    static void print(String message) {
        System.out.printf("%s %s%n", LocalDateTime.now().format(TIMESTAMP), message);
    }

    public static void main(String[] args) {
        var module = WorkflowModule.defaults("timer-workflows", SimpleWorkflowContext.class)
                .definition(d -> d.autodetected(c -> new TimerWorkflow()));
        var configuration = WorkflowConfigurer.create().registerWorkflowModule(module).build();
        try {
            var store = configuration.getComponent(EventStore.class);
            var subscription = store.subscribe((events, context) -> {
                for (var event : events) {
                    if (event.metadata().containsKey("workflowId")) {
                        Runnable print = () -> print("[workflow %s] %s payload=%s metadata=%s".formatted(
                                event.metadata().get("workflowId"), event.type(), event.payload(), event.metadata()));
                        if (context == null || context.isCommitted()) {
                            print.run();
                        } else {
                            context.runOnAfterCommit(ignored -> print.run());
                        }
                    }
                }
                return CompletableFuture.completedFuture(null);
            });
            try (var input = new Scanner(System.in)) {
                configuration.start();
                var gateway = configuration.getComponent(EventGateway.class);
                print("Enter a number of seconds to start a timer, or q to quit.");
                while (input.hasNextLine()) {
                    String line = input.nextLine().trim();
                    if (line.equalsIgnoreCase("q")) {
                        break;
                    }
                    try {
                        long seconds = Long.parseLong(line);
                        if (seconds <= 0 || seconds > 31_536_000) {
                            print("Enter a number between 1 and 31536000.");
                            continue;
                        }
                        var event = new TimerScheduled(UUID.randomUUID().toString(), seconds);
                        gateway.publish(List.of(event)).join();
                        print("Scheduled timer %s for %d seconds.".formatted(event.timerId(), seconds));
                    } catch (NumberFormatException e) {
                        print("Enter a whole number of seconds, or q to quit.");
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
