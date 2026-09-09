package com.staffops.evidence;

import com.staffops.activity.ActivityService;
import com.staffops.data.Database;
import com.staffops.model.ActivityRecord;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class EvidenceService {
    private final Database db; private final ActivityService activity;
    public EvidenceService(Database db, ActivityService activity){this.db=db;this.activity=activity;}

    public CompletableFuture<Long> capture(CommandSender staff, UUID target, String targetName, Long caseId, String reason) {
        List<ActivityRecord> snapshot=activity.recent(target,50); long now=System.currentTimeMillis();
        return db.supplyAsync(c->{boolean old=c.getAutoCommit();c.setAutoCommit(false);try{long evidenceId;try(PreparedStatement ps=c.prepareStatement("INSERT INTO evidence(case_id,target_uuid,target_name,created_by,created_by_name,reason,created_at) VALUES(?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){if(caseId==null)ps.setNull(1,Types.BIGINT);else ps.setLong(1,caseId);ps.setString(2,target.toString());ps.setString(3,targetName);ps.setString(4,staff instanceof Player p?p.getUniqueId().toString():null);ps.setString(5,staff.getName());ps.setString(6,reason);ps.setLong(7,now);ps.executeUpdate();try(ResultSet r=ps.getGeneratedKeys()){r.next();evidenceId=r.getLong(1);}}
            try(PreparedStatement ps=c.prepareStatement("INSERT INTO evidence_events(evidence_id,event_type,details,created_at) VALUES(?,?,?,?)")){for(ActivityRecord a:snapshot){ps.setLong(1,evidenceId);ps.setString(2,a.type());ps.setString(3,a.details());ps.setLong(4,a.createdAt());ps.addBatch();}ps.executeBatch();}c.commit();return evidenceId;}catch(SQLException ex){c.rollback();throw ex;}finally{c.setAutoCommit(old);}});
    }

    public CompletableFuture<List<String>> listForPlayer(UUID target,int limit){return db.supplyAsync(c->{List<String> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT e.id,e.reason,e.created_at,e.case_id,(SELECT COUNT(*) FROM evidence_events x WHERE x.evidence_id=e.id) event_count FROM evidence e WHERE target_uuid=? ORDER BY created_at DESC LIMIT ?")){ps.setString(1,target.toString());ps.setInt(2,limit);try(ResultSet r=ps.executeQuery()){while(r.next())out.add("#"+r.getLong("id")+" | "+r.getString("reason")+" | "+r.getInt("event_count")+" events"+(r.getObject("case_id")!=null?" | Case #"+r.getLong("case_id"):""));}}return out;});}
}
