package com.staffops.player;

import com.staffops.data.Database;
import com.staffops.model.IdentitySnapshot;
import com.staffops.model.PlayerProfile;
import com.staffops.util.HashUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataService {
    private final Database db;
    private final boolean hashIps;
    private final boolean storeRawIps;
    private final String salt;
    private final Map<UUID, Long> sessions = new ConcurrentHashMap<>();

    public PlayerDataService(Database db, FileConfiguration cfg){
        this.db=db;
        hashIps=cfg.getBoolean("settings.privacy.store-ip-hash",true);
        storeRawIps=cfg.getBoolean("settings.privacy.store-raw-ip",false);
        salt=cfg.getString("settings.privacy.ip-hash-salt","");
    }

    public void joined(Player p){
        long now=System.currentTimeMillis(); sessions.put(p.getUniqueId(),now);
        String raw=rawIp(p); String hash=raw==null?null:hash(raw);
        db.runAsync(c->{
            try(PreparedStatement ps=c.prepareStatement("INSERT INTO players(uuid,name,first_seen,last_seen,playtime_seconds,ip_hash,last_join) VALUES(?,?,?,?,0,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,last_seen=excluded.last_seen,ip_hash=excluded.ip_hash,last_join=excluded.last_join")){
                ps.setString(1,p.getUniqueId().toString());ps.setString(2,p.getName());ps.setLong(3,now);ps.setLong(4,now);ps.setString(5,hash);ps.setLong(6,now);ps.executeUpdate();
            }
            try(PreparedStatement ps=c.prepareStatement("INSERT INTO username_history(player_uuid,name,first_seen,last_seen) VALUES(?,?,?,?) ON CONFLICT DO UPDATE SET last_seen=excluded.last_seen")){
                ps.setString(1,p.getUniqueId().toString());ps.setString(2,p.getName());ps.setLong(3,now);ps.setLong(4,now);ps.executeUpdate();
            }
            if(hash!=null){try(PreparedStatement ps=c.prepareStatement("INSERT INTO ip_history(player_uuid,ip_hash,raw_ip,first_seen,last_seen) VALUES(?,?,?,?,?) ON CONFLICT(player_uuid,ip_hash) DO UPDATE SET raw_ip=excluded.raw_ip,last_seen=excluded.last_seen")){
                ps.setString(1,p.getUniqueId().toString());ps.setString(2,hash);ps.setString(3,storeRawIps?raw:null);ps.setLong(4,now);ps.setLong(5,now);ps.executeUpdate();
            }}
        });
    }

    public void quit(Player p){Long start=sessions.remove(p.getUniqueId());long now=System.currentTimeMillis();long sec=start==null?0:Math.max(0,(now-start)/1000);db.runAsync(c->{try(PreparedStatement ps=c.prepareStatement("UPDATE players SET name=?,last_seen=?,playtime_seconds=playtime_seconds+? WHERE uuid=?")){ps.setString(1,p.getName());ps.setLong(2,now);ps.setLong(3,sec);ps.setString(4,p.getUniqueId().toString());ps.executeUpdate();}});}

    public long currentSessionSeconds(UUID id){Long start=sessions.get(id);return start==null?0:Math.max(0,(System.currentTimeMillis()-start)/1000);}
    public CompletableFuture<Optional<PlayerProfile>> byUuid(UUID uuid){return db.supplyAsync(c->{try(PreparedStatement ps=c.prepareStatement("SELECT * FROM players WHERE uuid=?")){ps.setString(1,uuid.toString());try(ResultSet r=ps.executeQuery()){return r.next()?Optional.of(read(r)):Optional.empty();}}});}
    public CompletableFuture<Optional<PlayerProfile>> byName(String name){return db.supplyAsync(c->{try(PreparedStatement ps=c.prepareStatement("SELECT * FROM players WHERE name=? COLLATE NOCASE LIMIT 1")){ps.setString(1,name);try(ResultSet r=ps.executeQuery()){return r.next()?Optional.of(read(r)):Optional.empty();}}});}

    public CompletableFuture<IdentitySnapshot> identity(UUID uuid, boolean canViewRawIp){
        return db.supplyAsync(c->{
            List<String> names=new ArrayList<>(); List<String> ips=new ArrayList<>(); Set<String> hashes=new HashSet<>(); int alts=0;
            try(PreparedStatement ps=c.prepareStatement("SELECT name FROM username_history WHERE player_uuid=? ORDER BY last_seen DESC LIMIT 20")){ps.setString(1,uuid.toString());try(ResultSet r=ps.executeQuery()){while(r.next())names.add(r.getString(1));}}
            try(PreparedStatement ps=c.prepareStatement("SELECT ip_hash,raw_ip,last_seen FROM ip_history WHERE player_uuid=? ORDER BY last_seen DESC LIMIT 20")){ps.setString(1,uuid.toString());try(ResultSet r=ps.executeQuery()){while(r.next()){String h=r.getString("ip_hash"),raw=r.getString("raw_ip");hashes.add(h);ips.add(canViewRawIp&&raw!=null?raw:shortHash(h));}}}
            if(!hashes.isEmpty()){
                String placeholders=String.join(",",Collections.nCopies(hashes.size(),"?"));
                try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(DISTINCT player_uuid) FROM ip_history WHERE ip_hash IN ("+placeholders+") AND player_uuid<>?")){int i=1;for(String h:hashes)ps.setString(i++,h);ps.setString(i,uuid.toString());try(ResultSet r=ps.executeQuery()){if(r.next())alts=r.getInt(1);}}
            }
            return new IdentitySnapshot(List.copyOf(names),List.copyOf(ips),alts);
        });
    }

    private PlayerProfile read(ResultSet r)throws SQLException{return new PlayerProfile(UUID.fromString(r.getString("uuid")),r.getString("name"),r.getLong("first_seen"),r.getLong("last_seen"),r.getLong("last_join"),r.getLong("playtime_seconds"),r.getString("ip_hash"));}
    private String rawIp(Player p){return p.getAddress()==null?null:p.getAddress().getAddress().getHostAddress();}
    private String hash(String raw){return (hashIps||storeRawIps)?HashUtil.sha256(salt+":"+raw):null;}
    private String shortHash(String h){if(h==null)return "Unknown";return h.length()>12?h.substring(0,12)+"…":h;}
}
