package com.ejemplo.crudpersona.studio;

import java.util.*;

public final class MCApiFieldCatalog {
    public record MCField(@com.fasterxml.jackson.annotation.JsonProperty("service") String MCservice,@com.fasterxml.jackson.annotation.JsonProperty("key") String MCkey,@com.fasterxml.jackson.annotation.JsonProperty("label") String MClabel,@com.fasterxml.jackson.annotation.JsonProperty("factilizaPath") String MCfactilizaPath,@com.fasterxml.jackson.annotation.JsonProperty("apisPeruPath") String MCapisPeruPath){
        @Override public String toString(){return MClabel;}
    }
    private static final List<MCField> MCFIELDS=List.of(
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
    public static List<MCField> MCfields(String MCservice){return MCFIELDS.stream().filter(MCf->MCf.MCservice().equals(MCservice)).toList();}
    public static MCField MCfield(String MCservice,String MCkey){return MCfields(MCservice).stream().filter(MCf->MCf.MCkey().equals(MCkey)).findFirst().orElse(null);}
    public static String MCcanonical(String MCservice,String MCstored){
        if(MCstored==null||MCstored.isBlank())return null;
        if(MCstored.startsWith("field:"))return MCstored.substring(6);
        for(MCField MCf:MCfields(MCservice))if(MCstored.equals(MCf.MCfactilizaPath())||MCstored.equals(MCf.MCapisPeruPath())||MCstored.equals(MCf.MCfactilizaPath()==null?null:MCf.MCfactilizaPath().replace("data.","")))return MCf.MCkey();
        return "legacy:"+MCstored;
    }
    public static String MClabel(String MCservice,String MCkey){MCField MCf=MCfield(MCservice,MCkey);return MCf==null?"Campo anterior sin equivalencia":MCf.MClabel();}
    public static Set<String> MCsupportedKeys(String MCprovider,String MCservice){Set<String> MCout=new LinkedHashSet<>();for(MCField MCf:MCfields(MCservice))if(("Factiliza".equals(MCprovider)?MCf.MCfactilizaPath():MCf.MCapisPeruPath())!=null)MCout.add(MCf.MCkey());return MCout;}
    public static Map<String,String> MCnormalize(String MCprovider,String MCservice,Map<String,String> MCraw){Map<String,String> MCvalues=new LinkedHashMap<>();for(MCField MCfield:MCfields(MCservice)){String MCpath="Factiliza".equals(MCprovider)?MCfield.MCfactilizaPath():MCfield.MCapisPeruPath();if(MCpath==null)continue;String MCvalue=MCraw.get(MCpath);if("APIsPERU".equals(MCprovider)&&"telefonos".equals(MCfield.MCkey())){List<String> MCnumbers=new ArrayList<>();for(int MCi=0;MCraw.containsKey("telefonos["+MCi+"]");MCi++){String MCnumber=MCraw.get("telefonos["+MCi+"]");if(MCnumber!=null&&!MCnumber.isBlank())MCnumbers.add(MCnumber);}MCvalue=String.join(", ",MCnumbers);}if(MCvalue!=null&&!MCvalue.isBlank())MCvalues.put(MCfield.MCkey(),MCvalue);}if("DNI".equals(MCservice)){String MCfamily=MCraw.get("Factiliza".equals(MCprovider)?"data.apellidos":"apellidos");if(MCfamily!=null&&!MCfamily.isBlank())MCvalues.put("apellidos",MCfamily);MCnormalizePersonName(MCvalues);}return MCvalues;}
    public static void MCnormalizePersonName(Map<String,String> MCvalues){
        String MCgiven=MCcleanName(MCvalues.get("nombres")),MCpaternal=MCcleanName(MCvalues.get("apellido_paterno")),MCmaternal=MCcleanName(MCvalues.get("apellido_materno"));
        String MCfamily=MCcleanName(MCpaternal+" "+MCmaternal);if(MCfamily.isEmpty())MCfamily=MCcleanName(MCvalues.get("apellidos"));
        String MCfull=MCcleanName(MCvalues.get("nombre_completo")),MCfallbackFamily="";
        if(MCgiven.contains(",")){String[] MCparts=MCgiven.split(",",2);MCfallbackFamily=MCcleanName(MCparts[0]);MCgiven=MCcleanName(MCparts[1]);}
        if(MCfull.contains(",")){String[] MCparts=MCfull.split(",",2);if(MCfallbackFamily.isEmpty())MCfallbackFamily=MCcleanName(MCparts[0]);if(MCgiven.isEmpty())MCgiven=MCcleanName(MCparts[1]);MCfull=MCcleanName(MCparts[1]+" "+MCparts[0]);}
        if(MCfamily.isEmpty())MCfamily=MCfallbackFamily;
        if(MCfamily.isEmpty()&&!MCgiven.isEmpty()&&MCfull.length()>MCgiven.length()){
            if(MCfull.regionMatches(true,0,MCgiven+" ",0,MCgiven.length()+1))MCfamily=MCcleanName(MCfull.substring(MCgiven.length()));
            else if(MCfull.regionMatches(true,MCfull.length()-MCgiven.length()-1," "+MCgiven,0,MCgiven.length()+1))MCfamily=MCcleanName(MCfull.substring(0,MCfull.length()-MCgiven.length()));
        }
        if(MCgiven.isEmpty()&&!MCfull.isEmpty()&&!MCfamily.isEmpty())MCgiven=MCstripFamily(MCfull,MCfamily);
        MCgiven=MCstripFamily(MCgiven,MCfamily);
        if(!MCgiven.isEmpty()){
            MCvalues.put("nombres",MCgiven);if(!MCfamily.isEmpty()||MCfull.isEmpty())MCfull=MCcleanName(MCgiven+" "+MCfamily);
            if(MCvalues.containsKey("data.nombres"))MCvalues.put("data.nombres",MCgiven);
        }
        if(!MCfamily.isEmpty()&&!MCvalues.containsKey("apellidos")&&(!MCpaternal.isEmpty()&&!MCmaternal.isEmpty()||!MCfallbackFamily.isEmpty()||MCpaternal.isEmpty()&&MCmaternal.isEmpty()))MCvalues.put("apellidos",MCfamily);
        if(!MCfull.isEmpty()){MCfull=MCcleanName(MCfull.replace(',', ' '));MCvalues.put("nombre_completo",MCfull);if(MCvalues.containsKey("data.nombre_completo"))MCvalues.put("data.nombre_completo",MCfull);}
    }
    private static String MCstripFamily(String MCgiven,String MCfamily){
        if(MCfamily.isEmpty())return MCgiven;
        String MCprevious;do{MCprevious=MCgiven;if(MCgiven.length()>MCfamily.length()&&MCgiven.regionMatches(true,0,MCfamily+" ",0,MCfamily.length()+1))MCgiven=MCcleanName(MCgiven.substring(MCfamily.length()));if(MCgiven.length()>MCfamily.length()&&MCgiven.regionMatches(true,MCgiven.length()-MCfamily.length()-1," "+MCfamily,0,MCfamily.length()+1))MCgiven=MCcleanName(MCgiven.substring(0,MCgiven.length()-MCfamily.length()));}while(!MCgiven.equals(MCprevious));return MCgiven;
    }
    private static String MCcleanName(String MCvalue){return MCvalue==null?"":MCvalue.replaceAll("[\\s\\u00a0]+"," ").trim();}
    static Map<String,String> MCnormalizedResponse(String MCprovider,String MCservice,Map<String,String> MCresponse){
        Map<String,String> MCraw=new LinkedHashMap<>(MCresponse);if("DNI".equals(MCservice)){Map<String,String> MCvalues=MCnormalize(MCprovider,MCservice,MCraw);String MCprefix="Factiliza".equals(MCprovider)?"data.":"";for(String MCkey:List.of("nombres","nombre_completo","apellidos"))if(MCvalues.containsKey(MCkey))MCraw.put(MCprefix+MCkey,MCvalues.get(MCkey));}return MCraw;
    }
    public static String MClegacyValue(String MCprovider,String MCstored,Map<String,String> MCraw){return "Factiliza".equals(MCprovider)?MCraw.get(MCstored):null;}
}
