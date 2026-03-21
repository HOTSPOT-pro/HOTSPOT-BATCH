package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.ScoreData;
import org.springframework.stereotype.Service;

import hotspot.batch.common.util.JsonConverter;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.LastWeekUsageListData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.SummaryData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.WeeklyReportSnapshot;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.LastWeekUsageService;
import hotspot.batch.jobs.usage_aggregation.repository.WeeklyReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 지난주 DB 리포트 스냅샷을 벌크로 가져오는 서비스 구현체
 * [최종 개선] DISTINCT ON 제거 쿼리 적용 및 불필요한 루프 최소화
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

        // 배치는 동일 주차를 처리하므로 첫 번째 데이터의 날짜를 기준 날짜로 사용함 (성능 최적화)
        LocalDate lastWeekMonday = lastReportDateMap.values().iterator().next();
        List<Long> subIds = List.copyOf(lastReportDateMap.keySet());

        Map<Long, WeeklyReportSnapshot> resultMap = new HashMap<>();
        
        // [최적화] 인덱스를 타는 날짜 타겟팅 쿼리 호출 (정렬 및 중복 제거 연산 제거됨)
        List<Map<String, Object>> rows = weeklyReportRepository.findLastWeekSnapshotsByDate(subIds, lastWeekMonday);
        
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

        return resultMap;
    }

    private WeeklyReportSnapshot mapRowToSnapshot(Map<String, Object> row) {
        return WeeklyReportSnapshot.builder()
            .totalUsage(row.get("total_usage") != null ? ((Number) row.get("total_usage")).longValue() : 0L)
            .scoreData(jsonConverter.fromJson(convertPgObjectToString(row.get("score_data")), ScoreData.class))
            .summaryData(jsonConverter.fromJson(convertPgObjectToString(row.get("summary_data")), SummaryData.class))
            .usageListData(jsonConverter.fromJson(convertPgObjectToString(row.get("usage_list_data")), LastWeekUsageListData.class))
            .build();
    }

    private String convertPgObjectToString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof org.postgresql.util.PGobject pgObj) {
            return pgObj.getValue();
        }
        if (obj instanceof String s) {
            return s;
        }
        return null;
    }
}
