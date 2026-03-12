package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import hotspot.batch.common.util.JsonConverter;
import org.springframework.stereotype.Service;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.LastWeekSummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.LastWeekUsageListData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReportSnapshot;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.LastWeekUsageService;
import hotspot.batch.jobs.usage_aggregation.repository.WeeklyReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 지난주 DB 리포트 스냅샷을 벌크로 가져오는 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LastWeekUsageServiceImpl implements LastWeekUsageService {

    private final WeeklyReportRepository weeklyReportRepository;
    private final JsonConverter jsonConverter;

    @Override
    public Map<Long, WeeklyReportSnapshot> getBulkSnapshotList(Map<Long, LocalDate> lastReportDateMap) {
        if (lastReportDateMap.isEmpty()) return new HashMap<>();

        // (lastReportDate -> List<subId>)로 그룹핑하여 동일한 날짜를 가진 유저들을 한 번에 조회함
        Map<LocalDate, List<Long>> dateGroupedSubIds = lastReportDateMap.entrySet().stream()
            .collect(Collectors.groupingBy(Map.Entry::getValue,
                Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

        Map<Long, WeeklyReportSnapshot> resultMap = new HashMap<>();
        
        // 날짜별로 IN 절 쿼리를 실행하여 DB 부하를 분산시킴
        dateGroupedSubIds.forEach((date, subIds) -> {
            List<Map<String, Object>> rows = weeklyReportRepository.findLastWeekSnapshotsForComparison(subIds, date);
            
            for (Map<String, Object> row : rows) {
                try {
                    Long subId = ((Number) row.get("sub_id")).longValue();
                    WeeklyReportSnapshot snapshot = mapRowToSnapshot(row);
                    if (snapshot != null) {
                        resultMap.put(subId, snapshot);
                    }
                } catch (Exception e) {
                    log.warn("Failed to process last week report record for row: {}", row, e);
                }
            }
        });

        return resultMap;
    }

    private WeeklyReportSnapshot mapRowToSnapshot(Map<String, Object> row) {
        return WeeklyReportSnapshot.builder()
            .totalUsage(row.get("total_usage") != null ? ((Number) row.get("total_usage")).longValue() : 0L)
            .totalScore(row.get("total_score") != null ? (Integer) row.get("total_score") : 0)
            .summaryData(jsonConverter.fromJson((String) row.get("summary_data"), LastWeekSummaryData.class))
            .usageListData(jsonConverter.fromJson((String) row.get("usage_list_data"), LastWeekUsageListData.class))
            .build();
    }
}
