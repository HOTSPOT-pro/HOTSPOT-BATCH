package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.reader;

import java.util.List;

import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsItem;

/**
 * Step2에서 지표 계산이 필요한 WeeklyReport 대상을 읽어오는 reader
 * 현재는 skeleton 형태이며 추후 PENDING 대상 조회로 교체 예정
 */
@Component
public class UsageMetricsReader extends ListItemReader<UsageMetricsItem> {

    public UsageMetricsReader() {
        super(List.of());
    }
}
