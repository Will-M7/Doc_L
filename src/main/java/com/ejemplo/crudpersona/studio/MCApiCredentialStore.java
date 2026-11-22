package com.ejemplo.crudpersona.studio;

import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;

/** @author MC */
public final class MCApiCredentialStore {
    /** @author MC */
    public record MCCredential(@com.fasterxml.jackson.annotation.JsonProperty("id") long mcId,@com.fasterxml.jackson.annotation.JsonProperty("name") String mcName,@com.fasterxml.jackson.annotation.JsonProperty("provider") String mcProvider,@com.fasterxml.jackson.annotation.JsonProperty("enabled") boolean mcEnabled,@com.fasterxml.jackson.annotation.JsonProperty("createdAt") String mcCreatedAt,@com.fasterxml.jackson.annotation.JsonProperty("status") String mcStatus,
                             @com.fasterxml.jackson.annotation.JsonProperty("invalidMonth") String mcInvalidMonth,@com.fasterxml.jackson.annotation.JsonProperty("invalidStreak") int mcInvalidStreak,@com.fasterxml.jackson.annotation.JsonProperty("retryAfter") long mcRetryAfter,@com.fasterxml.jackson.annotation.JsonProperty("lastUsed") String mcLastUsed,
                             @com.fasterxml.jackson.annotation.JsonProperty("requests") int mcRequests,@com.fasterxml.jackson.annotation.JsonProperty("successes") int mcSuccesses,@com.fasterxml.jackson.annotation.JsonProperty("failures") int mcFailures){
        @Override public String toString(){return mcName+" · "+mcProvider+" · "+(mcEnabled?mcStatus:"deshabilitada")+" · "+mcRequests+" consultas del mes";}
    }
    private final Path mcDirectory;
    private final String mcUrl;
    private final Clock mcClock;
    public MCApiCredentialStore(Path mcDirectory)throws Exception{this(mcDirectory,Clock.systemDefaultZone());}
    MCApiCredentialStore(Path mcDirectory,Clock mcClock)throws Exception{
        this.mcDirectory=mcDirectory;this.mcClock=mcClock;mcUrl="jdbc:sqlite:"+mcDirectory.resolve("studio.db");
        try(Connection mcC=mcOpen();Statement mcS=mcC.createStatement()){
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,provider TEXT NOT NULL,enabled INTEGER NOT NULL DEFAULT 1,created_at TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'Disponible',invalid_month TEXT,invalid_streak INTEGER NOT NULL DEFAULT 0,retry_after INTEGER NOT NULL DEFAULT 0,last_used TEXT)");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential_usage(credential_id INTEGER NOT NULL,month TEXT NOT NULL,requests INTEGER NOT NULL DEFAULT 0,successes INTEGER NOT NULL DEFAULT 0,failures INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(credential_id,month))");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential_failure(credential_id INTEGER NOT NULL,occurred_at TEXT NOT NULL,month TEXT NOT NULL,category TEXT NOT NULL)");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential_retired(name TEXT NOT NULL,provider TEXT NOT NULL,retired_at TEXT NOT NULL,invalid_month TEXT,invalid_streak INTEGER NOT NULL,failure_count INTEGER NOT NULL)");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        }
        MCLocalSecret mcLegacy=new MCLocalSecret(mcDirectory);
        if(mcLegacy.mcConfigured()&&!mcLegacyImported()){
            try{if(mcCredentials().stream().noneMatch(mcC->mcC.mcName().equals("Factiliza anterior")))mcAdd("Factiliza anterior","Factiliza",mcLegacy.mcLoad());mcMarkLegacyImported();}catch(Exception mcIgnored){}
        }
    }
    private boolean mcLegacyImported()throws SQLException{try(Connection mcC=mcOpen();Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT 1 FROM api_credential_meta WHERE key='factiliza_legacy_imported'")){return mcR.next();}}
    private void mcMarkLegacyImported()throws SQLException{try(Connection mcC=mcOpen();Statement mcS=mcC.createStatement()){mcS.executeUpdate("INSERT OR REPLACE INTO api_credential_meta(key,value) VALUES('factiliza_legacy_imported','1')");}}
    private Connection mcOpen()throws SQLException{return DriverManager.getConnection(mcUrl);}
    private MCLocalSecret mcVault(long mcId){return new MCLocalSecret(mcDirectory,"credential-"+mcId+".secret");}
    public List<MCCredential> mcCredentials()throws SQLException{
        List<MCCredential> mcOut=new ArrayList<>();String mcMonth=YearMonth.now(mcClock).toString();
        String mcSql="SELECT c.*,COALESCE(u.requests,0) requests,COALESCE(u.successes,0) successes,COALESCE(u.failures,0) failures FROM api_credential c LEFT JOIN api_credential_usage u ON u.credential_id=c.id AND u.month=? ORDER BY CASE c.provider WHEN 'Factiliza' THEN 0 ELSE 1 END,c.id";
        try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement(mcSql)){mcS.setString(1,mcMonth);try(ResultSet mcR=mcS.executeQuery()){while(mcR.next())mcOut.add(new MCCredential(mcR.getLong("id"),mcR.getString("name"),mcR.getString("provider"),mcR.getInt("enabled")==1,mcR.getString("created_at"),mcR.getString("status"),mcR.getString("invalid_month"),mcR.getInt("invalid_streak"),mcR.getLong("retry_after"),mcR.getString("last_used"),mcR.getInt("requests"),mcR.getInt("successes"),mcR.getInt("failures")));}}return mcOut;
    }
    public MCCredential mcAdd(String mcName,String mcProvider,String mcToken)throws Exception{
        mcValidate(mcName,mcProvider);if(mcToken==null||mcToken.isBlank())throw new IllegalArgumentException("Escriba una clave API.");long mcId;
        try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("INSERT INTO api_credential(name,provider,created_at) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)){mcS.setString(1,mcName.trim());mcS.setString(2,mcProvider);mcS.setString(3,LocalDateTime.now(mcClock).toString());mcS.executeUpdate();try(ResultSet mcR=mcS.getGeneratedKeys()){if(!mcR.next())throw new SQLException("No se creó la credencial.");mcId=mcR.getLong(1);}}
        try{mcVault(mcId).mcSave(mcToken);}catch(Exception mcE){try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM api_credential WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}throw mcE;}
        return mcCredentials().stream().filter(mcX->mcX.mcId()==mcId).findFirst().orElseThrow();
    }
    private static void mcValidate(String mcName,String mcProvider){if(mcName==null||mcName.isBlank()||!List.of("Factiliza","APIsPERU").contains(mcProvider))throw new IllegalArgumentException("Nombre o proveedor inválido.");}
    public void mcRename(long mcId,String mcName)throws SQLException{if(mcName==null||mcName.isBlank())throw new IllegalArgumentException("Escriba un nombre.");try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("UPDATE api_credential SET name=? WHERE id=?")){mcS.setString(1,mcName.trim());mcS.setLong(2,mcId);mcS.executeUpdate();}}
    public void mcEnabled(long mcId,boolean mcEnabled)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("UPDATE api_credential SET enabled=? WHERE id=?")){mcS.setInt(1,mcEnabled?1:0);mcS.setLong(2,mcId);mcS.executeUpdate();}}
    public void mcReplace(long mcId,String mcToken)throws Exception{mcVault(mcId).mcSave(mcToken);try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("UPDATE api_credential SET status='Disponible',invalid_month=NULL,invalid_streak=0,retry_after=0 WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}}
    public String mcToken(long mcId)throws Exception{return mcVault(mcId).mcLoad();}
    public void mcDelete(long mcId)throws Exception{MCCredential mcCredential=mcCredentials().stream().filter(mcX->mcX.mcId()==mcId).findFirst().orElseThrow();try(Connection mcC=mcOpen()){mcC.setAutoCommit(false);try(PreparedStatement mcS=mcC.prepareStatement("INSERT INTO api_credential_retired(name,provider,retired_at,invalid_month,invalid_streak,failure_count) VALUES(?,?,?,?,?,(SELECT COUNT(*) FROM api_credential_failure WHERE credential_id=?))")){mcS.setString(1,mcCredential.mcName());mcS.setString(2,mcCredential.mcProvider());mcS.setString(3,LocalDateTime.now(mcClock).toString());mcS.setString(4,mcCredential.mcInvalidMonth());mcS.setInt(5,mcCredential.mcInvalidStreak());mcS.setLong(6,mcId);mcS.executeUpdate();}try(PreparedStatement mcS=mcC.prepareStatement("DELETE FROM api_credential_usage WHERE credential_id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}try(PreparedStatement mcS=mcC.prepareStatement("DELETE FROM api_credential_failure WHERE credential_id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}try(PreparedStatement mcS=mcC.prepareStatement("DELETE FROM api_credential WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}mcC.commit();}mcVault(mcId).mcDelete();}
    public void mcRecord(long mcId,MCLookupRouter.MCStatus mcStatus,long mcRetryAfterSeconds)throws SQLException{
        String mcMonth=YearMonth.now(mcClock).toString(),mcNow=LocalDateTime.now(mcClock).toString();boolean mcOk=mcStatus==MCLookupRouter.MCStatus.SUCCESS||mcStatus==MCLookupRouter.MCStatus.PARTIAL;boolean mcDispatched=mcStatus!=MCLookupRouter.MCStatus.NO_INTERNET;
        try(Connection mcC=mcOpen()){mcC.setAutoCommit(false);
            try(PreparedStatement mcS=mcC.prepareStatement("INSERT INTO api_credential_usage(credential_id,month,requests,successes,failures) VALUES(?,?,?,?,?) ON CONFLICT(credential_id,month) DO UPDATE SET requests=requests+excluded.requests,successes=successes+excluded.successes,failures=failures+excluded.failures")){mcS.setLong(1,mcId);mcS.setString(2,mcMonth);mcS.setInt(3,mcDispatched?1:0);mcS.setInt(4,mcOk?1:0);mcS.setInt(5,mcOk?0:1);mcS.executeUpdate();}
            if(!mcOk)try(PreparedStatement mcS=mcC.prepareStatement("INSERT INTO api_credential_failure(credential_id,occurred_at,month,category) VALUES(?,?,?,?)")){mcS.setLong(1,mcId);mcS.setString(2,mcNow);mcS.setString(3,mcMonth);mcS.setString(4,mcStatus.name());mcS.executeUpdate();}
            String mcPrevious=null;int mcStreak=0;try(PreparedStatement mcS=mcC.prepareStatement("SELECT invalid_month,invalid_streak FROM api_credential WHERE id=?")){mcS.setLong(1,mcId);try(ResultSet mcR=mcS.executeQuery()){if(mcR.next()){mcPrevious=mcR.getString(1);mcStreak=mcR.getInt(2);}}}
            if(mcStatus==MCLookupRouter.MCStatus.SUCCESS||mcStatus==MCLookupRouter.MCStatus.PARTIAL){mcPrevious=null;mcStreak=0;}
            else if(mcStatus==MCLookupRouter.MCStatus.INVALID&&(!mcMonth.equals(mcPrevious))){mcStreak=YearMonth.now(mcClock).minusMonths(1).toString().equals(mcPrevious)?mcStreak+1:1;mcPrevious=mcMonth;}
            long mcRetry=0;if(mcStatus==MCLookupRouter.MCStatus.RATE_LIMIT)mcRetry=Instant.now(mcClock).plusSeconds(Math.max(3600,mcRetryAfterSeconds)).getEpochSecond();else if(mcStatus==MCLookupRouter.MCStatus.TEMPORARY||mcStatus==MCLookupRouter.MCStatus.TIMEOUT)mcRetry=Instant.now(mcClock).plusSeconds(60).getEpochSecond();
            String mcState=switch(mcStatus){case SUCCESS->"Disponible";case PARTIAL->"Respuesta parcial";case INVALID->mcStreak>=2?"Invalidez confirmada dos meses":"Credencial inválida";case RATE_LIMIT->"Límite informado";case TIMEOUT->"Tiempo agotado";case NO_INTERNET->"Sin conexión";case TEMPORARY->"Servicio temporalmente no disponible";case PERMISSION->"Permisos insuficientes";case NOT_FOUND->"Documento no encontrado";case UNSUPPORTED->"Servicio no habilitado";case BAD_RESPONSE->"Respuesta incorrecta";case NO_CREDENTIAL->"Sin credencial";};
            try(PreparedStatement mcS=mcC.prepareStatement("UPDATE api_credential SET status=?,invalid_month=?,invalid_streak=?,retry_after=?,last_used=? WHERE id=?")){mcS.setString(1,mcState);mcS.setString(2,mcPrevious);mcS.setInt(3,mcStreak);mcS.setLong(4,mcRetry);mcS.setString(5,mcNow);mcS.setLong(6,mcId);mcS.executeUpdate();}mcC.commit();
        }
    }
}
