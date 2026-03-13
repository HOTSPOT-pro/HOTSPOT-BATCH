package hotspot.batch.jobs.family_remove.repository;

import java.time.LocalDate;
import java.util.List;

public interface FamilyRemoveBatchRepository {

    List<FamilyRemoveScheduleRow> findDueSchedules(LocalDate baseDate);

    boolean existsFamilySub(Long familyId, Long targetSubId);

    long countFamilyMembers(Long familyId);

    void deleteFamilySub(Long familyId, Long targetSubId);

    void deletePolicySub(Long targetSubId);

    void deleteBlockedServiceSub(Long targetSubId);

    void updateFamilySummary(Long familyId, int familyNum, long familyDataAmount);

    void updateFamilySubDataLimit(Long familyId, long dataLimit);

    void markCompleted(Long scheduleId);

    void markFailed(Long scheduleId);

    record FamilyRemoveScheduleRow(Long scheduleId, Long familyId, Long targetSubId) {
    }
}
