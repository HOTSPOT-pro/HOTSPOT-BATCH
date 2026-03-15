package hotspot.batch.jobs.llm_feedback.job;

import hotspot.batch.common.config.JobParameterValidator;
import hotspot.batch.common.listener.JobResultListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Job2: WeeklyReport AI 피드백 생성 Job
 */
@Configuration
@RequiredArgsConstructor
public class LlmFeedbackJobConfig {

    private final JobParameterValidator jobParameterValidator;
    private final JobResultListener jobResultListener;

    @Bean
    public Job llmFeedbackJob(
            JobRepository jobRepository,
            @Qualifier("llmFeedbackStep") Step llmFeedbackStep) {
        return new JobBuilder("llmFeedbackJob", jobRepository)
                .validator(jobParameterValidator)
                .start(llmFeedbackStep)
                .listener(jobResultListener)
                .build();
    }
}
