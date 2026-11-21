package com.ejemplo.crudpersona.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Additional local records. Connections are short lived so backups can safely replace the database. */
public final class StudioData {
    private static final ObjectMapper JSON=new ObjectMapper();
    public record Contact(long id,String kind,String given,String family,String documentType,String number,
                          String address,String representatives,String extrasJson) {
        @Override public String toString(){return ("Empresa".equals(kind)?given:given+" "+family)+" · "+number;}
    }
    public record History(long id,String title,Long templateId,int templateVersion,String generatedAt,
                          String valuesJson,String docxPath,String pdfPath,byte[] docx,byte[] pdf){
        @Override public String toString(){return generatedAt+" · "+title;}
    }
    public record ClientDocument(long id,long contactId,String title,String fileName,String extension,
                                 String sourceType,byte[] contentBytes,String updatedAt) {}
    public record Mapping(long id,long templateId,String sourceKey,String apiField,String targetKey){
        @Override public String toString(){return sourceKey+" → "+apiField+" → "+targetKey;}
    }
    public record ApiBinding(long definitionId,String service,String path){}
    public record ApiContext(long templateId,String variableKey,String groupKey,String service,boolean source){
        @Override public String toString(){return groupKey+" · "+service+" · "+variableKey+(source?" (número a consultar)":"");}
    }
    public record ContactBinding(long id,long templateId,String groupKey,String kind,String field,String variableKey){
        @Override public String toString(){return groupKey+" · "+kind+" · "+field+" → {{"+variableKey+"}}";}
    }
    private final Path db;
    private static final String HISTORY_COLUMNS="id,title,template_id,template_version,generated_at,values_json,docx_path,pdf_path,docx,pdf";
    private static String historySchema(String table){return "CREATE TABLE "+table+"(id INTEGER PRIMARY KEY,title TEXT NOT NULL,template_id INTEGER REFERENCES template(id) ON DELETE SET NULL,template_version INTEGER NOT NULL,generated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,values_json TEXT NOT NULL,docx_path TEXT,pdf_path TEXT,docx BLOB NOT NULL,pdf BLOB)";}
    public StudioData(Path directory)throws SQLException{
        db=directory.resolve("studio.db");
        try(Connection c=open();Statement s=c.createStatement()){
            s.executeUpdate("CREATE TABLE IF NOT EXISTS contact(id INTEGER PRIMARY KEY,kind TEXT NOT NULL,given TEXT NOT NULL,family TEXT NOT NULL DEFAULT '',document_type TEXT NOT NULL DEFAULT '',number TEXT NOT NULL DEFAULT '',address TEXT NOT NULL DEFAULT '',representatives TEXT NOT NULL DEFAULT '',extras_json TEXT NOT NULL DEFAULT '{}')");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS ix_contact_number ON contact(number)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS ix_contact_identity ON contact(kind,UPPER(TRIM(document_type)),TRIM(number))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS variable_pair_memory(id INTEGER PRIMARY KEY AUTOINCREMENT,source_key TEXT NOT NULL,source_value TEXT NOT NULL,source_display TEXT NOT NULL,target_key TEXT NOT NULL,target_value TEXT NOT NULL,updated_at TEXT NOT NULL,UNIQUE(source_key,source_value,target_key))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS template_revision(template_id INTEGER NOT NULL,version INTEGER NOT NULL,content BLOB NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(template_id,version))");
            s.executeUpdate(historySchema("IF NOT EXISTS document_history"));
            s.executeUpdate("CREATE TABLE IF NOT EXISTS field_mapping(id INTEGER PRIMARY KEY,template_id INTEGER NOT NULL,source_key TEXT NOT NULL,api_field TEXT NOT NULL,target_key TEXT NOT NULL,UNIQUE(template_id,source_key,target_key))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS variable_api_binding(definition_id INTEGER PRIMARY KEY,service TEXT NOT NULL,path TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS variable_query_context(template_id INTEGER NOT NULL,variable_key TEXT NOT NULL,group_key TEXT NOT NULL,service TEXT NOT NULL,source INTEGER NOT NULL,PRIMARY KEY(template_id,variable_key))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS contact_binding(id INTEGER PRIMARY KEY,template_id INTEGER NOT NULL,group_key TEXT NOT NULL,kind TEXT NOT NULL,field TEXT NOT NULL,variable_key TEXT NOT NULL,UNIQUE(template_id,group_key,field))");
        }
        try(Connection c=open()){migrateHistory(c);migrateContact(c);}
        try(Connection c=open();Statement s=c.createStatement()){
            s.executeUpdate("CREATE TABLE IF NOT EXISTS client_document(id INTEGER PRIMARY KEY AUTOINCREMENT,contact_id INTEGER NOT NULL REFERENCES contact(id) ON DELETE CASCADE,title TEXT NOT NULL,file_name TEXT NOT NULL,extension TEXT NOT NULL,source_type TEXT NOT NULL CHECK(source_type IN ('GENERATED','UPLOADED')),content_bytes BLOB NOT NULL,updated_at TEXT NOT NULL,history_id INTEGER REFERENCES document_history(id) ON DELETE SET NULL,history_format TEXT,UNIQUE(contact_id,history_id,history_format))");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS ix_client_document_contact ON client_document(contact_id)");
        }
    }

