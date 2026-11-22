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

public final class MCStudioTransfer {
    public enum MCConflict {KEEP,REPLACE}
    public record MCImportResult(@com.fasterxml.jackson.annotation.JsonProperty("templates") int MCtemplates,@com.fasterxml.jackson.annotation.JsonProperty("kinds") int MCkinds,@com.fasterxml.jackson.annotation.JsonProperty("conflicts") int MCconflicts,@com.fasterxml.jackson.annotation.JsonProperty("clients") int MCclients){
        public MCImportResult(int MCtemplates,int MCkinds,int MCconflicts){this(MCtemplates,MCkinds,MCconflicts,0);}
    }
    private static final ObjectMapper MCJSON=new ObjectMapper();
    private static final byte[] MCMAGIC="DSBK1".getBytes(StandardCharsets.US_ASCII),MCMAGIC_V2="DSBK2".getBytes(StandardCharsets.US_ASCII);
    private final MCDocumentStore MCstore;private final MCStudioData MCdata;
    public MCStudioTransfer(MCDocumentStore MCstore,MCStudioData MCdata){this.MCstore=MCstore;this.MCdata=MCdata;}

    public void MCexportConfiguration(Path MCdestination)throws Exception{
        MCexportConfiguration(MCdestination,null);
    }

    public void MCexportConfiguration(Path MCdestination,Collection<Long> MCtemplateIds)throws Exception{
        List<MCTemplate> MCall=MCstore.MCtemplates();
        List<MCTemplate> MCtargets=MCtemplateIds==null?MCall:MCall.stream().filter(MCt->MCtemplateIds.contains(MCt.MCid())).toList();
        Set<Long> MCtargetIds=new HashSet<>();for(MCTemplate MCt:MCtargets)MCtargetIds.add(MCt.MCid());

        Set<Long> MCtargetGroupIds=new HashSet<>();
        for(MCTemplate MCt:MCtargets)MCtargetGroupIds.addAll(MCstore.MCgroupIds(MCt.MCid()));
        List<MCDocumentStore.MCTemplateGroup> MCtargetGroups=MCstore.MCgroups().stream().filter(MCg->MCtargetGroupIds.contains(MCg.MCid())).toList();

        if(MCtemplateIds!=null&&!MCtargetIds.containsAll(MCtemplateIds))throw new IOException("La selección contiene plantillas inexistentes.");
        Map<Long,List<MCVariable>> MCtargetVariables=new LinkedHashMap<>();
        Set<Long> MCtargetDefIds=new LinkedHashSet<>();
        List<MCDefinition> MCdefinitions=MCstore.MCdefinitions();
        for(MCTemplate MCt:MCtargets){
            List<MCVariable> MCvariables=new ArrayList<>(MCstore.MCvariables(MCt.MCid()));
            Set<String> MCknown=new HashSet<>();for(MCVariable MCv:MCvariables){MCknown.add(MCv.MCkey());MCtargetDefIds.add(MCstore.MCdefinitionId(MCv.MCid()));}
            try(XWPFDocument MCdocument=MCDocumentEngine.MCopen(MCt.MCedited())){
                Set<String> MCmarkers=MCDocumentEngine.MCmarkers(MCdocument);
                for(String MCmarker:MCmarkers)for(MCDefinition MCd:MCdefinitions)if(MCDocumentEngine.MCbaseKey(MCmarker).equals(MCd.MCkey())&&MCknown.add(MCmarker)){
                    MCvariables.add(new MCVariable(-MCd.MCid(),MCt.MCid(),MCmarker,MCd.MClabel(),MCd.MCtype(),MCd.MCdefaultValue(),false,MCvariables.size()+1));
                    MCtargetDefIds.add(MCd.MCid());
                }
            }
            MCtargetVariables.put(MCt.MCid(),MCvariables);
        }
        boolean MCexpanded;List<MCComplement> MCallComplements=MCstore.MCcomplements();
        do{MCexpanded=false;for(MCComplement MClink:MCallComplements)if(MCtargetDefIds.contains(MClink.MCsourceDefId())||MCtargetDefIds.contains(MClink.MCtargetDefId())){MCexpanded|=MCtargetDefIds.add(MClink.MCsourceDefId());MCexpanded|=MCtargetDefIds.add(MClink.MCtargetDefId());}}while(MCexpanded);
        List<MCDefinition> MCtargetDefs=MCdefinitions.stream().filter(MCd->MCtargetDefIds.contains(MCd.MCid())).toList();
        List<MCApiBinding> MCtargetBindings=MCdata.MCapiBindings().stream().filter(MCb->MCtargetDefIds.contains(MCb.MCdefinitionId())).toList();

        ObjectNode MCroot=MCJSON.createObjectNode();
        MCroot.put("format","document-studio-config");
        MCroot.put("version",9);
        MCroot.set("definitions",MCJSON.valueToTree(MCtargetDefs));
        MCroot.set("complements",MCJSON.valueToTree(MCallComplements.stream().filter(MClink->MCtargetDefIds.contains(MClink.MCsourceDefId())&&MCtargetDefIds.contains(MClink.MCtargetDefId())).toList()));
        MCroot.set("apiBindings",MCJSON.valueToTree(MCtargetBindings));
        MCroot.set("groups",MCJSON.valueToTree(MCtargetGroups.stream().map(MCg->new MCTemplateGroup(MCg.MCid(),MCg.MCname(),(int)MCtargets.stream().filter(MCt->{try{return MCstore.MCinGroup(MCt.MCid(),MCg.MCid());}catch(SQLException MCe){throw new IllegalStateException(MCe);}}).count())).toList()));
        ArrayNode MCtemplates=MCroot.putArray("templates");
        for(MCTemplate MCt:MCtargets){
            ObjectNode MCn=MCtemplates.addObject();
            MCn.put("id",MCt.MCid());
            MCn.put("name",MCt.MCname());
            MCn.put("original",Base64.getEncoder().encodeToString(MCt.MCoriginal()));
            MCn.put("edited",Base64.getEncoder().encodeToString(MCt.MCedited()));
            MCn.set("groupIds",MCJSON.valueToTree(MCstore.MCgroupIds(MCt.MCid())));
            MCn.set("personas",MCJSON.valueToTree(MCstore.MCpersonas(MCt.MCid())));
            ArrayNode MCvariables=MCn.putArray("variables");
            for(MCVariable MCv:MCtargetVariables.get(MCt.MCid())){
                ObjectNode MCvariable=(ObjectNode)MCJSON.valueToTree(MCv);
                MCvariable.put("definitionId",MCv.MCid()<0?-MCv.MCid():MCstore.MCdefinitionId(MCv.MCid()));
                MCvariables.add(MCvariable);
            }
            MCn.set("mappings",MCJSON.valueToTree(MCdata.MCmappings(MCt.MCid())));
            MCn.set("contactBindings",MCJSON.valueToTree(MCdata.MCcontactBindings(MCt.MCid())));
            MCn.set("apiContexts",MCJSON.valueToTree(MCdata.MCapiContexts(MCt.MCid())));
        }
        ArrayNode MCkinds=MCroot.putArray("kinds");
        for(MCKind MCk:MCstore.MCkinds()){
            if(MCtargetIds.contains(MCk.MCtemplateId())){
                ObjectNode MCkind=(ObjectNode)MCJSON.valueToTree(MCk);
                MCkind.set("requirements",MCJSON.valueToTree(MCstore.MCkindRequirements(MCk.MCid())));
                MCkind.set("defaults",MCJSON.valueToTree(MCstore.MCkindDefaults(MCk.MCid())));
                MCkinds.add(MCkind);
            }
        }
        try(ZipOutputStream MCzip=new ZipOutputStream(Files.newOutputStream(MCdestination))){
            MCzip.putNextEntry(new ZipEntry("configuration.json"));
            MCzip.write(MCJSON.writeValueAsBytes(MCroot));
            MCzip.closeEntry();
        }
    }

