package io.axoniq.example.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    public static void sendConfirmation(String email) {
        logger.info("Sending order confirmation to {}.", email);
    }
}
