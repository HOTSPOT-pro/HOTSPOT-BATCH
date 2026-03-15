package hotspot.batch.jobs.log_aggregation.scheduler;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

//@Component
public class LogAggregationSchedulerKeepAlive {

    private static final Logger log = LoggerFactory.getLogger(LogAggregationSchedulerKeepAlive.class);

    private final ApplicationArguments applicationArguments;

    public LogAggregationSchedulerKeepAlive(ApplicationArguments applicationArguments) {
        this.applicationArguments = applicationArguments;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void keepAlive() throws InterruptedException {
        if (hasManualJobExecutionRequest()) {
            return;
        }

        log.info("Log aggregation scheduler keep-alive enabled. Process will keep running.");
        new CountDownLatch(1).await();
    }

    private boolean hasManualJobExecutionRequest() {
        return findOption("job.name").isPresent() || findOption("spring.batch.job.name").isPresent();
    }

    private Optional<String> findOption(String key) {
        return Optional.ofNullable(applicationArguments.getOptionValues(key))
                .flatMap(values -> values.stream().findFirst());
    }
}
