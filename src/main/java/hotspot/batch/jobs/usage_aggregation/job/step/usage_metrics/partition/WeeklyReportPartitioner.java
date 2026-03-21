package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.partition;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Step2 대상 처리 범위를 ID의 나머지(MOD) 기준으로 균등하게 분배하는 Partitioner
 * [최종 개선] Modular Partitioning + DB 함수 기반 인덱스 조합으로 부하 분산 및 속도 동시 해결
 */
@Component
public class WeeklyReportPartitioner implements Partitioner {

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();

        for (int i = 0; i < gridSize; i++) {
            ExecutionContext context = new ExecutionContext();
            context.putInt("gridSize", gridSize);   // 나눌 수 (8)
            context.putInt("remainder", i);         // 나머지 값 (0~7)
            partitions.put("partition" + i, context);
        }

        return partitions;
    }
}
