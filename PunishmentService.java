package com.staffops.moderation;

import com.staffops.audit.AuditService;
import com.staffops.config.Messages;
import com.staffops.data.Database;
import com.staffops.model.Punishment;
import com.staffops.model.PunishmentType;
import com.staffops.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PunishmentService {
    private final JavaPlugin plugin;
    private final Database database;
    private final Messages messages;
    private final AuditService audit;
    private final Map<UUID, Punishment> bans = new ConcurrentHashMap<>();
    private final Map<UUID, Punishment> mutes = new ConcurrentHashMap<>();

    public PunishmentService(JavaPlugin plugin, Database database, Messages messages, AuditService audit) {
        this.plugin = plugin;
        this.database = database;
        this.messages = messages;
        this.audit = audit;
        loadActive();
    }

    private void loadActive() {
        long now = System.currentTimeMillis();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM punishments WHERE active=1 AND type IN ('BAN','MUTE')")) {
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    Punishment punishment = read(results);
                    if (punishment.expired(now)) {
                        expire(punishment.id());
                        continue;
                    }
                    mapFor(punishment.type()).put(punishment.target(), punishment);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load active punishments", exception);
        }
    }

    public Optional<Punishment> activeBan(UUID id) { return current(bans, id); }
    public Optional<Punishment> activeMute(UUID id) { return current(mutes, id); }

    private Optional<Punishment> current(Map<UUID, Punishment> map, UUID id) {
        Punishment punishment = map.get(id);
        if (punishment == null) return Optional.empty();
        if (punishment.expired(System.currentTimeMillis())) {
            map.remove(id, punishment);
            expire(punishment.id());
            return Optional.empty();
        }
        return Optional.of(punishment);
    }

    public CompletableFuture<List<Punishment>> history(UUID target, int limit) {
        return database.supplyAsync(connection -> {
            List<Punishment> output = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM punishments WHERE target_uuid=? ORDER BY created_at DESC LIMIT ?")) {
                statement.setString(1, target.toString());
                statement.setInt(2, limit);
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) output.add(read(results));
                }
            }
            return output;
        });
    }

    public CompletableFuture<Integer> ladderCount(UUID target, String ladder) {
        return database.supplyAsync(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM punishments WHERE target_uuid=? AND ladder=?")) {
                statement.setString(1, target.toString());
                statement.setString(2, ladder);
                try (ResultSet results = statement.executeQuery()) {
                    return results.next() ? results.getInt(1) : 0;
                }
            }
        });
    }

    public CompletableFuture<Punishment> apply(CommandSender staff, UUID target, String targetName, PunishmentType type,
                                                String reason, long durationMillis, String ladder) {
        if (!staff.hasPermission("staffops.punish." + type.name().toLowerCase(Locale.ROOT))) {
            return CompletableFuture.failedFuture(new SecurityException("No permission for " + type));
        }

        Player online = Bukkit.getPlayer(target);
        if (plugin.getConfig().getBoolean("settings.punishments.protect-staff", true)
                && online != null
                && online.hasPermission("staffops.protected")
                && !staff.hasPermission("staffops.override.protected")) {
            return CompletableFuture.failedFuture(new SecurityException("Target is protected"));
        }

        UUID staffId = staff instanceof Player player ? player.getUniqueId() : null;
        long now = System.currentTimeMillis();
        Long expires = (type == PunishmentType.BAN || type == PunishmentType.MUTE) && durationMillis > 0
                ? now + durationMillis : null;

        return database.supplyAsync(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO punishments(target_uuid,target_name,staff_uuid,staff_name,type,reason,created_at,expires_at,active,ladder) VALUES(?,?,?,?,?,?,?,?,1,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, target.toString());
                statement.setString(2, targetName);
                statement.setString(3, staffId == null ? null : staffId.toString());
                statement.setString(4, staff.getName());
                statement.setString(5, type.name());
                statement.setString(6, reason);
                statement.setLong(7, now);
                if (expires == null) statement.setNull(8, Types.BIGINT); else statement.setLong(8, expires);
                statement.setString(9, ladder);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    return new Punishment(keys.getLong(1), target, targetName, staffId, staff.getName(), type, reason, now, expires, true, ladder);
                }
            }
        }).thenApply(punishment -> {
            if (type == PunishmentType.BAN) bans.put(target, punishment);
            if (type == PunishmentType.MUTE) mutes.put(target, punishment);
            Bukkit.getScheduler().runTask(plugin, () -> executeLive(punishment));
            audit.log(staff, "PUNISH_" + type, target, targetName,
                    "reason=" + reason + ", duration=" + (durationMillis <= 0 ? "permanent" : TimeUtil.compactDuration(durationMillis)) + ", ladder=" + ladder);
            return punishment;
        });
    }

    public void revoke(CommandSender staff, long id) {
        database.supplyAsync(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM punishments WHERE id=?")) {
                statement.setLong(1, id);
                try (ResultSet results = statement.executeQuery()) {
                    return results.next() ? read(results) : null;
                }
            }
        }).thenAccept(punishment -> {
            if (punishment == null) return;
            database.runAsync(connection -> {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE punishments SET active=0 WHERE id=?")) {
                    statement.setLong(1, id);
                    statement.executeUpdate();
                }
            });
            mapFor(punishment.type()).remove(punishment.target(), punishment);
            audit.log(staff, "PUNISHMENT_REVOKE", punishment.target(), punishment.targetName(), "id=" + id + ", type=" + punishment.type());
        });
    }

    private void executeLive(Punishment punishment) {
        Player target = Bukkit.getPlayer(punishment.target());
        switch (punishment.type()) {
            case WARN -> {
                if (target != null) target.sendMessage(messages.forAudience(target, "punishments.warning", Map.of("reason", punishment.reason())));
            }
            case KICK -> {
                if (target != null) target.kick(messages.player("punishments.kick-screen", Map.of("reason", punishment.reason())));
                expire(punishment.id());
            }
            case BAN -> {
                if (target != null) target.kick(banMessage(punishment));
            }
            case MUTE -> {
                if (target != null) target.sendMessage(messages.forAudience(target, "punishments.mute-applied", Map.of("reason", punishment.reason())));
            }
        }
    }

    public Component banMessage(Punishment punishment) {
        String expires = punishment.expiresAt() == null ? "Permanent" : TimeUtil.formatDate(punishment.expiresAt());
        return messages.player("punishments.ban-screen", Map.of("reason", punishment.reason(), "expires", expires));
    }

    private Map<UUID, Punishment> mapFor(PunishmentType type) {
        return type == PunishmentType.BAN ? bans : type == PunishmentType.MUTE ? mutes : new ConcurrentHashMap<>();
    }

    private void expire(long id) {
        database.runAsync(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE punishments SET active=0 WHERE id=?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
        });
    }

    private Punishment read(ResultSet results) throws SQLException {
        String staffUuid = results.getString("staff_uuid");
        long expiresRaw = results.getLong("expires_at");
        Long expires = results.wasNull() ? null : expiresRaw;
        return new Punishment(
                results.getLong("id"),
                UUID.fromString(results.getString("target_uuid")),
                results.getString("target_name"),
                staffUuid == null ? null : UUID.fromString(staffUuid),
                results.getString("staff_name"),
                PunishmentType.valueOf(results.getString("type")),
                results.getString("reason"),
                results.getLong("created_at"),
                expires,
                results.getInt("active") == 1,
                results.getString("ladder")
        );
    }
}
