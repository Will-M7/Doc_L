package com.ejemplo.crudpersona.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** @author MC */
public final class MCStudioData {
    private static final ObjectMapper mcJSON=new ObjectMapper();
    /** @author MC */
    public record MCContact(@com.fasterxml.jackson.annotation.JsonProperty("id") long mcId,@com.fasterxml.jackson.annotation.JsonProperty("kind") String mcKind,@com.fasterxml.jackson.annotation.JsonProperty("given") String mcGiven,@com.fasterxml.jackson.annotation.JsonProperty("family") String mcFamily,@com.fasterxml.jackson.annotation.JsonProperty("documentType") String mcDocumentType,@com.fasterxml.jackson.annotation.JsonProperty("number") String mcNumber,
                          @com.fasterxml.jackson.annotation.JsonProperty("address") String mcAddress,@com.fasterxml.jackson.annotation.JsonProperty("representatives") String mcRepresentatives,@com.fasterxml.jackson.annotation.JsonProperty("extrasJson") String mcExtrasJson) {
        @Override public String toString(){return ("Empresa".equals(mcKind)?mcGiven:mcGiven+" "+mcFamily)+" · "+mcNumber;}
    }
    /** @author MC */
    public record MCHistory(@com.fasterxml.jackson.annotation.JsonProperty("id") long mcId,@com.fasterxml.jackson.annotation.JsonProperty("title") String mcTitle,@com.fasterxml.jackson.annotation.JsonProperty("templateId") Long mcTemplateId,@com.fasterxml.jackson.annotation.JsonProperty("templateVersion") int mcTemplateVersion,@com.fasterxml.jackson.annotation.JsonProperty("generatedAt") String mcGeneratedAt,
                          @com.fasterxml.jackson.annotation.JsonProperty("valuesJson") String mcValuesJson,@com.fasterxml.jackson.annotation.JsonProperty("docxPath") String mcDocxPath,@com.fasterxml.jackson.annotation.JsonProperty("pdfPath") String mcPdfPath,@com.fasterxml.jackson.annotation.JsonProperty("docx") byte[] mcDocx,@com.fasterxml.jackson.annotation.JsonProperty("pdf") byte[] mcPdf){
        @Override public String toString(){return mcGeneratedAt+" · "+mcTitle;}
    }
    /** @author MC */
    public record MCClientDocument(@com.fasterxml.jackson.annotation.JsonProperty("id") long mcId,@com.fasterxml.jackson.annotation.JsonProperty("contactId") long mcContactId,@com.fasterxml.jackson.annotation.JsonProperty("title") String mcTitle,@com.fasterxml.jackson.annotation.JsonProperty("fileName") String mcFileName,@com.fasterxml.jackson.annotation.JsonProperty("extension") String mcExtension,
                                 @com.fasterxml.jackson.annotation.JsonProperty("sourceType") String mcSourceType,@com.fasterxml.jackson.annotation.JsonProperty("contentBytes") byte[] mcContentBytes,@com.fasterxml.jackson.annotation.JsonProperty("updatedAt") String mcUpdatedAt) {}
    /** @author MC */
    public record MCMapping(@com.fasterxml.jackson.annotation.JsonProperty("id") long mcId,@com.fasterxml.jackson.annotation.JsonProperty("templateId") long mcTemplateId,@com.fasterxml.jackson.annotation.JsonProperty("sourceKey") String mcSourceKey,@com.fasterxml.jackson.annotation.JsonProperty("apiField") String mcApiField,@com.fasterxml.jackson.annotation.JsonProperty("targetKey") String mcTargetKey){
        @Override public String toString(){return mcSourceKey+" → "+mcApiField+" → "+mcTargetKey;}
    }
    /** @author MC */
    public record MCApiBinding(@com.fasterxml.jackson.annotation.JsonProperty("definitionId") long mcDefinitionId,@com.fasterxml.jackson.annotation.JsonProperty("service") String mcService,@com.fasterxml.jackson.annotation.JsonProperty("path") String mcPath){}
    /** @author MC */
    public record MCApiContext(@com.fasterxml.jackson.annotation.JsonProperty("templateId") long mcTemplateId,@com.fasterxml.jackson.annotation.JsonProperty("variableKey") String mcVariableKey,@com.fasterxml.jackson.annotation.JsonProperty("groupKey") String mcGroupKey,@com.fasterxml.jackson.annotation.JsonProperty("service") String mcService,@com.fasterxml.jackson.annotation.JsonProperty("source") boolean mcSource){
        @Override public String toString(){return mcGroupKey+" · "+mcService+" · "+mcVariableKey+(mcSource?" (número a consultar)":"");}
    }
    /** @author MC */
    public record MCContactBinding(@com.fasterxml.jackson.annotation.JsonProperty("id") long mcId,@com.fasterxml.jackson.annotation.JsonProperty("templateId") long mcTemplateId,@com.fasterxml.jackson.annotation.JsonProperty("groupKey") String mcGroupKey,@com.fasterxml.jackson.annotation.JsonProperty("kind") String mcKind,@com.fasterxml.jackson.annotation.JsonProperty("field") String mcField,@com.fasterxml.jackson.annotation.JsonProperty("variableKey") String mcVariableKey){
        @Override public String toString(){return mcGroupKey+" · "+mcKind+" · "+mcField+" → {{"+mcVariableKey+"}}";}
    }
    private final Path mcDb;
    private static final String mcHISTORY_COLUMNS="id,title,template_id,template_version,generated_at,values_json,docx_path,pdf_path,docx,pdf";
    private static String mcHistorySchema(String mcTable){return "CREATE TABLE "+mcTable+"(id INTEGER PRIMARY KEY,title TEXT NOT NULL,template_id INTEGER REFERENCES template(id) ON DELETE SET NULL,template_version INTEGER NOT NULL,generated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,values_json TEXT NOT NULL,docx_path TEXT,pdf_path TEXT,docx BLOB NOT NULL,pdf BLOB)";}
    public MCStudioData(Path mcDirectory)throws SQLException{
        mcDb=mcDirectory.resolve("studio.db");
        try(Connection mcC=mcOpen();Statement mcS=mcC.createStatement()){
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS contact(id INTEGER PRIMARY KEY,kind TEXT NOT NULL,given TEXT NOT NULL,family TEXT NOT NULL DEFAULT '',document_type TEXT NOT NULL DEFAULT '',number TEXT NOT NULL DEFAULT '',address TEXT NOT NULL DEFAULT '',representatives TEXT NOT NULL DEFAULT '',extras_json TEXT NOT NULL DEFAULT '{}')");
            mcS.executeUpdate("CREATE INDEX IF NOT EXISTS ix_contact_number ON contact(number)");
            mcS.executeUpdate("CREATE INDEX IF NOT EXISTS ix_contact_identity ON contact(kind,UPPER(TRIM(document_type)),TRIM(number))");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS variable_pair_memory(id INTEGER PRIMARY KEY AUTOINCREMENT,source_key TEXT NOT NULL,source_value TEXT NOT NULL,source_display TEXT NOT NULL,target_key TEXT NOT NULL,target_value TEXT NOT NULL,updated_at TEXT NOT NULL,UNIQUE(source_key,source_value,target_key))");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS template_revision(template_id INTEGER NOT NULL,version INTEGER NOT NULL,content BLOB NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(template_id,version))");
            mcS.executeUpdate(mcHistorySchema("IF NOT EXISTS document_history"));
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS field_mapping(id INTEGER PRIMARY KEY,template_id INTEGER NOT NULL,source_key TEXT NOT NULL,api_field TEXT NOT NULL,target_key TEXT NOT NULL,UNIQUE(template_id,source_key,target_key))");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS variable_api_binding(definition_id INTEGER PRIMARY KEY,service TEXT NOT NULL,path TEXT NOT NULL)");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS variable_query_context(template_id INTEGER NOT NULL,variable_key TEXT NOT NULL,group_key TEXT NOT NULL,service TEXT NOT NULL,source INTEGER NOT NULL,PRIMARY KEY(template_id,variable_key))");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS contact_binding(id INTEGER PRIMARY KEY,template_id INTEGER NOT NULL,group_key TEXT NOT NULL,kind TEXT NOT NULL,field TEXT NOT NULL,variable_key TEXT NOT NULL,UNIQUE(template_id,group_key,field))");
        }
        try(Connection mcC=mcOpen()){mcMigrateHistory(mcC);mcMigrateContact(mcC);}
        try(Connection mcC=mcOpen();Statement mcS=mcC.createStatement()){
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS client_document(id INTEGER PRIMARY KEY AUTOINCREMENT,contact_id INTEGER NOT NULL REFERENCES contact(id) ON DELETE CASCADE,title TEXT NOT NULL,file_name TEXT NOT NULL,extension TEXT NOT NULL,source_type TEXT NOT NULL CHECK(source_type IN ('GENERATED','UPLOADED')),content_bytes BLOB NOT NULL,updated_at TEXT NOT NULL,history_id INTEGER REFERENCES document_history(id) ON DELETE SET NULL,history_format TEXT,UNIQUE(contact_id,history_id,history_format))");
            mcS.executeUpdate("CREATE INDEX IF NOT EXISTS ix_client_document_contact ON client_document(contact_id)");
        }
    }

    public List<MCClientDocument> mcClientDocuments(long mcContactId)throws SQLException{
        MCContact mcContact=mcContacts().stream().filter(mcX->mcX.mcId()==mcContactId).findFirst().orElse(null);
        if(mcContact==null)return List.of();
        Set<String> mcIndependent=mcIndependentKeys();
        for(MCHistory mcHistory:mcHistory())if(mcHistoryMatches(mcContact,mcHistory,mcIndependent))mcLinkHistoryToContacts(mcHistory.mcId(),List.of(mcContactId));
        List<MCClientDocument> mcDocuments=new ArrayList<>();
        try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("SELECT * FROM client_document WHERE contact_id=? ORDER BY datetime(updated_at) DESC,id DESC")){
            mcS.setLong(1,mcContactId);try(ResultSet mcR=mcS.executeQuery()){while(mcR.next())mcDocuments.add(mcReadClientDocument(mcR));}
        }return mcDocuments;
    }
    public MCClientDocument mcAddClientDocument(long mcContactId,String mcTitle,String mcFileName,String mcExtension,byte[] mcBytes)throws SQLException{
        mcValidateClientDocument(mcTitle,mcFileName,mcExtension,mcBytes,"UPLOADED");
        try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("INSERT INTO client_document(contact_id,title,file_name,extension,source_type,content_bytes,updated_at) VALUES(?,?,?,?,'UPLOADED',?,?)",Statement.RETURN_GENERATED_KEYS)){
            String mcNow=java.time.Instant.now().toString();mcS.setLong(1,mcContactId);mcS.setString(2,mcTitle);mcS.setString(3,mcFileName);mcS.setString(4,mcExtension.toLowerCase(Locale.ROOT));mcS.setBytes(5,mcBytes);mcS.setString(6,mcNow);mcS.executeUpdate();
            try(ResultSet mcR=mcS.getGeneratedKeys()){return new MCClientDocument(mcR.next()?mcR.getLong(1):0,mcContactId,mcTitle,mcFileName,mcExtension.toLowerCase(Locale.ROOT),"UPLOADED",mcBytes,mcNow);}
        }
    }
    public void mcReplaceClientDocument(long mcDocId,String mcNewTitle,String mcNewFileName,String mcNewExtension,byte[] mcNewBytes,String mcSourceType)throws SQLException{
        mcValidateClientDocument(mcNewTitle,mcNewFileName,mcNewExtension,mcNewBytes,mcSourceType);
        try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("UPDATE client_document SET title=?,file_name=?,extension=?,content_bytes=?,source_type=?,updated_at=? WHERE id=?")){
            mcS.setString(1,mcNewTitle);mcS.setString(2,mcNewFileName);mcS.setString(3,mcNewExtension.toLowerCase(Locale.ROOT));mcS.setBytes(4,mcNewBytes);mcS.setString(5,mcSourceType);mcS.setString(6,java.time.Instant.now().toString());mcS.setLong(7,mcDocId);
            if(mcS.executeUpdate()!=1)throw new SQLException("El documento del cliente ya no existe.");
        }
    }
    private static void mcValidateClientDocument(String mcTitle,String mcFileName,String mcExtension,byte[] mcBytes,String mcSource){
        if(mcTitle==null||mcTitle.isBlank()||mcFileName==null||mcFileName.isBlank()||mcExtension==null||mcBytes==null||mcBytes.length==0)
            throw new IllegalArgumentException("Seleccione un archivo con contenido y nombre.");
        if(!Set.of("GENERATED","UPLOADED").contains(mcSource))throw new IllegalArgumentException("Origen de documento inválido.");
    }
    private static MCClientDocument mcReadClientDocument(ResultSet mcR)throws SQLException{
        return new MCClientDocument(mcR.getLong("id"),mcR.getLong("contact_id"),mcR.getString("title"),mcR.getString("file_name"),mcR.getString("extension"),mcR.getString("source_type"),mcR.getBytes("content_bytes"),mcR.getString("updated_at"));
    }
    public void mcLinkHistoryToContacts(long mcHistoryId,Collection<Long> mcContactIds)throws SQLException{
        try(Connection mcC=mcOpen()){
            mcC.setAutoCommit(false);try{
                MCHistory mcHistory;
                try(PreparedStatement mcS=mcC.prepareStatement("SELECT * FROM document_history WHERE id=?")){mcS.setLong(1,mcHistoryId);try(ResultSet mcR=mcS.executeQuery()){if(!mcR.next())return;mcHistory=mcReadHistory(mcR);}}
                for(long mcContactId:new LinkedHashSet<>(mcContactIds))for(String mcFormat:List.of("docx","pdf")){
                    byte[] mcBytes=mcFormat.equals("docx")?mcHistory.mcDocx():mcHistory.mcPdf();if(mcBytes==null||mcBytes.length==0)continue;
                    try(PreparedStatement mcS=mcC.prepareStatement("INSERT OR IGNORE INTO client_document(contact_id,title,file_name,extension,source_type,content_bytes,updated_at,history_id,history_format) VALUES(?,?,?,?,'GENERATED',?,?,?,?)")){
                        mcS.setLong(1,mcContactId);mcS.setString(2,mcHistory.mcTitle());mcS.setString(3,mcHistory.mcTitle().replaceAll("[\\\\/:*?\"<>|]","_")+"."+mcFormat);mcS.setString(4,mcFormat);mcS.setBytes(5,mcBytes);mcS.setString(6,mcHistory.mcGeneratedAt());mcS.setLong(7,mcHistoryId);mcS.setString(8,mcFormat);mcS.executeUpdate();
                    }
                }mcC.commit();
            }catch(SQLException mcE){mcC.rollback();throw mcE;}
        }
    }
    private boolean mcHistoryMatches(MCContact mcContact,MCHistory mcHistory,Set<String> mcIndependent)throws SQLException{
        try{
            var mcValues=mcJSON.readTree(mcHistory.mcValuesJson());if(mcValues==null||!mcValues.isObject())return false;
            Map<String,Map<String,String>> mcScopes=new LinkedHashMap<>();
            var mcFields=mcValues.fields();while(mcFields.hasNext()){
                var mcField=mcFields.next();if(!mcField.getValue().isValueNode())continue;
                String mcKey=MCDocumentEngine.mcBaseKey(mcField.getKey());
                if(mcIndependent.contains(mcVariableKey(mcKey)))continue;
                mcScopes.computeIfAbsent(MCDocumentEngine.mcPersonaKey(mcField.getKey()),mcIgnored->new LinkedHashMap<>()).put(mcVariableKey(mcKey),mcField.getValue().asText(""));
            }
            if(mcHistory.mcTemplateId()!=null)for(MCContactBinding mcBinding:mcContactBindings(mcHistory.mcTemplateId())){
                if(mcIndependent.contains(mcVariableKey(MCDocumentEngine.mcBaseKey(mcBinding.mcVariableKey()))))continue;
                var mcValue=mcValues.get(mcBinding.mcVariableKey());if(mcValue!=null&&mcValue.isValueNode())mcScopes.computeIfAbsent("binding:"+mcBinding.mcGroupKey(),mcIgnored->new LinkedHashMap<>()).put(mcBinding.mcField(),mcValue.asText(""));
            }
            for(Map<String,String> mcScope:mcScopes.values()){
                String mcGiven="",mcFamily="";
                for(var mcEntry:mcScope.entrySet()){
                    String mcKey=mcEntry.getKey(),mcValue=mcEntry.getValue();
                    if(Set.of("contact_id","cliente_id").contains(mcKey)&&mcValue.equals(Long.toString(mcContact.mcId())))return true;
                    if(!mcContact.mcNumber().isBlank()&&Set.of("dni","ruc","documento","numero_documento","number").contains(mcKey)&&mcNormalized(mcValue).equals(mcNormalized(mcContact.mcNumber())))return true;
                    if(!mcValue.isBlank()&&mcNormalized(mcValue).equals(mcNormalized(mcContactName(mcContact))))return true;
                    if(Set.of("nombre","nombres","given").contains(mcKey))mcGiven=mcValue;
                    if(Set.of("apellidos","family").contains(mcKey))mcFamily=mcValue;
                }
                if(!mcGiven.isBlank()&&mcNormalized(mcGiven+" "+mcFamily).equals(mcNormalized(mcContactName(mcContact))))return true;
            }return false;
        }catch(com.fasterxml.jackson.core.JsonProcessingException mcE){throw new SQLException("Valores del historial inválidos.",mcE);}
    }
    static void mcMigrateHistory(Connection mcC)throws SQLException{
        boolean mcExists=false,mcNotNull=false,mcSetNull=false;List<String> mcObjects=new ArrayList<>();
        try(Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("PRAGMA table_info(document_history)")){
            while(mcR.next()){mcExists=true;if("template_id".equals(mcR.getString("name")))mcNotNull=mcR.getInt("notnull")!=0;}
        }
        if(!mcExists)return;
        try(Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("PRAGMA foreign_key_list(document_history)")){
            while(mcR.next())if("template_id".equals(mcR.getString("from"))&&"template".equals(mcR.getString("table"))&&"SET NULL".equalsIgnoreCase(mcR.getString("on_delete")))mcSetNull=true;
        }
        if(!mcNotNull&&mcSetNull)return;
        try(Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT sql FROM sqlite_master WHERE tbl_name='document_history' AND type IN ('index','trigger') AND sql IS NOT NULL")){while(mcR.next())mcObjects.add(mcR.getString(1));}
        try(Statement mcS=mcC.createStatement()){mcS.execute("PRAGMA foreign_keys=OFF");}
        mcC.setAutoCommit(false);
        try(Statement mcS=mcC.createStatement()){
            mcS.executeUpdate(mcHistorySchema("document_history_migration"));
            mcS.executeUpdate("INSERT INTO document_history_migration("+mcHISTORY_COLUMNS+") SELECT h.id,h.title,CASE WHEN EXISTS(SELECT 1 FROM template t WHERE t.id=h.template_id) THEN h.template_id ELSE NULL END,h.template_version,h.generated_at,h.values_json,h.docx_path,h.pdf_path,h.docx,h.pdf FROM document_history h");
            mcS.executeUpdate("DROP TABLE document_history");mcS.executeUpdate("ALTER TABLE document_history_migration RENAME TO document_history");
            for(String mcSql:mcObjects)mcS.executeUpdate(mcSql);
            try(ResultSet mcCheck=mcS.executeQuery("PRAGMA foreign_key_check(document_history)")){if(mcCheck.next())throw new SQLException("El historial migrado contiene una referencia inválida.");}
            mcC.commit();
        }catch(SQLException mcE){mcC.rollback();throw mcE;}
        finally{mcC.setAutoCommit(true);try(Statement mcS=mcC.createStatement()){mcS.execute("PRAGMA foreign_keys=ON");}}
    }
    public Path mcDatabase(){return mcDb;}
    private Connection mcOpen()throws SQLException{Connection mcC=DriverManager.getConnection("jdbc:sqlite:"+mcDb);try(Statement mcS=mcC.createStatement()){mcS.execute("PRAGMA foreign_keys=ON");}return mcC;}
    public List<MCContact> mcContacts()throws SQLException{List<MCContact> mcOut=new ArrayList<>();try(Connection mcC=mcOpen();Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT * FROM contact ORDER BY kind,given,family")){while(mcR.next())mcOut.add(new MCContact(mcR.getLong("id"),mcR.getString("kind"),mcR.getString("given"),mcR.getString("family"),mcR.getString("document_type"),mcR.getString("number"),mcR.getString("address"),mcR.getString("representatives"),mcR.getString("extras_json")));}return mcOut;}
    public synchronized MCContact mcSaveContact(MCContact mcX)throws SQLException{
        try(Connection mcC=mcOpen()){
            mcC.setAutoCommit(false);
            try{
                MCContact mcExisting=mcMatchingContact(mcC,mcX);
                if(mcX.mcId()!=0&&mcExisting!=null&&mcExisting.mcId()!=mcX.mcId())throw new SQLException("Ya existe otro cliente con esa identidad.");
                long mcId=mcExisting==null?mcX.mcId():mcExisting.mcId();
                String mcSql=mcId==0?"INSERT INTO contact(kind,given,family,document_type,number,address,representatives,extras_json) VALUES(?,?,?,?,?,?,?,?)":"UPDATE contact SET kind=?,given=?,family=?,document_type=?,number=?,address=?,representatives=?,extras_json=? WHERE id=?";
                try(PreparedStatement mcS=mcC.prepareStatement(mcSql,Statement.RETURN_GENERATED_KEYS)){
                    mcBindContact(mcS,mcX);if(mcId!=0)mcS.setLong(9,mcId);mcS.executeUpdate();
                    if(mcId==0)try(ResultSet mcKeys=mcS.getGeneratedKeys()){if(mcKeys.next())mcId=mcKeys.getLong(1);}
                }
                mcC.commit();
                return new MCContact(mcId,mcX.mcKind(),mcX.mcGiven(),mcX.mcFamily(),mcX.mcDocumentType().trim().toUpperCase(Locale.ROOT),mcX.mcNumber().trim(),mcX.mcAddress(),mcX.mcRepresentatives(),mcX.mcExtrasJson());
            }catch(SQLException mcE){mcC.rollback();throw mcE;}
        }
    }
    public synchronized MCContact mcUpsertContact(MCContact mcIncoming)throws SQLException{
        MCContact mcExisting=mcMatchingContact(mcIncoming);
        return mcSaveContact(mcExisting==null?mcIncoming:mcMergeContact(mcExisting,mcIncoming));
    }
    static MCContact mcMergeContact(MCContact mcExisting,MCContact mcIncoming)throws SQLException{
        try{
            ObjectNode mcExtras=mcJSON.createObjectNode();
            for(String mcSource:List.of(mcExisting.mcExtrasJson(),mcIncoming.mcExtrasJson())){
                var mcNode=mcJSON.readTree(mcSource==null||mcSource.isBlank()?"{}":mcSource);
                if(mcNode==null||!mcNode.isObject())throw new IllegalArgumentException("Los datos adicionales deben ser un objeto JSON.");
                mcNode.fields().forEachRemaining(mcE->{if(!mcE.getValue().isNull()&&!(mcE.getValue().isTextual()&&mcE.getValue().asText().isBlank()))mcExtras.set(mcE.getKey(),mcE.getValue());});
            }
            var mcIncomingExtras=mcJSON.readTree(mcIncoming.mcExtrasJson()==null||mcIncoming.mcExtrasJson().isBlank()?"{}":mcIncoming.mcExtrasJson());
            boolean mcSurnameParts=mcIncomingExtras.hasNonNull("apellido_paterno")||mcIncomingExtras.hasNonNull("apellido_materno");
            boolean mcWholeFamily=mcIncomingExtras.hasNonNull("apellidos");
            String mcFamily=mcPrefer(mcIncoming.mcFamily(),mcExisting.mcFamily());
            if(mcSurnameParts&&!mcWholeFamily){String mcCombined=(mcExtras.path("apellido_paterno").asText("")+" "+mcExtras.path("apellido_materno").asText("")).trim();mcFamily=mcPrefer(mcCombined,mcFamily);}
            return new MCContact(mcExisting.mcId(),mcIncoming.mcKind(),mcPrefer(mcIncoming.mcGiven(),mcExisting.mcGiven()),mcFamily,mcIncoming.mcDocumentType(),mcIncoming.mcNumber(),mcPrefer(mcIncoming.mcAddress(),mcExisting.mcAddress()),mcPrefer(mcIncoming.mcRepresentatives(),mcExisting.mcRepresentatives()),mcJSON.writeValueAsString(mcExtras));
        }catch(Exception mcE){throw new SQLException("Datos adicionales de cliente inválidos.",mcE);}
    }
    private static String mcPrefer(String mcNewer,String mcOlder){return mcNewer==null||mcNewer.isBlank()?mcOlder:mcNewer;}
    private static void mcBindContact(PreparedStatement mcS,MCContact mcX)throws SQLException{mcS.setString(1,mcX.mcKind());mcS.setString(2,mcX.mcGiven());mcS.setString(3,mcX.mcFamily());mcS.setString(4,mcX.mcDocumentType().trim().toUpperCase(Locale.ROOT));mcS.setString(5,mcX.mcNumber().trim());mcS.setString(6,mcX.mcAddress());mcS.setString(7,mcX.mcRepresentatives());mcS.setString(8,mcX.mcExtrasJson());}
    public void mcDeleteContact(long mcId)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM contact WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}}
    public int mcRecordRevision(long mcTemplateId,byte[] mcContent)throws SQLException{try(Connection mcC=mcOpen()){try(PreparedStatement mcQ=mcC.prepareStatement("SELECT version,content FROM template_revision WHERE template_id=? ORDER BY version DESC LIMIT 1")){mcQ.setLong(1,mcTemplateId);try(ResultSet mcR=mcQ.executeQuery()){if(mcR.next()){int mcVersion=mcR.getInt(1);if(Arrays.equals(mcContent,mcR.getBytes(2)))return mcVersion;return mcInsertRevision(mcC,mcTemplateId,mcVersion+1,mcContent);}}}return mcInsertRevision(mcC,mcTemplateId,1,mcContent);}}
    private static int mcInsertRevision(Connection mcC,long mcId,int mcVersion,byte[] mcContent)throws SQLException{try(PreparedStatement mcS=mcC.prepareStatement("INSERT INTO template_revision(template_id,version,content) VALUES(?,?,?)")){mcS.setLong(1,mcId);mcS.setInt(2,mcVersion);mcS.setBytes(3,mcContent);mcS.executeUpdate();}return mcVersion;}
    public int mcVersion(long mcTemplateId)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("SELECT COALESCE(MAX(version),0) FROM template_revision WHERE template_id=?")){mcS.setLong(1,mcTemplateId);try(ResultSet mcR=mcS.executeQuery()){return mcR.next()?mcR.getInt(1):0;}}}
    public Map<Long,String> mcRevisionDates()throws SQLException{Map<Long,String> mcDates=new HashMap<>();try(Connection mcC=mcOpen();Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT template_id,MAX(created_at) FROM template_revision GROUP BY template_id")){while(mcR.next())mcDates.put(mcR.getLong(1),mcR.getString(2));}return mcDates;}
    public byte[] mcRevision(long mcTemplateId,int mcVersion)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("SELECT content FROM template_revision WHERE template_id=? AND version=?")){mcS.setLong(1,mcTemplateId);mcS.setInt(2,mcVersion);try(ResultSet mcR=mcS.executeQuery()){return mcR.next()?mcR.getBytes(1):null;}}}
    public List<MCHistory> mcHistory()throws SQLException{List<MCHistory> mcOut=new ArrayList<>();try(Connection mcC=mcOpen();Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT * FROM document_history ORDER BY id DESC")){while(mcR.next())mcOut.add(mcReadHistory(mcR));}return mcOut;}
    private static MCHistory mcReadHistory(ResultSet mcR)throws SQLException{long mcValue=mcR.getLong("template_id");Long mcTemplateId=mcR.wasNull()?null:mcValue;return new MCHistory(mcR.getLong("id"),mcR.getString("title"),mcTemplateId,mcR.getInt("template_version"),mcR.getString("generated_at"),mcR.getString("values_json"),mcR.getString("docx_path"),mcR.getString("pdf_path"),mcR.getBytes("docx"),mcR.getBytes("pdf"));}
    public MCHistory mcSaveHistory(MCHistory mcH)throws SQLException{
        long mcId;
        try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("INSERT INTO document_history(title,template_id,template_version,values_json,docx_path,pdf_path,docx,pdf) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            mcS.setString(1,mcH.mcTitle());if(mcH.mcTemplateId()==null)mcS.setNull(2,Types.INTEGER);else mcS.setLong(2,mcH.mcTemplateId());mcS.setInt(3,mcH.mcTemplateVersion());mcS.setString(4,mcH.mcValuesJson());mcS.setString(5,mcH.mcDocxPath());mcS.setString(6,mcH.mcPdfPath());mcS.setBytes(7,mcH.mcDocx());mcS.setBytes(8,mcH.mcPdf());mcS.executeUpdate();try(ResultSet mcR=mcS.getGeneratedKeys()){mcId=mcR.next()?mcR.getLong(1):0;}
        }
        MCHistory mcSaved=mcHistoryById(mcId);mcSynchronizeHistoryClients(mcSaved);return mcSaved;
    }
    private MCHistory mcHistoryById(long mcId)throws SQLException{
        try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("SELECT * FROM document_history WHERE id=?")){mcS.setLong(1,mcId);try(ResultSet mcR=mcS.executeQuery()){return mcR.next()?mcReadHistory(mcR):null;}}
    }
    private Set<String> mcIndependentKeys()throws SQLException{
        Set<String> mcIndependent=new HashSet<>();try(Connection mcC=mcOpen();Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT key FROM variable_definition WHERE bind_to_client=0")){while(mcR.next())mcIndependent.add(mcVariableKey(mcR.getString(1)));}return mcIndependent;
    }
    private void mcSynchronizeHistoryClients(MCHistory mcHistory)throws SQLException{
        if(mcHistory==null)return;List<MCContact> mcContacts=mcContacts();if(mcContacts.isEmpty())return;Set<String> mcIndependent=mcIndependentKeys();List<Long> mcIds=new ArrayList<>();
        for(MCContact mcContact:mcContacts)if(mcHistoryMatches(mcContact,mcHistory,mcIndependent))mcIds.add(mcContact.mcId());
        if(!mcIds.isEmpty())mcLinkHistoryToContacts(mcHistory.mcId(),mcIds);
    }
    public void mcDeleteHistory(long mcId)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM document_history WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}}
    public void mcUpdateHistoryFiles(long mcId,String mcDocxPath,String mcPdfPath,byte[] mcPdf)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("UPDATE document_history SET docx_path=COALESCE(?,docx_path),pdf_path=COALESCE(?,pdf_path),pdf=COALESCE(?,pdf) WHERE id=?")){mcS.setString(1,mcDocxPath);mcS.setString(2,mcPdfPath);mcS.setBytes(3,mcPdf);mcS.setLong(4,mcId);mcS.executeUpdate();}mcSynchronizeHistoryClients(mcHistoryById(mcId));}
    public List<MCMapping> mcMappings(long mcTemplateId)throws SQLException{List<MCMapping> mcOut=new ArrayList<>();try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("SELECT * FROM field_mapping WHERE template_id=? ORDER BY id")){mcS.setLong(1,mcTemplateId);try(ResultSet mcR=mcS.executeQuery()){while(mcR.next())mcOut.add(new MCMapping(mcR.getLong(1),mcR.getLong(2),mcR.getString(3),mcR.getString(4),mcR.getString(5)));}}return mcOut;}
    public MCMapping mcSaveMapping(MCMapping mcM)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("INSERT INTO field_mapping(template_id,source_key,api_field,target_key) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){mcS.setLong(1,mcM.mcTemplateId());mcS.setString(2,mcM.mcSourceKey());mcS.setString(3,mcM.mcApiField());mcS.setString(4,mcM.mcTargetKey());mcS.executeUpdate();try(ResultSet mcR=mcS.getGeneratedKeys()){return new MCMapping(mcR.next()?mcR.getLong(1):0,mcM.mcTemplateId(),mcM.mcSourceKey(),mcM.mcApiField(),mcM.mcTargetKey());}}}
    public void mcDeleteMapping(long mcId)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM field_mapping WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}}
    public void mcClearMappings(long mcTemplateId)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM field_mapping WHERE template_id=?")){mcS.setLong(1,mcTemplateId);mcS.executeUpdate();}}
    public MCApiBinding mcApiBinding(long mcDefinitionId)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("SELECT service,path FROM variable_api_binding WHERE definition_id=?")){mcS.setLong(1,mcDefinitionId);try(ResultSet mcR=mcS.executeQuery()){return mcR.next()?new MCApiBinding(mcDefinitionId,mcR.getString(1),mcR.getString(2)):null;}}}
    public void mcSaveApiBinding(MCApiBinding mcBinding)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("INSERT INTO variable_api_binding(definition_id,service,path) VALUES(?,?,?) ON CONFLICT(definition_id) DO UPDATE SET service=excluded.service,path=excluded.path")){mcS.setLong(1,mcBinding.mcDefinitionId());mcS.setString(2,mcBinding.mcService());mcS.setString(3,mcBinding.mcPath());mcS.executeUpdate();}}
    public void mcDeleteApiBinding(long mcDefinitionId)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM variable_api_binding WHERE definition_id=?")){mcS.setLong(1,mcDefinitionId);mcS.executeUpdate();}}
    public List<MCApiBinding> mcApiBindings()throws SQLException{List<MCApiBinding> mcOut=new ArrayList<>();try(Connection mcC=mcOpen();Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT definition_id,service,path FROM variable_api_binding")){while(mcR.next())mcOut.add(new MCApiBinding(mcR.getLong(1),mcR.getString(2),mcR.getString(3)));}return mcOut;}
    public List<MCApiContext> mcApiContexts(long mcTemplateId)throws SQLException{List<MCApiContext> mcOut=new ArrayList<>();try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("SELECT template_id,variable_key,group_key,service,source FROM variable_query_context WHERE template_id=? ORDER BY group_key,source DESC,variable_key")){mcS.setLong(1,mcTemplateId);try(ResultSet mcR=mcS.executeQuery()){while(mcR.next())mcOut.add(new MCApiContext(mcR.getLong(1),mcR.getString(2),mcR.getString(3),mcR.getString(4),mcR.getInt(5)==1));}}return mcOut;}
    public void mcSaveApiContext(MCApiContext mcContext)throws SQLException{if(mcContext.mcGroupKey()==null||mcContext.mcGroupKey().isBlank()||mcContext.mcVariableKey()==null||mcContext.mcVariableKey().isBlank()||!List.of("DNI","RUC").contains(mcContext.mcService()))throw new IllegalArgumentException("Grupo, variable o servicio inválido.");try(Connection mcC=mcOpen()){
        if(mcContext.mcSource())try(PreparedStatement mcCheck=mcC.prepareStatement("SELECT variable_key FROM variable_query_context WHERE template_id=? AND group_key=? AND service=? AND source=1 AND variable_key<>? LIMIT 1")){mcCheck.setLong(1,mcContext.mcTemplateId());mcCheck.setString(2,mcContext.mcGroupKey().trim());mcCheck.setString(3,mcContext.mcService());mcCheck.setString(4,mcContext.mcVariableKey());try(ResultSet mcR=mcCheck.executeQuery()){if(mcR.next())throw new IllegalArgumentException("El grupo ya tiene un número a consultar. Use otro grupo independiente.");}}
        try(PreparedStatement mcS=mcC.prepareStatement("INSERT INTO variable_query_context(template_id,variable_key,group_key,service,source) VALUES(?,?,?,?,?) ON CONFLICT(template_id,variable_key) DO UPDATE SET group_key=excluded.group_key,service=excluded.service,source=excluded.source")){mcS.setLong(1,mcContext.mcTemplateId());mcS.setString(2,mcContext.mcVariableKey());mcS.setString(3,mcContext.mcGroupKey().trim());mcS.setString(4,mcContext.mcService());mcS.setInt(5,mcContext.mcSource()?1:0);mcS.executeUpdate();}
    }}
    public void mcDeleteApiContext(long mcTemplateId,String mcVariableKey)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM variable_query_context WHERE template_id=? AND variable_key=?")){mcS.setLong(1,mcTemplateId);mcS.setString(2,mcVariableKey);mcS.executeUpdate();}}
    public void mcClearApiContexts(long mcTemplateId)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM variable_query_context WHERE template_id=?")){mcS.setLong(1,mcTemplateId);mcS.executeUpdate();}}
    public List<MCContactBinding> mcContactBindings(long mcTemplateId)throws SQLException{
        List<MCContactBinding> mcOut=new ArrayList<>();
        try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("SELECT * FROM contact_binding WHERE template_id=? ORDER BY group_key,id")){
            mcS.setLong(1,mcTemplateId);try(ResultSet mcR=mcS.executeQuery()){while(mcR.next())mcOut.add(new MCContactBinding(mcR.getLong("id"),mcR.getLong("template_id"),mcR.getString("group_key"),mcR.getString("kind"),mcR.getString("field"),mcR.getString("variable_key")));}
        }
        return mcOut;
    }
    public MCContactBinding mcSaveContactBinding(MCContactBinding mcB)throws SQLException{
        if(mcB.mcGroupKey()==null||mcB.mcGroupKey().isBlank()||!List.of("Persona","Empresa").contains(mcB.mcKind())||mcB.mcField()==null||mcB.mcField().isBlank()||mcB.mcVariableKey()==null||mcB.mcVariableKey().isBlank())throw new IllegalArgumentException("Asociación incompleta.");
        try(Connection mcC=mcOpen()){
            try(PreparedStatement mcCheck=mcC.prepareStatement("SELECT kind FROM contact_binding WHERE template_id=? AND group_key=? LIMIT 1")){
                mcCheck.setLong(1,mcB.mcTemplateId());mcCheck.setString(2,mcB.mcGroupKey());try(ResultSet mcR=mcCheck.executeQuery()){if(mcR.next()&&!mcR.getString(1).equals(mcB.mcKind()))throw new IllegalArgumentException("El grupo ya tiene otro tipo de registro.");}
            }
            try(PreparedStatement mcCheck=mcC.prepareStatement("SELECT group_key,field FROM contact_binding WHERE template_id=? AND variable_key=? LIMIT 1")){
                mcCheck.setLong(1,mcB.mcTemplateId());mcCheck.setString(2,mcB.mcVariableKey());try(ResultSet mcR=mcCheck.executeQuery()){if(mcR.next())throw new IllegalArgumentException("La variable ya está asociada a "+mcR.getString(1)+" / "+mcR.getString(2)+". Use una variable independiente para otro registro.");}
            }
            try(PreparedStatement mcS=mcC.prepareStatement("INSERT INTO contact_binding(template_id,group_key,kind,field,variable_key) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                mcS.setLong(1,mcB.mcTemplateId());mcS.setString(2,mcB.mcGroupKey());mcS.setString(3,mcB.mcKind());mcS.setString(4,mcB.mcField());mcS.setString(5,mcB.mcVariableKey());mcS.executeUpdate();try(ResultSet mcR=mcS.getGeneratedKeys()){return new MCContactBinding(mcR.next()?mcR.getLong(1):0,mcB.mcTemplateId(),mcB.mcGroupKey(),mcB.mcKind(),mcB.mcField(),mcB.mcVariableKey());}
            }
        }
    }
    public void mcDeleteContactBinding(long mcId)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM contact_binding WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}}
    public void mcClearContactBindings(long mcTemplateId)throws SQLException{try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM contact_binding WHERE template_id=?")){mcS.setLong(1,mcTemplateId);mcS.executeUpdate();}}
    public MCContact mcMatchingContact(String mcKind,String mcDocumentType,String mcNumber)throws SQLException{
        try(Connection mcC=mcOpen()){return mcMatchingContact(mcC,mcKind,mcDocumentType,mcNumber);}
    }
    private static MCContact mcMatchingContact(Connection mcC,String mcKind,String mcDocumentType,String mcNumber)throws SQLException{
        if(mcNumber==null||mcNumber.isBlank())return null;
        try(PreparedStatement mcS=mcC.prepareStatement("SELECT * FROM contact WHERE kind=? AND UPPER(TRIM(document_type))=UPPER(?) AND TRIM(number)=? ORDER BY id LIMIT 1")){
            mcS.setString(1,mcKind);mcS.setString(2,mcDocumentType==null?"":mcDocumentType.trim());mcS.setString(3,mcNumber.trim());
            try(ResultSet mcR=mcS.executeQuery()){return mcR.next()?mcReadContact(mcR):null;}
        }
    }
    public MCContact mcFindContactByNumber(String mcNumber)throws SQLException{
        if(mcNumber==null||mcNumber.isBlank())return null;
        try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement("SELECT * FROM contact WHERE TRIM(number)=? LIMIT 1")){
            mcS.setString(1,mcNumber.trim());
            try(ResultSet mcR=mcS.executeQuery()){return mcR.next()?mcReadContact(mcR):null;}
        }
    }
    private static MCContact mcReadContact(ResultSet mcR)throws SQLException{return new MCContact(mcR.getLong("id"),mcR.getString("kind"),mcR.getString("given"),mcR.getString("family"),mcR.getString("document_type"),mcR.getString("number"),mcR.getString("address"),mcR.getString("representatives"),mcR.getString("extras_json"));}    /** @author MC */ public record MCSuggestion(@com.fasterxml.jackson.annotation.JsonProperty("value") String mcValue,@com.fasterxml.jackson.annotation.JsonProperty("complements") Map<String,String> mcComplements,@com.fasterxml.jackson.annotation.JsonProperty("origin") String mcOrigin){}
    public static String mcNormalized(String mcValue){return java.text.Normalizer.normalize(mcValue==null?"":mcValue,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    public static String mcVariableKey(String mcValue){return mcNormalized(mcValue).replace(' ','_');}
    public static boolean mcPersonVariable(String mcKey){String mcK=mcVariableKey(mcKey);return Set.of("nombre","nombres","nombres_com","nombres_completo","nombres_completos","nombre_completo","nombre_cliente","razon_social","persona","cliente","solicitante","dirigido","destinatario","beneficiario").contains(mcK)||mcK.startsWith("dirigido_")||mcK.startsWith("solicitante_");}
    private static String mcContactName(MCContact mcC){return (mcC.mcGiven()+" "+mcC.mcFamily()).trim();}
    public MCContact mcMatchingContact(MCContact mcIncoming)throws SQLException{
        try(Connection mcC=mcOpen()){return mcMatchingContact(mcC,mcIncoming);}
    }
    private static MCContact mcMatchingContact(Connection mcC,MCContact mcIncoming)throws SQLException{
        if(mcIncoming.mcNumber()!=null&&!mcIncoming.mcNumber().isBlank())return mcMatchingContact(mcC,mcIncoming.mcKind(),mcIncoming.mcDocumentType(),mcIncoming.mcNumber());
        String mcName=mcNormalized(mcContactName(mcIncoming));if(mcName.isEmpty())return null;
        try(PreparedStatement mcS=mcC.prepareStatement("SELECT * FROM contact WHERE kind=? AND TRIM(number)='' ORDER BY id")){mcS.setString(1,mcIncoming.mcKind());try(ResultSet mcR=mcS.executeQuery()){while(mcR.next()){MCContact mcFound=mcReadContact(mcR);if(mcNormalized(mcContactName(mcFound)).equals(mcName))return mcFound;}}}return null;
    }
    public List<MCContact> mcFindContacts(String mcQuery)throws SQLException{
        String mcQ=mcNormalized(mcQuery);List<MCContact> mcOut=new ArrayList<>();for(MCContact mcC:mcContacts()){
            if(mcNormalized(mcContactName(mcC)+" "+mcC.mcNumber()+" "+mcC.mcAddress()).contains(mcQ)){mcOut.add(mcC);continue;}
            try{var mcNode=mcJSON.readTree(mcC.mcExtrasJson());if(mcNode!=null&&mcExtraContains(mcNode,mcQ))mcOut.add(mcC);}catch(Exception mcE){throw new SQLException("Datos adicionales de cliente inválidos.",mcE);}
        }return mcOut;
    }
    private static boolean mcExtraContains(com.fasterxml.jackson.databind.JsonNode mcNode,String mcQuery){if(mcNode.isValueNode())return mcNormalized(mcNode.asText()).contains(mcQuery);for(var mcChild:mcNode)if(mcExtraContains(mcChild,mcQuery))return true;return false;}
    public List<MCContact> mcFindContactsByVariable(String mcKey,String mcValue,boolean mcPrefix)throws SQLException{
        String mcQ=mcNormalized(mcValue);List<MCContact> mcOut=new ArrayList<>();for(MCContact mcContact:mcContacts()){String mcFound=mcNormalized(mcContactValue(mcContact,mcKey));if(!mcFound.isEmpty()&&(mcPrefix?mcFound.startsWith(mcQ):mcFound.equals(mcQ)))mcOut.add(mcContact);}return mcOut;
    }
    public String mcContactValue(MCContact mcContact,String mcKey)throws SQLException{
        String mcNormalizedKey=mcVariableKey(mcKey);
        try{var mcExtras=mcJSON.readTree(mcContact.mcExtrasJson());if(mcExtras!=null){var mcFields=mcExtras.fields();while(mcFields.hasNext()){var mcField=mcFields.next();if(mcVariableKey(mcField.getKey()).equals(mcNormalizedKey)&&mcField.getValue().isValueNode())return mcField.getValue().asText("");}}}catch(Exception mcE){throw new SQLException("Datos adicionales inválidos.",mcE);}
        if(mcPersonVariable(mcKey))return mcContactName(mcContact);
        return switch(mcNormalizedKey){case "dni","ruc","documento","number"->mcContact.mcNumber();case "direccion","domicilio","address"->mcContact.mcAddress();case "apellidos","family"->mcContact.mcFamily();default->"";};
    }
    public void mcRememberPair(String mcSourceKey,String mcSourceValue,String mcTargetKey,String mcTargetValue,boolean mcBidirectional)throws SQLException{
        if(mcSourceValue==null||mcSourceValue.isBlank()||mcTargetValue==null||mcTargetValue.isBlank())return;
        try(Connection mcC=mcOpen()){
            mcC.setAutoCommit(false);try{
                if(mcBidirectional)try(PreparedStatement mcPrevious=mcC.prepareStatement("SELECT source_display,target_value FROM variable_pair_memory WHERE source_key=? AND source_value=? AND target_key=?")){
                    mcPrevious.setString(1,mcVariableKey(mcSourceKey));mcPrevious.setString(2,mcNormalized(mcSourceValue));mcPrevious.setString(3,mcVariableKey(mcTargetKey));
                    try(ResultSet mcR=mcPrevious.executeQuery()){if(mcR.next()&&!mcNormalized(mcR.getString(2)).equals(mcNormalized(mcTargetValue)))try(PreparedStatement mcRemove=mcC.prepareStatement("DELETE FROM variable_pair_memory WHERE source_key=? AND source_value=? AND target_key=? AND target_value=?")){mcRemove.setString(1,mcVariableKey(mcTargetKey));mcRemove.setString(2,mcNormalized(mcR.getString(2)));mcRemove.setString(3,mcVariableKey(mcSourceKey));mcRemove.setString(4,mcR.getString(1));mcRemove.executeUpdate();}}
                }
                mcWritePair(mcC,mcSourceKey,mcSourceValue,mcTargetKey,mcTargetValue);if(mcBidirectional)mcWritePair(mcC,mcTargetKey,mcTargetValue,mcSourceKey,mcSourceValue);mcC.commit();}catch(SQLException mcE){mcC.rollback();throw mcE;}
        }
    }
    private static void mcWritePair(Connection mcC,String mcSource,String mcValue,String mcTarget,String mcResult)throws SQLException{
        try(PreparedStatement mcS=mcC.prepareStatement("INSERT INTO variable_pair_memory(source_key,source_value,source_display,target_key,target_value,updated_at) VALUES(?,?,?,?,?,?) ON CONFLICT(source_key,source_value,target_key) DO UPDATE SET source_display=excluded.source_display,target_value=excluded.target_value,updated_at=excluded.updated_at")){
            mcS.setString(1,mcVariableKey(mcSource));mcS.setString(2,mcNormalized(mcValue));mcS.setString(3,mcValue);mcS.setString(4,mcVariableKey(mcTarget));mcS.setString(5,mcResult);mcS.setString(6,java.time.Instant.now().toString());mcS.executeUpdate();
        }
    }
    public List<MCSuggestion> mcSuggestions(String mcSourceKey,Collection<String> mcTargetKeys,String mcValue,boolean mcExact)throws SQLException{
        if(mcValue==null||mcValue.isBlank()||mcTargetKeys.isEmpty())return List.of();
        Set<String> mcTargets=new LinkedHashSet<>();mcTargetKeys.forEach(mcK->mcTargets.add(mcVariableKey(mcK)));Map<String,String> mcDisplay=new LinkedHashMap<>(),mcOrigins=new HashMap<>();Map<String,Map<String,String>> mcResults=new LinkedHashMap<>();String mcQ=mcNormalized(mcValue);
        String mcSql="SELECT source_display,target_key,target_value FROM variable_pair_memory WHERE source_key=? AND source_value "+(mcExact?"=?":"LIKE ? ESCAPE '\\'")+" ORDER BY updated_at DESC,id DESC";
        try(Connection mcC=mcOpen();PreparedStatement mcS=mcC.prepareStatement(mcSql)){
            mcS.setString(1,mcVariableKey(mcSourceKey));mcS.setString(2,mcExact?mcQ:mcQ.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");
            try(ResultSet mcR=mcS.executeQuery()){while(mcR.next()){String mcTarget=mcR.getString(2);if(!mcTargets.contains(mcTarget))continue;String mcOriginal=mcR.getString(1),mcKey=mcNormalized(mcOriginal);mcDisplay.putIfAbsent(mcKey,mcOriginal);mcOrigins.putIfAbsent(mcKey,"Memoria local");mcResults.computeIfAbsent(mcKey,mcK->new LinkedHashMap<>()).putIfAbsent(mcTarget,mcR.getString(3));}}
        }
        for(MCContact mcContact:mcFindContactsByVariable(mcSourceKey,mcValue,!mcExact)){
            String mcOriginal=mcContactValue(mcContact,mcSourceKey),mcKey=mcNormalized(mcOriginal);Map<String,String> mcComplements=new LinkedHashMap<>();for(String mcTarget:mcTargets){String mcFound=mcContactValue(mcContact,mcTarget);if(!mcFound.isBlank())mcComplements.put(mcTarget,mcFound);}if(mcComplements.isEmpty())continue;
            Map<String,String> mcKnown=mcResults.get(mcKey);if(mcKnown!=null&&mcComplements.entrySet().stream().anyMatch(mcE->mcKnown.containsKey(mcE.getKey())&&!mcKnown.get(mcE.getKey()).equals(mcE.getValue())))mcKey=mcKey+"#contact-"+mcContact.mcId();
            mcDisplay.putIfAbsent(mcKey,mcOriginal);mcOrigins.putIfAbsent(mcKey,"Clientes");Map<String,String> mcRow=mcResults.computeIfAbsent(mcKey,mcK->new LinkedHashMap<>());mcComplements.forEach(mcRow::putIfAbsent);
        }
        List<MCSuggestion> mcOut=new ArrayList<>();for(var mcEntry:mcResults.entrySet()){mcOut.add(new MCSuggestion(mcDisplay.get(mcEntry.getKey()),Map.copyOf(mcEntry.getValue()),mcOrigins.get(mcEntry.getKey())));if(mcOut.size()==10)break;}return mcOut;
    }
    public void mcLearnComplements(List<MCDocumentStore.MCDefinition> mcDefinitions,List<MCDocumentStore.MCComplement> mcLinks,Map<String,String> mcValues)throws SQLException{
        Map<Long,MCDocumentStore.MCDefinition> mcDefs=new HashMap<>();mcDefinitions.forEach(mcD->mcDefs.put(mcD.mcId(),mcD));Map<String,String> mcNormalizedValues=new HashMap<>();mcValues.forEach((mcK,mcV)->mcNormalizedValues.put(mcVariableKey(mcK),mcV));
        for(var mcLink:mcLinks){var mcSource=mcDefs.get(mcLink.mcSourceDefId());var mcTarget=mcDefs.get(mcLink.mcTargetDefId());if(mcSource==null||mcTarget==null)continue;String mcA=mcNormalizedValues.get(mcVariableKey(mcSource.mcKey())),mcB=mcNormalizedValues.get(mcVariableKey(mcTarget.mcKey()));if(mcA==null||mcB==null||mcA.isBlank()||mcB.isBlank())continue;
            mcRememberPair(mcSource.mcKey(),mcA,mcTarget.mcKey(),mcB,mcLink.mcBidirectional());
            if(mcPersonVariable(mcSource.mcKey()))mcLearnPerson(mcSource.mcKey(),mcA,mcTarget.mcKey(),mcB);
            else if(mcPersonVariable(mcTarget.mcKey()))mcLearnPerson(mcTarget.mcKey(),mcB,mcSource.mcKey(),mcA);
        }
    }
    private void mcLearnPerson(String mcNameKey,String mcName,String mcOtherKey,String mcValue)throws SQLException{
        MCContact mcExisting=mcContacts().stream().filter(mcC->mcNormalized(mcContactName(mcC)).equals(mcNormalized(mcName))).findFirst().orElse(null);
        try{ObjectNode mcExtras=mcJSON.createObjectNode();mcExtras.put(mcVariableKey(mcNameKey),mcName);mcExtras.put(mcVariableKey(mcOtherKey),mcValue);
            MCContact mcIncoming=mcExisting==null?new MCContact(0,"razon_social".equals(mcVariableKey(mcNameKey))?"Empresa":"Persona",mcName,"","","","","",mcExtras.toString()):new MCContact(mcExisting.mcId(),mcExisting.mcKind(),mcExisting.mcGiven(),mcExisting.mcFamily(),mcExisting.mcDocumentType(),mcExisting.mcNumber(),mcExisting.mcAddress(),mcExisting.mcRepresentatives(),mcExtras.toString());
            mcUpsertContact(mcIncoming);
        }catch(Exception mcE){if(mcE instanceof SQLException mcSql)throw mcSql;throw new SQLException("No se pudo actualizar el cliente del complemento.",mcE);}
    }
    private static void mcMigrateContact(Connection mcC)throws SQLException{
        List<String> mcIndexes=new ArrayList<>();boolean mcRebuild=false;
        try(Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("PRAGMA index_list(contact)")){while(mcR.next())if(mcR.getInt("unique")!=0)mcIndexes.add(mcR.getString("name"));}
        List<String> mcDrop=new ArrayList<>();for(String mcIndex:mcIndexes){List<String> mcColumns=new ArrayList<>();try(PreparedStatement mcS=mcC.prepareStatement("SELECT name FROM pragma_index_info(?)")){mcS.setString(1,mcIndex);try(ResultSet mcR=mcS.executeQuery()){while(mcR.next())mcColumns.add(mcR.getString(1));}}
            if(mcColumns.equals(List.of("kind","document_type","number"))){if(mcIndex.startsWith("sqlite_autoindex"))mcRebuild=true;else mcDrop.add(mcIndex);}
        }
        if(!mcRebuild&&mcDrop.isEmpty())return;
        List<String> mcObjects=new ArrayList<>();try(Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT name,sql FROM sqlite_master WHERE tbl_name='contact' AND type IN ('index','trigger') AND sql IS NOT NULL")){while(mcR.next())if(!mcDrop.contains(mcR.getString(1)))mcObjects.add(mcR.getString(2));}
        mcC.setAutoCommit(false);try(Statement mcS=mcC.createStatement()){
            if(mcRebuild){mcS.executeUpdate("CREATE TABLE contact_migration(id INTEGER PRIMARY KEY,kind TEXT NOT NULL,given TEXT NOT NULL,family TEXT NOT NULL DEFAULT '',document_type TEXT NOT NULL DEFAULT '',number TEXT NOT NULL DEFAULT '',address TEXT NOT NULL DEFAULT '',representatives TEXT NOT NULL DEFAULT '',extras_json TEXT NOT NULL DEFAULT '{}')");mcS.executeUpdate("INSERT INTO contact_migration SELECT id,kind,given,family,document_type,number,address,representatives,extras_json FROM contact");mcS.executeUpdate("DROP TABLE contact");mcS.executeUpdate("ALTER TABLE contact_migration RENAME TO contact");for(String mcSql:mcObjects)mcS.executeUpdate(mcSql);}
            else for(String mcIndex:mcDrop)mcS.executeUpdate("DROP INDEX \""+mcIndex.replace("\"","\"\"")+"\"");mcC.commit();
        }catch(SQLException mcE){mcC.rollback();throw mcE;}finally{mcC.setAutoCommit(true);}
    }

}
