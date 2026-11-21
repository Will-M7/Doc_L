# Contexto maestro — Estudio Documental 1.0.0

Actualizado: **2026-11-16** (`America/Lima`). Ancla para próximos chats; detalle y evidencia en [AUDITORIA_PROYECTO.md](AUDITORIA_PROYECTO.md). Esta actualización documental no cambia código ni builds.

## 1. Stack y entorno

| Elemento | Versión / contrato |
|---|---|
| Artefacto / Java | `com.ejemplo:estudio-documental:1.0.0`; compilación Java **17** |
| JVM host / suite verde | OpenJDK **25.0.5.0.3** por defecto / **21.0.13.0.3** en pruebas |
| OS / Maven | Ubuntu **26.04.1 LTS**, kernel `7.0.0-31-generic`; Maven **3.9.16**, wrapper **3.3.4** |
| UI | JavaFX **21.0.4**, AtlantaFX **2.0.1**, Ikonli Feather **12.3.1** |
| Datos / documentos | SQLite JDBC **3.45.3.0**, Apache POI **5.2.5**, OpenPDF **1.3.43** |
| JSON / tests | Jackson **2.19.4**, JUnit Jupiter **5.10.3**, AssertJ **3.25.3** |
| Build | Spring Boot parent/plugin **3.3.4** (sin servidor Spring), JavaFX plugin **0.0.8**, Compiler **3.13.0**, Surefire **3.2.5** |

`Launcher` → `JavaFxApplication` → `StudioWindow`. Escena 1280×800, mínimos 1100×700. **1134 líneas en StudioWindow**, ocho vistas principales y pestaña interna de configuración de generación.

## 2. Árbol actual

Exclusiones: `.git`, `target`, `.serena`; el resto incluye archivos ignorados y directorios vacíos. `.visual-backup` es histórico, `solic.docx` es material local; no son código Maven activo.

```text
/home/willian/Desarrollo/Documentos/
├── .agents/
├── .aws/
├── .codex/
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── .visual-backup/
│   ├── JavaFxApplication.java
│   ├── StudioWindow.java
│   └── studio.css
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── ejemplo/
│   │   │           └── crudpersona/
│   │   │               ├── studio/
│   │   │               │   ├── ApiCredentialStore.java
│   │   │               │   ├── ApiFieldCatalog.java
│   │   │               │   ├── ApisPeruProvider.java
│   │   │               │   ├── ContactReuse.java
│   │   │               │   ├── ContinuousDocxEditor.java
│   │   │               │   ├── DocumentEngine.java
│   │   │               │   ├── DocumentStore.java
│   │   │               │   ├── DocxPdfExporter.java
│   │   │               │   ├── FactilizaClient.java
│   │   │               │   ├── FactilizaFields.java
│   │   │               │   ├── FactilizaProvider.java
│   │   │               │   ├── LocalSecret.java
│   │   │               │   ├── LookupProvider.java
│   │   │               │   ├── LookupRouter.java
│   │   │               │   ├── StudioData.java
│   │   │               │   ├── StudioTransfer.java
│   │   │               │   ├── StudioWindow.java
│   │   │               │   └── VariableInput.java
│   │   │               ├── JavaFxApplication.java
│   │   │               └── Launcher.java
│   │   └── resources/
│   │       ├── css/
│   │       │   ├── app.css
│   │       │   └── studio.css
│   │       └── editor/
│   │           └── document-editor.html
│   └── test/
│       └── java/
│           └── com/
│               └── ejemplo/
│                   └── crudpersona/
│                       └── studio/
│                           ├── ContactReuseTest.java
│                           ├── ContinuousDocxEditorTest.java
│                           ├── DocumentWorkflowTest.java
│                           ├── HistoryRetentionTest.java
│                           ├── LocalModulesTest.java
│                           ├── MultiApiWorkflowTest.java
│                           ├── PartialGenerationTest.java
│                           ├── StudioTransferTest.java
│                           └── TemplateCatalogUiTest.java
├── .gitattributes
├── .gitignore
├── AUDITORIA_PROYECTO.md
├── CONTEXTO.md
├── mvnw
├── mvnw.cmd
├── package-windows.cmd
├── pom.xml
└── solic.docx
```

## 3. Arquitectura y convenciones vigentes

