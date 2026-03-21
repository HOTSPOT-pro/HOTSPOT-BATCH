package hotspot.batch.jobs.crypto_key.dto;

public record PhoneRotationUpdate(
        Long subId,
        String phoneEnc,
        Integer sourceKeyVersion,
        Integer targetKeyVersion
) {
}
