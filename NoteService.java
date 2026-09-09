package com.staffops.player;
import com.staffops.audit.AuditService; import com.staffops.data.Database; import org.bukkit.entity.Player;
import java.sql.*; import java.util.*; import java.util.concurrent.CompletableFuture;
public final class NoteService {
    public record Note(long id,String staff,String text,long createdAt){}
    private final Database db;private final AuditService audit;public NoteService(Database db,AuditService audit){this.db=db;this.audit=audit;}
    public void add(Player staff,UUID target,String targetName,String note){long now=System.currentTimeMillis();db.runAsync(c->{try(PreparedStatement ps=c.prepareStatement("INSERT INTO notes(target_uuid,target_name,staff_uuid,staff_name,note,created_at) VALUES(?,?,?,?,?,?)")){ps.setString(1,target.toString());ps.setString(2,targetName);ps.setString(3,staff.getUniqueId().toString());ps.setString(4,staff.getName());ps.setString(5,note);ps.setLong(6,now);ps.executeUpdate();}});audit.log(staff,"NOTE_ADD",target,targetName,note);}
    public CompletableFuture<List<Note>> list(UUID target,int limit){return db.supplyAsync(c->{List<Note> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT id,staff_name,note,created_at FROM notes WHERE target_uuid=? ORDER BY created_at DESC LIMIT ?")){ps.setString(1,target.toString());ps.setInt(2,limit);try(ResultSet r=ps.executeQuery()){while(r.next())out.add(new Note(r.getLong(1),r.getString(2),r.getString(3),r.getLong(4)));}}return out;});}
}
