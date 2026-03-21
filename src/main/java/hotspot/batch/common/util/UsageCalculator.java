package hotspot.batch.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 사용량 단위 변환 및 계산을 담당하는 공통 유틸리티
 */
public final class UsageCalculator {

    private static final double KB_TO_GB = 1024.0 * 1024.0;

    private UsageCalculator() {}

    /**
     * KB 단위를 GB 단위로 변환하고 소수점 첫째 자리에서 반올림함
     */
    public static double kbToGb(double kb) {
        if (kb < 0) {
            return -1;
        }

        double gb = kb / KB_TO_GB;

        return BigDecimal.valueOf(gb)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * GB 단위를 KB 단위로 변환함
     */
    public static long gbToKb(double gb) {
        if (gb < 0) {
            return -1L;
        }
        return Math.round(gb * KB_TO_GB);
    }

    /**
     * 남은 사용량을 계산함
     */
    public static double calculateRemain(double limitKb, double usedKb) {
        return Math.max(limitKb - usedKb, 0);
    }

    /**
     * 사용률을 백분율로 계산하여 정수로 반환함
     */
    public static int calculatePercent(double remainKb, double limitKb) {
        if (limitKb <= 0) {
            return 0;
        }

        double safeRemain = Math.max(remainKb, 0);
        double percent = (safeRemain / limitKb) * 100;

        int rounded = BigDecimal.valueOf(percent)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        return Math.min(Math.max(rounded, 0), 100);
    }

    /**
     * KB 단위를 GB 단위로 변환 후 올림 처리함
     */
    public static Long kbToGbCeil(long kb) {
        if (kb <= 0) {
            return 0L;
        }

        double gb = kb / (1024.0 * 1024.0);

        return (long) Math.ceil(gb);
    }
}
