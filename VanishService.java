package com.staffops.staff;
import org.bukkit.Bukkit; import org.bukkit.entity.Player; import org.bukkit.plugin.Plugin; import java.util.Set; import java.util.UUID; import java.util.concurrent.ConcurrentHashMap;
public final class VanishService {
    private final Plugin plugin; private final Set<UUID> vanished=ConcurrentHashMap.newKeySet();
    public VanishService(Plugin plugin){this.plugin=plugin;}
    public boolean toggle(Player p){return set(p,!isVanished(p));}
    public boolean set(Player p,boolean value){if(value)vanished.add(p.getUniqueId());else vanished.remove(p.getUniqueId());refresh(p);return value;}
    public boolean isVanished(Player p){return vanished.contains(p.getUniqueId());}
    public void handleJoin(Player viewer){for(UUID id:vanished){Player hidden=Bukkit.getPlayer(id);if(hidden!=null&&!viewer.hasPermission("staffops.vanish.see"))viewer.hidePlayer(plugin,hidden);}if(isVanished(viewer))refresh(viewer);}
    private void refresh(Player target){for(Player viewer:Bukkit.getOnlinePlayers()){if(viewer.equals(target))continue;if(isVanished(target)&&!viewer.hasPermission("staffops.vanish.see"))viewer.hidePlayer(plugin,target);else viewer.showPlayer(plugin,target);}}
    public void shutdown(){for(UUID id:Set.copyOf(vanished)){Player p=Bukkit.getPlayer(id);if(p!=null)set(p,false);}vanished.clear();}
}
