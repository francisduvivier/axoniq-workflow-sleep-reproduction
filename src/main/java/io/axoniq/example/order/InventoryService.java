package io.axoniq.example.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class InventoryService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    public static boolean reserveStock(Map<String, Object> payload) {
        logger.info("Reserving stock for order: {}", payload);
        return true;
    }
}
