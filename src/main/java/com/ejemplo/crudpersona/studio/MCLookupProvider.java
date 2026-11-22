package com.ejemplo.crudpersona.studio;

import java.util.Map;
import java.util.Set;

/** @author MC */
public interface MCLookupProvider {
    String mcName();
    Set<String> mcServices();
    Map<String,String> mcQuery(String mcService,String mcIdentifier,String mcCredential)throws Exception;
}
