package com.staffops.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.concurrent.*;

public final class Database implements AutoCloseable {
    private final HikariDataSource ds;
    private final ExecutorService executor;

    public Database(JavaPlugin plugin){
        File db=new File(plugin.getDataFolder(),"staffops.db");
        HikariConfig hc=new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:"+db.getAbsolutePath());
        hc.setMaximumPoolSize(Math.max(2,plugin.getConfig().getInt("settings.database.pool-size",4)));
        hc.setMinimumIdle(1);
        hc.setConnectionTimeout(5000);
        hc.setPoolName("StaffOps-SQLite");
        hc.setConnectionTestQuery("SELECT 1");
        ds=new HikariDataSource(hc);
        executor=Executors.newFixedThreadPool(Math.max(2,plugin.getConfig().getInt("settings.database.pool-size",4)),r->{Thread t=new Thread(r,"StaffOps-DB");t.setDaemon(true);return t;});
        migrate();
    }

    private void migrate(){
        try(Connection c=ds.getConnection(); Statement s=c.createStatement()){
            s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA busy_timeout=5000");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS players(uuid TEXT PRIMARY KEY,name TEXT NOT NULL,first_seen INTEGER NOT NULL,last_seen INTEGER NOT NULL,playtime_seconds INTEGER NOT NULL DEFAULT 0,ip_hash TEXT,last_join INTEGER)");
            ensureColumn(c,"players","last_join","INTEGER");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_players_name ON players(name COLLATE NOCASE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS username_history(id INTEGER PRIMARY KEY AUTOINCREMENT,player_uuid TEXT NOT NULL,name TEXT NOT NULL,first_seen INTEGER NOT NULL,last_seen INTEGER NOT NULL,UNIQUE(player_uuid,name COLLATE NOCASE))");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_username_history_player ON username_history(player_uuid,last_seen DESC)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS ip_history(id INTEGER PRIMARY KEY AUTOINCREMENT,player_uuid TEXT NOT NULL,ip_hash TEXT NOT NULL,raw_ip TEXT,first_seen INTEGER NOT NULL,last_seen INTEGER NOT NULL,UNIQUE(player_uuid,ip_hash))");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ip_history_hash ON ip_history(ip_hash,last_seen DESC)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ip_history_player ON ip_history(player_uuid,last_seen DESC)");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS punishments(id INTEGER PRIMARY KEY AUTOINCREMENT,target_uuid TEXT NOT NULL,target_name TEXT NOT NULL,staff_uuid TEXT,staff_name TEXT NOT NULL,type TEXT NOT NULL,reason TEXT NOT NULL,created_at INTEGER NOT NULL,expires_at INTEGER,active INTEGER NOT NULL DEFAULT 1,ladder TEXT)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_punishments_target ON punishments(target_uuid,created_at DESC)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_punishments_active ON punishments(target_uuid,type,active)");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS reports(id INTEGER PRIMARY KEY AUTOINCREMENT,reporter_uuid TEXT NOT NULL,reporter_name TEXT NOT NULL,target_uuid TEXT NOT NULL,target_name TEXT NOT NULL,reason TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'OPEN',claimed_by TEXT,claimed_name TEXT,created_at INTEGER NOT NULL,resolved_at INTEGER,resolution TEXT,category TEXT NOT NULL DEFAULT 'OTHER',priority TEXT NOT NULL DEFAULT 'NORMAL')");
            ensureColumn(c,"reports","category","TEXT NOT NULL DEFAULT 'OTHER'");
            ensureColumn(c,"reports","priority","TEXT NOT NULL DEFAULT 'NORMAL'");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status,created_at DESC)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_reports_target_category ON reports(target_uuid,category,status,created_at DESC)");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY AUTOINCREMENT,target_uuid TEXT NOT NULL,target_name TEXT NOT NULL,staff_uuid TEXT NOT NULL,staff_name TEXT NOT NULL,note TEXT NOT NULL,created_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notes_target ON notes(target_uuid,created_at DESC)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY AUTOINCREMENT,actor_uuid TEXT,actor_name TEXT NOT NULL,action TEXT NOT NULL,target_uuid TEXT,target_name TEXT,details TEXT,created_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_created ON audit(created_at DESC)");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS moderation_cases(id INTEGER PRIMARY KEY AUTOINCREMENT,target_uuid TEXT NOT NULL,target_name TEXT NOT NULL,opened_by TEXT,opened_by_name TEXT NOT NULL,reason TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'OPEN',priority TEXT NOT NULL DEFAULT 'NORMAL',created_at INTEGER NOT NULL,closed_at INTEGER,resolution TEXT)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_cases_status ON moderation_cases(status,priority,created_at DESC)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_cases_target ON moderation_cases(target_uuid,created_at DESC)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS case_events(id INTEGER PRIMARY KEY AUTOINCREMENT,case_id INTEGER NOT NULL,actor_uuid TEXT,actor_name TEXT NOT NULL,type TEXT NOT NULL,details TEXT,created_at INTEGER NOT NULL,FOREIGN KEY(case_id) REFERENCES moderation_cases(id) ON DELETE CASCADE)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_case_events_case ON case_events(case_id,created_at DESC)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS evidence(id INTEGER PRIMARY KEY AUTOINCREMENT,case_id INTEGER,target_uuid TEXT NOT NULL,target_name TEXT NOT NULL,created_by TEXT,created_by_name TEXT NOT NULL,reason TEXT NOT NULL,created_at INTEGER NOT NULL,FOREIGN KEY(case_id) REFERENCES moderation_cases(id) ON DELETE SET NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_evidence_target ON evidence(target_uuid,created_at DESC)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS evidence_events(id INTEGER PRIMARY KEY AUTOINCREMENT,evidence_id INTEGER NOT NULL,event_type TEXT NOT NULL,details TEXT,created_at INTEGER NOT NULL,FOREIGN KEY(evidence_id) REFERENCES evidence(id) ON DELETE CASCADE)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_evidence_events ON evidence_events(evidence_id,created_at ASC)");
        }catch(SQLException e){throw new IllegalStateException("Could not initialize StaffOps database",e);}
    }

    private void ensureColumn(Connection c,String table,String column,String definition)throws SQLException{
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA table_info("+table+")")){while(r.next())if(column.equalsIgnoreCase(r.getString("name")))return;}
        try(Statement s=c.createStatement()){s.executeUpdate("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);}
    }

    public CompletableFuture<Void> runAsync(SqlConsumer work){return CompletableFuture.runAsync(()->{try(Connection c=ds.getConnection()){work.accept(c);}catch(SQLException e){throw new CompletionException(e);}},executor);}
    public <T> CompletableFuture<T> supplyAsync(SqlFunction<T> work){return CompletableFuture.supplyAsync(()->{try(Connection c=ds.getConnection()){return work.apply(c);}catch(SQLException e){throw new CompletionException(e);}},executor);}
    public CompletableFuture<Long> latencyMillis(){long start=System.nanoTime();return supplyAsync(c->{try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT 1")){r.next();return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);}});}
    public Connection connection()throws SQLException{return ds.getConnection();}
    public int activeConnections(){return ds.getHikariPoolMXBean()==null?0:ds.getHikariPoolMXBean().getActiveConnections();}
    public int totalConnections(){return ds.getHikariPoolMXBean()==null?0:ds.getHikariPoolMXBean().getTotalConnections();}
    @Override public void close(){executor.shutdown();try{executor.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}ds.close();}
    @FunctionalInterface public interface SqlConsumer{void accept(Connection c)throws SQLException;}
    @FunctionalInterface public interface SqlFunction<T>{T apply(Connection c)throws SQLException;}
}
