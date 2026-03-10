package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.reader;

import java.util.List;

import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.usage_aggregation.job.step.report_seed.dto.ReportSeedItem;

/**
 * Step1에서 WeeklyReport 생성 대상자를 읽어오는 reader
 * 현재는 skeleton 형태이며 추후 ReportTarget 조회 구현으로 교체 예정
 */
@Component
public class ReportSeedReader extends ListItemReader<ReportSeedItem> {

    public ReportSeedReader() {
        super(List.of());
    }
}
