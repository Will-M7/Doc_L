package com.ejemplo.crudpersona.studio;

import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class MCApiCredentialStore {
    public record MCCredential(@com.fasterxml.jackson.annotation.JsonProperty("id") long MCid,@com.fasterxml.jackson.annotation.JsonProperty("name") String MCname,@com.fasterxml.jackson.annotation.JsonProperty("provider") String MCprovider,@com.fasterxml.jackson.annotation.JsonProperty("enabled") boolean MCenabled,@com.fasterxml.jackson.annotation.JsonProperty("createdAt") String MCcreatedAt,@com.fasterxml.jackson.annotation.JsonProperty("status") String MCstatus,
                             @com.fasterxml.jackson.annotation.JsonProperty("invalidMonth") String MCinvalidMonth,@com.fasterxml.jackson.annotation.JsonProperty("invalidStreak") int MCinvalidStreak,@com.fasterxml.jackson.annotation.JsonProperty("retryAfter") long MCretryAfter,@com.fasterxml.jackson.annotation.JsonProperty("lastUsed") String MClastUsed,
                             @com.fasterxml.jackson.annotation.JsonProperty("requests") int MCrequests,@com.fasterxml.jackson.annotation.JsonProperty("successes") int MCsuccesses,@com.fasterxml.jackson.annotation.JsonProperty("failures") int MCfailures){
        @Override public String toString(){return MCname+" · "+MCprovider+" · "+(MCenabled?MCstatus:"deshabilitada")+" · "+MCrequests+" consultas del mes";}
    }
    private final Path MCdirectory;
    private final String MCurl;
    private final Clock MCclock;
    public MCApiCredentialStore(Path MCdirectory)throws Exception{this(MCdirectory,Clock.systemDefaultZone());}
    MCApiCredentialStore(Path MCdirectory,Clock MCclock)throws Exception{
        this.MCdirectory=MCdirectory;this.MCclock=MCclock;MCurl="jdbc:sqlite:"+MCdirectory.resolve("studio.db");
        try(Connection MCc=MCopen();Statement MCs=MCc.createStatement()){
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,provider TEXT NOT NULL,enabled INTEGER NOT NULL DEFAULT 1,created_at TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'Disponible',invalid_month TEXT,invalid_streak INTEGER NOT NULL DEFAULT 0,retry_after INTEGER NOT NULL DEFAULT 0,last_used TEXT)");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential_usage(credential_id INTEGER NOT NULL,month TEXT NOT NULL,requests INTEGER NOT NULL DEFAULT 0,successes INTEGER NOT NULL DEFAULT 0,failures INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(credential_id,month))");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential_failure(credential_id INTEGER NOT NULL,occurred_at TEXT NOT NULL,month TEXT NOT NULL,category TEXT NOT NULL)");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential_retired(name TEXT NOT NULL,provider TEXT NOT NULL,retired_at TEXT NOT NULL,invalid_month TEXT,invalid_streak INTEGER NOT NULL,failure_count INTEGER NOT NULL)");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        }
        MCLocalSecret MClegacy=new MCLocalSecret(MCdirectory);
        if(MClegacy.MCconfigured()&&!MClegacyImported()){
            try{if(MCcredentials().stream().noneMatch(MCc->MCc.MCname().equals("Factiliza anterior")))MCadd("Factiliza anterior","Factiliza",MClegacy.MCload());MCmarkLegacyImported();}catch(Exception MCignored){}
        }
    }
    private boolean MClegacyImported()throws SQLException{try(Connection MCc=MCopen();Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT 1 FROM api_credential_meta WHERE key='factiliza_legacy_imported'")){return MCr.next();}}
    private void MCmarkLegacyImported()throws SQLException{try(Connection MCc=MCopen();Statement MCs=MCc.createStatement()){MCs.executeUpdate("INSERT OR REPLACE INTO api_credential_meta(key,value) VALUES('factiliza_legacy_imported','1')");}}
    private Connection MCopen()throws SQLException{return DriverManager.getConnection(MCurl);}
    private MCLocalSecret MCvault(long MCid){return new MCLocalSecret(MCdirectory,"credential-"+MCid+".secret");}
    public List<MCCredential> MCcredentials()throws SQLException{
        List<MCCredential> MCout=new ArrayList<>();String MCmonth=YearMonth.now(MCclock).toString();
        String MCsql="SELECT c.*,COALESCE(u.requests,0) requests,COALESCE(u.successes,0) successes,COALESCE(u.failures,0) failures FROM api_credential c LEFT JOIN api_credential_usage u ON u.credential_id=c.id AND u.month=? ORDER BY CASE c.provider WHEN 'Factiliza' THEN 0 ELSE 1 END,c.id";
        try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement(MCsql)){MCs.setString(1,MCmonth);try(ResultSet MCr=MCs.executeQuery()){while(MCr.next())MCout.add(new MCCredential(MCr.getLong("id"),MCr.getString("name"),MCr.getString("provider"),MCr.getInt("enabled")==1,MCr.getString("created_at"),MCr.getString("status"),MCr.getString("invalid_month"),MCr.getInt("invalid_streak"),MCr.getLong("retry_after"),MCr.getString("last_used"),MCr.getInt("requests"),MCr.getInt("successes"),MCr.getInt("failures")));}}return MCout;
    }
    public MCCredential MCadd(String MCname,String MCprovider,String MCtoken)throws Exception{
        MCvalidate(MCname,MCprovider);if(MCtoken==null||MCtoken.isBlank())throw new IllegalArgumentException("Escriba una clave API.");long MCid;
        try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("INSERT INTO api_credential(name,provider,created_at) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)){MCs.setString(1,MCname.trim());MCs.setString(2,MCprovider);MCs.setString(3,LocalDateTime.now(MCclock).toString());MCs.executeUpdate();try(ResultSet MCr=MCs.getGeneratedKeys()){if(!MCr.next())throw new SQLException("No se creó la credencial.");MCid=MCr.getLong(1);}}
        try{MCvault(MCid).MCsave(MCtoken);}catch(Exception MCe){try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM api_credential WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}throw MCe;}
        return MCcredentials().stream().filter(MCx->MCx.MCid()==MCid).findFirst().orElseThrow();
    }
    private static void MCvalidate(String MCname,String MCprovider){if(MCname==null||MCname.isBlank()||!List.of("Factiliza","APIsPERU").contains(MCprovider))throw new IllegalArgumentException("Nombre o proveedor inválido.");}
    public void MCrename(long MCid,String MCname)throws SQLException{if(MCname==null||MCname.isBlank())throw new IllegalArgumentException("Escriba un nombre.");try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("UPDATE api_credential SET name=? WHERE id=?")){MCs.setString(1,MCname.trim());MCs.setLong(2,MCid);MCs.executeUpdate();}}
    public void MCenabled(long MCid,boolean MCenabled)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("UPDATE api_credential SET enabled=? WHERE id=?")){MCs.setInt(1,MCenabled?1:0);MCs.setLong(2,MCid);MCs.executeUpdate();}}
    public void MCreplace(long MCid,String MCtoken)throws Exception{MCvault(MCid).MCsave(MCtoken);try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("UPDATE api_credential SET status='Disponible',invalid_month=NULL,invalid_streak=0,retry_after=0 WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}}
    public String MCtoken(long MCid)throws Exception{return MCvault(MCid).MCload();}
    public void MCdelete(long MCid)throws Exception{MCCredential MCcredential=MCcredentials().stream().filter(MCx->MCx.MCid()==MCid).findFirst().orElseThrow();try(Connection MCc=MCopen()){MCc.setAutoCommit(false);try(PreparedStatement MCs=MCc.prepareStatement("INSERT INTO api_credential_retired(name,provider,retired_at,invalid_month,invalid_streak,failure_count) VALUES(?,?,?,?,?,(SELECT COUNT(*) FROM api_credential_failure WHERE credential_id=?))")){MCs.setString(1,MCcredential.MCname());MCs.setString(2,MCcredential.MCprovider());MCs.setString(3,LocalDateTime.now(MCclock).toString());MCs.setString(4,MCcredential.MCinvalidMonth());MCs.setInt(5,MCcredential.MCinvalidStreak());MCs.setLong(6,MCid);MCs.executeUpdate();}try(PreparedStatement MCs=MCc.prepareStatement("DELETE FROM api_credential_usage WHERE credential_id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}try(PreparedStatement MCs=MCc.prepareStatement("DELETE FROM api_credential_failure WHERE credential_id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}try(PreparedStatement MCs=MCc.prepareStatement("DELETE FROM api_credential WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}MCc.commit();}MCvault(MCid).MCdelete();}
    public void MCrecord(long MCid,MCLookupRouter.MCStatus MCstatus,long MCretryAfterSeconds)throws SQLException{
        String MCmonth=YearMonth.now(MCclock).toString(),MCnow=LocalDateTime.now(MCclock).toString();boolean MCok=MCstatus==MCLookupRouter.MCStatus.SUCCESS||MCstatus==MCLookupRouter.MCStatus.PARTIAL;boolean MCdispatched=MCstatus!=MCLookupRouter.MCStatus.NO_INTERNET;
        try(Connection MCc=MCopen()){MCc.setAutoCommit(false);
            try(PreparedStatement MCs=MCc.prepareStatement("INSERT INTO api_credential_usage(credential_id,month,requests,successes,failures) VALUES(?,?,?,?,?) ON CONFLICT(credential_id,month) DO UPDATE SET requests=requests+excluded.requests,successes=successes+excluded.successes,failures=failures+excluded.failures")){MCs.setLong(1,MCid);MCs.setString(2,MCmonth);MCs.setInt(3,MCdispatched?1:0);MCs.setInt(4,MCok?1:0);MCs.setInt(5,MCok?0:1);MCs.executeUpdate();}
            if(!MCok)try(PreparedStatement MCs=MCc.prepareStatement("INSERT INTO api_credential_failure(credential_id,occurred_at,month,category) VALUES(?,?,?,?)")){MCs.setLong(1,MCid);MCs.setString(2,MCnow);MCs.setString(3,MCmonth);MCs.setString(4,MCstatus.name());MCs.executeUpdate();}
            String MCprevious=null;int MCstreak=0;try(PreparedStatement MCs=MCc.prepareStatement("SELECT invalid_month,invalid_streak FROM api_credential WHERE id=?")){MCs.setLong(1,MCid);try(ResultSet MCr=MCs.executeQuery()){if(MCr.next()){MCprevious=MCr.getString(1);MCstreak=MCr.getInt(2);}}}
            if(MCstatus==MCLookupRouter.MCStatus.SUCCESS||MCstatus==MCLookupRouter.MCStatus.PARTIAL){MCprevious=null;MCstreak=0;}
            else if(MCstatus==MCLookupRouter.MCStatus.INVALID&&(!MCmonth.equals(MCprevious))){MCstreak=YearMonth.now(MCclock).minusMonths(1).toString().equals(MCprevious)?MCstreak+1:1;MCprevious=MCmonth;}
            long MCretry=0;if(MCstatus==MCLookupRouter.MCStatus.RATE_LIMIT)MCretry=Instant.now(MCclock).plusSeconds(Math.max(3600,MCretryAfterSeconds)).getEpochSecond();else if(MCstatus==MCLookupRouter.MCStatus.TEMPORARY||MCstatus==MCLookupRouter.MCStatus.TIMEOUT)MCretry=Instant.now(MCclock).plusSeconds(60).getEpochSecond();
            String MCstate=switch(MCstatus){case SUCCESS->"Disponible";case PARTIAL->"Respuesta parcial";case INVALID->MCstreak>=2?"Invalidez confirmada dos meses":"Credencial inválida";case RATE_LIMIT->"Límite informado";case TIMEOUT->"Tiempo agotado";case NO_INTERNET->"Sin conexión";case TEMPORARY->"Servicio temporalmente no disponible";case PERMISSION->"Permisos insuficientes";case NOT_FOUND->"Documento no encontrado";case UNSUPPORTED->"Servicio no habilitado";case BAD_RESPONSE->"Respuesta incorrecta";case NO_CREDENTIAL->"Sin credencial";};
            try(PreparedStatement MCs=MCc.prepareStatement("UPDATE api_credential SET status=?,invalid_month=?,invalid_streak=?,retry_after=?,last_used=? WHERE id=?")){MCs.setString(1,MCstate);MCs.setString(2,MCprevious);MCs.setInt(3,MCstreak);MCs.setLong(4,MCretry);MCs.setString(5,MCnow);MCs.setLong(6,MCid);MCs.executeUpdate();}MCc.commit();
        }
    }
}
