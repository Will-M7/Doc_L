package com.ejemplo.crudpersona.studio;

import java.util.*;

/** @author MC */
public final class MCFactilizaFields {
    private MCFactilizaFields(){}
    public static List<String> mcPaths(String mcService){
        List<String> mcCommon=List.of("numero","departamento","provincia","distrito","direccion","direccion_completa","ubigeo_sunat","ubigeo[0]","ubigeo[1]","ubigeo[2]");
        List<String> mcFields=new ArrayList<>();
        switch(mcService){
            case "DNI"->{mcFields.addAll(List.of("numero","nombres","apellido_paterno","apellido_materno","nombre_completo","departamento","provincia","distrito","direccion","direccion_completa","ubigeo_reniec","ubigeo_sunat","ubigeo[0]","ubigeo[1]","ubigeo[2]","fecha_nacimiento","sexo"));}
            case "RUC"->{mcFields.addAll(List.of("numero","nombre_o_razon_social","tipo_contribuyente","estado","condicion"));mcFields.addAll(mcCommon.subList(1,mcCommon.size()));}
            case "RUC - Representantes"->mcFields.addAll(List.of("tipo_de_documento","numero_de_documento","nombre","cargo","fecha_desde"));
            case "RUC - Establecimientos"->mcFields.addAll(List.of("codigo","tipo_establecimiento","actividad_economica","direccion","direccion_completa","departamento","provincia","distrito","ubigeo_sunat","ubigeo[0]","ubigeo[1]","ubigeo[2]"));
            case "CEE"->mcFields.addAll(List.of("numero","nombres","apellido_paterno","apellido_materno"));
            default->{}
        }
        String mcPrefix=mcService.startsWith("RUC - ")?"data[0].":"data.";
        return mcFields.stream().map(mcF->mcPrefix+mcF).toList();
    }
}
