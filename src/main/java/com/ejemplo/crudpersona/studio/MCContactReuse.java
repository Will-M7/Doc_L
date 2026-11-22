package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.MCDocumentStore.MCVariable;
import com.ejemplo.crudpersona.studio.MCStudioData.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.text.Normalizer;
import java.util.*;

/** @author MC */
public final class MCContactReuse {
    /** @author MC */
    public record MCResult(@com.fasterxml.jackson.annotation.JsonProperty("created") int mcCreated,@com.fasterxml.jackson.annotation.JsonProperty("updated") int mcUpdated,@com.fasterxml.jackson.annotation.JsonProperty("unchanged") int mcUnchanged,@com.fasterxml.jackson.annotation.JsonProperty("skipped") int mcSkipped){}
    private static final ObjectMapper mcJSON=new ObjectMapper();
    private MCContactReuse(){}

    public static Map<String,String> mcValuesFor(MCContact mcContact,List<MCContactBinding> mcGroup){
        Map<String,String> mcValues=new LinkedHashMap<>();
        Map<String,String> mcExtras=mcApiValues(mcContact);
        for(MCContactBinding mcB:mcGroup){
            String mcValue=switch(mcB.mcField()){
                case "given"->mcContact.mcGiven();case "family"->mcContact.mcFamily();case "document_type"->mcContact.mcDocumentType();
                case "number"->mcContact.mcNumber();case "address"->mcContact.mcAddress();case "representatives"->mcContact.mcRepresentatives();
                default->mcExtras.getOrDefault(mcB.mcField().startsWith("extra:")?mcB.mcField().substring(6):mcB.mcField(),"");
            };
            mcValues.put(mcB.mcVariableKey(),mcValue==null?"":mcValue);
        }
        return mcValues;
    }

    public static MCContact mcFromApi(MCLookupRouter.MCResult mcResult)throws Exception{
        Map<String,String> mcFields=new LinkedHashMap<>(mcResult.mcRaw());mcFields.putAll(mcResult.mcValues());
        boolean mcCompany="RUC".equals(mcResult.mcService());if(!mcCompany)MCApiFieldCatalog.mcNormalizePersonName(mcFields);
        String mcName=mcCompany?mcFields.getOrDefault("razon_social",""):mcFields.getOrDefault("nombres","");
        if(mcName.isBlank())mcName=mcFields.getOrDefault(mcCompany?"nombre_o_razon_social":"nombre_completo","");
        String mcFamily=(mcFields.getOrDefault("apellido_paterno","")+" "+mcFields.getOrDefault("apellido_materno","")).trim();if(mcFamily.isBlank())mcFamily=mcFields.getOrDefault("apellidos","");
        String mcAddress=mcPrefer(mcFields.get("direccion_completa"),mcFields.getOrDefault("direccion",""));
        String mcReps=mcFields.entrySet().stream().filter(mcE->mcE.getKey().toLowerCase(Locale.ROOT).contains("representante"))
                .map(Map.Entry::getValue).filter(mcV->mcV!=null&&!mcV.isBlank()).distinct().collect(java.util.stream.Collectors.joining("; "));
        return new MCContact(0,mcCompany?"Empresa":"Persona",mcName,mcCompany?"":mcFamily,mcResult.mcService(),mcResult.mcIdentifier().trim(),mcAddress,mcReps,mcJSON.writeValueAsString(mcFields));
    }

    public static Map<String,String> mcApiValues(MCContact mcContact){
        Map<String,String> mcValues=new LinkedHashMap<>();
        try{JsonNode mcExtras=mcJSON.readTree(mcContact.mcExtrasJson());if(mcExtras!=null)mcFlatten(mcExtras,"",mcValues);}catch(Exception mcIgnored){}
        String mcService="Empresa".equals(mcContact.mcKind())?"RUC":"DNI";
        for(String mcProvider:List.of("Factiliza","APIsPERU"))MCApiFieldCatalog.mcNormalize(mcProvider,mcService,mcValues).forEach(mcValues::putIfAbsent);
        mcValues.put("numero",mcContact.mcNumber());
        if("RUC".equals(mcService))mcPutNonEmpty(mcValues,"razon_social",mcContact.mcGiven());
        else{
            mcPutNonEmpty(mcValues,"nombres",mcContact.mcGiven());
            mcPutNonEmpty(mcValues,"apellidos",mcContact.mcFamily());
            MCApiFieldCatalog.mcNormalizePersonName(mcValues);
        }
        mcPutNonEmpty(mcValues,"direccion",mcContact.mcAddress());mcPutNonEmpty(mcValues,"direccion_completa",mcContact.mcAddress());
        mcPutNonEmpty(mcValues,"representantes",mcContact.mcRepresentatives());
        return mcValues;
    }
    private static void mcFlatten(JsonNode mcNode,String mcPath,Map<String,String> mcValues){
        if(mcNode.isObject())mcNode.fields().forEachRemaining(mcE->mcFlatten(mcE.getValue(),mcPath.isEmpty()?mcE.getKey():mcPath+"."+mcE.getKey(),mcValues));
        else if(mcNode.isArray()){for(int mcI=0;mcI<mcNode.size();mcI++)mcFlatten(mcNode.get(mcI),mcPath+"["+mcI+"]",mcValues);}
        else if(!mcNode.isNull())mcValues.put(mcPath,mcNode.asText());
    }
    private static void mcPutNonEmpty(Map<String,String> mcValues,String mcKey,String mcValue){if(mcValue!=null&&!mcValue.isBlank())mcValues.put(mcKey,mcValue);}

