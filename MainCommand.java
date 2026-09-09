package com.staffops.command;

import com.staffops.StaffOpsPlugin;
import com.staffops.model.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MainCommand implements CommandExecutor, TabCompleter {
    private final StaffOpsPlugin plugin;

    public MainCommand(StaffOpsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.messages().staff("player-only"));
                return true;
            }
            plugin.gui().dashboard(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("open") || sub.equals("player")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.messages().staff("player-only"));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(plugin.messages().staff("usage.main-player"));
                return true;
            }
            resolve(args[1], profile -> plugin.gui().profile(player, profile.uuid(), profile.name()), player);
            return true;
        }

        if (sub.equals("note")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.messages().staff("player-only"));
                return true;
            }
            if (!player.hasPermission("staffops.notes")) {
                player.sendMessage(plugin.messages().staff("no-permission"));
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(plugin.messages().staff("usage.note"));
                return true;
            }
            int limit = plugin.getConfig().getInt("settings.notes.max-length", 300);
            String note = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            if (note.length() > limit) {
                player.sendMessage(plugin.messages().staff("notes.too-long", java.util.Map.of("limit", String.valueOf(limit))));
                return true;
            }
            resolve(args[1], profile -> {
                plugin.notes().add(player, profile.uuid(), profile.name(), note);
                plugin.cases().recordActive(player, "STAFF_NOTE", profile.name() + ": " + note);
                player.sendMessage(plugin.messages().staff("notes.added", java.util.Map.of("player", profile.name())));
            }, player);
            return true;
        }

        if (sub.equals("history")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.messages().staff("player-only"));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(plugin.messages().staff("usage.history"));
                return true;
            }
            resolve(args[1], profile -> plugin.gui().profile(player, profile.uuid(), profile.name()), player);
            return true;
        }


        if (sub.equals("announce")) {
            if (!sender.hasPermission("staffops.operations.announce")) {
                sender.sendMessage(plugin.messages().staff("no-permission"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(plugin.messages().staff("usage.announce"));
                return true;
            }
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            Bukkit.broadcast(plugin.messages().player("operations.announcement", java.util.Map.of("message", message)));
            plugin.audit().log(sender, "SERVER_ANNOUNCEMENT", null, null, message);
            return true;
        }

        if (sub.equals("reload")) {
            if (!sender.hasPermission("staffops.admin")) {
                sender.sendMessage(plugin.messages().staff("no-permission"));
                return true;
            }
            plugin.reloadStaffOpsConfig();
            sender.sendMessage(plugin.messages().staff("config-reloaded"));
            return true;
        }

        sender.sendMessage(plugin.messages().staff("usage.main-help"));
        return true;
    }

    private void resolve(String name, java.util.function.Consumer<PlayerProfile> success, Player viewer) {
        Player live = Bukkit.getPlayerExact(name);
        if (live != null) {
            success.accept(new PlayerProfile(live.getUniqueId(), live.getName(), 0, 0, System.currentTimeMillis(), 0, null));
            return;
        }
        plugin.playerData().byName(name).thenAccept(optional -> Bukkit.getScheduler().runTask(plugin,
                () -> optional.ifPresentOrElse(success, () -> viewer.sendMessage(plugin.messages().staff("player-not-found")))));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return prefix(args[0], List.of("player", "note", "history", "announce", "reload"));
        if (args.length == 2 && (args[0].equalsIgnoreCase("player") || args[0].equalsIgnoreCase("note") || args[0].equalsIgnoreCase("history"))) {
            return prefix(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return List.of();
    }

    private List<String> prefix(String value, List<String> input) {
        String query = value.toLowerCase(Locale.ROOT);
        return input.stream().filter(entry -> entry.toLowerCase(Locale.ROOT).startsWith(query)).toList();
    }
}
