package com.staffops.operations;

import com.staffops.StaffOpsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;

public final class OperationsService {
    private final StaffOpsPlugin plugin; private volatile boolean maintenance;
    public OperationsService(StaffOpsPlugin plugin){this.plugin=plugin;this.maintenance=plugin.getConfig().getBoolean("settings.operations.maintenance-on-start",false);}
    public boolean maintenance(){return maintenance;}
    public void setMaintenance(boolean value){maintenance=value;}
    public Snapshot snapshot(){long used=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();long max=Runtime.getRuntime().maxMemory();int chunks=0,entities=0;for(World w:Bukkit.getWorlds()){chunks+=w.getLoadedChunks().length;entities+=w.getEntityCount();}double[] tps=Bukkit.getTPS();return new Snapshot(tps.length>0?tps[0]:20.0,Bukkit.getAverageTickTime(),used,max,Bukkit.getOnlinePlayers().size(),Bukkit.getMaxPlayers(),chunks,entities,maintenance,Bukkit.hasWhitelist());}
    public record Snapshot(double tps,double mspt,long usedMemory,long maxMemory,int players,int maxPlayers,int chunks,int entities,boolean maintenance,boolean whitelist){}
}
