package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.MCDocumentStore.MCVariable;
import com.ejemplo.crudpersona.studio.MCStudioData.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** @author MC */
class MCContactReuseTest {
    @TempDir Path mcTemp;

    @Test void mcPartialApiUpdatesPreserveNonEmptyFieldsAllRawValuesAndExactIdentities()throws Exception{
        new MCDocumentStore(mcTemp);MCStudioData mcData=new MCStudioData(mcTemp);
        mcData.mcSaveContact(new MCContact(0,"Persona","CE","","CE","12345678","","","{}"));
        MCContact mcFirst=mcData.mcUpsertContact(MCContactReuse.mcFromApi(mcResult("DNI","12345678",Map.of("nombres","Ana","apellido_paterno","Pérez","direccion","Lima"),Map.of("data.ubigeo[2]","150101","data.extra","conservar"))));
        MCContact mcUpdated=mcData.mcUpsertContact(MCContactReuse.mcFromApi(mcResult("DNI","12345678",Map.of("nombres","Ana nueva","direccion",""),Map.of("data.sexo","F"))));
        assertThat(mcUpdated.mcId()).isEqualTo(mcFirst.mcId());assertThat(mcUpdated.mcGiven()).isEqualTo("Ana nueva");assertThat(mcUpdated.mcAddress()).isEqualTo("Lima");assertThat(mcUpdated.mcFamily()).isEqualTo("Pérez");assertThat(mcUpdated.mcExtrasJson()).contains("data.extra","conservar","data.sexo");assertThat(MCContactReuse.mcApiValues(mcUpdated)).containsEntry("ubigeo","150101").containsEntry("nombres","Ana nueva");assertThat(mcData.mcContacts()).hasSize(2);assertThat(mcData.mcMatchingContact("Empresa","RUC","12345678")).isNull();
        MCContact mcCompany=mcData.mcUpsertContact(MCContactReuse.mcFromApi(mcResult("RUC","20123456789",Map.of("razon_social","ACME","direccion_completa","Cusco"),Map.of("data.representantes[0].nombre","Lucía"))));assertThat(mcCompany.mcRepresentatives()).contains("Lucía");assertThat(mcCompany.mcExtrasJson()).contains("data.representantes[0].nombre");
    }

    @Test void mcTypedManualFormsAndApiContextsSaveOfflineWithoutConflictCallback()throws Exception{
        new MCDocumentStore(mcTemp);MCStudioData mcData=new MCStudioData(mcTemp);long mcTemplate=1;
        List<MCVariable> mcVars=List.of(mcV("dni","DNI","DNI"),mcV("nombre","Nombres","Texto corto"),mcV("direccion","Domicilio","Domicilio"));
        List<MCContactBinding> mcBindings=MCContactReuse.mcFormBindings(mcTemplate,mcVars,List.of(),List.of(),Map.of());Map<String,String> mcValues=new LinkedHashMap<>(Map.of("dni","12345678","nombre","Ana","direccion","Lima"));
        assertThat(MCContactReuse.mcSave(mcData,mcBindings,mcValues,Map.of("dni","DNI")).mcCreated()).isEqualTo(1);mcValues.put("nombre","Ana nueva");mcValues.put("direccion","");
        assertThat(MCContactReuse.mcSave(mcData,mcBindings,mcValues,Map.of("dni","DNI")).mcUpdated()).isEqualTo(1);assertThat(mcData.mcMatchingContact("Persona","DNI","12345678").mcAddress()).isEqualTo("Lima");
        List<MCVariable> mcCompanies=List.of(mcV("numero","Identificador","Texto corto"),mcV("razon_social","Razón social","Texto corto"),mcV("direccion","Domicilio","Domicilio"));List<MCApiContext> mcContexts=List.of(new MCApiContext(1,"numero","empresa","RUC",true),new MCApiContext(1,"razon_social","empresa","RUC",false),new MCApiContext(1,"direccion","empresa","RUC",false));
        assertThat(MCContactReuse.mcSave(mcData,MCContactReuse.mcFormBindings(mcTemplate,mcCompanies,List.of(),mcContexts,Map.of()),Map.of("numero","20123456789","razon_social","ACME","direccion","Cusco"),Map.of()).mcCreated()).isEqualTo(1);assertThat(mcData.mcMatchingContact("Empresa","RUC","20123456789").mcGiven()).isEqualTo("ACME");
    }

