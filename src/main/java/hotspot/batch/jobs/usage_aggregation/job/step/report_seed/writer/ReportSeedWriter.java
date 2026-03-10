package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * TODO: WeeklyReport batch insert/upsert writer로 교체한다.
 */
@Component
public class ReportSeedWriter implements ItemWriter<Long> {

    @Override
    public void write(Chunk<? extends Long> chunk) {
        // Skeleton writer for future implementation.
    }
}
