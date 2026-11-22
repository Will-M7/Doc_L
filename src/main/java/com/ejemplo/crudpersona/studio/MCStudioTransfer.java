package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.MCDocumentStore.*;
import com.ejemplo.crudpersona.studio.MCStudioData.MCMapping;
import com.ejemplo.crudpersona.studio.MCStudioData.MCContactBinding;
import com.ejemplo.crudpersona.studio.MCStudioData.MCApiBinding;
import com.ejemplo.crudpersona.studio.MCStudioData.MCContact;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.zip.*;

/** @author MC */
public final class MCStudioTransfer {
    /** @author MC */
    public enum MCConflict {KEEP,REPLACE}
    /** @author MC */
    public record MCImportResult(@com.fasterxml.jackson.annotation.JsonProperty("templates") int mcTemplates,@com.fasterxml.jackson.annotation.JsonProperty("kinds") int mcKinds,@com.fasterxml.jackson.annotation.JsonProperty("conflicts") int mcConflicts,@com.fasterxml.jackson.annotation.JsonProperty("clients") int mcClients){
        public MCImportResult(int mcTemplates,int mcKinds,int mcConflicts){this(mcTemplates,mcKinds,mcConflicts,0);}
    }
    private static final ObjectMapper mcJSON=new ObjectMapper();
    private static final byte[] mcMAGIC="DSBK1".getBytes(StandardCharsets.US_ASCII),mcMAGIC_V2="DSBK2".getBytes(StandardCharsets.US_ASCII);
    private final MCDocumentStore mcStore;private final MCStudioData mcData;
    public MCStudioTransfer(MCDocumentStore mcStore,MCStudioData mcData){this.mcStore=mcStore;this.mcData=mcData;}

    public void mcExportConfiguration(Path mcDestination)throws Exception{
        mcExportConfiguration(mcDestination,null);
    }