    /** Import historic snapshots once. Their immutable source identity survives replacements. */
    public List<ClientDocument> clientDocuments(long contactId)throws SQLException{
        Contact contact=contacts().stream().filter(x->x.id()==contactId).findFirst().orElse(null);
        if(contact==null)return List.of();
        Set<String> independent=independentKeys();
        for(History history:history())if(historyMatches(contact,history,independent))linkHistoryToContacts(history.id(),List.of(contactId));
        List<ClientDocument> documents=new ArrayList<>();
        try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT * FROM client_document WHERE contact_id=? ORDER BY datetime(updated_at) DESC,id DESC")){
            s.setLong(1,contactId);try(ResultSet r=s.executeQuery()){while(r.next())documents.add(readClientDocument(r));}
        }return documents;
    }
    public ClientDocument addClientDocument(long contactId,String title,String fileName,String extension,byte[] bytes)throws SQLException{
        validateClientDocument(title,fileName,extension,bytes,"UPLOADED");
        try(Connection c=open();PreparedStatement s=c.prepareStatement("INSERT INTO client_document(contact_id,title,file_name,extension,source_type,content_bytes,updated_at) VALUES(?,?,?,?,'UPLOADED',?,?)",Statement.RETURN_GENERATED_KEYS)){
            String now=java.time.Instant.now().toString();s.setLong(1,contactId);s.setString(2,title);s.setString(3,fileName);s.setString(4,extension.toLowerCase(Locale.ROOT));s.setBytes(5,bytes);s.setString(6,now);s.executeUpdate();
            try(ResultSet r=s.getGeneratedKeys()){return new ClientDocument(r.next()?r.getLong(1):0,contactId,title,fileName,extension.toLowerCase(Locale.ROOT),"UPLOADED",bytes,now);}
        }
    }
    public void replaceClientDocument(long docId,String newTitle,String newFileName,String newExtension,byte[] newBytes,String sourceType)throws SQLException{
        validateClientDocument(newTitle,newFileName,newExtension,newBytes,sourceType);
        try(Connection c=open();PreparedStatement s=c.prepareStatement("UPDATE client_document SET title=?,file_name=?,extension=?,content_bytes=?,source_type=?,updated_at=? WHERE id=?")){
            s.setString(1,newTitle);s.setString(2,newFileName);s.setString(3,newExtension.toLowerCase(Locale.ROOT));s.setBytes(4,newBytes);s.setString(5,sourceType);s.setString(6,java.time.Instant.now().toString());s.setLong(7,docId);
            if(s.executeUpdate()!=1)throw new SQLException("El documento del cliente ya no existe.");
        }
    }
    private static void validateClientDocument(String title,String fileName,String extension,byte[] bytes,String source){
        if(title==null||title.isBlank()||fileName==null||fileName.isBlank()||extension==null||bytes==null||bytes.length==0)
            throw new IllegalArgumentException("Seleccione un archivo con contenido y nombre.");
        if(!Set.of("GENERATED","UPLOADED").contains(source))throw new IllegalArgumentException("Origen de documento inválido.");
    }
    private static ClientDocument readClientDocument(ResultSet r)throws SQLException{
        return new ClientDocument(r.getLong("id"),r.getLong("contact_id"),r.getString("title"),r.getString("file_name"),r.getString("extension"),r.getString("source_type"),r.getBytes("content_bytes"),r.getString("updated_at"));
    }
    public void linkHistoryToContacts(long historyId,Collection<Long> contactIds)throws SQLException{
        try(Connection c=open()){
            c.setAutoCommit(false);try{
                History history;
                try(PreparedStatement s=c.prepareStatement("SELECT * FROM document_history WHERE id=?")){s.setLong(1,historyId);try(ResultSet r=s.executeQuery()){if(!r.next())return;history=readHistory(r);}}
                for(long contactId:new LinkedHashSet<>(contactIds))for(String format:List.of("docx","pdf")){
                    byte[] bytes=format.equals("docx")?history.docx():history.pdf();if(bytes==null||bytes.length==0)continue;
                    try(PreparedStatement s=c.prepareStatement("INSERT OR IGNORE INTO client_document(contact_id,title,file_name,extension,source_type,content_bytes,updated_at,history_id,history_format) VALUES(?,?,?,?,'GENERATED',?,?,?,?)")){
                        s.setLong(1,contactId);s.setString(2,history.title());s.setString(3,history.title().replaceAll("[\\\\/:*?\"<>|]","_")+"."+format);s.setString(4,format);s.setBytes(5,bytes);s.setString(6,history.generatedAt());s.setLong(7,historyId);s.setString(8,format);s.executeUpdate();
                    }
                }c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }
    }
    private boolean historyMatches(Contact contact,History history,Set<String> independent)throws SQLException{
        try{
            var values=JSON.readTree(history.valuesJson());if(values==null||!values.isObject())return false;
            Map<String,Map<String,String>> scopes=new LinkedHashMap<>();
            var fields=values.fields();while(fields.hasNext()){
                var field=fields.next();if(!field.getValue().isValueNode())continue;
                String key=DocumentEngine.baseKey(field.getKey());
                // Independent fields must never identify a person in a historic document.
                if(independent.contains(variableKey(key)))continue;
                scopes.computeIfAbsent(DocumentEngine.personaKey(field.getKey()),ignored->new LinkedHashMap<>()).put(variableKey(key),field.getValue().asText(""));
            }
            if(history.templateId()!=null)for(ContactBinding binding:contactBindings(history.templateId())){
                if(independent.contains(variableKey(DocumentEngine.baseKey(binding.variableKey()))))continue;
                var value=values.get(binding.variableKey());if(value!=null&&value.isValueNode())scopes.computeIfAbsent("binding:"+binding.groupKey(),ignored->new LinkedHashMap<>()).put(binding.field(),value.asText(""));
            }
            for(Map<String,String> scope:scopes.values()){
                String given="",family="";
                for(var entry:scope.entrySet()){
                    String key=entry.getKey(),value=entry.getValue();
                    if(Set.of("contact_id","cliente_id").contains(key)&&value.equals(Long.toString(contact.id())))return true;
                    if(!contact.number().isBlank()&&Set.of("dni","ruc","documento","numero_documento","number").contains(key)&&normalized(value).equals(normalized(contact.number())))return true;
                    if(!value.isBlank()&&normalized(value).equals(normalized(contactName(contact))))return true;
                    if(Set.of("nombre","nombres","given").contains(key))given=value;
                    if(Set.of("apellidos","family").contains(key))family=value;
                }
                if(!given.isBlank()&&normalized(given+" "+family).equals(normalized(contactName(contact))))return true;
            }return false;
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new SQLException("Valores del historial inválidos.",e);}
    }
    /** Rebuild old NOT NULL / RESTRICT schemas without changing any document snapshot. */
    static void migrateHistory(Connection c)throws SQLException{
        boolean exists=false,notNull=false,setNull=false;List<String> objects=new ArrayList<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA table_info(document_history)")){
            while(r.next()){exists=true;if("template_id".equals(r.getString("name")))notNull=r.getInt("notnull")!=0;}
        }
        if(!exists)return;
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA foreign_key_list(document_history)")){
            while(r.next())if("template_id".equals(r.getString("from"))&&"template".equals(r.getString("table"))&&"SET NULL".equalsIgnoreCase(r.getString("on_delete")))setNull=true;
        }
        if(!notNull&&setNull)return;
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT sql FROM sqlite_master WHERE tbl_name='document_history' AND type IN ('index','trigger') AND sql IS NOT NULL")){while(r.next())objects.add(r.getString(1));}
        try(Statement s=c.createStatement()){s.execute("PRAGMA foreign_keys=OFF");}
        c.setAutoCommit(false);
        try(Statement s=c.createStatement()){
            s.executeUpdate(historySchema("document_history_migration"));
            s.executeUpdate("INSERT INTO document_history_migration("+HISTORY_COLUMNS+") SELECT h.id,h.title,CASE WHEN EXISTS(SELECT 1 FROM template t WHERE t.id=h.template_id) THEN h.template_id ELSE NULL END,h.template_version,h.generated_at,h.values_json,h.docx_path,h.pdf_path,h.docx,h.pdf FROM document_history h");
            s.executeUpdate("DROP TABLE document_history");s.executeUpdate("ALTER TABLE document_history_migration RENAME TO document_history");
            for(String sql:objects)s.executeUpdate(sql);
            try(ResultSet check=s.executeQuery("PRAGMA foreign_key_check(document_history)")){if(check.next())throw new SQLException("El historial migrado contiene una referencia inválida.");}
            c.commit();
        }catch(SQLException e){c.rollback();throw e;}
        finally{c.setAutoCommit(true);try(Statement s=c.createStatement()){s.execute("PRAGMA foreign_keys=ON");}}
    }
    public Path database(){return db;}
    private Connection open()throws SQLException{Connection c=DriverManager.getConnection("jdbc:sqlite:"+db);try(Statement s=c.createStatement()){s.execute("PRAGMA foreign_keys=ON");}return c;}
    public List<Contact> contacts()throws SQLException{List<Contact> out=new ArrayList<>();try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM contact ORDER BY kind,given,family")){while(r.next())out.add(new Contact(r.getLong("id"),r.getString("kind"),r.getString("given"),r.getString("family"),r.getString("document_type"),r.getString("number"),r.getString("address"),r.getString("representatives"),r.getString("extras_json")));}return out;}
    public synchronized Contact saveContact(Contact x)throws SQLException{
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                Contact existing=matchingContact(c,x);
                if(x.id()!=0&&existing!=null&&existing.id()!=x.id())throw new SQLException("Ya existe otro cliente con esa identidad.");
                long id=existing==null?x.id():existing.id();
                String sql=id==0?"INSERT INTO contact(kind,given,family,document_type,number,address,representatives,extras_json) VALUES(?,?,?,?,?,?,?,?)":"UPDATE contact SET kind=?,given=?,family=?,document_type=?,number=?,address=?,representatives=?,extras_json=? WHERE id=?";
                try(PreparedStatement s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
                    bindContact(s,x);if(id!=0)s.setLong(9,id);s.executeUpdate();
                    if(id==0)try(ResultSet keys=s.getGeneratedKeys()){if(keys.next())id=keys.getLong(1);}
                }
                c.commit();
                return new Contact(id,x.kind(),x.given(),x.family(),x.documentType().trim().toUpperCase(Locale.ROOT),x.number().trim(),x.address(),x.representatives(),x.extrasJson());
            }catch(SQLException e){c.rollback();throw e;}
        }
    }
    /** Merge non-empty automatic input while retaining data absent from partial responses. */
    public synchronized Contact upsertContact(Contact incoming)throws SQLException{
        Contact existing=matchingContact(incoming);
        return saveContact(existing==null?incoming:mergeContact(existing,incoming));
    }
    static Contact mergeContact(Contact existing,Contact incoming)throws SQLException{
        try{
            ObjectNode extras=JSON.createObjectNode();
            for(String source:List.of(existing.extrasJson(),incoming.extrasJson())){
                var node=JSON.readTree(source==null||source.isBlank()?"{}":source);
                if(node==null||!node.isObject())throw new IllegalArgumentException("Los datos adicionales deben ser un objeto JSON.");
                node.fields().forEachRemaining(e->{if(!e.getValue().isNull()&&!(e.getValue().isTextual()&&e.getValue().asText().isBlank()))extras.set(e.getKey(),e.getValue());});
            }
            var incomingExtras=JSON.readTree(incoming.extrasJson()==null||incoming.extrasJson().isBlank()?"{}":incoming.extrasJson());
            boolean surnameParts=incomingExtras.hasNonNull("apellido_paterno")||incomingExtras.hasNonNull("apellido_materno");
            boolean wholeFamily=incomingExtras.hasNonNull("apellidos");
            String family=prefer(incoming.family(),existing.family());
            if(surnameParts&&!wholeFamily){String combined=(extras.path("apellido_paterno").asText("")+" "+extras.path("apellido_materno").asText("")).trim();family=prefer(combined,family);}
            return new Contact(existing.id(),incoming.kind(),prefer(incoming.given(),existing.given()),family,incoming.documentType(),incoming.number(),prefer(incoming.address(),existing.address()),prefer(incoming.representatives(),existing.representatives()),JSON.writeValueAsString(extras));
        }catch(Exception e){throw new SQLException("Datos adicionales de cliente inválidos.",e);}
    }
    private static String prefer(String newer,String older){return newer==null||newer.isBlank()?older:newer;}
    private static void bindContact(PreparedStatement s,Contact x)throws SQLException{s.setString(1,x.kind());s.setString(2,x.given());s.setString(3,x.family());s.setString(4,x.documentType().trim().toUpperCase(Locale.ROOT));s.setString(5,x.number().trim());s.setString(6,x.address());s.setString(7,x.representatives());s.setString(8,x.extrasJson());}
    public void deleteContact(long id)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM contact WHERE id=?")){s.setLong(1,id);s.executeUpdate();}}
    public int recordRevision(long templateId,byte[] content)throws SQLException{try(Connection c=open()){try(PreparedStatement q=c.prepareStatement("SELECT version,content FROM template_revision WHERE template_id=? ORDER BY version DESC LIMIT 1")){q.setLong(1,templateId);try(ResultSet r=q.executeQuery()){if(r.next()){int version=r.getInt(1);if(Arrays.equals(content,r.getBytes(2)))return version;return insertRevision(c,templateId,version+1,content);}}}return insertRevision(c,templateId,1,content);}}
    private static int insertRevision(Connection c,long id,int version,byte[] content)throws SQLException{try(PreparedStatement s=c.prepareStatement("INSERT INTO template_revision(template_id,version,content) VALUES(?,?,?)")){s.setLong(1,id);s.setInt(2,version);s.setBytes(3,content);s.executeUpdate();}return version;}
    public int version(long templateId)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT COALESCE(MAX(version),0) FROM template_revision WHERE template_id=?")){s.setLong(1,templateId);try(ResultSet r=s.executeQuery()){return r.next()?r.getInt(1):0;}}}
    public Map<Long,String> revisionDates()throws SQLException{Map<Long,String> dates=new HashMap<>();try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT template_id,MAX(created_at) FROM template_revision GROUP BY template_id")){while(r.next())dates.put(r.getLong(1),r.getString(2));}return dates;}
    public byte[] revision(long templateId,int version)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT content FROM template_revision WHERE template_id=? AND version=?")){s.setLong(1,templateId);s.setInt(2,version);try(ResultSet r=s.executeQuery()){return r.next()?r.getBytes(1):null;}}}
    public List<History> history()throws SQLException{List<History> out=new ArrayList<>();try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM document_history ORDER BY id DESC")){while(r.next())out.add(readHistory(r));}return out;}
    private static History readHistory(ResultSet r)throws SQLException{long value=r.getLong("template_id");Long templateId=r.wasNull()?null:value;return new History(r.getLong("id"),r.getString("title"),templateId,r.getInt("template_version"),r.getString("generated_at"),r.getString("values_json"),r.getString("docx_path"),r.getString("pdf_path"),r.getBytes("docx"),r.getBytes("pdf"));}
    public History saveHistory(History h)throws SQLException{
        long id;
        try(Connection c=open();PreparedStatement s=c.prepareStatement("INSERT INTO document_history(title,template_id,template_version,values_json,docx_path,pdf_path,docx,pdf) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            s.setString(1,h.title());if(h.templateId()==null)s.setNull(2,Types.INTEGER);else s.setLong(2,h.templateId());s.setInt(3,h.templateVersion());s.setString(4,h.valuesJson());s.setString(5,h.docxPath());s.setString(6,h.pdfPath());s.setBytes(7,h.docx());s.setBytes(8,h.pdf());s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){id=r.next()?r.getLong(1):0;}
        }
        History saved=historyById(id);synchronizeHistoryClients(saved);return saved;
    }
    private History historyById(long id)throws SQLException{
        try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT * FROM document_history WHERE id=?")){s.setLong(1,id);try(ResultSet r=s.executeQuery()){return r.next()?readHistory(r):null;}}
    }
    private Set<String> independentKeys()throws SQLException{
        Set<String> independent=new HashSet<>();try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT key FROM variable_definition WHERE bind_to_client=0")){while(r.next())independent.add(variableKey(r.getString(1)));}return independent;
    }
    private void synchronizeHistoryClients(History history)throws SQLException{
        if(history==null)return;List<Contact> contacts=contacts();if(contacts.isEmpty())return;Set<String> independent=independentKeys();List<Long> ids=new ArrayList<>();
        for(Contact contact:contacts)if(historyMatches(contact,history,independent))ids.add(contact.id());
        if(!ids.isEmpty())linkHistoryToContacts(history.id(),ids);
    }
    public void deleteHistory(long id)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM document_history WHERE id=?")){s.setLong(1,id);s.executeUpdate();}}
    public void updateHistoryFiles(long id,String docxPath,String pdfPath,byte[] pdf)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("UPDATE document_history SET docx_path=COALESCE(?,docx_path),pdf_path=COALESCE(?,pdf_path),pdf=COALESCE(?,pdf) WHERE id=?")){s.setString(1,docxPath);s.setString(2,pdfPath);s.setBytes(3,pdf);s.setLong(4,id);s.executeUpdate();}synchronizeHistoryClients(historyById(id));}
    public List<Mapping> mappings(long templateId)throws SQLException{List<Mapping> out=new ArrayList<>();try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT * FROM field_mapping WHERE template_id=? ORDER BY id")){s.setLong(1,templateId);try(ResultSet r=s.executeQuery()){while(r.next())out.add(new Mapping(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getString(5)));}}return out;}
    public Mapping saveMapping(Mapping m)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("INSERT INTO field_mapping(template_id,source_key,api_field,target_key) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){s.setLong(1,m.templateId());s.setString(2,m.sourceKey());s.setString(3,m.apiField());s.setString(4,m.targetKey());s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){return new Mapping(r.next()?r.getLong(1):0,m.templateId(),m.sourceKey(),m.apiField(),m.targetKey());}}}
    public void deleteMapping(long id)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM field_mapping WHERE id=?")){s.setLong(1,id);s.executeUpdate();}}
    public void clearMappings(long templateId)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM field_mapping WHERE template_id=?")){s.setLong(1,templateId);s.executeUpdate();}}
    public ApiBinding apiBinding(long definitionId)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT service,path FROM variable_api_binding WHERE definition_id=?")){s.setLong(1,definitionId);try(ResultSet r=s.executeQuery()){return r.next()?new ApiBinding(definitionId,r.getString(1),r.getString(2)):null;}}}
    public void saveApiBinding(ApiBinding binding)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("INSERT INTO variable_api_binding(definition_id,service,path) VALUES(?,?,?) ON CONFLICT(definition_id) DO UPDATE SET service=excluded.service,path=excluded.path")){s.setLong(1,binding.definitionId());s.setString(2,binding.service());s.setString(3,binding.path());s.executeUpdate();}}
    public void deleteApiBinding(long definitionId)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM variable_api_binding WHERE definition_id=?")){s.setLong(1,definitionId);s.executeUpdate();}}
    public List<ApiBinding> apiBindings()throws SQLException{List<ApiBinding> out=new ArrayList<>();try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT definition_id,service,path FROM variable_api_binding")){while(r.next())out.add(new ApiBinding(r.getLong(1),r.getString(2),r.getString(3)));}return out;}
    public List<ApiContext> apiContexts(long templateId)throws SQLException{List<ApiContext> out=new ArrayList<>();try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT template_id,variable_key,group_key,service,source FROM variable_query_context WHERE template_id=? ORDER BY group_key,source DESC,variable_key")){s.setLong(1,templateId);try(ResultSet r=s.executeQuery()){while(r.next())out.add(new ApiContext(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5)==1));}}return out;}
    public void saveApiContext(ApiContext context)throws SQLException{if(context.groupKey()==null||context.groupKey().isBlank()||context.variableKey()==null||context.variableKey().isBlank()||!List.of("DNI","RUC").contains(context.service()))throw new IllegalArgumentException("Grupo, variable o servicio inválido.");try(Connection c=open()){
        if(context.source())try(PreparedStatement check=c.prepareStatement("SELECT variable_key FROM variable_query_context WHERE template_id=? AND group_key=? AND service=? AND source=1 AND variable_key<>? LIMIT 1")){check.setLong(1,context.templateId());check.setString(2,context.groupKey().trim());check.setString(3,context.service());check.setString(4,context.variableKey());try(ResultSet r=check.executeQuery()){if(r.next())throw new IllegalArgumentException("El grupo ya tiene un número a consultar. Use otro grupo independiente.");}}
        try(PreparedStatement s=c.prepareStatement("INSERT INTO variable_query_context(template_id,variable_key,group_key,service,source) VALUES(?,?,?,?,?) ON CONFLICT(template_id,variable_key) DO UPDATE SET group_key=excluded.group_key,service=excluded.service,source=excluded.source")){s.setLong(1,context.templateId());s.setString(2,context.variableKey());s.setString(3,context.groupKey().trim());s.setString(4,context.service());s.setInt(5,context.source()?1:0);s.executeUpdate();}
    }}
    public void deleteApiContext(long templateId,String variableKey)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM variable_query_context WHERE template_id=? AND variable_key=?")){s.setLong(1,templateId);s.setString(2,variableKey);s.executeUpdate();}}
    public void clearApiContexts(long templateId)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM variable_query_context WHERE template_id=?")){s.setLong(1,templateId);s.executeUpdate();}}
    public List<ContactBinding> contactBindings(long templateId)throws SQLException{
        List<ContactBinding> out=new ArrayList<>();
        try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT * FROM contact_binding WHERE template_id=? ORDER BY group_key,id")){
            s.setLong(1,templateId);try(ResultSet r=s.executeQuery()){while(r.next())out.add(new ContactBinding(r.getLong("id"),r.getLong("template_id"),r.getString("group_key"),r.getString("kind"),r.getString("field"),r.getString("variable_key")));}
        }
        return out;
    }
    public ContactBinding saveContactBinding(ContactBinding b)throws SQLException{
        if(b.groupKey()==null||b.groupKey().isBlank()||!List.of("Persona","Empresa").contains(b.kind())||b.field()==null||b.field().isBlank()||b.variableKey()==null||b.variableKey().isBlank())throw new IllegalArgumentException("Asociación incompleta.");
        try(Connection c=open()){
            try(PreparedStatement check=c.prepareStatement("SELECT kind FROM contact_binding WHERE template_id=? AND group_key=? LIMIT 1")){
                check.setLong(1,b.templateId());check.setString(2,b.groupKey());try(ResultSet r=check.executeQuery()){if(r.next()&&!r.getString(1).equals(b.kind()))throw new IllegalArgumentException("El grupo ya tiene otro tipo de registro.");}
            }
            try(PreparedStatement check=c.prepareStatement("SELECT group_key,field FROM contact_binding WHERE template_id=? AND variable_key=? LIMIT 1")){
                check.setLong(1,b.templateId());check.setString(2,b.variableKey());try(ResultSet r=check.executeQuery()){if(r.next())throw new IllegalArgumentException("La variable ya está asociada a "+r.getString(1)+" / "+r.getString(2)+". Use una variable independiente para otro registro.");}
            }
            try(PreparedStatement s=c.prepareStatement("INSERT INTO contact_binding(template_id,group_key,kind,field,variable_key) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                s.setLong(1,b.templateId());s.setString(2,b.groupKey());s.setString(3,b.kind());s.setString(4,b.field());s.setString(5,b.variableKey());s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){return new ContactBinding(r.next()?r.getLong(1):0,b.templateId(),b.groupKey(),b.kind(),b.field(),b.variableKey());}
            }
        }
    }
    public void deleteContactBinding(long id)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM contact_binding WHERE id=?")){s.setLong(1,id);s.executeUpdate();}}
    public void clearContactBindings(long templateId)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM contact_binding WHERE template_id=?")){s.setLong(1,templateId);s.executeUpdate();}}
    public Contact matchingContact(String kind,String documentType,String number)throws SQLException{
        try(Connection c=open()){return matchingContact(c,kind,documentType,number);}
    }
    private static Contact matchingContact(Connection c,String kind,String documentType,String number)throws SQLException{
        if(number==null||number.isBlank())return null;
        try(PreparedStatement s=c.prepareStatement("SELECT * FROM contact WHERE kind=? AND UPPER(TRIM(document_type))=UPPER(?) AND TRIM(number)=? ORDER BY id LIMIT 1")){
            s.setString(1,kind);s.setString(2,documentType==null?"":documentType.trim());s.setString(3,number.trim());
            try(ResultSet r=s.executeQuery()){return r.next()?readContact(r):null;}
        }
    }
    public Contact findContactByNumber(String number)throws SQLException{
        if(number==null||number.isBlank())return null;
        try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT * FROM contact WHERE TRIM(number)=? LIMIT 1")){
            s.setString(1,number.trim());
            try(ResultSet r=s.executeQuery()){return r.next()?readContact(r):null;}
        }
    }
    private static Contact readContact(ResultSet r)throws SQLException{return new Contact(r.getLong("id"),r.getString("kind"),r.getString("given"),r.getString("family"),r.getString("document_type"),r.getString("number"),r.getString("address"),r.getString("representatives"),r.getString("extras_json"));}    public record Suggestion(String value,Map<String,String> complements,String origin){}
    public static String normalized(String value){return java.text.Normalizer.normalize(value==null?"":value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    public static String variableKey(String value){return normalized(value).replace(' ','_');}
    public static boolean personVariable(String key){String k=variableKey(key);return Set.of("nombre","nombres","nombres_com","nombres_completo","nombres_completos","nombre_completo","nombre_cliente","razon_social","persona","cliente","solicitante","dirigido","destinatario","beneficiario").contains(k)||k.startsWith("dirigido_")||k.startsWith("solicitante_");}
    private static String contactName(Contact c){return (c.given()+" "+c.family()).trim();}
    public Contact matchingContact(Contact incoming)throws SQLException{
        try(Connection c=open()){return matchingContact(c,incoming);}
    }
    private static Contact matchingContact(Connection c,Contact incoming)throws SQLException{
        if(incoming.number()!=null&&!incoming.number().isBlank())return matchingContact(c,incoming.kind(),incoming.documentType(),incoming.number());
        String name=normalized(contactName(incoming));if(name.isEmpty())return null;
        try(PreparedStatement s=c.prepareStatement("SELECT * FROM contact WHERE kind=? AND TRIM(number)='' ORDER BY id")){s.setString(1,incoming.kind());try(ResultSet r=s.executeQuery()){while(r.next()){Contact found=readContact(r);if(normalized(contactName(found)).equals(name))return found;}}}return null;
    }
    public List<Contact> findContacts(String query)throws SQLException{
        String q=normalized(query);List<Contact> out=new ArrayList<>();for(Contact c:contacts()){
            if(normalized(contactName(c)+" "+c.number()+" "+c.address()).contains(q)){out.add(c);continue;}
            try{var node=JSON.readTree(c.extrasJson());if(node!=null&&extraContains(node,q))out.add(c);}catch(Exception e){throw new SQLException("Datos adicionales de cliente inválidos.",e);}
        }return out;
    }
    private static boolean extraContains(com.fasterxml.jackson.databind.JsonNode node,String query){if(node.isValueNode())return normalized(node.asText()).contains(query);for(var child:node)if(extraContains(child,query))return true;return false;}
    public List<Contact> findContactsByVariable(String key,String value,boolean prefix)throws SQLException{
        String q=normalized(value);List<Contact> out=new ArrayList<>();for(Contact contact:contacts()){String found=normalized(contactValue(contact,key));if(!found.isEmpty()&&(prefix?found.startsWith(q):found.equals(q)))out.add(contact);}return out;
    }
    public String contactValue(Contact contact,String key)throws SQLException{
        String normalizedKey=variableKey(key);
        try{var extras=JSON.readTree(contact.extrasJson());if(extras!=null){var fields=extras.fields();while(fields.hasNext()){var field=fields.next();if(variableKey(field.getKey()).equals(normalizedKey)&&field.getValue().isValueNode())return field.getValue().asText("");}}}catch(Exception e){throw new SQLException("Datos adicionales inválidos.",e);}
        if(personVariable(key))return contactName(contact);
        return switch(normalizedKey){case "dni","ruc","documento","number"->contact.number();case "direccion","domicilio","address"->contact.address();case "apellidos","family"->contact.family();default->"";};
    }
    public void rememberPair(String sourceKey,String sourceValue,String targetKey,String targetValue,boolean bidirectional)throws SQLException{
        if(sourceValue==null||sourceValue.isBlank()||targetValue==null||targetValue.isBlank())return;
        try(Connection c=open()){
            c.setAutoCommit(false);try{
                if(bidirectional)try(PreparedStatement previous=c.prepareStatement("SELECT source_display,target_value FROM variable_pair_memory WHERE source_key=? AND source_value=? AND target_key=?")){
                    previous.setString(1,variableKey(sourceKey));previous.setString(2,normalized(sourceValue));previous.setString(3,variableKey(targetKey));
                    try(ResultSet r=previous.executeQuery()){if(r.next()&&!normalized(r.getString(2)).equals(normalized(targetValue)))try(PreparedStatement remove=c.prepareStatement("DELETE FROM variable_pair_memory WHERE source_key=? AND source_value=? AND target_key=? AND target_value=?")){remove.setString(1,variableKey(targetKey));remove.setString(2,normalized(r.getString(2)));remove.setString(3,variableKey(sourceKey));remove.setString(4,r.getString(1));remove.executeUpdate();}}
                }
                writePair(c,sourceKey,sourceValue,targetKey,targetValue);if(bidirectional)writePair(c,targetKey,targetValue,sourceKey,sourceValue);c.commit();}catch(SQLException e){c.rollback();throw e;}
        }
    }
    private static void writePair(Connection c,String source,String value,String target,String result)throws SQLException{
        try(PreparedStatement s=c.prepareStatement("INSERT INTO variable_pair_memory(source_key,source_value,source_display,target_key,target_value,updated_at) VALUES(?,?,?,?,?,?) ON CONFLICT(source_key,source_value,target_key) DO UPDATE SET source_display=excluded.source_display,target_value=excluded.target_value,updated_at=excluded.updated_at")){
            s.setString(1,variableKey(source));s.setString(2,normalized(value));s.setString(3,value);s.setString(4,variableKey(target));s.setString(5,result);s.setString(6,java.time.Instant.now().toString());s.executeUpdate();
        }
    }
    public List<Suggestion> suggestions(String sourceKey,Collection<String> targetKeys,String value,boolean exact)throws SQLException{
        if(value==null||value.isBlank()||targetKeys.isEmpty())return List.of();
        Set<String> targets=new LinkedHashSet<>();targetKeys.forEach(k->targets.add(variableKey(k)));Map<String,String> display=new LinkedHashMap<>(),origins=new HashMap<>();Map<String,Map<String,String>> results=new LinkedHashMap<>();String q=normalized(value);
        String sql="SELECT source_display,target_key,target_value FROM variable_pair_memory WHERE source_key=? AND source_value "+(exact?"=?":"LIKE ? ESCAPE '\\'")+" ORDER BY updated_at DESC,id DESC";
        try(Connection c=open();PreparedStatement s=c.prepareStatement(sql)){
            s.setString(1,variableKey(sourceKey));s.setString(2,exact?q:q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");
            try(ResultSet r=s.executeQuery()){while(r.next()){String target=r.getString(2);if(!targets.contains(target))continue;String original=r.getString(1),key=normalized(original);display.putIfAbsent(key,original);origins.putIfAbsent(key,"Memoria local");results.computeIfAbsent(key,k->new LinkedHashMap<>()).putIfAbsent(target,r.getString(3));}}
        }
        for(Contact contact:findContactsByVariable(sourceKey,value,!exact)){
            String original=contactValue(contact,sourceKey),key=normalized(original);Map<String,String> complements=new LinkedHashMap<>();for(String target:targets){String found=contactValue(contact,target);if(!found.isBlank())complements.put(target,found);}if(complements.isEmpty())continue;
            Map<String,String> known=results.get(key);if(known!=null&&complements.entrySet().stream().anyMatch(e->known.containsKey(e.getKey())&&!known.get(e.getKey()).equals(e.getValue())))key=key+"#contact-"+contact.id();
            display.putIfAbsent(key,original);origins.putIfAbsent(key,"Clientes");Map<String,String> row=results.computeIfAbsent(key,k->new LinkedHashMap<>());complements.forEach(row::putIfAbsent);
        }
        List<Suggestion> out=new ArrayList<>();for(var entry:results.entrySet()){out.add(new Suggestion(display.get(entry.getKey()),Map.copyOf(entry.getValue()),origins.get(entry.getKey())));if(out.size()==10)break;}return out;
    }
    /** Learn only pairs present in this document; names update the existing client without losing DNI/API data. */
    public void learnComplements(List<DocumentStore.Definition> definitions,List<DocumentStore.Complement> links,Map<String,String> values)throws SQLException{
        Map<Long,DocumentStore.Definition> defs=new HashMap<>();definitions.forEach(d->defs.put(d.id(),d));Map<String,String> normalizedValues=new HashMap<>();values.forEach((k,v)->normalizedValues.put(variableKey(k),v));
        for(var link:links){var source=defs.get(link.sourceDefId());var target=defs.get(link.targetDefId());if(source==null||target==null)continue;String a=normalizedValues.get(variableKey(source.key())),b=normalizedValues.get(variableKey(target.key()));if(a==null||b==null||a.isBlank()||b.isBlank())continue;
            rememberPair(source.key(),a,target.key(),b,link.bidirectional());
            if(personVariable(source.key()))learnPerson(source.key(),a,target.key(),b);
            else if(personVariable(target.key()))learnPerson(target.key(),b,source.key(),a);
        }
    }
    private void learnPerson(String nameKey,String name,String otherKey,String value)throws SQLException{
        Contact existing=contacts().stream().filter(c->normalized(contactName(c)).equals(normalized(name))).findFirst().orElse(null);
        try{ObjectNode extras=JSON.createObjectNode();extras.put(variableKey(nameKey),name);extras.put(variableKey(otherKey),value);
            Contact incoming=existing==null?new Contact(0,"razon_social".equals(variableKey(nameKey))?"Empresa":"Persona",name,"","","","","",extras.toString()):new Contact(existing.id(),existing.kind(),existing.given(),existing.family(),existing.documentType(),existing.number(),existing.address(),existing.representatives(),extras.toString());
            upsertContact(incoming);
        }catch(Exception e){if(e instanceof SQLException sql)throw sql;throw new SQLException("No se pudo actualizar el cliente del complemento.",e);}
    }
    /** Remove legacy full identity uniqueness, which incorrectly treats all undocumented people as one. */
    private static void migrateContact(Connection c)throws SQLException{
        List<String> indexes=new ArrayList<>();boolean rebuild=false;
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA index_list(contact)")){while(r.next())if(r.getInt("unique")!=0)indexes.add(r.getString("name"));}
        List<String> drop=new ArrayList<>();for(String index:indexes){List<String> columns=new ArrayList<>();try(PreparedStatement s=c.prepareStatement("SELECT name FROM pragma_index_info(?)")){s.setString(1,index);try(ResultSet r=s.executeQuery()){while(r.next())columns.add(r.getString(1));}}
            if(columns.equals(List.of("kind","document_type","number"))){if(index.startsWith("sqlite_autoindex"))rebuild=true;else drop.add(index);}
        }
        if(!rebuild&&drop.isEmpty())return;
        List<String> objects=new ArrayList<>();try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT name,sql FROM sqlite_master WHERE tbl_name='contact' AND type IN ('index','trigger') AND sql IS NOT NULL")){while(r.next())if(!drop.contains(r.getString(1)))objects.add(r.getString(2));}
        c.setAutoCommit(false);try(Statement s=c.createStatement()){
            if(rebuild){s.executeUpdate("CREATE TABLE contact_migration(id INTEGER PRIMARY KEY,kind TEXT NOT NULL,given TEXT NOT NULL,family TEXT NOT NULL DEFAULT '',document_type TEXT NOT NULL DEFAULT '',number TEXT NOT NULL DEFAULT '',address TEXT NOT NULL DEFAULT '',representatives TEXT NOT NULL DEFAULT '',extras_json TEXT NOT NULL DEFAULT '{}')");s.executeUpdate("INSERT INTO contact_migration SELECT id,kind,given,family,document_type,number,address,representatives,extras_json FROM contact");s.executeUpdate("DROP TABLE contact");s.executeUpdate("ALTER TABLE contact_migration RENAME TO contact");for(String sql:objects)s.executeUpdate(sql);}
            else for(String index:drop)s.executeUpdate("DROP INDEX \""+index.replace("\"","\"\"")+"\"");c.commit();
        }catch(SQLException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
    }

}