1. **Offline-first**: generación local DOCX/PDF y agenda SQLite. En el generador, DNI/RUC consulta primero Clientes; solo si no existe llama al router. La consulta rápida de Conexiones API usa directamente el router.
2. **Persistencia efímera**: cada operación debe abrir/cerrar su conexión con `try-with-resources` y activar `PRAGMA foreign_keys=ON`. DocumentStore/StudioData lo hacen; **ApiCredentialStore.open aún no lo activa**. Es deuda real, no comportamiento ya corregido. Varias asociaciones carecen de FK declarada.
3. **Ubicación**: `%APPDATA%/DocumentStudio/studio.db` o `~/.document-studio/studio.db`. No abrir ni migrar datos reales para una revisión que solo requiere inspección.
4. **Secretos**: tokens fuera de SQLite/ZIP, en `credential-<id>.secret`; DPAPI por usuario en Windows o AES-256-GCM y `.local-secret.key` en Linux. No incluir secretos, URLs con token ni datos personales en logs/Git/ZIP.
5. **Red**: `LookupRouter` con Factiliza/APIsPERU, DNI/RUC, hasta cuatro intentos y enfriamiento por estado. Ejecutar mediante `Task` en hilo daemon; guardar respuestas utilizables antes de aplicar a UI. Normalización compartida limpia `nombres` y produce `nombre_completo` como **Nombres Apellidos**, sin comas ni apellidos duplicados; conservar respuestas parciales útiles.
6. **Tokens y participantes**: `{{campo}}` o `{{Persona_1.campo}}`; prefijo estable y rol visible renombrable en `template_persona`. API/local/complementos y autoguardado se aíslan por persona. Claves ASCII `[a-zA-Z][a-zA-Z0-9_]*`; etiquetas/valores admiten Unicode. Usar clave `Nombre_del_Anio` y etiqueta «Nombre del Año» si se necesita ese ejemplo.
7. **Alcance**: `variable_definition.bind_to_client` por defecto 1. Con 0, insertar siempre sin prefijo, agrupar en Datos generales, prellenar valor global y excluir de bindings/autoguardado/mezcla de Clientes y de complementos. Resolver también los marcadores antiguos prefijados al generar. No hay una depuración masiva de extras históricos ni restricción SQL de JSON arbitrario.
8. **Fecha**: `auto_current_date` y `date_format`, `LocalDate.now()` con `es-PE`, cinco formatos, edición manual. Para independientes, `Definition.initialValue()` prioriza el valor predeterminado global.
9. **Clientes**: con documento, identidad `(kind,tipo normalizado,número)`; sin número, `(kind,nombre completo normalizado)`. Extras JSON editables. Upsert automático mezcla datos no vacíos y no pregunta confirmación; evitar mezclar personas/globales. La unicidad actual se aplica en Java, no mediante UNIQUE general en la BD.
10. **Historial**: `document_history.template_id` nullable, FK SET NULL. Borrar plantilla requiere confirmación y conserva título, versión, valores, fecha y bytes emitidos. Abrir/exportar sigue disponible; regenerar desde plantilla requiere que esta y su revisión existan. Guardar DOCX/PDF registra historial; preview/Word/imprimir realizan autoguardado, sin insertar por sí solos un registro histórico.
11. **OOXML/editor**: preservar bloques intactos. Enter repara índices duplicados, nuevos párrafos se insertan en orden DOM con XmlCursor y se conservan blancos reales. No escribir `\u200B` ni `<br>` de relleno como espacios/saltos extra. Tras sincronizar, reabrir DOCX y reconocer IDs en DOM para refrescar cachés POI. Reemplazo recorre nodos de texto de runs sin añadir párrafos ni alterar espacios adyacentes.
12. **UI**: mantener PrimerLight/PrimerDark, navegación y animaciones. Solo Editor carece de cabecera superior. Cinta Inicio/Insertar/Vista, Feather/tooltips; panel Variables con personas y contraer en su cabecera; drag por puntero, guía 2 px con RAF y menú contextual de borrado.

## 4. Estado funcional y formatos