    public void mcExportConfiguration(Path mcDestination,Collection<Long> mcTemplateIds)throws Exception{
        List<MCTemplate> mcAll=mcStore.mcTemplates();
        List<MCTemplate> mcTargets=mcTemplateIds==null?mcAll:mcAll.stream().filter(mcT->mcTemplateIds.contains(mcT.mcId())).toList();
        Set<Long> mcTargetIds=new HashSet<>();for(MCTemplate mcT:mcTargets)mcTargetIds.add(mcT.mcId());

        Set<Long> mcTargetGroupIds=new HashSet<>();
        for(MCTemplate mcT:mcTargets)mcTargetGroupIds.addAll(mcStore.mcGroupIds(mcT.mcId()));
        List<MCDocumentStore.MCTemplateGroup> mcTargetGroups=mcStore.mcGroups().stream().filter(mcG->mcTargetGroupIds.contains(mcG.mcId())).toList();

        if(mcTemplateIds!=null&&!mcTargetIds.containsAll(mcTemplateIds))throw new IOException("La selección contiene plantillas inexistentes.");
        Map<Long,List<MCVariable>> mcTargetVariables=new LinkedHashMap<>();
        Set<Long> mcTargetDefIds=new LinkedHashSet<>();
        List<MCDefinition> mcDefinitions=mcStore.mcDefinitions();
        for(MCTemplate mcT:mcTargets){
            List<MCVariable> mcVariables=new ArrayList<>(mcStore.mcVariables(mcT.mcId()));
            Set<String> mcKnown=new HashSet<>();for(MCVariable mcV:mcVariables){mcKnown.add(mcV.mcKey());mcTargetDefIds.add(mcStore.mcDefinitionId(mcV.mcId()));}
            try(XWPFDocument mcDocument=MCDocumentEngine.mcOpen(mcT.mcEdited())){
                Set<String> mcMarkers=MCDocumentEngine.mcMarkers(mcDocument);
                for(String mcMarker:mcMarkers)for(MCDefinition mcD:mcDefinitions)if(MCDocumentEngine.mcBaseKey(mcMarker).equals(mcD.mcKey())&&mcKnown.add(mcMarker)){
                    mcVariables.add(new MCVariable(-mcD.mcId(),mcT.mcId(),mcMarker,mcD.mcLabel(),mcD.mcType(),mcD.mcDefaultValue(),false,mcVariables.size()+1));
                    mcTargetDefIds.add(mcD.mcId());
                }
            }
            mcTargetVariables.put(mcT.mcId(),mcVariables);
        }
        boolean mcExpanded;List<MCComplement> mcAllComplements=mcStore.mcComplements();
        do{mcExpanded=false;for(MCComplement mcLink:mcAllComplements)if(mcTargetDefIds.contains(mcLink.mcSourceDefId())||mcTargetDefIds.contains(mcLink.mcTargetDefId())){mcExpanded|=mcTargetDefIds.add(mcLink.mcSourceDefId());mcExpanded|=mcTargetDefIds.add(mcLink.mcTargetDefId());}}while(mcExpanded);
        List<MCDefinition> mcTargetDefs=mcDefinitions.stream().filter(mcD->mcTargetDefIds.contains(mcD.mcId())).toList();
        List<MCApiBinding> mcTargetBindings=mcData.mcApiBindings().stream().filter(mcB->mcTargetDefIds.contains(mcB.mcDefinitionId())).toList();

        ObjectNode mcRoot=mcJSON.createObjectNode();
        mcRoot.put("format","document-studio-config");
        mcRoot.put("version",9);
        mcRoot.set("definitions",mcJSON.valueToTree(mcTargetDefs));
        mcRoot.set("complements",mcJSON.valueToTree(mcAllComplements.stream().filter(mcLink->mcTargetDefIds.contains(mcLink.mcSourceDefId())&&mcTargetDefIds.contains(mcLink.mcTargetDefId())).toList()));
        mcRoot.set("apiBindings",mcJSON.valueToTree(mcTargetBindings));
        mcRoot.set("groups",mcJSON.valueToTree(mcTargetGroups.stream().map(mcG->new MCTemplateGroup(mcG.mcId(),mcG.mcName(),(int)mcTargets.stream().filter(mcT->{try{return mcStore.mcInGroup(mcT.mcId(),mcG.mcId());}catch(SQLException mcE){throw new IllegalStateException(mcE);}}).count())).toList()));
        ArrayNode mcTemplates=mcRoot.putArray("templates");
        for(MCTemplate mcT:mcTargets){
            ObjectNode mcN=mcTemplates.addObject();
            mcN.put("id",mcT.mcId());
            mcN.put("name",mcT.mcName());
            mcN.put("original",Base64.getEncoder().encodeToString(mcT.mcOriginal()));
            mcN.put("edited",Base64.getEncoder().encodeToString(mcT.mcEdited()));
            mcN.set("groupIds",mcJSON.valueToTree(mcStore.mcGroupIds(mcT.mcId())));
            mcN.set("personas",mcJSON.valueToTree(mcStore.mcPersonas(mcT.mcId())));
            ArrayNode mcVariables=mcN.putArray("variables");
            for(MCVariable mcV:mcTargetVariables.get(mcT.mcId())){
                ObjectNode mcVariable=(ObjectNode)mcJSON.valueToTree(mcV);
                mcVariable.put("definitionId",mcV.mcId()<0?-mcV.mcId():mcStore.mcDefinitionId(mcV.mcId()));
                mcVariables.add(mcVariable);
            }
            mcN.set("mappings",mcJSON.valueToTree(mcData.mcMappings(mcT.mcId())));
            mcN.set("contactBindings",mcJSON.valueToTree(mcData.mcContactBindings(mcT.mcId())));
            mcN.set("apiContexts",mcJSON.valueToTree(mcData.mcApiContexts(mcT.mcId())));
        }
        ArrayNode mcKinds=mcRoot.putArray("kinds");
        for(MCKind mcK:mcStore.mcKinds()){
            if(mcTargetIds.contains(mcK.mcTemplateId())){
                ObjectNode mcKind=(ObjectNode)mcJSON.valueToTree(mcK);
                mcKind.set("requirements",mcJSON.valueToTree(mcStore.mcKindRequirements(mcK.mcId())));
                mcKind.set("defaults",mcJSON.valueToTree(mcStore.mcKindDefaults(mcK.mcId())));
                mcKinds.add(mcKind);
            }
        }
        try(ZipOutputStream mcZip=new ZipOutputStream(Files.newOutputStream(mcDestination))){
            mcZip.putNextEntry(new ZipEntry("configuration.json"));
            mcZip.write(mcJSON.writeValueAsBytes(mcRoot));
            mcZip.closeEntry();
        }
    }

