package com.ejemplo.crudpersona.studio;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.*;

public final class MCLookupRouter {
    public enum MCStatus {SUCCESS,PARTIAL,NO_CREDENTIAL,NO_INTERNET,TIMEOUT,TEMPORARY,RATE_LIMIT,INVALID,PERMISSION,UNSUPPORTED,NOT_FOUND,BAD_RESPONSE}
    public record MCResult(@com.fasterxml.jackson.annotation.JsonProperty("service") String MCservice,@com.fasterxml.jackson.annotation.JsonProperty("identifier") String MCidentifier,@com.fasterxml.jackson.annotation.JsonProperty("provider") String MCprovider,@com.fasterxml.jackson.annotation.JsonProperty("values") Map<String,String> MCvalues,
                         @com.fasterxml.jackson.annotation.JsonProperty("available") Set<String> MCavailable,@com.fasterxml.jackson.annotation.JsonProperty("unavailable") Set<String> MCunavailable,@com.fasterxml.jackson.annotation.JsonProperty("status") MCStatus MCstatus,@com.fasterxml.jackson.annotation.JsonProperty("error") String MCerror,@com.fasterxml.jackson.annotation.JsonProperty("raw") Map<String,String> MCraw){
        public boolean MCusable(){return MCstatus==MCStatus.SUCCESS||MCstatus==MCStatus.PARTIAL;}
    }
    private final MCApiCredentialStore MCcredentials;
    private final Map<String,MCLookupProvider> MCproviders=new LinkedHashMap<>();
    public MCLookupRouter(MCApiCredentialStore MCcredentials,Collection<MCLookupProvider> MCproviders){this.MCcredentials=MCcredentials;for(MCLookupProvider MCprovider:MCproviders)this.MCproviders.put(MCprovider.MCname(),MCprovider);}
    public MCResult MCquery(String MCservice,String MCidentifier)throws Exception{return MCquery(MCservice,MCidentifier,null);}
    public MCResult MCquerySpecific(String MCservice,String MCidentifier,long MCcredentialId)throws Exception{return MCquery(MCservice,MCidentifier,MCcredentialId);}
    private MCResult MCquery(String MCservice,String MCidentifier,Long MCrequestedId)throws Exception{
        int MClength="DNI".equals(MCservice)?8:"RUC".equals(MCservice)?11:0;
        if(MClength==0||MCidentifier==null||!MCidentifier.matches("\\d{"+MClength+"}"))throw new IllegalArgumentException("Número de documento inválido.");
        List<MCApiCredentialStore.MCCredential> MCoptions=new ArrayList<>(MCcredentials.MCcredentials());MCoptions.removeIf(MCc->MCrequestedId!=null?MCc.MCid()!=MCrequestedId:!MCc.MCenabled()||MCc.MCstatus().contains("inválida")||MCc.MCstatus().contains("Invalidez confirmada")||MCc.MCretryAfter()>Instant.now().getEpochSecond());
        MCoptions.removeIf(MCc->!MCproviders.containsKey(MCc.MCprovider())||!MCproviders.get(MCc.MCprovider()).MCservices().contains(MCservice));
        MCoptions.sort(Comparator.comparingInt((MCApiCredentialStore.MCCredential MCc)->MCc.MCprovider().equals("Factiliza")?0:1).thenComparingInt(MCApiCredentialStore.MCCredential::MCrequests).thenComparingLong(MCApiCredentialStore.MCCredential::MCid));
        if(MCoptions.isEmpty())return MCempty(MCservice,MCidentifier,MCStatus.NO_CREDENTIAL,"No hay credenciales habilitadas; complete los campos manualmente.");
        MCStatus MClast=MCStatus.NO_CREDENTIAL;String MCreason="No se obtuvo información.";Set<String> MCskippedProviders=new HashSet<>();int MCattempts=0;
        for(MCApiCredentialStore.MCCredential MCcredential:MCoptions){
            if(MCattempts>=4||MCskippedProviders.contains(MCcredential.MCprovider()))continue;
            MCLookupProvider MCprovider=MCproviders.get(MCcredential.MCprovider());String MCtoken;
            try{MCtoken=MCcredentials.MCtoken(MCcredential.MCid());}catch(Exception MCe){MClast=MCStatus.BAD_RESPONSE;MCreason="No se pudo leer la credencial protegida.";continue;}
            if(MCtoken==null||MCtoken.isBlank()){MClast=MCStatus.BAD_RESPONSE;MCreason="La credencial protegida está vacía.";continue;}
            MCattempts++;
            try{
                Map<String,String> MCraw=MCprovider.MCquery(MCservice,MCidentifier,MCtoken);
                Map<String,String> MCvalues=MCApiFieldCatalog.MCnormalize(MCprovider.MCname(),MCservice,MCraw);
                Set<String> MCexpected=new LinkedHashSet<>();for(MCApiFieldCatalog.MCField MCfield:MCApiFieldCatalog.MCfields(MCservice))MCexpected.add(MCfield.MCkey());
                Set<String> MCunavailable=new LinkedHashSet<>(MCexpected);MCunavailable.removeAll(MCvalues.keySet());
                Set<String> MCmissingSupported=new LinkedHashSet<>(MCApiFieldCatalog.MCsupportedKeys(MCprovider.MCname(),MCservice));MCmissingSupported.removeAll(MCvalues.keySet());
                MCStatus MCstatus=MCvalues.isEmpty()?MCStatus.BAD_RESPONSE:MCmissingSupported.isEmpty()?MCStatus.SUCCESS:MCStatus.PARTIAL;
                MCcredentials.MCrecord(MCcredential.MCid(),MCstatus,0);
                if(MCstatus==MCStatus.BAD_RESPONSE){MClast=MCstatus;MCreason="El proveedor devolvió una respuesta sin datos utilizables.";MCskippedProviders.add(MCprovider.MCname());continue;}
                return new MCResult(MCservice,MCidentifier,MCprovider.MCname(),Map.copyOf(MCvalues),Set.copyOf(MCvalues.keySet()),Set.copyOf(MCunavailable),MCstatus,MCstatus==MCStatus.PARTIAL?"Algunos campos no están disponibles.":"",Map.copyOf(MCraw));
            }catch(Exception MCfailure){
                MCStatus MCstatus=MCclassify(MCfailure);long MCretry=MCfailure instanceof MCFactilizaClient.MCHttpStatusException MChttp?MChttp.MCretryAfterSeconds():0;
                MCcredentials.MCrecord(MCcredential.MCid(),MCstatus,MCretry);MClast=MCstatus;MCreason=MCmessage(MCstatus);
                if(MCstatus==MCStatus.NO_INTERNET||MCstatus==MCStatus.NOT_FOUND)break;
                if(MCstatus==MCStatus.RATE_LIMIT||MCstatus==MCStatus.TIMEOUT||MCstatus==MCStatus.TEMPORARY||MCstatus==MCStatus.PERMISSION||MCstatus==MCStatus.UNSUPPORTED||MCstatus==MCStatus.BAD_RESPONSE)MCskippedProviders.add(MCprovider.MCname());
            }
        }
        return MCempty(MCservice,MCidentifier,MClast,MCreason+" Puede continuar manualmente.");
    }
    private static MCResult MCempty(String MCservice,String MCidentifier,MCStatus MCstatus,String MCreason){Set<String> MCmissing=new LinkedHashSet<>();for(MCApiFieldCatalog.MCField MCfield:MCApiFieldCatalog.MCfields(MCservice))MCmissing.add(MCfield.MCkey());return new MCResult(MCservice,MCidentifier,"",Map.of(),Set.of(),Set.copyOf(MCmissing),MCstatus,MCreason,Map.of());}
    private static MCStatus MCclassify(Throwable MCfailure){
        if(MCfailure instanceof MCFactilizaClient.MCHttpStatusException MChttp)return switch(MChttp.MCstatusCode()){case 401->MCStatus.INVALID;case 403->MCStatus.PERMISSION;case 404->MCStatus.NOT_FOUND;case 429->MCStatus.RATE_LIMIT;case 408,504->MCStatus.TIMEOUT;case 501->MCStatus.UNSUPPORTED;default->MChttp.MCstatusCode()>=500?MCStatus.TEMPORARY:MCStatus.BAD_RESPONSE;};
        if(MCfailure instanceof HttpTimeoutException)return MCStatus.TIMEOUT;
        if(MCfailure instanceof UnknownHostException||MCfailure instanceof ConnectException)return MCStatus.NO_INTERNET;
        if(MCfailure instanceof IOException MCio&&MCio.getCause()!=null)return MCclassify(MCio.getCause());
        return MCStatus.BAD_RESPONSE;
    }
    private static String MCmessage(MCStatus MCstatus){return switch(MCstatus){case NO_INTERNET->"Sin conexión a Internet.";case TIMEOUT->"La consulta agotó el tiempo de espera.";case TEMPORARY->"El proveedor no está disponible temporalmente.";case RATE_LIMIT->"Se alcanzó un límite de consultas.";case INVALID->"La credencial fue rechazada.";case PERMISSION->"La credencial no tiene permisos suficientes.";case UNSUPPORTED->"El servicio no está habilitado.";case NOT_FOUND->"Documento no encontrado.";case PARTIAL->"Respuesta parcial.";case BAD_RESPONSE->"Respuesta incorrecta o incompleta.";case NO_CREDENTIAL->"Sin credenciales.";case SUCCESS->"Consulta correcta.";};}
}
