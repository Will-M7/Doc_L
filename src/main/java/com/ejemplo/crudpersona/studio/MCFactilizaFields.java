package com.ejemplo.crudpersona.studio;

import java.util.*;

public final class MCFactilizaFields {
    private MCFactilizaFields(){}
    public static List<String> MCpaths(String MCservice){
        List<String> MCcommon=List.of("numero","departamento","provincia","distrito","direccion","direccion_completa","ubigeo_sunat","ubigeo[0]","ubigeo[1]","ubigeo[2]");
        List<String> MCfields=new ArrayList<>();
        switch(MCservice){
            case "DNI"->{MCfields.addAll(List.of("numero","nombres","apellido_paterno","apellido_materno","nombre_completo","departamento","provincia","distrito","direccion","direccion_completa","ubigeo_reniec","ubigeo_sunat","ubigeo[0]","ubigeo[1]","ubigeo[2]","fecha_nacimiento","sexo"));}
            case "RUC"->{MCfields.addAll(List.of("numero","nombre_o_razon_social","tipo_contribuyente","estado","condicion"));MCfields.addAll(MCcommon.subList(1,MCcommon.size()));}
            case "RUC - Representantes"->MCfields.addAll(List.of("tipo_de_documento","numero_de_documento","nombre","cargo","fecha_desde"));
            case "RUC - Establecimientos"->MCfields.addAll(List.of("codigo","tipo_establecimiento","actividad_economica","direccion","direccion_completa","departamento","provincia","distrito","ubigeo_sunat","ubigeo[0]","ubigeo[1]","ubigeo[2]"));
            case "CEE"->MCfields.addAll(List.of("numero","nombres","apellido_paterno","apellido_materno"));
            default->{}
        }
        String MCprefix=MCservice.startsWith("RUC - ")?"data[0].":"data.";
        return MCfields.stream().map(MCf->MCprefix+MCf).toList();
    }
}
