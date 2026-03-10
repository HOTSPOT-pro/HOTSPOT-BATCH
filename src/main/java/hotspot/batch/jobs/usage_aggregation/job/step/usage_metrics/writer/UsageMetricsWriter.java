package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsCommand;

/**
 * Step2 계산 결과를 WeeklyReport와 스냅샷 저장소에 반영하는 writer
 */
@Component
public class UsageMetricsWriter implements ItemWriter<UsageMetricsCommand> {

    @Override
    public void write(Chunk<? extends UsageMetricsCommand> chunk) {
        // Skeleton writer for future implementation.
    }
}
