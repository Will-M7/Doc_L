package com.ejemplo.crudpersona.studio;

import java.util.Map;
import java.util.Set;

public final class MCFactilizaProvider implements MCLookupProvider {
    private final MCFactilizaClient MCclient;
    public MCFactilizaProvider(){this(new MCFactilizaClient());}
    public MCFactilizaProvider(MCFactilizaClient MCclient){this.MCclient=MCclient;}
    @Override public String MCname(){return "Factiliza";}
    @Override public Set<String> MCservices(){return Set.of("DNI","RUC");}
    @Override public Map<String,String> MCquery(String MCservice,String MCidentifier,String MCcredential)throws Exception{return MCApiFieldCatalog.MCnormalizedResponse(MCname(),MCservice,MCclient.MCquery(MCservice,MCidentifier,MCcredential).MCdata());}
}
