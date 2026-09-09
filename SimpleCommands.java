package com.staffops.command;

import com.staffops.StaffOpsPlugin;
import com.staffops.model.PlayerProfile;
import com.staffops.report.ReportService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.BiConsumer;

public final class SimpleCommands implements CommandExecutor, TabCompleter {
    private final StaffOpsPlugin plugin;
    public SimpleCommands(StaffOpsPlugin plugin){this.plugin=plugin;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){String name=command.getName().toLowerCase(Locale.ROOT);
        switch(name){
            case "staffmode"->{Player p=player(sender);if(p==null)return true;boolean enabled=plugin.staffMode().toggle(p);p.sendMessage(plugin.messages().staff(enabled?"staffmode.enabled":"staffmode.disabled"));}
            case "vanish"->{Player p=player(sender);if(p==null)return true;boolean enabled=plugin.vanish().toggle(p);p.sendMessage(plugin.messages().staff(enabled?"vanish.enabled":"vanish.disabled"));}
            case "freeze"->{Player staff=player(sender);if(staff==null)return true;if(args.length<1){staff.sendMessage(plugin.messages().staff("usage.freeze"));return true;}Player target=Bukkit.getPlayerExact(args[0]);if(target==null){staff.sendMessage(plugin.messages().staff("player-not-found"));return true;}plugin.toggleFreeze(staff,target);}
            case "punish"->{Player staff=player(sender);if(staff==null)return true;if(args.length<1){staff.sendMessage(plugin.messages().staff("usage.punish"));return true;}resolve(args[0],staff,(id,n)->plugin.gui().punishMenu(staff,id,n));}
            case "inspect"->{Player staff=player(sender);if(staff==null)return true;if(args.length<1){staff.sendMessage(plugin.messages().staff("usage.inspect"));return true;}resolve(args[0],staff,(id,n)->plugin.gui().profile(staff,id,n));}
            case "reports"->{Player p=player(sender);if(p==null)return true;plugin.gui().reports(p);}
            case "cases"->{Player p=player(sender);if(p==null)return true;plugin.gui().cases(p);}
            case "case"->{Player staff=player(sender);if(staff==null)return true;handleCase(staff,args);}
            case "evidence"->{Player staff=player(sender);if(staff==null)return true;if(args.length<1){staff.sendMessage(plugin.messages().staff("usage.evidence"));return true;}resolve(args[0],staff,(id,n)->plugin.gui().evidence(staff,id,n));}
            case "operations"->{Player p=player(sender);if(p==null)return true;plugin.gui().operations(p);}
            case "report"->{handleReport(sender,args);}
            case "staffchat"->{if(args.length==0){sender.sendMessage(plugin.messages().staff("usage.staffchat"));return true;}String msg=String.join(" ",args);for(Player p:Bukkit.getOnlinePlayers())if(p.hasPermission("staffops.staffchat"))p.sendMessage(plugin.messages().staff("staffchat",Map.of("staff",sender.getName(),"message",msg)));plugin.audit().log(sender,"STAFF_CHAT",null,null,msg);}
            default->{return false;}
        }return true;
    }

