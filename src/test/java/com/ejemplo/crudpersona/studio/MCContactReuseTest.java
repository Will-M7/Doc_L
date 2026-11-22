package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.MCDocumentStore.MCVariable;
import com.ejemplo.crudpersona.studio.MCStudioData.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MCContactReuseTest {
    @TempDir Path MCtemp;

    @Test void MCpartialApiUpdatesPreserveNonEmptyFieldsAllRawValuesAndExactIdentities()throws Exception{
        new MCDocumentStore(MCtemp);MCStudioData MCdata=new MCStudioData(MCtemp);
        MCdata.MCsaveContact(new MCContact(0,"Persona","CE","","CE","12345678","","","{}"));
        MCContact MCfirst=MCdata.MCupsertContact(MCContactReuse.MCfromApi(MCresult("DNI","12345678",Map.of("nombres","Ana","apellido_paterno","Pérez","direccion","Lima"),Map.of("data.ubigeo[2]","150101","data.extra","conservar"))));
        MCContact MCupdated=MCdata.MCupsertContact(MCContactReuse.MCfromApi(MCresult("DNI","12345678",Map.of("nombres","Ana nueva","direccion",""),Map.of("data.sexo","F"))));
        assertThat(MCupdated.MCid()).isEqualTo(MCfirst.MCid());assertThat(MCupdated.MCgiven()).isEqualTo("Ana nueva");assertThat(MCupdated.MCaddress()).isEqualTo("Lima");assertThat(MCupdated.MCfamily()).isEqualTo("Pérez");assertThat(MCupdated.MCextrasJson()).contains("data.extra","conservar","data.sexo");assertThat(MCContactReuse.MCapiValues(MCupdated)).containsEntry("ubigeo","150101").containsEntry("nombres","Ana nueva");assertThat(MCdata.MCcontacts()).hasSize(2);assertThat(MCdata.MCmatchingContact("Empresa","RUC","12345678")).isNull();
        MCContact MCcompany=MCdata.MCupsertContact(MCContactReuse.MCfromApi(MCresult("RUC","20123456789",Map.of("razon_social","ACME","direccion_completa","Cusco"),Map.of("data.representantes[0].nombre","Lucía"))));assertThat(MCcompany.MCrepresentatives()).contains("Lucía");assertThat(MCcompany.MCextrasJson()).contains("data.representantes[0].nombre");
    }

    @Test void MCtypedManualFormsAndApiContextsSaveOfflineWithoutConflictCallback()throws Exception{
        new MCDocumentStore(MCtemp);MCStudioData MCdata=new MCStudioData(MCtemp);long MCtemplate=1;
        List<MCVariable> MCvars=List.of(MCv("dni","DNI","DNI"),MCv("nombre","Nombres","Texto corto"),MCv("direccion","Domicilio","Domicilio"));
        List<MCContactBinding> MCbindings=MCContactReuse.MCformBindings(MCtemplate,MCvars,List.of(),List.of(),Map.of());Map<String,String> MCvalues=new LinkedHashMap<>(Map.of("dni","12345678","nombre","Ana","direccion","Lima"));
        assertThat(MCContactReuse.MCsave(MCdata,MCbindings,MCvalues,Map.of("dni","DNI")).MCcreated()).isEqualTo(1);MCvalues.put("nombre","Ana nueva");MCvalues.put("direccion","");
        assertThat(MCContactReuse.MCsave(MCdata,MCbindings,MCvalues,Map.of("dni","DNI")).MCupdated()).isEqualTo(1);assertThat(MCdata.MCmatchingContact("Persona","DNI","12345678").MCaddress()).isEqualTo("Lima");
        List<MCVariable> MCcompanies=List.of(MCv("numero","Identificador","Texto corto"),MCv("razon_social","Razón social","Texto corto"),MCv("direccion","Domicilio","Domicilio"));List<MCApiContext> MCcontexts=List.of(new MCApiContext(1,"numero","empresa","RUC",true),new MCApiContext(1,"razon_social","empresa","RUC",false),new MCApiContext(1,"direccion","empresa","RUC",false));
        assertThat(MCContactReuse.MCsave(MCdata,MCContactReuse.MCformBindings(MCtemplate,MCcompanies,List.of(),MCcontexts,Map.of()),Map.of("numero","20123456789","razon_social","ACME","direccion","Cusco"),Map.of()).MCcreated()).isEqualTo(1);assertThat(MCdata.MCmatchingContact("Empresa","RUC","20123456789").MCgiven()).isEqualTo("ACME");
    }

    @Test void MCindependentPeopleKeepTheirApiBoundFieldsSeparatedWhenEnteredManually()throws Exception{
        new MCDocumentStore(MCtemp);MCStudioData MCdata=new MCStudioData(MCtemp);List<MCVariable> MCvariables=List.of(MCv("documento_a","Documento","Texto corto"),MCv("dato_a","Dato","Texto corto"),MCv("documento_b","Documento","Texto corto"),MCv("dato_b","Dato","Texto corto"));
        List<MCApiContext> MCcontexts=List.of(new MCApiContext(1,"documento_a","a","DNI",true),new MCApiContext(1,"dato_a","a","DNI",false),new MCApiContext(1,"documento_b","b","DNI",true),new MCApiContext(1,"dato_b","b","DNI",false));
        Map<String,MCApiBinding> MCapi=Map.of("dato_a",new MCApiBinding(1,"DNI","field:nombres"),"dato_b",new MCApiBinding(2,"DNI","field:nombres"));List<MCContactBinding> MCbindings=MCContactReuse.MCformBindings(1,MCvariables,List.of(),MCcontexts,MCapi);
        assertThat(MCContactReuse.MCsave(MCdata,MCbindings,Map.of("documento_a","12345678","dato_a","Ana","documento_b","87654321","dato_b","María"),Map.of("documento_a","DNI","documento_b","DNI")).MCcreated()).isEqualTo(2);assertThat(MCdata.MCmatchingContact("Persona","DNI","12345678").MCgiven()).isEqualTo("Ana");assertThat(MCdata.MCmatchingContact("Persona","DNI","87654321").MCgiven()).isEqualTo("María");
    }
    @Test void MCfullNamesAndPartialSurnameUpdatesRetainTheirOriginalMeaning()throws Exception{
        new MCDocumentStore(MCtemp);MCStudioData MCdata=new MCStudioData(MCtemp);
        MCContact MCcontact=MCdata.MCupsertContact(MCContactReuse.MCfromApi(MCresult("DNI","12345678",Map.of("nombres","ANA","apellido_paterno","PÉREZ","apellido_materno","RUIZ","nombre_completo","PÉREZ RUIZ, ANA"),Map.of())));
        assertThat(MCContactReuse.MCapiValues(MCcontact)).containsEntry("nombre_completo","ANA PÉREZ RUIZ");
        List<MCContactBinding> MCbindings=MCContactReuse.MCformBindings(1,List.of(MCv("dni","DNI","DNI"),MCv("completo","Dato","Texto corto")),List.of(),List.of(),Map.of("completo",new MCApiBinding(1,"DNI","field:nombre_completo")));
        assertThat(MCContactReuse.MCvaluesFor(MCcontact,MCbindings)).containsEntry("completo","ANA PÉREZ RUIZ");
        MCContact MCupdated=MCdata.MCupsertContact(MCContactReuse.MCfromApi(MCresult("DNI","12345678",Map.of("apellido_paterno","GÓMEZ"),Map.of())));
        assertThat(MCupdated.MCfamily()).isEqualTo("GÓMEZ RUIZ");assertThat(MCupdated.MCgiven()).isEqualTo("ANA");
    }

    @Test void MCpollutedGivenNamesAreCleanedOnceAndStayCleanWhenSavedOrReused()throws Exception{
        new MCDocumentStore(MCtemp);MCStudioData MCdata=new MCStudioData(MCtemp);
        for(String MCprovider:List.of("Factiliza","APIsPERU"))for(String MCgiven:List.of("QUISPE MAMANI, JUAN CARLOS","QUISPE MAMANI JUAN CARLOS","JUAN CARLOS QUISPE MAMANI","QUISPE MAMANI JUAN CARLOS QUISPE MAMANI")){
            String MCprefix="Factiliza".equals(MCprovider)?"data.":"";Map<String,String> MCraw=Map.of(MCprefix+"nombres",MCgiven,MCprefix+("Factiliza".equals(MCprovider)?"apellido_paterno":"apellidoPaterno"),"QUISPE",MCprefix+("Factiliza".equals(MCprovider)?"apellido_materno":"apellidoMaterno"),"MAMANI");
            Map<String,String> MCresponse=MCApiFieldCatalog.MCnormalizedResponse(MCprovider,"DNI",MCraw),MCvalues=MCApiFieldCatalog.MCnormalize(MCprovider,"DNI",MCresponse);assertThat(MCvalues).containsEntry("nombres","JUAN CARLOS").containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI").containsEntry("apellido_paterno","QUISPE").containsEntry("apellido_materno","MAMANI");Map<String,String> MCagain=new LinkedHashMap<>(MCvalues);MCApiFieldCatalog.MCnormalizePersonName(MCagain);assertThat(MCagain).isEqualTo(MCvalues);
            MCLookupRouter.MCResult MCresult=new MCLookupRouter.MCResult("DNI","12345678",MCprovider,MCvalues,MCvalues.keySet(),Set.of(),MCLookupRouter.MCStatus.SUCCESS,"",MCresponse);MCContact MCcontact=MCdata.MCupsertContact(MCContactReuse.MCfromApi(MCresult));assertThat(MCcontact.MCgiven()).isEqualTo("JUAN CARLOS");assertThat(MCcontact.MCfamily()).isEqualTo("QUISPE MAMANI");assertThat(MCContactReuse.MCapiValues(MCcontact)).containsEntry("nombres","JUAN CARLOS").containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI");assertThat(MCdata.MCcontacts()).hasSize(1);
        }
        for(String MCprovider:List.of("Factiliza","APIsPERU")){String MCprefix="Factiliza".equals(MCprovider)?"data.":"";Map<String,String> MCresponse=MCApiFieldCatalog.MCnormalizedResponse(MCprovider,"DNI",Map.of(MCprefix+"nombres","QUISPE MAMANI, JUAN CARLOS"));assertThat(MCApiFieldCatalog.MCnormalize(MCprovider,"DNI",MCresponse)).containsEntry("nombres","JUAN CARLOS").containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI");}
        Map<String,String> MCparsed=MCApisPeruProvider.MCparse("DNI",200,"{\"dni\":\"12345678\",\"nombres\":\"QUISPE MAMANI, JUAN CARLOS\",\"apellidoPaterno\":\"QUISPE\",\"apellidoMaterno\":\"MAMANI\"}",0);assertThat(MCparsed).containsEntry("nombres","JUAN CARLOS").containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI");
    }
    private static MCVariable MCv(String MCkey,String MClabel,String MCtype){return new MCVariable(0,1,MCkey,MClabel,MCtype,"",false,1);}
    private static MCLookupRouter.MCResult MCresult(String MCservice,String MCnumber,Map<String,String> MCvalues,Map<String,String> MCraw){return new MCLookupRouter.MCResult(MCservice,MCnumber,"Factiliza",MCvalues,MCvalues.keySet(),Set.of(),MCLookupRouter.MCStatus.PARTIAL,"",MCraw);}
}
