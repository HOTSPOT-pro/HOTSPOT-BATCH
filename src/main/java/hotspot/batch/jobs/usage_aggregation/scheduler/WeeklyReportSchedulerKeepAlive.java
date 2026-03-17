package hotspot.batch.jobs.usage_aggregation.scheduler;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 스케줄러 실행 시 애플리케이션이 즉시 종료되지 않도록 유지하는 컴포넌트
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "batch.weekly-report.scheduler.keep-alive", havingValue = "true")
public class WeeklyReportSchedulerKeepAlive {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportSchedulerKeepAlive.class);

    private final ApplicationArguments applicationArguments;

    @EventListener(ApplicationReadyEvent.class)
    public void keepAlive() throws InterruptedException {
        if (hasManualJobExecutionRequest()) {
            return;
        }

        log.info("Weekly report scheduler keep-alive enabled. Process will keep running.");
        // 메인 스레드를 대기 상태로 유지하여 스케줄러가 계속 동작하게 함
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
