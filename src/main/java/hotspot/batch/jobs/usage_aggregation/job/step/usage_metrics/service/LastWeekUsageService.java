package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReportSnapshot;

/**
 * 지난주 DB 리포트 스냅샷을 벌크로 가져오는 서비스
 */
public interface LastWeekUsageService {
    /**
     * (subId -> lastReportDate) 맵을 받아 지난주 리포트 스냅샷을 일괄 조회
     */
    Map<Long, WeeklyReportSnapshot> getBulkSnapshotList(Map<Long, LocalDate> lastReportDateMap);
}
