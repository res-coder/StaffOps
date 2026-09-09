package com.staffops.report;

import com.staffops.audit.AuditService;
import com.staffops.config.Messages;
import com.staffops.data.Database;
import com.staffops.model.ReportRecord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ReportService {
    public enum Category {
        CHEATING("Cheating"), CHAT_ABUSE("Chat Abuse"), SCAMMING("Scamming"), GRIEFING("Griefing"),
        INAPPROPRIATE("Inappropriate Skin/Name"), OTHER("Other");
        private final String display;
        Category(String display){this.display=display;}
        public String display(){return display;}
        public static Category parse(String raw){String normalized=raw.toUpperCase(Locale.ROOT).replace('-','_');for(Category c:values())if(c.name().equals(normalized)||c.display.equalsIgnoreCase(raw))return c;return OTHER;}
    }

    private final JavaPlugin plugin; private final Database database; private final Messages messages; private final AuditService audit;
    private final Map<UUID, Long> lastReport=new ConcurrentHashMap<>();
    private final Map<UUID, Draft> drafts=new ConcurrentHashMap<>();
    public record Draft(UUID target,String targetName,Category category){}
    public ReportService(JavaPlugin plugin,Database database,Messages messages,AuditService audit){this.plugin=plugin;this.database=database;this.messages=messages;this.audit=audit;}

    public CompletableFuture<Long> create(Player reporter,Player target,Category category,String reason){ return create(reporter,target.getUniqueId(),target.getName(),category,reason); }

    private CompletableFuture<Long> create(Player reporter,UUID targetId,String targetName,Category category,String reason){
        long now=System.currentTimeMillis();UUID reporterId=reporter.getUniqueId();String reporterName=reporter.getName();Long last=lastReport.get(reporterId);int cooldown=plugin.getConfig().getInt("settings.reports.cooldown-seconds",60),maxOpen=plugin.getConfig().getInt("settings.reports.max-open-per-reporter",3);if(last!=null&&now-last<cooldown*1000L)return CompletableFuture.failedFuture(new IllegalStateException("cooldown"));
        long duplicateWindow=Math.max(1,plugin.getConfig().getInt("settings.reports.duplicate-window-minutes",10))*60_000L;
        return database.supplyAsync(c->{try(PreparedStatement count=c.prepareStatement("SELECT COUNT(*) FROM reports WHERE reporter_uuid=? AND status IN ('OPEN','CLAIMED','WATCHING','ESCALATED')")){count.setString(1,reporterId.toString());try(ResultSet r=count.executeQuery()){if(r.next()&&r.getInt(1)>=maxOpen)throw new IllegalStateException("open-limit");}}
            int duplicates=0;try(PreparedStatement count=c.prepareStatement("SELECT COUNT(*) FROM reports WHERE target_uuid=? AND category=? AND status IN ('OPEN','CLAIMED','WATCHING','ESCALATED') AND created_at>=?")){count.setString(1,targetId.toString());count.setString(2,category.name());count.setLong(3,now-duplicateWindow);try(ResultSet r=count.executeQuery()){if(r.next())duplicates=r.getInt(1);}}
            String finalPriority=duplicates>=2?"CRITICAL":(category==Category.CHEATING||duplicates>=1?"HIGH":"NORMAL");
            try(PreparedStatement ps=c.prepareStatement("INSERT INTO reports(reporter_uuid,reporter_name,target_uuid,target_name,category,priority,reason,status,created_at) VALUES(?,?,?,?,?,?,?,'OPEN',?)",Statement.RETURN_GENERATED_KEYS)){ps.setString(1,reporterId.toString());ps.setString(2,reporterName);ps.setString(3,targetId.toString());ps.setString(4,targetName);ps.setString(5,category.name());ps.setString(6,finalPriority);ps.setString(7,reason);ps.setLong(8,now);ps.executeUpdate();try(ResultSet keys=ps.getGeneratedKeys()){keys.next();return new Created(keys.getLong(1),finalPriority);}}}).thenApply(created->{lastReport.put(reporterId,now);Bukkit.getScheduler().runTask(plugin,()->{for(Player staff:Bukkit.getOnlinePlayers())if(staff.hasPermission("staffops.reports"))staff.sendMessage(messages.staff("reports.staff-alert",Map.of("reporter",reporterName,"target",targetName,"category",category.display(),"priority",created.priority(),"reason",reason)));});return created.id();});
    }

    private record Created(long id,String priority){}


    public void beginDraft(Player reporter, Player target, Category category){drafts.put(reporter.getUniqueId(),new Draft(target.getUniqueId(),target.getName(),category));}
    public boolean hasDraft(Player reporter){return drafts.containsKey(reporter.getUniqueId());}
    public void cancelDraft(Player reporter){drafts.remove(reporter.getUniqueId());}
    public CompletableFuture<Long> completeDraft(Player reporter,String reason){Draft d=drafts.remove(reporter.getUniqueId());if(d==null)return CompletableFuture.failedFuture(new IllegalStateException("no-draft"));return create(reporter,d.target(),d.targetName(),d.category(),reason);}

    public CompletableFuture<List<ReportRecord>> openReports(){
        long window=Math.max(1,plugin.getConfig().getInt("settings.reports.duplicate-window-minutes",10))*60_000L;
        return database.supplyAsync(c->{List<ReportRecord> list=new ArrayList<>();String sql="SELECT r.*, (SELECT COUNT(*) FROM reports d WHERE d.target_uuid=r.target_uuid AND d.category=r.category AND d.status IN ('OPEN','CLAIMED','WATCHING','ESCALATED') AND ABS(d.created_at-r.created_at)<=?) duplicate_count FROM reports r WHERE r.status IN ('OPEN','CLAIMED','WATCHING','ESCALATED') AND r.id=(SELECT MIN(x.id) FROM reports x WHERE x.target_uuid=r.target_uuid AND x.category=r.category AND x.status IN ('OPEN','CLAIMED','WATCHING','ESCALATED') AND ABS(x.created_at-r.created_at)<=?) ORDER BY CASE r.priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END,r.created_at ASC LIMIT 200";try(PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,window);ps.setLong(2,window);try(ResultSet r=ps.executeQuery()){while(r.next())list.add(read(r));}}return list;});
    }

    public CompletableFuture<List<ReportRecord>> forPlayer(UUID target,int limit){return database.supplyAsync(c->{List<ReportRecord> list=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT r.*,1 duplicate_count FROM reports r WHERE target_uuid=? ORDER BY created_at DESC LIMIT ?")){ps.setString(1,target.toString());ps.setInt(2,limit);try(ResultSet r=ps.executeQuery()){while(r.next())list.add(read(r));}}return list;});}
    public CompletableFuture<Integer> openCount(){return database.supplyAsync(c->{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM reports WHERE status IN ('OPEN','CLAIMED','WATCHING','ESCALATED')")){return r.next()?r.getInt(1):0;}});}


    public void claimGroup(Player staff, ReportRecord r){updateGroup(staff,r,"CLAIMED","REPORT_GROUP_CLAIM",false,null);}
    public void watchGroup(Player staff, ReportRecord r){updateGroup(staff,r,"WATCHING","REPORT_GROUP_WATCH",false,null);}
    public void escalateGroup(Player staff, ReportRecord r){updateGroup(staff,r,"ESCALATED","REPORT_GROUP_ESCALATE",true,null);}
    public void dismissGroup(Player staff, ReportRecord r,String resolution){updateGroup(staff,r,"RESOLVED","REPORT_GROUP_DISMISS",false,"Dismissed: "+resolution);}
    public void resolveGroup(Player staff, ReportRecord r,String resolution){updateGroup(staff,r,"RESOLVED","REPORT_GROUP_RESOLVE",false,resolution);}
    private void updateGroup(Player staff,ReportRecord r,String status,String auditAction,boolean critical,String resolution){long window=Math.max(1,plugin.getConfig().getInt("settings.reports.duplicate-window-minutes",10))*60_000L;long now=System.currentTimeMillis();database.runAsync(c->{String sql="UPDATE reports SET status=?,claimed_by=?,claimed_name=?"+(critical?",priority='CRITICAL'":"")+(status.equals("RESOLVED")?",resolved_at=?,resolution=?":"")+" WHERE target_uuid=? AND category=? AND status IN ('OPEN','CLAIMED','WATCHING','ESCALATED') AND ABS(created_at-?)<=?";try(PreparedStatement ps=c.prepareStatement(sql)){int i=1;ps.setString(i++,status);ps.setString(i++,staff.getUniqueId().toString());ps.setString(i++,staff.getName());if(status.equals("RESOLVED")){ps.setLong(i++,now);ps.setString(i++,resolution);}ps.setString(i++,r.target().toString());ps.setString(i++,r.category());ps.setLong(i++,r.createdAt());ps.setLong(i,window);ps.executeUpdate();}});audit.log(staff,auditAction,r.target(),r.targetName(),"category="+r.category()+", reports="+r.duplicateCount());}

    public void claim(Player staff,long id){updateState(staff,id,"CLAIMED","REPORT_CLAIM");}
    public void watch(Player staff,long id){updateState(staff,id,"WATCHING","REPORT_WATCH");}
    public void escalate(Player staff,long id){database.runAsync(c->{try(PreparedStatement ps=c.prepareStatement("UPDATE reports SET status='ESCALATED',priority='CRITICAL',claimed_by=?,claimed_name=? WHERE id=? AND status IN ('OPEN','CLAIMED','WATCHING')")){ps.setString(1,staff.getUniqueId().toString());ps.setString(2,staff.getName());ps.setLong(3,id);ps.executeUpdate();}});audit.log(staff,"REPORT_ESCALATE",null,null,"report="+id);}
    public void dismiss(Player staff,long id,String resolution){resolve(staff,id,"Dismissed: "+resolution);}
    public void resolve(Player staff,long id,String resolution){long now=System.currentTimeMillis();database.runAsync(c->{try(PreparedStatement ps=c.prepareStatement("UPDATE reports SET status='RESOLVED',resolved_at=?,resolution=? WHERE id=? AND status IN ('OPEN','CLAIMED','WATCHING','ESCALATED')")){ps.setLong(1,now);ps.setString(2,resolution);ps.setLong(3,id);ps.executeUpdate();}});audit.log(staff,"REPORT_RESOLVE",null,null,"report="+id+", resolution="+resolution);}
    private void updateState(Player staff,long id,String state,String auditAction){database.runAsync(c->{try(PreparedStatement ps=c.prepareStatement("UPDATE reports SET status=?,claimed_by=?,claimed_name=? WHERE id=? AND status IN ('OPEN','CLAIMED','WATCHING')")){ps.setString(1,state);ps.setString(2,staff.getUniqueId().toString());ps.setString(3,staff.getName());ps.setLong(4,id);ps.executeUpdate();}});audit.log(staff,auditAction,null,null,"report="+id);}

    private ReportRecord read(ResultSet r)throws SQLException{String claimed=r.getString("claimed_by");return new ReportRecord(r.getLong("id"),UUID.fromString(r.getString("reporter_uuid")),r.getString("reporter_name"),UUID.fromString(r.getString("target_uuid")),r.getString("target_name"),r.getString("category"),r.getString("priority"),r.getString("reason"),r.getString("status"),claimed==null?null:UUID.fromString(claimed),r.getString("claimed_name"),r.getLong("created_at"),r.getInt("duplicate_count"));}
}
