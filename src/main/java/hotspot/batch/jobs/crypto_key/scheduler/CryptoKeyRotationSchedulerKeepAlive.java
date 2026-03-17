package hotspot.batch.jobs.crypto_key.scheduler;

import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import hotspot.batch.common.util.ManualJobExecutionChecker;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "batch.crypto-key.scheduler.keep-alive", havingValue = "true")
public class CryptoKeyRotationSchedulerKeepAlive {

    private static final Logger log = LoggerFactory.getLogger(CryptoKeyRotationSchedulerKeepAlive.class);

    private final ManualJobExecutionChecker manualJobExecutionChecker;

    @EventListener(ApplicationReadyEvent.class)
    public void keepAlive() throws InterruptedException {
        if (manualJobExecutionChecker.hasManualJobExecutionRequest()) {
            return;
        }

        log.info("Crypto key rotation scheduler keep-alive enabled. Process will keep running.");
        new CountDownLatch(1).await();
    }
}
