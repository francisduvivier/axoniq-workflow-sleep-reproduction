package io.axoniq.example.order;

import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ShippingService {
    private static final Logger logger = LoggerFactory.getLogger(ShippingService.class);

    public static Map<String, Object> shipOrder(ProcessingContext context, Map<String, Object> payload) {
        logger.info("Shipping order: {}", payload.get("orderId"));
        return Map.of("shipped", true);
    }
}
