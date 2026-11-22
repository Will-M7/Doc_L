package com.ejemplo.crudpersona.studio;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public final class MCDocumentStore {
    public record MCTemplate(@com.fasterxml.jackson.annotation.JsonProperty("id") long MCid, @com.fasterxml.jackson.annotation.JsonProperty("name") String MCname, @com.fasterxml.jackson.annotation.JsonProperty("original") byte[] MCoriginal, @com.fasterxml.jackson.annotation.JsonProperty("edited") byte[] MCedited) {
        @Override public String toString() { return MCname; }
    }
    public record MCVariable(@com.fasterxml.jackson.annotation.JsonProperty("id") long MCid, @com.fasterxml.jackson.annotation.JsonProperty("templateId") long MCtemplateId, @com.fasterxml.jackson.annotation.JsonProperty("key") String MCkey, @com.fasterxml.jackson.annotation.JsonProperty("label") String MClabel, @com.fasterxml.jackson.annotation.JsonProperty("type") String MCtype,
                           @com.fasterxml.jackson.annotation.JsonProperty("defaultValue") String MCdefaultValue, @com.fasterxml.jackson.annotation.JsonProperty("required") boolean MCrequired, @com.fasterxml.jackson.annotation.JsonProperty("position") int MCposition) {
        @Override public String toString() { return MClabel + "  {{" + MCkey + "}}"; }
    }
    public static final List<String> MCDATE_FORMATS=List.of("dd/MM/yyyy","d 'de' MMMM 'de' yyyy","EEEE, d 'de' MMMM 'de' yyyy","dd-MM-yyyy","yyyy-MM-dd");
    public record MCDefinition(@com.fasterxml.jackson.annotation.JsonProperty("id") long MCid, @com.fasterxml.jackson.annotation.JsonProperty("key") String MCkey, @com.fasterxml.jackson.annotation.JsonProperty("label") String MClabel, @com.fasterxml.jackson.annotation.JsonProperty("type") String MCtype, @com.fasterxml.jackson.annotation.JsonProperty("defaultValue") String MCdefaultValue,@com.fasterxml.jackson.annotation.JsonProperty("autoCurrentDate") boolean MCautoCurrentDate,@com.fasterxml.jackson.annotation.JsonProperty("dateFormat") String MCdateFormat,@com.fasterxml.jackson.annotation.JsonProperty("bindToClient") boolean MCbindToClient) {
        public MCDefinition(long MCid,String MCkey,String MClabel,String MCtype,String MCdefaultValue){this(MCid,MCkey,MClabel,MCtype,MCdefaultValue,false,"dd/MM/yyyy",true);}
        public MCDefinition(long MCid,String MCkey,String MClabel,String MCtype,String MCdefaultValue,boolean MCautoCurrentDate,String MCdateFormat){this(MCid,MCkey,MClabel,MCtype,MCdefaultValue,MCautoCurrentDate,MCdateFormat,true);}
        public String MCinitialValue(){return !MCbindToClient?(MCdefaultValue==null?"":MCdefaultValue):MCautoCurrentDate?MCcurrentDate():MCVariableInput.MCinitialValue(MCtype,MCdefaultValue);}
        public String MCeditorKey(String MCpersona){return MCDocumentEngine.MCqualify(MCbindToClient?MCpersona:"",MCkey);}
        public String MCcurrentDate(){return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern(MCdateFormat,Locale.forLanguageTag("es-PE")));}
        @Override public String toString() { return MClabel + "  {{" + MCkey + "}} · " + MCtype + " #" + MCid; }
    }
    public record MCKind(@com.fasterxml.jackson.annotation.JsonProperty("id") long MCid, @com.fasterxml.jackson.annotation.JsonProperty("title") String MCtitle, @com.fasterxml.jackson.annotation.JsonProperty("category") String MCcategory, @com.fasterxml.jackson.annotation.JsonProperty("description") String MCdescription,
                       @com.fasterxml.jackson.annotation.JsonProperty("templateId") long MCtemplateId, @com.fasterxml.jackson.annotation.JsonProperty("fields") String MCfields, @com.fasterxml.jackson.annotation.JsonProperty("active") boolean MCactive) {
        @Override public String toString() { return MCtitle; }
    }
    public record MCTemplateGroup(@com.fasterxml.jackson.annotation.JsonProperty("id") long MCid,@com.fasterxml.jackson.annotation.JsonProperty("name") String MCname,@com.fasterxml.jackson.annotation.JsonProperty("count") int MCcount){@Override public String toString(){return MCname+" ("+MCcount+")";}}

    public record MCPersona(@com.fasterxml.jackson.annotation.JsonProperty("id") long MCid,@com.fasterxml.jackson.annotation.JsonProperty("templateId") long MCtemplateId,@com.fasterxml.jackson.annotation.JsonProperty("personaKey") String MCpersonaKey,@com.fasterxml.jackson.annotation.JsonProperty("displayName") String MCdisplayName,@com.fasterxml.jackson.annotation.JsonProperty("sortOrder") int MCsortOrder){@Override public String toString(){return MCdisplayName;}}
    public List<MCPersona> MCpersonas(long MCtemplateId)throws SQLException{
        List<MCPersona> MCout=new ArrayList<>();try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("SELECT * FROM template_persona WHERE template_id=? ORDER BY sort_order,id")){MCs.setLong(1,MCtemplateId);try(ResultSet MCr=MCs.executeQuery()){while(MCr.next())MCout.add(new MCPersona(MCr.getLong("id"),MCtemplateId,MCr.getString("persona_key"),MCr.getString("display_name"),MCr.getInt("sort_order")));}}return MCout;
    }
    public MCPersona MCaddPersona(long MCtemplateId,String MCdisplayName)throws SQLException{
        List<MCPersona> MCexisting=MCpersonas(MCtemplateId);int MCindex=1;Set<String> MCkeys=new HashSet<>();MCexisting.forEach(MCp->MCkeys.add(MCp.MCpersonaKey()));while(MCkeys.contains("Persona_"+MCindex))MCindex++;
        return MCaddPersona(MCtemplateId,"Persona_"+MCindex,MCdisplayName==null||MCdisplayName.isBlank()?"Persona "+MCindex:MCdisplayName,MCexisting.stream().mapToInt(MCPersona::MCsortOrder).max().orElse(-1)+1);
    }
    public MCPersona MCaddPersona(long MCtemplateId,String MCkey,String MCname,int MCorder)throws SQLException{
        if(!MCkey.matches("[a-zA-Z][a-zA-Z0-9_]*")||MCname==null||MCname.isBlank())throw new IllegalArgumentException("Persona inválida.");
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("INSERT INTO template_persona(template_id,persona_key,display_name,sort_order) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){MCs.setLong(1,MCtemplateId);MCs.setString(2,MCkey);MCs.setString(3,MCname.trim());MCs.setInt(4,MCorder);MCs.executeUpdate();try(ResultSet MCr=MCs.getGeneratedKeys()){return new MCPersona(MCr.next()?MCr.getLong(1):0,MCtemplateId,MCkey,MCname.trim(),MCorder);}}
    }
    public void MCrenamePersona(long MCid,String MCname)throws SQLException{if(MCname==null||MCname.isBlank())throw new IllegalArgumentException("Escriba un nombre o rol.");try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("UPDATE template_persona SET display_name=? WHERE id=?")){MCs.setString(1,MCname.trim());MCs.setLong(2,MCid);MCs.executeUpdate();}}
    public void MCdeletePersona(long MCid)throws SQLException{try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM template_persona WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}}
    public record MCComplement(@com.fasterxml.jackson.annotation.JsonProperty("id") long MCid,@com.fasterxml.jackson.annotation.JsonProperty("sourceDefId") long MCsourceDefId,@com.fasterxml.jackson.annotation.JsonProperty("targetDefId") long MCtargetDefId,@com.fasterxml.jackson.annotation.JsonProperty("bidirectional") boolean MCbidirectional){}
    private final Path MCdirectory;
    private final String MCurl;

    public MCDocumentStore() throws Exception {
        this(MCdefaultDirectory());
    }

    private static Path MCdefaultDirectory() {
        String MCappData = System.getenv("APPDATA");
        return MCappData != null && !MCappData.isBlank()
                ? Path.of(MCappData, "DocumentStudio")
                : Path.of(System.getProperty("user.home"), ".document-studio");
    }

    public MCDocumentStore(Path MCdirectory) throws Exception {
        this.MCdirectory = MCdirectory;
        Files.createDirectories(MCdirectory);
        MCurl = "jdbc:sqlite:" + MCdirectory.resolve("studio.db");
        Class.forName("org.sqlite.JDBC");
        try (Connection MCc = MCconnect(); Statement MCs = MCc.createStatement()) {
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS template (id INTEGER PRIMARY KEY, name TEXT NOT NULL, original BLOB NOT NULL, edited BLOB NOT NULL)");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS template_group (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE UNIQUE)");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS template_group_member (template_id INTEGER PRIMARY KEY REFERENCES template(id) ON DELETE CASCADE, group_id INTEGER NOT NULL REFERENCES template_group(id) ON DELETE CASCADE)");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS template_group_link (template_id INTEGER NOT NULL REFERENCES template(id) ON DELETE CASCADE, group_id INTEGER NOT NULL REFERENCES template_group(id) ON DELETE CASCADE, PRIMARY KEY(template_id,group_id))");
            MCs.executeUpdate("INSERT OR IGNORE INTO template_group_link(template_id,group_id) SELECT template_id,group_id FROM template_group_member");
            MCs.executeUpdate("DELETE FROM template_group_member");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS template_persona(id INTEGER PRIMARY KEY AUTOINCREMENT,template_id INTEGER NOT NULL REFERENCES template(id) ON DELETE CASCADE,persona_key TEXT NOT NULL,display_name TEXT NOT NULL,sort_order INTEGER NOT NULL DEFAULT 0,UNIQUE(template_id,persona_key))");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS variable (id INTEGER PRIMARY KEY, template_id INTEGER NOT NULL REFERENCES template(id) ON DELETE CASCADE, key TEXT NOT NULL, label TEXT NOT NULL, type TEXT NOT NULL, default_value TEXT, required INTEGER NOT NULL, position INTEGER NOT NULL, UNIQUE(template_id,key))");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS variable_definition (id INTEGER PRIMARY KEY, key TEXT NOT NULL, label TEXT NOT NULL, type TEXT NOT NULL, default_value TEXT)");
            Set<String> MCdefinitionColumns=new HashSet<>();try(ResultSet MCr=MCs.executeQuery("PRAGMA table_info(variable_definition)")){while(MCr.next())MCdefinitionColumns.add(MCr.getString("name"));}
            if(!MCdefinitionColumns.contains("auto_current_date"))MCs.executeUpdate("ALTER TABLE variable_definition ADD COLUMN auto_current_date INTEGER NOT NULL DEFAULT 0");
            if(!MCdefinitionColumns.contains("bind_to_client"))MCs.executeUpdate("ALTER TABLE variable_definition ADD COLUMN bind_to_client INTEGER NOT NULL DEFAULT 1");
            if(!MCdefinitionColumns.contains("date_format"))MCs.executeUpdate("ALTER TABLE variable_definition ADD COLUMN date_format TEXT NOT NULL DEFAULT 'dd/MM/yyyy'");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS variable_complement(id INTEGER PRIMARY KEY AUTOINCREMENT,source_def_id INTEGER NOT NULL REFERENCES variable_definition(id) ON DELETE CASCADE,target_def_id INTEGER NOT NULL REFERENCES variable_definition(id) ON DELETE CASCADE,bidirectional INTEGER NOT NULL DEFAULT 1,UNIQUE(source_def_id,target_def_id))");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS kind (id INTEGER PRIMARY KEY, title TEXT NOT NULL, category TEXT, description TEXT, template_id INTEGER NOT NULL REFERENCES template(id), fields TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL)");
            MCs.executeUpdate("CREATE TABLE IF NOT EXISTS kind_field_rule (kind_id INTEGER NOT NULL REFERENCES kind(id) ON DELETE CASCADE, key TEXT NOT NULL, required INTEGER NOT NULL, PRIMARY KEY(kind_id,key))");
            boolean MChasDefault=false;
            try(ResultSet MCcolumns=MCs.executeQuery("PRAGMA table_info(kind_field_rule)")){while(MCcolumns.next())if("default_value".equals(MCcolumns.getString("name")))MChasDefault=true;}
            if(!MChasDefault)MCs.executeUpdate("ALTER TABLE kind_field_rule ADD COLUMN default_value TEXT");
            MCs.executeUpdate("PRAGMA foreign_keys=ON");
        }
        MCmigrateDefinitions();
    }

    public Path MCdirectory() { return MCdirectory; }
    private Connection MCconnect() throws SQLException {
        Connection MCc = DriverManager.getConnection(MCurl);
        try (Statement MCs = MCc.createStatement()) { MCs.execute("PRAGMA foreign_keys=ON"); }
        return MCc;
    }

    private void MCmigrateDefinitions() throws SQLException {
        try(Connection MCc=MCconnect()) {
            boolean MChasColumn=false;
            try(Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("PRAGMA table_info(variable)")) {
                while(MCr.next())if("definition_id".equals(MCr.getString("name")))MChasColumn=true;
            }
            if(!MChasColumn)try(Statement MCs=MCc.createStatement()){MCs.executeUpdate("ALTER TABLE variable ADD COLUMN definition_id INTEGER REFERENCES variable_definition(id)");}
            MCc.setAutoCommit(false);
            try(Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT id,key,label,type,default_value FROM variable WHERE definition_id IS NULL")) {
                while(MCr.next()){
                    long MCdefinition=MCfindMatchingDefinition(MCc,MCr.getString("key"),MCr.getString("label"),MCr.getString("type"),MCr.getString("default_value"));
                    if(MCdefinition==0)MCdefinition=MCinsertDefinition(MCc,MCr.getString("key"),MCr.getString("label"),MCr.getString("type"),MCr.getString("default_value"));
                    try(PreparedStatement MCupdate=MCc.prepareStatement("UPDATE variable SET definition_id=? WHERE id=?")){MCupdate.setLong(1,MCdefinition);MCupdate.setLong(2,MCr.getLong("id"));MCupdate.executeUpdate();}
                }
            }
            MCc.commit();
        }
    }
    private static long MCfindMatchingDefinition(Connection MCc,String MCkey,String MClabel,String MCtype,String MCvalue)throws SQLException {
        try(PreparedStatement MCs=MCc.prepareStatement("SELECT id FROM variable_definition WHERE key=? AND label=? AND type=? AND COALESCE(default_value,'')=COALESCE(?,'') ORDER BY id LIMIT 1")) {
            MCs.setString(1,MCkey);MCs.setString(2,MClabel);MCs.setString(3,MCtype);MCs.setString(4,MCvalue);
            try(ResultSet MCr=MCs.executeQuery()){return MCr.next()?MCr.getLong(1):0;}
        }
    }
    private static long MCinsertDefinition(Connection MCc,String MCkey,String MClabel,String MCtype,String MCvalue)throws SQLException {
        try(PreparedStatement MCs=MCc.prepareStatement("INSERT INTO variable_definition(key,label,type,default_value) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
            MCs.setString(1,MCkey);MCs.setString(2,MClabel);MCs.setString(3,MCtype);MCs.setString(4,MCvalue);MCs.executeUpdate();
            try(ResultSet MCr=MCs.getGeneratedKeys()){if(MCr.next())return MCr.getLong(1);throw new SQLException("No se creó la definición.");}
        }
    }
    public List<MCDefinition> MCdefinitions()throws SQLException {
        List<MCDefinition> MCout=new ArrayList<>();
        try(Connection MCc=MCconnect();Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT * FROM variable_definition ORDER BY label,key,id")){
            while(MCr.next())MCout.add(new MCDefinition(MCr.getLong("id"),MCr.getString("key"),MCr.getString("label"),MCr.getString("type"),MCr.getString("default_value"),MCr.getInt("auto_current_date")==1,MCr.getString("date_format"),MCr.getInt("bind_to_client")==1));
        }
        return MCout;
    }
    public MCDefinition MCsaveDefinition(MCDefinition MCd)throws SQLException {
        if(!MCd.MCkey().matches("[a-zA-Z][a-zA-Z0-9_]*")||MCd.MClabel().isBlank()||MCd.MCtype().isBlank())throw new IllegalArgumentException("Definición de variable incompleta.");
        try(Connection MCc=MCconnect()){
            MCc.setAutoCommit(false);
            try{
                if(MCd.MCid()==0){
                    try(PreparedStatement MCcheck=MCc.prepareStatement("SELECT id FROM variable_definition WHERE key=? LIMIT 1")){MCcheck.setString(1,MCd.MCkey());try(ResultSet MCr=MCcheck.executeQuery()){if(MCr.next())throw new IllegalArgumentException("El identificador ya existe. Use otro o edite la variable existente.");}}
                    long MCid=MCinsertDefinition(MCc,MCd.MCkey(),MCd.MClabel(),MCd.MCtype(),MCd.MCdefaultValue());MCsaveDate(MCc,MCid,MCd);MCc.commit();return new MCDefinition(MCid,MCd.MCkey(),MCd.MClabel(),MCd.MCtype(),MCd.MCdefaultValue(),MCd.MCautoCurrentDate(),MCd.MCdateFormat(),MCd.MCbindToClient());
                }
                try(PreparedStatement MCs=MCc.prepareStatement("UPDATE variable_definition SET label=?,type=?,default_value=? WHERE id=? AND key=?")){
                    MCs.setString(1,MCd.MClabel());MCs.setString(2,MCd.MCtype());MCs.setString(3,MCd.MCdefaultValue());MCs.setLong(4,MCd.MCid());MCs.setString(5,MCd.MCkey());
                    if(MCs.executeUpdate()!=1)throw new IllegalArgumentException("El identificador es estable y no puede cambiarse.");
                }
                try(PreparedStatement MCs=MCc.prepareStatement("UPDATE variable SET label=?,type=?,default_value=? WHERE definition_id=?")){
                    MCs.setString(1,MCd.MClabel());MCs.setString(2,MCd.MCtype());MCs.setString(3,MCd.MCdefaultValue());MCs.setLong(4,MCd.MCid());MCs.executeUpdate();
                }
                MCsaveDate(MCc,MCd.MCid(),MCd);MCc.commit();return MCd;
            }catch(Exception MCe){MCc.rollback();if(MCe instanceof SQLException MCsql)throw MCsql;throw MCe;}
        }
    }
    private static void MCsaveDate(Connection MCc,long MCid,MCDefinition MCdefinition)throws SQLException{
        if(!MCDATE_FORMATS.contains(MCdefinition.MCdateFormat()))throw new IllegalArgumentException("Formato de fecha inválido.");
        try(PreparedStatement MCs=MCc.prepareStatement("UPDATE variable_definition SET auto_current_date=?,date_format=?,bind_to_client=? WHERE id=?")){MCs.setInt(1,MCdefinition.MCautoCurrentDate()?1:0);MCs.setString(2,MCdefinition.MCdateFormat());MCs.setInt(3,MCdefinition.MCbindToClient()?1:0);MCs.setLong(4,MCid);MCs.executeUpdate();}
    }
    public MCDefinition MCimportDefinition(MCDefinition MCd)throws SQLException {
        if(!MCd.MCkey().matches("[a-zA-Z][a-zA-Z0-9_]*")||MCd.MClabel().isBlank()||MCd.MCtype().isBlank())throw new IllegalArgumentException("Definición importada inválida.");
        try(Connection MCc=MCconnect()){
            long MCid=0;try(PreparedStatement MCs=MCc.prepareStatement("SELECT id FROM variable_definition WHERE key=? AND label=? AND type=? AND COALESCE(default_value,'')=COALESCE(?,'') AND auto_current_date=? AND date_format=? AND bind_to_client=?")){MCs.setString(1,MCd.MCkey());MCs.setString(2,MCd.MClabel());MCs.setString(3,MCd.MCtype());MCs.setString(4,MCd.MCdefaultValue());MCs.setInt(5,MCd.MCautoCurrentDate()?1:0);MCs.setString(6,MCd.MCdateFormat());MCs.setInt(7,MCd.MCbindToClient()?1:0);try(ResultSet MCr=MCs.executeQuery()){if(MCr.next())MCid=MCr.getLong(1);}}
            if(MCid==0){MCid=MCinsertDefinition(MCc,MCd.MCkey(),MCd.MClabel(),MCd.MCtype(),MCd.MCdefaultValue());MCsaveDate(MCc,MCid,MCd);}
            return new MCDefinition(MCid,MCd.MCkey(),MCd.MClabel(),MCd.MCtype(),MCd.MCdefaultValue(),MCd.MCautoCurrentDate(),MCd.MCdateFormat(),MCd.MCbindToClient());
        }
    }
    public long MCdefinitionId(long MCvariableId)throws SQLException {
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("SELECT definition_id FROM variable WHERE id=?")){
            MCs.setLong(1,MCvariableId);try(ResultSet MCr=MCs.executeQuery()){return MCr.next()?MCr.getLong(1):0;}
        }
    }
    public List<MCComplement> MCcomplements(long MCdefinitionId)throws SQLException{
        List<MCComplement> MCout=new ArrayList<>();for(MCComplement MClink:MCcomplements()){
            if(MClink.MCsourceDefId()==MCdefinitionId)MCout.add(MClink);
            else if(MClink.MCbidirectional()&&MClink.MCtargetDefId()==MCdefinitionId)MCout.add(new MCComplement(MClink.MCid(),MCdefinitionId,MClink.MCsourceDefId(),true));
        }return MCout;
    }
    public List<MCComplement> MCcomplements()throws SQLException{
        List<MCComplement> MCout=new ArrayList<>();try(Connection MCc=MCconnect();Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT * FROM variable_complement ORDER BY id")){while(MCr.next())MCout.add(new MCComplement(MCr.getLong("id"),MCr.getLong("source_def_id"),MCr.getLong("target_def_id"),MCr.getInt("bidirectional")==1));}return MCout;
    }
    public void MCsaveComplement(long MCsource,long MCtarget,boolean MCbidirectional)throws SQLException{
        if(MCsource==MCtarget)throw new IllegalArgumentException("Elija otra variable como complemento.");
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("INSERT INTO variable_complement(source_def_id,target_def_id,bidirectional) VALUES(?,?,?) ON CONFLICT(source_def_id,target_def_id) DO UPDATE SET bidirectional=excluded.bidirectional")){MCs.setLong(1,MCsource);MCs.setLong(2,MCtarget);MCs.setInt(3,MCbidirectional?1:0);MCs.executeUpdate();}
    }
    public void MCdeleteComplement(long MCid)throws SQLException{try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM variable_complement WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}}
    public List<String> MCdefinitionDependencies(long MCid)throws SQLException {
        return MCdefinitionTemplates(MCid).stream().map(MCTemplate::MCname).distinct().toList();
    }
    public List<MCTemplate> MCdefinitionTemplates(long MCid)throws SQLException {
        Set<Long> MCids=new LinkedHashSet<>();
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("SELECT template_id FROM variable WHERE definition_id=?")){
            MCs.setLong(1,MCid);try(ResultSet MCr=MCs.executeQuery()){while(MCr.next())MCids.add(MCr.getLong(1));}
        }
        String MCkey=null;try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("SELECT key FROM variable_definition WHERE id=?")){MCs.setLong(1,MCid);try(ResultSet MCr=MCs.executeQuery()){if(MCr.next())MCkey=MCr.getString(1);}}
        List<MCTemplate> MCtemplates=MCtemplates();
        final String MCdefinitionKey=MCkey;if(MCkey!=null)for(MCTemplate MCtemplate:MCtemplates)try(var MCdocument=MCDocumentEngine.MCopen(MCtemplate.MCedited())){if(MCDocumentEngine.MCmarkers(MCdocument).stream().anyMatch(MCtoken->MCDocumentEngine.MCbaseKey(MCtoken).equals(MCdefinitionKey)))MCids.add(MCtemplate.MCid());}catch(Exception MCe){throw new SQLException("No se pudieron comprobar las plantillas antes de eliminar la variable.",MCe);}
        return MCtemplates.stream().filter(MCt->MCids.contains(MCt.MCid())).toList();
    }
    public void MCdeleteDefinition(long MCid)throws SQLException {
        List<String> MCuses=MCdefinitionDependencies(MCid);if(!MCuses.isEmpty())throw new IllegalStateException("Variable utilizada en: "+String.join(", ",MCuses));
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM variable_definition WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}
    }
    public MCVariable MCbindDefinition(long MCtemplateId,long MCdefinitionId,boolean MCrequired,int MCposition)throws SQLException{return MCbindDefinition(MCtemplateId,MCdefinitionId,null,MCrequired,MCposition);}
    public MCVariable MCbindDefinition(long MCtemplateId,long MCdefinitionId,String MCqualifiedKey,boolean MCrequired,int MCposition)throws SQLException {
        MCDefinition MCd=MCdefinitions().stream().filter(MCx->MCx.MCid()==MCdefinitionId).findFirst().orElseThrow(()->new IllegalArgumentException("Variable inexistente."));
        String MCkey=MCqualifiedKey==null?MCd.MCkey():MCqualifiedKey;if(!MCDocumentEngine.MCvalidKey(MCkey)||!MCDocumentEngine.MCbaseKey(MCkey).equals(MCd.MCkey()))throw new IllegalArgumentException("Variable calificada inválida.");if(!MCd.MCbindToClient())MCkey=MCd.MCkey();
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("INSERT INTO variable(template_id,key,label,type,default_value,required,position,definition_id) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            MCs.setLong(1,MCtemplateId);MCs.setString(2,MCkey);MCs.setString(3,MCd.MClabel());MCs.setString(4,MCd.MCtype());MCs.setString(5,MCd.MCdefaultValue());MCs.setInt(6,MCrequired?1:0);MCs.setInt(7,MCposition);MCs.setLong(8,MCdefinitionId);MCs.executeUpdate();
            try(ResultSet MCr=MCs.getGeneratedKeys()){return new MCVariable(MCr.next()?MCr.getLong(1):0,MCtemplateId,MCkey,MCd.MClabel(),MCd.MCtype(),MCd.MCdefaultValue(),MCrequired,MCposition);}
        }
    }
    public void MClinkDefinition(long MCvariableId,long MCdefinitionId)throws SQLException {
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("UPDATE variable SET definition_id=? WHERE id=?")){MCs.setLong(1,MCdefinitionId);MCs.setLong(2,MCvariableId);MCs.executeUpdate();}
    }

    public List<MCTemplate> MCtemplates() throws SQLException {
        List<MCTemplate> MCresult = new ArrayList<>();
        try (Connection MCc = MCconnect(); Statement MCs = MCc.createStatement(); ResultSet MCr = MCs.executeQuery("SELECT * FROM template ORDER BY name")) {
            while (MCr.next()) MCresult.add(new MCTemplate(MCr.getLong("id"), MCr.getString("name"), MCr.getBytes("original"), MCr.getBytes("edited")));
        }
        return MCresult;
    }
    public List<MCTemplateGroup> MCgroups()throws SQLException{
        List<MCTemplateGroup> MCout=new ArrayList<>();try(Connection MCc=MCconnect();Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT g.id,g.name,COUNT(m.template_id) AS members FROM template_group g LEFT JOIN template_group_link m ON m.group_id=g.id GROUP BY g.id ORDER BY g.name")){while(MCr.next())MCout.add(new MCTemplateGroup(MCr.getLong(1),MCr.getString(2),MCr.getInt(3)));}return MCout;
    }
    public MCTemplateGroup MCsaveGroup(long MCid,String MCname)throws SQLException{
        String MCvalue=MCname==null?"":MCname.trim();if(MCvalue.isBlank())throw new IllegalArgumentException("Escriba el nombre del grupo.");
        try(Connection MCc=MCconnect()){
            if(MCid==0){try(PreparedStatement MCs=MCc.prepareStatement("INSERT INTO template_group(name) VALUES(?)",Statement.RETURN_GENERATED_KEYS)){MCs.setString(1,MCvalue);MCs.executeUpdate();try(ResultSet MCr=MCs.getGeneratedKeys()){return new MCTemplateGroup(MCr.next()?MCr.getLong(1):0,MCvalue,0);}}}
            try(PreparedStatement MCs=MCc.prepareStatement("UPDATE template_group SET name=? WHERE id=?")){MCs.setString(1,MCvalue);MCs.setLong(2,MCid);if(MCs.executeUpdate()!=1)throw new IllegalArgumentException("Grupo inexistente.");}
        }return MCgroups().stream().filter(MCg->MCg.MCid()==MCid).findFirst().orElseThrow();
    }
    public List<Long> MCgroupIds(long MCtemplateId)throws SQLException{List<Long> MCids=new ArrayList<>();try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("SELECT group_id FROM template_group_link WHERE template_id=? ORDER BY group_id")){MCs.setLong(1,MCtemplateId);try(ResultSet MCr=MCs.executeQuery()){while(MCr.next())MCids.add(MCr.getLong(1));}}return MCids;}
    public boolean MCinGroup(long MCtemplateId,long MCgroupId)throws SQLException{return MCgroupIds(MCtemplateId).contains(MCgroupId);}
    public Long MCgroupOf(long MCtemplateId)throws SQLException{List<Long> MCids=MCgroupIds(MCtemplateId);return MCids.isEmpty()?null:MCids.get(0);}
    public void MCsetTemplateGroups(long MCtemplateId,Collection<Long> MCids)throws SQLException{try(Connection MCc=MCconnect()){MCc.setAutoCommit(false);try(PreparedStatement MCs=MCc.prepareStatement("DELETE FROM template_group_link WHERE template_id=?")){MCs.setLong(1,MCtemplateId);MCs.executeUpdate();}for(Long MCid:new LinkedHashSet<>(MCids))try(PreparedStatement MCs=MCc.prepareStatement("INSERT INTO template_group_link(template_id,group_id) VALUES(?,?)")){MCs.setLong(1,MCtemplateId);MCs.setLong(2,MCid);MCs.executeUpdate();}MCc.commit();}}
    public void MCmoveTemplate(long MCtemplateId,Long MCgroupId)throws SQLException{MCsetTemplateGroups(MCtemplateId,MCgroupId==null?List.of():List.of(MCgroupId));}
    public void MCdeleteGroup(long MCid,Long MCdestination)throws SQLException{if(MCdestination!=null&&MCdestination==MCid)throw new IllegalArgumentException("Seleccione otro grupo.");try(Connection MCc=MCconnect()){MCc.setAutoCommit(false);if(MCdestination!=null)try(PreparedStatement MCs=MCc.prepareStatement("INSERT OR IGNORE INTO template_group_link(template_id,group_id) SELECT template_id,? FROM template_group_link WHERE group_id=?")){MCs.setLong(1,MCdestination);MCs.setLong(2,MCid);MCs.executeUpdate();}try(PreparedStatement MCs=MCc.prepareStatement("DELETE FROM template_group_link WHERE group_id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}try(PreparedStatement MCs=MCc.prepareStatement("DELETE FROM template_group_member WHERE group_id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}try(PreparedStatement MCs=MCc.prepareStatement("DELETE FROM template_group WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}MCc.commit();}}
    public List<String> MCtemplateDependencies(long MCid)throws SQLException{List<String> MCout=new ArrayList<>();try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("SELECT title FROM kind WHERE template_id=?")){MCs.setLong(1,MCid);try(ResultSet MCr=MCs.executeQuery()){while(MCr.next())MCout.add(MCr.getString(1));}}return MCout;}
    public void MCdeleteTemplate(long MCid)throws SQLException{List<String> MCuses=MCtemplateDependencies(MCid);if(!MCuses.isEmpty())throw new IllegalStateException("Plantilla utilizada en: "+String.join(", ",MCuses));MCdeleteTemplate(MCid,false);}
    public void MCdeleteTemplateWithLegacyKinds(long MCid)throws SQLException{MCdeleteTemplate(MCid,true);}
    private void MCdeleteTemplate(long MCid,boolean MCremoveKinds)throws SQLException{
        try(Connection MCc=MCconnect()){
            MCStudioData.MCmigrateHistory(MCc);MCc.setAutoCommit(false);
            try{
                if(MCremoveKinds)try(PreparedStatement MCs=MCc.prepareStatement("DELETE FROM kind WHERE template_id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}
                try(PreparedStatement MCs=MCc.prepareStatement("DELETE FROM template WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}
                MCc.commit();
            }catch(SQLException MCe){MCc.rollback();throw MCe;}
        }
    }

    public MCTemplate MCsaveTemplate(MCTemplate MCt) throws SQLException {
        if (MCt.MCid() == 0) {
            try (Connection MCc = MCconnect(); PreparedStatement MCs = MCc.prepareStatement("INSERT INTO template(name,original,edited) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                MCs.setString(1,MCt.MCname()); MCs.setBytes(2,MCt.MCoriginal()); MCs.setBytes(3,MCt.MCedited()); MCs.executeUpdate();
                try (ResultSet MCr = MCs.getGeneratedKeys()) { return new MCTemplate(MCr.next() ? MCr.getLong(1) : 0,MCt.MCname(),MCt.MCoriginal(),MCt.MCedited()); }
            }
        }
        try (Connection MCc = MCconnect(); PreparedStatement MCs = MCc.prepareStatement("UPDATE template SET name=?,edited=? WHERE id=?")) {
            MCs.setString(1,MCt.MCname()); MCs.setBytes(2,MCt.MCedited()); MCs.setLong(3,MCt.MCid()); MCs.executeUpdate();
        }
        return MCt;
    }

    public MCTemplate MCreplaceTemplate(MCTemplate MCt)throws SQLException{
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("UPDATE template SET name=?,original=?,edited=? WHERE id=?")){
            MCs.setString(1,MCt.MCname());MCs.setBytes(2,MCt.MCoriginal());MCs.setBytes(3,MCt.MCedited());MCs.setLong(4,MCt.MCid());MCs.executeUpdate();
        }
        return MCt;
    }

    public void MCclearVariables(long MCtemplateId)throws SQLException{
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM variable WHERE template_id=?")){MCs.setLong(1,MCtemplateId);MCs.executeUpdate();}
    }

    public List<MCVariable> MCvariables(long MCtemplateId) throws SQLException {
        List<MCVariable> MCresult = new ArrayList<>();
        try (Connection MCc = MCconnect(); PreparedStatement MCs = MCc.prepareStatement("SELECT * FROM variable WHERE template_id=? ORDER BY position,id")) {
            MCs.setLong(1,MCtemplateId);
            try (ResultSet MCr = MCs.executeQuery()) { while (MCr.next()) MCresult.add(new MCVariable(MCr.getLong("id"),MCtemplateId,MCr.getString("key"),MCr.getString("label"),MCr.getString("type"),MCr.getString("default_value"),MCr.getInt("required")!=0,MCr.getInt("position"))); }
        }
        return MCresult;
    }

    public MCVariable MCsaveVariable(MCVariable MCv) throws SQLException {
        if (MCv.MCid()==0) {
            try (Connection MCc=MCconnect()) {
                MCc.setAutoCommit(false);
                try{
                    long MCdefinition=MCfindMatchingDefinition(MCc,MCDocumentEngine.MCbaseKey(MCv.MCkey()),MCv.MClabel(),MCv.MCtype(),MCv.MCdefaultValue());
                    if(MCdefinition==0)MCdefinition=MCinsertDefinition(MCc,MCDocumentEngine.MCbaseKey(MCv.MCkey()),MCv.MClabel(),MCv.MCtype(),MCv.MCdefaultValue());
                    try(PreparedStatement MCs=MCc.prepareStatement("INSERT INTO variable(template_id,key,label,type,default_value,required,position,definition_id) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                        MCbindVariable(MCs,MCv);MCs.setLong(8,MCdefinition);MCs.executeUpdate();
                        try(ResultSet MCr=MCs.getGeneratedKeys()){MCVariable MCsaved=new MCVariable(MCr.next()?MCr.getLong(1):0,MCv.MCtemplateId(),MCv.MCkey(),MCv.MClabel(),MCv.MCtype(),MCv.MCdefaultValue(),MCv.MCrequired(),MCv.MCposition());MCc.commit();return MCsaved;}
                    }
                }catch(SQLException MCe){MCc.rollback();throw MCe;}
            }
        }
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("UPDATE variable SET key=?,label=?,type=?,default_value=?,required=?,position=? WHERE id=?")) {
            MCs.setString(1,MCv.MCkey());MCs.setString(2,MCv.MClabel());MCs.setString(3,MCv.MCtype());MCs.setString(4,MCv.MCdefaultValue());MCs.setInt(5,MCv.MCrequired()?1:0);MCs.setInt(6,MCv.MCposition());MCs.setLong(7,MCv.MCid());MCs.executeUpdate();
        }
        return MCv;
    }
    private static void MCbindVariable(PreparedStatement MCs,MCVariable MCv)throws SQLException{
        MCs.setLong(1,MCv.MCtemplateId());MCs.setString(2,MCv.MCkey());MCs.setString(3,MCv.MClabel());MCs.setString(4,MCv.MCtype());MCs.setString(5,MCv.MCdefaultValue());MCs.setInt(6,MCv.MCrequired()?1:0);MCs.setInt(7,MCv.MCposition());
    }
    public void MCdeleteVariable(long MCid)throws SQLException{
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM variable WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}
    }

    public List<MCKind> MCkinds() throws SQLException {
        List<MCKind> MCresult=new ArrayList<>();
        try(Connection MCc=MCconnect();Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT * FROM kind ORDER BY title")){
            while(MCr.next())MCresult.add(new MCKind(MCr.getLong("id"),MCr.getString("title"),MCr.getString("category"),MCr.getString("description"),MCr.getLong("template_id"),MCr.getString("fields"),MCr.getInt("active")!=0));
        }
        return MCresult;
    }
    public MCKind MCsaveKind(MCKind MCk)throws SQLException{
        if(MCk.MCid()==0){
            try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("INSERT INTO kind(title,category,description,template_id,fields,active) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                MCbindKind(MCs,MCk);MCs.executeUpdate();try(ResultSet MCr=MCs.getGeneratedKeys()){return new MCKind(MCr.next()?MCr.getLong(1):0,MCk.MCtitle(),MCk.MCcategory(),MCk.MCdescription(),MCk.MCtemplateId(),MCk.MCfields(),MCk.MCactive());}
            }
        }
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("UPDATE kind SET title=?,category=?,description=?,template_id=?,fields=?,active=? WHERE id=?")){
            MCbindKind(MCs,MCk);MCs.setLong(7,MCk.MCid());MCs.executeUpdate();
        }
        return MCk;
    }
    private static void MCbindKind(PreparedStatement MCs,MCKind MCk)throws SQLException{
        MCs.setString(1,MCk.MCtitle());MCs.setString(2,MCk.MCcategory());MCs.setString(3,MCk.MCdescription());MCs.setLong(4,MCk.MCtemplateId());MCs.setString(5,MCk.MCfields());MCs.setInt(6,MCk.MCactive()?1:0);
    }
    public void MCdeleteKind(long MCid)throws SQLException{
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("DELETE FROM kind WHERE id=?")){MCs.setLong(1,MCid);MCs.executeUpdate();}
    }
    public Map<String,Boolean> MCkindRequirements(long MCkindId)throws SQLException{
        Map<String,Boolean> MCout=new HashMap<>();
        try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("SELECT key,required FROM kind_field_rule WHERE kind_id=?")){
            MCs.setLong(1,MCkindId);try(ResultSet MCr=MCs.executeQuery()){while(MCr.next())MCout.put(MCr.getString(1),MCr.getInt(2)!=0);}
        }
        return MCout;
    }
    public void MCsaveKindRequirements(long MCkindId,Map<String,Boolean> MCchoices)throws SQLException{
        MCsaveKindFieldRules(MCkindId,MCchoices,MCkindDefaults(MCkindId));
    }
    public Map<String,String> MCkindDefaults(long MCkindId)throws SQLException{
        Map<String,String> MCresult=new HashMap<>();try(Connection MCc=MCconnect();PreparedStatement MCs=MCc.prepareStatement("SELECT key,default_value FROM kind_field_rule WHERE kind_id=? AND default_value IS NOT NULL")){MCs.setLong(1,MCkindId);try(ResultSet MCr=MCs.executeQuery()){while(MCr.next())MCresult.put(MCr.getString(1),MCr.getString(2));}}return MCresult;
    }
    public void MCsaveKindFieldRules(long MCkindId,Map<String,Boolean> MCchoices,Map<String,String> MCdefaults)throws SQLException{
        try(Connection MCc=MCconnect()){
            MCc.setAutoCommit(false);
            try(PreparedStatement MCclear=MCc.prepareStatement("DELETE FROM kind_field_rule WHERE kind_id=?")){MCclear.setLong(1,MCkindId);MCclear.executeUpdate();}
            try(PreparedStatement MCinsert=MCc.prepareStatement("INSERT INTO kind_field_rule(kind_id,key,required,default_value) VALUES(?,?,?,?)")){
                Set<String> MCkeys=new LinkedHashSet<>(MCchoices.keySet());MCkeys.addAll(MCdefaults.keySet());
                for(String MCkey:MCkeys){MCinsert.setLong(1,MCkindId);MCinsert.setString(2,MCkey);MCinsert.setInt(3,MCchoices.getOrDefault(MCkey,false)?1:0);MCinsert.setString(4,MCdefaults.get(MCkey));MCinsert.addBatch();}
                MCinsert.executeBatch();
            }
            MCc.commit();
        }
    }
}
