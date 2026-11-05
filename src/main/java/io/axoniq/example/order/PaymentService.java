package io.axoniq.example.order;

import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class PaymentService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    public static Map<String, Object> initiatePayment(ProcessingContext context, Map<String, Object> payload) {
        logger.info("Initiating payment for order: {}", payload.get("orderId"));
        return Map.of();
    }
}
