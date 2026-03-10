package hotspot.batch.common.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.stereotype.Component;

/**
 * Chunk 처리 진행 상황을 일정 시간 간격으로 로깅하는 공통 listener
 */
@Component
public class TimeBasedChunkListener implements ChunkListener<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(TimeBasedChunkListener.class);
    private static final long LOG_INTERVAL_MS = 10_000L;
    private volatile long lastLogTime = 0L;

    @Override
    public void afterChunk(Chunk<Object> chunk) {
        long now = System.currentTimeMillis();

        if (now - lastLogTime < LOG_INTERVAL_MS) {
            return;
        }

        log.info("CHUNK PROGRESS chunkSize={}", chunk.size());
        lastLogTime = now;
    }
}
