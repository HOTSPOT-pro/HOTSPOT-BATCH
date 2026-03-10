package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * TODO: WeeklyReport snapshot/update writer로 교체한다.
 */
@Component
public class UsageMetricsWriter implements ItemWriter<Long> {

    @Override
    public void write(Chunk<? extends Long> chunk) {
        // Skeleton writer for future implementation.
    }
}
