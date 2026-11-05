package io.axoniq.example.order;

public record PaymentConfirmed(String orderId, String transactionId) {}
