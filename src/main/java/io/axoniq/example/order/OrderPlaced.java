package io.axoniq.example.order;

public record OrderPlaced(String orderId, String customerId, String email, double amount) {}
