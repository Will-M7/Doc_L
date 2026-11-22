package com.ejemplo.crudpersona.studio;

import java.util.Map;
import java.util.Set;

public interface MCLookupProvider {
    String MCname();
    Set<String> MCservices();
    Map<String,String> MCquery(String MCservice,String MCidentifier,String MCcredential)throws Exception;
}
