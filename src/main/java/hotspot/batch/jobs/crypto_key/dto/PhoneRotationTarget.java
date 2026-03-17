package hotspot.batch.jobs.crypto_key.dto;

public record PhoneRotationTarget(
        Long subId,
        String phoneEnc,
        Integer phoneKeyVersion
) {
}
