package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import hotspot.batch.jobs.usage_aggregation.job.UsageAggregationDateCalculator;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.DailyUsageRecord;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.ThisWeekUsageService;
import lombok.RequiredArgsConstructor;

/**
 * 이번 주 Redis 사용량을 벌크로 가져오는 서비스 구현체.
 * 100만 건 규모의 데이터를 효율적으로 처리하기 위해 Redis Pipeline 기술을 적용함.
 */
@Service
@RequiredArgsConstructor
public class ThisWeekUsageServiceImpl implements ThisWeekUsageService {

    private final StringRedisTemplate redisTemplate;
    private final UsageAggregationDateCalculator dateCalculator;

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String DAILY_KEY_PREFIX = "usage:sub:";
    private static final String THREE_HOURLY_KEY_PREFIX = "usage:3hourly:";

    /**
     * sub_id 리스트를 받아 이번 주(지난 7일간)의 모든 사용량 데이터를 Redis Pipeline으로 일괄 조회한다.
     * 단일 네트워크 통신으로 수천 건의 명령을 처리하여 N+1 문제를 방지함.
     */
    @Override
    public Map<Long, UsageData> getBulkUsageList(List<Long> subIds) {
        // 1. 조회 대상 날짜 리스트 생성 (배치 실행 기준일로부터 역산하여 7일치 준비)
        LocalDate baseDate = dateCalculator.getBaseDate(null);
        List<LocalDate> targetDates = new ArrayList<>();
        for (int i = 7; i >= 1; i--) {
            targetDates.add(baseDate.minusDays(i));
        }

        /**
         * 2. Redis Pipeline 실행
         * - connection당 수백 개의 HGETALL 명령어를 버퍼링하여 한꺼번에 전송함.
         * - (RedisCallback<Object>) 캐스팅을 통해 파이프라인 호출의 모호성을 해결.
         */
        List<Object> pipelineResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long subId : subIds) {
                for (LocalDate date : targetDates) {
                    String dateStr = date.format(YYYYMMDD);
                    
                    // (1) 일별 카테고리별 사용량 조회: usage:sub:{subId}:{YYYYMMDD}
                    connection.hashCommands().hGetAll((DAILY_KEY_PREFIX + subId + ":" + dateStr).getBytes());

                    // (2) 3시간 단위 시계열 사용량 조회: usage:3hourly:{subId}:{YYYYMMDD}
                    connection.hashCommands().hGetAll((THREE_HOURLY_KEY_PREFIX + subId + ":" + dateStr).getBytes());
                }
            }
            return null; // 결과는 파이프라인 종료 후 List<Object>로 일괄 반환됨
        });

        // 3. Pipeline 결과(Raw 데이터 리스트)를 비즈니스 객체인 UsageData 맵으로 재구성하여 반환
        return mapResults(subIds, targetDates, pipelineResults);
    }

    /**
     * 파이프라인에서 반환된 순서(HGETALL 명령 순서)와 subIds/targetDates를 매칭하여 객체 조립
     */
    private Map<Long, UsageData> mapResults(List<Long> subIds, List<LocalDate> targetDates, List<Object> results) {
        Map<Long, UsageData> finalMap = new HashMap<>();
        int resultIndex = 0;

        for (Long subId : subIds) {
            List<DailyUsageRecord> dailyRecords = new ArrayList<>();

            for (LocalDate date : targetDates) {
                // (1) 일별 데이터 파싱: Redis Hash의 모든 필드(카테고리)를 Map<String, Long>으로 변환
                Map<byte[], byte[]> dailyRaw = (Map<byte[], byte[]>) results.get(resultIndex++);
                Map<String, Long> categoryMap = parseAllFields(dailyRaw);

                // (2) 3시간별 데이터 파싱: 00_used, 03_used... 등 시계열 데이터 추출
                Map<byte[], byte[]> hourlyRaw = (Map<byte[], byte[]>) results.get(resultIndex++);
                Map<Integer, Long> hourlyMap = parseThreeHourly(hourlyRaw);

                dailyRecords.add(new DailyUsageRecord(
                    date.toString(),
                    date.getDayOfWeek().name(),
                    categoryMap,
                    hourlyMap
                ));
            }

            finalMap.put(subId, new UsageData(subId, dailyRecords));
        }
        return finalMap;
    }

    /**
     * Redis의 byte[] 응답을 String/Long으로 안전하게 변환하여 맵에 담음.
     */
    private Map<String, Long> parseAllFields(Map<byte[], byte[]> raw) {
        Map<String, Long> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;

        raw.forEach((key, value) -> {
            try {
                map.put(new String(key), Long.parseLong(new String(value)));
            } catch (NumberFormatException e) {
                // 숫자 변환 불가 시 해당 필드 무시 (데이터 정합성 방어 로직)
            }
        });
        return map;
    }

    /**
     * 3시간 단위(0~21시)로 정의된 필드를 찾아 시간대별 사용량 맵을 생성함.
     */
    private Map<Integer, Long> parseThreeHourly(Map<byte[], byte[]> raw) {
        Map<Integer, Long> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;

        for (int h = 0; h < 24; h += 3) {
            String field = String.format("%02d_used", h);
            byte[] value = raw.get(field.getBytes());
            map.put(h, value == null ? 0L : Long.parseLong(new String(value)));
        }
        return map;
    }
}
