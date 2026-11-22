package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.DocumentStore.*;
import com.ejemplo.crudpersona.studio.StudioData.History;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** @author MC */
class HistoryRetentionTest {
    @TempDir Path temp;

    @Test void migratesLegacyHistoryAndDeletingTemplateRetainsEverySnapshotAndOtherTemplateLink()throws Exception{
        for(boolean restrictive:List.of(false,true)){
            Path directory=temp.resolve(restrictive?"restrict":"not-null");DocumentStore store=new DocumentStore(directory);
            byte[] docx;try(XWPFDocument document=new XWPFDocument()){document.createParagraph().createRun().setText("Documento conservado");docx=DocumentEngine.bytes(document);}
            byte[] pdf=DocxPdfExporter.render(docx);Template removed=store.saveTemplate(new Template(0,"Borrable",docx,docx)),retained=store.saveTemplate(new Template(0,"Conservada",docx,docx));
            try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve("studio.db"));Statement s=c.createStatement()){
                s.execute("PRAGMA foreign_keys=ON");
                s.executeUpdate("CREATE TABLE document_history(id INTEGER PRIMARY KEY,title TEXT NOT NULL,template_id INTEGER NOT NULL"+(restrictive?" REFERENCES template(id) ON DELETE RESTRICT":"")+",template_version INTEGER NOT NULL,generated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,values_json TEXT NOT NULL,docx_path TEXT,pdf_path TEXT,docx BLOB NOT NULL,pdf BLOB)");
                s.executeUpdate("CREATE INDEX ix_history_title ON document_history(title)");
                try(PreparedStatement insert=c.prepareStatement("INSERT INTO document_history(id,title,template_id,template_version,generated_at,values_json,docx_path,pdf_path,docx,pdf) VALUES(?, ?, ?,7,'2026-11-01 12:34:56','{\"nombre\":\"Ana\"}','/documentos/carta.docx','/documentos/carta.pdf',?,?)")){
                    for(long id:List.of(removed.id(),retained.id())){insert.setLong(1,id);insert.setString(2,"Carta "+id);insert.setLong(3,id);insert.setBytes(4,docx);insert.setBytes(5,pdf);insert.executeUpdate();}
                }
            }
            StudioData data=new StudioData(directory);History before=data.history().stream().filter(h->h.templateId().equals(removed.id())).findFirst().orElseThrow();
            if(restrictive){store.saveKind(new Kind(0,"Configuración antigua","","",removed.id(),"",true));store.deleteTemplateWithLegacyKinds(removed.id());}
            else store.deleteTemplate(removed.id());
            StudioData reopened=new StudioData(directory);List<History> history=reopened.history();assertThat(history).hasSize(2);
            History after=history.stream().filter(h->h.id()==before.id()).findFirst().orElseThrow();assertThat(after.templateId()).isNull();
            assertThat(after.title()).isEqualTo(before.title());assertThat(after.generatedAt()).isEqualTo(before.generatedAt());assertThat(after.templateVersion()).isEqualTo(7);assertThat(after.valuesJson()).isEqualTo(before.valuesJson());assertThat(after.docxPath()).isEqualTo(before.docxPath());assertThat(after.pdfPath()).isEqualTo(before.pdfPath());assertThat(after.docx()).containsExactly(docx);assertThat(after.pdf()).containsExactly(pdf);
            assertThat(history.stream().filter(h->h.id()!=after.id()).findFirst().orElseThrow().templateId()).isEqualTo(retained.id());assertThat(store.templates()).extracting(Template::id).containsExactly(retained.id());
            History copy=reopened.saveHistory(new History(0,after.title(),after.templateId(),after.templateVersion(),null,after.valuesJson(),null,null,after.docx(),after.pdf()));assertThat(copy.templateId()).isNull();assertThat(reopened.history()).hasSize(3);
            try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve("studio.db"));Statement s=c.createStatement()){
                try(ResultSet check=s.executeQuery("PRAGMA foreign_key_check(document_history)")){assertThat(check.next()).isFalse();}
                try(ResultSet index=s.executeQuery("SELECT count(*) FROM sqlite_master WHERE name='ix_history_title'")){assertThat(index.next()).isTrue();assertThat(index.getInt(1)).isEqualTo(1);}
            }
        }
    }

    @Test void directSqlDeleteLeavesHistoryDetachedAndReadable()throws Exception{
        DocumentStore store=new DocumentStore(temp);byte[] docx;try(XWPFDocument document=new XWPFDocument()){docx=DocumentEngine.bytes(document);}
        Template template=store.saveTemplate(new Template(0,"Plantilla",docx,docx));StudioData data=new StudioData(temp);data.saveHistory(new History(0,"Generado",template.id(),1,null,"{}",null,null,docx,null));
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+data.database());Statement s=c.createStatement()){s.execute("PRAGMA foreign_keys=ON");s.executeUpdate("DELETE FROM template WHERE id="+template.id());}
        History history=new StudioData(temp).history().get(0);assertThat(history.templateId()).isNull();assertThat(history.docx()).containsExactly(docx);
    }
}
