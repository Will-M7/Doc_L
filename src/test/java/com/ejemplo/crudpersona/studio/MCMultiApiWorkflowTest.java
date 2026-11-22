package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.MCDocumentStore.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** @author MC */
class MCMultiApiWorkflowTest {
    @TempDir Path mcTemp;
    private static Clock mcClock(String mcDate){return Clock.fixed(LocalDate.parse(mcDate).atStartOfDay(ZoneId.systemDefault()).toInstant(),ZoneId.systemDefault());}

    @Test void mcNormalizesDocumentedExamplesWithoutInventingAbsentFields()throws Exception{
        String mcFactiliza="{\"status\":200,\"success\":true,\"data\":{\"numero\":\"12345678\",\"nombres\":\"ANA\",\"apellido_paterno\":\"PEREZ\",\"nombre_completo\":\"PEREZ, ANA\",\"ubigeo\":[\"15\",\"1501\",\"150101\"],\"fecha_nacimiento\":null}}";
        Map<String,String> mcRaw=MCFactilizaClient.mcParse("DNI","12345678",200,mcFactiliza).mcData();
        assertThat(MCApiFieldCatalog.mcNormalize("Factiliza","DNI",mcRaw)).containsEntry("nombre_completo","ANA PEREZ").containsEntry("ubigeo","150101").doesNotContainKey("fecha_nacimiento");
        String mcPeru="{\"dni\":\"12345678\",\"nombres\":\"ANA\",\"apellidoPaterno\":\"PEREZ\",\"apellidoMaterno\":\"RUIZ\",\"codVerifica\":\"1\"}";
        assertThat(MCApiFieldCatalog.mcNormalize("APIsPERU","DNI",MCApisPeruProvider.mcParse("DNI",200,mcPeru,0))).containsEntry("apellido_paterno","PEREZ").doesNotContainKey("direccion").containsEntry("nombre_completo","ANA PEREZ RUIZ");
        assertThat(MCApiFieldCatalog.mcCanonical("DNI","data.apellido_paterno")).isEqualTo("apellido_paterno");
        assertThat(MCApiFieldCatalog.mcCanonical("DNI","data.campo_desconocido")).startsWith("legacy:");
    }

    @Test void mcFullNamesUseGivenThenFamilyForBothProvidersAndCommaOnlyResponses()throws Exception{
        for(String mcProvider:List.of("Factiliza","APIsPERU")){
            String mcPrefix="Factiliza".equals(mcProvider)?"data.":"";
            Map<String,String> mcSeparate=new LinkedHashMap<>();mcSeparate.put(mcPrefix+"nombres","  JUAN   CARLOS ");mcSeparate.put(mcPrefix+("Factiliza".equals(mcProvider)?"apellido_paterno":"apellidoPaterno"),"QUISPE");mcSeparate.put(mcPrefix+("Factiliza".equals(mcProvider)?"apellido_materno":"apellidoMaterno"),"MAMANI");mcSeparate.put(mcPrefix+"nombre_completo","QUISPE MAMANI, JUAN CARLOS");
            Map<String,String> mcValues=MCApiFieldCatalog.mcNormalize(mcProvider,"DNI",mcSeparate);assertThat(mcValues).containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI").containsEntry("nombres","JUAN CARLOS").containsEntry("apellido_paterno","QUISPE").containsEntry("apellido_materno","MAMANI");
            Map<String,String> mcFallback=MCApiFieldCatalog.mcNormalize(mcProvider,"DNI",Map.of(mcPrefix+"nombre_completo","  QUISPE  MAMANI,  JUAN   CARLOS  "));assertThat(mcFallback).containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI");
            MCLookupRouter.MCResult mcResult=new MCLookupRouter.MCResult("DNI","12345678",mcProvider,mcFallback,mcFallback.keySet(),Set.of(),MCLookupRouter.MCStatus.SUCCESS,"",Map.of(mcPrefix+"nombre_completo","QUISPE MAMANI, JUAN CARLOS"));
            new MCDocumentStore(mcTemp.resolve(mcProvider));MCStudioData mcData=new MCStudioData(mcTemp.resolve(mcProvider));mcData.mcUpsertContact(MCContactReuse.mcFromApi(mcResult));var mcContact=mcData.mcContacts().get(0);assertThat(mcContact.mcGiven()).isEqualTo("JUAN CARLOS");assertThat(mcContact.mcFamily()).isEqualTo("QUISPE MAMANI");assertThat(MCContactReuse.mcApiValues(mcContact)).containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI");
        }
        Map<String,String> mcParsed=MCApisPeruProvider.mcParse("DNI",200,"{\"dni\":\"12345678\",\"nombre_completo\":\"QUISPE MAMANI, JUAN CARLOS\"}",0);assertThat(mcParsed).containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI");
    }