    public static boolean mcClientKey(String mcKey,Collection<MCDocumentStore.MCDefinition> mcDefinitions){String mcBase=MCStudioData.mcVariableKey(MCDocumentEngine.mcBaseKey(mcKey));return mcDefinitions.stream().noneMatch(mcD->!mcD.mcBindToClient()&&MCStudioData.mcVariableKey(mcD.mcKey()).equals(mcBase));}
    public static Map<String,String> mcClientValues(Map<String,String> mcValues,Collection<MCDocumentStore.MCDefinition> mcDefinitions){Map<String,String> mcFiltered=new LinkedHashMap<>();mcValues.forEach((mcKey,mcValue)->{if(mcClientKey(mcKey,mcDefinitions))mcFiltered.put(mcKey,mcValue);});return mcFiltered;}
    public static MCResult mcSave(MCStudioData mcData,List<MCContactBinding> mcBindings,Map<String,String> mcValues,Map<String,String> mcVariableTypes,Collection<MCDocumentStore.MCDefinition> mcDefinitions)throws SQLException{
        return mcSave(mcData,mcBindings.stream().filter(mcB->mcClientKey(mcB.mcVariableKey(),mcDefinitions)).toList(),mcClientValues(mcValues,mcDefinitions),mcVariableTypes);
    }
    public static List<MCContactBinding> mcFormBindings(long mcTemplateId,List<MCVariable> mcVariables,List<MCContactBinding> mcExplicit,List<MCApiContext> mcContexts,Map<String,MCApiBinding> mcApi,Collection<MCDocumentStore.MCDefinition> mcDefinitions){
        Map<String,MCApiBinding> mcFilteredApi=new LinkedHashMap<>();mcApi.forEach((mcKey,mcValue)->{if(mcClientKey(mcKey,mcDefinitions))mcFilteredApi.put(mcKey,mcValue);});
        return mcFormBindings(mcTemplateId,mcVariables.stream().filter(mcV->mcClientKey(mcV.mcKey(),mcDefinitions)).toList(),mcExplicit.stream().filter(mcB->mcClientKey(mcB.mcVariableKey(),mcDefinitions)).toList(),mcContexts.stream().filter(mcC->mcClientKey(mcC.mcVariableKey(),mcDefinitions)).toList(),mcFilteredApi);
    }
    public static MCResult mcSave(MCStudioData mcData,List<MCContactBinding> mcBindings,Map<String,String> mcValues,
                              Map<String,String> mcVariableTypes)throws SQLException{
        Map<String,List<MCContactBinding>> mcGroups=new LinkedHashMap<>();
        for(MCContactBinding mcBinding:mcBindings)mcGroups.computeIfAbsent(mcBinding.mcGroupKey(),mcIgnored->new ArrayList<>()).add(mcBinding);
        int mcCreated=0,mcUpdated=0,mcUnchanged=0,mcSkipped=0;
        for(List<MCContactBinding> mcGroup:mcGroups.values()){
            String mcKind=mcGroup.get(0).mcKind();Map<String,String> mcFields=new LinkedHashMap<>();
            for(MCContactBinding mcB:mcGroup){String mcValue=mcValues.getOrDefault(mcB.mcVariableKey(),"").trim();if(!mcValue.isBlank())mcFields.put(mcB.mcField(),mcValue);}
            String mcName=mcFields.getOrDefault("given",mcFields.getOrDefault("nombre_completo","")),mcNumber=mcFields.getOrDefault("number","");
            String mcDocType=mcFields.getOrDefault("document_type","");
            if("Empresa".equals(mcKind))mcDocType="RUC";
            else if(mcDocType.isBlank()&&mcNumber.matches("\\d{8}"))mcDocType="DNI";
            if(!mcValidIdentifier(mcKind,mcDocType,mcNumber)&&!(mcGroup.get(0).mcGroupKey().startsWith("persona:")&&mcNumber.isBlank()&&!mcName.isBlank())){mcSkipped++;continue;}
            try{
                Map<String,String> mcExtras=new LinkedHashMap<>();String mcPerson=MCDocumentEngine.mcPersonaKey(mcGroup.get(0).mcVariableKey());mcValues.forEach((mcKey,mcValue)->{if(MCDocumentEngine.mcPersonaKey(mcKey).equals(mcPerson))mcExtras.put(MCDocumentEngine.mcBaseKey(mcKey),mcValue);});
                for(MCContactBinding mcB:mcGroup){String mcValue=mcValues.getOrDefault(mcB.mcVariableKey(),"").trim();if(!mcValue.isBlank()){
                    mcExtras.put(MCDocumentEngine.mcBaseKey(mcB.mcVariableKey()),mcValue);
                    String mcCanonical=switch(mcB.mcField()){case "number"->"numero";case "given"->"Empresa".equals(mcKind)?"razon_social":"nombres";case "family"->"apellidos";case "address"->"direccion";case "representatives"->"representantes";default->mcB.mcField().startsWith("extra:")?MCDocumentEngine.mcBaseKey(mcB.mcVariableKey()):mcB.mcField();};
                    mcExtras.put(mcCanonical,mcValue);
                }}
                String mcFamily=mcFields.getOrDefault("family",(mcFields.getOrDefault("apellido_paterno","")+" "+mcFields.getOrDefault("apellido_materno","")).trim());
                MCContact mcIncoming=new MCContact(0,mcKind,mcName,mcFamily,mcDocType,mcNumber,mcFields.getOrDefault("address",""),mcFields.getOrDefault("representatives",""),mcJSON.writeValueAsString(mcExtras));
                MCContact mcExisting=mcData.mcMatchingContact(mcIncoming);
                if(mcExisting==null){mcData.mcUpsertContact(mcIncoming);mcCreated++;continue;}
                boolean mcFullNameOnly=!mcFields.containsKey("given")&&mcFields.containsKey("nombre_completo")&&!mcFields.containsKey("family")&&!mcFields.containsKey("apellido_paterno")&&!mcFields.containsKey("apellido_materno");
                boolean mcSameFullName=MCStudioData.mcNormalized(mcName).equals(MCStudioData.mcNormalized(mcExisting.mcGiven()+" "+mcExisting.mcFamily()));
                if(mcFullNameOnly&&mcSameFullName)mcIncoming=new MCContact(0,mcKind,mcExisting.mcGiven(),mcExisting.mcFamily(),mcDocType,mcNumber,mcIncoming.mcAddress(),mcIncoming.mcRepresentatives(),mcIncoming.mcExtrasJson());
                MCContact mcMerged=MCStudioData.mcMergeContact(mcExisting,mcIncoming);
                if(mcFullNameOnly&&!mcSameFullName)mcMerged=new MCContact(mcMerged.mcId(),mcMerged.mcKind(),mcName,"",mcMerged.mcDocumentType(),mcMerged.mcNumber(),mcMerged.mcAddress(),mcMerged.mcRepresentatives(),mcMerged.mcExtrasJson());
                if(mcExisting.equals(mcMerged)){mcUnchanged++;continue;}
                mcData.mcSaveContact(mcMerged);mcUpdated++;
            }catch(java.io.IOException mcE){throw new SQLException("No se pudo guardar el cliente.",mcE);}
        }
        return new MCResult(mcCreated,mcUpdated,mcUnchanged,mcSkipped);
    }

