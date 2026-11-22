package com.ejemplo.crudpersona.studio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public final class MCVariableInput {
    public static final List<String> MCTYPES=List.of("Texto","Número","Mixto","Fecha","Número entero","Número decimal");
    private MCVariableInput(){}
    public static String MCvisualType(String MCstored){return switch(MCstored){case "Texto corto","Texto largo","Domicilio"->"Texto";case "DNI","RUC"->"Mixto";case "Monto"->"Número decimal";default->MCstored;};}
    public static String MCinitialValue(String MCtype,String MCconfigured){return "Fecha".equals(MCtype)?LocalDate.now().toString():MCconfigured==null?"":MCconfigured;}
    public static String MCvalidate(String MCtype,String MCvalue,Locale MClocale){
        String MCv=MCvalue==null?"":MCvalue.trim();if(MCv.isEmpty())return "";
        switch(MCtype){
            case "Fecha"->{try{return LocalDate.parse(MCv).toString();}catch(Exception MCignored){throw new IllegalArgumentException("Fecha inválida. Use una fecha del calendario.");}}
            case "Número entero"->{if(!MCv.matches("[+-]?\\d+"))throw new IllegalArgumentException("Se requiere un número entero.");return MCv;}
            case "Número decimal","Monto"->{String MCnormalized=MCnormalizeDecimal(MCv,MClocale);try{new BigDecimal(MCnormalized);}catch(Exception MCex){throw new IllegalArgumentException("Número decimal inválido.");}return MCnormalized;}
            case "Número"->{if(!MCv.matches("[+-]?[\\d.,\\s]+"))throw new IllegalArgumentException("Se requiere una entrada numérica.");return MCv;}
            case "DNI"->{if(!MCv.matches("\\d{8}"))throw new IllegalArgumentException("DNI debe tener ocho dígitos.");return MCv;}
            case "RUC"->{if(!MCv.matches("\\d{11}"))throw new IllegalArgumentException("RUC debe tener once dígitos.");return MCv;}
            default->{return MCvalue;}
        }
    }
    private static String MCnormalizeDecimal(String MCvalue,Locale MClocale){
        String MCv=MCvalue.replace(" ","");int MCcomma=MCv.lastIndexOf(','),MCdot=MCv.lastIndexOf('.');
        if(MCcomma>=0&&MCdot>=0)return MCcomma>MCdot?MCv.replace(".","").replace(',','.'):MCv.replace(",","");
        if(MCcomma>=0)return MCv.replace(',','.');
        return MCv;
    }
}
