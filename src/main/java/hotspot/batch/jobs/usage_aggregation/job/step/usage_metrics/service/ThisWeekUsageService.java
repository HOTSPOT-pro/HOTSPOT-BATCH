package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service;

import java.util.List;
import java.util.Map;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageData;

/**
 * 이번 주 Redis 사용량을 벌크로 가져오는 서비스
 */
public interface ThisWeekUsageService {
    /**
     * sub_id 리스트를 받아 Redis MGET 등으로 일괄 조회
     */
    Map<Long, UsageData> getBulkUsageList(List<Long> subIds);
}
