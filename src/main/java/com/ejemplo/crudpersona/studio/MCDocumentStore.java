package com.ejemplo.crudpersona.studio;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** @author MC */
public final class MCDocumentStore {
    /** @author MC */
    public record MCTemplate(@com.fasterxml.jackson.annotation.JsonProperty("id") long mcId, @com.fasterxml.jackson.annotation.JsonProperty("name") String mcName, @com.fasterxml.jackson.annotation.JsonProperty("original") byte[] mcOriginal, @com.fasterxml.jackson.annotation.JsonProperty("edited") byte[] mcEdited) {
        @Override public String toString() { return mcName; }
    }
    /** @author MC */
    public record MCVariable(@com.fasterxml.jackson.annotation.JsonProperty("id") long mcId, @com.fasterxml.jackson.annotation.JsonProperty("templateId") long mcTemplateId, @com.fasterxml.jackson.annotation.JsonProperty("key") String mcKey, @com.fasterxml.jackson.annotation.JsonProperty("label") String mcLabel, @com.fasterxml.jackson.annotation.JsonProperty("type") String mcType,
                           @com.fasterxml.jackson.annotation.JsonProperty("defaultValue") String mcDefaultValue, @com.fasterxml.jackson.annotation.JsonProperty("required") boolean mcRequired, @com.fasterxml.jackson.annotation.JsonProperty("position") int mcPosition) {
        @Override public String toString() { return mcLabel + "  {{" + mcKey + "}}"; }
    }
    public static final List<String> mcDATE_FORMATS=List.of("dd/MM/yyyy","d 'de' MMMM 'de' yyyy","EEEE, d 'de' MMMM 'de' yyyy","dd-MM-yyyy","yyyy-MM-dd");
    /** @author MC */
    public record MCDefinition(@com.fasterxml.jackson.annotation.JsonProperty("id") long mcId, @com.fasterxml.jackson.annotation.JsonProperty("key") String mcKey, @com.fasterxml.jackson.annotation.JsonProperty("label") String mcLabel, @com.fasterxml.jackson.annotation.JsonProperty("type") String mcType, @com.fasterxml.jackson.annotation.JsonProperty("defaultValue") String mcDefaultValue,@com.fasterxml.jackson.annotation.JsonProperty("autoCurrentDate") boolean mcAutoCurrentDate,@com.fasterxml.jackson.annotation.JsonProperty("dateFormat") String mcDateFormat,@com.fasterxml.jackson.annotation.JsonProperty("bindToClient") boolean mcBindToClient) {
        public MCDefinition(long mcId,String mcKey,String mcLabel,String mcType,String mcDefaultValue){this(mcId,mcKey,mcLabel,mcType,mcDefaultValue,false,"dd/MM/yyyy",true);}
        public MCDefinition(long mcId,String mcKey,String mcLabel,String mcType,String mcDefaultValue,boolean mcAutoCurrentDate,String mcDateFormat){this(mcId,mcKey,mcLabel,mcType,mcDefaultValue,mcAutoCurrentDate,mcDateFormat,true);}
        public String mcInitialValue(){return !mcBindToClient?(mcDefaultValue==null?"":mcDefaultValue):mcAutoCurrentDate?mcCurrentDate():MCVariableInput.mcInitialValue(mcType,mcDefaultValue);}
        public String mcEditorKey(String mcPersona){return MCDocumentEngine.mcQualify(mcBindToClient?mcPersona:"",mcKey);}
        public String mcCurrentDate(){return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern(mcDateFormat,Locale.forLanguageTag("es-PE")));}
        @Override public String toString() { return mcLabel + "  {{" + mcKey + "}} · " + mcType + " #" + mcId; }
    }
    /** @author MC */
    public record MCKind(@com.fasterxml.jackson.annotation.JsonProperty("id") long mcId, @com.fasterxml.jackson.annotation.JsonProperty("title") String mcTitle, @com.fasterxml.jackson.annotation.JsonProperty("category") String mcCategory, @com.fasterxml.jackson.annotation.JsonProperty("description") String mcDescription,
                       @com.fasterxml.jackson.annotation.JsonProperty("templateId") long mcTemplateId, @com.fasterxml.jackson.annotation.JsonProperty("fields") String mcFields, @com.fasterxml.jackson.annotation.JsonProperty("active") boolean mcActive) {
        @Override public String toString() { return mcTitle; }
    }
    /** @author MC */
    public record MCTemplateGroup(@com.fasterxml.jackson.annotation.JsonProperty("id") long mcId,@com.fasterxml.jackson.annotation.JsonProperty("name") String mcName,@com.fasterxml.jackson.annotation.JsonProperty("count") int mcCount){@Override public String toString(){return mcName+" ("+mcCount+")";}}

    /** @author MC */
    public record MCPersona(@com.fasterxml.jackson.annotation.JsonProperty("id") long mcId,@com.fasterxml.jackson.annotation.JsonProperty("templateId") long mcTemplateId,@com.fasterxml.jackson.annotation.JsonProperty("personaKey") String mcPersonaKey,@com.fasterxml.jackson.annotation.JsonProperty("displayName") String mcDisplayName,@com.fasterxml.jackson.annotation.JsonProperty("sortOrder") int mcSortOrder){@Override public String toString(){return mcDisplayName;}}
    public List<MCPersona> mcPersonas(long mcTemplateId)throws SQLException{
        List<MCPersona> mcOut=new ArrayList<>();try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("SELECT * FROM template_persona WHERE template_id=? ORDER BY sort_order,id")){mcS.setLong(1,mcTemplateId);try(ResultSet mcR=mcS.executeQuery()){while(mcR.next())mcOut.add(new MCPersona(mcR.getLong("id"),mcTemplateId,mcR.getString("persona_key"),mcR.getString("display_name"),mcR.getInt("sort_order")));}}return mcOut;
    }
    public MCPersona mcAddPersona(long mcTemplateId,String mcDisplayName)throws SQLException{
        List<MCPersona> mcExisting=mcPersonas(mcTemplateId);int mcIndex=1;Set<String> mcKeys=new HashSet<>();mcExisting.forEach(mcP->mcKeys.add(mcP.mcPersonaKey()));while(mcKeys.contains("Persona_"+mcIndex))mcIndex++;
        return mcAddPersona(mcTemplateId,"Persona_"+mcIndex,mcDisplayName==null||mcDisplayName.isBlank()?"Persona "+mcIndex:mcDisplayName,mcExisting.stream().mapToInt(MCPersona::mcSortOrder).max().orElse(-1)+1);
    }
    public MCPersona mcAddPersona(long mcTemplateId,String mcKey,String mcName,int mcOrder)throws SQLException{
        if(!mcKey.matches("[a-zA-Z][a-zA-Z0-9_]*")||mcName==null||mcName.isBlank())throw new IllegalArgumentException("Persona inválida.");
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("INSERT INTO template_persona(template_id,persona_key,display_name,sort_order) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){mcS.setLong(1,mcTemplateId);mcS.setString(2,mcKey);mcS.setString(3,mcName.trim());mcS.setInt(4,mcOrder);mcS.executeUpdate();try(ResultSet mcR=mcS.getGeneratedKeys()){return new MCPersona(mcR.next()?mcR.getLong(1):0,mcTemplateId,mcKey,mcName.trim(),mcOrder);}}
    }
    public void mcRenamePersona(long mcId,String mcName)throws SQLException{if(mcName==null||mcName.isBlank())throw new IllegalArgumentException("Escriba un nombre o rol.");try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("UPDATE template_persona SET display_name=? WHERE id=?")){mcS.setString(1,mcName.trim());mcS.setLong(2,mcId);mcS.executeUpdate();}}
    public void mcDeletePersona(long mcId)throws SQLException{try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM template_persona WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}}
    /** @author MC */
    public record MCComplement(@com.fasterxml.jackson.annotation.JsonProperty("id") long mcId,@com.fasterxml.jackson.annotation.JsonProperty("sourceDefId") long mcSourceDefId,@com.fasterxml.jackson.annotation.JsonProperty("targetDefId") long mcTargetDefId,@com.fasterxml.jackson.annotation.JsonProperty("bidirectional") boolean mcBidirectional){}
    private final Path mcDirectory;
    private final String mcUrl;

    public MCDocumentStore() throws Exception {
        this(mcDefaultDirectory());
    }

    private static Path mcDefaultDirectory() {
        String mcAppData = System.getenv("APPDATA");
        return mcAppData != null && !mcAppData.isBlank()
                ? Path.of(mcAppData, "DocumentStudio")
                : Path.of(System.getProperty("user.home"), ".document-studio");
    }

    public MCDocumentStore(Path mcDirectory) throws Exception {
        this.mcDirectory = mcDirectory;
        Files.createDirectories(mcDirectory);
        mcUrl = "jdbc:sqlite:" + mcDirectory.resolve("studio.db");
        Class.forName("org.sqlite.JDBC");
        try (Connection mcC = mcConnect(); Statement mcS = mcC.createStatement()) {
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS template (id INTEGER PRIMARY KEY, name TEXT NOT NULL, original BLOB NOT NULL, edited BLOB NOT NULL)");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS template_group (id INTEGER PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE UNIQUE)");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS template_group_member (template_id INTEGER PRIMARY KEY REFERENCES template(id) ON DELETE CASCADE, group_id INTEGER NOT NULL REFERENCES template_group(id) ON DELETE CASCADE)");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS template_group_link (template_id INTEGER NOT NULL REFERENCES template(id) ON DELETE CASCADE, group_id INTEGER NOT NULL REFERENCES template_group(id) ON DELETE CASCADE, PRIMARY KEY(template_id,group_id))");
            mcS.executeUpdate("INSERT OR IGNORE INTO template_group_link(template_id,group_id) SELECT template_id,group_id FROM template_group_member");
            mcS.executeUpdate("DELETE FROM template_group_member");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS template_persona(id INTEGER PRIMARY KEY AUTOINCREMENT,template_id INTEGER NOT NULL REFERENCES template(id) ON DELETE CASCADE,persona_key TEXT NOT NULL,display_name TEXT NOT NULL,sort_order INTEGER NOT NULL DEFAULT 0,UNIQUE(template_id,persona_key))");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS variable (id INTEGER PRIMARY KEY, template_id INTEGER NOT NULL REFERENCES template(id) ON DELETE CASCADE, key TEXT NOT NULL, label TEXT NOT NULL, type TEXT NOT NULL, default_value TEXT, required INTEGER NOT NULL, position INTEGER NOT NULL, UNIQUE(template_id,key))");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS variable_definition (id INTEGER PRIMARY KEY, key TEXT NOT NULL, label TEXT NOT NULL, type TEXT NOT NULL, default_value TEXT)");
            Set<String> mcDefinitionColumns=new HashSet<>();try(ResultSet mcR=mcS.executeQuery("PRAGMA table_info(variable_definition)")){while(mcR.next())mcDefinitionColumns.add(mcR.getString("name"));}
            if(!mcDefinitionColumns.contains("auto_current_date"))mcS.executeUpdate("ALTER TABLE variable_definition ADD COLUMN auto_current_date INTEGER NOT NULL DEFAULT 0");
            if(!mcDefinitionColumns.contains("bind_to_client"))mcS.executeUpdate("ALTER TABLE variable_definition ADD COLUMN bind_to_client INTEGER NOT NULL DEFAULT 1");
            if(!mcDefinitionColumns.contains("date_format"))mcS.executeUpdate("ALTER TABLE variable_definition ADD COLUMN date_format TEXT NOT NULL DEFAULT 'dd/MM/yyyy'");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS variable_complement(id INTEGER PRIMARY KEY AUTOINCREMENT,source_def_id INTEGER NOT NULL REFERENCES variable_definition(id) ON DELETE CASCADE,target_def_id INTEGER NOT NULL REFERENCES variable_definition(id) ON DELETE CASCADE,bidirectional INTEGER NOT NULL DEFAULT 1,UNIQUE(source_def_id,target_def_id))");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS kind (id INTEGER PRIMARY KEY, title TEXT NOT NULL, category TEXT, description TEXT, template_id INTEGER NOT NULL REFERENCES template(id), fields TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL)");
            mcS.executeUpdate("CREATE TABLE IF NOT EXISTS kind_field_rule (kind_id INTEGER NOT NULL REFERENCES kind(id) ON DELETE CASCADE, key TEXT NOT NULL, required INTEGER NOT NULL, PRIMARY KEY(kind_id,key))");
            boolean mcHasDefault=false;
            try(ResultSet mcColumns=mcS.executeQuery("PRAGMA table_info(kind_field_rule)")){while(mcColumns.next())if("default_value".equals(mcColumns.getString("name")))mcHasDefault=true;}
            if(!mcHasDefault)mcS.executeUpdate("ALTER TABLE kind_field_rule ADD COLUMN default_value TEXT");
            mcS.executeUpdate("PRAGMA foreign_keys=ON");
        }
        mcMigrateDefinitions();
    }

    public Path mcDirectory() { return mcDirectory; }
    private Connection mcConnect() throws SQLException {
        Connection mcC = DriverManager.getConnection(mcUrl);
        try (Statement mcS = mcC.createStatement()) { mcS.execute("PRAGMA foreign_keys=ON"); }
        return mcC;
    }

    private void mcMigrateDefinitions() throws SQLException {
        try(Connection mcC=mcConnect()) {
            boolean mcHasColumn=false;
            try(Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("PRAGMA table_info(variable)")) {
                while(mcR.next())if("definition_id".equals(mcR.getString("name")))mcHasColumn=true;
            }
            if(!mcHasColumn)try(Statement mcS=mcC.createStatement()){mcS.executeUpdate("ALTER TABLE variable ADD COLUMN definition_id INTEGER REFERENCES variable_definition(id)");}
            mcC.setAutoCommit(false);
            try(Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT id,key,label,type,default_value FROM variable WHERE definition_id IS NULL")) {
                while(mcR.next()){
                    long mcDefinition=mcFindMatchingDefinition(mcC,mcR.getString("key"),mcR.getString("label"),mcR.getString("type"),mcR.getString("default_value"));
                    if(mcDefinition==0)mcDefinition=mcInsertDefinition(mcC,mcR.getString("key"),mcR.getString("label"),mcR.getString("type"),mcR.getString("default_value"));
                    try(PreparedStatement mcUpdate=mcC.prepareStatement("UPDATE variable SET definition_id=? WHERE id=?")){mcUpdate.setLong(1,mcDefinition);mcUpdate.setLong(2,mcR.getLong("id"));mcUpdate.executeUpdate();}
                }
            }
            mcC.commit();
        }
    }
    private static long mcFindMatchingDefinition(Connection mcC,String mcKey,String mcLabel,String mcType,String mcValue)throws SQLException {
        try(PreparedStatement mcS=mcC.prepareStatement("SELECT id FROM variable_definition WHERE key=? AND label=? AND type=? AND COALESCE(default_value,'')=COALESCE(?,'') ORDER BY id LIMIT 1")) {
            mcS.setString(1,mcKey);mcS.setString(2,mcLabel);mcS.setString(3,mcType);mcS.setString(4,mcValue);
            try(ResultSet mcR=mcS.executeQuery()){return mcR.next()?mcR.getLong(1):0;}
        }
    }
    private static long mcInsertDefinition(Connection mcC,String mcKey,String mcLabel,String mcType,String mcValue)throws SQLException {
        try(PreparedStatement mcS=mcC.prepareStatement("INSERT INTO variable_definition(key,label,type,default_value) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
            mcS.setString(1,mcKey);mcS.setString(2,mcLabel);mcS.setString(3,mcType);mcS.setString(4,mcValue);mcS.executeUpdate();
            try(ResultSet mcR=mcS.getGeneratedKeys()){if(mcR.next())return mcR.getLong(1);throw new SQLException("No se creó la definición.");}
        }
    }
    public List<MCDefinition> mcDefinitions()throws SQLException {
        List<MCDefinition> mcOut=new ArrayList<>();
        try(Connection mcC=mcConnect();Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT * FROM variable_definition ORDER BY label,key,id")){
            while(mcR.next())mcOut.add(new MCDefinition(mcR.getLong("id"),mcR.getString("key"),mcR.getString("label"),mcR.getString("type"),mcR.getString("default_value"),mcR.getInt("auto_current_date")==1,mcR.getString("date_format"),mcR.getInt("bind_to_client")==1));
        }
        return mcOut;
    }
    public MCDefinition mcSaveDefinition(MCDefinition mcD)throws SQLException {
        if(!mcD.mcKey().matches("[a-zA-Z][a-zA-Z0-9_]*")||mcD.mcLabel().isBlank()||mcD.mcType().isBlank())throw new IllegalArgumentException("Definición de variable incompleta.");
        try(Connection mcC=mcConnect()){
            mcC.setAutoCommit(false);
            try{
                if(mcD.mcId()==0){
                    try(PreparedStatement mcCheck=mcC.prepareStatement("SELECT id FROM variable_definition WHERE key=? LIMIT 1")){mcCheck.setString(1,mcD.mcKey());try(ResultSet mcR=mcCheck.executeQuery()){if(mcR.next())throw new IllegalArgumentException("El identificador ya existe. Use otro o edite la variable existente.");}}
                    long mcId=mcInsertDefinition(mcC,mcD.mcKey(),mcD.mcLabel(),mcD.mcType(),mcD.mcDefaultValue());mcSaveDate(mcC,mcId,mcD);mcC.commit();return new MCDefinition(mcId,mcD.mcKey(),mcD.mcLabel(),mcD.mcType(),mcD.mcDefaultValue(),mcD.mcAutoCurrentDate(),mcD.mcDateFormat(),mcD.mcBindToClient());
                }
                try(PreparedStatement mcS=mcC.prepareStatement("UPDATE variable_definition SET label=?,type=?,default_value=? WHERE id=? AND key=?")){
                    mcS.setString(1,mcD.mcLabel());mcS.setString(2,mcD.mcType());mcS.setString(3,mcD.mcDefaultValue());mcS.setLong(4,mcD.mcId());mcS.setString(5,mcD.mcKey());
                    if(mcS.executeUpdate()!=1)throw new IllegalArgumentException("El identificador es estable y no puede cambiarse.");
                }
                try(PreparedStatement mcS=mcC.prepareStatement("UPDATE variable SET label=?,type=?,default_value=? WHERE definition_id=?")){
                    mcS.setString(1,mcD.mcLabel());mcS.setString(2,mcD.mcType());mcS.setString(3,mcD.mcDefaultValue());mcS.setLong(4,mcD.mcId());mcS.executeUpdate();
                }
                mcSaveDate(mcC,mcD.mcId(),mcD);mcC.commit();return mcD;
            }catch(Exception mcE){mcC.rollback();if(mcE instanceof SQLException mcSql)throw mcSql;throw mcE;}
        }
    }
    private static void mcSaveDate(Connection mcC,long mcId,MCDefinition mcDefinition)throws SQLException{
        if(!mcDATE_FORMATS.contains(mcDefinition.mcDateFormat()))throw new IllegalArgumentException("Formato de fecha inválido.");
        try(PreparedStatement mcS=mcC.prepareStatement("UPDATE variable_definition SET auto_current_date=?,date_format=?,bind_to_client=? WHERE id=?")){mcS.setInt(1,mcDefinition.mcAutoCurrentDate()?1:0);mcS.setString(2,mcDefinition.mcDateFormat());mcS.setInt(3,mcDefinition.mcBindToClient()?1:0);mcS.setLong(4,mcId);mcS.executeUpdate();}
    }
    public MCDefinition mcImportDefinition(MCDefinition mcD)throws SQLException {
        if(!mcD.mcKey().matches("[a-zA-Z][a-zA-Z0-9_]*")||mcD.mcLabel().isBlank()||mcD.mcType().isBlank())throw new IllegalArgumentException("Definición importada inválida.");
        try(Connection mcC=mcConnect()){
            long mcId=0;try(PreparedStatement mcS=mcC.prepareStatement("SELECT id FROM variable_definition WHERE key=? AND label=? AND type=? AND COALESCE(default_value,'')=COALESCE(?,'') AND auto_current_date=? AND date_format=? AND bind_to_client=?")){mcS.setString(1,mcD.mcKey());mcS.setString(2,mcD.mcLabel());mcS.setString(3,mcD.mcType());mcS.setString(4,mcD.mcDefaultValue());mcS.setInt(5,mcD.mcAutoCurrentDate()?1:0);mcS.setString(6,mcD.mcDateFormat());mcS.setInt(7,mcD.mcBindToClient()?1:0);try(ResultSet mcR=mcS.executeQuery()){if(mcR.next())mcId=mcR.getLong(1);}}
            if(mcId==0){mcId=mcInsertDefinition(mcC,mcD.mcKey(),mcD.mcLabel(),mcD.mcType(),mcD.mcDefaultValue());mcSaveDate(mcC,mcId,mcD);}
            return new MCDefinition(mcId,mcD.mcKey(),mcD.mcLabel(),mcD.mcType(),mcD.mcDefaultValue(),mcD.mcAutoCurrentDate(),mcD.mcDateFormat(),mcD.mcBindToClient());
        }
    }
    public long mcDefinitionId(long mcVariableId)throws SQLException {
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("SELECT definition_id FROM variable WHERE id=?")){
            mcS.setLong(1,mcVariableId);try(ResultSet mcR=mcS.executeQuery()){return mcR.next()?mcR.getLong(1):0;}
        }
    }
    public List<MCComplement> mcComplements(long mcDefinitionId)throws SQLException{
        List<MCComplement> mcOut=new ArrayList<>();for(MCComplement mcLink:mcComplements()){
            if(mcLink.mcSourceDefId()==mcDefinitionId)mcOut.add(mcLink);
            else if(mcLink.mcBidirectional()&&mcLink.mcTargetDefId()==mcDefinitionId)mcOut.add(new MCComplement(mcLink.mcId(),mcDefinitionId,mcLink.mcSourceDefId(),true));
        }return mcOut;
    }
    public List<MCComplement> mcComplements()throws SQLException{
        List<MCComplement> mcOut=new ArrayList<>();try(Connection mcC=mcConnect();Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT * FROM variable_complement ORDER BY id")){while(mcR.next())mcOut.add(new MCComplement(mcR.getLong("id"),mcR.getLong("source_def_id"),mcR.getLong("target_def_id"),mcR.getInt("bidirectional")==1));}return mcOut;
    }
    public void mcSaveComplement(long mcSource,long mcTarget,boolean mcBidirectional)throws SQLException{
        if(mcSource==mcTarget)throw new IllegalArgumentException("Elija otra variable como complemento.");
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("INSERT INTO variable_complement(source_def_id,target_def_id,bidirectional) VALUES(?,?,?) ON CONFLICT(source_def_id,target_def_id) DO UPDATE SET bidirectional=excluded.bidirectional")){mcS.setLong(1,mcSource);mcS.setLong(2,mcTarget);mcS.setInt(3,mcBidirectional?1:0);mcS.executeUpdate();}
    }
    public void mcDeleteComplement(long mcId)throws SQLException{try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM variable_complement WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}}
    public List<String> mcDefinitionDependencies(long mcId)throws SQLException {
        return mcDefinitionTemplates(mcId).stream().map(MCTemplate::mcName).distinct().toList();
    }
    public List<MCTemplate> mcDefinitionTemplates(long mcId)throws SQLException {
        Set<Long> mcIds=new LinkedHashSet<>();
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("SELECT template_id FROM variable WHERE definition_id=?")){
            mcS.setLong(1,mcId);try(ResultSet mcR=mcS.executeQuery()){while(mcR.next())mcIds.add(mcR.getLong(1));}
        }
        String mcKey=null;try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("SELECT key FROM variable_definition WHERE id=?")){mcS.setLong(1,mcId);try(ResultSet mcR=mcS.executeQuery()){if(mcR.next())mcKey=mcR.getString(1);}}
        List<MCTemplate> mcTemplates=mcTemplates();
        final String mcDefinitionKey=mcKey;if(mcKey!=null)for(MCTemplate mcTemplate:mcTemplates)try(var mcDocument=MCDocumentEngine.mcOpen(mcTemplate.mcEdited())){if(MCDocumentEngine.mcMarkers(mcDocument).stream().anyMatch(mcToken->MCDocumentEngine.mcBaseKey(mcToken).equals(mcDefinitionKey)))mcIds.add(mcTemplate.mcId());}catch(Exception mcE){throw new SQLException("No se pudieron comprobar las plantillas antes de eliminar la variable.",mcE);}
        return mcTemplates.stream().filter(mcT->mcIds.contains(mcT.mcId())).toList();
    }
    public void mcDeleteDefinition(long mcId)throws SQLException {
        List<String> mcUses=mcDefinitionDependencies(mcId);if(!mcUses.isEmpty())throw new IllegalStateException("Variable utilizada en: "+String.join(", ",mcUses));
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM variable_definition WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}
    }
    public MCVariable mcBindDefinition(long mcTemplateId,long mcDefinitionId,boolean mcRequired,int mcPosition)throws SQLException{return mcBindDefinition(mcTemplateId,mcDefinitionId,null,mcRequired,mcPosition);}
    public MCVariable mcBindDefinition(long mcTemplateId,long mcDefinitionId,String mcQualifiedKey,boolean mcRequired,int mcPosition)throws SQLException {
        MCDefinition mcD=mcDefinitions().stream().filter(mcX->mcX.mcId()==mcDefinitionId).findFirst().orElseThrow(()->new IllegalArgumentException("Variable inexistente."));
        String mcKey=mcQualifiedKey==null?mcD.mcKey():mcQualifiedKey;if(!MCDocumentEngine.mcValidKey(mcKey)||!MCDocumentEngine.mcBaseKey(mcKey).equals(mcD.mcKey()))throw new IllegalArgumentException("Variable calificada inválida.");if(!mcD.mcBindToClient())mcKey=mcD.mcKey();
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("INSERT INTO variable(template_id,key,label,type,default_value,required,position,definition_id) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            mcS.setLong(1,mcTemplateId);mcS.setString(2,mcKey);mcS.setString(3,mcD.mcLabel());mcS.setString(4,mcD.mcType());mcS.setString(5,mcD.mcDefaultValue());mcS.setInt(6,mcRequired?1:0);mcS.setInt(7,mcPosition);mcS.setLong(8,mcDefinitionId);mcS.executeUpdate();
            try(ResultSet mcR=mcS.getGeneratedKeys()){return new MCVariable(mcR.next()?mcR.getLong(1):0,mcTemplateId,mcKey,mcD.mcLabel(),mcD.mcType(),mcD.mcDefaultValue(),mcRequired,mcPosition);}
        }
    }
    public void mcLinkDefinition(long mcVariableId,long mcDefinitionId)throws SQLException {
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("UPDATE variable SET definition_id=? WHERE id=?")){mcS.setLong(1,mcDefinitionId);mcS.setLong(2,mcVariableId);mcS.executeUpdate();}
    }

    public List<MCTemplate> mcTemplates() throws SQLException {
        List<MCTemplate> mcResult = new ArrayList<>();
        try (Connection mcC = mcConnect(); Statement mcS = mcC.createStatement(); ResultSet mcR = mcS.executeQuery("SELECT * FROM template ORDER BY name")) {
            while (mcR.next()) mcResult.add(new MCTemplate(mcR.getLong("id"), mcR.getString("name"), mcR.getBytes("original"), mcR.getBytes("edited")));
        }
        return mcResult;
    }
    public List<MCTemplateGroup> mcGroups()throws SQLException{
        List<MCTemplateGroup> mcOut=new ArrayList<>();try(Connection mcC=mcConnect();Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT g.id,g.name,COUNT(m.template_id) AS members FROM template_group g LEFT JOIN template_group_link m ON m.group_id=g.id GROUP BY g.id ORDER BY g.name")){while(mcR.next())mcOut.add(new MCTemplateGroup(mcR.getLong(1),mcR.getString(2),mcR.getInt(3)));}return mcOut;
    }
    public MCTemplateGroup mcSaveGroup(long mcId,String mcName)throws SQLException{
        String mcValue=mcName==null?"":mcName.trim();if(mcValue.isBlank())throw new IllegalArgumentException("Escriba el nombre del grupo.");
        try(Connection mcC=mcConnect()){
            if(mcId==0){try(PreparedStatement mcS=mcC.prepareStatement("INSERT INTO template_group(name) VALUES(?)",Statement.RETURN_GENERATED_KEYS)){mcS.setString(1,mcValue);mcS.executeUpdate();try(ResultSet mcR=mcS.getGeneratedKeys()){return new MCTemplateGroup(mcR.next()?mcR.getLong(1):0,mcValue,0);}}}
            try(PreparedStatement mcS=mcC.prepareStatement("UPDATE template_group SET name=? WHERE id=?")){mcS.setString(1,mcValue);mcS.setLong(2,mcId);if(mcS.executeUpdate()!=1)throw new IllegalArgumentException("Grupo inexistente.");}
        }return mcGroups().stream().filter(mcG->mcG.mcId()==mcId).findFirst().orElseThrow();
    }
    public List<Long> mcGroupIds(long mcTemplateId)throws SQLException{List<Long> mcIds=new ArrayList<>();try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("SELECT group_id FROM template_group_link WHERE template_id=? ORDER BY group_id")){mcS.setLong(1,mcTemplateId);try(ResultSet mcR=mcS.executeQuery()){while(mcR.next())mcIds.add(mcR.getLong(1));}}return mcIds;}
    public boolean mcInGroup(long mcTemplateId,long mcGroupId)throws SQLException{return mcGroupIds(mcTemplateId).contains(mcGroupId);}
    public Long mcGroupOf(long mcTemplateId)throws SQLException{List<Long> mcIds=mcGroupIds(mcTemplateId);return mcIds.isEmpty()?null:mcIds.get(0);}
    public void mcSetTemplateGroups(long mcTemplateId,Collection<Long> mcIds)throws SQLException{try(Connection mcC=mcConnect()){mcC.setAutoCommit(false);try(PreparedStatement mcS=mcC.prepareStatement("DELETE FROM template_group_link WHERE template_id=?")){mcS.setLong(1,mcTemplateId);mcS.executeUpdate();}for(Long mcId:new LinkedHashSet<>(mcIds))try(PreparedStatement mcS=mcC.prepareStatement("INSERT INTO template_group_link(template_id,group_id) VALUES(?,?)")){mcS.setLong(1,mcTemplateId);mcS.setLong(2,mcId);mcS.executeUpdate();}mcC.commit();}}
    public void mcMoveTemplate(long mcTemplateId,Long mcGroupId)throws SQLException{mcSetTemplateGroups(mcTemplateId,mcGroupId==null?List.of():List.of(mcGroupId));}
    public void mcDeleteGroup(long mcId,Long mcDestination)throws SQLException{if(mcDestination!=null&&mcDestination==mcId)throw new IllegalArgumentException("Seleccione otro grupo.");try(Connection mcC=mcConnect()){mcC.setAutoCommit(false);if(mcDestination!=null)try(PreparedStatement mcS=mcC.prepareStatement("INSERT OR IGNORE INTO template_group_link(template_id,group_id) SELECT template_id,? FROM template_group_link WHERE group_id=?")){mcS.setLong(1,mcDestination);mcS.setLong(2,mcId);mcS.executeUpdate();}try(PreparedStatement mcS=mcC.prepareStatement("DELETE FROM template_group_link WHERE group_id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}try(PreparedStatement mcS=mcC.prepareStatement("DELETE FROM template_group_member WHERE group_id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}try(PreparedStatement mcS=mcC.prepareStatement("DELETE FROM template_group WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}mcC.commit();}}
    public List<String> mcTemplateDependencies(long mcId)throws SQLException{List<String> mcOut=new ArrayList<>();try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("SELECT title FROM kind WHERE template_id=?")){mcS.setLong(1,mcId);try(ResultSet mcR=mcS.executeQuery()){while(mcR.next())mcOut.add(mcR.getString(1));}}return mcOut;}
    public void mcDeleteTemplate(long mcId)throws SQLException{List<String> mcUses=mcTemplateDependencies(mcId);if(!mcUses.isEmpty())throw new IllegalStateException("Plantilla utilizada en: "+String.join(", ",mcUses));mcDeleteTemplate(mcId,false);}
    public void mcDeleteTemplateWithLegacyKinds(long mcId)throws SQLException{mcDeleteTemplate(mcId,true);}
    private void mcDeleteTemplate(long mcId,boolean mcRemoveKinds)throws SQLException{
        try(Connection mcC=mcConnect()){
            MCStudioData.mcMigrateHistory(mcC);mcC.setAutoCommit(false);
            try{
                if(mcRemoveKinds)try(PreparedStatement mcS=mcC.prepareStatement("DELETE FROM kind WHERE template_id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}
                try(PreparedStatement mcS=mcC.prepareStatement("DELETE FROM template WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}
                mcC.commit();
            }catch(SQLException mcE){mcC.rollback();throw mcE;}
        }
    }

    public MCTemplate mcSaveTemplate(MCTemplate mcT) throws SQLException {
        if (mcT.mcId() == 0) {
            try (Connection mcC = mcConnect(); PreparedStatement mcS = mcC.prepareStatement("INSERT INTO template(name,original,edited) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                mcS.setString(1,mcT.mcName()); mcS.setBytes(2,mcT.mcOriginal()); mcS.setBytes(3,mcT.mcEdited()); mcS.executeUpdate();
                try (ResultSet mcR = mcS.getGeneratedKeys()) { return new MCTemplate(mcR.next() ? mcR.getLong(1) : 0,mcT.mcName(),mcT.mcOriginal(),mcT.mcEdited()); }
            }
        }
        try (Connection mcC = mcConnect(); PreparedStatement mcS = mcC.prepareStatement("UPDATE template SET name=?,edited=? WHERE id=?")) {
            mcS.setString(1,mcT.mcName()); mcS.setBytes(2,mcT.mcEdited()); mcS.setLong(3,mcT.mcId()); mcS.executeUpdate();
        }
        return mcT;
    }

    public MCTemplate mcReplaceTemplate(MCTemplate mcT)throws SQLException{
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("UPDATE template SET name=?,original=?,edited=? WHERE id=?")){
            mcS.setString(1,mcT.mcName());mcS.setBytes(2,mcT.mcOriginal());mcS.setBytes(3,mcT.mcEdited());mcS.setLong(4,mcT.mcId());mcS.executeUpdate();
        }
        return mcT;
    }

    public void mcClearVariables(long mcTemplateId)throws SQLException{
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM variable WHERE template_id=?")){mcS.setLong(1,mcTemplateId);mcS.executeUpdate();}
    }

    public List<MCVariable> mcVariables(long mcTemplateId) throws SQLException {
        List<MCVariable> mcResult = new ArrayList<>();
        try (Connection mcC = mcConnect(); PreparedStatement mcS = mcC.prepareStatement("SELECT * FROM variable WHERE template_id=? ORDER BY position,id")) {
            mcS.setLong(1,mcTemplateId);
            try (ResultSet mcR = mcS.executeQuery()) { while (mcR.next()) mcResult.add(new MCVariable(mcR.getLong("id"),mcTemplateId,mcR.getString("key"),mcR.getString("label"),mcR.getString("type"),mcR.getString("default_value"),mcR.getInt("required")!=0,mcR.getInt("position"))); }
        }
        return mcResult;
    }

    public MCVariable mcSaveVariable(MCVariable mcV) throws SQLException {
        if (mcV.mcId()==0) {
            try (Connection mcC=mcConnect()) {
                mcC.setAutoCommit(false);
                try{
                    long mcDefinition=mcFindMatchingDefinition(mcC,MCDocumentEngine.mcBaseKey(mcV.mcKey()),mcV.mcLabel(),mcV.mcType(),mcV.mcDefaultValue());
                    if(mcDefinition==0)mcDefinition=mcInsertDefinition(mcC,MCDocumentEngine.mcBaseKey(mcV.mcKey()),mcV.mcLabel(),mcV.mcType(),mcV.mcDefaultValue());
                    try(PreparedStatement mcS=mcC.prepareStatement("INSERT INTO variable(template_id,key,label,type,default_value,required,position,definition_id) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                        mcBindVariable(mcS,mcV);mcS.setLong(8,mcDefinition);mcS.executeUpdate();
                        try(ResultSet mcR=mcS.getGeneratedKeys()){MCVariable mcSaved=new MCVariable(mcR.next()?mcR.getLong(1):0,mcV.mcTemplateId(),mcV.mcKey(),mcV.mcLabel(),mcV.mcType(),mcV.mcDefaultValue(),mcV.mcRequired(),mcV.mcPosition());mcC.commit();return mcSaved;}
                    }
                }catch(SQLException mcE){mcC.rollback();throw mcE;}
            }
        }
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("UPDATE variable SET key=?,label=?,type=?,default_value=?,required=?,position=? WHERE id=?")) {
            mcS.setString(1,mcV.mcKey());mcS.setString(2,mcV.mcLabel());mcS.setString(3,mcV.mcType());mcS.setString(4,mcV.mcDefaultValue());mcS.setInt(5,mcV.mcRequired()?1:0);mcS.setInt(6,mcV.mcPosition());mcS.setLong(7,mcV.mcId());mcS.executeUpdate();
        }
        return mcV;
    }
    private static void mcBindVariable(PreparedStatement mcS,MCVariable mcV)throws SQLException{
        mcS.setLong(1,mcV.mcTemplateId());mcS.setString(2,mcV.mcKey());mcS.setString(3,mcV.mcLabel());mcS.setString(4,mcV.mcType());mcS.setString(5,mcV.mcDefaultValue());mcS.setInt(6,mcV.mcRequired()?1:0);mcS.setInt(7,mcV.mcPosition());
    }
    public void mcDeleteVariable(long mcId)throws SQLException{
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM variable WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}
    }

    public List<MCKind> mcKinds() throws SQLException {
        List<MCKind> mcResult=new ArrayList<>();
        try(Connection mcC=mcConnect();Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT * FROM kind ORDER BY title")){
            while(mcR.next())mcResult.add(new MCKind(mcR.getLong("id"),mcR.getString("title"),mcR.getString("category"),mcR.getString("description"),mcR.getLong("template_id"),mcR.getString("fields"),mcR.getInt("active")!=0));
        }
        return mcResult;
    }
    public MCKind mcSaveKind(MCKind mcK)throws SQLException{
        if(mcK.mcId()==0){
            try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("INSERT INTO kind(title,category,description,template_id,fields,active) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                mcBindKind(mcS,mcK);mcS.executeUpdate();try(ResultSet mcR=mcS.getGeneratedKeys()){return new MCKind(mcR.next()?mcR.getLong(1):0,mcK.mcTitle(),mcK.mcCategory(),mcK.mcDescription(),mcK.mcTemplateId(),mcK.mcFields(),mcK.mcActive());}
            }
        }
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("UPDATE kind SET title=?,category=?,description=?,template_id=?,fields=?,active=? WHERE id=?")){
            mcBindKind(mcS,mcK);mcS.setLong(7,mcK.mcId());mcS.executeUpdate();
        }
        return mcK;
    }
    private static void mcBindKind(PreparedStatement mcS,MCKind mcK)throws SQLException{
        mcS.setString(1,mcK.mcTitle());mcS.setString(2,mcK.mcCategory());mcS.setString(3,mcK.mcDescription());mcS.setLong(4,mcK.mcTemplateId());mcS.setString(5,mcK.mcFields());mcS.setInt(6,mcK.mcActive()?1:0);
    }
    public void mcDeleteKind(long mcId)throws SQLException{
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("DELETE FROM kind WHERE id=?")){mcS.setLong(1,mcId);mcS.executeUpdate();}
    }
    public Map<String,Boolean> mcKindRequirements(long mcKindId)throws SQLException{
        Map<String,Boolean> mcOut=new HashMap<>();
        try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("SELECT key,required FROM kind_field_rule WHERE kind_id=?")){
            mcS.setLong(1,mcKindId);try(ResultSet mcR=mcS.executeQuery()){while(mcR.next())mcOut.put(mcR.getString(1),mcR.getInt(2)!=0);}
        }
        return mcOut;
    }
    public void mcSaveKindRequirements(long mcKindId,Map<String,Boolean> mcChoices)throws SQLException{
        mcSaveKindFieldRules(mcKindId,mcChoices,mcKindDefaults(mcKindId));
    }
    public Map<String,String> mcKindDefaults(long mcKindId)throws SQLException{
        Map<String,String> mcResult=new HashMap<>();try(Connection mcC=mcConnect();PreparedStatement mcS=mcC.prepareStatement("SELECT key,default_value FROM kind_field_rule WHERE kind_id=? AND default_value IS NOT NULL")){mcS.setLong(1,mcKindId);try(ResultSet mcR=mcS.executeQuery()){while(mcR.next())mcResult.put(mcR.getString(1),mcR.getString(2));}}return mcResult;
    }
    public void mcSaveKindFieldRules(long mcKindId,Map<String,Boolean> mcChoices,Map<String,String> mcDefaults)throws SQLException{
        try(Connection mcC=mcConnect()){
            mcC.setAutoCommit(false);
            try(PreparedStatement mcClear=mcC.prepareStatement("DELETE FROM kind_field_rule WHERE kind_id=?")){mcClear.setLong(1,mcKindId);mcClear.executeUpdate();}
            try(PreparedStatement mcInsert=mcC.prepareStatement("INSERT INTO kind_field_rule(kind_id,key,required,default_value) VALUES(?,?,?,?)")){
                Set<String> mcKeys=new LinkedHashSet<>(mcChoices.keySet());mcKeys.addAll(mcDefaults.keySet());
                for(String mcKey:mcKeys){mcInsert.setLong(1,mcKindId);mcInsert.setString(2,mcKey);mcInsert.setInt(3,mcChoices.getOrDefault(mcKey,false)?1:0);mcInsert.setString(4,mcDefaults.get(mcKey));mcInsert.addBatch();}
                mcInsert.executeBatch();
            }
            mcC.commit();
        }
    }
}
