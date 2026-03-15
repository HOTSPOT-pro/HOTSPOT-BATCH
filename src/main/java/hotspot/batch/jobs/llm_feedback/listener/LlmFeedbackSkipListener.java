package hotspot.batch.jobs.llm_feedback.listener;

import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

/**
 * LLM 피드백 생성 중 발생한 Skip 현상을 기록하는 리스너
 */
@Slf4j
@Component
public class LlmFeedbackSkipListener implements SkipListener<LlmFeedbackWeeklyReport, Object> {

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("Skip during Reader: {}", t.getMessage());
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        log.warn("Skip during Writer: {}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(LlmFeedbackWeeklyReport item, Throwable t) {
        log.warn("Skipping record due to processing error. reportId: {}, error: {}", 
                item.weeklyReportId(), t.getMessage());
    }
}
