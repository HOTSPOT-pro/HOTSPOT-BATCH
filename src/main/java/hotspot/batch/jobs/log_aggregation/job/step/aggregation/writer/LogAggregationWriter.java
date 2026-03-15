package hotspot.batch.jobs.log_aggregation.job.step.aggregation.writer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hotspot.batch.jobs.log_aggregation.repository.LogAggregationRepository;
import hotspot.batch.jobs.log_aggregation.repository.LogAggregationRepository.SubUsageMonthlyAggregateRow;
import hotspot.batch.jobs.log_aggregation.repository.LogAggregationRepository.SubUsageMonthlyAggregateUpsert;
import hotspot.batch.jobs.log_aggregation.repository.LogAggregationRepository.UsageAppliedEventLogRow;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

@StepScope
@Component
public class LogAggregationWriter implements ItemWriter<UsageAppliedEventLogRow> {

    private final LogAggregationRepository logAggregationRepository;
    private final ObjectMapper objectMapper;
    private final Map<SubMonthKey, SubUsageMonthlyAggregateRow> aggregateCache = new LinkedHashMap<>();

    public LogAggregationWriter(LogAggregationRepository logAggregationRepository) {
        this.logAggregationRepository = logAggregationRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void write(Chunk<? extends UsageAppliedEventLogRow> chunk) throws Exception {
        Map<SubMonthKey, DeltaAccumulator> grouped = new LinkedHashMap<>();

        for (UsageAppliedEventLogRow row : chunk.getItems()) {
            SubMonthKey key = new SubMonthKey(row.subId(), row.yyyymm());
            DeltaAccumulator delta = grouped.computeIfAbsent(key, ignored -> new DeltaAccumulator());
            delta.rows().add(row);
        }

        for (Entry<SubMonthKey, DeltaAccumulator> entry : grouped.entrySet()) {
            SubMonthKey key = entry.getKey();
            DeltaAccumulator delta = entry.getValue();
            SubUsageMonthlyAggregateRow current = getCachedAggregate(key);

            long currentLastAppliedSeq = current == null ? 0L : current.lastAppliedSeq();
            MergedDelta mergedDelta = mergeRows(delta, currentLastAppliedSeq);
            if (mergedDelta.maxAppliedSeq() <= currentLastAppliedSeq) {
                continue;
            }

            Map<String, Long> mergedDailyUsage = new LinkedHashMap<>();
            mergeMap(mergedDailyUsage, current == null ? Map.of() : parseUsageMap(current.dailyUsageJson()));
            mergeMap(mergedDailyUsage, mergedDelta.dailyUsageMap());

            Map<String, Long> mergedGiftUsage = new LinkedHashMap<>();
            mergeMap(mergedGiftUsage, current == null ? Map.of() : parseUsageMap(current.giftUsageJson()));
            mergeMap(mergedGiftUsage, mergedDelta.giftUsageMap());

            long giftUsedTotal = (current == null ? 0L : current.giftUsedTotal()) + mergedDelta.giftUsedTotal();
            long planUsedTotal = (current == null ? 0L : current.planUsedTotal()) + mergedDelta.planUsedTotal();
            long familyUsedTotal = (current == null ? 0L : current.familyUsedTotal()) + mergedDelta.familyUsedTotal();
            long totalUsed = (current == null ? 0L : current.totalUsed()) + mergedDelta.totalUsed();

            Long familyId = mergedDelta.lastFamilyId() != null
                    ? mergedDelta.lastFamilyId()
                    : (current == null ? null : current.familyId());

            SubUsageMonthlyAggregateUpsert upsert = new SubUsageMonthlyAggregateUpsert(
                    key.subId(),
                    key.yyyymm(),
                    familyId,
                    giftUsedTotal,
                    planUsedTotal,
                    familyUsedTotal,
                    totalUsed,
                    toJson(mergedDailyUsage),
                    toJson(mergedGiftUsage),
                    mergedDelta.maxAppliedSeq());

            logAggregationRepository.upsertSubUsageMonthlyAggregate(upsert);

            aggregateCache.put(key, new SubUsageMonthlyAggregateRow(
                    key.subId(),
                    key.yyyymm(),
                    familyId,
                    giftUsedTotal,
                    planUsedTotal,
                    familyUsedTotal,
                    totalUsed,
                    upsert.dailyUsageJson(),
                    upsert.giftUsageJson(),
                    mergedDelta.maxAppliedSeq()));
        }
    }

    private SubUsageMonthlyAggregateRow getCachedAggregate(SubMonthKey key) {
        if (!aggregateCache.containsKey(key)) {
            aggregateCache.put(key, logAggregationRepository.findSubUsageMonthlyAggregate(key.subId(), key.yyyymm()));
        }
        return aggregateCache.get(key);
    }

    private MergedDelta mergeRows(DeltaAccumulator delta, long currentLastAppliedSeq) {
        Map<String, Long> dailyUsage = new LinkedHashMap<>();
        Map<String, Long> giftUsage = new LinkedHashMap<>();

        long giftUsedTotal = 0L;
        long planUsedTotal = 0L;
        long familyUsedTotal = 0L;
        long totalUsed = 0L;
        long maxAppliedSeq = currentLastAppliedSeq;
        Long familyId = null;

        for (UsageAppliedEventLogRow row : delta.rows()) {
            if (row.appliedSeq() <= currentLastAppliedSeq) {
                continue;
            }

            familyId = row.familyId();
            giftUsedTotal += row.giftUsed();
            planUsedTotal += row.planUsed();
            familyUsedTotal += row.familyUsed();
            totalUsed += row.usageAmount();
            maxAppliedSeq = Math.max(maxAppliedSeq, row.appliedSeq());

            if (row.yyyymmdd() != null && !row.yyyymmdd().isBlank()) {
                dailyUsage.merge(row.yyyymmdd(), row.usageAmount(), Long::sum);
            }
            mergeMap(giftUsage, extractGiftUsage(row.giftDetailJson()));
        }

        return new MergedDelta(giftUsedTotal, planUsedTotal, familyUsedTotal, totalUsed, maxAppliedSeq, familyId, dailyUsage, giftUsage);
    }

    private Map<String, Long> parseUsageMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            Map<String, Long> parsed = new LinkedHashMap<>();
            if (root != null && root.isObject()) {
                root.fields().forEachRemaining(field -> {
                    if (field.getValue() != null && field.getValue().canConvertToLong()) {
                        parsed.put(field.getKey(), field.getValue().longValue());
                    }
                });
            }
            return parsed;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse usage map json: " + json, e);
        }
    }

    private Map<String, Long> extractGiftUsage(String giftDetailJson) {
        if (giftDetailJson == null || giftDetailJson.isBlank()) {
            return Map.of();
        }

        try {
            JsonNode root = objectMapper.readTree(giftDetailJson);
            Map<String, Long> extracted = new LinkedHashMap<>();
            extractGiftUsageNode(root, extracted);
            return extracted;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse gift_detail_json: " + giftDetailJson, e);
        }
    }

    private void extractGiftUsageNode(JsonNode node, Map<String, Long> accumulator) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                extractGiftUsageNode(child, accumulator);
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        String giftId = findText(node, "giftId", "gift_id", "id");
        Long amount = findLong(node, "usedAmount", "used_amount", "amount", "used", "value");
        if (giftId != null && amount != null) {
            accumulator.merge(giftId, amount, Long::sum);
            return;
        }

        node.fields().forEachRemaining(field -> {
            JsonNode child = field.getValue();
            if (child != null && (child.isObject() || child.isArray())) {
                extractGiftUsageNode(child, accumulator);
            }
        });
    }

    private String findText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private Long findLong(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.canConvertToLong()) {
                return value.longValue();
            }
        }
        return null;
    }

    private void mergeMap(Map<String, Long> target, Map<String, Long> source) {
        for (Entry<String, Long> entry : source.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            target.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
    }

    private String toJson(Map<String, Long> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize usage map to json", e);
        }
    }

    private record SubMonthKey(Long subId, String yyyymm) {
    }

    private record DeltaAccumulator(java.util.List<UsageAppliedEventLogRow> rows) {
        private DeltaAccumulator() {
            this(new java.util.ArrayList<>());
        }
    }

    private record MergedDelta(
            long giftUsedTotal,
            long planUsedTotal,
            long familyUsedTotal,
            long totalUsed,
            long maxAppliedSeq,
            Long lastFamilyId,
            Map<String, Long> dailyUsageMap,
            Map<String, Long> giftUsageMap) {
    }
}
