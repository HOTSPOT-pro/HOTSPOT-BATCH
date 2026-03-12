package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service;

import java.util.List;
import java.util.Map;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReportSnapshot;

/**
 * 지난주 DB 리포트 스냅샷을 벌크로 가져오는 서비스
 */
public interface LastWeekUsageService {
    /**
     * sub_id 리스트를 받아 DB IN 절로 일괄 조회
     */
    Map<Long, WeeklyReportSnapshot> getBulkSnapshotList(List<Long> subIds);
}
