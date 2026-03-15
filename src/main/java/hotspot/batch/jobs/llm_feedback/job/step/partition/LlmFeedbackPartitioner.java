package hotspot.batch.jobs.llm_feedback.job.step.partition;

import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;
import hotspot.batch.jobs.usage_aggregation.repository.WeeklyReportRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * WeeklyReport ID 범위를 기반으로 파티션을 분할하는 Partitioner
 */
@Slf4j
@RequiredArgsConstructor
public class LlmFeedbackPartitioner implements Partitioner {

    private final WeeklyReportRepository weeklyReportRepository;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        // AGGREGATED 상태인 데이터의 ID 최소/최대값 조회
        long min = weeklyReportRepository.findMinIdByStatus(ReportStatus.AGGREGATED);
        long max = weeklyReportRepository.findMaxIdByStatus(ReportStatus.AGGREGATED);

        if (min == 0 || max == 0 || min > max) {
            log.info("[Partition] No data found for processing. min={}, max={}", min, max);
            return Map.of();
        }

        long targetSize = (max - min) / gridSize + 1;
        Map<String, ExecutionContext> result = new HashMap<>();

        long start = min;
        long end = start + targetSize - 1;
        int number = 0;

        while (start <= max) {
            ExecutionContext context = new ExecutionContext();
            result.put("partition" + number, context);

            if (end >= max) {
                end = max;
            }

            context.putLong("startId", start);
            context.putLong("endId", end);

            log.debug("[Partition] partition{} range: {} ~ {}", number, start, end);

            start += targetSize;
            end += targetSize;
            number++;
        }

        log.info("[Partition] Created {} partitions for range {} ~ {}", result.size(), min, max);
        return result;
    }
}
