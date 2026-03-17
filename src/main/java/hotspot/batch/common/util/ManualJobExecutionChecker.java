package hotspot.batch.common.util;

import java.util.Optional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

/**
 * CLI를 통한 수동 Job 실행 요청이 있는지 확인하는 유틸리티
 */
@Component
@RequiredArgsConstructor
public class ManualJobExecutionChecker {

    private static final String JOB_NAME_PARAM = "job.name";
    private static final String SPRING_BATCH_JOB_NAME_PARAM = "spring.batch.job.name";

    private final ApplicationArguments applicationArguments;

    /**
     * CLI 인자로 job.name 등이 넘어왔는지 확인하여 수동 실행 여부를 반환함
     */
    public boolean hasManualJobExecutionRequest() {
        return findOption(JOB_NAME_PARAM).isPresent() || findOption(SPRING_BATCH_JOB_NAME_PARAM).isPresent();
    }

    private Optional<String> findOption(String key) {
        return Optional.ofNullable(applicationArguments.getOptionValues(key))
                .flatMap(values -> values.stream().findFirst());
    }
}
