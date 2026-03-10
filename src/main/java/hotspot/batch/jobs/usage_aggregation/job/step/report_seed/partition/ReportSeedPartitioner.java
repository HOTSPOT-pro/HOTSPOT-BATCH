package hotspot.batch.jobs.usage_aggregation.job.step.report_seed.partition;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * Step1 대상 처리 범위를 worker step에 분배하는 partitioner
 * 현재는 skeleton 형태이며 추후 실제 대상 건수와 범위 계산 로직으로 교체 예정
 */
public class ReportSeedPartitioner implements Partitioner {

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();

        for (int partitionNumber = 0; partitionNumber < gridSize; partitionNumber++) {
            ExecutionContext executionContext = new ExecutionContext();
            executionContext.putInt("partitionNumber", partitionNumber);
            executionContext.putInt("gridSize", gridSize);
            partitions.put("reportSeedPartition" + partitionNumber, executionContext);
        }

        return partitions;
    }
}
