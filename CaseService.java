package com.staffops.casefile;

import com.staffops.audit.AuditService;
import com.staffops.data.Database;
import com.staffops.model.CaseEvent;
import com.staffops.model.CaseRecord;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class CaseService {
    private final Database db;
    private final AuditService audit;
    private final Map<UUID, Session> activeSessions = new ConcurrentHashMap<>();
    private record Session(long caseId, UUID target, String targetName) {}

    public CaseService(Database db, AuditService audit) { this.db = db; this.audit = audit; }

    public CompletableFuture<Long> create(Player staff, UUID target, String targetName, String reason, String priority) {
        long now = System.currentTimeMillis();
        return db.supplyAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO moderation_cases(target_uuid,target_name,opened_by,opened_by_name,reason,status,priority,created_at) VALUES(?,?,?,?,?,'OPEN',?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1,target.toString()); ps.setString(2,targetName); ps.setString(3,staff.getUniqueId().toString()); ps.setString(4,staff.getName()); ps.setString(5,reason); ps.setString(6,priority); ps.setLong(7,now); ps.executeUpdate();
                try(ResultSet r=ps.getGeneratedKeys()){r.next(); return r.getLong(1);}
            }
        }).thenApply(id -> { activeSessions.put(staff.getUniqueId(), new Session(id, target, targetName)); addEvent(id, staff, "CASE_OPENED", reason); audit.log(staff,"CASE_OPEN",target,targetName,"case="+id+", reason="+reason); return id; });
    }

    public CompletableFuture<List<CaseRecord>> openCases() { return db.supplyAsync(c -> queryCases(c, "WHERE status='OPEN' ORDER BY CASE priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END, created_at ASC LIMIT 200")); }
    public CompletableFuture<List<CaseRecord>> forPlayer(UUID target, int limit) { return db.supplyAsync(c -> { try(PreparedStatement ps=c.prepareStatement("SELECT * FROM moderation_cases WHERE target_uuid=? ORDER BY created_at DESC LIMIT ?")){ps.setString(1,target.toString());ps.setInt(2,limit);try(ResultSet r=ps.executeQuery()){return readCases(r);}} }); }
    public CompletableFuture<Optional<CaseRecord>> get(long id) { return db.supplyAsync(c -> { try(PreparedStatement ps=c.prepareStatement("SELECT * FROM moderation_cases WHERE id=?")){ps.setLong(1,id);try(ResultSet r=ps.executeQuery()){return r.next()?Optional.of(readCase(r)):Optional.empty();}} }); }
    public CompletableFuture<List<CaseEvent>> events(long caseId, int limit) { return db.supplyAsync(c -> {List<CaseEvent> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT * FROM case_events WHERE case_id=? ORDER BY created_at DESC LIMIT ?")){ps.setLong(1,caseId);ps.setInt(2,limit);try(ResultSet r=ps.executeQuery()){while(r.next())out.add(new CaseEvent(r.getLong("id"),caseId,uuid(r.getString("actor_uuid")),r.getString("actor_name"),r.getString("type"),r.getString("details"),r.getLong("created_at")));}}return out;}); }

    public void activate(Player staff, CaseRecord record) { activeSessions.put(staff.getUniqueId(), new Session(record.id(), record.target(), record.targetName())); addEvent(record.id(), staff, "INVESTIGATION_SESSION", "Staff opened investigation session"); }
    public Long activeCase(Player staff) { Session s=activeSessions.get(staff.getUniqueId()); return s==null?null:s.caseId(); }
    public void endSession(Player staff) { Session s=activeSessions.remove(staff.getUniqueId()); if(s!=null)addEvent(s.caseId(),staff,"SESSION_ENDED","Investigation session ended"); }
    public void recordActive(Player staff, String type, String details) { Session s=activeSessions.get(staff.getUniqueId()); if(s!=null)addEvent(s.caseId(),staff,type,details); }
    public void recordTargetActivity(Player target, String type, String details) { for (Session s : Set.copyOf(activeSessions.values())) if (s.target().equals(target.getUniqueId())) addEvent(s.caseId(), target, "PLAYER_"+type, details); }


    public void addEvent(long caseId, CommandSender actor, String type, String details) {
        String actorUuid = actor instanceof Player p ? p.getUniqueId().toString() : null; String actorName=actor.getName(); long now=System.currentTimeMillis();
        db.runAsync(c->{try(PreparedStatement ps=c.prepareStatement("INSERT INTO case_events(case_id,actor_uuid,actor_name,type,details,created_at) VALUES(?,?,?,?,?,?)")){ps.setLong(1,caseId);ps.setString(2,actorUuid);ps.setString(3,actorName);ps.setString(4,type);ps.setString(5,details);ps.setLong(6,now);ps.executeUpdate();}});
    }

    public void close(Player staff, long id, String resolution) {
        long now=System.currentTimeMillis();
        db.runAsync(c->{try(PreparedStatement ps=c.prepareStatement("UPDATE moderation_cases SET status='CLOSED',closed_at=?,resolution=? WHERE id=? AND status='OPEN'")){ps.setLong(1,now);ps.setString(2,resolution);ps.setLong(3,id);ps.executeUpdate();}});
        addEvent(id,staff,"CASE_CLOSED",resolution); Session active=activeSessions.get(staff.getUniqueId());if(active!=null&&active.caseId()==id)activeSessions.remove(staff.getUniqueId()); audit.log(staff,"CASE_CLOSE",null,null,"case="+id+", resolution="+resolution);
    }

    private List<CaseRecord> queryCases(Connection c,String suffix)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM moderation_cases "+suffix)){return readCases(r);}}
    private List<CaseRecord> readCases(ResultSet r)throws SQLException{List<CaseRecord> out=new ArrayList<>();while(r.next())out.add(readCase(r));return out;}
    private CaseRecord readCase(ResultSet r)throws SQLException{long closed=r.getLong("closed_at");boolean closedNull=r.wasNull();return new CaseRecord(r.getLong("id"),UUID.fromString(r.getString("target_uuid")),r.getString("target_name"),uuid(r.getString("opened_by")),r.getString("opened_by_name"),r.getString("reason"),r.getString("status"),r.getString("priority"),r.getLong("created_at"),closedNull?null:closed);}
    private UUID uuid(String raw){return raw==null?null:UUID.fromString(raw);}
}