    public void MCexportClients(Path MCdestination)throws Exception{
        ObjectNode MCroot=MCJSON.createObjectNode();
        MCroot.put("format","document-studio-clients");
        MCroot.put("version",1);
        ArrayNode MCarr=MCroot.putArray("clients");
        for(MCContact MCc:MCdata.MCcontacts()){
            ObjectNode MCn=MCarr.addObject();
            MCn.put("kind",MCc.MCkind());
            MCn.put("given",MCc.MCgiven());
            MCn.put("family",MCc.MCfamily());
            MCn.put("documentType",MCc.MCdocumentType());
            MCn.put("number",MCc.MCnumber());
            MCn.put("address",MCc.MCaddress());
            MCn.put("representatives",MCc.MCrepresentatives());
            MCn.put("extrasJson",MCc.MCextrasJson());
        }
        try(ZipOutputStream MCzip=new ZipOutputStream(Files.newOutputStream(MCdestination))){
            MCzip.putNextEntry(new ZipEntry("clients.json"));
            MCzip.write(MCJSON.writeValueAsBytes(MCroot));
            MCzip.closeEntry();
        }
    }

    public MCImportResult MCimportConfiguration(Path MCsource,MCConflict MCconflict)throws Exception{
        Map<String,byte[]> MCentriesByName=new HashMap<>();
        long MCtotal=0;
        try(ZipInputStream MCzip=new ZipInputStream(Files.newInputStream(MCsource))){
            ZipEntry MCe;
            while((MCe=MCzip.getNextEntry())!=null){
                if(MCe.isDirectory())continue;
                String MCname=MCe.getName();
                if(!List.of("configuration.json","clients.json").contains(MCname)){
                    throw new IOException("El paquete contiene una entrada desconocida: "+MCname);
                }
                ByteArrayOutputStream MCout=new ByteArrayOutputStream();
                byte[] MCchunk=new byte[8192];
                int MCn;
                while((MCn=MCzip.read(MCchunk))!=-1){
                    MCtotal+=MCn;
                    if(MCtotal>200_000_000)throw new IOException("Paquete demasiado grande.");
                    MCout.write(MCchunk,0,MCn);
                }
                if(MCentriesByName.putIfAbsent(MCname,MCout.toByteArray())!=null)throw new IOException("Entrada ZIP duplicada: "+MCname);
            }
        }
        if(!MCentriesByName.containsKey("configuration.json")&&!MCentriesByName.containsKey("clients.json")){
            throw new IOException("Falta configuration.json o clients.json en el paquete.");
        }

        List<MCContact> MCclients=MCentriesByName.containsKey("clients.json")?MCreadClients(MCentriesByName.get("clients.json")):List.of();

        int MCtemplateCount=0,MCkindCount=0,MCconflicts=0,MCclientCount=0;

        if(MCentriesByName.containsKey("configuration.json")){
            byte[] MCcontent=MCentriesByName.get("configuration.json");
            JsonNode MCroot=MCJSON.readTree(MCcontent);
            int MCversion=MCroot.path("version").asInt();
            if(MCroot==null||!"document-studio-config".equals(MCroot.path("format").asText())||(MCversion<1||MCversion>9)||!MCroot.path("templates").isArray()||!MCroot.path("kinds").isArray()||(MCversion>=2&&!MCroot.path("definitions").isArray())||(MCversion>=3&&!MCroot.path("groups").isArray())||(MCversion>=4&&!MCroot.path("apiBindings").isArray()))throw new IOException("Formato de configuración inválido.");
            Set<Long> MCgroupIds=new HashSet<>();if(MCversion>=3)for(JsonNode MCg:MCroot.path("groups"))if(MCg.path("id").asLong()<=0||!MCgroupIds.add(MCg.path("id").asLong())||MCg.path("name").asText().isBlank())throw new IOException("Grupo inválido.");
            Set<Long> MCdefinitionIds=new HashSet<>();if(MCversion>=2)for(JsonNode MCd:MCroot.path("definitions")){long MCid=MCd.path("id").asLong();if(MCid<=0||!MCdefinitionIds.add(MCid)||!MCd.path("key").asText().matches("[a-zA-Z][a-zA-Z0-9_]*")||MCd.path("label").asText().isBlank()||MCd.path("type").asText().isBlank())throw new IOException("Definición inválida.");}
            if(MCversion>=9)for(JsonNode MCd:MCroot.path("definitions"))if(!MCd.path("bindToClient").isBoolean())throw new IOException("Alcance de variable inválido.");
            if(MCversion>=8)for(JsonNode MCd:MCroot.path("definitions"))if(!MCd.path("autoCurrentDate").isBoolean()||!MCDocumentStore.MCDATE_FORMATS.contains(MCd.path("dateFormat").asText()))throw new IOException("Configuración de fecha inválida.");
            if(MCversion>=4)for(JsonNode MCb:MCroot.path("apiBindings"))if(!MCdefinitionIds.contains(MCb.path("definitionId").asLong())||!MCb.path("path").asText().matches("(?:field:[a-z_][a-z0-9_]*|data(?:\\.[a-zA-Z_][a-zA-Z0-9_]*|\\[\\d+])+)")||MCb.path("service").asText().isBlank())throw new IOException("Vinculación API inválida.");
            if(MCversion>=7){if(!MCroot.path("complements").isArray())throw new IOException("Faltan complementos.");Set<String> MCpairs=new HashSet<>();for(JsonNode MClink:MCroot.path("complements")){long MCa=MClink.path("sourceDefId").asLong(),MCb=MClink.path("targetDefId").asLong();if(MCa==MCb||!MCdefinitionIds.contains(MCa)||!MCdefinitionIds.contains(MCb)||!MClink.path("bidirectional").isBoolean()||!MCpairs.add(MCa+":"+MCb))throw new IOException("Complemento inválido.");}}
            List<JsonNode> MCentries=new ArrayList<>();Set<Long> MCids=new HashSet<>();Map<Long,Set<String>> MCkeys=new HashMap<>();
            for(JsonNode MCn:MCroot.path("templates")){
                long MCid=MCn.path("id").asLong();
                if(MCid<=0||!MCids.add(MCid)||MCn.path("name").asText().isBlank()||!MCn.path("variables").isArray()||!MCn.path("mappings").isArray())throw new IOException("Plantilla inválida o duplicada.");
                if(MCversion>=3&&MCn.has("groupId")&&!MCgroupIds.contains(MCn.path("groupId").asLong()))throw new IOException("Plantilla con grupo inexistente.");
                if(MCversion>=5){if(!MCn.path("groupIds").isArray())throw new IOException("Faltan grupos de plantilla.");for(JsonNode MCgroup:MCn.path("groupIds"))if(!MCgroupIds.contains(MCgroup.asLong()))throw new IOException("Plantilla con grupo inexistente.");}
                if(MCversion>=8){if(!MCn.path("personas").isArray())throw new IOException("Faltan personas de plantilla.");Set<String> MCpersonas=new HashSet<>();for(JsonNode MCp:MCn.path("personas"))if(!MCp.path("personaKey").asText().matches("[a-zA-Z][a-zA-Z0-9_]*")||!MCpersonas.add(MCp.path("personaKey").asText())||MCp.path("displayName").asText().isBlank())throw new IOException("Persona inválida.");}
                byte[] MCoriginal=MCdecode(MCn,"original"),MCedited=MCdecode(MCn,"edited");
                try(XWPFDocument MCa=MCDocumentEngine.MCopen(MCoriginal);XWPFDocument MCb=MCDocumentEngine.MCopen(MCedited)){}
                Set<String> MCtemplateKeys=new HashSet<>();
                for(JsonNode MCv:MCn.path("variables")){
                    String MCkey=MCv.path("key").asText();
                    if(!MCDocumentEngine.MCvalidKey(MCkey)||!MCtemplateKeys.add(MCkey)||MCv.path("label").asText().isBlank())throw new IOException("Variable inválida o duplicada.");
                    if(MCversion>=2&&!MCdefinitionIds.contains(MCv.path("definitionId").asLong()))throw new IOException("Variable con definición inexistente.");
                }
                for(JsonNode MCm:MCn.path("mappings"))if(!MCtemplateKeys.contains(MCm.path("sourceKey").asText())||!MCtemplateKeys.contains(MCm.path("targetKey").asText())||MCm.path("apiField").asText().isBlank())throw new IOException("Correspondencia inválida.");
                if(MCversion>=2){if(!MCn.path("contactBindings").isArray())throw new IOException("Faltan asociaciones de contactos.");for(JsonNode MCb:MCn.path("contactBindings"))if(!MCtemplateKeys.contains(MCb.path("variableKey").asText())||MCb.path("groupKey").asText().isBlank()||!List.of("Persona","Empresa").contains(MCb.path("kind").asText()))throw new IOException("Asociación de contacto inválida.");}
                if(MCversion>=6){if(!MCn.path("apiContexts").isArray())throw new IOException("Faltan grupos de consulta.");for(JsonNode MCb:MCn.path("apiContexts"))if(!MCtemplateKeys.contains(MCb.path("variableKey").asText())||MCb.path("groupKey").asText().isBlank()||!List.of("DNI","RUC").contains(MCb.path("service").asText()))throw new IOException("Grupo de consulta inválido.");}
                MCkeys.put(MCid,MCtemplateKeys);MCentries.add(MCn);
            }
            for(JsonNode MCk:MCroot.path("kinds")){
                long MCtemplateId=MCk.path("templateId").asLong();
                if(!MCids.contains(MCtemplateId)||MCk.path("title").asText().isBlank())throw new IOException("Tipo documental sin plantilla válida.");
                for(String MCfield:MCk.path("fields").asText("").split(","))if(!MCfield.isBlank()&&!MCkeys.get(MCtemplateId).contains(MCfield))throw new IOException("Formulario con variable inexistente.");
            }
            Map<Long,Long> MCdefinitionMap=new HashMap<>();if(MCversion>=2)for(JsonNode MCd:MCroot.path("definitions")){MCDefinition MCsaved=MCstore.MCimportDefinition(new MCDefinition(0,MCd.path("key").asText(),MCd.path("label").asText(),MCd.path("type").asText(),MCd.path("defaultValue").asText(""),MCd.path("autoCurrentDate").asBoolean(false),MCd.path("dateFormat").asText("dd/MM/yyyy"),MCd.path("bindToClient").asBoolean(true)));MCdefinitionMap.put(MCd.path("id").asLong(),MCsaved.MCid());}
            if(MCversion>=4)for(JsonNode MCb:MCroot.path("apiBindings")){long MCid=MCdefinitionMap.get(MCb.path("definitionId").asLong());MCApiBinding MCexisting=MCdata.MCapiBinding(MCid);if(MCexisting==null||MCconflict==MCConflict.REPLACE)MCdata.MCsaveApiBinding(new MCApiBinding(MCid,MCb.path("service").asText(),MCb.path("path").asText()));}
            if(MCversion>=7)for(JsonNode MClink:MCroot.path("complements")){long MCa=MCdefinitionMap.get(MClink.path("sourceDefId").asLong()),MCb=MCdefinitionMap.get(MClink.path("targetDefId").asLong());if(MCconflict==MCConflict.REPLACE||MCstore.MCcomplements().stream().noneMatch(MCx->MCx.MCsourceDefId()==MCa&&MCx.MCtargetDefId()==MCb))MCstore.MCsaveComplement(MCa,MCb,MClink.path("bidirectional").asBoolean());}
            Map<Long,Long> MCgroupMap=new HashMap<>();if(MCversion>=3)for(JsonNode MCg:MCroot.path("groups")){MCDocumentStore.MCTemplateGroup MCexisting=MCstore.MCgroups().stream().filter(MCx->MCx.MCname().equalsIgnoreCase(MCg.path("name").asText())).findFirst().orElse(null);MCDocumentStore.MCTemplateGroup MCsaved=MCexisting==null?MCstore.MCsaveGroup(0,MCg.path("name").asText()):MCexisting;MCgroupMap.put(MCg.path("id").asLong(),MCsaved.MCid());}
            Map<Long,Long> MCidMap=new HashMap<>();Set<Long> MCkeptTemplates=new HashSet<>();
            for(JsonNode MCn:MCentries){
                long MColdId=MCn.path("id").asLong();
                String MCname=MCn.path("name").asText();
                MCTemplate MCexisting=MCstore.MCtemplates().stream().filter(MCt->MCt.MCname().equalsIgnoreCase(MCname)).findFirst().orElse(null);
                if(MCexisting!=null){MCconflicts++;if(MCconflict==MCConflict.KEEP){MCidMap.put(MColdId,MCexisting.MCid());MCkeptTemplates.add(MColdId);continue;}}
                byte[] MCoriginal=MCdecode(MCn,"original"),MCedited=MCdecode(MCn,"edited");
                MCTemplate MCsaved=MCexisting==null?MCstore.MCsaveTemplate(new MCTemplate(0,MCname,MCoriginal,MCedited)):MCstore.MCreplaceTemplate(new MCTemplate(MCexisting.MCid(),MCname,MCoriginal,MCedited));
                MCidMap.put(MColdId,MCsaved.MCid());
                if(MCexisting!=null){MCstore.MCclearVariables(MCsaved.MCid());MCdata.MCclearMappings(MCsaved.MCid());MCdata.MCclearContactBindings(MCsaved.MCid());MCdata.MCclearApiContexts(MCsaved.MCid());}
                if(MCexisting!=null)for(MCPersona MCperson:MCstore.MCpersonas(MCsaved.MCid()))MCstore.MCdeletePersona(MCperson.MCid());
                if(MCversion>=8)for(JsonNode MCp:MCn.path("personas"))MCstore.MCaddPersona(MCsaved.MCid(),MCp.path("personaKey").asText(),MCp.path("displayName").asText(),MCp.path("sortOrder").asInt());
                for(JsonNode MCv:MCn.path("variables")){
                    String MCkey=MCv.path("key").asText();
                    MCVariable MCbinding=MCstore.MCsaveVariable(new MCVariable(0,MCsaved.MCid(),MCkey,MCv.path("label").asText(),MCv.path("type").asText(),MCv.path("defaultValue").asText(""),MCv.path("required").asBoolean(),MCv.path("position").asInt(1)));
                    if(MCversion>=2)MCstore.MClinkDefinition(MCbinding.MCid(),MCdefinitionMap.get(MCv.path("definitionId").asLong()));
                }
                for(JsonNode MCm:MCn.path("mappings"))MCdata.MCsaveMapping(new MCMapping(0,MCsaved.MCid(),MCm.path("sourceKey").asText(),MCm.path("apiField").asText(),MCm.path("targetKey").asText()));
                if(MCversion>=2)for(JsonNode MCb:MCn.path("contactBindings"))MCdata.MCsaveContactBinding(new MCContactBinding(0,MCsaved.MCid(),MCb.path("groupKey").asText(),MCb.path("kind").asText(),MCb.path("field").asText(),MCb.path("variableKey").asText()));
                if(MCversion>=6)for(JsonNode MCb:MCn.path("apiContexts"))MCdata.MCsaveApiContext(new MCStudioData.MCApiContext(MCsaved.MCid(),MCb.path("variableKey").asText(),MCb.path("groupKey").asText(),MCb.path("service").asText(),MCb.path("source").asBoolean()));
                MCdata.MCrecordRevision(MCsaved.MCid(),MCedited);
                MCtemplateCount++;
            }
            if(MCversion>=3)for(JsonNode MCn:MCentries){
                if(MCkeptTemplates.contains(MCn.path("id").asLong()))continue;
                List<Long> MCtargets=new ArrayList<>();
                if(MCversion>=5)for(JsonNode MCid:MCn.path("groupIds")){Long MCtarget=MCgroupMap.get(MCid.asLong());if(MCtarget!=null)MCtargets.add(MCtarget);}
                else{Long MCtarget=MCgroupMap.get(MCn.path("groupId").asLong());if(MCtarget!=null)MCtargets.add(MCtarget);}
                MCstore.MCsetTemplateGroups(MCidMap.get(MCn.path("id").asLong()),MCtargets);
            }
            for(JsonNode MCn:MCroot.path("kinds")){
                long MCtemplateId=MCidMap.get(MCn.path("templateId").asLong());
                String MCtitle=MCn.path("title").asText();
                MCKind MCexisting=MCstore.MCkinds().stream().filter(MCk->MCk.MCtitle().equalsIgnoreCase(MCtitle)).findFirst().orElse(null);
                if(MCexisting!=null){MCconflicts++;if(MCconflict==MCConflict.KEEP)continue;}
                MCKind MCsaved=MCstore.MCsaveKind(new MCKind(MCexisting==null?0:MCexisting.MCid(),MCtitle,MCn.path("category").asText(""),MCn.path("description").asText(""),MCtemplateId,MCn.path("fields").asText(""),MCn.path("active").asBoolean(true)));
                if(MCversion>=2&&MCn.path("requirements").isObject()){
                    Map<String,Boolean> MCrequirements=new HashMap<>();MCn.path("requirements").fields().forEachRemaining(MCe->MCrequirements.put(MCe.getKey(),MCe.getValue().asBoolean()));
                    Map<String,String> MCdefaults=new HashMap<>();MCn.path("defaults").fields().forEachRemaining(MCe->MCdefaults.put(MCe.getKey(),MCe.getValue().asText()));
                    MCstore.MCsaveKindFieldRules(MCsaved.MCid(),MCrequirements,MCdefaults);
                }
                MCkindCount++;
            }
        }

        for(MCContact MCincoming:MCclients){
            MCContact MCexisting=MCdata.MCmatchingContact(MCincoming);
            if(MCexisting!=null){MCconflicts++;if(MCconflict==MCConflict.KEEP)continue;}
            MCdata.MCsaveContact(new MCContact(MCexisting==null?0:MCexisting.MCid(),MCincoming.MCkind(),MCincoming.MCgiven(),MCincoming.MCfamily(),MCincoming.MCdocumentType(),MCincoming.MCnumber(),MCincoming.MCaddress(),MCincoming.MCrepresentatives(),MCincoming.MCextrasJson()));
            MCclientCount++;
        }

        return new MCImportResult(MCtemplateCount,MCkindCount,MCconflicts,MCclientCount);
    }

