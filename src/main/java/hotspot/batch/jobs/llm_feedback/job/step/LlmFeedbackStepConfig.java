package hotspot.batch.jobs.llm_feedback.job.step;

import hotspot.batch.jobs.llm_feedback.dto.LlmFeedbackWeeklyReport;
import hotspot.batch.jobs.llm_feedback.listener.LlmFeedbackSkipListener;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * WeeklyReport LLM 피드백 생성을 위한 Step 설정
 * - AsyncItemProcessor/Writer를 통한 병렬 처리
 * - SkipPolicy를 통한 예외 허용 및 복구력(Resilience) 확보
 */
@Configuration
@RequiredArgsConstructor
public class LlmFeedbackStepConfig {

    private final LlmFeedbackSkipListener llmFeedbackSkipListener;

    @Value("${llm.job.chunk-size}")
    private int chunkSize;

    @Value("${llm.job.skip-limit}")
    private int skipLimit;

    @Bean
    public Step llmFeedbackStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("llmFeedbackReader") ItemReader<LlmFeedbackWeeklyReport> reader,
            @Qualifier("asyncLlmFeedbackProcessor") ItemProcessor<LlmFeedbackWeeklyReport, Future<LlmFeedbackWeeklyReport>> processor,
            @Qualifier("asyncLlmFeedbackWriter") ItemWriter<Future<LlmFeedbackWeeklyReport>> writer) {
        
        return new StepBuilder("llmFeedbackStep", jobRepository)
                .<LlmFeedbackWeeklyReport, Future<LlmFeedbackWeeklyReport>>chunk(chunkSize)
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant() // 결함 허용(Fault Tolerance) 설정 활성화
                .skip(Exception.class) // 처리 중 예외 발생 시 Skip 정책 적용
                .skipLimit(skipLimit)  // 지정된 횟수만큼 Skip 허용 (YAML에서 관리)
                .listener(llmFeedbackSkipListener) // Skip 발생 시 로그를 남길 리스너 등록
                .build();
    }
}
