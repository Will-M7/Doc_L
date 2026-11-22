package com.ejemplo.crudpersona.studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public final class MCApisPeruProvider implements MCLookupProvider {
    private static final String MCBASE="https://dniruc.apisperu.com/api/v1/";
    private static final ObjectMapper MCJSON=new ObjectMapper();
    private final HttpClient MChttp=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    @Override public String MCname(){return "APIsPERU";}
    @Override public Set<String> MCservices(){return Set.of("DNI","RUC");}
    @Override public Map<String,String> MCquery(String MCservice,String MCidentifier,String MCcredential)throws Exception{
        if(!MCservices().contains(MCservice))throw new IllegalArgumentException("Servicio no admitido.");
        if(MCcredential==null||MCcredential.isBlank())throw new IllegalArgumentException("Falta una credencial.");
        int MClength="DNI".equals(MCservice)?8:11;if(MCidentifier==null||!MCidentifier.matches("\\d{"+MClength+"}"))throw new IllegalArgumentException("Número de documento inválido.");
        String MCsegment=MCservice.toLowerCase(Locale.ROOT);
        URI MCuri=URI.create(MCBASE+MCsegment+"/"+MCidentifier+"?token="+URLEncoder.encode(MCcredential,StandardCharsets.UTF_8));
        HttpRequest MCrequest=HttpRequest.newBuilder(MCuri).timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> MCresponse=MChttp.send(MCrequest,HttpResponse.BodyHandlers.ofString());
        long MCretry=MCFactilizaClient.MCretryAfter(MCresponse.headers());
        return MCparse(MCservice,MCresponse.statusCode(),MCresponse.body(),MCretry);
    }
    public static Map<String,String> MCparse(String MCservice,int MCstatus,String MCbody,long MCretryAfter)throws IOException{
        JsonNode MCroot;try{MCroot=MCJSON.readTree(MCbody);}catch(Exception MCe){throw new IOException("APIsPERU devolvió JSON incorrecto.",MCe);}
        if(MCroot==null||!MCroot.isObject())throw new IOException("APIsPERU devolvió una respuesta incorrecta.");
        if(MCstatus!=200||MCroot.has("success")&&!MCroot.path("success").asBoolean(true))throw new MCFactilizaClient.MCHttpStatusException(MCstatus==200?MCroot.path("status").asInt(400):MCstatus,MCretryAfter,"APIsPERU: "+MCroot.path("message").asText("La consulta no se completó."));
        Map<String,String> MCraw=new LinkedHashMap<>();MCflatten("",MCroot,MCraw);
        if("DNI".equals(MCservice)&&!MCraw.containsKey("dni")||"RUC".equals(MCservice)&&!MCraw.containsKey("ruc"))throw new IOException("APIsPERU devolvió datos incompletos.");
        return MCApiFieldCatalog.MCnormalizedResponse("APIsPERU",MCservice,MCraw);
    }
    private static void MCflatten(String MCprefix,JsonNode MCnode,Map<String,String> MCout){
        if(MCnode.isObject())MCnode.fields().forEachRemaining(MCe->MCflatten(MCprefix.isBlank()?MCe.getKey():MCprefix+"."+MCe.getKey(),MCe.getValue(),MCout));
        else if(MCnode.isArray())for(int MCi=0;MCi<MCnode.size();MCi++)MCflatten(MCprefix+"["+MCi+"]",MCnode.get(MCi),MCout);
        else if(!MCnode.isNull())MCout.put(MCprefix,MCnode.asText());
    }
}
