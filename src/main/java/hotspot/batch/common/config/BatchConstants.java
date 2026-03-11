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
     * DB 부하와 메모리 효율을 고려한 권장값 (1,000건 단위)
     */
    public static final int CHUNK_SIZE = 1000;
}
