package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.partition;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import org.springframework.stereotype.Component;

import hotspot.batch.jobs.usage_aggregation.job.ReportStatus;
import hotspot.batch.jobs.usage_aggregation.repository.WeeklyReportRepository;
import lombok.RequiredArgsConstructor;

/**
 * Step2 대상 처리 범위를 report_id 기준으로 분배하는 partitioner
 */
@Component
@RequiredArgsConstructor
public class WeeklyReportPartitioner implements Partitioner {

    private final WeeklyReportRepository weeklyReportRepository;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Long minId = weeklyReportRepository.findMinIdByStatus(ReportStatus.PENDING);
        Long maxId = weeklyReportRepository.findMaxIdByStatus(ReportStatus.PENDING);

        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();

        if (minId == null || maxId == null || minId > maxId) {
            return partitions;
        }

        long targetCount = maxId - minId + 1;
        long partitionSize = (targetCount / gridSize) + 1;

        for (int i = 0; i < gridSize; i++) {
            long startId = minId + (i * partitionSize);
            long endId = Math.min(maxId, startId + partitionSize - 1);

            if (startId > maxId) {
                break;
            }

            ExecutionContext context = new ExecutionContext();
            context.putLong("startId", startId);
            context.putLong("endId", endId);
            partitions.put("partition" + i, context);
        }

        return partitions;
    }
}