    @Test void mcIndependentPeopleKeepTheirApiBoundFieldsSeparatedWhenEnteredManually()throws Exception{
        new MCDocumentStore(mcTemp);MCStudioData mcData=new MCStudioData(mcTemp);List<MCVariable> mcVariables=List.of(mcV("documento_a","Documento","Texto corto"),mcV("dato_a","Dato","Texto corto"),mcV("documento_b","Documento","Texto corto"),mcV("dato_b","Dato","Texto corto"));
        List<MCApiContext> mcContexts=List.of(new MCApiContext(1,"documento_a","a","DNI",true),new MCApiContext(1,"dato_a","a","DNI",false),new MCApiContext(1,"documento_b","b","DNI",true),new MCApiContext(1,"dato_b","b","DNI",false));
        Map<String,MCApiBinding> mcApi=Map.of("dato_a",new MCApiBinding(1,"DNI","field:nombres"),"dato_b",new MCApiBinding(2,"DNI","field:nombres"));List<MCContactBinding> mcBindings=MCContactReuse.mcFormBindings(1,mcVariables,List.of(),mcContexts,mcApi);
        assertThat(MCContactReuse.mcSave(mcData,mcBindings,Map.of("documento_a","12345678","dato_a","Ana","documento_b","87654321","dato_b","María"),Map.of("documento_a","DNI","documento_b","DNI")).mcCreated()).isEqualTo(2);assertThat(mcData.mcMatchingContact("Persona","DNI","12345678").mcGiven()).isEqualTo("Ana");assertThat(mcData.mcMatchingContact("Persona","DNI","87654321").mcGiven()).isEqualTo("María");
    }
    @Test void mcFullNamesAndPartialSurnameUpdatesRetainTheirOriginalMeaning()throws Exception{
        new MCDocumentStore(mcTemp);MCStudioData mcData=new MCStudioData(mcTemp);
        MCContact mcContact=mcData.mcUpsertContact(MCContactReuse.mcFromApi(mcResult("DNI","12345678",Map.of("nombres","ANA","apellido_paterno","PÉREZ","apellido_materno","RUIZ","nombre_completo","PÉREZ RUIZ, ANA"),Map.of())));
        assertThat(MCContactReuse.mcApiValues(mcContact)).containsEntry("nombre_completo","ANA PÉREZ RUIZ");
        List<MCContactBinding> mcBindings=MCContactReuse.mcFormBindings(1,List.of(mcV("dni","DNI","DNI"),mcV("completo","Dato","Texto corto")),List.of(),List.of(),Map.of("completo",new MCApiBinding(1,"DNI","field:nombre_completo")));
        assertThat(MCContactReuse.mcValuesFor(mcContact,mcBindings)).containsEntry("completo","ANA PÉREZ RUIZ");
        MCContact mcUpdated=mcData.mcUpsertContact(MCContactReuse.mcFromApi(mcResult("DNI","12345678",Map.of("apellido_paterno","GÓMEZ"),Map.of())));
        assertThat(mcUpdated.mcFamily()).isEqualTo("GÓMEZ RUIZ");assertThat(mcUpdated.mcGiven()).isEqualTo("ANA");
    }

    @Test void mcPollutedGivenNamesAreCleanedOnceAndStayCleanWhenSavedOrReused()throws Exception{
        new MCDocumentStore(mcTemp);MCStudioData mcData=new MCStudioData(mcTemp);
        for(String mcProvider:List.of("Factiliza","APIsPERU"))for(String mcGiven:List.of("QUISPE MAMANI, JUAN CARLOS","QUISPE MAMANI JUAN CARLOS","JUAN CARLOS QUISPE MAMANI","QUISPE MAMANI JUAN CARLOS QUISPE MAMANI")){
            String mcPrefix="Factiliza".equals(mcProvider)?"data.":"";Map<String,String> mcRaw=Map.of(mcPrefix+"nombres",mcGiven,mcPrefix+("Factiliza".equals(mcProvider)?"apellido_paterno":"apellidoPaterno"),"QUISPE",mcPrefix+("Factiliza".equals(mcProvider)?"apellido_materno":"apellidoMaterno"),"MAMANI");
            Map<String,String> mcResponse=MCApiFieldCatalog.mcNormalizedResponse(mcProvider,"DNI",mcRaw),mcValues=MCApiFieldCatalog.mcNormalize(mcProvider,"DNI",mcResponse);assertThat(mcValues).containsEntry("nombres","JUAN CARLOS").containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI").containsEntry("apellido_paterno","QUISPE").containsEntry("apellido_materno","MAMANI");Map<String,String> mcAgain=new LinkedHashMap<>(mcValues);MCApiFieldCatalog.mcNormalizePersonName(mcAgain);assertThat(mcAgain).isEqualTo(mcValues);
            MCLookupRouter.MCResult mcResult=new MCLookupRouter.MCResult("DNI","12345678",mcProvider,mcValues,mcValues.keySet(),Set.of(),MCLookupRouter.MCStatus.SUCCESS,"",mcResponse);MCContact mcContact=mcData.mcUpsertContact(MCContactReuse.mcFromApi(mcResult));assertThat(mcContact.mcGiven()).isEqualTo("JUAN CARLOS");assertThat(mcContact.mcFamily()).isEqualTo("QUISPE MAMANI");assertThat(MCContactReuse.mcApiValues(mcContact)).containsEntry("nombres","JUAN CARLOS").containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI");assertThat(mcData.mcContacts()).hasSize(1);
        }
        for(String mcProvider:List.of("Factiliza","APIsPERU")){String mcPrefix="Factiliza".equals(mcProvider)?"data.":"";Map<String,String> mcResponse=MCApiFieldCatalog.mcNormalizedResponse(mcProvider,"DNI",Map.of(mcPrefix+"nombres","QUISPE MAMANI, JUAN CARLOS"));assertThat(MCApiFieldCatalog.mcNormalize(mcProvider,"DNI",mcResponse)).containsEntry("nombres","JUAN CARLOS").containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI");}
        Map<String,String> mcParsed=MCApisPeruProvider.mcParse("DNI",200,"{\"dni\":\"12345678\",\"nombres\":\"QUISPE MAMANI, JUAN CARLOS\",\"apellidoPaterno\":\"QUISPE\",\"apellidoMaterno\":\"MAMANI\"}",0);assertThat(mcParsed).containsEntry("nombres","JUAN CARLOS").containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI");
    }
    private static MCVariable mcV(String mcKey,String mcLabel,String mcType){return new MCVariable(0,1,mcKey,mcLabel,mcType,"",false,1);}
    private static MCLookupRouter.MCResult mcResult(String mcService,String mcNumber,Map<String,String> mcValues,Map<String,String> mcRaw){return new MCLookupRouter.MCResult(mcService,mcNumber,"Factiliza",mcValues,mcValues.keySet(),Set.of(),MCLookupRouter.MCStatus.PARTIAL,"",mcRaw);}
}
