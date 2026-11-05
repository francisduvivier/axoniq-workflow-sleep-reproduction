package io.axoniq.example.order;

import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class StoredEventLogger implements EventStorageEngine {
    private final EventStorageEngine delegate;

    public StoredEventLogger(EventStorageEngine delegate) {
        this.delegate = delegate;
    }

    public static void register(ComponentRegistry registry) {
        registry.registerDecorator(EventStorageEngine.class, 0,
                (configuration, name, delegate) -> new StoredEventLogger(delegate));
    }

    @Override
    public CompletableFuture<AppendTransaction<?>> appendEvents(AppendCondition condition,
                                                                  @Nullable ProcessingContext context,
                                                                  List<TaggedEventMessage<?>> events) {
        var batch = List.copyOf(events);
        return delegate.appendEvents(condition, context, batch)
                .thenApply(transaction -> loggingTransaction(transaction, batch));
    }

    private <R> AppendTransaction<R> loggingTransaction(AppendTransaction<R> transaction,
                                                        List<TaggedEventMessage<?>> events) {
        return new AppendTransaction<>() {
            @Override
            public CompletableFuture<R> commit() {
                return transaction.commit().thenApply(result -> {
                    for (var tagged : events) {
                        var event = tagged.event();
                        System.out.printf("Stored event: type=%s id=%s timestamp=%s payload=%s metadata=%s tags=%s%n",
                                event.type(), event.identifier(), event.timestamp(), event.payload(), event.metadata(),
                                tagged.tags());
                    }
                    return result;
                });
            }

            @Override
            public void rollback() {
                transaction.rollback();
            }

            @Override
            public CompletableFuture<ConsistencyMarker> afterCommit(R result) {
                return transaction.afterCommit(result);
            }
        };
    }

    @Override
    public MessageStream<EventMessage> source(SourcingCondition condition, @Nullable ProcessingContext context) {
        return delegate.source(condition, context);
    }

    @Override
    public MessageStream<EventMessage> stream(StreamingCondition condition) {
        return delegate.stream(condition);
    }

    @Override
    public CompletableFuture<TrackingToken> firstToken() {
        return delegate.firstToken();
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken() {
        return delegate.latestToken();
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(Instant at) {
        return delegate.tokenAt(at);
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }
}
