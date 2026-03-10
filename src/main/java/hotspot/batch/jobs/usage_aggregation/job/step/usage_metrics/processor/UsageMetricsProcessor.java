package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.processor;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * TODO: Redis 집계 기반 지표/태그/점수 계산 로직으로 교체한다.
 */
@Component
public class UsageMetricsProcessor implements ItemProcessor<Long, Long> {

    @Override
    public Long process(Long item) {
        return item;
    }
}
