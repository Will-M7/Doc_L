package com.ejemplo.crudpersona.studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** @author MC */
public final class MCFactilizaClient {
    /** @author MC */
    public static final class MCHttpStatusException extends IOException {
        private final int mcStatusCode;private final long mcRetryAfterSeconds;
        public MCHttpStatusException(int mcStatusCode,long mcRetryAfterSeconds,String mcMessage){super(mcMessage);this.mcStatusCode=mcStatusCode;this.mcRetryAfterSeconds=mcRetryAfterSeconds;}
        public int mcStatusCode(){return mcStatusCode;}
        public long mcRetryAfterSeconds(){return mcRetryAfterSeconds;}
    }
    /** @author MC */
    public record MCResult(@com.fasterxml.jackson.annotation.JsonProperty("type") String mcType,@com.fasterxml.jackson.annotation.JsonProperty("number") String mcNumber,@com.fasterxml.jackson.annotation.JsonProperty("data") Map<String,String> mcData,@com.fasterxml.jackson.annotation.JsonProperty("message") String mcMessage){}
    private static final URI mcBASE=URI.create("https://api.factiliza.com/v1/");
    private final HttpClient mcHttp=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final ObjectMapper mcJSON=new ObjectMapper();
    public MCResult mcQuery(String mcType,String mcNumber,String mcToken)throws Exception{
        String mcSegment=switch(mcType){case "DNI"->"dni/info";case "RUC"->"ruc/info";case "RUC - Representantes"->"ruc/representante";case "RUC - Establecimientos"->"ruc/anexo";case "CEE"->"cee/info";default->throw new IllegalArgumentException("Tipo de consulta no admitido.");};
        int mcExpected=mcType.equals("DNI")?8:mcType.equals("CEE")?9:11;
        if(mcNumber==null||!mcNumber.matches("\\d{"+mcExpected+"}"))throw new IllegalArgumentException(mcType+" debe tener "+mcExpected+" dígitos.");
        if(mcToken==null||mcToken.isBlank())throw new IllegalArgumentException("Configure el token de Factiliza.");
        HttpRequest mcRequest=HttpRequest.newBuilder(mcBASE.resolve(mcSegment+"/"+mcNumber)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+mcToken).GET().build();
        HttpResponse<String> mcResponse=mcHttp.send(mcRequest,HttpResponse.BodyHandlers.ofString());
        try{return mcParse(mcType,mcNumber,mcResponse.statusCode(),mcResponse.body());}catch(MCHttpStatusException mcE){throw new MCHttpStatusException(mcE.mcStatusCode(),mcRetryAfter(mcResponse.headers()),mcE.getMessage());}
    }
    public static MCResult mcParse(String mcType,String mcNumber,int mcCode,String mcBody)throws IOException{
        JsonNode mcRoot;
        try{mcRoot=mcJSON.readTree(mcBody);}catch(Exception mcE){throw new IOException("Factiliza devolvió una respuesta que no es JSON.",mcE);}
        if(mcRoot==null||!mcRoot.isObject())throw new IOException("Respuesta incompleta de Factiliza.");
        String mcMessage=mcRoot.path("message").asText("");
        if(mcCode!=200||mcRoot.has("success")&&!mcRoot.path("success").asBoolean(false))throw new MCHttpStatusException(mcCode!=200?mcCode:mcRoot.path("status").asInt(400),0,"Factiliza: "+(mcMessage.isBlank()?"HTTP "+mcCode:mcMessage));
        JsonNode mcData=mcRoot.path("data");
        if(!mcData.isObject()&&!mcData.isArray()||List.of("DNI","RUC").contains(mcType)&&mcData.path("numero").asText("").isBlank())throw new IOException("Factiliza devolvió datos incompletos.");
        Map<String,String> mcFields=new LinkedHashMap<>();
        mcFlatten("data",mcData,mcFields);
        if(mcData.isObject())mcData.fields().forEachRemaining(mcE->{if(mcE.getValue().isValueNode()&&!mcE.getValue().isNull())mcFields.put(mcE.getKey(),mcE.getValue().asText());});
        return new MCResult(mcType,mcNumber,mcFields,mcMessage);
    }
    private static void mcFlatten(String mcPath,JsonNode mcNode,Map<String,String> mcFields){
        if(mcNode.isObject())mcNode.fields().forEachRemaining(mcE->mcFlatten(mcPath+"."+mcE.getKey(),mcE.getValue(),mcFields));
        else if(mcNode.isArray())for(int mcI=0;mcI<mcNode.size();mcI++)mcFlatten(mcPath+"["+mcI+"]",mcNode.get(mcI),mcFields);
        else if(!mcNode.isNull())mcFields.put(mcPath,mcNode.asText());
    }
    public static long mcRetryAfter(HttpHeaders mcHeaders){String mcValue=mcHeaders.firstValue("Retry-After").orElse("").trim();try{return Math.max(0,Long.parseLong(mcValue));}catch(Exception mcIgnored){}try{return Math.max(0,Duration.between(Instant.now(),ZonedDateTime.parse(mcValue,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds());}catch(Exception mcIgnored){return 0;}}
}
