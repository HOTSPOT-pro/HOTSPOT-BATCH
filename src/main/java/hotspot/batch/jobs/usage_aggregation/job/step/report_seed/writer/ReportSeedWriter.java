package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.WeeklyReportSeedCommand;

/**
 * Step1에서 생성한 WeeklyReport seed 명령을 저장하는 writer
 * 추후 batch insert/upsert 구현이 들어갈 예정
 */
@Component
public class ReportSeedWriter implements ItemWriter<WeeklyReportSeedCommand> {

    @Override
    public void write(Chunk<? extends WeeklyReportSeedCommand> chunk) {
        // Skeleton writer for future implementation.
    }
}
