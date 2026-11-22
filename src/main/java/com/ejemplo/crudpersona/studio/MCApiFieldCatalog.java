package com.ejemplo.crudpersona.studio;

import java.util.*;

/** @author MC */
public final class MCApiFieldCatalog {
    /** @author MC */
    public record MCField(@com.fasterxml.jackson.annotation.JsonProperty("service") String mcService,@com.fasterxml.jackson.annotation.JsonProperty("key") String mcKey,@com.fasterxml.jackson.annotation.JsonProperty("label") String mcLabel,@com.fasterxml.jackson.annotation.JsonProperty("factilizaPath") String mcFactilizaPath,@com.fasterxml.jackson.annotation.JsonProperty("apisPeruPath") String mcApisPeruPath){
        @Override public String toString(){return mcLabel;}
    }
    private static final List<MCField> mcFIELDS=List.of(
        new MCField("DNI","numero","Número de DNI","data.numero","dni"),
        new MCField("DNI","nombres","Nombres","data.nombres","nombres"),
        new MCField("DNI","apellido_paterno","Apellido paterno","data.apellido_paterno","apellidoPaterno"),
        new MCField("DNI","apellido_materno","Apellido materno","data.apellido_materno","apellidoMaterno"),
        new MCField("DNI","nombre_completo","Nombre completo","data.nombre_completo","nombre_completo"),
        new MCField("DNI","direccion","Dirección","data.direccion",null),
        new MCField("DNI","direccion_completa","Dirección completa","data.direccion_completa",null),
        new MCField("DNI","departamento","Departamento","data.departamento",null),
        new MCField("DNI","provincia","Provincia","data.provincia",null),
        new MCField("DNI","distrito","Distrito","data.distrito",null),
        new MCField("DNI","ubigeo","Ubigeo","data.ubigeo[2]",null),
        new MCField("DNI","ubigeo_reniec","Ubigeo RENIEC","data.ubigeo_reniec",null),
        new MCField("DNI","ubigeo_sunat","Ubigeo SUNAT","data.ubigeo_sunat",null),
        new MCField("DNI","fecha_nacimiento","Fecha de nacimiento","data.fecha_nacimiento",null),
        new MCField("DNI","sexo","Sexo","data.sexo",null),
        new MCField("DNI","codigo_verificacion","Código de verificación",null,"codVerifica"),
        new MCField("RUC","numero","Número de RUC","data.numero","ruc"),
        new MCField("RUC","razon_social","Razón social","data.nombre_o_razon_social","razonSocial"),
        new MCField("RUC","nombre_comercial","Nombre comercial",null,"nombreComercial"),
        new MCField("RUC","tipo_contribuyente","Tipo de contribuyente","data.tipo_contribuyente",null),
        new MCField("RUC","estado","Estado del contribuyente","data.estado","estado"),
        new MCField("RUC","condicion","Condición del contribuyente","data.condicion","condicion"),
        new MCField("RUC","direccion","Dirección fiscal","data.direccion","direccion"),
        new MCField("RUC","direccion_completa","Dirección completa","data.direccion_completa",null),
        new MCField("RUC","departamento","Departamento","data.departamento","departamento"),
        new MCField("RUC","provincia","Provincia","data.provincia","provincia"),
        new MCField("RUC","distrito","Distrito","data.distrito","distrito"),
        new MCField("RUC","ubigeo","Ubigeo","data.ubigeo[2]","ubigeo"),
        new MCField("RUC","ubigeo_sunat","Ubigeo SUNAT","data.ubigeo_sunat",null),
        new MCField("RUC","telefonos","Teléfonos",null,"telefonos[0]"),
        new MCField("RUC","capital","Capital",null,"capital")
    );
    private MCApiFieldCatalog(){}
    public static List<MCField> mcFields(String mcService){return mcFIELDS.stream().filter(mcF->mcF.mcService().equals(mcService)).toList();}
    public static MCField mcField(String mcService,String mcKey){return mcFields(mcService).stream().filter(mcF->mcF.mcKey().equals(mcKey)).findFirst().orElse(null);}
    public static String mcCanonical(String mcService,String mcStored){
        if(mcStored==null||mcStored.isBlank())return null;
        if(mcStored.startsWith("field:"))return mcStored.substring(6);
        for(MCField mcF:mcFields(mcService))if(mcStored.equals(mcF.mcFactilizaPath())||mcStored.equals(mcF.mcApisPeruPath())||mcStored.equals(mcF.mcFactilizaPath()==null?null:mcF.mcFactilizaPath().replace("data.","")))return mcF.mcKey();
        return "legacy:"+mcStored;
    }
    public static String mcLabel(String mcService,String mcKey){MCField mcF=mcField(mcService,mcKey);return mcF==null?"Campo anterior sin equivalencia":mcF.mcLabel();}
    public static Set<String> mcSupportedKeys(String mcProvider,String mcService){Set<String> mcOut=new LinkedHashSet<>();for(MCField mcF:mcFields(mcService))if(("Factiliza".equals(mcProvider)?mcF.mcFactilizaPath():mcF.mcApisPeruPath())!=null)mcOut.add(mcF.mcKey());return mcOut;}
    public static Map<String,String> mcNormalize(String mcProvider,String mcService,Map<String,String> mcRaw){Map<String,String> mcValues=new LinkedHashMap<>();for(MCField mcField:mcFields(mcService)){String mcPath="Factiliza".equals(mcProvider)?mcField.mcFactilizaPath():mcField.mcApisPeruPath();if(mcPath==null)continue;String mcValue=mcRaw.get(mcPath);if("APIsPERU".equals(mcProvider)&&"telefonos".equals(mcField.mcKey())){List<String> mcNumbers=new ArrayList<>();for(int mcI=0;mcRaw.containsKey("telefonos["+mcI+"]");mcI++){String mcNumber=mcRaw.get("telefonos["+mcI+"]");if(mcNumber!=null&&!mcNumber.isBlank())mcNumbers.add(mcNumber);}mcValue=String.join(", ",mcNumbers);}if(mcValue!=null&&!mcValue.isBlank())mcValues.put(mcField.mcKey(),mcValue);}if("DNI".equals(mcService)){String mcFamily=mcRaw.get("Factiliza".equals(mcProvider)?"data.apellidos":"apellidos");if(mcFamily!=null&&!mcFamily.isBlank())mcValues.put("apellidos",mcFamily);mcNormalizePersonName(mcValues);}return mcValues;}
    public static void mcNormalizePersonName(Map<String,String> mcValues){
        String mcGiven=mcCleanName(mcValues.get("nombres")),mcPaternal=mcCleanName(mcValues.get("apellido_paterno")),mcMaternal=mcCleanName(mcValues.get("apellido_materno"));
        String mcFamily=mcCleanName(mcPaternal+" "+mcMaternal);if(mcFamily.isEmpty())mcFamily=mcCleanName(mcValues.get("apellidos"));
        String mcFull=mcCleanName(mcValues.get("nombre_completo")),mcFallbackFamily="";
        if(mcGiven.contains(",")){String[] mcParts=mcGiven.split(",",2);mcFallbackFamily=mcCleanName(mcParts[0]);mcGiven=mcCleanName(mcParts[1]);}
        if(mcFull.contains(",")){String[] mcParts=mcFull.split(",",2);if(mcFallbackFamily.isEmpty())mcFallbackFamily=mcCleanName(mcParts[0]);if(mcGiven.isEmpty())mcGiven=mcCleanName(mcParts[1]);mcFull=mcCleanName(mcParts[1]+" "+mcParts[0]);}
        if(mcFamily.isEmpty())mcFamily=mcFallbackFamily;
        if(mcFamily.isEmpty()&&!mcGiven.isEmpty()&&mcFull.length()>mcGiven.length()){
            if(mcFull.regionMatches(true,0,mcGiven+" ",0,mcGiven.length()+1))mcFamily=mcCleanName(mcFull.substring(mcGiven.length()));
            else if(mcFull.regionMatches(true,mcFull.length()-mcGiven.length()-1," "+mcGiven,0,mcGiven.length()+1))mcFamily=mcCleanName(mcFull.substring(0,mcFull.length()-mcGiven.length()));
        }
        if(mcGiven.isEmpty()&&!mcFull.isEmpty()&&!mcFamily.isEmpty())mcGiven=mcStripFamily(mcFull,mcFamily);
        mcGiven=mcStripFamily(mcGiven,mcFamily);
        if(!mcGiven.isEmpty()){
            mcValues.put("nombres",mcGiven);if(!mcFamily.isEmpty()||mcFull.isEmpty())mcFull=mcCleanName(mcGiven+" "+mcFamily);
            if(mcValues.containsKey("data.nombres"))mcValues.put("data.nombres",mcGiven);
        }
        if(!mcFamily.isEmpty()&&!mcValues.containsKey("apellidos")&&(!mcPaternal.isEmpty()&&!mcMaternal.isEmpty()||!mcFallbackFamily.isEmpty()||mcPaternal.isEmpty()&&mcMaternal.isEmpty()))mcValues.put("apellidos",mcFamily);
        if(!mcFull.isEmpty()){mcFull=mcCleanName(mcFull.replace(',', ' '));mcValues.put("nombre_completo",mcFull);if(mcValues.containsKey("data.nombre_completo"))mcValues.put("data.nombre_completo",mcFull);}
    }
    private static String mcStripFamily(String mcGiven,String mcFamily){
        if(mcFamily.isEmpty())return mcGiven;
        String mcPrevious;do{mcPrevious=mcGiven;if(mcGiven.length()>mcFamily.length()&&mcGiven.regionMatches(true,0,mcFamily+" ",0,mcFamily.length()+1))mcGiven=mcCleanName(mcGiven.substring(mcFamily.length()));if(mcGiven.length()>mcFamily.length()&&mcGiven.regionMatches(true,mcGiven.length()-mcFamily.length()-1," "+mcFamily,0,mcFamily.length()+1))mcGiven=mcCleanName(mcGiven.substring(0,mcGiven.length()-mcFamily.length()));}while(!mcGiven.equals(mcPrevious));return mcGiven;
    }
    private static String mcCleanName(String mcValue){return mcValue==null?"":mcValue.replaceAll("[\\s\\u00a0]+"," ").trim();}
    static Map<String,String> mcNormalizedResponse(String mcProvider,String mcService,Map<String,String> mcResponse){
        Map<String,String> mcRaw=new LinkedHashMap<>(mcResponse);if("DNI".equals(mcService)){Map<String,String> mcValues=mcNormalize(mcProvider,mcService,mcRaw);String mcPrefix="Factiliza".equals(mcProvider)?"data.":"";for(String mcKey:List.of("nombres","nombre_completo","apellidos"))if(mcValues.containsKey(mcKey))mcRaw.put(mcPrefix+mcKey,mcValues.get(mcKey));}return mcRaw;
    }
    public static String mcLegacyValue(String mcProvider,String mcStored,Map<String,String> mcRaw){return "Factiliza".equals(mcProvider)?mcRaw.get(mcStored):null;}
}
