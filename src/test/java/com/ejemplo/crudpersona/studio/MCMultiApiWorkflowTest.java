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

class MCMultiApiWorkflowTest {
    @TempDir Path MCtemp;
    private static Clock MCclock(String MCdate){return Clock.fixed(LocalDate.parse(MCdate).atStartOfDay(ZoneId.systemDefault()).toInstant(),ZoneId.systemDefault());}

    @Test void MCnormalizesDocumentedExamplesWithoutInventingAbsentFields()throws Exception{
        String MCfactiliza="{\"status\":200,\"success\":true,\"data\":{\"numero\":\"12345678\",\"nombres\":\"ANA\",\"apellido_paterno\":\"PEREZ\",\"nombre_completo\":\"PEREZ, ANA\",\"ubigeo\":[\"15\",\"1501\",\"150101\"],\"fecha_nacimiento\":null}}";
        Map<String,String> MCraw=MCFactilizaClient.MCparse("DNI","12345678",200,MCfactiliza).MCdata();
        assertThat(MCApiFieldCatalog.MCnormalize("Factiliza","DNI",MCraw)).containsEntry("nombre_completo","ANA PEREZ").containsEntry("ubigeo","150101").doesNotContainKey("fecha_nacimiento");
        String MCperu="{\"dni\":\"12345678\",\"nombres\":\"ANA\",\"apellidoPaterno\":\"PEREZ\",\"apellidoMaterno\":\"RUIZ\",\"codVerifica\":\"1\"}";
        assertThat(MCApiFieldCatalog.MCnormalize("APIsPERU","DNI",MCApisPeruProvider.MCparse("DNI",200,MCperu,0))).containsEntry("apellido_paterno","PEREZ").doesNotContainKey("direccion").containsEntry("nombre_completo","ANA PEREZ RUIZ");
        assertThat(MCApiFieldCatalog.MCcanonical("DNI","data.apellido_paterno")).isEqualTo("apellido_paterno");
        assertThat(MCApiFieldCatalog.MCcanonical("DNI","data.campo_desconocido")).startsWith("legacy:");
    }

    @Test void MCfullNamesUseGivenThenFamilyForBothProvidersAndCommaOnlyResponses()throws Exception{
        for(String MCprovider:List.of("Factiliza","APIsPERU")){
            String MCprefix="Factiliza".equals(MCprovider)?"data.":"";
            Map<String,String> MCseparate=new LinkedHashMap<>();MCseparate.put(MCprefix+"nombres","  JUAN   CARLOS ");MCseparate.put(MCprefix+("Factiliza".equals(MCprovider)?"apellido_paterno":"apellidoPaterno"),"QUISPE");MCseparate.put(MCprefix+("Factiliza".equals(MCprovider)?"apellido_materno":"apellidoMaterno"),"MAMANI");MCseparate.put(MCprefix+"nombre_completo","QUISPE MAMANI, JUAN CARLOS");
            Map<String,String> MCvalues=MCApiFieldCatalog.MCnormalize(MCprovider,"DNI",MCseparate);assertThat(MCvalues).containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI").containsEntry("nombres","JUAN CARLOS").containsEntry("apellido_paterno","QUISPE").containsEntry("apellido_materno","MAMANI");
            Map<String,String> MCfallback=MCApiFieldCatalog.MCnormalize(MCprovider,"DNI",Map.of(MCprefix+"nombre_completo","  QUISPE  MAMANI,  JUAN   CARLOS  "));assertThat(MCfallback).containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI");
            MCLookupRouter.MCResult MCresult=new MCLookupRouter.MCResult("DNI","12345678",MCprovider,MCfallback,MCfallback.keySet(),Set.of(),MCLookupRouter.MCStatus.SUCCESS,"",Map.of(MCprefix+"nombre_completo","QUISPE MAMANI, JUAN CARLOS"));
            new MCDocumentStore(MCtemp.resolve(MCprovider), false);MCStudioData MCdata=new MCStudioData(MCtemp.resolve(MCprovider));MCdata.MCupsertContact(MCContactReuse.MCfromApi(MCresult));var MCcontact=MCdata.MCcontacts().get(0);assertThat(MCcontact.MCgiven()).isEqualTo("JUAN CARLOS");assertThat(MCcontact.MCfamily()).isEqualTo("QUISPE MAMANI");assertThat(MCContactReuse.MCapiValues(MCcontact)).containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI");
        }
        Map<String,String> MCparsed=MCApisPeruProvider.MCparse("DNI",200,"{\"dni\":\"12345678\",\"nombre_completo\":\"QUISPE MAMANI, JUAN CARLOS\"}",0);assertThat(MCparsed).containsEntry("nombre_completo","JUAN CARLOS QUISPE MAMANI");
    }

