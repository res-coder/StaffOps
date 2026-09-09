package com.staffops.activity;

import com.staffops.model.ActivityRecord;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class ActivityService {
    private final Map<UUID, Deque<ActivityRecord>> activity = new ConcurrentHashMap<>();
    private final int maxEntries;
    private final Set<String> redactedCommands;

    public ActivityService(FileConfiguration config) {
        maxEntries = Math.max(20, config.getInt("settings.activity.max-entries-per-player", 100));
        redactedCommands = new HashSet<>();
        for (String cmd : config.getStringList("settings.activity.redacted-commands")) {
            redactedCommands.add(cmd.toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
        }
    }

    public void record(Player player, String type, String details) {
        record(player.getUniqueId(), player.getName(), type, details);
    }

    public void record(UUID id, String name, String type, String details) {
        Deque<ActivityRecord> deque = activity.computeIfAbsent(id, ignored -> new ConcurrentLinkedDeque<>());
        deque.addFirst(new ActivityRecord(id, name, type, details, System.currentTimeMillis()));
        while (deque.size() > maxEntries) deque.pollLast();
    }

    public void recordCommand(Player player, String raw) {
        record(player, "COMMAND", commandForStorage(raw));
    }

    public String commandForStorage(String raw) {
        String stripped = raw.startsWith("/") ? raw.substring(1) : raw;
        String root = stripped.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return redactedCommands.contains(root) ? "/" + root + " <redacted>" : raw;
    }

    public List<ActivityRecord> recent(UUID id, int limit) {
        Deque<ActivityRecord> deque = activity.get(id);
        if (deque == null) return List.of();
        return deque.stream().limit(Math.max(1, limit)).toList();
    }

    public List<ActivityRecord> recentOfType(UUID id, String type, int limit) {
        return recent(id, maxEntries).stream().filter(a -> a.type().equalsIgnoreCase(type)).limit(limit).toList();
    }

    public long countTypeSince(String type, long sinceMillis) {
        return activity.values().stream().flatMap(Collection::stream)
                .filter(a -> a.createdAt() >= sinceMillis && a.type().equalsIgnoreCase(type)).count();
    }

    public void clear(UUID id) { activity.remove(id); }
}
