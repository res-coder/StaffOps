package com.staffops.model;
import java.util.UUID;
public record Punishment(long id, UUID target, String targetName, UUID staff, String staffName, PunishmentType type, String reason, long createdAt, Long expiresAt, boolean active, String ladder) {
    public boolean expired(long now) { return expiresAt != null && expiresAt <= now; }
}