    @Test void mcFallsBackAfterTimeoutTracksMonthlyUsageAndKeepsInvalidCredentials()throws Exception{
        Path mcDir=mcTemp.resolve("router");new MCDocumentStore(mcDir);MCApiCredentialStore mcStore=new MCApiCredentialStore(mcDir,mcClock("2026-11-12"));
        MCApiCredentialStore.MCCredential mcFirst=mcStore.mcAdd("Principal","Factiliza","private-factiliza-token");
        MCApiCredentialStore.MCCredential mcSecond=mcStore.mcAdd("Respaldo","APIsPERU","private-apisperu-token");
        AtomicInteger mcFirstCalls=new AtomicInteger(),mcSecondCalls=new AtomicInteger();
        MCLookupProvider mcFactiliza=new MCLookupProvider(){public String mcName(){return "Factiliza";}public Set<String> mcServices(){return Set.of("DNI","RUC");}public Map<String,String> mcQuery(String mcService,String mcNumber,String mcToken)throws Exception{mcFirstCalls.incrementAndGet();throw new HttpTimeoutException("timeout");}};
        MCLookupProvider mcPeru=new MCLookupProvider(){public String mcName(){return "APIsPERU";}public Set<String> mcServices(){return Set.of("DNI","RUC");}public Map<String,String> mcQuery(String mcService,String mcNumber,String mcToken){mcSecondCalls.incrementAndGet();return Map.of("dni",mcNumber,"nombres","ANA");}};
        MCLookupRouter mcRouter=new MCLookupRouter(mcStore,List.of(mcFactiliza,mcPeru));MCLookupRouter.MCResult mcResult=mcRouter.mcQuery("DNI","12345678");
        assertThat(mcResult.mcProvider()).isEqualTo("APIsPERU");assertThat(mcResult.mcValues()).containsEntry("nombres","ANA");assertThat(mcFirstCalls).hasValue(1);assertThat(mcSecondCalls).hasValue(1);
        assertThat(mcStore.mcCredentials().stream().filter(mcC->mcC.mcId()==mcFirst.mcId()).findFirst().orElseThrow().mcRequests()).isEqualTo(1);
        assertThat(mcStore.mcCredentials().stream().filter(mcC->mcC.mcId()==mcSecond.mcId()).findFirst().orElseThrow().mcSuccesses()).isEqualTo(1);
        mcStore.mcRecord(mcFirst.mcId(),MCLookupRouter.MCStatus.INVALID,0);assertThat(mcStore.mcCredentials()).hasSize(2);
        MCApiCredentialStore mcNextMonth=new MCApiCredentialStore(mcDir,mcClock("2026-12-01"));assertThat(mcNextMonth.mcCredentials().stream().filter(mcC->mcC.mcId()==mcFirst.mcId()).findFirst().orElseThrow().mcRequests()).isZero();
        mcNextMonth.mcRecord(mcFirst.mcId(),MCLookupRouter.MCStatus.INVALID,0);assertThat(mcNextMonth.mcCredentials().stream().filter(mcC->mcC.mcId()==mcFirst.mcId()).findFirst().orElseThrow().mcInvalidStreak()).isEqualTo(2);
        assertThat(mcNextMonth.mcCredentials()).hasSize(2);
        mcNextMonth.mcRecord(mcFirst.mcId(),MCLookupRouter.MCStatus.SUCCESS,0);assertThat(mcNextMonth.mcCredentials().stream().filter(mcC->mcC.mcId()==mcFirst.mcId()).findFirst().orElseThrow().mcInvalidStreak()).isZero();
        assertThat(java.nio.file.Files.readString(mcDir.resolve("studio.db"),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("private-factiliza-token","private-apisperu-token");
    }

    @Test void mcRateLimitSkipsOtherKeyOfSameProviderAndOfflineStaysManual()throws Exception{
        Path mcDir=mcTemp.resolve("rate");new MCDocumentStore(mcDir);MCApiCredentialStore mcStore=new MCApiCredentialStore(mcDir);
        mcStore.mcAdd("F1","Factiliza","token-one");mcStore.mcAdd("F2","Factiliza","token-two");mcStore.mcAdd("P1","APIsPERU","token-three");
        AtomicInteger mcFactCalls=new AtomicInteger();
        MCLookupProvider mcLimited=new MCLookupProvider(){public String mcName(){return "Factiliza";}public Set<String> mcServices(){return Set.of("DNI","RUC");}public Map<String,String> mcQuery(String mcS,String mcN,String mcT)throws Exception{mcFactCalls.incrementAndGet();throw new MCFactilizaClient.MCHttpStatusException(429,120,"limit");}};
        MCLookupProvider mcAlternative=new MCLookupProvider(){public String mcName(){return "APIsPERU";}public Set<String> mcServices(){return Set.of("DNI","RUC");}public Map<String,String> mcQuery(String mcS,String mcN,String mcT){return Map.of("ruc",mcN,"razonSocial","EMPRESA");}};
        MCLookupRouter mcRouter=new MCLookupRouter(mcStore,List.of(mcLimited,mcAlternative));assertThat(mcRouter.mcQuery("RUC","20123456789").mcProvider()).isEqualTo("APIsPERU");assertThat(mcFactCalls).hasValue(1);
        for(MCApiCredentialStore.MCCredential mcC:mcStore.mcCredentials())mcStore.mcEnabled(mcC.mcId(),false);
        MCLookupRouter.MCResult mcOffline=mcRouter.mcQuery("RUC","20123456789");assertThat(mcOffline.mcStatus()).isEqualTo(MCLookupRouter.MCStatus.NO_CREDENTIAL);assertThat(mcOffline.mcValues()).isEmpty();
    }

    @Test void mcDocumentedFactilizaResponseAndPartialApisPeruStayEditable()throws Exception{
        Path mcDir=mcTemp.resolve("responses");new MCDocumentStore(mcDir);MCApiCredentialStore mcVault=new MCApiCredentialStore(mcDir);
        MCApiCredentialStore.MCCredential mcFirst=mcVault.mcAdd("F","Factiliza","test-only");mcVault.mcAdd("P","APIsPERU","test-only");
        MCLookupProvider mcFactiliza=new MCLookupProvider(){public String mcName(){return "Factiliza";}public Set<String> mcServices(){return Set.of("DNI","RUC");}public Map<String,String> mcQuery(String mcS,String mcN,String mcT){return Map.of("data.numero",mcN,"data.nombres","ANA","data.apellido_paterno","PEREZ");}};
        MCLookupProvider mcPeru=new MCLookupProvider(){public String mcName(){return "APIsPERU";}public Set<String> mcServices(){return Set.of("DNI","RUC");}public Map<String,String> mcQuery(String mcS,String mcN,String mcT){return Map.of("dni",mcN,"nombres","ANA");}};
        MCLookupRouter.MCResult mcResult=new MCLookupRouter(mcVault,List.of(mcFactiliza,mcPeru)).mcQuery("DNI","12345678");
        assertThat(mcResult.mcProvider()).isEqualTo("Factiliza");assertThat(mcResult.mcValues()).containsEntry("apellido_paterno","PEREZ");assertThat(mcResult.mcUnavailable()).contains("sexo");
        mcVault.mcEnabled(mcFirst.mcId(),false);MCLookupRouter.MCResult mcPartial=new MCLookupRouter(mcVault,List.of(mcFactiliza,mcPeru)).mcQuery("DNI","12345678");
        assertThat(mcPartial.mcStatus()).isEqualTo(MCLookupRouter.MCStatus.PARTIAL);assertThat(mcPartial.mcValues()).doesNotContainKey("apellido_paterno");assertThat(mcPartial.mcUnavailable()).contains("apellido_paterno");
    }

    @Test void mcMultipleGroupsPersistTransferAndDoNotDuplicateTemplate()throws Exception{
        Path mcSource=mcTemp.resolve("groups-source");MCDocumentStore mcStore=new MCDocumentStore(mcSource);MCStudioData mcData=new MCStudioData(mcSource);
        byte[] mcBytes;try(XWPFDocument mcDoc=new XWPFDocument()){mcDoc.createParagraph().createRun().setText("{{nombre}} {{nombre}}");mcBytes=MCDocumentEngine.mcBytes(mcDoc);}
        MCTemplate mcTemplate=mcStore.mcSaveTemplate(new MCTemplate(0,"Carta",mcBytes,mcBytes));MCTemplateGroup mcA=mcStore.mcSaveGroup(0,"Solicitudes"),mcB=mcStore.mcSaveGroup(0,"Frecuentes");
        mcStore.mcSetTemplateGroups(mcTemplate.mcId(),List.of(mcA.mcId(),mcB.mcId()));assertThat(mcStore.mcGroupIds(mcTemplate.mcId())).containsExactlyInAnyOrder(mcA.mcId(),mcB.mcId());
        assertThat(mcStore.mcTemplates()).hasSize(1);mcStore.mcSetTemplateGroups(mcTemplate.mcId(),List.of(mcB.mcId()));assertThat(mcStore.mcGroupIds(mcTemplate.mcId())).containsExactly(mcB.mcId());
        mcStore.mcSetTemplateGroups(mcTemplate.mcId(),List.of(mcA.mcId(),mcB.mcId()));
        Path mcZip=mcTemp.resolve("groups.zip");new MCStudioTransfer(mcStore,mcData).mcExportConfiguration(mcZip);
        Path mcDestination=mcTemp.resolve("groups-destination");MCDocumentStore mcImported=new MCDocumentStore(mcDestination);MCStudioData mcImportedData=new MCStudioData(mcDestination);new MCStudioTransfer(mcImported,mcImportedData).mcImportConfiguration(mcZip,MCStudioTransfer.MCConflict.KEEP);
        assertThat(mcImported.mcTemplates()).hasSize(1);assertThat(mcImported.mcGroupIds(mcImported.mcTemplates().get(0).mcId())).hasSize(2);assertThat(new MCDocumentStore(mcDestination).mcGroupIds(mcImported.mcTemplates().get(0).mcId())).hasSize(2);
        mcImported.mcDeleteGroup(mcImported.mcGroups().get(0).mcId(),null);assertThat(mcImported.mcTemplates()).hasSize(1);
        assertThat(MCDocumentEngine.mcGenerate(mcBytes,Map.of("nombre","Ana"))).isNotEmpty();
    }

    @Test void mcEncryptedBackupAndOrdinaryTransferExcludeCredentialTokens()throws Exception{
        Path mcSource=mcTemp.resolve("backup-source"),mcDestination=mcTemp.resolve("backup-destination");MCDocumentStore mcStore=new MCDocumentStore(mcSource);MCStudioData mcData=new MCStudioData(mcSource);MCApiCredentialStore mcVault=new MCApiCredentialStore(mcSource);
        MCApiCredentialStore.MCCredential mcCredential=mcVault.mcAdd("Cuenta privada","APIsPERU","token-super-privado");
        Path mcConfig=mcTemp.resolve("ordinary.zip");new MCStudioTransfer(mcStore,mcData).mcExportConfiguration(mcConfig);
        assertThat(java.nio.file.Files.readString(mcConfig,java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("token-super-privado");
        Path mcBackup=mcTemp.resolve("full.dsbk");new MCStudioTransfer(mcStore,mcData).mcBackup(mcBackup,"password-safe".toCharArray());
        assertThat(java.nio.file.Files.readString(mcBackup,java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("token-super-privado");
        new MCDocumentStore(mcDestination);MCStudioData mcDestinationData=new MCStudioData(mcDestination);new MCStudioTransfer(new MCDocumentStore(mcDestination),mcDestinationData).mcRestore(mcBackup,"password-safe".toCharArray());
        MCApiCredentialStore mcRestored=new MCApiCredentialStore(mcDestination);assertThat(mcRestored.mcToken(mcCredential.mcId())).isNull();assertThat(mcVault.mcToken(mcCredential.mcId())).isEqualTo("token-super-privado");
    }

    @Test void mcLegacyGroupAssociationMigratesWithoutChangingTemplate()throws Exception{
        Path mcDir=mcTemp.resolve("legacy-group");MCDocumentStore mcInitial=new MCDocumentStore(mcDir);
        byte[] mcBytes;try(XWPFDocument mcDoc=new XWPFDocument()){mcDoc.createParagraph().createRun().setText("Plantilla");mcBytes=MCDocumentEngine.mcBytes(mcDoc);}
        MCTemplate mcTemplate=mcInitial.mcSaveTemplate(new MCTemplate(0,"Anterior",mcBytes,mcBytes));MCTemplateGroup mcGroup=mcInitial.mcSaveGroup(0,"Anterior");
        try(var mcC=java.sql.DriverManager.getConnection("jdbc:sqlite:"+mcDir.resolve("studio.db"));var mcS=mcC.prepareStatement("INSERT INTO template_group_member(template_id,group_id) VALUES(?,?)")){mcS.setLong(1,mcTemplate.mcId());mcS.setLong(2,mcGroup.mcId());mcS.executeUpdate();}
        MCDocumentStore mcMigrated=new MCDocumentStore(mcDir);assertThat(mcMigrated.mcGroupIds(mcTemplate.mcId())).containsExactly(mcGroup.mcId());assertThat(mcMigrated.mcTemplates()).hasSize(1);
        assertThat(new MCDocumentStore(mcDir).mcGroupIds(mcTemplate.mcId())).containsExactly(mcGroup.mcId());
    }

    @Test void mcIndependentApiContextsSurviveTransferAndNeverCrossFill()throws Exception{
        Path mcSource=mcTemp.resolve("contexts-source");MCDocumentStore mcStore=new MCDocumentStore(mcSource);MCStudioData mcData=new MCStudioData(mcSource);
        byte[] mcBytes;try(XWPFDocument mcDoc=new XWPFDocument()){mcDoc.createParagraph().createRun().setText("{{dni_1}} {{nombre_1}} {{dni_2}} {{nombre_2}}");mcBytes=MCDocumentEngine.mcBytes(mcDoc);}
        MCTemplate mcTemplate=mcStore.mcSaveTemplate(new MCTemplate(0,"Dos personas",mcBytes,mcBytes));
        for(String mcKey:List.of("dni_1","nombre_1","dni_2","nombre_2"))mcStore.mcSaveVariable(new MCVariable(0,mcTemplate.mcId(),mcKey,mcKey,"Texto corto","",false,1));
        for(int mcI=1;mcI<=2;mcI++){mcData.mcSaveApiContext(new MCStudioData.MCApiContext(mcTemplate.mcId(),"dni_"+mcI,"persona_"+mcI,"DNI",true));mcData.mcSaveApiContext(new MCStudioData.MCApiContext(mcTemplate.mcId(),"nombre_"+mcI,"persona_"+mcI,"DNI",false));}
        assertThat(MCStudioWindow.mcSameQueryGroup("persona_1","persona_2",2,"dni_1","nombre_2")).isFalse();
        assertThat(MCStudioWindow.mcSameQueryGroup("persona_1","persona_1",2,"dni_1","nombre_1")).isTrue();
        Path mcZip=mcTemp.resolve("contexts.zip");new MCStudioTransfer(mcStore,mcData).mcExportConfiguration(mcZip);
        Path mcTarget=mcTemp.resolve("contexts-target");MCDocumentStore mcImported=new MCDocumentStore(mcTarget);MCStudioData mcImportedData=new MCStudioData(mcTarget);
        new MCStudioTransfer(mcImported,mcImportedData).mcImportConfiguration(mcZip,MCStudioTransfer.MCConflict.KEEP);
        assertThat(mcImportedData.mcApiContexts(mcImported.mcTemplates().get(0).mcId())).hasSize(4);
        assertThat(new MCStudioData(mcTarget).mcApiContexts(mcImported.mcTemplates().get(0).mcId())).hasSize(4);
    }
}
