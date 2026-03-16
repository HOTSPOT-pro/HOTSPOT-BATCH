package hotspot.batch.jobs.usage_aggregation.job.step.usage_metrics.dto;

import java.util.List;
import lombok.Builder;

/**
 * 리포트 최종 점수 및 등급 정보
 */
@Builder
public record ScoreData(
    int totalScore,
    String scoreLevel,
    int scoreDiff,
    List<ScoreReason> reasons
) {}
