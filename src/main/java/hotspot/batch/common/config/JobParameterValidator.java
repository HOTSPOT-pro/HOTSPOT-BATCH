package hotspot.batch.common.config;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;
import org.springframework.stereotype.Component;

@Component
public class JobParameterValidator implements JobParametersValidator {

    private static final DateTimeFormatter TARGET_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {
        validateTargetDate(parameters.getString("targetDate"));
        validateYearMonth(parameters.getString("yearMonth"));
    }

    private void validateTargetDate(String targetDate) throws InvalidJobParametersException {
        if (targetDate == null || targetDate.isBlank()) {
            return;
        }

        try {
            LocalDate.parse(targetDate, TARGET_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new InvalidJobParametersException("Invalid targetDate format. Required: yyyy-MM-dd");
        }
    }

    private void validateYearMonth(String yearMonth) throws InvalidJobParametersException {
        if (yearMonth == null || yearMonth.isBlank()) {
            return;
        }

        try {
            YearMonth ym = YearMonth.parse(yearMonth, YEAR_MONTH_FORMAT);
            if (ym.isAfter(YearMonth.now().plusMonths(1))) {
                throw new InvalidJobParametersException("yearMonth cannot be future month.");
            }
        } catch (DateTimeParseException e) {
            throw new InvalidJobParametersException("Invalid yearMonth format. Required: yyyyMM");
        }
    }
}