    private void handleReport(CommandSender sender,String[] args){if(!(sender instanceof Player reporter)){sender.sendMessage(plugin.messages().player("player-only"));return;}if(args.length<1){reporter.sendMessage(plugin.messages().forAudience(reporter,"usage.report"));return;}Player target=Bukkit.getPlayerExact(args[0]);if(target==null){reporter.sendMessage(plugin.messages().forAudience(reporter,"player-not-found"));return;}if(args.length==1){plugin.gui().reportCategories(reporter,target);return;}
        ReportService.Category category=ReportService.Category.OTHER;int reasonStart=1;if(args.length>=3){ReportService.Category parsed=ReportService.Category.parse(args[1]);if(parsed!=ReportService.Category.OTHER||args[1].equalsIgnoreCase("other")){category=parsed;reasonStart=2;}}
        String reason=String.join(" ",Arrays.copyOfRange(args,reasonStart,args.length));int limit=plugin.getConfig().getInt("settings.reports.max-reason-length",160);if(reason.isBlank()){reporter.sendMessage(plugin.messages().forAudience(reporter,"usage.report"));return;}if(reason.length()>limit){reporter.sendMessage(plugin.messages().forAudience(reporter,"reports.reason-too-long",Map.of("limit",String.valueOf(limit))));return;}
        ReportService.Category finalCategory=category;plugin.reports().create(reporter,target,category,reason).whenComplete((id,error)->Bukkit.getScheduler().runTask(plugin,()->{if(!reporter.isOnline())return;if(error==null){reporter.sendMessage(plugin.messages().forAudience(reporter,"reports.created",Map.of("player",target.getName(),"category",finalCategory.display())));plugin.activity().record(reporter,"REPORT","Reported "+target.getName()+" for "+finalCategory.display()+": "+reason);plugin.evidence().capture(reporter,target.getUniqueId(),target.getName(),null,"Automatic report snapshot #"+id);return;}String key=switch(rootMessage(error)){case "cooldown"->"reports.cooldown";case "open-limit"->"reports.open-limit";default->"reports.failed";};reporter.sendMessage(plugin.messages().forAudience(reporter,key));}));
    }

    private void handleCase(Player staff,String[] args){if(!staff.hasPermission("staffops.cases")){staff.sendMessage(plugin.messages().staff("no-permission"));return;}if(args.length==0){plugin.gui().cases(staff);return;}if(args[0].equalsIgnoreCase("create")){if(args.length<3){staff.sendMessage(plugin.messages().staff("usage.case-create"));return;}String reason=String.join(" ",Arrays.copyOfRange(args,2,args.length));resolve(args[1],staff,(id,n)->plugin.cases().create(staff,id,n,reason,"NORMAL").thenAccept(caseId->Bukkit.getScheduler().runTask(plugin,()->staff.sendMessage(plugin.messages().staff("cases.created",Map.of("id",String.valueOf(caseId),"player",n))))));return;}try{long id=Long.parseLong(args[0]);plugin.cases().get(id).thenAccept(opt->Bukkit.getScheduler().runTask(plugin,()->opt.ifPresentOrElse(c->{plugin.cases().activate(staff,c);plugin.gui().caseDetail(staff,c);},()->staff.sendMessage(plugin.messages().staff("cases.not-found")))));}catch(NumberFormatException ex){staff.sendMessage(plugin.messages().staff("usage.case"));}}

    private Player player(CommandSender sender){if(sender instanceof Player p)return p;sender.sendMessage(plugin.messages().staff("player-only"));return null;}
    private void resolve(String query,Player viewer,BiConsumer<UUID,String> success){Player live=Bukkit.getPlayerExact(query);if(live!=null){success.accept(live.getUniqueId(),live.getName());return;}plugin.playerData().byName(query).thenAccept(opt->Bukkit.getScheduler().runTask(plugin,()->opt.ifPresentOrElse(p->success.accept(p.uuid(),p.name()),()->viewer.sendMessage(plugin.messages().staff("player-not-found")))));}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(Set.of("freeze","punish","report","inspect","evidence").contains(command.getName().toLowerCase(Locale.ROOT))&&args.length==1)return players(args[0]);if(command.getName().equalsIgnoreCase("report")&&args.length==2)return Arrays.stream(ReportService.Category.values()).map(c->c.name().toLowerCase(Locale.ROOT)).filter(s->s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();if(command.getName().equalsIgnoreCase("case")&&args.length==1)return List.of("create");if(command.getName().equalsIgnoreCase("case")&&args.length==2&&args[0].equalsIgnoreCase("create"))return players(args[1]);return List.of();}
    private List<String> players(String q){String s=q.toLowerCase(Locale.ROOT);return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n->n.toLowerCase(Locale.ROOT).startsWith(s)).toList();}
    private String rootMessage(Throwable t){Throwable c=t;while(c.getCause()!=null)c=c.getCause();return c.getMessage()==null?"":c.getMessage();}
}
