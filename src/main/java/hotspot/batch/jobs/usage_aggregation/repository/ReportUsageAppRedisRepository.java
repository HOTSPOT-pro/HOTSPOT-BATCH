package hotspot.batch.jobs.usage_aggregation.repository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import hotspot.batch.common.util.UsageCalculator;
import hotspot.batch.common.util.redis.RedisValueParser;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.AppUsage;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.DailyAppUsage;
import lombok.RequiredArgsConstructor;

/**
 * 앱별 사용량 Redis 조회를 담당하는 리포지토리
 */
@Repository
@RequiredArgsConstructor
public class ReportUsageAppRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    /**
     * 특정 사용자의 이번 달 앱 사용량을 조회함
     */
    public List<AppUsage> findMonthlyAppUsage(Long subId) {
        LocalDate now = LocalDate.now(clock);
        String key = ReportUsageRedisKeyBuilder.monthlyAppUsage(subId, now);
        return getAppUsageFromZSet(key);
    }

    /**
     * 특정 사용자의 특정 날짜 앱 사용량을 조회함
     */
    public List<AppUsage> findDailyAppUsage(Long subId, LocalDate date) {
        String key = ReportUsageRedisKeyBuilder.dailyAppUsage(subId, date);
        return getAppUsageFromZSet(key);
    }

    /**
     * 단일 ZSet 키로부터 앱 사용량 리스트를 추출함
     */
    private List<AppUsage> getAppUsageFromZSet(String key) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet()
                        .reverseRangeWithScores(key, 0, -1);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        return tuples.stream()
                .filter(tuple -> tuple.getValue() != null)
                .map(this::mapToAppUsage)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 다수 사용자의 일주일치 앱 사용량 데이터를 Redis Pipeline으로 벌크 조회함
     * 1,000명 기준 약 7,000번의 조회를 단일 통신으로 처리함
     */
    public Map<Long, List<DailyAppUsage>> findBulkWeeklyAppUsage(List<Long> subIds, LocalDate startDate, LocalDate endDate) {
        if (subIds.isEmpty()) return Collections.emptyMap();

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dates.add(date);
        }

        List<Object> pipelineResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long subId : subIds) {
                for (LocalDate date : dates) {
                    String key = ReportUsageRedisKeyBuilder.dailyAppUsage(subId, date);
                    connection.zSetCommands().zRevRangeWithScores(key.getBytes(), 0, -1);
                }
            }
            return null;
        });

        return mapResults(subIds, dates, pipelineResults);
    }

    private Map<Long, List<DailyAppUsage>> mapResults(List<Long> subIds, List<LocalDate> dates, List<Object> results) {
        Map<Long, List<DailyAppUsage>> finalMap = new HashMap<>();
        int resultIndex = 0;

        for (Long subId : subIds) {
            List<DailyAppUsage> weeklyList = new ArrayList<>();
            for (LocalDate date : dates) {
                Set<ZSetOperations.TypedTuple<String>> tuples = (Set<ZSetOperations.TypedTuple<String>>) results.get(resultIndex++);
                weeklyList.add(new DailyAppUsage(date.toString(), parseTuples(tuples)));
            }
            finalMap.put(subId, weeklyList);
        }
        return finalMap;
    }

    private List<AppUsage> parseTuples(Set<ZSetOperations.TypedTuple<String>> tuples) {
        if (tuples == null || tuples.isEmpty()) return Collections.emptyList();
        return tuples.stream()
                .filter(tuple -> tuple.getValue() != null)
                .map(this::mapToAppUsage)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Redis 튜플 데이터를 AppUsage 도메인 객체로 변환함
     */
    private AppUsage mapToAppUsage(ZSetOperations.TypedTuple<String> tuple) {
        Long appId = RedisValueParser.toLong(tuple.getValue());
        if (appId == null) return null;

        double usedKb = tuple.getScore() != null ? tuple.getScore() : 0D;
        return new AppUsage(appId, UsageCalculator.kbToGb(usedKb));
    }
}
