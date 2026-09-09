package com.staffops.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ServerIntegrationService {
    public String balance(Player player) {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) return "Unavailable";
            Object economy = registration.getClass().getMethod("getProvider").invoke(registration);
            Method getBalance = economyClass.getMethod("getBalance", org.bukkit.OfflinePlayer.class);
            double value = ((Number) getBalance.invoke(economy, player)).doubleValue();
            Method format = economyClass.getMethod("format", double.class);
            return String.valueOf(format.invoke(economy, value));
        } catch (Throwable ignored) {
            return "Unavailable";
        }
    }

    public String primaryGroup(Player player) {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, player.getUniqueId());
            if (user == null) return "Unknown";
            return String.valueOf(user.getClass().getMethod("getPrimaryGroup").invoke(user));
        } catch (Throwable ignored) {
            return player.isOp() ? "op" : "Unknown";
        }
    }

    public List<String> staffOpsPermissions(Player player) {
        List<String> nodes = new ArrayList<>();
        for (var info : player.getEffectivePermissions()) {
            if (info.getValue() && info.getPermission().startsWith("staffops.")) nodes.add(info.getPermission());
        }
        nodes.sort(String.CASE_INSENSITIVE_ORDER);
        return nodes;
    }
}
