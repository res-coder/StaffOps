package com.staffops;

import com.staffops.activity.ActivityService;
import com.staffops.audit.AuditService;
import com.staffops.casefile.CaseService;
import com.staffops.command.MainCommand;
import com.staffops.command.SimpleCommands;
import com.staffops.config.Messages;
import com.staffops.data.Database;
import com.staffops.evidence.EvidenceService;
import com.staffops.gui.GuiService;
import com.staffops.integration.ServerIntegrationService;
import com.staffops.investigation.InvestigationService;
import com.staffops.listener.CoreListener;
import com.staffops.listener.LoginListener;
import com.staffops.moderation.PunishmentService;
import com.staffops.operations.OperationsService;
import com.staffops.player.NoteService;
import com.staffops.player.PlayerDataService;
import com.staffops.report.ReportService;
import com.staffops.staff.FreezeService;
import com.staffops.staff.StaffModeService;
import com.staffops.staff.VanishService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;

public final class StaffOpsPlugin extends JavaPlugin {
    private Database database; private Messages messages; private AuditService audit; private PlayerDataService playerData;
    private NoteService notes; private VanishService vanish; private FreezeService freeze; private StaffModeService staffMode;
    private PunishmentService punishments; private ReportService reports; private ActivityService activity; private CaseService cases;
    private EvidenceService evidence; private ServerIntegrationService integrations; private InvestigationService investigation;
    private OperationsService operations; private GuiService gui;

    @Override public void onEnable(){
        saveDefaultConfig(); validateConfig(); messages=new Messages(this); database=new Database(this); audit=new AuditService(database);
        playerData=new PlayerDataService(database,getConfig()); notes=new NoteService(database,audit); vanish=new VanishService(this); freeze=new FreezeService();
        activity=new ActivityService(getConfig()); cases=new CaseService(database,audit); evidence=new EvidenceService(database,activity); integrations=new ServerIntegrationService();
        staffMode=new StaffModeService(this,vanish,messages,audit); punishments=new PunishmentService(this,database,messages,audit); reports=new ReportService(this,database,messages,audit);
        operations=new OperationsService(this); investigation=new InvestigationService(this); gui=new GuiService(this);
        registerCommands(); Bukkit.getPluginManager().registerEvents(new CoreListener(this),this); Bukkit.getPluginManager().registerEvents(new LoginListener(this),this);
        for(Player player:Bukkit.getOnlinePlayers())playerData.joined(player);
        getLogger().info("StaffOps "+getPluginMeta().getVersion()+" enabled on Paper "+Bukkit.getMinecraftVersion()+".");
    }

    @Override public void onDisable(){if(investigation!=null)investigation.shutdown();if(staffMode!=null)staffMode.shutdown();if(vanish!=null)vanish.shutdown();if(freeze!=null)freeze.clear();if(playerData!=null)for(Player p:Bukkit.getOnlinePlayers())playerData.quit(p);if(database!=null)database.close();}
    public void reloadStaffOpsConfig(){reloadConfig();messages.reload();validateConfig();}

    private void registerCommands(){MainCommand main=new MainCommand(this);bind("staffops",main);SimpleCommands simple=new SimpleCommands(this);for(String name:new String[]{"staffmode","vanish","freeze","punish","report","reports","staffchat","inspect","cases","case","evidence","operations"})bind(name,simple);}
    private void bind(String name,CommandExecutor executor){PluginCommand c=Objects.requireNonNull(getCommand(name));c.setExecutor(executor);if(executor instanceof TabCompleter tc)c.setTabCompleter(tc);}
    private void validateConfig(){String salt=getConfig().getString("settings.privacy.ip-hash-salt","");if(salt.equals("CHANGE-ME-TO-A-LONG-RANDOM-SECRET")||salt.length()<16)getLogger().warning("Change settings.privacy.ip-hash-salt to a long random secret before production use.");if(getConfig().getBoolean("settings.privacy.store-raw-ip",false))getLogger().warning("Raw IP storage is enabled. Protect StaffOps data and restrict staffops.profile.ip permissions.");}

    public boolean canModerate(Player staff,Player target){return !getConfig().getBoolean("settings.punishments.protect-staff",true)||!target.hasPermission("staffops.protected")||staff.hasPermission("staffops.override.protected");}
    public void toggleFreeze(Player staff,Player target){if(!canModerate(staff,target)){staff.sendMessage(messages.staff("protected-player"));return;}boolean state=freeze.toggle(target.getUniqueId());target.sendMessage(messages.forAudience(target,state?"freeze.target-frozen":"freeze.target-unfrozen"));staff.sendMessage(messages.staff("freeze.staff-state",Map.of("player",target.getName(),"state",state?"frozen":"unfrozen")));audit.log(staff,state?"FREEZE":"UNFREEZE",target.getUniqueId(),target.getName(),null);cases.recordActive(staff,state?"FREEZE":"UNFREEZE",target.getName());}

    public Database database(){return database;} public Messages messages(){return messages;} public AuditService audit(){return audit;} public PlayerDataService playerData(){return playerData;} public NoteService notes(){return notes;} public VanishService vanish(){return vanish;} public FreezeService freeze(){return freeze;} public StaffModeService staffMode(){return staffMode;} public PunishmentService punishments(){return punishments;} public ReportService reports(){return reports;} public ActivityService activity(){return activity;} public CaseService cases(){return cases;} public EvidenceService evidence(){return evidence;} public ServerIntegrationService integrations(){return integrations;} public InvestigationService investigation(){return investigation;} public OperationsService operations(){return operations;} public GuiService gui(){return gui;}
}
