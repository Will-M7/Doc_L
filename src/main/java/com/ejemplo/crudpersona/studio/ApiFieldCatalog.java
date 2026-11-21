package com.ejemplo.crudpersona.studio;

import java.util.*;

/** Provider-neutral fields shown to the user; paths stay behind the adapters. */
public final class ApiFieldCatalog {
    public record Field(String service,String key,String label,String factilizaPath,String apisPeruPath){
        @Override public String toString(){return label;}
    }
    private static final List<Field> FIELDS=List.of(
        new Field("DNI","numero","Número de DNI","data.numero","dni"),
        new Field("DNI","nombres","Nombres","data.nombres","nombres"),
        new Field("DNI","apellido_paterno","Apellido paterno","data.apellido_paterno","apellidoPaterno"),
        new Field("DNI","apellido_materno","Apellido materno","data.apellido_materno","apellidoMaterno"),
        new Field("DNI","nombre_completo","Nombre completo","data.nombre_completo","nombre_completo"),
        new Field("DNI","direccion","Dirección","data.direccion",null),
        new Field("DNI","direccion_completa","Dirección completa","data.direccion_completa",null),
        new Field("DNI","departamento","Departamento","data.departamento",null),
        new Field("DNI","provincia","Provincia","data.provincia",null),
        new Field("DNI","distrito","Distrito","data.distrito",null),
        new Field("DNI","ubigeo","Ubigeo","data.ubigeo[2]",null),
        new Field("DNI","ubigeo_reniec","Ubigeo RENIEC","data.ubigeo_reniec",null),
        new Field("DNI","ubigeo_sunat","Ubigeo SUNAT","data.ubigeo_sunat",null),
        new Field("DNI","fecha_nacimiento","Fecha de nacimiento","data.fecha_nacimiento",null),
        new Field("DNI","sexo","Sexo","data.sexo",null),
        new Field("DNI","codigo_verificacion","Código de verificación",null,"codVerifica"),
        new Field("RUC","numero","Número de RUC","data.numero","ruc"),
        new Field("RUC","razon_social","Razón social","data.nombre_o_razon_social","razonSocial"),
        new Field("RUC","nombre_comercial","Nombre comercial",null,"nombreComercial"),
        new Field("RUC","tipo_contribuyente","Tipo de contribuyente","data.tipo_contribuyente",null),
        new Field("RUC","estado","Estado del contribuyente","data.estado","estado"),
        new Field("RUC","condicion","Condición del contribuyente","data.condicion","condicion"),
        new Field("RUC","direccion","Dirección fiscal","data.direccion","direccion"),
        new Field("RUC","direccion_completa","Dirección completa","data.direccion_completa",null),
        new Field("RUC","departamento","Departamento","data.departamento","departamento"),
        new Field("RUC","provincia","Provincia","data.provincia","provincia"),
        new Field("RUC","distrito","Distrito","data.distrito","distrito"),
        new Field("RUC","ubigeo","Ubigeo","data.ubigeo[2]","ubigeo"),
        new Field("RUC","ubigeo_sunat","Ubigeo SUNAT","data.ubigeo_sunat",null),
        new Field("RUC","telefonos","Teléfonos",null,"telefonos[0]"),
        new Field("RUC","capital","Capital",null,"capital")
    );
    private ApiFieldCatalog(){}
    public static List<Field> fields(String service){return FIELDS.stream().filter(f->f.service().equals(service)).toList();}
    public static Field field(String service,String key){return fields(service).stream().filter(f->f.key().equals(key)).findFirst().orElse(null);}
    public static String canonical(String service,String stored){
        if(stored==null||stored.isBlank())return null;
        if(stored.startsWith("field:"))return stored.substring(6);
        for(Field f:fields(service))if(stored.equals(f.factilizaPath())||stored.equals(f.apisPeruPath())||stored.equals(f.factilizaPath()==null?null:f.factilizaPath().replace("data.","")))return f.key();
        return "legacy:"+stored;
    }
    public static String label(String service,String key){Field f=field(service,key);return f==null?"Campo anterior sin equivalencia":f.label();}
    public static Set<String> supportedKeys(String provider,String service){Set<String> out=new LinkedHashSet<>();for(Field f:fields(service))if(("Factiliza".equals(provider)?f.factilizaPath():f.apisPeruPath())!=null)out.add(f.key());return out;}
    public static Map<String,String> normalize(String provider,String service,Map<String,String> raw){Map<String,String> values=new LinkedHashMap<>();for(Field field:fields(service)){String path="Factiliza".equals(provider)?field.factilizaPath():field.apisPeruPath();if(path==null)continue;String value=raw.get(path);if("APIsPERU".equals(provider)&&"telefonos".equals(field.key())){List<String> numbers=new ArrayList<>();for(int i=0;raw.containsKey("telefonos["+i+"]");i++){String number=raw.get("telefonos["+i+"]");if(number!=null&&!number.isBlank())numbers.add(number);}value=String.join(", ",numbers);}if(value!=null&&!value.isBlank())values.put(field.key(),value);}if("DNI".equals(service)){String family=raw.get("Factiliza".equals(provider)?"data.apellidos":"apellidos");if(family!=null&&!family.isBlank())values.put("apellidos",family);normalizePersonName(values);}return values;}
    /** One normalization for provider results, form fields and locally reused contacts. */
    public static void normalizePersonName(Map<String,String> values){
        String given=cleanName(values.get("nombres")),paternal=cleanName(values.get("apellido_paterno")),maternal=cleanName(values.get("apellido_materno"));
        String family=cleanName(paternal+" "+maternal);if(family.isEmpty())family=cleanName(values.get("apellidos"));
        String full=cleanName(values.get("nombre_completo")),fallbackFamily="";
        if(given.contains(",")){String[] parts=given.split(",",2);fallbackFamily=cleanName(parts[0]);given=cleanName(parts[1]);}
        if(full.contains(",")){String[] parts=full.split(",",2);if(fallbackFamily.isEmpty())fallbackFamily=cleanName(parts[0]);if(given.isEmpty())given=cleanName(parts[1]);full=cleanName(parts[1]+" "+parts[0]);}
        if(family.isEmpty())family=fallbackFamily;
        if(family.isEmpty()&&!given.isEmpty()&&full.length()>given.length()){
            if(full.regionMatches(true,0,given+" ",0,given.length()+1))family=cleanName(full.substring(given.length()));
            else if(full.regionMatches(true,full.length()-given.length()-1," "+given,0,given.length()+1))family=cleanName(full.substring(0,full.length()-given.length()));
        }
        if(given.isEmpty()&&!full.isEmpty()&&!family.isEmpty())given=stripFamily(full,family);
        given=stripFamily(given,family);
        if(!given.isEmpty()){
            values.put("nombres",given);if(!family.isEmpty()||full.isEmpty())full=cleanName(given+" "+family);
            if(values.containsKey("data.nombres"))values.put("data.nombres",given);
        }
        if(!family.isEmpty()&&!values.containsKey("apellidos")&&(!paternal.isEmpty()&&!maternal.isEmpty()||!fallbackFamily.isEmpty()||paternal.isEmpty()&&maternal.isEmpty()))values.put("apellidos",family);
        if(!full.isEmpty()){full=cleanName(full.replace(',', ' '));values.put("nombre_completo",full);if(values.containsKey("data.nombre_completo"))values.put("data.nombre_completo",full);}
    }
    private static String stripFamily(String given,String family){
        if(family.isEmpty())return given;
        String previous;do{previous=given;if(given.length()>family.length()&&given.regionMatches(true,0,family+" ",0,family.length()+1))given=cleanName(given.substring(family.length()));if(given.length()>family.length()&&given.regionMatches(true,given.length()-family.length()-1," "+family,0,family.length()+1))given=cleanName(given.substring(0,given.length()-family.length()));}while(!given.equals(previous));return given;
    }
    private static String cleanName(String value){return value==null?"":value.replaceAll("[\\s\\u00a0]+"," ").trim();}
    static Map<String,String> normalizedResponse(String provider,String service,Map<String,String> response){
        Map<String,String> raw=new LinkedHashMap<>(response);if("DNI".equals(service)){Map<String,String> values=normalize(provider,service,raw);String prefix="Factiliza".equals(provider)?"data.":"";for(String key:List.of("nombres","nombre_completo","apellidos"))if(values.containsKey(key))raw.put(prefix+key,values.get(key));}return raw;
    }
    public static String legacyValue(String provider,String stored,Map<String,String> raw){return "Factiliza".equals(provider)?raw.get(stored):null;}
}
