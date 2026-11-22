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

/** @author MC */
public final class MCApisPeruProvider implements MCLookupProvider {
    private static final String mcBASE="https://dniruc.apisperu.com/api/v1/";
    private static final ObjectMapper mcJSON=new ObjectMapper();
    private final HttpClient mcHttp=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    @Override public String mcName(){return "APIsPERU";}
    @Override public Set<String> mcServices(){return Set.of("DNI","RUC");}
    @Override public Map<String,String> mcQuery(String mcService,String mcIdentifier,String mcCredential)throws Exception{
        if(!mcServices().contains(mcService))throw new IllegalArgumentException("Servicio no admitido.");
        if(mcCredential==null||mcCredential.isBlank())throw new IllegalArgumentException("Falta una credencial.");
        int mcLength="DNI".equals(mcService)?8:11;if(mcIdentifier==null||!mcIdentifier.matches("\\d{"+mcLength+"}"))throw new IllegalArgumentException("Número de documento inválido.");
        String mcSegment=mcService.toLowerCase(Locale.ROOT);
        URI mcUri=URI.create(mcBASE+mcSegment+"/"+mcIdentifier+"?token="+URLEncoder.encode(mcCredential,StandardCharsets.UTF_8));
        HttpRequest mcRequest=HttpRequest.newBuilder(mcUri).timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> mcResponse=mcHttp.send(mcRequest,HttpResponse.BodyHandlers.ofString());
        long mcRetry=MCFactilizaClient.mcRetryAfter(mcResponse.headers());
        return mcParse(mcService,mcResponse.statusCode(),mcResponse.body(),mcRetry);
    }
    public static Map<String,String> mcParse(String mcService,int mcStatus,String mcBody,long mcRetryAfter)throws IOException{
        JsonNode mcRoot;try{mcRoot=mcJSON.readTree(mcBody);}catch(Exception mcE){throw new IOException("APIsPERU devolvió JSON incorrecto.",mcE);}
        if(mcRoot==null||!mcRoot.isObject())throw new IOException("APIsPERU devolvió una respuesta incorrecta.");
        if(mcStatus!=200||mcRoot.has("success")&&!mcRoot.path("success").asBoolean(true))throw new MCFactilizaClient.MCHttpStatusException(mcStatus==200?mcRoot.path("status").asInt(400):mcStatus,mcRetryAfter,"APIsPERU: "+mcRoot.path("message").asText("La consulta no se completó."));
        Map<String,String> mcRaw=new LinkedHashMap<>();mcFlatten("",mcRoot,mcRaw);
        if("DNI".equals(mcService)&&!mcRaw.containsKey("dni")||"RUC".equals(mcService)&&!mcRaw.containsKey("ruc"))throw new IOException("APIsPERU devolvió datos incompletos.");
        return MCApiFieldCatalog.mcNormalizedResponse("APIsPERU",mcService,mcRaw);
    }
    private static void mcFlatten(String mcPrefix,JsonNode mcNode,Map<String,String> mcOut){
        if(mcNode.isObject())mcNode.fields().forEachRemaining(mcE->mcFlatten(mcPrefix.isBlank()?mcE.getKey():mcPrefix+"."+mcE.getKey(),mcE.getValue(),mcOut));
        else if(mcNode.isArray())for(int mcI=0;mcI<mcNode.size();mcI++)mcFlatten(mcPrefix+"["+mcI+"]",mcNode.get(mcI),mcOut);
        else if(!mcNode.isNull())mcOut.put(mcPrefix,mcNode.asText());
    }
}