    public void mcExportClients(Path mcDestination)throws Exception{
        ObjectNode mcRoot=mcJSON.createObjectNode();
        mcRoot.put("format","document-studio-clients");
        mcRoot.put("version",1);
        ArrayNode mcArr=mcRoot.putArray("clients");
        for(MCContact mcC:mcData.mcContacts()){
            ObjectNode mcN=mcArr.addObject();
            mcN.put("kind",mcC.mcKind());
            mcN.put("given",mcC.mcGiven());
            mcN.put("family",mcC.mcFamily());
            mcN.put("documentType",mcC.mcDocumentType());
            mcN.put("number",mcC.mcNumber());
            mcN.put("address",mcC.mcAddress());
            mcN.put("representatives",mcC.mcRepresentatives());
            mcN.put("extrasJson",mcC.mcExtrasJson());
        }
        try(ZipOutputStream mcZip=new ZipOutputStream(Files.newOutputStream(mcDestination))){
            mcZip.putNextEntry(new ZipEntry("clients.json"));
            mcZip.write(mcJSON.writeValueAsBytes(mcRoot));
            mcZip.closeEntry();
        }
    }

    public MCImportResult mcImportConfiguration(Path mcSource,MCConflict mcConflict)throws Exception{
        Map<String,byte[]> mcEntriesByName=new HashMap<>();
        long mcTotal=0;
        try(ZipInputStream mcZip=new ZipInputStream(Files.newInputStream(mcSource))){
            ZipEntry mcE;
            while((mcE=mcZip.getNextEntry())!=null){
                if(mcE.isDirectory())continue;
                String mcName=mcE.getName();
                if(!List.of("configuration.json","clients.json").contains(mcName)){
                    throw new IOException("El paquete contiene una entrada desconocida: "+mcName);
                }
                ByteArrayOutputStream mcOut=new ByteArrayOutputStream();
                byte[] mcChunk=new byte[8192];
                int mcN;
                while((mcN=mcZip.read(mcChunk))!=-1){
                    mcTotal+=mcN;
                    if(mcTotal>200_000_000)throw new IOException("Paquete demasiado grande.");
                    mcOut.write(mcChunk,0,mcN);
                }
                if(mcEntriesByName.putIfAbsent(mcName,mcOut.toByteArray())!=null)throw new IOException("Entrada ZIP duplicada: "+mcName);
            }
        }
        if(!mcEntriesByName.containsKey("configuration.json")&&!mcEntriesByName.containsKey("clients.json")){
            throw new IOException("Falta configuration.json o clients.json en el paquete.");
        }

        List<MCContact> mcClients=mcEntriesByName.containsKey("clients.json")?mcReadClients(mcEntriesByName.get("clients.json")):List.of();

        int mcTemplateCount=0,mcKindCount=0,mcConflicts=0,mcClientCount=0;

        if(mcEntriesByName.containsKey("configuration.json")){
            byte[] mcContent=mcEntriesByName.get("configuration.json");
            JsonNode mcRoot=mcJSON.readTree(mcContent);
            int mcVersion=mcRoot.path("version").asInt();
            if(mcRoot==null||!"document-studio-config".equals(mcRoot.path("format").asText())||(mcVersion<1||mcVersion>9)||!mcRoot.path("templates").isArray()||!mcRoot.path("kinds").isArray()||(mcVersion>=2&&!mcRoot.path("definitions").isArray())||(mcVersion>=3&&!mcRoot.path("groups").isArray())||(mcVersion>=4&&!mcRoot.path("apiBindings").isArray()))throw new IOException("Formato de configuración inválido.");
            Set<Long> mcGroupIds=new HashSet<>();if(mcVersion>=3)for(JsonNode mcG:mcRoot.path("groups"))if(mcG.path("id").asLong()<=0||!mcGroupIds.add(mcG.path("id").asLong())||mcG.path("name").asText().isBlank())throw new IOException("Grupo inválido.");
            Set<Long> mcDefinitionIds=new HashSet<>();if(mcVersion>=2)for(JsonNode mcD:mcRoot.path("definitions")){long mcId=mcD.path("id").asLong();if(mcId<=0||!mcDefinitionIds.add(mcId)||!mcD.path("key").asText().matches("[a-zA-Z][a-zA-Z0-9_]*")||mcD.path("label").asText().isBlank()||mcD.path("type").asText().isBlank())throw new IOException("Definición inválida.");}
            if(mcVersion>=9)for(JsonNode mcD:mcRoot.path("definitions"))if(!mcD.path("bindToClient").isBoolean())throw new IOException("Alcance de variable inválido.");
            if(mcVersion>=8)for(JsonNode mcD:mcRoot.path("definitions"))if(!mcD.path("autoCurrentDate").isBoolean()||!MCDocumentStore.mcDATE_FORMATS.contains(mcD.path("dateFormat").asText()))throw new IOException("Configuración de fecha inválida.");
            if(mcVersion>=4)for(JsonNode mcB:mcRoot.path("apiBindings"))if(!mcDefinitionIds.contains(mcB.path("definitionId").asLong())||!mcB.path("path").asText().matches("(?:field:[a-z_][a-z0-9_]*|data(?:\\.[a-zA-Z_][a-zA-Z0-9_]*|\\[\\d+])+)")||mcB.path("service").asText().isBlank())throw new IOException("Vinculación API inválida.");
            if(mcVersion>=7){if(!mcRoot.path("complements").isArray())throw new IOException("Faltan complementos.");Set<String> mcPairs=new HashSet<>();for(JsonNode mcLink:mcRoot.path("complements")){long mcA=mcLink.path("sourceDefId").asLong(),mcB=mcLink.path("targetDefId").asLong();if(mcA==mcB||!mcDefinitionIds.contains(mcA)||!mcDefinitionIds.contains(mcB)||!mcLink.path("bidirectional").isBoolean()||!mcPairs.add(mcA+":"+mcB))throw new IOException("Complemento inválido.");}}
            List<JsonNode> mcEntries=new ArrayList<>();Set<Long> mcIds=new HashSet<>();Map<Long,Set<String>> mcKeys=new HashMap<>();
            for(JsonNode mcN:mcRoot.path("templates")){
                long mcId=mcN.path("id").asLong();
                if(mcId<=0||!mcIds.add(mcId)||mcN.path("name").asText().isBlank()||!mcN.path("variables").isArray()||!mcN.path("mappings").isArray())throw new IOException("Plantilla inválida o duplicada.");
                if(mcVersion>=3&&mcN.has("groupId")&&!mcGroupIds.contains(mcN.path("groupId").asLong()))throw new IOException("Plantilla con grupo inexistente.");
                if(mcVersion>=5){if(!mcN.path("groupIds").isArray())throw new IOException("Faltan grupos de plantilla.");for(JsonNode mcGroup:mcN.path("groupIds"))if(!mcGroupIds.contains(mcGroup.asLong()))throw new IOException("Plantilla con grupo inexistente.");}
                if(mcVersion>=8){if(!mcN.path("personas").isArray())throw new IOException("Faltan personas de plantilla.");Set<String> mcPersonas=new HashSet<>();for(JsonNode mcP:mcN.path("personas"))if(!mcP.path("personaKey").asText().matches("[a-zA-Z][a-zA-Z0-9_]*")||!mcPersonas.add(mcP.path("personaKey").asText())||mcP.path("displayName").asText().isBlank())throw new IOException("Persona inválida.");}
                byte[] mcOriginal=mcDecode(mcN,"original"),mcEdited=mcDecode(mcN,"edited");
                try(XWPFDocument mcA=MCDocumentEngine.mcOpen(mcOriginal);XWPFDocument mcB=MCDocumentEngine.mcOpen(mcEdited)){}
                Set<String> mcTemplateKeys=new HashSet<>();
                for(JsonNode mcV:mcN.path("variables")){
                    String mcKey=mcV.path("key").asText();
                    if(!MCDocumentEngine.mcValidKey(mcKey)||!mcTemplateKeys.add(mcKey)||mcV.path("label").asText().isBlank())throw new IOException("Variable inválida o duplicada.");
                    if(mcVersion>=2&&!mcDefinitionIds.contains(mcV.path("definitionId").asLong()))throw new IOException("Variable con definición inexistente.");
                }
                for(JsonNode mcM:mcN.path("mappings"))if(!mcTemplateKeys.contains(mcM.path("sourceKey").asText())||!mcTemplateKeys.contains(mcM.path("targetKey").asText())||mcM.path("apiField").asText().isBlank())throw new IOException("Correspondencia inválida.");
                if(mcVersion>=2){if(!mcN.path("contactBindings").isArray())throw new IOException("Faltan asociaciones de contactos.");for(JsonNode mcB:mcN.path("contactBindings"))if(!mcTemplateKeys.contains(mcB.path("variableKey").asText())||mcB.path("groupKey").asText().isBlank()||!List.of("Persona","Empresa").contains(mcB.path("kind").asText()))throw new IOException("Asociación de contacto inválida.");}
                if(mcVersion>=6){if(!mcN.path("apiContexts").isArray())throw new IOException("Faltan grupos de consulta.");for(JsonNode mcB:mcN.path("apiContexts"))if(!mcTemplateKeys.contains(mcB.path("variableKey").asText())||mcB.path("groupKey").asText().isBlank()||!List.of("DNI","RUC").contains(mcB.path("service").asText()))throw new IOException("Grupo de consulta inválido.");}
                mcKeys.put(mcId,mcTemplateKeys);mcEntries.add(mcN);
            }
            for(JsonNode mcK:mcRoot.path("kinds")){
                long mcTemplateId=mcK.path("templateId").asLong();
                if(!mcIds.contains(mcTemplateId)||mcK.path("title").asText().isBlank())throw new IOException("Tipo documental sin plantilla válida.");
                for(String mcField:mcK.path("fields").asText("").split(","))if(!mcField.isBlank()&&!mcKeys.get(mcTemplateId).contains(mcField))throw new IOException("Formulario con variable inexistente.");
            }
            Map<Long,Long> mcDefinitionMap=new HashMap<>();if(mcVersion>=2)for(JsonNode mcD:mcRoot.path("definitions")){MCDefinition mcSaved=mcStore.mcImportDefinition(new MCDefinition(0,mcD.path("key").asText(),mcD.path("label").asText(),mcD.path("type").asText(),mcD.path("defaultValue").asText(""),mcD.path("autoCurrentDate").asBoolean(false),mcD.path("dateFormat").asText("dd/MM/yyyy"),mcD.path("bindToClient").asBoolean(true)));mcDefinitionMap.put(mcD.path("id").asLong(),mcSaved.mcId());}
            if(mcVersion>=4)for(JsonNode mcB:mcRoot.path("apiBindings")){long mcId=mcDefinitionMap.get(mcB.path("definitionId").asLong());MCApiBinding mcExisting=mcData.mcApiBinding(mcId);if(mcExisting==null||mcConflict==MCConflict.REPLACE)mcData.mcSaveApiBinding(new MCApiBinding(mcId,mcB.path("service").asText(),mcB.path("path").asText()));}
            if(mcVersion>=7)for(JsonNode mcLink:mcRoot.path("complements")){long mcA=mcDefinitionMap.get(mcLink.path("sourceDefId").asLong()),mcB=mcDefinitionMap.get(mcLink.path("targetDefId").asLong());if(mcConflict==MCConflict.REPLACE||mcStore.mcComplements().stream().noneMatch(mcX->mcX.mcSourceDefId()==mcA&&mcX.mcTargetDefId()==mcB))mcStore.mcSaveComplement(mcA,mcB,mcLink.path("bidirectional").asBoolean());}
            Map<Long,Long> mcGroupMap=new HashMap<>();if(mcVersion>=3)for(JsonNode mcG:mcRoot.path("groups")){MCDocumentStore.MCTemplateGroup mcExisting=mcStore.mcGroups().stream().filter(mcX->mcX.mcName().equalsIgnoreCase(mcG.path("name").asText())).findFirst().orElse(null);MCDocumentStore.MCTemplateGroup mcSaved=mcExisting==null?mcStore.mcSaveGroup(0,mcG.path("name").asText()):mcExisting;mcGroupMap.put(mcG.path("id").asLong(),mcSaved.mcId());}
            Map<Long,Long> mcIdMap=new HashMap<>();Set<Long> mcKeptTemplates=new HashSet<>();
            for(JsonNode mcN:mcEntries){
                long mcOldId=mcN.path("id").asLong();
                String mcName=mcN.path("name").asText();
                MCTemplate mcExisting=mcStore.mcTemplates().stream().filter(mcT->mcT.mcName().equalsIgnoreCase(mcName)).findFirst().orElse(null);
                if(mcExisting!=null){mcConflicts++;if(mcConflict==MCConflict.KEEP){mcIdMap.put(mcOldId,mcExisting.mcId());mcKeptTemplates.add(mcOldId);continue;}}
                byte[] mcOriginal=mcDecode(mcN,"original"),mcEdited=mcDecode(mcN,"edited");
                MCTemplate mcSaved=mcExisting==null?mcStore.mcSaveTemplate(new MCTemplate(0,mcName,mcOriginal,mcEdited)):mcStore.mcReplaceTemplate(new MCTemplate(mcExisting.mcId(),mcName,mcOriginal,mcEdited));
                mcIdMap.put(mcOldId,mcSaved.mcId());
                if(mcExisting!=null){mcStore.mcClearVariables(mcSaved.mcId());mcData.mcClearMappings(mcSaved.mcId());mcData.mcClearContactBindings(mcSaved.mcId());mcData.mcClearApiContexts(mcSaved.mcId());}
                if(mcExisting!=null)for(MCPersona mcPerson:mcStore.mcPersonas(mcSaved.mcId()))mcStore.mcDeletePersona(mcPerson.mcId());
                if(mcVersion>=8)for(JsonNode mcP:mcN.path("personas"))mcStore.mcAddPersona(mcSaved.mcId(),mcP.path("personaKey").asText(),mcP.path("displayName").asText(),mcP.path("sortOrder").asInt());
                for(JsonNode mcV:mcN.path("variables")){
                    String mcKey=mcV.path("key").asText();
                    MCVariable mcBinding=mcStore.mcSaveVariable(new MCVariable(0,mcSaved.mcId(),mcKey,mcV.path("label").asText(),mcV.path("type").asText(),mcV.path("defaultValue").asText(""),mcV.path("required").asBoolean(),mcV.path("position").asInt(1)));
                    if(mcVersion>=2)mcStore.mcLinkDefinition(mcBinding.mcId(),mcDefinitionMap.get(mcV.path("definitionId").asLong()));
                }
                for(JsonNode mcM:mcN.path("mappings"))mcData.mcSaveMapping(new MCMapping(0,mcSaved.mcId(),mcM.path("sourceKey").asText(),mcM.path("apiField").asText(),mcM.path("targetKey").asText()));
                if(mcVersion>=2)for(JsonNode mcB:mcN.path("contactBindings"))mcData.mcSaveContactBinding(new MCContactBinding(0,mcSaved.mcId(),mcB.path("groupKey").asText(),mcB.path("kind").asText(),mcB.path("field").asText(),mcB.path("variableKey").asText()));
                if(mcVersion>=6)for(JsonNode mcB:mcN.path("apiContexts"))mcData.mcSaveApiContext(new MCStudioData.MCApiContext(mcSaved.mcId(),mcB.path("variableKey").asText(),mcB.path("groupKey").asText(),mcB.path("service").asText(),mcB.path("source").asBoolean()));
                mcData.mcRecordRevision(mcSaved.mcId(),mcEdited);
                mcTemplateCount++;
            }
            if(mcVersion>=3)for(JsonNode mcN:mcEntries){
                if(mcKeptTemplates.contains(mcN.path("id").asLong()))continue;
                List<Long> mcTargets=new ArrayList<>();
                if(mcVersion>=5)for(JsonNode mcId:mcN.path("groupIds")){Long mcTarget=mcGroupMap.get(mcId.asLong());if(mcTarget!=null)mcTargets.add(mcTarget);}
                else{Long mcTarget=mcGroupMap.get(mcN.path("groupId").asLong());if(mcTarget!=null)mcTargets.add(mcTarget);}
                mcStore.mcSetTemplateGroups(mcIdMap.get(mcN.path("id").asLong()),mcTargets);
            }
            for(JsonNode mcN:mcRoot.path("kinds")){
                long mcTemplateId=mcIdMap.get(mcN.path("templateId").asLong());
                String mcTitle=mcN.path("title").asText();
                MCKind mcExisting=mcStore.mcKinds().stream().filter(mcK->mcK.mcTitle().equalsIgnoreCase(mcTitle)).findFirst().orElse(null);
                if(mcExisting!=null){mcConflicts++;if(mcConflict==MCConflict.KEEP)continue;}
                MCKind mcSaved=mcStore.mcSaveKind(new MCKind(mcExisting==null?0:mcExisting.mcId(),mcTitle,mcN.path("category").asText(""),mcN.path("description").asText(""),mcTemplateId,mcN.path("fields").asText(""),mcN.path("active").asBoolean(true)));
                if(mcVersion>=2&&mcN.path("requirements").isObject()){
                    Map<String,Boolean> mcRequirements=new HashMap<>();mcN.path("requirements").fields().forEachRemaining(mcE->mcRequirements.put(mcE.getKey(),mcE.getValue().asBoolean()));
                    Map<String,String> mcDefaults=new HashMap<>();mcN.path("defaults").fields().forEachRemaining(mcE->mcDefaults.put(mcE.getKey(),mcE.getValue().asText()));
                    mcStore.mcSaveKindFieldRules(mcSaved.mcId(),mcRequirements,mcDefaults);
                }
                mcKindCount++;
            }
        }

        for(MCContact mcIncoming:mcClients){
            MCContact mcExisting=mcData.mcMatchingContact(mcIncoming);
            if(mcExisting!=null){mcConflicts++;if(mcConflict==MCConflict.KEEP)continue;}
            mcData.mcSaveContact(new MCContact(mcExisting==null?0:mcExisting.mcId(),mcIncoming.mcKind(),mcIncoming.mcGiven(),mcIncoming.mcFamily(),mcIncoming.mcDocumentType(),mcIncoming.mcNumber(),mcIncoming.mcAddress(),mcIncoming.mcRepresentatives(),mcIncoming.mcExtrasJson()));
            mcClientCount++;
        }

        return new MCImportResult(mcTemplateCount,mcKindCount,mcConflicts,mcClientCount);
    }

