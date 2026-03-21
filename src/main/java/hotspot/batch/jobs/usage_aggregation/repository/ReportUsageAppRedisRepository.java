package hotspot.batch.jobs.usage_aggregation.repository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * [최종 최적화] 대량 데이터 처리를 위해 스트림을 제거하고 전통적인 for 루프와 Early Exit 도입
 */
@Repository
@RequiredArgsConstructor
public class ReportUsageAppRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public List<AppUsage> findMonthlyAppUsage(Long subId) {
        LocalDate now = LocalDate.now(clock);
        String key = ReportUsageRedisKeyBuilder.monthlyAppUsage(subId, now);
        return getAppUsageFromZSet(key);
    }

    public List<AppUsage> findDailyAppUsage(Long subId, LocalDate date) {
        String key = ReportUsageRedisKeyBuilder.dailyAppUsage(subId, date);
        return getAppUsageFromZSet(key);
    }

    private List<AppUsage> getAppUsageFromZSet(String key) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);
        return parseTuples(tuples);
    }

    public Map<Long, List<DailyAppUsage>> findBulkWeeklyAppUsage(List<Long> subIds, LocalDate startDate, LocalDate endDate) {
        if (subIds.isEmpty()) return Collections.emptyMap();

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dates.add(date);
        }

        List<Object> pipelineResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long subId : subIds) {
                for (LocalDate date : dates) {
                    byte[] key = ReportUsageRedisKeyBuilder.dailyAppUsage(subId, date).getBytes();
                    connection.zSetCommands().zRevRangeWithScores(key, 0, -1);
                }
            }
            return null;
        });

        return mapResults(subIds, dates, pipelineResults);
    }

    /**
     * [최적화] 불필요한 객체 생성을 막기 위해 ArrayList 재사용 및 for 루프 사용
     */
    private Map<Long, List<DailyAppUsage>> mapResults(List<Long> subIds, List<LocalDate> dates, List<Object> results) {
        Map<Long, List<DailyAppUsage>> finalMap = new HashMap<>(subIds.size());
        int resultIndex = 0;

        for (Long subId : subIds) {
            List<DailyAppUsage> weeklyList = new ArrayList<>(dates.size());
            for (LocalDate date : dates) {
                Set<ZSetOperations.TypedTuple<String>> tuples = (Set<ZSetOperations.TypedTuple<String>>) results.get(resultIndex++);
                // 데이터가 없는 경우를 빠르게 처리
                List<AppUsage> parsed = parseTuples(tuples);
                weeklyList.add(new DailyAppUsage(date.toString(), parsed));
            }
            finalMap.put(subId, weeklyList);
        }
        return finalMap;
    }

    /**
     * [최적화] 스트림을 제거하여 대량 처리 시 CPU 및 GC 부하 절감
     */
    private List<AppUsage> parseTuples(Set<ZSetOperations.TypedTuple<String>> tuples) {
        if (tuples == null || tuples.isEmpty()) return Collections.emptyList();
        
        List<AppUsage> list = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String value = tuple.getValue();
            if (value == null) continue;
            
            Long appId = RedisValueParser.toLong(value);
            if (appId != null) {
                double usedKb = tuple.getScore() != null ? tuple.getScore() : 0D;
                list.add(new AppUsage(appId, UsageCalculator.kbToGb(usedKb)));
            }
        }
        return list;
    }
}
