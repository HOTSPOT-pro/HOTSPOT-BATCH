package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ScoreResult;
import org.springframework.stereotype.Service;

import hotspot.batch.common.util.JsonConverter;
import hotspot.batch.jobs.usage_aggregation.job.UsageAggregationDateCalculator;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.LastWeekUsageListData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
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
    private final UsageAggregationDateCalculator dateCalculator;
    private final JsonConverter jsonConverter;

    @Override
    public Map<Long, WeeklyReportSnapshot> getBulkSnapshotList(Map<Long, LocalDate> lastReportDateMap) {
        if (lastReportDateMap.isEmpty()) return new HashMap<>();

        Map<LocalDate, List<Long>> dateGroupedSubIds = lastReportDateMap.entrySet().stream()
            .collect(Collectors.groupingBy(Map.Entry::getValue,
                Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

        Map<Long, WeeklyReportSnapshot> resultMap = new HashMap<>();
        
        dateGroupedSubIds.forEach((date, subIds) -> {
            log.info("[LastWeek-DB] Querying snapshots for {} subIds before current week start: {}", subIds.size(), date.plusDays(7));
            List<Map<String, Object>> rows = weeklyReportRepository.findLastWeekSnapshotsForComparison(subIds, date.plusDays(7));
            log.info("[LastWeek-DB] Found {} snapshot rows in DB.", rows.size());
            
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
            .scoreResult(jsonConverter.fromJson(convertPgObjectToString(row.get("score_data")), ScoreResult.class)) // ScoreResult 객체로 변환
            .summaryData(jsonConverter.fromJson(convertPgObjectToString(row.get("summary_data")), SummaryData.class))
            .usageListData(jsonConverter.fromJson(convertPgObjectToString(row.get("usage_list_data")), LastWeekUsageListData.class))
            .build();
    }

    /**
     * PostgreSQL의 JSONB 타입(PGobject)이나 String 타입을 안전하게 String으로 변환함
     */
    private String convertPgObjectToString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof org.postgresql.util.PGobject pgObj) {
            return pgObj.getValue();
        }
        // JDBC Template 설정에 따라 JSONB가 바로 String으로 넘어오는 경우도 처리
        if (obj instanceof String s) {
            return s;
        }
        // 예상치 못한 타입이므로 경고 로그를 남기고 null을 반환하여 JsonConverter가 처리하도록 함
        log.warn("Unexpected type encountered for JSONB column: {}", obj.getClass().getName());
        return null;
    }
}
