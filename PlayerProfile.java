package com.staffops.model;
import java.util.UUID;
public record PlayerProfile(UUID uuid, String name, long firstSeen, long lastSeen, long lastJoin, long playtimeSeconds, String ipHash) {}
