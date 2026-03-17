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
        validatePositiveInteger(parameters.getString("targetBucketId"), "targetBucketId");
        validatePositiveInteger(parameters.getString("sourceKeyVersion"), "sourceKeyVersion");
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

    private void validatePositiveInteger(String value, String parameterName) throws InvalidJobParametersException {
        if (value == null || value.isBlank()) {
            return;
        }

        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new InvalidJobParametersException(parameterName + " must be a positive integer.");
            }
        } catch (NumberFormatException e) {
            throw new InvalidJobParametersException(parameterName + " must be a positive integer.");
        }
    }
}
