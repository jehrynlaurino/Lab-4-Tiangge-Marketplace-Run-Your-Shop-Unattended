package edu.cit.laurino;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Parent package deliberately scans all feature modules:
 * edu.cit.laurino.shop, edu.cit.laurino.inventory, edu.cit.laurino.notification,
 * edu.cit.laurino.supplier, and edu.cit.laurino.channel.
 * @EnableScheduling powers the supplier and channel modules' background jobs.
 * @EnableAsync lets the channel module's stock-sync listener run off the
 * calling thread, so a slow Tiangge call can never stall or roll back an
 * Inventory transaction.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ShopApplication {
    private static final Logger log = LoggerFactory.getLogger(ShopApplication.class);

    private final InstanceContext instanceContext;

    public ShopApplication(InstanceContext instanceContext) {
        this.instanceContext = instanceContext;
    }

    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    void logInstanceId() {
        log.info("Application instance ID: {} (started {})", instanceContext.instanceId(), instanceContext.startedAt());
    }
}
