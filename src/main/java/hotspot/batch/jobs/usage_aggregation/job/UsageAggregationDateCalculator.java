package hotspot.batch.jobs.usage_aggregation.job;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 사용량 집계 배치에서 사용하는 날짜 계산 컴포넌트
 */
@Component
public class UsageAggregationDateCalculator {

    private final Clock kstClock;

    public UsageAggregationDateCalculator(Clock kstClock) {
        this.kstClock = kstClock;
    }

    /**
     * 실행 기준일 (파라미터가 없으면 오늘)
     */
    public LocalDate getBaseDate(String targetDateStr) {
        if (targetDateStr == null || targetDateStr.isBlank()) {
            return LocalDate.now(kstClock);
        }
        return LocalDate.parse(targetDateStr);
    }

    /**
     * 기준일의 요일 문자열 반환 (예: "Tuesday")
     */
    public String getReceiveDay(LocalDate baseDate) {
        return baseDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase();
    }

    /**
     * 집계 시작일: 기준일로부터 7일 전
     */
    public LocalDate getWeekStartDate(LocalDate baseDate) {
        return baseDate.minusDays(7);
    }

    /**
     * 집계 종료일: 기준일로부터 1일 전 (어제까지의 데이터)
     */
    public LocalDate getWeekEndDate(LocalDate baseDate) {
        return baseDate.minusDays(1);
    }

    public LocalDateTime createdDateTime() {
        return LocalDateTime.now(kstClock);
    }
}
