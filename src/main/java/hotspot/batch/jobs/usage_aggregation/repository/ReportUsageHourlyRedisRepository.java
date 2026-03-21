package hotspot.batch.jobs.usage_aggregation.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.DailyHourlyUsage;
import lombok.RequiredArgsConstructor;

/**
 * 3시간 단위 시간대별 사용량 Redis 조회를 담당하는 리포지토리
 * [최종 최적화] 스트림 및 불필요한 sum 연산 제거로 Redis 응답 처리 속도 극대화
 */
@Repository
@RequiredArgsConstructor
public class ReportUsageHourlyRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(ReportUsageHourlyRedisRepository.class);

    private static final String HOURLY_USAGE_FIELD_FORMAT = "%02d_used";
    private static final int HOUR_START = 0;
    private static final int HOUR_END = 24;
    private static final int HOUR_INTERVAL = 3;

    private final StringRedisTemplate redisTemplate;

    public Map<Long, List<DailyHourlyUsage>> findBulkWeeklyHourlyUsage(List<Long> subIds, LocalDate startDate, LocalDate endDate) {
        if (subIds.isEmpty()) return Collections.emptyMap();

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dates.add(date);
        }

        List<Object> pipelineResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long subId : subIds) {
                for (LocalDate date : dates) {
                    byte[] key = ReportUsageRedisKeyBuilder.dailyHourlyUsage(subId, date).getBytes();
                    connection.hashCommands().hGetAll(key);
                }
            }
            return null;
        });

        return mapResults(subIds, dates, pipelineResults);
    }

    /**
     * [최적화] 스트림과 불필요한 로그/합계 연산을 제거하여 CPU 부하 절감
     */
    private Map<Long, List<DailyHourlyUsage>> mapResults(List<Long> subIds, List<LocalDate> dates, List<Object> results) {
        Map<Long, List<DailyHourlyUsage>> finalMap = new HashMap<>(subIds.size());
        int resultIndex = 0;

        for (Long subId : subIds) {
            List<DailyHourlyUsage> weeklyList = new ArrayList<>(dates.size());
            for (LocalDate date : dates) {
                Map<String, String> rawMap = (Map<String, String>) results.get(resultIndex++);
                // [최적화] parseHourlyMap 내부에서 빈 맵 처리 효율화
                weeklyList.add(new DailyHourlyUsage(date.toString(), parseHourlyMap(rawMap)));
            }
            finalMap.put(subId, weeklyList);
        }
        return finalMap;
    }

    /**
     * [최적화] String.format 생략 및 직접 키 매핑으로 속도 향상
     */
    private Map<Integer, Long> parseHourlyMap(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyMap();

        Map<Integer, Long> map = new HashMap<>(8);
        // 00, 03, 06, 09, 12, 15, 18, 21 직접 매핑 (루프 내 format 오버헤드 제거)
        map.put(0, parseLong(raw.get("00_used")));
        map.put(3, parseLong(raw.get("03_used")));
        map.put(6, parseLong(raw.get("06_used")));
        map.put(9, parseLong(raw.get("09_used")));
        map.put(12, parseLong(raw.get("12_used")));
        map.put(15, parseLong(raw.get("15_used")));
        map.put(18, parseLong(raw.get("18_used")));
        map.put(21, parseLong(raw.get("21_used")));
        
        return map;
    }

    private Long parseLong(String val) {
        if (val == null) return 0L;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
