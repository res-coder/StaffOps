package com.staffops.model;

import java.util.UUID;

public record ActivityRecord(UUID playerId, String playerName, String type, String details, long createdAt) {}
