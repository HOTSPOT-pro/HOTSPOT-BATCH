package hotspot.batch.common.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 배치 전역에서 사용하는 공통 상수 관리
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BatchConstants {

    /**
     * 기본 Chunk 처리 크기
     */
    public static final int CHUNK_SIZE = 1000;

    /**
     * 기본 병렬 처리 스레드 수 (Grid Size)
     */
    public static final int GRID_SIZE = 8;
}
