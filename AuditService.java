package com.staffops.audit;
import com.staffops.data.Database;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.sql.PreparedStatement;
import java.util.UUID;
public final class AuditService {
    private final Database db;
    public AuditService(Database db){this.db=db;}
    public void log(CommandSender actor,String action,UUID target,String targetName,String details){
        UUID actorId=actor instanceof Player p?p.getUniqueId():null; String actorName=actor.getName(); long now=System.currentTimeMillis();
        db.runAsync(c->{try(PreparedStatement ps=c.prepareStatement("INSERT INTO audit(actor_uuid,actor_name,action,target_uuid,target_name,details,created_at) VALUES(?,?,?,?,?,?,?)")){ps.setString(1,actorId==null?null:actorId.toString());ps.setString(2,actorName);ps.setString(3,action);ps.setString(4,target==null?null:target.toString());ps.setString(5,targetName);ps.setString(6,details);ps.setLong(7,now);ps.executeUpdate();}});
    }
}
