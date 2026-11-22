package com.ejemplo.crudpersona.studio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** @author MC */
public final class MCVariableInput {
    public static final List<String> mcTYPES=List.of("Texto","Número","Mixto","Fecha","Número entero","Número decimal");
    private MCVariableInput(){}
    public static String mcVisualType(String mcStored){return switch(mcStored){case "Texto corto","Texto largo","Domicilio"->"Texto";case "DNI","RUC"->"Mixto";case "Monto"->"Número decimal";default->mcStored;};}
    public static String mcInitialValue(String mcType,String mcConfigured){return "Fecha".equals(mcType)?LocalDate.now().toString():mcConfigured==null?"":mcConfigured;}
    public static String mcValidate(String mcType,String mcValue,Locale mcLocale){
        String mcV=mcValue==null?"":mcValue.trim();if(mcV.isEmpty())return "";
        switch(mcType){
            case "Fecha"->{try{return LocalDate.parse(mcV).toString();}catch(Exception mcIgnored){throw new IllegalArgumentException("Fecha inválida. Use una fecha del calendario.");}}
            case "Número entero"->{if(!mcV.matches("[+-]?\\d+"))throw new IllegalArgumentException("Se requiere un número entero.");return mcV;}
            case "Número decimal","Monto"->{String mcNormalized=mcNormalizeDecimal(mcV,mcLocale);try{new BigDecimal(mcNormalized);}catch(Exception mcEx){throw new IllegalArgumentException("Número decimal inválido.");}return mcNormalized;}
            case "Número"->{if(!mcV.matches("[+-]?[\\d.,\\s]+"))throw new IllegalArgumentException("Se requiere una entrada numérica.");return mcV;}
            case "DNI"->{if(!mcV.matches("\\d{8}"))throw new IllegalArgumentException("DNI debe tener ocho dígitos.");return mcV;}
            case "RUC"->{if(!mcV.matches("\\d{11}"))throw new IllegalArgumentException("RUC debe tener once dígitos.");return mcV;}
            default->{return mcValue;}
        }
    }
    private static String mcNormalizeDecimal(String mcValue,Locale mcLocale){
        String mcV=mcValue.replace(" ","");int mcComma=mcV.lastIndexOf(','),mcDot=mcV.lastIndexOf('.');
        if(mcComma>=0&&mcDot>=0)return mcComma>mcDot?mcV.replace(".","").replace(',','.'):mcV.replace(",","");
        if(mcComma>=0)return mcV.replace(',','.');
        return mcV;
    }
}
