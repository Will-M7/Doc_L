package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.MCDocumentStore.MCVariable;
import com.ejemplo.crudpersona.studio.MCStudioData.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.text.Normalizer;
import java.util.*;

public final class MCContactReuse {
    public record MCResult(@com.fasterxml.jackson.annotation.JsonProperty("created") int MCcreated,@com.fasterxml.jackson.annotation.JsonProperty("updated") int MCupdated,@com.fasterxml.jackson.annotation.JsonProperty("unchanged") int MCunchanged,@com.fasterxml.jackson.annotation.JsonProperty("skipped") int MCskipped){}
    private static final ObjectMapper MCJSON=new ObjectMapper();
    private MCContactReuse(){}

    public static Map<String,String> MCvaluesFor(MCContact MCcontact,List<MCContactBinding> MCgroup){
        Map<String,String> MCvalues=new LinkedHashMap<>();
        Map<String,String> MCextras=MCapiValues(MCcontact);
        for(MCContactBinding MCb:MCgroup){
            String MCvalue=switch(MCb.MCfield()){
                case "given"->MCcontact.MCgiven();case "family"->MCcontact.MCfamily();case "document_type"->MCcontact.MCdocumentType();
                case "number"->MCcontact.MCnumber();case "address"->MCcontact.MCaddress();case "representatives"->MCcontact.MCrepresentatives();
                default->MCextras.getOrDefault(MCb.MCfield().startsWith("extra:")?MCb.MCfield().substring(6):MCb.MCfield(),"");
            };
            MCvalues.put(MCb.MCvariableKey(),MCvalue==null?"":MCvalue);
        }
        return MCvalues;
    }

    public static MCContact MCfromApi(MCLookupRouter.MCResult MCresult)throws Exception{
        Map<String,String> MCfields=new LinkedHashMap<>(MCresult.MCraw());MCfields.putAll(MCresult.MCvalues());
        boolean MCcompany="RUC".equals(MCresult.MCservice());if(!MCcompany)MCApiFieldCatalog.MCnormalizePersonName(MCfields);
        String MCname=MCcompany?MCfields.getOrDefault("razon_social",""):MCfields.getOrDefault("nombres","");
        if(MCname.isBlank())MCname=MCfields.getOrDefault(MCcompany?"nombre_o_razon_social":"nombre_completo","");
        String MCfamily=(MCfields.getOrDefault("apellido_paterno","")+" "+MCfields.getOrDefault("apellido_materno","")).trim();if(MCfamily.isBlank())MCfamily=MCfields.getOrDefault("apellidos","");
        String MCaddress=MCprefer(MCfields.get("direccion_completa"),MCfields.getOrDefault("direccion",""));
        String MCreps=MCfields.entrySet().stream().filter(MCe->MCe.getKey().toLowerCase(Locale.ROOT).contains("representante"))
                .map(Map.Entry::getValue).filter(MCv->MCv!=null&&!MCv.isBlank()).distinct().collect(java.util.stream.Collectors.joining("; "));
        return new MCContact(0,MCcompany?"Empresa":"Persona",MCname,MCcompany?"":MCfamily,MCresult.MCservice(),MCresult.MCidentifier().trim(),MCaddress,MCreps,MCJSON.writeValueAsString(MCfields));
    }

    public static Map<String,String> MCapiValues(MCContact MCcontact){
        Map<String,String> MCvalues=new LinkedHashMap<>();
        try{JsonNode MCextras=MCJSON.readTree(MCcontact.MCextrasJson());if(MCextras!=null)MCflatten(MCextras,"",MCvalues);}catch(Exception MCignored){}
        String MCservice="Empresa".equals(MCcontact.MCkind())?"RUC":"DNI";
        for(String MCprovider:List.of("Factiliza","APIsPERU"))MCApiFieldCatalog.MCnormalize(MCprovider,MCservice,MCvalues).forEach(MCvalues::putIfAbsent);
        MCvalues.put("numero",MCcontact.MCnumber());
        if("RUC".equals(MCservice))MCputNonEmpty(MCvalues,"razon_social",MCcontact.MCgiven());
        else{
            MCputNonEmpty(MCvalues,"nombres",MCcontact.MCgiven());
            MCputNonEmpty(MCvalues,"apellidos",MCcontact.MCfamily());
            MCApiFieldCatalog.MCnormalizePersonName(MCvalues);
        }
        MCputNonEmpty(MCvalues,"direccion",MCcontact.MCaddress());MCputNonEmpty(MCvalues,"direccion_completa",MCcontact.MCaddress());
        MCputNonEmpty(MCvalues,"representantes",MCcontact.MCrepresentatives());
        return MCvalues;
    }
    private static void MCflatten(JsonNode MCnode,String MCpath,Map<String,String> MCvalues){
        if(MCnode.isObject())MCnode.fields().forEachRemaining(MCe->MCflatten(MCe.getValue(),MCpath.isEmpty()?MCe.getKey():MCpath+"."+MCe.getKey(),MCvalues));
        else if(MCnode.isArray()){for(int MCi=0;MCi<MCnode.size();MCi++)MCflatten(MCnode.get(MCi),MCpath+"["+MCi+"]",MCvalues);}
        else if(!MCnode.isNull())MCvalues.put(MCpath,MCnode.asText());
    }
    private static void MCputNonEmpty(Map<String,String> MCvalues,String MCkey,String MCvalue){if(MCvalue!=null&&!MCvalue.isBlank())MCvalues.put(MCkey,MCvalue);}

