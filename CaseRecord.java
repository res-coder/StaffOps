package com.staffops.model;

import java.util.UUID;

public record CaseRecord(long id, UUID target, String targetName, UUID openedBy, String openedByName,
                         String reason, String status, String priority, long createdAt, Long closedAt) {}
