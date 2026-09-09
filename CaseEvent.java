package com.staffops.model;

import java.util.UUID;

public record CaseEvent(long id, long caseId, UUID actor, String actorName, String type, String details, long createdAt) {}
