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

class MCHistoryRetentionTest {
    @TempDir Path MCtemp;

    @Test void MCmigratesLegacyHistoryAndDeletingTemplateRetainsEverySnapshotAndOtherTemplateLink()throws Exception{
        for(boolean MCrestrictive:List.of(false,true)){
            Path MCdirectory=MCtemp.resolve(MCrestrictive?"restrict":"not-null");MCDocumentStore MCstore=new MCDocumentStore(MCdirectory);
            byte[] MCdocx;try(XWPFDocument MCdocument=new XWPFDocument()){MCdocument.createParagraph().createRun().setText("Documento conservado");MCdocx=MCDocumentEngine.MCbytes(MCdocument);}
            byte[] MCpdf=MCDocxPdfExporter.MCrender(MCdocx);MCTemplate MCremoved=MCstore.MCsaveTemplate(new MCTemplate(0,"Borrable",MCdocx,MCdocx)),MCretained=MCstore.MCsaveTemplate(new MCTemplate(0,"Conservada",MCdocx,MCdocx));
            try(Connection MCc=DriverManager.getConnection("jdbc:sqlite:"+MCdirectory.resolve("studio.db"));Statement MCs=MCc.createStatement()){
                MCs.execute("PRAGMA foreign_keys=ON");
                MCs.executeUpdate("CREATE TABLE document_history(id INTEGER PRIMARY KEY,title TEXT NOT NULL,template_id INTEGER NOT NULL"+(MCrestrictive?" REFERENCES template(id) ON DELETE RESTRICT":"")+",template_version INTEGER NOT NULL,generated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,values_json TEXT NOT NULL,docx_path TEXT,pdf_path TEXT,docx BLOB NOT NULL,pdf BLOB)");
                MCs.executeUpdate("CREATE INDEX ix_history_title ON document_history(title)");
                try(PreparedStatement MCinsert=MCc.prepareStatement("INSERT INTO document_history(id,title,template_id,template_version,generated_at,values_json,docx_path,pdf_path,docx,pdf) VALUES(?, ?, ?,7,'2026-11-01 12:34:56','{\"nombre\":\"Ana\"}','/documentos/carta.docx','/documentos/carta.pdf',?,?)")){
                    for(long MCid:List.of(MCremoved.MCid(),MCretained.MCid())){MCinsert.setLong(1,MCid);MCinsert.setString(2,"Carta "+MCid);MCinsert.setLong(3,MCid);MCinsert.setBytes(4,MCdocx);MCinsert.setBytes(5,MCpdf);MCinsert.executeUpdate();}
                }
            }
            MCStudioData MCdata=new MCStudioData(MCdirectory);MCHistory MCbefore=MCdata.MChistory().stream().filter(MCh->MCh.MCtemplateId().equals(MCremoved.MCid())).findFirst().orElseThrow();
            if(MCrestrictive){MCstore.MCsaveKind(new MCKind(0,"Configuración antigua","","",MCremoved.MCid(),"",true));MCstore.MCdeleteTemplateWithLegacyKinds(MCremoved.MCid());}
            else MCstore.MCdeleteTemplate(MCremoved.MCid());
            MCStudioData MCreopened=new MCStudioData(MCdirectory);List<MCHistory> MChistory=MCreopened.MChistory();assertThat(MChistory).hasSize(2);
            MCHistory MCafter=MChistory.stream().filter(MCh->MCh.MCid()==MCbefore.MCid()).findFirst().orElseThrow();assertThat(MCafter.MCtemplateId()).isNull();
            assertThat(MCafter.MCtitle()).isEqualTo(MCbefore.MCtitle());assertThat(MCafter.MCgeneratedAt()).isEqualTo(MCbefore.MCgeneratedAt());assertThat(MCafter.MCtemplateVersion()).isEqualTo(7);assertThat(MCafter.MCvaluesJson()).isEqualTo(MCbefore.MCvaluesJson());assertThat(MCafter.MCdocxPath()).isEqualTo(MCbefore.MCdocxPath());assertThat(MCafter.MCpdfPath()).isEqualTo(MCbefore.MCpdfPath());assertThat(MCafter.MCdocx()).containsExactly(MCdocx);assertThat(MCafter.MCpdf()).containsExactly(MCpdf);
            assertThat(MChistory.stream().filter(MCh->MCh.MCid()!=MCafter.MCid()).findFirst().orElseThrow().MCtemplateId()).isEqualTo(MCretained.MCid());assertThat(MCstore.MCtemplates()).extracting(MCTemplate::MCid).containsExactly(MCretained.MCid());
            MCHistory MCcopy=MCreopened.MCsaveHistory(new MCHistory(0,MCafter.MCtitle(),MCafter.MCtemplateId(),MCafter.MCtemplateVersion(),null,MCafter.MCvaluesJson(),null,null,MCafter.MCdocx(),MCafter.MCpdf()));assertThat(MCcopy.MCtemplateId()).isNull();assertThat(MCreopened.MChistory()).hasSize(3);
            try(Connection MCc=DriverManager.getConnection("jdbc:sqlite:"+MCdirectory.resolve("studio.db"));Statement MCs=MCc.createStatement()){
                try(ResultSet MCcheck=MCs.executeQuery("PRAGMA foreign_key_check(document_history)")){assertThat(MCcheck.next()).isFalse();}
                try(ResultSet MCindex=MCs.executeQuery("SELECT count(*) FROM sqlite_master WHERE name='ix_history_title'")){assertThat(MCindex.next()).isTrue();assertThat(MCindex.getInt(1)).isEqualTo(1);}
            }
        }
    }

    @Test void MCdirectSqlDeleteLeavesHistoryDetachedAndReadable()throws Exception{
        MCDocumentStore MCstore=new MCDocumentStore(MCtemp);byte[] MCdocx;try(XWPFDocument MCdocument=new XWPFDocument()){MCdocx=MCDocumentEngine.MCbytes(MCdocument);}
        MCTemplate MCtemplate=MCstore.MCsaveTemplate(new MCTemplate(0,"Plantilla",MCdocx,MCdocx));MCStudioData MCdata=new MCStudioData(MCtemp);MCdata.MCsaveHistory(new MCHistory(0,"Generado",MCtemplate.MCid(),1,null,"{}",null,null,MCdocx,null));
        try(Connection MCc=DriverManager.getConnection("jdbc:sqlite:"+MCdata.MCdatabase());Statement MCs=MCc.createStatement()){MCs.execute("PRAGMA foreign_keys=ON");MCs.executeUpdate("DELETE FROM template WHERE id="+MCtemplate.MCid());}
        MCHistory MChistory=new MCStudioData(MCtemp).MChistory().get(0);assertThat(MChistory.MCtemplateId()).isNull();assertThat(MChistory.MCdocx()).containsExactly(MCdocx);
    }
}
