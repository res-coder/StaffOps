package com.staffops.staff;

import com.staffops.audit.AuditService;
import com.staffops.config.Messages;
import org.bukkit.*;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffModeService {
    public static final String TOOL_NAVIGATOR="navigator",TOOL_INSPECTOR="inspector",TOOL_REPORTS="reports",TOOL_INVENTORY="inventory",TOOL_FREEZE="freeze",TOOL_VANISH="vanish",TOOL_ACTIVITY="activity",TOOL_DASHBOARD="dashboard";
    private final JavaPlugin plugin;private final VanishService vanish;private final Messages messages;private final AuditService audit;private final Set<UUID> active=ConcurrentHashMap.newKeySet();private final File snapshotDirectory;private final NamespacedKey toolKey;
    public StaffModeService(JavaPlugin plugin,VanishService vanish,Messages messages,AuditService audit){this.plugin=plugin;this.vanish=vanish;this.messages=messages;this.audit=audit;snapshotDirectory=new File(plugin.getDataFolder(),"staff-snapshots");snapshotDirectory.mkdirs();toolKey=new NamespacedKey(plugin,"staff_tool");}
    public boolean toggle(Player p){if(isActive(p)){disable(p);return false;}enable(p);return true;} public boolean isActive(Player p){return active.contains(p.getUniqueId());}
    public String toolId(ItemStack item){if(item==null||item.getType().isAir()||!item.hasItemMeta())return null;return item.getItemMeta().getPersistentDataContainer().get(toolKey,PersistentDataType.STRING);} public boolean isStaffTool(ItemStack i){return toolId(i)!=null;}
    public void enable(Player p){if(active.contains(p.getUniqueId()))return;saveSnapshot(p);active.add(p.getUniqueId());p.closeInventory();p.getInventory().clear();p.getInventory().setArmorContents(new ItemStack[4]);p.getInventory().setItemInOffHand(null);p.setGameMode(GameMode.ADVENTURE);p.setAllowFlight(plugin.getConfig().getBoolean("settings.staff-mode.flight",true));if(p.getAllowFlight())p.setFlying(true);if(plugin.getConfig().getBoolean("settings.staff-mode.auto-vanish",true))vanish.set(p,true);giveTools(p);p.updateInventory();audit.log(p,"STAFF_MODE_ENABLE",p.getUniqueId(),p.getName(),null);}
    public void disable(Player p){if(!active.remove(p.getUniqueId())&&!snapshot(p.getUniqueId()).exists())return;vanish.set(p,false);p.closeInventory();restoreSnapshot(p);p.updateInventory();audit.log(p,"STAFF_MODE_DISABLE",p.getUniqueId(),p.getName(),null);}
    public void recoverIfNeeded(Player p){if(snapshot(p.getUniqueId()).exists()&&!active.contains(p.getUniqueId())){active.add(p.getUniqueId());disable(p);}}
    public void shutdown(){for(UUID id:Set.copyOf(active)){Player p=Bukkit.getPlayer(id);if(p!=null)disable(p);}active.clear();}
    private void giveTools(Player p){
        p.getInventory().setItem(0,tool(Material.COMPASS,TOOL_NAVIGATOR,"staff-tools.navigator"));
        p.getInventory().setItem(1,tool(Material.PLAYER_HEAD,TOOL_INSPECTOR,"staff-tools.inspector"));
        p.getInventory().setItem(2,tool(Material.WRITABLE_BOOK,TOOL_REPORTS,"staff-tools.reports"));
        p.getInventory().setItem(3,tool(Material.CHEST,TOOL_INVENTORY,"staff-tools.inventory"));
        p.getInventory().setItem(4,tool(Material.PACKED_ICE,TOOL_FREEZE,"staff-tools.freeze"));
        p.getInventory().setItem(6,tool(Material.ENDER_EYE,TOOL_VANISH,"staff-tools.vanish"));
        p.getInventory().setItem(7,tool(Material.CLOCK,TOOL_ACTIVITY,"staff-tools.activity"));
        p.getInventory().setItem(8,tool(Material.NETHER_STAR,TOOL_DASHBOARD,"staff-tools.dashboard"));
    }
    private ItemStack tool(Material material,String id,String key){ItemStack item=new ItemStack(material);item.editMeta(meta->{meta.displayName(messages.raw(messages.text(key+".name")));meta.lore(List.of(messages.raw(messages.text(key+".lore"))));meta.getPersistentDataContainer().set(toolKey,PersistentDataType.STRING,id);});return item;}
    private File snapshot(UUID id){return new File(snapshotDirectory,id+".yml");}
    private void saveSnapshot(Player p){YamlConfiguration y=new YamlConfiguration();y.set("inventory",Arrays.asList(p.getInventory().getStorageContents()));y.set("armor",Arrays.asList(p.getInventory().getArmorContents()));y.set("offhand",p.getInventory().getItemInOffHand());y.set("gamemode",p.getGameMode().name());y.set("allow-flight",p.getAllowFlight());y.set("flying",p.isFlying());y.set("walk-speed",p.getWalkSpeed());y.set("fly-speed",p.getFlySpeed());y.set("location",p.getLocation());File f=snapshot(p.getUniqueId()),tmp=new File(f.getParentFile(),f.getName()+".tmp");try{y.save(tmp);try{Files.move(tmp.toPath(),f.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(IOException ex){Files.move(tmp.toPath(),f.toPath(),StandardCopyOption.REPLACE_EXISTING);}}catch(IOException ex){throw new IllegalStateException("Could not persist staff-mode snapshot for "+p.getName(),ex);}}
    @SuppressWarnings("unchecked") private void restoreSnapshot(Player p){File f=snapshot(p.getUniqueId());if(!f.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(f);List<ItemStack> inv=(List<ItemStack>)(List<?>)y.getList("inventory",List.of()),armor=(List<ItemStack>)(List<?>)y.getList("armor",List.of());p.getInventory().clear();ItemStack[] storage=new ItemStack[p.getInventory().getStorageContents().length];for(int i=0;i<Math.min(storage.length,inv.size());i++)storage[i]=inv.get(i);p.getInventory().setStorageContents(storage);if(armor.size()==4)p.getInventory().setArmorContents(armor.toArray(ItemStack[]::new));p.getInventory().setItemInOffHand(y.getItemStack("offhand"));try{p.setGameMode(GameMode.valueOf(y.getString("gamemode","SURVIVAL")));}catch(IllegalArgumentException ex){p.setGameMode(GameMode.SURVIVAL);}p.setAllowFlight(y.getBoolean("allow-flight",false));if(p.getAllowFlight())p.setFlying(y.getBoolean("flying",false));p.setWalkSpeed((float)y.getDouble("walk-speed",0.2));p.setFlySpeed((float)y.getDouble("fly-speed",0.1));Location loc=y.getLocation("location");if(loc!=null&&loc.getWorld()!=null)p.teleportAsync(loc);try{Files.deleteIfExists(f.toPath());}catch(IOException ex){plugin.getLogger().warning("Could not remove restored snapshot "+f.getName());}}
}
