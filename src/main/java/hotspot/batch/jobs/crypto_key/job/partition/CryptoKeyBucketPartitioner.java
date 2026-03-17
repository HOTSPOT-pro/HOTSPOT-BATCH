package hotspot.batch.jobs.crypto_key.job.partition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.stereotype.Component;

import hotspot.batch.jobs.crypto_key.repository.CryptoKeyRotationRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CryptoKeyBucketPartitioner implements Partitioner {

    private final CryptoKeyRotationRepository repository;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        List<Integer> bucketIds = repository.findTargetBucketIds();
        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();

        for (int i = 0; i < bucketIds.size(); i++) {
            ExecutionContext context = new ExecutionContext();
            context.putInt("targetBucketId", bucketIds.get(i));
            partitions.put("partition" + i, context);
        }

        return partitions;
    }
}