    public static boolean MCclientKey(String MCkey,Collection<MCDocumentStore.MCDefinition> MCdefinitions){String MCbase=MCStudioData.MCvariableKey(MCDocumentEngine.MCbaseKey(MCkey));return MCdefinitions.stream().noneMatch(MCd->!MCd.MCbindToClient()&&MCStudioData.MCvariableKey(MCd.MCkey()).equals(MCbase));}
    public static Map<String,String> MCclientValues(Map<String,String> MCvalues,Collection<MCDocumentStore.MCDefinition> MCdefinitions){Map<String,String> MCfiltered=new LinkedHashMap<>();MCvalues.forEach((MCkey,MCvalue)->{if(MCclientKey(MCkey,MCdefinitions))MCfiltered.put(MCkey,MCvalue);});return MCfiltered;}
    public static MCResult MCsave(MCStudioData MCdata,List<MCContactBinding> MCbindings,Map<String,String> MCvalues,Map<String,String> MCvariableTypes,Collection<MCDocumentStore.MCDefinition> MCdefinitions)throws SQLException{
        return MCsave(MCdata,MCbindings.stream().filter(MCb->MCclientKey(MCb.MCvariableKey(),MCdefinitions)).toList(),MCclientValues(MCvalues,MCdefinitions),MCvariableTypes);
    }
    public static List<MCContactBinding> MCformBindings(long MCtemplateId,List<MCVariable> MCvariables,List<MCContactBinding> MCexplicit,List<MCApiContext> MCcontexts,Map<String,MCApiBinding> MCapi,Collection<MCDocumentStore.MCDefinition> MCdefinitions){
        Map<String,MCApiBinding> MCfilteredApi=new LinkedHashMap<>();MCapi.forEach((MCkey,MCvalue)->{if(MCclientKey(MCkey,MCdefinitions))MCfilteredApi.put(MCkey,MCvalue);});
        return MCformBindings(MCtemplateId,MCvariables.stream().filter(MCv->MCclientKey(MCv.MCkey(),MCdefinitions)).toList(),MCexplicit.stream().filter(MCb->MCclientKey(MCb.MCvariableKey(),MCdefinitions)).toList(),MCcontexts.stream().filter(MCc->MCclientKey(MCc.MCvariableKey(),MCdefinitions)).toList(),MCfilteredApi);
    }
    public static MCResult MCsave(MCStudioData MCdata,List<MCContactBinding> MCbindings,Map<String,String> MCvalues,
                              Map<String,String> MCvariableTypes)throws SQLException{
        Map<String,List<MCContactBinding>> MCgroups=new LinkedHashMap<>();
        for(MCContactBinding MCbinding:MCbindings)MCgroups.computeIfAbsent(MCbinding.MCgroupKey(),MCignored->new ArrayList<>()).add(MCbinding);
        int MCcreated=0,MCupdated=0,MCunchanged=0,MCskipped=0;
        for(List<MCContactBinding> MCgroup:MCgroups.values()){
            String MCkind=MCgroup.get(0).MCkind();Map<String,String> MCfields=new LinkedHashMap<>();
            for(MCContactBinding MCb:MCgroup){String MCvalue=MCvalues.getOrDefault(MCb.MCvariableKey(),"").trim();if(!MCvalue.isBlank())MCfields.put(MCb.MCfield(),MCvalue);}
            String MCname=MCfields.getOrDefault("given",MCfields.getOrDefault("nombre_completo","")),MCnumber=MCfields.getOrDefault("number","");
            String MCdocType=MCfields.getOrDefault("document_type","");
            if("Empresa".equals(MCkind))MCdocType="RUC";
            else if(MCdocType.isBlank()&&MCnumber.matches("\\d{8}"))MCdocType="DNI";
            if(!MCvalidIdentifier(MCkind,MCdocType,MCnumber)&&!(MCgroup.get(0).MCgroupKey().startsWith("persona:")&&MCnumber.isBlank()&&!MCname.isBlank())){MCskipped++;continue;}
            try{
                Map<String,String> MCextras=new LinkedHashMap<>();String MCperson=MCDocumentEngine.MCpersonaKey(MCgroup.get(0).MCvariableKey());MCvalues.forEach((MCkey,MCvalue)->{if(MCDocumentEngine.MCpersonaKey(MCkey).equals(MCperson))MCextras.put(MCDocumentEngine.MCbaseKey(MCkey),MCvalue);});
                for(MCContactBinding MCb:MCgroup){String MCvalue=MCvalues.getOrDefault(MCb.MCvariableKey(),"").trim();if(!MCvalue.isBlank()){
                    MCextras.put(MCDocumentEngine.MCbaseKey(MCb.MCvariableKey()),MCvalue);
                    String MCcanonical=switch(MCb.MCfield()){case "number"->"numero";case "given"->"Empresa".equals(MCkind)?"razon_social":"nombres";case "family"->"apellidos";case "address"->"direccion";case "representatives"->"representantes";default->MCb.MCfield().startsWith("extra:")?MCDocumentEngine.MCbaseKey(MCb.MCvariableKey()):MCb.MCfield();};
                    MCextras.put(MCcanonical,MCvalue);
                }}
                String MCfamily=MCfields.getOrDefault("family",(MCfields.getOrDefault("apellido_paterno","")+" "+MCfields.getOrDefault("apellido_materno","")).trim());
                MCContact MCincoming=new MCContact(0,MCkind,MCname,MCfamily,MCdocType,MCnumber,MCfields.getOrDefault("address",""),MCfields.getOrDefault("representatives",""),MCJSON.writeValueAsString(MCextras));
                MCContact MCexisting=MCdata.MCmatchingContact(MCincoming);
                if(MCexisting==null){MCdata.MCupsertContact(MCincoming);MCcreated++;continue;}
                boolean MCfullNameOnly=!MCfields.containsKey("given")&&MCfields.containsKey("nombre_completo")&&!MCfields.containsKey("family")&&!MCfields.containsKey("apellido_paterno")&&!MCfields.containsKey("apellido_materno");
                boolean MCsameFullName=MCStudioData.MCnormalized(MCname).equals(MCStudioData.MCnormalized(MCexisting.MCgiven()+" "+MCexisting.MCfamily()));
                if(MCfullNameOnly&&MCsameFullName)MCincoming=new MCContact(0,MCkind,MCexisting.MCgiven(),MCexisting.MCfamily(),MCdocType,MCnumber,MCincoming.MCaddress(),MCincoming.MCrepresentatives(),MCincoming.MCextrasJson());
                MCContact MCmerged=MCStudioData.MCmergeContact(MCexisting,MCincoming);
                if(MCfullNameOnly&&!MCsameFullName)MCmerged=new MCContact(MCmerged.MCid(),MCmerged.MCkind(),MCname,"",MCmerged.MCdocumentType(),MCmerged.MCnumber(),MCmerged.MCaddress(),MCmerged.MCrepresentatives(),MCmerged.MCextrasJson());
                if(MCexisting.equals(MCmerged)){MCunchanged++;continue;}
                MCdata.MCsaveContact(MCmerged);MCupdated++;
            }catch(java.io.IOException MCe){throw new SQLException("No se pudo guardar el cliente.",MCe);}
        }
        return new MCResult(MCcreated,MCupdated,MCunchanged,MCskipped);
    }

