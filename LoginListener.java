package com.staffops.listener;
import com.staffops.StaffOpsPlugin; import org.bukkit.event.*; import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
public final class LoginListener implements Listener {private final StaffOpsPlugin plugin;public LoginListener(StaffOpsPlugin p){plugin=p;}@EventHandler(priority=EventPriority.HIGHEST) public void login(AsyncPlayerPreLoginEvent e){plugin.punishments().activeBan(e.getUniqueId()).ifPresent(p->e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,plugin.punishments().banMessage(p)));}}
