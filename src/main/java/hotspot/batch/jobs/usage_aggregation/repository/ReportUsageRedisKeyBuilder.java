package hotspot.batch.jobs.usage_aggregation.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 리포트용 Redis 키 생성을 담당하는 빌더
 */
public final class ReportUsageRedisKeyBuilder {

    private static final String APP_USAGE_PREFIX = "usage:sub:";
    private static final String HOURLY_USAGE_PREFIX = "usage:3hourly:";

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    private ReportUsageRedisKeyBuilder() {}

    /**
     * 일별 앱 사용량 ZSet 키 생성: usage:sub:{subId}:{YYYYMMDD}
     */
    public static String dailyAppUsage(Long subId, LocalDate date) {
        return APP_USAGE_PREFIX + subId + ":" + date.format(YYYYMMDD);
    }

    /**
     * 3시간 단위 시간대별 사용량 Hash 키 생성: usage:3hourly:{subId}:{YYYYMMDD}
     */
    public static String dailyHourlyUsage(Long subId, LocalDate date) {
        return HOURLY_USAGE_PREFIX + subId + ":" + date.format(YYYYMMDD);
    }

    /**
     * 월별 앱 사용량 ZSet 키 생성: usage:sub:{subId}:{YYYYMM}
     */
    public static String monthlyAppUsage(Long subId, LocalDate date) {
        return APP_USAGE_PREFIX + subId + ":" + date.format(YYYYMM);
    }
}