    private static List<MCContact> MCreadClients(byte[] MCcontent)throws IOException{
        JsonNode MCroot=MCJSON.readTree(MCcontent);
        if(MCroot==null||!"document-studio-clients".equals(MCroot.path("format").asText())||MCroot.path("version").asInt()!=1||!MCroot.path("clients").isArray())throw new IOException("Formato de clients.json inválido.");
        List<MCContact> MCclients=new ArrayList<>();
        for(JsonNode MCn:MCroot.path("clients")){
            String MCkind=MCn.path("kind").asText(),MCgiven=MCn.path("given").asText("");
            if(!List.of("Persona","Empresa").contains(MCkind))throw new IOException("Tipo de cliente inválido.");
            String MCextras=MCn.path("extrasJson").asText("{}");JsonNode MCparsed=MCJSON.readTree(MCextras);
            if(MCparsed==null||!MCparsed.isObject())throw new IOException("Datos adicionales de cliente inválidos.");
            MCclients.add(new MCContact(0,MCkind,MCgiven,MCn.path("family").asText(""),MCn.path("documentType").asText(""),MCn.path("number").asText(""),MCn.path("address").asText(""),MCn.path("representatives").asText(""),MCextras));
        }
        return MCclients;
    }

    private static byte[] MCdecode(JsonNode MCn,String MCfield)throws IOException{
        try{
            byte[] MCb=Base64.getDecoder().decode(MCn.path(MCfield).asText());
            if(MCb.length==0||MCb.length>100_000_000)throw new IOException("DOCX vacío o demasiado grande.");
            return MCb;
        }catch(IllegalArgumentException MCe){
            throw new IOException("Contenido DOCX inválido.",MCe);
        }
    }

