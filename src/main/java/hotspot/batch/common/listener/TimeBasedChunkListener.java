package hotspot.batch.common.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.stereotype.Component;

@Component
public class TimeBasedChunkListener implements ChunkListener<Long, Long> {

    private static final Logger log = LoggerFactory.getLogger(TimeBasedChunkListener.class);
    private static final long LOG_INTERVAL_MS = 10_000L;
    private volatile long lastLogTime = 0L;

    @Override
    public void afterChunk(Chunk<Long> chunk) {
        long now = System.currentTimeMillis();

        if (now - lastLogTime < LOG_INTERVAL_MS) {
            return;
        }

        log.info("CHUNK PROGRESS chunkSize={}", chunk.size());
        lastLogTime = now;
    }
}
