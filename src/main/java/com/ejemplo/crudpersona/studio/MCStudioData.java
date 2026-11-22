package com.ejemplo.crudpersona.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

public final class MCStudioData {
    private static final ObjectMapper MCJSON=new ObjectMapper();
    public record MCContact(@com.fasterxml.jackson.annotation.JsonProperty("id") long MCid,@com.fasterxml.jackson.annotation.JsonProperty("kind") String MCkind,@com.fasterxml.jackson.annotation.JsonProperty("given") String MCgiven,@com.fasterxml.jackson.annotation.JsonProperty("family") String MCfamily,@com.fasterxml.jackson.annotation.JsonProperty("documentType") String MCdocumentType,@com.fasterxml.jackson.annotation.JsonProperty("number") String MCnumber,
                          @com.fasterxml.jackson.annotation.JsonProperty("address") String MCaddress,@com.fasterxml.jackson.annotation.JsonProperty("representatives") String MCrepresentatives,@com.fasterxml.jackson.annotation.JsonProperty("extrasJson") String MCextrasJson) {
        @Override public String toString(){return ("Empresa".equals(MCkind)?MCgiven:MCgiven+" "+MCfamily)+" · "+MCnumber;}
    }
    public record MCHistory(@com.fasterxml.jackson.annotation.JsonProperty("id") long MCid,@com.fasterxml.jackson.annotation.JsonProperty("title") String MCtitle,@com.fasterxml.jackson.annotation.JsonProperty("templateId") Long MCtemplateId,@com.fasterxml.jackson.annotation.JsonProperty("templateVersion") int MCtemplateVersion,@com.fasterxml.jackson.annotation.JsonProperty("generatedAt") String MCgeneratedAt,
                          @com.fasterxml.jackson.annotation.JsonProperty("valuesJson") String MCvaluesJson,@com.fasterxml.jackson.annotation.JsonProperty("docxPath") String MCdocxPath,@com.fasterxml.jackson.annotation.JsonProperty("pdfPath") String MCpdfPath,@com.fasterxml.jackson.annotation.JsonProperty("docx") byte[] MCdocx,@com.fasterxml.jackson.annotation.JsonProperty("pdf") byte[] MCpdf){
        @Override public String toString(){return MCgeneratedAt+" · "+MCtitle;}
    }
    public record MCClientDocument(@com.fasterxml.jackson.annotation.JsonProperty("id") long MCid,@com.fasterxml.jackson.annotation.JsonProperty("contactId") long MCcontactId,@com.fasterxml.jackson.annotation.JsonProperty("title") String MCtitle,@com.fasterxml.jackson.annotation.JsonProperty("fileName") String MCfileName,@com.fasterxml.jackson.annotation.JsonProperty("extension") String MCextension,
                                 @com.fasterxml.jackson.annotation.JsonProperty("sourceType") String MCsourceType,@com.fasterxml.jackson.annotation.JsonProperty("contentBytes") byte[] MCcontentBytes,@com.fasterxml.jackson.annotation.JsonProperty("updatedAt") String MCupdatedAt) {}
    public record MCMapping(@com.fasterxml.jackson.annotation.JsonProperty("id") long MCid,@com.fasterxml.jackson.annotation.JsonProperty("templateId") long MCtemplateId,@com.fasterxml.jackson.annotation.JsonProperty("sourceKey") String MCsourceKey,@com.fasterxml.jackson.annotation.JsonProperty("apiField") String MCapiField,@com.fasterxml.jackson.annotation.JsonProperty("targetKey") String MCtargetKey){
        @Override public String toString(){return MCsourceKey+" → "+MCapiField+" → "+MCtargetKey;}
    }
    public record MCApiBinding(@com.fasterxml.jackson.annotation.JsonProperty("definitionId") long MCdefinitionId,@com.fasterxml.jackson.annotation.JsonProperty("service") String MCservice,@com.fasterxml.jackson.annotation.JsonProperty("path") String MCpath){}
    public record MCApiContext(@com.fasterxml.jackson.annotation.JsonProperty("templateId") long MCtemplateId,@com.fasterxml.jackson.annotation.JsonProperty("variableKey") String MCvariableKey,@com.fasterxml.jackson.annotation.JsonProperty("groupKey") String MCgroupKey,@com.fasterxml.jackson.annotation.JsonProperty("service") String MCservice,@com.fasterxml.jackson.annotation.JsonProperty("source") boolean MCsource){
        @Override public String toString(){return MCgroupKey+" · "+MCservice+" · "+MCvariableKey+(MCsource?" (número a consultar)":"");}
    }
    public record MCContactBinding(@com.fasterxml.jackson.annotation.JsonProperty("id") long MCid,@com.fasterxml.jackson.annotation.JsonProperty("templateId") long MCtemplateId,@com.fasterxml.jackson.annotation.JsonProperty("groupKey") String MCgroupKey,@com.fasterxml.jackson.annotation.JsonProperty("kind") String MCkind,@com.fasterxml.jackson.annotation.JsonProperty("field") String MCfield,@com.fasterxml.jackson.annotation.JsonProperty("variableKey") String MCvariableKey){
        @Override public String toString(){return MCgroupKey+" · "+MCkind+" · "+MCfield+" → {{"+MCvariableKey+"}}";}
    }
    private final Path MCdb;
    private static final String MCHISTORY_COLUMNS="id,title,template_id,template_version,generated_at,values_json,docx_path,pdf_path,docx,pdf";
    private static String MChistorySchema(String MCtable){return "CREATE TABLE "+MCtable+"(id INTEGER PRIMARY KEY,title TEXT NOT NULL,template_id INTEGER REFERENCES template(id) ON DELETE SET NULL,template_version INTEGER NOT NULL,generated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,values_json TEXT NOT NULL,docx_path TEXT,pdf_path TEXT,docx BLOB NOT NULL,pdf BLOB)";}
    public MCStudioData(Path MCdirectory)throws SQLException{
        MCdb=MCdirectory.resolve("studio.db");
        try(Connection MCc=MCopen();Statement MCs=MCc.createStatement()){
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS contact(id INTEGER PRIMARY KEY,kind TEXT NOT NULL,given TEXT NOT NULL,family TEXT NOT NULL DEFAULT '',document_type TEXT NOT NULL DEFAULT '',number TEXT NOT NULL DEFAULT '',address TEXT NOT NULL DEFAULT '',representatives TEXT NOT NULL DEFAULT '',extras_json TEXT NOT NULL DEFAULT '{}')");
            MCs.executeUpdate("CREATE INDEX IF NOT EXISTS ix_contact_number ON contact(number)");
            MCs.executeUpdate("CREATE INDEX IF NOT EXISTS ix_contact_identity ON contact(kind,UPPER(TRIM(document_type)),TRIM(number))");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS variable_pair_memory(id INTEGER PRIMARY KEY AUTOINCREMENT,source_key TEXT NOT NULL,source_value TEXT NOT NULL,source_display TEXT NOT NULL,target_key TEXT NOT NULL,target_value TEXT NOT NULL,updated_at TEXT NOT NULL,UNIQUE(source_key,source_value,target_key))");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS template_revision(template_id INTEGER NOT NULL,version INTEGER NOT NULL,content BLOB NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(template_id,version))");
            MCs.executeUpdate(MChistorySchema("IF NOT EXISTS document_history"));
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS field_mapping(id INTEGER PRIMARY KEY,template_id INTEGER NOT NULL,source_key TEXT NOT NULL,api_field TEXT NOT NULL,target_key TEXT NOT NULL,UNIQUE(template_id,source_key,target_key))");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS variable_api_binding(definition_id INTEGER PRIMARY KEY,service TEXT NOT NULL,path TEXT NOT NULL)");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS variable_query_context(template_id INTEGER NOT NULL,variable_key TEXT NOT NULL,group_key TEXT NOT NULL,service TEXT NOT NULL,source INTEGER NOT NULL,PRIMARY KEY(template_id,variable_key))");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS contact_binding(id INTEGER PRIMARY KEY,template_id INTEGER NOT NULL,group_key TEXT NOT NULL,kind TEXT NOT NULL,field TEXT NOT NULL,variable_key TEXT NOT NULL,UNIQUE(template_id,group_key,field))");
        }
        try(Connection MCc=MCopen()){MCmigrateHistory(MCc);MCmigrateContact(MCc);}
        try(Connection MCc=MCopen();Statement MCs=MCc.createStatement()){
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS client_document(id INTEGER PRIMARY KEY AUTOINCREMENT,contact_id INTEGER NOT NULL REFERENCES contact(id) ON DELETE CASCADE,title TEXT NOT NULL,file_name TEXT NOT NULL,extension TEXT NOT NULL,source_type TEXT NOT NULL CHECK(source_type IN ('GENERATED','UPLOADED')),content_bytes BLOB NOT NULL,updated_at TEXT NOT NULL,history_id INTEGER REFERENCES document_history(id) ON DELETE SET NULL,history_format TEXT,UNIQUE(contact_id,history_id,history_format))");
            MCs.executeUpdate("CREATE INDEX IF NOT EXISTS ix_client_document_contact ON client_document(contact_id)");
        }
    }

    public List<MCClientDocument> MCclientDocuments(long MCcontactId)throws SQLException{
        MCContact MCcontact=MCcontacts().stream().filter(MCx->MCx.MCid()==MCcontactId).findFirst().orElse(null);
        if(MCcontact==null)return List.of();
        Set<String> MCindependent=MCindependentKeys();
        for(MCHistory MChistory:MChistory())if(MChistoryMatches(MCcontact,MChistory,MCindependent))MClinkHistoryToContacts(MChistory.MCid(),List.of(MCcontactId));
        List<MCClientDocument> MCdocuments=new ArrayList<>();
        try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("SELECT * FROM client_document WHERE contact_id=? ORDER BY datetime(updated_at) DESC,id DESC")){
            MCs.setLong(1,MCcontactId);try(ResultSet MCr=MCs.executeQuery()){while(MCr.next())MCdocuments.add(MCreadClientDocument(MCr));}
        }return MCdocuments;
    }
    public MCClientDocument MCaddClientDocument(long MCcontactId,String MCtitle,String MCfileName,String MCextension,byte[] MCbytes)throws SQLException{
        MCvalidateClientDocument(MCtitle,MCfileName,MCextension,MCbytes,"UPLOADED");
        try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("INSERT INTO client_document(contact_id,title,file_name,extension,source_type,content_bytes,updated_at) VALUES(?,?,?,?,'UPLOADED',?,?)",Statement.RETURN_GENERATED_KEYS)){
            String MCnow=java.time.Instant.now().toString();MCs.setLong(1,MCcontactId);MCs.setString(2,MCtitle);MCs.setString(3,MCfileName);MCs.setString(4,MCextension.toLowerCase(Locale.ROOT));MCs.setBytes(5,MCbytes);MCs.setString(6,MCnow);MCs.executeUpdate();
            try(ResultSet MCr=MCs.getGeneratedKeys()){return new MCClientDocument(MCr.next()?MCr.getLong(1):0,MCcontactId,MCtitle,MCfileName,MCextension.toLowerCase(Locale.ROOT),"UPLOADED",MCbytes,MCnow);}
        }
    }
    public void MCreplaceClientDocument(long MCdocId,String MCnewTitle,String MCnewFileName,String MCnewExtension,byte[] MCnewBytes,String MCsourceType)throws SQLException{
        MCvalidateClientDocument(MCnewTitle,MCnewFileName,MCnewExtension,MCnewBytes,MCsourceType);
        try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("UPDATE client_document SET title=?,file_name=?,extension=?,content_bytes=?,source_type=?,updated_at=? WHERE id=?")){
            MCs.setString(1,MCnewTitle);MCs.setString(2,MCnewFileName);MCs.setString(3,MCnewExtension.toLowerCase(Locale.ROOT));MCs.setBytes(4,MCnewBytes);MCs.setString(5,MCsourceType);MCs.setString(6,java.time.Instant.now().toString());MCs.setLong(7,MCdocId);
            if(MCs.executeUpdate()!=1)throw new SQLException("El documento del cliente ya no existe.");
        }
    }
    private static void MCvalidateClientDocument(String MCtitle,String MCfileName,String MCextension,byte[] MCbytes,String MCsource){
        if(MCtitle==null||MCtitle.isBlank()||MCfileName==null||MCfileName.isBlank()||MCextension==null||MCbytes==null||MCbytes.length==0)
            throw new IllegalArgumentException("Seleccione un archivo con contenido y nombre.");
        if(!Set.of("GENERATED","UPLOADED").contains(MCsource))throw new IllegalArgumentException("Origen de documento inválido.");
    }
    private static MCClientDocument MCreadClientDocument(ResultSet MCr)throws SQLException{
        return new MCClientDocument(MCr.getLong("id"),MCr.getLong("contact_id"),MCr.getString("title"),MCr.getString("file_name"),MCr.getString("extension"),MCr.getString("source_type"),MCr.getBytes("content_bytes"),MCr.getString("updated_at"));
    }
    public void MClinkHistoryToContacts(long MChistoryId,Collection<Long> MCcontactIds)throws SQLException{
        try(Connection MCc=MCopen()){
            MCc.setAutoCommit(false);try{
                MCHistory MChistory;
                try(PreparedStatement MCs=MCc.prepareStatement("SELECT * FROM document_history WHERE id=?")){MCs.setLong(1,MChistoryId);try(ResultSet MCr=MCs.executeQuery()){if(!MCr.next())return;MChistory=MCreadHistory(MCr);}}
                for(long MCcontactId:new LinkedHashSet<>(MCcontactIds))for(String MCformat:List.of("docx","pdf")){
                    byte[] MCbytes=MCformat.equals("docx")?MChistory.MCdocx():MChistory.MCpdf();if(MCbytes==null||MCbytes.length==0)continue;
                    try(PreparedStatement MCs=MCc.prepareStatement("INSERT OR IGNORE INTO client_document(contact_id,title,file_name,extension,source_type,content_bytes,updated_at,history_id,history_format) VALUES(?,?,?,?,'GENERATED',?,?,?,?)")){
                        MCs.setLong(1,MCcontactId);MCs.setString(2,MChistory.MCtitle());MCs.setString(3,MChistory.MCtitle().replaceAll("[\\\\/:*?\"<>|]","_")+"."+MCformat);MCs.setString(4,MCformat);MCs.setBytes(5,MCbytes);MCs.setString(6,MChistory.MCgeneratedAt());MCs.setLong(7,MChistoryId);MCs.setString(8,MCformat);MCs.executeUpdate();
                    }
                }MCc.commit();
            }catch(SQLException MCe){MCc.rollback();throw MCe;}
        }
    }
    private boolean MChistoryMatches(MCContact MCcontact,MCHistory MChistory,Set<String> MCindependent)throws SQLException{
        try{
            var MCvalues=MCJSON.readTree(MChistory.MCvaluesJson());if(MCvalues==null||!MCvalues.isObject())return false;
            Map<String,Map<String,String>> MCscopes=new LinkedHashMap<>();
            var MCfields=MCvalues.fields();while(MCfields.hasNext()){
                var MCfield=MCfields.next();if(!MCfield.getValue().isValueNode())continue;
                String MCkey=MCDocumentEngine.MCbaseKey(MCfield.getKey());
                if(MCindependent.contains(MCvariableKey(MCkey)))continue;
                MCscopes.computeIfAbsent(MCDocumentEngine.MCpersonaKey(MCfield.getKey()),MCignored->new LinkedHashMap<>()).put(MCvariableKey(MCkey),MCfield.getValue().asText(""));
            }
            if(MChistory.MCtemplateId()!=null)for(MCContactBinding MCbinding:MCcontactBindings(MChistory.MCtemplateId())){
                if(MCindependent.contains(MCvariableKey(MCDocumentEngine.MCbaseKey(MCbinding.MCvariableKey()))))continue;
                var MCvalue=MCvalues.get(MCbinding.MCvariableKey());if(MCvalue!=null&&MCvalue.isValueNode())MCscopes.computeIfAbsent("binding:"+MCbinding.MCgroupKey(),MCignored->new LinkedHashMap<>()).put(MCbinding.MCfield(),MCvalue.asText(""));
            }
            for(Map<String,String> MCscope:MCscopes.values()){
                String MCgiven="",MCfamily="";
                for(var MCentry:MCscope.entrySet()){
                    String MCkey=MCentry.getKey(),MCvalue=MCentry.getValue();
                    if(Set.of("contact_id","cliente_id").contains(MCkey)&&MCvalue.equals(Long.toString(MCcontact.MCid())))return true;
                    if(!MCcontact.MCnumber().isBlank()&&Set.of("dni","ruc","documento","numero_documento","number").contains(MCkey)&&MCnormalized(MCvalue).equals(MCnormalized(MCcontact.MCnumber())))return true;
                    if(!MCvalue.isBlank()&&MCnormalized(MCvalue).equals(MCnormalized(MCcontactName(MCcontact))))return true;
                    if(Set.of("nombre","nombres","given").contains(MCkey))MCgiven=MCvalue;
                    if(Set.of("apellidos","family").contains(MCkey))MCfamily=MCvalue;
                }
                if(!MCgiven.isBlank()&&MCnormalized(MCgiven+" "+MCfamily).equals(MCnormalized(MCcontactName(MCcontact))))return true;
            }return false;
        }catch(com.fasterxml.jackson.core.JsonProcessingException MCe){throw new SQLException("Valores del historial inválidos.",MCe);}
    }
    static void MCmigrateHistory(Connection MCc)throws SQLException{
        boolean MCexists=false,MCnotNull=false,MCsetNull=false;List<String> MCobjects=new ArrayList<>();
        try(Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("PRAGMA table_info(document_history)")){
            while(MCr.next()){MCexists=true;if("template_id".equals(MCr.getString("name")))MCnotNull=MCr.getInt("notnull")!=0;}
        }
        if(!MCexists)return;
        try(Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("PRAGMA foreign_key_list(document_history)")){
            while(MCr.next())if("template_id".equals(MCr.getString("from"))&&"template".equals(MCr.getString("table"))&&"SET NULL".equalsIgnoreCase(MCr.getString("on_delete")))MCsetNull=true;
        }
        if(!MCnotNull&&MCsetNull)return;
        try(Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT sql FROM sqlite_master WHERE tbl_name='document_history' AND type IN ('index','trigger') AND sql IS NOT NULL")){while(MCr.next())MCobjects.add(MCr.getString(1));}
        try(Statement MCs=MCc.createStatement()){MCs.execute("PRAGMA foreign_keys=OFF");}
        MCc.setAutoCommit(false);
        try(Statement MCs=MCc.createStatement()){
            MCs.executeUpdate(MChistorySchema("document_history_migration"));
            MCs.executeUpdate("INSERT INTO document_history_migration("+MCHISTORY_COLUMNS+") SELECT h.id,h.title,CASE WHEN EXISTS(SELECT 1 FROM template t WHERE t.id=h.template_id) THEN h.template_id ELSE NULL END,h.template_version,h.generated_at,h.values_json,h.docx_path,h.pdf_path,h.docx,h.pdf FROM document_history h");
            MCs.executeUpdate("DROP TABLE document_history");MCs.executeUpdate("ALTER TABLE document_history_migration RENAME TO document_history");
            for(String MCsql:MCobjects)MCs.executeUpdate(MCsql);
            try(ResultSet MCcheck=MCs.executeQuery("PRAGMA foreign_key_check(document_history)")){if(MCcheck.next())throw new SQLException("El historial migrado contiene una referencia inválida.");}
            MCc.commit();
        }catch(SQLException MCe){MCc.rollback();throw MCe;}
        finally{MCc.setAutoCommit(true);try(Statement MCs=MCc.createStatement()){MCs.execute("PRAGMA foreign_keys=ON");}}
    }
    public Path MCdatabase(){return MCdb;}
    private Connection MCopen()throws SQLException{Connection MCc=DriverManager.getConnection("jdbc:sqlite:"+MCdb);try(Statement MCs=MCc.createStatement()){MCs.execute("PRAGMA foreign_keys=ON");}return MCc;}
    public List<MCContact> MCcontacts()throws SQLException{List<MCContact> MCout=new ArrayList<>();try(Connection MCc=MCopen();Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT * FROM contact ORDER BY kind,given,family")){while(MCr.next())MCout.add(new MCContact(MCr.getLong("id"),MCr.getString("kind"),MCr.getString("given"),MCr.getString("family"),MCr.getString("document_type"),MCr.getString("number"),MCr.getString("address"),MCr.getString("representatives"),MCr.getString("extras_json")));}return MCout;}
    public synchronized MCContact MCsaveContact(MCContact MCx)throws SQLException{
        try(Connection MCc=MCopen()){
            MCc.setAutoCommit(false);
            try{
                MCContact MCexisting=MCmatchingContact(MCc,MCx);
                if(MCx.MCid()!=0&&MCexisting!=null&&MCexisting.MCid()!=MCx.MCid())throw new SQLException("Ya existe otro cliente con esa identidad.");
                long MCid=MCexisting==null?MCx.MCid():MCexisting.MCid();
                String MCsql=MCid==0?"INSERT INTO contact(kind,given,family,document_type,number,address,representatives,extras_json) VALUES(?,?,?,?,?,?,?,?)":"UPDATE contact SET kind=?,given=?,family=?,document_type=?,number=?,address=?,representatives=?,extras_json=? WHERE id=?";
                try(PreparedStatement MCs=MCc.prepareStatement(MCsql,Statement.RETURN_GENERATED_KEYS)){
                    MCbindContact(MCs,MCx);if(MCid!=0)MCs.setLong(9,MCid);MCs.executeUpdate();
                    if(MCid==0)try(ResultSet MCkeys=MCs.getGeneratedKeys()){if(MCkeys.next())MCid=MCkeys.getLong(1);}
                }
                MCc.commit();
                return new MCContact(MCid,MCx.MCkind(),MCx.MCgiven(),MCx.MCfamily(),MCx.MCdocumentType().trim().toUpperCase(Locale.ROOT),MCx.MCnumber().trim(),MCx.MCaddress(),MCx.MCrepresentatives(),MCx.MCextrasJson());
            }catch(SQLException MCe){MCc.rollback();throw MCe;}
        }
    }
    public synchronized MCContact MCupsertContact(MCContact MCincoming)throws SQLException{
        MCContact MCexisting=MCmatchingContact(MCincoming);
        return MCsaveContact(MCexisting==null?MCincoming:MCmergeContact(MCexisting,MCincoming));
    }
    static MCContact MCmergeContact(MCContact MCexisting,MCContact MCincoming)throws SQLException{
        try{
            ObjectNode MCextras=MCJSON.createObjectNode();
            for(String MCsource:List.of(MCexisting.MCextrasJson(),MCincoming.MCextrasJson())){
                var MCnode=MCJSON.readTree(MCsource==null||MCsource.isBlank()?"{}":MCsource);
                if(MCnode==null||!MCnode.isObject())throw new IllegalArgumentException("Los datos adicionales deben ser un objeto JSON.");
                MCnode.fields().forEachRemaining(MCe->{if(!MCe.getValue().isNull()&&!(MCe.getValue().isTextual()&&MCe.getValue().asText().isBlank()))MCextras.set(MCe.getKey(),MCe.getValue());});
            }
            var MCincomingExtras=MCJSON.readTree(MCincoming.MCextrasJson()==null||MCincoming.MCextrasJson().isBlank()?"{}":MCincoming.MCextrasJson());
            boolean MCsurnameParts=MCincomingExtras.hasNonNull("apellido_paterno")||MCincomingExtras.hasNonNull("apellido_materno");
            boolean MCwholeFamily=MCincomingExtras.hasNonNull("apellidos");
            String MCfamily=MCprefer(MCincoming.MCfamily(),MCexisting.MCfamily());
            if(MCsurnameParts&&!MCwholeFamily){String MCcombined=(MCextras.path("apellido_paterno").asText("")+" "+MCextras.path("apellido_materno").asText("")).trim();MCfamily=MCprefer(MCcombined,MCfamily);}
            return new MCContact(MCexisting.MCid(),MCincoming.MCkind(),MCprefer(MCincoming.MCgiven(),MCexisting.MCgiven()),MCfamily,MCincoming.MCdocumentType(),MCincoming.MCnumber(),MCprefer(MCincoming.MCaddress(),MCexisting.MCaddress()),MCprefer(MCincoming.MCrepresentatives(),MCexisting.MCrepresentatives()),MCJSON.writeValueAsString(MCextras));
        }catch(Exception MCe){throw new SQLException("Datos adicionales de cliente inválidos.",MCe);}
    }
    private static String MCprefer(String MCnewer,String MColder){return MCnewer==null||MCnewer.isBlank()?MColder:MCnewer;}
    private static void MCbindContact(PreparedStatement MCs,MCContact MCx)throws SQLException{MCs.setString(1,MCx.MCkind());MCs.setString(2,MCx.MCgiven());MCs.setString(3,MCx.MCfamily());MCs.setString(4,MCx.MCdocumentType().trim().toUpperCase(Locale.ROOT));MCs.setString(5,MCx.MCnumber().trim());MCs.setString(6,MCx.MCaddress());MCs.setString(7,MCx.MCrepresentatives());MCs.setString(8,MCx.MCextrasJson());}
    public void MCdeleteContact(long MCid)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM contact WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}}
    public int MCrecordRevision(long MCtemplateId,byte[] MCcontent)throws SQLException{try(Connection MCc=MCopen()){try(PreparedStatement MCq=MCc.prepareStatement("SELECT version,content FROM template_revision WHERE template_id=? ORDER BY version DESC LIMIT 1")){MCq.setLong(1,MCtemplateId);try(ResultSet MCr=MCq.executeQuery()){if(MCr.next()){int MCversion=MCr.getInt(1);if(Arrays.equals(MCcontent,MCr.getBytes(2)))return MCversion;return MCinsertRevision(MCc,MCtemplateId,MCversion+1,MCcontent);}}}return MCinsertRevision(MCc,MCtemplateId,1,MCcontent);}}
    private static int MCinsertRevision(Connection MCc,long MCid,int MCversion,byte[] MCcontent)throws SQLException{try(PreparedStatement MCs=MCc.prepareStatement("INSERT INTO template_revision(template_id,version,content) VALUES(?,?,?)")){MCs.setLong(1,MCid);MCs.setInt(2,MCversion);MCs.setBytes(3,MCcontent);MCs.executeUpdate();}return MCversion;}
    public int MCversion(long MCtemplateId)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("SELECT COALESCE(MAX(version),0) FROM template_revision WHERE template_id=?")){MCs.setLong(1,MCtemplateId);try(ResultSet MCr=MCs.executeQuery()){return MCr.next()?MCr.getInt(1):0;}}}
    public Map<Long,String> MCrevisionDates()throws SQLException{Map<Long,String> MCdates=new HashMap<>();try(Connection MCc=MCopen();Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT template_id,MAX(created_at) FROM template_revision GROUP BY template_id")){while(MCr.next())MCdates.put(MCr.getLong(1),MCr.getString(2));}return MCdates;}
    public byte[] MCrevision(long MCtemplateId,int MCversion)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("SELECT content FROM template_revision WHERE template_id=? AND version=?")){MCs.setLong(1,MCtemplateId);MCs.setInt(2,MCversion);try(ResultSet MCr=MCs.executeQuery()){return MCr.next()?MCr.getBytes(1):null;}}}
    public List<MCHistory> MChistory()throws SQLException{List<MCHistory> MCout=new ArrayList<>();try(Connection MCc=MCopen();Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT * FROM document_history ORDER BY id DESC")){while(MCr.next())MCout.add(MCreadHistory(MCr));}return MCout;}
    private static MCHistory MCreadHistory(ResultSet MCr)throws SQLException{long MCvalue=MCr.getLong("template_id");Long MCtemplateId=MCr.wasNull()?null:MCvalue;return new MCHistory(MCr.getLong("id"),MCr.getString("title"),MCtemplateId,MCr.getInt("template_version"),MCr.getString("generated_at"),MCr.getString("values_json"),MCr.getString("docx_path"),MCr.getString("pdf_path"),MCr.getBytes("docx"),MCr.getBytes("pdf"));}
    public MCHistory MCsaveHistory(MCHistory MCh)throws SQLException{
        long MCid;
        try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("INSERT INTO document_history(title,template_id,template_version,values_json,docx_path,pdf_path,docx,pdf) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            MCs.setString(1,MCh.MCtitle());if(MCh.MCtemplateId()==null)MCs.setNull(2,Types.INTEGER);else MCs.setLong(2,MCh.MCtemplateId());MCs.setInt(3,MCh.MCtemplateVersion());MCs.setString(4,MCh.MCvaluesJson());MCs.setString(5,MCh.MCdocxPath());MCs.setString(6,MCh.MCpdfPath());MCs.setBytes(7,MCh.MCdocx());MCs.setBytes(8,MCh.MCpdf());MCs.executeUpdate();try(ResultSet MCr=MCs.getGeneratedKeys()){MCid=MCr.next()?MCr.getLong(1):0;}
        }
        MCHistory MCsaved=MChistoryById(MCid);MCsynchronizeHistoryClients(MCsaved);return MCsaved;
    }
    private MCHistory MChistoryById(long MCid)throws SQLException{
        try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("SELECT * FROM document_history WHERE id=?")){MCs.setLong(1,MCid);try(ResultSet MCr=MCs.executeQuery()){return MCr.next()?MCreadHistory(MCr):null;}}
    }
    private Set<String> MCindependentKeys()throws SQLException{
        Set<String> MCindependent=new HashSet<>();try(Connection MCc=MCopen();Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT key FROM variable_definition WHERE bind_to_client=0")){while(MCr.next())MCindependent.add(MCvariableKey(MCr.getString(1)));}return MCindependent;
    }
    private void MCsynchronizeHistoryClients(MCHistory MChistory)throws SQLException{
        if(MChistory==null)return;List<MCContact> MCcontacts=MCcontacts();if(MCcontacts.isEmpty())return;Set<String> MCindependent=MCindependentKeys();List<Long> MCids=new ArrayList<>();
        for(MCContact MCcontact:MCcontacts)if(MChistoryMatches(MCcontact,MChistory,MCindependent))MCids.add(MCcontact.MCid());
        if(!MCids.isEmpty())MClinkHistoryToContacts(MChistory.MCid(),MCids);
    }
    public void MCdeleteHistory(long MCid)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM document_history WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}}
    public void MCupdateHistoryFiles(long MCid,String MCdocxPath,String MCpdfPath,byte[] MCpdf)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("UPDATE document_history SET docx_path=COALESCE(?,docx_path),pdf_path=COALESCE(?,pdf_path),pdf=COALESCE(?,pdf) WHERE id=?")){MCs.setString(1,MCdocxPath);MCs.setString(2,MCpdfPath);MCs.setBytes(3,MCpdf);MCs.setLong(4,MCid);MCs.executeUpdate();}MCsynchronizeHistoryClients(MChistoryById(MCid));}
    public List<MCMapping> MCmappings(long MCtemplateId)throws SQLException{List<MCMapping> MCout=new ArrayList<>();try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("SELECT * FROM field_mapping WHERE template_id=? ORDER BY id")){MCs.setLong(1,MCtemplateId);try(ResultSet MCr=MCs.executeQuery()){while(MCr.next())MCout.add(new MCMapping(MCr.getLong(1),MCr.getLong(2),MCr.getString(3),MCr.getString(4),MCr.getString(5)));}}return MCout;}
    public MCMapping MCsaveMapping(MCMapping MCm)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("INSERT INTO field_mapping(template_id,source_key,api_field,target_key) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){MCs.setLong(1,MCm.MCtemplateId());MCs.setString(2,MCm.MCsourceKey());MCs.setString(3,MCm.MCapiField());MCs.setString(4,MCm.MCtargetKey());MCs.executeUpdate();try(ResultSet MCr=MCs.getGeneratedKeys()){return new MCMapping(MCr.next()?MCr.getLong(1):0,MCm.MCtemplateId(),MCm.MCsourceKey(),MCm.MCapiField(),MCm.MCtargetKey());}}}
    public void MCdeleteMapping(long MCid)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM field_mapping WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}}
    public void MCclearMappings(long MCtemplateId)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM field_mapping WHERE template_id=?")){MCs.setLong(1,MCtemplateId);MCs.executeUpdate();}}
    public MCApiBinding MCapiBinding(long MCdefinitionId)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("SELECT service,path FROM variable_api_binding WHERE definition_id=?")){MCs.setLong(1,MCdefinitionId);try(ResultSet MCr=MCs.executeQuery()){return MCr.next()?new MCApiBinding(MCdefinitionId,MCr.getString(1),MCr.getString(2)):null;}}}
    public void MCsaveApiBinding(MCApiBinding MCbinding)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("INSERT INTO variable_api_binding(definition_id,service,path) VALUES(?,?,?) ON CONFLICT(definition_id) DO UPDATE SET service=excluded.service,path=excluded.path")){MCs.setLong(1,MCbinding.MCdefinitionId());MCs.setString(2,MCbinding.MCservice());MCs.setString(3,MCbinding.MCpath());MCs.executeUpdate();}}
    public void MCdeleteApiBinding(long MCdefinitionId)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM variable_api_binding WHERE definition_id=?")){MCs.setLong(1,MCdefinitionId);MCs.executeUpdate();}}
    public List<MCApiBinding> MCapiBindings()throws SQLException{List<MCApiBinding> MCout=new ArrayList<>();try(Connection MCc=MCopen();Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT definition_id,service,path FROM variable_api_binding")){while(MCr.next())MCout.add(new MCApiBinding(MCr.getLong(1),MCr.getString(2),MCr.getString(3)));}return MCout;}
    public List<MCApiContext> MCapiContexts(long MCtemplateId)throws SQLException{List<MCApiContext> MCout=new ArrayList<>();try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("SELECT template_id,variable_key,group_key,service,source FROM variable_query_context WHERE template_id=? ORDER BY group_key,source DESC,variable_key")){MCs.setLong(1,MCtemplateId);try(ResultSet MCr=MCs.executeQuery()){while(MCr.next())MCout.add(new MCApiContext(MCr.getLong(1),MCr.getString(2),MCr.getString(3),MCr.getString(4),MCr.getInt(5)==1));}}return MCout;}
    public void MCsaveApiContext(MCApiContext MCcontext)throws SQLException{if(MCcontext.MCgroupKey()==null||MCcontext.MCgroupKey().isBlank()||MCcontext.MCvariableKey()==null||MCcontext.MCvariableKey().isBlank()||!List.of("DNI","RUC").contains(MCcontext.MCservice()))throw new IllegalArgumentException("Grupo, variable o servicio inválido.");try(Connection MCc=MCopen()){
        if(MCcontext.MCsource())try(PreparedStatement MCcheck=MCc.prepareStatement("SELECT variable_key FROM variable_query_context WHERE template_id=? AND group_key=? AND service=? AND source=1 AND variable_key<>? LIMIT 1")){MCcheck.setLong(1,MCcontext.MCtemplateId());MCcheck.setString(2,MCcontext.MCgroupKey().trim());MCcheck.setString(3,MCcontext.MCservice());MCcheck.setString(4,MCcontext.MCvariableKey());try(ResultSet MCr=MCcheck.executeQuery()){if(MCr.next())throw new IllegalArgumentException("El grupo ya tiene un número a consultar. Use otro grupo independiente.");}}
        try(PreparedStatement MCs=MCc.prepareStatement("INSERT INTO variable_query_context(template_id,variable_key,group_key,service,source) VALUES(?,?,?,?,?) ON CONFLICT(template_id,variable_key) DO UPDATE SET group_key=excluded.group_key,service=excluded.service,source=excluded.source")){MCs.setLong(1,MCcontext.MCtemplateId());MCs.setString(2,MCcontext.MCvariableKey());MCs.setString(3,MCcontext.MCgroupKey().trim());MCs.setString(4,MCcontext.MCservice());MCs.setInt(5,MCcontext.MCsource()?1:0);MCs.executeUpdate();}
    }}
    public void MCdeleteApiContext(long MCtemplateId,String MCvariableKey)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM variable_query_context WHERE template_id=? AND variable_key=?")){MCs.setLong(1,MCtemplateId);MCs.setString(2,MCvariableKey);MCs.executeUpdate();}}
    public void MCclearApiContexts(long MCtemplateId)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM variable_query_context WHERE template_id=?")){MCs.setLong(1,MCtemplateId);MCs.executeUpdate();}}
    public List<MCContactBinding> MCcontactBindings(long MCtemplateId)throws SQLException{
        List<MCContactBinding> MCout=new ArrayList<>();
        try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("SELECT * FROM contact_binding WHERE template_id=? ORDER BY group_key,id")){
            MCs.setLong(1,MCtemplateId);try(ResultSet MCr=MCs.executeQuery()){while(MCr.next())MCout.add(new MCContactBinding(MCr.getLong("id"),MCr.getLong("template_id"),MCr.getString("group_key"),MCr.getString("kind"),MCr.getString("field"),MCr.getString("variable_key")));}
        }
        return MCout;
    }
    public MCContactBinding MCsaveContactBinding(MCContactBinding MCb)throws SQLException{
        if(MCb.MCgroupKey()==null||MCb.MCgroupKey().isBlank()||!List.of("Persona","Empresa").contains(MCb.MCkind())||MCb.MCfield()==null||MCb.MCfield().isBlank()||MCb.MCvariableKey()==null||MCb.MCvariableKey().isBlank())throw new IllegalArgumentException("Asociación incompleta.");
        try(Connection MCc=MCopen()){
            try(PreparedStatement MCcheck=MCc.prepareStatement("SELECT kind FROM contact_binding WHERE template_id=? AND group_key=? LIMIT 1")){
                MCcheck.setLong(1,MCb.MCtemplateId());MCcheck.setString(2,MCb.MCgroupKey());try(ResultSet MCr=MCcheck.executeQuery()){if(MCr.next()&&!MCr.getString(1).equals(MCb.MCkind()))throw new IllegalArgumentException("El grupo ya tiene otro tipo de registro.");}
            }
            try(PreparedStatement MCcheck=MCc.prepareStatement("SELECT group_key,field FROM contact_binding WHERE template_id=? AND variable_key=? LIMIT 1")){
                MCcheck.setLong(1,MCb.MCtemplateId());MCcheck.setString(2,MCb.MCvariableKey());try(ResultSet MCr=MCcheck.executeQuery()){if(MCr.next())throw new IllegalArgumentException("La variable ya está asociada a "+MCr.getString(1)+" / "+MCr.getString(2)+". Use una variable independiente para otro registro.");}
            }
            try(PreparedStatement MCs=MCc.prepareStatement("INSERT INTO contact_binding(template_id,group_key,kind,field,variable_key) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                MCs.setLong(1,MCb.MCtemplateId());MCs.setString(2,MCb.MCgroupKey());MCs.setString(3,MCb.MCkind());MCs.setString(4,MCb.MCfield());MCs.setString(5,MCb.MCvariableKey());MCs.executeUpdate();try(ResultSet MCr=MCs.getGeneratedKeys()){return new MCContactBinding(MCr.next()?MCr.getLong(1):0,MCb.MCtemplateId(),MCb.MCgroupKey(),MCb.MCkind(),MCb.MCfield(),MCb.MCvariableKey());}
            }
        }
    }
    public void MCdeleteContactBinding(long MCid)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM contact_binding WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}}
    public void MCclearContactBindings(long MCtemplateId)throws SQLException{try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM contact_binding WHERE template_id=?")){MCs.setLong(1,MCtemplateId);MCs.executeUpdate();}}
    public MCContact MCmatchingContact(String MCkind,String MCdocumentType,String MCnumber)throws SQLException{
        try(Connection MCc=MCopen()){return MCmatchingContact(MCc,MCkind,MCdocumentType,MCnumber);}
    }
    private static MCContact MCmatchingContact(Connection MCc,String MCkind,String MCdocumentType,String MCnumber)throws SQLException{
        if(MCnumber==null||MCnumber.isBlank())return null;
        try(PreparedStatement MCs=MCc.prepareStatement("SELECT * FROM contact WHERE kind=? AND UPPER(TRIM(document_type))=UPPER(?) AND TRIM(number)=? ORDER BY id LIMIT 1")){
            MCs.setString(1,MCkind);MCs.setString(2,MCdocumentType==null?"":MCdocumentType.trim());MCs.setString(3,MCnumber.trim());
            try(ResultSet MCr=MCs.executeQuery()){return MCr.next()?MCreadContact(MCr):null;}
        }
    }
    public MCContact MCfindContactByNumber(String MCnumber)throws SQLException{
        if(MCnumber==null||MCnumber.isBlank())return null;
        try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement("SELECT * FROM contact WHERE TRIM(number)=? LIMIT 1")){
            MCs.setString(1,MCnumber.trim());
            try(ResultSet MCr=MCs.executeQuery()){return MCr.next()?MCreadContact(MCr):null;}
        }
    }
    private static MCContact MCreadContact(ResultSet MCr)throws SQLException{return new MCContact(MCr.getLong("id"),MCr.getString("kind"),MCr.getString("given"),MCr.getString("family"),MCr.getString("document_type"),MCr.getString("number"),MCr.getString("address"),MCr.getString("representatives"),MCr.getString("extras_json"));}    public record MCSuggestion(@com.fasterxml.jackson.annotation.JsonProperty("value") String MCvalue,@com.fasterxml.jackson.annotation.JsonProperty("complements") Map<String,String> MCcomplements,@com.fasterxml.jackson.annotation.JsonProperty("origin") String MCorigin){}
    public static String MCnormalized(String MCvalue){return java.text.Normalizer.normalize(MCvalue==null?"":MCvalue,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    public static String MCvariableKey(String MCvalue){return MCnormalized(MCvalue).replace(' ','_');}
    public static boolean MCpersonVariable(String MCkey){String MCk=MCvariableKey(MCkey);return Set.of("nombre","nombres","nombres_com","nombres_completo","nombres_completos","nombre_completo","nombre_cliente","razon_social","persona","cliente","solicitante","dirigido","destinatario","beneficiario").contains(MCk)||MCk.startsWith("dirigido_")||MCk.startsWith("solicitante_");}
    private static String MCcontactName(MCContact MCc){return (MCc.MCgiven()+" "+MCc.MCfamily()).trim();}
    public MCContact MCmatchingContact(MCContact MCincoming)throws SQLException{
        try(Connection MCc=MCopen()){return MCmatchingContact(MCc,MCincoming);}
    }
    private static MCContact MCmatchingContact(Connection MCc,MCContact MCincoming)throws SQLException{
        if(MCincoming.MCnumber()!=null&&!MCincoming.MCnumber().isBlank())return MCmatchingContact(MCc,MCincoming.MCkind(),MCincoming.MCdocumentType(),MCincoming.MCnumber());
        String MCname=MCnormalized(MCcontactName(MCincoming));if(MCname.isEmpty())return null;
        try(PreparedStatement MCs=MCc.prepareStatement("SELECT * FROM contact WHERE kind=? AND TRIM(number)='' ORDER BY id")){MCs.setString(1,MCincoming.MCkind());try(ResultSet MCr=MCs.executeQuery()){while(MCr.next()){MCContact MCfound=MCreadContact(MCr);if(MCnormalized(MCcontactName(MCfound)).equals(MCname))return MCfound;}}}return null;
    }
    public List<MCContact> MCfindContacts(String MCquery)throws SQLException{
        String MCq=MCnormalized(MCquery);List<MCContact> MCout=new ArrayList<>();for(MCContact MCc:MCcontacts()){
            if(MCnormalized(MCcontactName(MCc)+" "+MCc.MCnumber()+" "+MCc.MCaddress()).contains(MCq)){MCout.add(MCc);continue;}
            try{var MCnode=MCJSON.readTree(MCc.MCextrasJson());if(MCnode!=null&&MCextraContains(MCnode,MCq))MCout.add(MCc);}catch(Exception MCe){throw new SQLException("Datos adicionales de cliente inválidos.",MCe);}
        }return MCout;
    }
    private static boolean MCextraContains(com.fasterxml.jackson.databind.JsonNode MCnode,String MCquery){if(MCnode.isValueNode())return MCnormalized(MCnode.asText()).contains(MCquery);for(var MCchild:MCnode)if(MCextraContains(MCchild,MCquery))return true;return false;}
    public List<MCContact> MCfindContactsByVariable(String MCkey,String MCvalue,boolean MCprefix)throws SQLException{
        String MCq=MCnormalized(MCvalue);List<MCContact> MCout=new ArrayList<>();for(MCContact MCcontact:MCcontacts()){String MCfound=MCnormalized(MCcontactValue(MCcontact,MCkey));if(!MCfound.isEmpty()&&(MCprefix?MCfound.startsWith(MCq):MCfound.equals(MCq)))MCout.add(MCcontact);}return MCout;
    }
    public String MCcontactValue(MCContact MCcontact,String MCkey)throws SQLException{
        String MCnormalizedKey=MCvariableKey(MCkey);
        try{var MCextras=MCJSON.readTree(MCcontact.MCextrasJson());if(MCextras!=null){var MCfields=MCextras.fields();while(MCfields.hasNext()){var MCfield=MCfields.next();if(MCvariableKey(MCfield.getKey()).equals(MCnormalizedKey)&&MCfield.getValue().isValueNode())return MCfield.getValue().asText("");}}}catch(Exception MCe){throw new SQLException("Datos adicionales inválidos.",MCe);}
        if(MCpersonVariable(MCkey))return MCcontactName(MCcontact);
        return switch(MCnormalizedKey){case "dni","ruc","documento","number"->MCcontact.MCnumber();case "direccion","domicilio","address"->MCcontact.MCaddress();case "apellidos","family"->MCcontact.MCfamily();default->"";};
    }
    public void MCrememberPair(String MCsourceKey,String MCsourceValue,String MCtargetKey,String MCtargetValue,boolean MCbidirectional)throws SQLException{
        if(MCsourceValue==null||MCsourceValue.isBlank()||MCtargetValue==null||MCtargetValue.isBlank())return;
        try(Connection MCc=MCopen()){
            MCc.setAutoCommit(false);try{
                if(MCbidirectional)try(PreparedStatement MCprevious=MCc.prepareStatement("SELECT source_display,target_value FROM variable_pair_memory WHERE source_key=? AND source_value=? AND target_key=?")){
                    MCprevious.setString(1,MCvariableKey(MCsourceKey));MCprevious.setString(2,MCnormalized(MCsourceValue));MCprevious.setString(3,MCvariableKey(MCtargetKey));
                    try(ResultSet MCr=MCprevious.executeQuery()){if(MCr.next()&&!MCnormalized(MCr.getString(2)).equals(MCnormalized(MCtargetValue)))try(PreparedStatement MCremove=MCc.prepareStatement("DELETE FROM variable_pair_memory WHERE source_key=? AND source_value=? AND target_key=? AND target_value=?")){MCremove.setString(1,MCvariableKey(MCtargetKey));MCremove.setString(2,MCnormalized(MCr.getString(2)));MCremove.setString(3,MCvariableKey(MCsourceKey));MCremove.setString(4,MCr.getString(1));MCremove.executeUpdate();}}
                }
                MCwritePair(MCc,MCsourceKey,MCsourceValue,MCtargetKey,MCtargetValue);if(MCbidirectional)MCwritePair(MCc,MCtargetKey,MCtargetValue,MCsourceKey,MCsourceValue);MCc.commit();}catch(SQLException MCe){MCc.rollback();throw MCe;}
        }
    }
    private static void MCwritePair(Connection MCc,String MCsource,String MCvalue,String MCtarget,String MCresult)throws SQLException{
        try(PreparedStatement MCs=MCc.prepareStatement("INSERT INTO variable_pair_memory(source_key,source_value,source_display,target_key,target_value,updated_at) VALUES(?,?,?,?,?,?) ON CONFLICT(source_key,source_value,target_key) DO UPDATE SET source_display=excluded.source_display,target_value=excluded.target_value,updated_at=excluded.updated_at")){
            MCs.setString(1,MCvariableKey(MCsource));MCs.setString(2,MCnormalized(MCvalue));MCs.setString(3,MCvalue);MCs.setString(4,MCvariableKey(MCtarget));MCs.setString(5,MCresult);MCs.setString(6,java.time.Instant.now().toString());MCs.executeUpdate();
        }
    }
    public List<MCSuggestion> MCsuggestions(String MCsourceKey,Collection<String> MCtargetKeys,String MCvalue,boolean MCexact)throws SQLException{
        if(MCvalue==null||MCvalue.isBlank()||MCtargetKeys.isEmpty())return List.of();
        Set<String> MCtargets=new LinkedHashSet<>();MCtargetKeys.forEach(MCk->MCtargets.add(MCvariableKey(MCk)));Map<String,String> MCdisplay=new LinkedHashMap<>(),MCorigins=new HashMap<>();Map<String,Map<String,String>> MCresults=new LinkedHashMap<>();String MCq=MCnormalized(MCvalue);
        String MCsql="SELECT source_display,target_key,target_value FROM variable_pair_memory WHERE source_key=? AND source_value "+(MCexact?"=?":"LIKE ? ESCAPE '\\'")+" ORDER BY updated_at DESC,id DESC";
        try(Connection MCc=MCopen();PreparedStatement MCs=MCc.prepareStatement(MCsql)){
            MCs.setString(1,MCvariableKey(MCsourceKey));MCs.setString(2,MCexact?MCq:MCq.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");
            try(ResultSet MCr=MCs.executeQuery()){while(MCr.next()){String MCtarget=MCr.getString(2);if(!MCtargets.contains(MCtarget))continue;String MCoriginal=MCr.getString(1),MCkey=MCnormalized(MCoriginal);MCdisplay.putIfAbsent(MCkey,MCoriginal);MCorigins.putIfAbsent(MCkey,"Memoria local");MCresults.computeIfAbsent(MCkey,MCk->new LinkedHashMap<>()).putIfAbsent(MCtarget,MCr.getString(3));}}
        }
        for(MCContact MCcontact:MCfindContactsByVariable(MCsourceKey,MCvalue,!MCexact)){
            String MCoriginal=MCcontactValue(MCcontact,MCsourceKey),MCkey=MCnormalized(MCoriginal);Map<String,String> MCcomplements=new LinkedHashMap<>();for(String MCtarget:MCtargets){String MCfound=MCcontactValue(MCcontact,MCtarget);if(!MCfound.isBlank())MCcomplements.put(MCtarget,MCfound);}if(MCcomplements.isEmpty())continue;
            Map<String,String> MCknown=MCresults.get(MCkey);if(MCknown!=null&&MCcomplements.entrySet().stream().anyMatch(MCe->MCknown.containsKey(MCe.getKey())&&!MCknown.get(MCe.getKey()).equals(MCe.getValue())))MCkey=MCkey+"#contact-"+MCcontact.MCid();
            MCdisplay.putIfAbsent(MCkey,MCoriginal);MCorigins.putIfAbsent(MCkey,"Clientes");Map<String,String> MCrow=MCresults.computeIfAbsent(MCkey,MCk->new LinkedHashMap<>());MCcomplements.forEach(MCrow::putIfAbsent);
        }
        List<MCSuggestion> MCout=new ArrayList<>();for(var MCentry:MCresults.entrySet()){MCout.add(new MCSuggestion(MCdisplay.get(MCentry.getKey()),Map.copyOf(MCentry.getValue()),MCorigins.get(MCentry.getKey())));if(MCout.size()==10)break;}return MCout;
    }
    public void MClearnComplements(List<MCDocumentStore.MCDefinition> MCdefinitions,List<MCDocumentStore.MCComplement> MClinks,Map<String,String> MCvalues)throws SQLException{
        Map<Long,MCDocumentStore.MCDefinition> MCdefs=new HashMap<>();MCdefinitions.forEach(MCd->MCdefs.put(MCd.MCid(),MCd));Map<String,String> MCnormalizedValues=new HashMap<>();MCvalues.forEach((MCk,MCv)->MCnormalizedValues.put(MCvariableKey(MCk),MCv));
        for(var MClink:MClinks){var MCsource=MCdefs.get(MClink.MCsourceDefId());var MCtarget=MCdefs.get(MClink.MCtargetDefId());if(MCsource==null||MCtarget==null)continue;String MCa=MCnormalizedValues.get(MCvariableKey(MCsource.MCkey())),MCb=MCnormalizedValues.get(MCvariableKey(MCtarget.MCkey()));if(MCa==null||MCb==null||MCa.isBlank()||MCb.isBlank())continue;
            MCrememberPair(MCsource.MCkey(),MCa,MCtarget.MCkey(),MCb,MClink.MCbidirectional());
            if(MCpersonVariable(MCsource.MCkey()))MClearnPerson(MCsource.MCkey(),MCa,MCtarget.MCkey(),MCb);
            else if(MCpersonVariable(MCtarget.MCkey()))MClearnPerson(MCtarget.MCkey(),MCb,MCsource.MCkey(),MCa);
        }
    }
    private void MClearnPerson(String MCnameKey,String MCname,String MCotherKey,String MCvalue)throws SQLException{
        MCContact MCexisting=MCcontacts().stream().filter(MCc->MCnormalized(MCcontactName(MCc)).equals(MCnormalized(MCname))).findFirst().orElse(null);
        try{ObjectNode MCextras=MCJSON.createObjectNode();MCextras.put(MCvariableKey(MCnameKey),MCname);MCextras.put(MCvariableKey(MCotherKey),MCvalue);
            MCContact MCincoming=MCexisting==null?new MCContact(0,"razon_social".equals(MCvariableKey(MCnameKey))?"Empresa":"Persona",MCname,"","","","","",MCextras.toString()):new MCContact(MCexisting.MCid(),MCexisting.MCkind(),MCexisting.MCgiven(),MCexisting.MCfamily(),MCexisting.MCdocumentType(),MCexisting.MCnumber(),MCexisting.MCaddress(),MCexisting.MCrepresentatives(),MCextras.toString());
            MCupsertContact(MCincoming);
        }catch(Exception MCe){if(MCe instanceof SQLException MCsql)throw MCsql;throw new SQLException("No se pudo actualizar el cliente del complemento.",MCe);}
    }
    private static void MCmigrateContact(Connection MCc)throws SQLException{
        List<String> MCindexes=new ArrayList<>();boolean MCrebuild=false;
        try(Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("PRAGMA index_list(contact)")){while(MCr.next())if(MCr.getInt("unique")!=0)MCindexes.add(MCr.getString("name"));}
        List<String> MCdrop=new ArrayList<>();for(String MCindex:MCindexes){List<String> MCcolumns=new ArrayList<>();try(PreparedStatement MCs=MCc.prepareStatement("SELECT name FROM pragma_index_info(?)")){MCs.setString(1,MCindex);try(ResultSet MCr=MCs.executeQuery()){while(MCr.next())MCcolumns.add(MCr.getString(1));}}
            if(MCcolumns.equals(List.of("kind","document_type","number"))){if(MCindex.startsWith("sqlite_autoindex"))MCrebuild=true;else MCdrop.add(MCindex);}
        }
        if(!MCrebuild&&MCdrop.isEmpty())return;
        List<String> MCobjects=new ArrayList<>();try(Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT name,sql FROM sqlite_master WHERE tbl_name='contact' AND type IN ('index','trigger') AND sql IS NOT NULL")){while(MCr.next())if(!MCdrop.contains(MCr.getString(1)))MCobjects.add(MCr.getString(2));}
        MCc.setAutoCommit(false);try(Statement MCs=MCc.createStatement()){
            if(MCrebuild){MCs.executeUpdate("CREATE TABLE contact_migration(id INTEGER PRIMARY KEY,kind TEXT NOT NULL,given TEXT NOT NULL,family TEXT NOT NULL DEFAULT '',document_type TEXT NOT NULL DEFAULT '',number TEXT NOT NULL DEFAULT '',address TEXT NOT NULL DEFAULT '',representatives TEXT NOT NULL DEFAULT '',extras_json TEXT NOT NULL DEFAULT '{}')");MCs.executeUpdate("INSERT INTO contact_migration SELECT id,kind,given,family,document_type,number,address,representatives,extras_json FROM contact");MCs.executeUpdate("DROP TABLE contact");MCs.executeUpdate("ALTER TABLE contact_migration RENAME TO contact");for(String MCsql:MCobjects)MCs.executeUpdate(MCsql);}
            else for(String MCindex:MCdrop)MCs.executeUpdate("DROP INDEX \""+MCindex.replace("\"","\"\"")+"\"");MCc.commit();
        }catch(SQLException MCe){MCc.rollback();throw MCe;}finally{MCc.setAutoCommit(true);}
    }

}
