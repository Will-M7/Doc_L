package com.ejemplo.crudpersona.studio;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.*;

/** @author MC */
public final class MCLookupRouter {
    /** @author MC */
    public enum MCStatus {SUCCESS,PARTIAL,NO_CREDENTIAL,NO_INTERNET,TIMEOUT,TEMPORARY,RATE_LIMIT,INVALID,PERMISSION,UNSUPPORTED,NOT_FOUND,BAD_RESPONSE}
    /** @author MC */
    public record MCResult(@com.fasterxml.jackson.annotation.JsonProperty("service") String mcService,@com.fasterxml.jackson.annotation.JsonProperty("identifier") String mcIdentifier,@com.fasterxml.jackson.annotation.JsonProperty("provider") String mcProvider,@com.fasterxml.jackson.annotation.JsonProperty("values") Map<String,String> mcValues,
                         @com.fasterxml.jackson.annotation.JsonProperty("available") Set<String> mcAvailable,@com.fasterxml.jackson.annotation.JsonProperty("unavailable") Set<String> mcUnavailable,@com.fasterxml.jackson.annotation.JsonProperty("status") MCStatus mcStatus,@com.fasterxml.jackson.annotation.JsonProperty("error") String mcError,@com.fasterxml.jackson.annotation.JsonProperty("raw") Map<String,String> mcRaw){
        public boolean mcUsable(){return mcStatus==MCStatus.SUCCESS||mcStatus==MCStatus.PARTIAL;}
    }
    private final MCApiCredentialStore mcCredentials;
    private final Map<String,MCLookupProvider> mcProviders=new LinkedHashMap<>();
    public MCLookupRouter(MCApiCredentialStore mcCredentials,Collection<MCLookupProvider> mcProviders){this.mcCredentials=mcCredentials;for(MCLookupProvider mcProvider:mcProviders)this.mcProviders.put(mcProvider.mcName(),mcProvider);}
    public MCResult mcQuery(String mcService,String mcIdentifier)throws Exception{return mcQuery(mcService,mcIdentifier,null);}
    public MCResult mcQuerySpecific(String mcService,String mcIdentifier,long mcCredentialId)throws Exception{return mcQuery(mcService,mcIdentifier,mcCredentialId);}
    private MCResult mcQuery(String mcService,String mcIdentifier,Long mcRequestedId)throws Exception{
        int mcLength="DNI".equals(mcService)?8:"RUC".equals(mcService)?11:0;
        if(mcLength==0||mcIdentifier==null||!mcIdentifier.matches("\\d{"+mcLength+"}"))throw new IllegalArgumentException("Número de documento inválido.");
        List<MCApiCredentialStore.MCCredential> mcOptions=new ArrayList<>(mcCredentials.mcCredentials());mcOptions.removeIf(mcC->mcRequestedId!=null?mcC.mcId()!=mcRequestedId:!mcC.mcEnabled()||mcC.mcStatus().contains("inválida")||mcC.mcStatus().contains("Invalidez confirmada")||mcC.mcRetryAfter()>Instant.now().getEpochSecond());
        mcOptions.removeIf(mcC->!mcProviders.containsKey(mcC.mcProvider())||!mcProviders.get(mcC.mcProvider()).mcServices().contains(mcService));
        mcOptions.sort(Comparator.comparingInt((MCApiCredentialStore.MCCredential mcC)->mcC.mcProvider().equals("Factiliza")?0:1).thenComparingInt(MCApiCredentialStore.MCCredential::mcRequests).thenComparingLong(MCApiCredentialStore.MCCredential::mcId));
        if(mcOptions.isEmpty())return mcEmpty(mcService,mcIdentifier,MCStatus.NO_CREDENTIAL,"No hay credenciales habilitadas; complete los campos manualmente.");
        MCStatus mcLast=MCStatus.NO_CREDENTIAL;String mcReason="No se obtuvo información.";Set<String> mcSkippedProviders=new HashSet<>();int mcAttempts=0;
        for(MCApiCredentialStore.MCCredential mcCredential:mcOptions){
            if(mcAttempts>=4||mcSkippedProviders.contains(mcCredential.mcProvider()))continue;
            MCLookupProvider mcProvider=mcProviders.get(mcCredential.mcProvider());String mcToken;
            try{mcToken=mcCredentials.mcToken(mcCredential.mcId());}catch(Exception mcE){mcLast=MCStatus.BAD_RESPONSE;mcReason="No se pudo leer la credencial protegida.";continue;}
            if(mcToken==null||mcToken.isBlank()){mcLast=MCStatus.BAD_RESPONSE;mcReason="La credencial protegida está vacía.";continue;}
            mcAttempts++;
            try{
                Map<String,String> mcRaw=mcProvider.mcQuery(mcService,mcIdentifier,mcToken);
                Map<String,String> mcValues=MCApiFieldCatalog.mcNormalize(mcProvider.mcName(),mcService,mcRaw);
                Set<String> mcExpected=new LinkedHashSet<>();for(MCApiFieldCatalog.MCField mcField:MCApiFieldCatalog.mcFields(mcService))mcExpected.add(mcField.mcKey());
                Set<String> mcUnavailable=new LinkedHashSet<>(mcExpected);mcUnavailable.removeAll(mcValues.keySet());
                Set<String> mcMissingSupported=new LinkedHashSet<>(MCApiFieldCatalog.mcSupportedKeys(mcProvider.mcName(),mcService));mcMissingSupported.removeAll(mcValues.keySet());
                MCStatus mcStatus=mcValues.isEmpty()?MCStatus.BAD_RESPONSE:mcMissingSupported.isEmpty()?MCStatus.SUCCESS:MCStatus.PARTIAL;
                mcCredentials.mcRecord(mcCredential.mcId(),mcStatus,0);
                if(mcStatus==MCStatus.BAD_RESPONSE){mcLast=mcStatus;mcReason="El proveedor devolvió una respuesta sin datos utilizables.";mcSkippedProviders.add(mcProvider.mcName());continue;}
                return new MCResult(mcService,mcIdentifier,mcProvider.mcName(),Map.copyOf(mcValues),Set.copyOf(mcValues.keySet()),Set.copyOf(mcUnavailable),mcStatus,mcStatus==MCStatus.PARTIAL?"Algunos campos no están disponibles.":"",Map.copyOf(mcRaw));
            }catch(Exception mcFailure){
                MCStatus mcStatus=mcClassify(mcFailure);long mcRetry=mcFailure instanceof MCFactilizaClient.MCHttpStatusException mcHttp?mcHttp.mcRetryAfterSeconds():0;
                mcCredentials.mcRecord(mcCredential.mcId(),mcStatus,mcRetry);mcLast=mcStatus;mcReason=mcMessage(mcStatus);
                if(mcStatus==MCStatus.NO_INTERNET||mcStatus==MCStatus.NOT_FOUND)break;
                if(mcStatus==MCStatus.RATE_LIMIT||mcStatus==MCStatus.TIMEOUT||mcStatus==MCStatus.TEMPORARY||mcStatus==MCStatus.PERMISSION||mcStatus==MCStatus.UNSUPPORTED||mcStatus==MCStatus.BAD_RESPONSE)mcSkippedProviders.add(mcProvider.mcName());
            }
        }
        return mcEmpty(mcService,mcIdentifier,mcLast,mcReason+" Puede continuar manualmente.");
    }
    private static MCResult mcEmpty(String mcService,String mcIdentifier,MCStatus mcStatus,String mcReason){Set<String> mcMissing=new LinkedHashSet<>();for(MCApiFieldCatalog.MCField mcField:MCApiFieldCatalog.mcFields(mcService))mcMissing.add(mcField.mcKey());return new MCResult(mcService,mcIdentifier,"",Map.of(),Set.of(),Set.copyOf(mcMissing),mcStatus,mcReason,Map.of());}
    private static MCStatus mcClassify(Throwable mcFailure){
        if(mcFailure instanceof MCFactilizaClient.MCHttpStatusException mcHttp)return switch(mcHttp.mcStatusCode()){case 401->MCStatus.INVALID;case 403->MCStatus.PERMISSION;case 404->MCStatus.NOT_FOUND;case 429->MCStatus.RATE_LIMIT;case 408,504->MCStatus.TIMEOUT;case 501->MCStatus.UNSUPPORTED;default->mcHttp.mcStatusCode()>=500?MCStatus.TEMPORARY:MCStatus.BAD_RESPONSE;};
        if(mcFailure instanceof HttpTimeoutException)return MCStatus.TIMEOUT;
        if(mcFailure instanceof UnknownHostException||mcFailure instanceof ConnectException)return MCStatus.NO_INTERNET;
        if(mcFailure instanceof IOException mcIo&&mcIo.getCause()!=null)return mcClassify(mcIo.getCause());
        return MCStatus.BAD_RESPONSE;
    }
    private static String mcMessage(MCStatus mcStatus){return switch(mcStatus){case NO_INTERNET->"Sin conexión a Internet.";case TIMEOUT->"La consulta agotó el tiempo de espera.";case TEMPORARY->"El proveedor no está disponible temporalmente.";case RATE_LIMIT->"Se alcanzó un límite de consultas.";case INVALID->"La credencial fue rechazada.";case PERMISSION->"La credencial no tiene permisos suficientes.";case UNSUPPORTED->"El servicio no está habilitado.";case NOT_FOUND->"Documento no encontrado.";case PARTIAL->"Respuesta parcial.";case BAD_RESPONSE->"Respuesta incorrecta o incompleta.";case NO_CREDENTIAL->"Sin credenciales.";case SUCCESS->"Consulta correcta.";};}
}