    @Test void MCfallsBackAfterTimeoutTracksMonthlyUsageAndKeepsInvalidCredentials()throws Exception{
        Path MCdir=MCtemp.resolve("router");new MCDocumentStore(MCdir, false);MCApiCredentialStore MCstore=new MCApiCredentialStore(MCdir,MCclock("2026-11-12"));
        MCApiCredentialStore.MCCredential MCfirst=MCstore.MCadd("Principal","Factiliza","private-factiliza-token");
        MCApiCredentialStore.MCCredential MCsecond=MCstore.MCadd("Respaldo","APIsPERU","private-apisperu-token");
        AtomicInteger MCfirstCalls=new AtomicInteger(),MCsecondCalls=new AtomicInteger();
        MCLookupProvider MCfactiliza=new MCLookupProvider(){public String MCname(){return "Factiliza";}public Set<String> MCservices(){return Set.of("DNI","RUC");}public Map<String,String> MCquery(String MCservice,String MCnumber,String MCtoken)throws Exception{MCfirstCalls.incrementAndGet();throw new HttpTimeoutException("timeout");}};
        MCLookupProvider MCperu=new MCLookupProvider(){public String MCname(){return "APIsPERU";}public Set<String> MCservices(){return Set.of("DNI","RUC");}public Map<String,String> MCquery(String MCservice,String MCnumber,String MCtoken){MCsecondCalls.incrementAndGet();return Map.of("dni",MCnumber,"nombres","ANA");}};
        MCLookupRouter MCrouter=new MCLookupRouter(MCstore,List.of(MCfactiliza,MCperu));MCLookupRouter.MCResult MCresult=MCrouter.MCquery("DNI","12345678");
        assertThat(MCresult.MCprovider()).isEqualTo("APIsPERU");assertThat(MCresult.MCvalues()).containsEntry("nombres","ANA");assertThat(MCfirstCalls).hasValue(1);assertThat(MCsecondCalls).hasValue(1);
        assertThat(MCstore.MCcredentials().stream().filter(MCc->MCc.MCid()==MCfirst.MCid()).findFirst().orElseThrow().MCrequests()).isEqualTo(1);
        assertThat(MCstore.MCcredentials().stream().filter(MCc->MCc.MCid()==MCsecond.MCid()).findFirst().orElseThrow().MCsuccesses()).isEqualTo(1);
        MCstore.MCrecord(MCfirst.MCid(),MCLookupRouter.MCStatus.INVALID,0);assertThat(MCstore.MCcredentials()).hasSize(2);
        MCApiCredentialStore MCnextMonth=new MCApiCredentialStore(MCdir,MCclock("2026-12-01"));assertThat(MCnextMonth.MCcredentials().stream().filter(MCc->MCc.MCid()==MCfirst.MCid()).findFirst().orElseThrow().MCrequests()).isZero();
        MCnextMonth.MCrecord(MCfirst.MCid(),MCLookupRouter.MCStatus.INVALID,0);assertThat(MCnextMonth.MCcredentials().stream().filter(MCc->MCc.MCid()==MCfirst.MCid()).findFirst().orElseThrow().MCinvalidStreak()).isEqualTo(2);
        assertThat(MCnextMonth.MCcredentials()).hasSize(2);
        MCnextMonth.MCrecord(MCfirst.MCid(),MCLookupRouter.MCStatus.SUCCESS,0);assertThat(MCnextMonth.MCcredentials().stream().filter(MCc->MCc.MCid()==MCfirst.MCid()).findFirst().orElseThrow().MCinvalidStreak()).isZero();
        assertThat(java.nio.file.Files.readString(MCdir.resolve("studio.db"),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("private-factiliza-token","private-apisperu-token");
    }

    @Test void MCrateLimitSkipsOtherKeyOfSameProviderAndOfflineStaysManual()throws Exception{
        Path MCdir=MCtemp.resolve("rate");new MCDocumentStore(MCdir, false);MCApiCredentialStore MCstore=new MCApiCredentialStore(MCdir);
        MCstore.MCadd("F1","Factiliza","token-one");MCstore.MCadd("F2","Factiliza","token-two");MCstore.MCadd("P1","APIsPERU","token-three");
        AtomicInteger MCfactCalls=new AtomicInteger();
        MCLookupProvider MClimited=new MCLookupProvider(){public String MCname(){return "Factiliza";}public Set<String> MCservices(){return Set.of("DNI","RUC");}public Map<String,String> MCquery(String MCs,String MCn,String MCt)throws Exception{MCfactCalls.incrementAndGet();throw new MCFactilizaClient.MCHttpStatusException(429,120,"limit");}};
        MCLookupProvider MCalternative=new MCLookupProvider(){public String MCname(){return "APIsPERU";}public Set<String> MCservices(){return Set.of("DNI","RUC");}public Map<String,String> MCquery(String MCs,String MCn,String MCt){return Map.of("ruc",MCn,"razonSocial","EMPRESA");}};
        MCLookupRouter MCrouter=new MCLookupRouter(MCstore,List.of(MClimited,MCalternative));assertThat(MCrouter.MCquery("RUC","20123456789").MCprovider()).isEqualTo("APIsPERU");assertThat(MCfactCalls).hasValue(1);
        for(MCApiCredentialStore.MCCredential MCc:MCstore.MCcredentials())MCstore.MCenabled(MCc.MCid(),false);
        MCLookupRouter.MCResult MCoffline=MCrouter.MCquery("RUC","20123456789");assertThat(MCoffline.MCstatus()).isEqualTo(MCLookupRouter.MCStatus.NO_CREDENTIAL);assertThat(MCoffline.MCvalues()).isEmpty();
    }

    @Test void MCdocumentedFactilizaResponseAndPartialApisPeruStayEditable()throws Exception{
        Path MCdir=MCtemp.resolve("responses");new MCDocumentStore(MCdir, false);MCApiCredentialStore MCvault=new MCApiCredentialStore(MCdir);
        MCApiCredentialStore.MCCredential MCfirst=MCvault.MCadd("F","Factiliza","test-only");MCvault.MCadd("P","APIsPERU","test-only");
        MCLookupProvider MCfactiliza=new MCLookupProvider(){public String MCname(){return "Factiliza";}public Set<String> MCservices(){return Set.of("DNI","RUC");}public Map<String,String> MCquery(String MCs,String MCn,String MCt){return Map.of("data.numero",MCn,"data.nombres","ANA","data.apellido_paterno","PEREZ");}};
        MCLookupProvider MCperu=new MCLookupProvider(){public String MCname(){return "APIsPERU";}public Set<String> MCservices(){return Set.of("DNI","RUC");}public Map<String,String> MCquery(String MCs,String MCn,String MCt){return Map.of("dni",MCn,"nombres","ANA");}};
        MCLookupRouter.MCResult MCresult=new MCLookupRouter(MCvault,List.of(MCfactiliza,MCperu)).MCquery("DNI","12345678");
        assertThat(MCresult.MCprovider()).isEqualTo("Factiliza");assertThat(MCresult.MCvalues()).containsEntry("apellido_paterno","PEREZ");assertThat(MCresult.MCunavailable()).contains("sexo");
        MCvault.MCenabled(MCfirst.MCid(),false);MCLookupRouter.MCResult MCpartial=new MCLookupRouter(MCvault,List.of(MCfactiliza,MCperu)).MCquery("DNI","12345678");
        assertThat(MCpartial.MCstatus()).isEqualTo(MCLookupRouter.MCStatus.PARTIAL);assertThat(MCpartial.MCvalues()).doesNotContainKey("apellido_paterno");assertThat(MCpartial.MCunavailable()).contains("apellido_paterno");
    }

    @Test void MCmultipleGroupsPersistTransferAndDoNotDuplicateTemplate()throws Exception{
        Path MCsource=MCtemp.resolve("groups-source");MCDocumentStore MCstore=new MCDocumentStore(MCsource, false);MCStudioData MCdata=new MCStudioData(MCsource);
        byte[] MCbytes;try(XWPFDocument MCdoc=new XWPFDocument()){MCdoc.createParagraph().createRun().setText("{{nombre}} {{nombre}}");MCbytes=MCDocumentEngine.MCbytes(MCdoc);}
        MCTemplate MCtemplate=MCstore.MCsaveTemplate(new MCTemplate(0,"Carta",MCbytes,MCbytes));MCTemplateGroup MCa=MCstore.MCsaveGroup(0,"Solicitudes"),MCb=MCstore.MCsaveGroup(0,"Frecuentes");
        MCstore.MCsetTemplateGroups(MCtemplate.MCid(),List.of(MCa.MCid(),MCb.MCid()));assertThat(MCstore.MCgroupIds(MCtemplate.MCid())).containsExactlyInAnyOrder(MCa.MCid(),MCb.MCid());
        assertThat(MCstore.MCtemplates()).hasSize(1);MCstore.MCsetTemplateGroups(MCtemplate.MCid(),List.of(MCb.MCid()));assertThat(MCstore.MCgroupIds(MCtemplate.MCid())).containsExactly(MCb.MCid());
        MCstore.MCsetTemplateGroups(MCtemplate.MCid(),List.of(MCa.MCid(),MCb.MCid()));
        Path MCzip=MCtemp.resolve("groups.zip");new MCStudioTransfer(MCstore,MCdata).MCexportConfiguration(MCzip);
        Path MCdestination=MCtemp.resolve("groups-destination");MCDocumentStore MCimported=new MCDocumentStore(MCdestination, false);MCStudioData MCimportedData=new MCStudioData(MCdestination);new MCStudioTransfer(MCimported,MCimportedData).MCimportConfiguration(MCzip,MCStudioTransfer.MCConflict.KEEP);
        assertThat(MCimported.MCtemplates()).hasSize(1);assertThat(MCimported.MCgroupIds(MCimported.MCtemplates().get(0).MCid())).hasSize(2);assertThat(new MCDocumentStore(MCdestination, false).MCgroupIds(MCimported.MCtemplates().get(0).MCid())).hasSize(2);
        MCimported.MCdeleteGroup(MCimported.MCgroups().get(0).MCid(),null);assertThat(MCimported.MCtemplates()).hasSize(1);
        assertThat(MCDocumentEngine.MCgenerate(MCbytes,Map.of("nombre","Ana"))).isNotEmpty();
    }

    @Test void MCencryptedBackupAndOrdinaryTransferExcludeCredentialTokens()throws Exception{
        Path MCsource=MCtemp.resolve("backup-source"),MCdestination=MCtemp.resolve("backup-destination");MCDocumentStore MCstore=new MCDocumentStore(MCsource, false);MCStudioData MCdata=new MCStudioData(MCsource);MCApiCredentialStore MCvault=new MCApiCredentialStore(MCsource);
        MCApiCredentialStore.MCCredential MCcredential=MCvault.MCadd("Cuenta privada","APIsPERU","token-super-privado");
        Path MCconfig=MCtemp.resolve("ordinary.zip");new MCStudioTransfer(MCstore,MCdata).MCexportConfiguration(MCconfig);
        assertThat(java.nio.file.Files.readString(MCconfig,java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("token-super-privado");
        Path MCbackup=MCtemp.resolve("full.dsbk");new MCStudioTransfer(MCstore,MCdata).MCbackup(MCbackup,"password-safe".toCharArray());
        assertThat(java.nio.file.Files.readString(MCbackup,java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("token-super-privado");
        new MCDocumentStore(MCdestination, false);MCStudioData MCdestinationData=new MCStudioData(MCdestination);new MCStudioTransfer(new MCDocumentStore(MCdestination, false),MCdestinationData).MCrestore(MCbackup,"password-safe".toCharArray());
        MCApiCredentialStore MCrestored=new MCApiCredentialStore(MCdestination);assertThat(MCrestored.MCtoken(MCcredential.MCid())).isNull();assertThat(MCvault.MCtoken(MCcredential.MCid())).isEqualTo("token-super-privado");
    }

    @Test void MClegacyGroupAssociationMigratesWithoutChangingTemplate()throws Exception{
        Path MCdir=MCtemp.resolve("legacy-group");MCDocumentStore MCinitial=new MCDocumentStore(MCdir, false);
        byte[] MCbytes;try(XWPFDocument MCdoc=new XWPFDocument()){MCdoc.createParagraph().createRun().setText("Plantilla");MCbytes=MCDocumentEngine.MCbytes(MCdoc);}
        MCTemplate MCtemplate=MCinitial.MCsaveTemplate(new MCTemplate(0,"Anterior",MCbytes,MCbytes));MCTemplateGroup MCgroup=MCinitial.MCsaveGroup(0,"Anterior");
        try(var MCc=java.sql.DriverManager.getConnection("jdbc:sqlite:"+MCdir.resolve("studio.db"));var MCs=MCc.prepareStatement("INSERT INTO template_group_member(template_id,group_id) VALUES(?,?)")){MCs.setLong(1,MCtemplate.MCid());MCs.setLong(2,MCgroup.MCid());MCs.executeUpdate();}
        MCDocumentStore MCmigrated=new MCDocumentStore(MCdir, false);assertThat(MCmigrated.MCgroupIds(MCtemplate.MCid())).containsExactly(MCgroup.MCid());assertThat(MCmigrated.MCtemplates()).hasSize(1);
        assertThat(new MCDocumentStore(MCdir, false).MCgroupIds(MCtemplate.MCid())).containsExactly(MCgroup.MCid());
    }

    @Test void MCindependentApiContextsSurviveTransferAndNeverCrossFill()throws Exception{
        Path MCsource=MCtemp.resolve("contexts-source");MCDocumentStore MCstore=new MCDocumentStore(MCsource, false);MCStudioData MCdata=new MCStudioData(MCsource);
        byte[] MCbytes;try(XWPFDocument MCdoc=new XWPFDocument()){MCdoc.createParagraph().createRun().setText("{{dni_1}} {{nombre_1}} {{dni_2}} {{nombre_2}}");MCbytes=MCDocumentEngine.MCbytes(MCdoc);}
        MCTemplate MCtemplate=MCstore.MCsaveTemplate(new MCTemplate(0,"Dos personas",MCbytes,MCbytes));
        for(String MCkey:List.of("dni_1","nombre_1","dni_2","nombre_2"))MCstore.MCsaveVariable(new MCVariable(0,MCtemplate.MCid(),MCkey,MCkey,"Texto corto","",false,1));
        for(int MCi=1;MCi<=2;MCi++){MCdata.MCsaveApiContext(new MCStudioData.MCApiContext(MCtemplate.MCid(),"dni_"+MCi,"persona_"+MCi,"DNI",true));MCdata.MCsaveApiContext(new MCStudioData.MCApiContext(MCtemplate.MCid(),"nombre_"+MCi,"persona_"+MCi,"DNI",false));}
        assertThat(MCStudioWindow.MCsameQueryGroup("persona_1","persona_2",2,"dni_1","nombre_2")).isFalse();
        assertThat(MCStudioWindow.MCsameQueryGroup("persona_1","persona_1",2,"dni_1","nombre_1")).isTrue();
        Path MCzip=MCtemp.resolve("contexts.zip");new MCStudioTransfer(MCstore,MCdata).MCexportConfiguration(MCzip);
        Path MCtarget=MCtemp.resolve("contexts-target");MCDocumentStore MCimported=new MCDocumentStore(MCtarget, false);MCStudioData MCimportedData=new MCStudioData(MCtarget);
        new MCStudioTransfer(MCimported,MCimportedData).MCimportConfiguration(MCzip,MCStudioTransfer.MCConflict.KEEP);
        assertThat(MCimportedData.MCapiContexts(MCimported.MCtemplates().get(0).MCid())).hasSize(4);
        assertThat(new MCStudioData(MCtarget).MCapiContexts(MCimported.MCtemplates().get(0).MCid())).hasSize(4);
    }
}
