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

    /**
     * 다수 사용자의 지정된 기간(일주일) 동안의 시간대별 사용량 데이터를 Redis Pipeline으로 벌크 조회함
     * 앱별 사용량 조회 방식과 동일하게 startDate부터 endDate까지의 모든 데이터를 한 번에 가져옴
     */
    public Map<Long, List<DailyHourlyUsage>> findBulkWeeklyHourlyUsage(List<Long> subIds, LocalDate startDate, LocalDate endDate) {
        if (subIds.isEmpty()) return Collections.emptyMap();

        // 1. 조회 기간 내의 날짜 리스트 생성 (앱별 조회 로직과 동일)
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dates.add(date);
        }

        log.info("[Redis-Hourly] Fetching data for {} subIds from {} to {}", subIds.size(), startDate, endDate);

        // 2. Redis Pipeline 실행: 1,000명 기준 7일치 Hash 데이터를 단일 네트워크 통신으로 조회
        List<Object> pipelineResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long subId : subIds) {
                for (LocalDate date : dates) {
                    // 키 빌더를 통해 usage:3hourly:{subId}:{YYYYMMDD} 키 생성
                    String key = ReportUsageRedisKeyBuilder.dailyHourlyUsage(subId, date);
                    connection.hashCommands().hGetAll(key.getBytes());
                }
            }
            return null;
        });

        // 3. 결과 매핑 및 반환
        return mapResults(subIds, dates, pipelineResults);
    }

    /**
     * 파이프라인 응답 결과를 유저별 일주일치 리스트로 재구성함
     */
    private Map<Long, List<DailyHourlyUsage>> mapResults(List<Long> subIds, List<LocalDate> dates, List<Object> results) {
        Map<Long, List<DailyHourlyUsage>> finalMap = new HashMap<>();
        int resultIndex = 0;

        for (Long subId : subIds) {
            List<DailyHourlyUsage> weeklyList = new ArrayList<>();
            for (LocalDate date : dates) {
                // Redis Hash 결과(String 매핑)를 맵으로 수신
                Map<String, String> rawMap = (Map<String, String>) results.get(resultIndex++);

                if (rawMap == null || rawMap.isEmpty()) {
                    log.debug("[Redis-Hourly] No data found for subId: {}, date: {}", subId, date);
                }

                weeklyList.add(new DailyHourlyUsage(date.toString(), parseHourlyMap(rawMap)));
            }

            long userTotal = weeklyList.stream()
                    .flatMap(d -> d.hourlyUsage().values().stream())
                    .mapToLong(Long::longValue).sum();
            log.info("[Redis-Hourly] SubId: {} - Total weekly hourly usage: {} KB", subId, userTotal);

            finalMap.put(subId, weeklyList);
        }
        return finalMap;
    }

    /**
     * 00시부터 21시까지 3시간 단위 필드를 순회하며 사용량 데이터를 파싱함
     */
    private Map<Integer, Long> parseHourlyMap(Map<String, String> raw) {
        Map<Integer, Long> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;

        for (int h = HOUR_START; h < HOUR_END; h += HOUR_INTERVAL) {
            String field = String.format(HOURLY_USAGE_FIELD_FORMAT, h);
            String value = raw.get(field);
            // 데이터가 없는 경우 0L로 처리하여 연산의 안정성 확보
            map.put(h, value == null ? 0L : Long.parseLong(value));
        }
        return map;
    }
}