- **Biblioteca Plantillas**: lista todas, busca/ordena y resalta pertenencias del grupo activo. **Generador**: filtra plantillas por grupo real y formulario por marcadores; tarjetas por persona + Datos generales; admite campos vacíos. Es distinta la función del grupo en ambas vistas.
- **Variables**: CRUD tipado y cuatro tarjetas: API, Complemento bidireccional, Fecha automática y Alcance. Una variable usada no se elimina; Abrir editor carga la primera plantilla afectada.
- **Complementos**: `variable_complement` define relaciones; `variable_pair_memory` guarda pares normalizados con sugerencias por prefijo/exactas y escritura inversa cuando es bidireccional. Aprendizaje determinista al generar; puede actualizar agenda de nombres/datos asociados.
- **Vista previa**: WebView paginada de solo lectura sobre DOCX rellenado, escalada al ancho y scroll inicial a cero; sin chips ni reglas. Es una representación HTML de un subconjunto OOXML, **no un render Word universal ni el mismo motor que PDF**.
- **Acciones**: Previsualizar, Guardar DOCX/PDF, Abrir en Word mediante aplicación asociada e Imprimir mediante PDF temporal. `DocxPdfExporter` sigue usando Helvetica/Cp1252 sin embebido: soporte Unicode y fidelidad PDF pendientes.
- **SQLite**: 23 tablas (10 DocumentStore, 8 StudioData, 5 credenciales). Migraciones idempotentes para grupos múltiples, definition_id/defaults, personas, complementos/memoria, fecha/alcance, historial nullable y contactos sin documento. No hay versionado central de migraciones; ver DDL completo en auditoría.
- **ZIP configuración**: `document-studio-config` **v9**, importa **v1–v9**. Incluye alcance global, fecha, personas, complementos y grafo de configuración de 1 plantilla / 1 grupo / todas. **Clientes**: `document-studio-clients` **v1**, agenda completa con extras. Puede importar ambas secciones juntas. KEEP/REPLACE remapea IDs. No incluye tokens, historial ni memoria de pares.
- **Configuración y respaldos**: exactamente tres tarjetas (Importar, Exportar Plantillas, Exportar Clientes). API cifrada de backup fuera de la UI: escribe **DSBK1** de BD; restaura DSBK1/DSBK2 histórico sin restaurar tokens. ZIP limita a 200 000 000 bytes descomprimidos y 100 000 000 por DOCX.

## 5. Git y verificación

- Rama `main`; referencias locales sin divergencia con `origin/main`, sin fetch en la auditoría. Solo dos commits disponibles: `7945f54` (flujo local-first/rediseño) y `7fa2c21` (inicial).
- Al inicio de la auditoría: **17 archivos rastreados modificados**, dos tests nuevos, `AUDITORIA_PROYECTO.md` sin seguimiento y `.serena/` sin seguimiento. Esta actualización añade `CONTEXTO.md` al diff documental. No consolidar con `git add .` indiscriminado.
- Última ejecución aprobada: **71 tests en 9 clases, 0 fallos, 0 errores, 0 omitidas**, 2026-11-16 08:24:49 −05:00, 31.585 s, JDK 21.0.13.0.3. Clases: LocalModules 17, TemplateCatalogUi 20, ContinuousDocxEditor 10, MultiApiWorkflow 9, ContactReuse 5, DocumentWorkflow 3, StudioTransfer 3, HistoryRetention 2, PartialGeneration 2.
- La auditoría verificó reportes existentes en `target/surefire-reports`, sin reejecutar ni modificar código. No hay porcentaje de cobertura medido; los tests no certifican API en vivo, impresión real, layout universal ni instaladores.

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw clean test
# Con pantalla gráfica; capturas opcionales:
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw test -Dstudio.ui.snapshots=/tmp/documentos-ui-review
```

## 6. Plan técnico priorizado (pendiente; no implementado)

1. **P0 — Consolidación Git**: revisar diff, incorporar fuentes/tests y documentación; excluir datos privados/archivos locales; validar el commit candidato con las 71 regresiones.
2. **P1 — Modularizar StudioWindow**: extraer vistas y servicios de aplicación sin alterar navegación, personas/globales, local-first y bridge.
3. **P1 — I/O asíncrono e integridad**: SQLite/POI/PDF/ZIP/archivos hoy bloquean callbacks FX; introducir tareas/cancelación y snapshots. Unificar PRAGMAs, migraciones y transacciones antes de paralelizar. Probar cierre de vistas/diálogos desde animaciones, deduplicación y backups consistentes.
4. **P1 — PDF Unicode/fidelidad**: fuentes embebidas Identity-H y corpus de glifos/tablas/secciones; documentar diferencias con Word y vista previa.
5. **P2 — Empaquetado Linux**: actualmente solo existe `package-windows.cmd` con jpackage (comprueba presencia, no versión). Crear flujo Linux reproducible y pruebas de instalación limpia; no hay `module-info.java` ni evidencia de instaladores certificados.