    private static List<MCContact> mcReadClients(byte[] mcContent)throws IOException{
        JsonNode mcRoot=mcJSON.readTree(mcContent);
        if(mcRoot==null||!"document-studio-clients".equals(mcRoot.path("format").asText())||mcRoot.path("version").asInt()!=1||!mcRoot.path("clients").isArray())throw new IOException("Formato de clients.json inválido.");
        List<MCContact> mcClients=new ArrayList<>();
        for(JsonNode mcN:mcRoot.path("clients")){
            String mcKind=mcN.path("kind").asText(),mcGiven=mcN.path("given").asText("");
            if(!List.of("Persona","Empresa").contains(mcKind))throw new IOException("Tipo de cliente inválido.");
            String mcExtras=mcN.path("extrasJson").asText("{}");JsonNode mcParsed=mcJSON.readTree(mcExtras);
            if(mcParsed==null||!mcParsed.isObject())throw new IOException("Datos adicionales de cliente inválidos.");
            mcClients.add(new MCContact(0,mcKind,mcGiven,mcN.path("family").asText(""),mcN.path("documentType").asText(""),mcN.path("number").asText(""),mcN.path("address").asText(""),mcN.path("representatives").asText(""),mcExtras));
        }
        return mcClients;
    }

    private static byte[] mcDecode(JsonNode mcN,String mcField)throws IOException{
        try{
            byte[] mcB=Base64.getDecoder().decode(mcN.path(mcField).asText());
            if(mcB.length==0||mcB.length>100_000_000)throw new IOException("DOCX vacío o demasiado grande.");
            return mcB;
        }catch(IllegalArgumentException mcE){
            throw new IOException("Contenido DOCX inválido.",mcE);
        }
    }