    public static List<MCContactBinding> mcFormBindings(long mcTemplateId,List<MCVariable> mcVariables,List<MCContactBinding> mcExplicit,List<MCApiContext> mcContexts,Map<String,MCApiBinding> mcApi){
        Map<String,List<MCVariable>> mcScopes=new LinkedHashMap<>();for(MCVariable mcVariable:mcVariables)mcScopes.computeIfAbsent(MCDocumentEngine.mcPersonaKey(mcVariable.mcKey()),mcK->new ArrayList<>()).add(mcVariable);List<MCContactBinding> mcResult=new ArrayList<>();
        for(var mcScope:mcScopes.entrySet()){
            String mcPerson=mcScope.getKey();if(mcPerson.isEmpty()){mcResult.addAll(mcBaseFormBindings(mcTemplateId,mcScope.getValue(),mcExplicit.stream().filter(mcB->MCDocumentEngine.mcPersonaKey(mcB.mcVariableKey()).isEmpty()).toList(),mcContexts.stream().filter(mcC->MCDocumentEngine.mcPersonaKey(mcC.mcVariableKey()).isEmpty()).toList(),mcApi));continue;}
            List<MCVariable> mcLocal=mcScope.getValue().stream().map(mcV->new MCVariable(mcV.mcId(),mcV.mcTemplateId(),MCDocumentEngine.mcBaseKey(mcV.mcKey()),mcV.mcLabel(),mcV.mcType(),mcV.mcDefaultValue(),mcV.mcRequired(),mcV.mcPosition())).toList();
            List<MCContactBinding> mcLocalBindings=mcExplicit.stream().filter(mcB->mcPerson.equals(MCDocumentEngine.mcPersonaKey(mcB.mcVariableKey()))).map(mcB->new MCContactBinding(mcB.mcId(),mcTemplateId,mcB.mcGroupKey(),mcB.mcKind(),mcB.mcField(),MCDocumentEngine.mcBaseKey(mcB.mcVariableKey()))).toList();
            List<MCApiContext> mcLocalContexts=mcContexts.stream().filter(mcC->mcPerson.equals(MCDocumentEngine.mcPersonaKey(mcC.mcVariableKey()))).map(mcC->new MCApiContext(mcTemplateId,MCDocumentEngine.mcBaseKey(mcC.mcVariableKey()),mcC.mcGroupKey(),mcC.mcService(),mcC.mcSource())).toList();
            Map<String,MCApiBinding> mcLocalApi=new LinkedHashMap<>();mcApi.forEach((mcKey,mcValue)->{if(mcPerson.equals(MCDocumentEngine.mcPersonaKey(mcKey)))mcLocalApi.put(MCDocumentEngine.mcBaseKey(mcKey),mcValue);});
            List<MCContactBinding> mcBindings=new ArrayList<>(mcBaseFormBindings(mcTemplateId,mcLocal,mcLocalBindings,mcLocalContexts,mcLocalApi));
            if(mcBindings.isEmpty())for(MCVariable mcV:mcLocal){String mcField=mcManualField(mcV);mcBindings.add(new MCContactBinding(0,mcTemplateId,"persona:"+mcPerson,"Persona",mcField==null?"extra:"+mcV.mcKey():mcField,mcV.mcKey()));}
            for(MCContactBinding mcB:mcBindings)mcResult.add(new MCContactBinding(0,mcTemplateId,"persona:"+mcPerson,mcB.mcKind(),mcB.mcField(),MCDocumentEngine.mcQualify(mcPerson,mcB.mcVariableKey())));
        }return mcResult;
    }
    private static List<MCContactBinding> mcBaseFormBindings(long mcTemplateId,List<MCVariable> mcVariables,List<MCContactBinding> mcExplicit,
                                                     List<MCApiContext> mcContexts,Map<String,MCApiBinding> mcApi){
        List<MCContactBinding> mcResult=new ArrayList<>(mcExplicit);
        Map<String,String> mcSources=new LinkedHashMap<>(),mcServices=new LinkedHashMap<>();
        for(MCVariable mcV:mcVariables){
            MCApiContext mcContext=mcContexts.stream().filter(mcC->mcC.mcVariableKey().equals(mcV.mcKey())).findFirst().orElse(null);
            MCContactBinding mcBound=mcExplicit.stream().filter(mcB->mcB.mcVariableKey().equals(mcV.mcKey())).findFirst().orElse(null);
            MCApiBinding mcBinding=mcApi.get(mcV.mcKey());
            String mcService;
            if(mcContext!=null)mcService=mcContext.mcSource()?mcContext.mcService():null;
            else if(mcBound!=null)mcService="number".equals(mcBound.mcField())?("Empresa".equals(mcBound.mcKind())?"RUC":"DNI"):null;
            else if(List.of("DNI","RUC").contains(mcV.mcType()))mcService=mcV.mcType();
            else if(mcBinding!=null)mcService="numero".equals(MCApiFieldCatalog.mcCanonical(mcBinding.mcService(),mcBinding.mcPath()))?mcBinding.mcService():null;
            else mcService=mcIdentifierService(mcV);
            if(mcService==null)continue;
            String mcGroup=mcContext!=null?mcContext.mcGroupKey():mcBound!=null?mcBound.mcGroupKey():"document:"+mcV.mcKey();
            mcSources.put(mcV.mcKey(),mcGroup);mcServices.put(mcV.mcKey(),mcService);
            if(mcBound==null)mcResult.add(new MCContactBinding(0,mcTemplateId,mcGroup,"RUC".equals(mcService)?"Empresa":"Persona","number",mcV.mcKey()));
        }
        for(MCVariable mcV:mcVariables){
            if(mcResult.stream().anyMatch(mcB->mcB.mcVariableKey().equals(mcV.mcKey())))continue;
            MCApiContext mcContext=mcContexts.stream().filter(mcC->mcC.mcVariableKey().equals(mcV.mcKey())).findFirst().orElse(null);
            MCApiBinding mcBinding=mcApi.get(mcV.mcKey());
            Set<String> mcCandidates=new LinkedHashSet<>();
            for(var mcSource:mcSources.entrySet())if((mcContext==null||mcContext.mcGroupKey().equals(mcSource.getValue())&&mcContext.mcService().equals(mcServices.get(mcSource.getKey())))&&(mcBinding==null||mcBinding.mcService().equals(mcServices.get(mcSource.getKey()))))mcCandidates.add(mcSource.getValue());
            if(mcCandidates.size()!=1)continue;
            String mcGroup=mcCandidates.iterator().next();String mcSource=mcSources.keySet().stream().filter(mcK->mcSources.get(mcK).equals(mcGroup)).findFirst().orElseThrow();String mcService=mcServices.get(mcSource);
            String mcCanonical=mcBinding==null?null:MCApiFieldCatalog.mcCanonical(mcService,mcBinding.mcPath());
            String mcField=mcCanonical==null?mcManualField(mcV):switch(mcCanonical){
                case "nombres","razon_social"->"given";case "apellidos"->"family";case "direccion","direccion_completa"->"address";case "representantes"->"representatives";case "numero"->"number";default->mcCanonical;
            };
            boolean mcDuplicate=false;for(MCContactBinding mcB:mcResult)if(mcB.mcGroupKey().equals(mcGroup)&&mcB.mcField().equals(mcField))mcDuplicate=true;
            if(mcField==null||mcDuplicate)mcField="extra:"+mcV.mcKey();
            mcResult.add(new MCContactBinding(0,mcTemplateId,mcGroup,"RUC".equals(mcService)?"Empresa":"Persona",mcField,mcV.mcKey()));
        }
        return mcResult;
    }
    static String mcIdentifierService(MCVariable mcVariable){
        if(List.of("DNI","RUC").contains(mcVariable.mcType()))return mcVariable.mcType();
        String mcText=mcNormalizedName(mcVariable);
        if(mcText.contains("ruc"))return "RUC";
        return mcText.contains("dni")?"DNI":null;
    }
    private static String mcNormalizedName(MCVariable mcVariable){
        return Normalizer.normalize(mcVariable.mcKey()+" "+mcVariable.mcLabel(),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("([a-z])([A-Z])","$1 $2").toLowerCase(Locale.ROOT).replace('_',' ');
    }
    private static String mcManualField(MCVariable mcV){
        String mcText=mcNormalizedName(mcV);
        if("Domicilio".equals(mcV.mcType())||mcText.matches(".*\\b(direccion|domicilio)\\b.*"))return "address";
        if(mcText.matches(".*\\b(representantes?)\\b.*"))return "representatives";
        if(mcText.matches(".*\\b(apellido paterno|primer apellido)\\b.*"))return "apellido_paterno";
        if(mcText.matches(".*\\b(apellido materno|segundo apellido)\\b.*"))return "apellido_materno";
        if(mcText.matches(".*\\b(apellidos?)\\b.*"))return "family";
        if(mcText.matches(".*\\b(nombre completo|nombres completo|nombres com)\\b.*"))return "nombre_completo";
        if(mcText.matches(".*\\b(nombres?|razon social|cliente|empresa|dirigido|solicitante|destinatario|arrendador|arrendatario)\\b.*"))return "given";
        return null;
    }
    private static boolean mcValidIdentifier(String mcKind,String mcType,String mcNumber){
        if("Empresa".equals(mcKind))return mcNumber.matches("\\d{11}");
        if(mcType.isBlank())return false;
        if("DNI".equalsIgnoreCase(mcType))return mcNumber.matches("\\d{8}");
        return mcNumber.matches("[A-Za-z0-9-]{4,30}");
    }
    private static String mcPrefer(String mcNewer,String mcOlder){return mcNewer==null||mcNewer.isBlank()?mcOlder:mcNewer;}
}
