package com.staffops.model;
import java.util.UUID;
public record ReportRecord(long id, UUID reporter, String reporterName, UUID target, String targetName,
                           String category, String priority, String reason, String status,
                           UUID claimedBy, String claimedName, long createdAt, int duplicateCount) {}
