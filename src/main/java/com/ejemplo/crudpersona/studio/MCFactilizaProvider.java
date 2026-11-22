package com.ejemplo.crudpersona.studio;

import java.util.Map;
import java.util.Set;

/** @author MC */
public final class MCFactilizaProvider implements MCLookupProvider {
    private final MCFactilizaClient mcClient;
    public MCFactilizaProvider(){this(new MCFactilizaClient());}
    public MCFactilizaProvider(MCFactilizaClient mcClient){this.mcClient=mcClient;}
    @Override public String mcName(){return "Factiliza";}
    @Override public Set<String> mcServices(){return Set.of("DNI","RUC");}
    @Override public Map<String,String> mcQuery(String mcService,String mcIdentifier,String mcCredential)throws Exception{return MCApiFieldCatalog.mcNormalizedResponse(mcName(),mcService,mcClient.mcQuery(mcService,mcIdentifier,mcCredential).mcData());}
}
