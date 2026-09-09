package com.staffops.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Messages {
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yml;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        ensureFile();
        reload();
    }

    public void reload() {
        yml = YamlConfiguration.loadConfiguration(file);
        migrateAndMergeDefaults();
    }

    public Component staff(String key) {
        return staff(key, Map.of());
    }

    public Component staff(String key, Map<String, String> vars) {
        return render(prefix("staff-prefix") + value(key), vars);
    }

    public Component player(String key) {
        return player(key, Map.of());
    }

    public Component player(String key, Map<String, String> vars) {
        return render(prefix("player-prefix") + value(key), vars);
    }

    public Component forAudience(CommandSender audience, String key) {
        return forAudience(audience, key, Map.of());
    }

    public Component forAudience(CommandSender audience, String key, Map<String, String> vars) {
        boolean staffAudience = audience.hasPermission("staffops.use") || audience.hasPermission("staffops.admin");
        return staffAudience ? staff(key, vars) : player(key, vars);
    }

    public Component raw(String text) {
        return render(text, Map.of());
    }

    public String plainPlayer(String key) {
        return PlainTextComponentSerializer.plainText().serialize(player(key));
    }

    public String text(String key) {
        return yml.getString(key, "<red>Missing message: " + key);
    }

    private String prefix(String key) {
        return yml.getString(key, "");
    }

    private String value(String key) {
        return yml.getString(key, "<red>Missing message: " + key);
    }

    private Component render(String template, Map<String, String> vars) {
        String output = template;
        for (var entry : vars.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", escape(entry.getValue()));
        }
        return mm.deserialize(output).decoration(TextDecoration.ITALIC, false);
    }

    private void ensureFile() {
        if (!file.exists()) plugin.saveResource("messages.yml", false);
    }

    private void migrateAndMergeDefaults() {
        boolean changed = false;

        if (!yml.contains("staff-prefix") && yml.contains("prefix")) {
            yml.set("staff-prefix", yml.getString("prefix"));
            changed = true;
        }

        Map<String, String> legacyKeys = Map.ofEntries(
                Map.entry("staffmode-enabled", "staffmode.enabled"),
                Map.entry("staffmode-disabled", "staffmode.disabled"),
                Map.entry("vanish-enabled", "vanish.enabled"),
                Map.entry("vanish-disabled", "vanish.disabled"),
                Map.entry("frozen-target", "freeze.target-frozen"),
                Map.entry("unfrozen-target", "freeze.target-unfrozen"),
                Map.entry("freeze-staff", "freeze.staff-state"),
                Map.entry("report-created", "reports.created"),
                Map.entry("report-cooldown", "reports.cooldown"),
                Map.entry("report-staff-alert", "reports.staff-alert"),
                Map.entry("muted", "punishments.muted-chat"),
                Map.entry("banned", "punishments.ban-screen")
        );
        for (var entry : legacyKeys.entrySet()) {
            if (!yml.contains(entry.getValue()) && yml.contains(entry.getKey())) {
                yml.set(entry.getValue(), yml.get(entry.getKey()));
                changed = true;
            }
        }

        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                for (String key : defaults.getKeys(true)) {
                    if (!defaults.isConfigurationSection(key) && !yml.contains(key)) {
                        yml.set(key, defaults.get(key));
                        changed = true;
                    }
                }
            }
        } catch (IOException ignored) {
            // InputStream close failure is non-fatal; existing configuration remains usable.
        }

        if (changed) {
            try {
                yml.save(file);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not update messages.yml with new StaffOps defaults: " + exception.getMessage());
            }
        }
    }

    private String escape(String input) {
        return input == null ? "" : input.replace("<", "&lt;").replace(">", "&gt;");
    }
}