    public static List<MCContactBinding> MCformBindings(long MCtemplateId,List<MCVariable> MCvariables,List<MCContactBinding> MCexplicit,List<MCApiContext> MCcontexts,Map<String,MCApiBinding> MCapi){
        Map<String,List<MCVariable>> MCscopes=new LinkedHashMap<>();for(MCVariable MCvariable:MCvariables)MCscopes.computeIfAbsent(MCDocumentEngine.MCpersonaKey(MCvariable.MCkey()),MCk->new ArrayList<>()).add(MCvariable);List<MCContactBinding> MCresult=new ArrayList<>();
        for(var MCscope:MCscopes.entrySet()){
            String MCperson=MCscope.getKey();if(MCperson.isEmpty()){MCresult.addAll(MCbaseFormBindings(MCtemplateId,MCscope.getValue(),MCexplicit.stream().filter(MCb->MCDocumentEngine.MCpersonaKey(MCb.MCvariableKey()).isEmpty()).toList(),MCcontexts.stream().filter(MCc->MCDocumentEngine.MCpersonaKey(MCc.MCvariableKey()).isEmpty()).toList(),MCapi));continue;}
            List<MCVariable> MClocal=MCscope.getValue().stream().map(MCv->new MCVariable(MCv.MCid(),MCv.MCtemplateId(),MCDocumentEngine.MCbaseKey(MCv.MCkey()),MCv.MClabel(),MCv.MCtype(),MCv.MCdefaultValue(),MCv.MCrequired(),MCv.MCposition())).toList();
            List<MCContactBinding> MClocalBindings=MCexplicit.stream().filter(MCb->MCperson.equals(MCDocumentEngine.MCpersonaKey(MCb.MCvariableKey()))).map(MCb->new MCContactBinding(MCb.MCid(),MCtemplateId,MCb.MCgroupKey(),MCb.MCkind(),MCb.MCfield(),MCDocumentEngine.MCbaseKey(MCb.MCvariableKey()))).toList();
            List<MCApiContext> MClocalContexts=MCcontexts.stream().filter(MCc->MCperson.equals(MCDocumentEngine.MCpersonaKey(MCc.MCvariableKey()))).map(MCc->new MCApiContext(MCtemplateId,MCDocumentEngine.MCbaseKey(MCc.MCvariableKey()),MCc.MCgroupKey(),MCc.MCservice(),MCc.MCsource())).toList();
            Map<String,MCApiBinding> MClocalApi=new LinkedHashMap<>();MCapi.forEach((MCkey,MCvalue)->{if(MCperson.equals(MCDocumentEngine.MCpersonaKey(MCkey)))MClocalApi.put(MCDocumentEngine.MCbaseKey(MCkey),MCvalue);});
            List<MCContactBinding> MCbindings=new ArrayList<>(MCbaseFormBindings(MCtemplateId,MClocal,MClocalBindings,MClocalContexts,MClocalApi));
            if(MCbindings.isEmpty())for(MCVariable MCv:MClocal){String MCfield=MCmanualField(MCv);MCbindings.add(new MCContactBinding(0,MCtemplateId,"persona:"+MCperson,"Persona",MCfield==null?"extra:"+MCv.MCkey():MCfield,MCv.MCkey()));}
            for(MCContactBinding MCb:MCbindings)MCresult.add(new MCContactBinding(0,MCtemplateId,"persona:"+MCperson,MCb.MCkind(),MCb.MCfield(),MCDocumentEngine.MCqualify(MCperson,MCb.MCvariableKey())));
        }return MCresult;
    }
    private static List<MCContactBinding> MCbaseFormBindings(long MCtemplateId,List<MCVariable> MCvariables,List<MCContactBinding> MCexplicit,
                                                     List<MCApiContext> MCcontexts,Map<String,MCApiBinding> MCapi){
        List<MCContactBinding> MCresult=new ArrayList<>(MCexplicit);
        Map<String,String> MCsources=new LinkedHashMap<>(),MCservices=new LinkedHashMap<>();
        for(MCVariable MCv:MCvariables){
            MCApiContext MCcontext=MCcontexts.stream().filter(MCc->MCc.MCvariableKey().equals(MCv.MCkey())).findFirst().orElse(null);
            MCContactBinding MCbound=MCexplicit.stream().filter(MCb->MCb.MCvariableKey().equals(MCv.MCkey())).findFirst().orElse(null);
            MCApiBinding MCbinding=MCapi.get(MCv.MCkey());
            String MCservice;
            if(MCcontext!=null)MCservice=MCcontext.MCsource()?MCcontext.MCservice():null;
            else if(MCbound!=null)MCservice="number".equals(MCbound.MCfield())?("Empresa".equals(MCbound.MCkind())?"RUC":"DNI"):null;
            else if(List.of("DNI","RUC").contains(MCv.MCtype()))MCservice=MCv.MCtype();
            else if(MCbinding!=null)MCservice="numero".equals(MCApiFieldCatalog.MCcanonical(MCbinding.MCservice(),MCbinding.MCpath()))?MCbinding.MCservice():null;
            else MCservice=MCidentifierService(MCv);
            if(MCservice==null)continue;
            String MCgroup=MCcontext!=null?MCcontext.MCgroupKey():MCbound!=null?MCbound.MCgroupKey():"document:"+MCv.MCkey();
            MCsources.put(MCv.MCkey(),MCgroup);MCservices.put(MCv.MCkey(),MCservice);
            if(MCbound==null)MCresult.add(new MCContactBinding(0,MCtemplateId,MCgroup,"RUC".equals(MCservice)?"Empresa":"Persona","number",MCv.MCkey()));
        }
        for(MCVariable MCv:MCvariables){
            if(MCresult.stream().anyMatch(MCb->MCb.MCvariableKey().equals(MCv.MCkey())))continue;
            MCApiContext MCcontext=MCcontexts.stream().filter(MCc->MCc.MCvariableKey().equals(MCv.MCkey())).findFirst().orElse(null);
            MCApiBinding MCbinding=MCapi.get(MCv.MCkey());
            Set<String> MCcandidates=new LinkedHashSet<>();
            for(var MCsource:MCsources.entrySet())if((MCcontext==null||MCcontext.MCgroupKey().equals(MCsource.getValue())&&MCcontext.MCservice().equals(MCservices.get(MCsource.getKey())))&&(MCbinding==null||MCbinding.MCservice().equals(MCservices.get(MCsource.getKey()))))MCcandidates.add(MCsource.getValue());
            if(MCcandidates.size()!=1)continue;
            String MCgroup=MCcandidates.iterator().next();String MCsource=MCsources.keySet().stream().filter(MCk->MCsources.get(MCk).equals(MCgroup)).findFirst().orElseThrow();String MCservice=MCservices.get(MCsource);
            String MCcanonical=MCbinding==null?null:MCApiFieldCatalog.MCcanonical(MCservice,MCbinding.MCpath());
            String MCfield=MCcanonical==null?MCmanualField(MCv):switch(MCcanonical){
                case "nombres","razon_social"->"given";case "apellidos"->"family";case "direccion","direccion_completa"->"address";case "representantes"->"representatives";case "numero"->"number";default->MCcanonical;
            };
            boolean MCduplicate=false;for(MCContactBinding MCb:MCresult)if(MCb.MCgroupKey().equals(MCgroup)&&MCb.MCfield().equals(MCfield))MCduplicate=true;
            if(MCfield==null||MCduplicate)MCfield="extra:"+MCv.MCkey();
            MCresult.add(new MCContactBinding(0,MCtemplateId,MCgroup,"RUC".equals(MCservice)?"Empresa":"Persona",MCfield,MCv.MCkey()));
        }
        return MCresult;
    }
    static String MCidentifierService(MCVariable MCvariable){
        if(List.of("DNI","RUC").contains(MCvariable.MCtype()))return MCvariable.MCtype();
        String MCtext=MCnormalizedName(MCvariable);
        if(MCtext.contains("ruc"))return "RUC";
        return MCtext.contains("dni")?"DNI":null;
    }
    private static String MCnormalizedName(MCVariable MCvariable){
        return Normalizer.normalize(MCvariable.MCkey()+" "+MCvariable.MClabel(),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("([a-z])([A-Z])","$1 $2").toLowerCase(Locale.ROOT).replace('_',' ');
    }
    private static String MCmanualField(MCVariable MCv){
        String MCtext=MCnormalizedName(MCv);
        if("Domicilio".equals(MCv.MCtype())||MCtext.matches(".*\\b(direccion|domicilio)\\b.*"))return "address";
        if(MCtext.matches(".*\\b(representantes?)\\b.*"))return "representatives";
        if(MCtext.matches(".*\\b(apellido paterno|primer apellido)\\b.*"))return "apellido_paterno";
        if(MCtext.matches(".*\\b(apellido materno|segundo apellido)\\b.*"))return "apellido_materno";
        if(MCtext.matches(".*\\b(apellidos?)\\b.*"))return "family";
        if(MCtext.matches(".*\\b(nombre completo|nombres completo|nombres com)\\b.*"))return "nombre_completo";
        if(MCtext.matches(".*\\b(nombres?|razon social|cliente|empresa|dirigido|solicitante|destinatario|arrendador|arrendatario)\\b.*"))return "given";
        return null;
    }
    private static boolean MCvalidIdentifier(String MCkind,String MCtype,String MCnumber){
        if("Empresa".equals(MCkind))return MCnumber.matches("\\d{11}");
        if(MCtype.isBlank())return false;
        if("DNI".equalsIgnoreCase(MCtype))return MCnumber.matches("\\d{8}");
        return MCnumber.matches("[A-Za-z0-9-]{4,30}");
    }
    private static String MCprefer(String MCnewer,String MColder){return MCnewer==null||MCnewer.isBlank()?MColder:MCnewer;}
}
