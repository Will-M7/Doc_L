package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.MCDocumentStore.*;
import com.ejemplo.crudpersona.studio.MCStudioData.MCHistory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** @author MC */
class MCHistoryRetentionTest {
    @TempDir Path mcTemp;

    @Test void mcMigratesLegacyHistoryAndDeletingTemplateRetainsEverySnapshotAndOtherTemplateLink()throws Exception{
        for(boolean mcRestrictive:List.of(false,true)){
            Path mcDirectory=mcTemp.resolve(mcRestrictive?"restrict":"not-null");MCDocumentStore mcStore=new MCDocumentStore(mcDirectory);
            byte[] mcDocx;try(XWPFDocument mcDocument=new XWPFDocument()){mcDocument.createParagraph().createRun().setText("Documento conservado");mcDocx=MCDocumentEngine.mcBytes(mcDocument);}
            byte[] mcPdf=MCDocxPdfExporter.mcRender(mcDocx);MCTemplate mcRemoved=mcStore.mcSaveTemplate(new MCTemplate(0,"Borrable",mcDocx,mcDocx)),mcRetained=mcStore.mcSaveTemplate(new MCTemplate(0,"Conservada",mcDocx,mcDocx));
            try(Connection mcC=DriverManager.getConnection("jdbc:sqlite:"+mcDirectory.resolve("studio.db"));Statement mcS=mcC.createStatement()){
                mcS.execute("PRAGMA foreign_keys=ON");
                mcS.executeUpdate("CREATE TABLE document_history(id INTEGER PRIMARY KEY,title TEXT NOT NULL,template_id INTEGER NOT NULL"+(mcRestrictive?" REFERENCES template(id) ON DELETE RESTRICT":"")+",template_version INTEGER NOT NULL,generated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,values_json TEXT NOT NULL,docx_path TEXT,pdf_path TEXT,docx BLOB NOT NULL,pdf BLOB)");
                mcS.executeUpdate("CREATE INDEX ix_history_title ON document_history(title)");
                try(PreparedStatement mcInsert=mcC.prepareStatement("INSERT INTO document_history(id,title,template_id,template_version,generated_at,values_json,docx_path,pdf_path,docx,pdf) VALUES(?, ?, ?,7,'2026-11-01 12:34:56','{\"nombre\":\"Ana\"}','/documentos/carta.docx','/documentos/carta.pdf',?,?)")){
                    for(long mcId:List.of(mcRemoved.mcId(),mcRetained.mcId())){mcInsert.setLong(1,mcId);mcInsert.setString(2,"Carta "+mcId);mcInsert.setLong(3,mcId);mcInsert.setBytes(4,mcDocx);mcInsert.setBytes(5,mcPdf);mcInsert.executeUpdate();}
                }
            }
            MCStudioData mcData=new MCStudioData(mcDirectory);MCHistory mcBefore=mcData.mcHistory().stream().filter(mcH->mcH.mcTemplateId().equals(mcRemoved.mcId())).findFirst().orElseThrow();
            if(mcRestrictive){mcStore.mcSaveKind(new MCKind(0,"Configuración antigua","","",mcRemoved.mcId(),"",true));mcStore.mcDeleteTemplateWithLegacyKinds(mcRemoved.mcId());}
            else mcStore.mcDeleteTemplate(mcRemoved.mcId());
            MCStudioData mcReopened=new MCStudioData(mcDirectory);List<MCHistory> mcHistory=mcReopened.mcHistory();assertThat(mcHistory).hasSize(2);
            MCHistory mcAfter=mcHistory.stream().filter(mcH->mcH.mcId()==mcBefore.mcId()).findFirst().orElseThrow();assertThat(mcAfter.mcTemplateId()).isNull();
            assertThat(mcAfter.mcTitle()).isEqualTo(mcBefore.mcTitle());assertThat(mcAfter.mcGeneratedAt()).isEqualTo(mcBefore.mcGeneratedAt());assertThat(mcAfter.mcTemplateVersion()).isEqualTo(7);assertThat(mcAfter.mcValuesJson()).isEqualTo(mcBefore.mcValuesJson());assertThat(mcAfter.mcDocxPath()).isEqualTo(mcBefore.mcDocxPath());assertThat(mcAfter.mcPdfPath()).isEqualTo(mcBefore.mcPdfPath());assertThat(mcAfter.mcDocx()).containsExactly(mcDocx);assertThat(mcAfter.mcPdf()).containsExactly(mcPdf);
            assertThat(mcHistory.stream().filter(mcH->mcH.mcId()!=mcAfter.mcId()).findFirst().orElseThrow().mcTemplateId()).isEqualTo(mcRetained.mcId());assertThat(mcStore.mcTemplates()).extracting(MCTemplate::mcId).containsExactly(mcRetained.mcId());
            MCHistory mcCopy=mcReopened.mcSaveHistory(new MCHistory(0,mcAfter.mcTitle(),mcAfter.mcTemplateId(),mcAfter.mcTemplateVersion(),null,mcAfter.mcValuesJson(),null,null,mcAfter.mcDocx(),mcAfter.mcPdf()));assertThat(mcCopy.mcTemplateId()).isNull();assertThat(mcReopened.mcHistory()).hasSize(3);
            try(Connection mcC=DriverManager.getConnection("jdbc:sqlite:"+mcDirectory.resolve("studio.db"));Statement mcS=mcC.createStatement()){
                try(ResultSet mcCheck=mcS.executeQuery("PRAGMA foreign_key_check(document_history)")){assertThat(mcCheck.next()).isFalse();}
                try(ResultSet mcIndex=mcS.executeQuery("SELECT count(*) FROM sqlite_master WHERE name='ix_history_title'")){assertThat(mcIndex.next()).isTrue();assertThat(mcIndex.getInt(1)).isEqualTo(1);}
            }
        }
    }

    @Test void mcDirectSqlDeleteLeavesHistoryDetachedAndReadable()throws Exception{
        MCDocumentStore mcStore=new MCDocumentStore(mcTemp);byte[] mcDocx;try(XWPFDocument mcDocument=new XWPFDocument()){mcDocx=MCDocumentEngine.mcBytes(mcDocument);}
        MCTemplate mcTemplate=mcStore.mcSaveTemplate(new MCTemplate(0,"Plantilla",mcDocx,mcDocx));MCStudioData mcData=new MCStudioData(mcTemp);mcData.mcSaveHistory(new MCHistory(0,"Generado",mcTemplate.mcId(),1,null,"{}",null,null,mcDocx,null));
        try(Connection mcC=DriverManager.getConnection("jdbc:sqlite:"+mcData.mcDatabase());Statement mcS=mcC.createStatement()){mcS.execute("PRAGMA foreign_keys=ON");mcS.executeUpdate("DELETE FROM template WHERE id="+mcTemplate.mcId());}
        MCHistory mcHistory=new MCStudioData(mcTemp).mcHistory().get(0);assertThat(mcHistory.mcTemplateId()).isNull();assertThat(mcHistory.mcDocx()).containsExactly(mcDocx);
    }
}
