package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import hotspot.batch.common.util.UsageCalculator;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.CategoryUsageItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.DailyUsageItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.HourlyUsageItem;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageListData;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto.UsageMetricsAggregationInput;
import hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.service.AppCategoryCache;
import lombok.RequiredArgsConstructor;

/**
 * Raw 데이터를 차트 및 상세 분석용 리스트 데이터(UsageListData)로 가공하는 팩토리
 */
@Component
@RequiredArgsConstructor
public class UsageListDataFactory {

    private final AppCategoryCache appCategoryCache;

    // 시간대 정의 상수 (SummaryCalculationService와 동일하게 유지 권장)
    private static final int LATE_NIGHT_START = 0;
    private static final int LATE_NIGHT_END = 6;
    private static final int STUDY_TIME_START = 9;
    private static final int STUDY_TIME_END = 18;

    /**
     * 이번 주 집계 입력 데이터를 받아 상세 리스트 객체를 생성함
     * 
     * @param input Redis에서 조회한 이번 주 Raw 데이터
     * @param totalUsage 합산된 이번 주 총 사용량
     * @return 일별/시간대별/카테고리별 합산 리스트가 포함된 통합 객체
     */
    public UsageListData create(UsageMetricsAggregationInput input, long totalUsage) {
        return UsageListData.builder()
                .totalUsage(totalUsage)
                .dailyUsageList(createDailyList(input))
                .hourlyUsageList(createHourlyList(input))
                .categoryUsageList(createCategoryList(input))
                .build();
    }

    /**
     * 일주일간의 데이터를 날짜별로 그룹화하여 합산 사용량을 계산함
     * 날짜 오름차순으로 정렬하여 반환
     */
    private List<DailyUsageItem> createDailyList(UsageMetricsAggregationInput input) {
        return input.weeklyAppUsage().stream()
                .map(d -> DailyUsageItem.builder()
                        .date(d.date())
                        .day(LocalDate.parse(d.date()).getDayOfWeek().name())
                        .totalUsage(d.appUsageList().stream()
                                .mapToLong(app -> UsageCalculator.gbToKb(app.usedGb()))
                                .sum())
                        .build())
                .sorted(Comparator.comparing(DailyUsageItem::date))
                .toList();
    }

    /**
     * 3시간 단위의 모든 데이터를 시간대별(0~21)로 합산함
     * 심야 시간(0-6시) 및 학습 시간(9-18시) 여부를 판정하여 플래그 설정
     */
    private List<HourlyUsageItem> createHourlyList(UsageMetricsAggregationInput input) {
        Map<Integer, Long> aggregated = new HashMap<>();
        input.weeklyHourlyUsage().stream()
                .flatMap(d -> d.hourlyUsage().entrySet().stream())
                .forEach(entry -> aggregated.merge(entry.getKey(), entry.getValue(), Long::sum));

        List<HourlyUsageItem> list = new ArrayList<>();
        aggregated.forEach((hour, usage) -> {
            list.add(HourlyUsageItem.builder()
                    .startHour(hour)
                    .isLateNight(hour >= LATE_NIGHT_START && hour <= LATE_NIGHT_END)
                    .isStudyTime(hour >= STUDY_TIME_START && hour <= STUDY_TIME_END)
                    .totalUsage(usage)
                    .build());
        });
        
        return list.stream()
                .sorted(Comparator.comparingInt(HourlyUsageItem::startHour))
                .toList();
    }

    /**
     * 앱별 사용량을 카테고리(STUDY, MEDIA 등)별로 그룹화하여 합산함
     * 사용량이 높은 카테고리 순으로 정렬하여 반환
     */
    private List<CategoryUsageItem> createCategoryList(UsageMetricsAggregationInput input) {
        Map<String, Long> categoryMap = new HashMap<>();
        
        input.weeklyAppUsage().stream()
                .flatMap(d -> d.appUsageList().stream())
                .forEach(app -> {
                    String category = appCategoryCache.getCategoryName(app.appId());
                    categoryMap.merge(category, UsageCalculator.gbToKb(app.usedGb()), Long::sum);
                });

        return categoryMap.entrySet().stream()
                .map(e -> new CategoryUsageItem(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(CategoryUsageItem::usage).reversed())
                .toList();
    }
}