    public void MCbackup(Path MCdestination,char[] MCpassword)throws Exception{
        try{
            if(MCpassword.length<8)throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
            byte[] MCpayload=Files.readAllBytes(MCdata.MCdatabase());
            byte[] MCsalt=new byte[16],MCiv=new byte[12];SecureRandom MCrandom=new SecureRandom();MCrandom.nextBytes(MCsalt);MCrandom.nextBytes(MCiv);
            Cipher MCcipher=Cipher.getInstance("AES/GCM/NoPadding");MCcipher.init(Cipher.ENCRYPT_MODE,MCkey(MCpassword,MCsalt),new GCMParameterSpec(128,MCiv));byte[] MCencrypted=MCcipher.doFinal(MCpayload);
            try(OutputStream MCout=Files.newOutputStream(MCdestination)){MCout.write(MCMAGIC);MCout.write(MCsalt);MCout.write(MCiv);MCout.write(MCencrypted);}
        }finally{Arrays.fill(MCpassword,'\0');}
    }
    public void MCrestore(Path MCsource,char[] MCpassword)throws Exception{
        try{
            byte[] MCall=Files.readAllBytes(MCsource);if(MCall.length<45)throw new IOException("Respaldo inválido.");boolean MCv2=Arrays.equals(Arrays.copyOfRange(MCall,0,5),MCMAGIC_V2);if(!MCv2&&!Arrays.equals(Arrays.copyOfRange(MCall,0,5),MCMAGIC))throw new IOException("Respaldo inválido.");
            byte[] MCsalt=Arrays.copyOfRange(MCall,5,21),MCiv=Arrays.copyOfRange(MCall,21,33);Cipher MCcipher=Cipher.getInstance("AES/GCM/NoPadding");MCcipher.init(Cipher.DECRYPT_MODE,MCkey(MCpassword,MCsalt),new GCMParameterSpec(128,MCiv));byte[] MCdecrypted=MCcipher.doFinal(MCall,33,MCall.length-33);
            byte[] MCdb=MCdecrypted;JsonNode MCrestoredSecrets=MCJSON.createObjectNode();
            if(MCv2){byte[] MCextractedDb=null;try(ZipInputStream MCzip=new ZipInputStream(new ByteArrayInputStream(MCdecrypted))){ZipEntry MCentry;while((MCentry=MCzip.getNextEntry())!=null){if(!List.of("studio.db","credentials.json").contains(MCentry.getName()))throw new IOException("Entrada no permitida en el respaldo.");byte[] MCbytes=MCzip.readAllBytes();if(MCbytes.length>500_000_000)throw new IOException("Respaldo demasiado grande.");if(MCentry.getName().equals("studio.db"))MCextractedDb=MCbytes;else MCrestoredSecrets=MCJSON.readTree(MCbytes);}}if(MCextractedDb==null||MCrestoredSecrets==null||!MCrestoredSecrets.isObject())throw new IOException("Respaldo incompleto.");MCdb=MCextractedDb;}
            Path MCtemp=Files.createTempFile(MCdata.MCdatabase().getParent(),"restore-",".db");
            try{
                Files.write(MCtemp,MCdb);
                try(Connection MCc=DriverManager.getConnection("jdbc:sqlite:"+MCtemp);Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("PRAGMA integrity_check")){
                    if(!MCr.next()||!"ok".equalsIgnoreCase(MCr.getString(1)))throw new IOException("Base de datos dañada.");
                }
                try(Connection MCc=DriverManager.getConnection("jdbc:sqlite:"+MCtemp);Statement MCs=MCc.createStatement();ResultSet MCr=MCs.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")){
                    Set<String> MCtables=new HashSet<>();while(MCr.next())MCtables.add(MCr.getString(1));
                    if(!MCtables.containsAll(Set.of("template","variable","kind","contact","template_revision","document_history","field_mapping")))throw new IOException("El respaldo no contiene todas las tablas del sistema.");
                }
                try{Files.move(MCtemp,MCdata.MCdatabase(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException MCe){Files.move(MCtemp,MCdata.MCdatabase(),StandardCopyOption.REPLACE_EXISTING);}

            }finally{Files.deleteIfExists(MCtemp);}
        }finally{Arrays.fill(MCpassword,'\0');}
    }
    private static SecretKeySpec MCkey(char[] MCpassword,byte[] MCsalt)throws Exception{PBEKeySpec MCspec=new PBEKeySpec(MCpassword,MCsalt,200_000,256);byte[] MCmaterial=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(MCspec).getEncoded();MCspec.clearPassword();return new SecretKeySpec(MCmaterial,"AES");}
}