    public void mcBackup(Path mcDestination,char[] mcPassword)throws Exception{
        try{
            if(mcPassword.length<8)throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
            byte[] mcPayload=Files.readAllBytes(mcData.mcDatabase());
            byte[] mcSalt=new byte[16],mcIv=new byte[12];SecureRandom mcRandom=new SecureRandom();mcRandom.nextBytes(mcSalt);mcRandom.nextBytes(mcIv);
            Cipher mcCipher=Cipher.getInstance("AES/GCM/NoPadding");mcCipher.init(Cipher.ENCRYPT_MODE,mcKey(mcPassword,mcSalt),new GCMParameterSpec(128,mcIv));byte[] mcEncrypted=mcCipher.doFinal(mcPayload);
            try(OutputStream mcOut=Files.newOutputStream(mcDestination)){mcOut.write(mcMAGIC);mcOut.write(mcSalt);mcOut.write(mcIv);mcOut.write(mcEncrypted);}
        }finally{Arrays.fill(mcPassword,'\0');}
    }
    public void mcRestore(Path mcSource,char[] mcPassword)throws Exception{
        try{
            byte[] mcAll=Files.readAllBytes(mcSource);if(mcAll.length<45)throw new IOException("Respaldo inválido.");boolean mcV2=Arrays.equals(Arrays.copyOfRange(mcAll,0,5),mcMAGIC_V2);if(!mcV2&&!Arrays.equals(Arrays.copyOfRange(mcAll,0,5),mcMAGIC))throw new IOException("Respaldo inválido.");
            byte[] mcSalt=Arrays.copyOfRange(mcAll,5,21),mcIv=Arrays.copyOfRange(mcAll,21,33);Cipher mcCipher=Cipher.getInstance("AES/GCM/NoPadding");mcCipher.init(Cipher.DECRYPT_MODE,mcKey(mcPassword,mcSalt),new GCMParameterSpec(128,mcIv));byte[] mcDecrypted=mcCipher.doFinal(mcAll,33,mcAll.length-33);
            byte[] mcDb=mcDecrypted;JsonNode mcRestoredSecrets=mcJSON.createObjectNode();
            if(mcV2){byte[] mcExtractedDb=null;try(ZipInputStream mcZip=new ZipInputStream(new ByteArrayInputStream(mcDecrypted))){ZipEntry mcEntry;while((mcEntry=mcZip.getNextEntry())!=null){if(!List.of("studio.db","credentials.json").contains(mcEntry.getName()))throw new IOException("Entrada no permitida en el respaldo.");byte[] mcBytes=mcZip.readAllBytes();if(mcBytes.length>500_000_000)throw new IOException("Respaldo demasiado grande.");if(mcEntry.getName().equals("studio.db"))mcExtractedDb=mcBytes;else mcRestoredSecrets=mcJSON.readTree(mcBytes);}}if(mcExtractedDb==null||mcRestoredSecrets==null||!mcRestoredSecrets.isObject())throw new IOException("Respaldo incompleto.");mcDb=mcExtractedDb;}
            Path mcTemp=Files.createTempFile(mcData.mcDatabase().getParent(),"restore-",".db");
            try{
                Files.write(mcTemp,mcDb);
                try(Connection mcC=DriverManager.getConnection("jdbc:sqlite:"+mcTemp);Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("PRAGMA integrity_check")){
                    if(!mcR.next()||!"ok".equalsIgnoreCase(mcR.getString(1)))throw new IOException("Base de datos dañada.");
                }
                try(Connection mcC=DriverManager.getConnection("jdbc:sqlite:"+mcTemp);Statement mcS=mcC.createStatement();ResultSet mcR=mcS.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")){
                    Set<String> mcTables=new HashSet<>();while(mcR.next())mcTables.add(mcR.getString(1));
                    if(!mcTables.containsAll(Set.of("template","variable","kind","contact","template_revision","document_history","field_mapping")))throw new IOException("El respaldo no contiene todas las tablas del sistema.");
                }
                try{Files.move(mcTemp,mcData.mcDatabase(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException mcE){Files.move(mcTemp,mcData.mcDatabase(),StandardCopyOption.REPLACE_EXISTING);}

            }finally{Files.deleteIfExists(mcTemp);}
        }finally{Arrays.fill(mcPassword,'\0');}
    }
    private static SecretKeySpec mcKey(char[] mcPassword,byte[] mcSalt)throws Exception{PBEKeySpec mcSpec=new PBEKeySpec(mcPassword,mcSalt,200_000,256);byte[] mcMaterial=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(mcSpec).getEncoded();mcSpec.clearPassword();return new SecretKeySpec(mcMaterial,"AES");}
}
