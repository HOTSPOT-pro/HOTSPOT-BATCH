package hotspot.batch.jobs.usage_aggregation.job;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 사용량 집계 배치에서 공통으로 사용하는 날짜 계산 컴포넌트
 */
@Component
public class UsageAggregationDateCalculator {

    private final Clock kstClock;

    public UsageAggregationDateCalculator(Clock kstClock) {
        this.kstClock = kstClock;
    }

    public LocalDate today() {
        return LocalDate.now(kstClock);
    }

    public LocalDateTime createdDateTime() {
        return LocalDateTime.now(kstClock);
    }

    public String receiveDay() {
        return today().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    /**
     * 오늘 실행 기준 receive_day와 동일한 요일을 시작일로 하는 집계 시작일을 반환
     */
    public LocalDate weekStartDate() {
        return weekStartDate(receiveDay());
    }

    /**
     * 오늘 실행 기준 receive_day 바로 전날을 집계 종료일로 반환
     */
    public LocalDate weekEndDate() {
        return weekEndDate(receiveDay());
    }

    /**
     * 특정 receive_day 기준 직전 7일 집계 시작일 계산
     */
    public LocalDate weekStartDate(String receiveDay) {
        DayOfWeek targetDay = dayOfWeek(receiveDay);
        LocalDate yesterday = today().minusDays(1);

        return yesterday.with(java.time.temporal.TemporalAdjusters.previousOrSame(targetDay));
    }

    /**
     * 특정 receive_day 기준 직전 7일 집계 종료일 계산
     */
    public LocalDate weekEndDate(String receiveDay) {
        return weekStartDate(receiveDay).plusDays(6);
    }

    /**
     * 요일 -> DayOfWeek로 반환
     */
    private DayOfWeek dayOfWeek(String receiveDay) {
        return switch (receiveDay.toLowerCase(Locale.ENGLISH)) {
            case "monday" -> DayOfWeek.MONDAY;
            case "tuesday" -> DayOfWeek.TUESDAY;
            case "wednesday" -> DayOfWeek.WEDNESDAY;
            case "thursday" -> DayOfWeek.THURSDAY;
            case "friday" -> DayOfWeek.FRIDAY;
            case "saturday" -> DayOfWeek.SATURDAY;
            case "sunday" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Invalid receiveDay: " + receiveDay);
        };
    }
}
