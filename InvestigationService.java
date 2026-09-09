package com.staffops.investigation;

import com.staffops.StaffOpsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InvestigationService {
    private final StaffOpsPlugin plugin;
    private final Map<UUID, UUID> following=new ConcurrentHashMap<>();
    private final BukkitTask task;
    public InvestigationService(StaffOpsPlugin plugin){this.plugin=plugin;task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,10L,10L);}

    public void teleport(Player staff,Player target){staff.teleportAsync(target.getLocation());plugin.cases().recordActive(staff,"TELEPORT","Teleported to "+target.getName());}
    public void spectate(Player staff,Player target){if(!plugin.staffMode().isActive(staff))plugin.staffMode().enable(staff);following.remove(staff.getUniqueId());staff.setGameMode(GameMode.SPECTATOR);staff.setSpectatorTarget(target);plugin.cases().recordActive(staff,"POV_MODE","Spectating "+target.getName());}
    public boolean toggleFollow(Player staff,Player target){if(!plugin.staffMode().isActive(staff))plugin.staffMode().enable(staff);if(target.getUniqueId().equals(following.get(staff.getUniqueId()))){following.remove(staff.getUniqueId());plugin.cases().recordActive(staff,"FOLLOW_STOP","Stopped following "+target.getName());return false;}following.put(staff.getUniqueId(),target.getUniqueId());plugin.cases().recordActive(staff,"FOLLOW_START","Following "+target.getName());return true;}
    public void stop(Player staff){following.remove(staff.getUniqueId());if(staff.getGameMode()==GameMode.SPECTATOR)staff.setSpectatorTarget(null);}
    public boolean isFollowing(Player staff,UUID target){return target.equals(following.get(staff.getUniqueId()));}
    private void tick(){for(var e:Map.copyOf(following).entrySet()){Player staff=Bukkit.getPlayer(e.getKey()),target=Bukkit.getPlayer(e.getValue());if(staff==null||target==null||!staff.isOnline()||!target.isOnline()||!plugin.staffMode().isActive(staff)){following.remove(e.getKey());continue;}Location t=target.getLocation().clone();t.add(t.getDirection().normalize().multiply(-3));t.add(0,1.5,0);staff.teleportAsync(t);}}
    public void shutdown(){task.cancel();following.clear();}
}
