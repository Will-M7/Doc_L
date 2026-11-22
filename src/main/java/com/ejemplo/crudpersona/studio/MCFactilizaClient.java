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

public final class MCFactilizaClient {
    public static final class MCHttpStatusException extends IOException {
        private final int MCstatusCode;private final long MCretryAfterSeconds;
        public MCHttpStatusException(int MCstatusCode,long MCretryAfterSeconds,String MCmessage){super(MCmessage);this.MCstatusCode=MCstatusCode;this.MCretryAfterSeconds=MCretryAfterSeconds;}
        public int MCstatusCode(){return MCstatusCode;}
        public long MCretryAfterSeconds(){return MCretryAfterSeconds;}
    }
    public record MCResult(@com.fasterxml.jackson.annotation.JsonProperty("type") String MCtype,@com.fasterxml.jackson.annotation.JsonProperty("number") String MCnumber,@com.fasterxml.jackson.annotation.JsonProperty("data") Map<String,String> MCdata,@com.fasterxml.jackson.annotation.JsonProperty("message") String MCmessage){}
    private static final URI MCBASE=URI.create("https://api.factiliza.com/v1/");
    private final HttpClient MChttp=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final ObjectMapper MCJSON=new ObjectMapper();
    public MCResult MCquery(String MCtype,String MCnumber,String MCtoken)throws Exception{
        String MCsegment=switch(MCtype){case "DNI"->"dni/info";case "RUC"->"ruc/info";case "RUC - Representantes"->"ruc/representante";case "RUC - Establecimientos"->"ruc/anexo";case "CEE"->"cee/info";default->throw new IllegalArgumentException("Tipo de consulta no admitido.");};
        int MCexpected=MCtype.equals("DNI")?8:MCtype.equals("CEE")?9:11;
        if(MCnumber==null||!MCnumber.matches("\\d{"+MCexpected+"}"))throw new IllegalArgumentException(MCtype+" debe tener "+MCexpected+" dígitos.");
        if(MCtoken==null||MCtoken.isBlank())throw new IllegalArgumentException("Configure el token de Factiliza.");
        HttpRequest MCrequest=HttpRequest.newBuilder(MCBASE.resolve(MCsegment+"/"+MCnumber)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+MCtoken).GET().build();
        HttpResponse<String> MCresponse=MChttp.send(MCrequest,HttpResponse.BodyHandlers.ofString());
        try{return MCparse(MCtype,MCnumber,MCresponse.statusCode(),MCresponse.body());}catch(MCHttpStatusException MCe){throw new MCHttpStatusException(MCe.MCstatusCode(),MCretryAfter(MCresponse.headers()),MCe.getMessage());}
    }
    public static MCResult MCparse(String MCtype,String MCnumber,int MCcode,String MCbody)throws IOException{
        JsonNode MCroot;
        try{MCroot=MCJSON.readTree(MCbody);}catch(Exception MCe){throw new IOException("Factiliza devolvió una respuesta que no es JSON.",MCe);}
        if(MCroot==null||!MCroot.isObject())throw new IOException("Respuesta incompleta de Factiliza.");
        String MCmessage=MCroot.path("message").asText("");
        if(MCcode!=200||MCroot.has("success")&&!MCroot.path("success").asBoolean(false))throw new MCHttpStatusException(MCcode!=200?MCcode:MCroot.path("status").asInt(400),0,"Factiliza: "+(MCmessage.isBlank()?"HTTP "+MCcode:MCmessage));
        JsonNode MCdata=MCroot.path("data");
        if(!MCdata.isObject()&&!MCdata.isArray()||List.of("DNI","RUC").contains(MCtype)&&MCdata.path("numero").asText("").isBlank())throw new IOException("Factiliza devolvió datos incompletos.");
        Map<String,String> MCfields=new LinkedHashMap<>();
        MCflatten("data",MCdata,MCfields);
        if(MCdata.isObject())MCdata.fields().forEachRemaining(MCe->{if(MCe.getValue().isValueNode()&&!MCe.getValue().isNull())MCfields.put(MCe.getKey(),MCe.getValue().asText());});
        return new MCResult(MCtype,MCnumber,MCfields,MCmessage);
    }
    private static void MCflatten(String MCpath,JsonNode MCnode,Map<String,String> MCfields){
        if(MCnode.isObject())MCnode.fields().forEachRemaining(MCe->MCflatten(MCpath+"."+MCe.getKey(),MCe.getValue(),MCfields));
        else if(MCnode.isArray())for(int MCi=0;MCi<MCnode.size();MCi++)MCflatten(MCpath+"["+MCi+"]",MCnode.get(MCi),MCfields);
        else if(!MCnode.isNull())MCfields.put(MCpath,MCnode.asText());
    }
    public static long MCretryAfter(HttpHeaders MCheaders){String MCvalue=MCheaders.firstValue("Retry-After").orElse("").trim();try{return Math.max(0,Long.parseLong(MCvalue));}catch(Exception MCignored){}try{return Math.max(0,Duration.between(Instant.now(),ZonedDateTime.parse(MCvalue,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds());}catch(Exception MCignored){return 0;}}
}
