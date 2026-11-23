<p align="center">
  <img src="src/main/resources/img/icon-128.png" alt="Logo de Estudio Documental" width="128">
</p>

<h1 align="center">Estudio Documental</h1>

<p align="center">
  Aplicación de escritorio para crear plantillas Word con variables y generar documentos llenos en segundos.
</p>

<p align="center">
  <a href="https://github.com/Will-M7/Doc_L/releases/latest">
    <img src="https://img.shields.io/badge/Descargar-Windows-2563eb?style=for-the-badge&logo=windows" alt="Descargar para Windows">
  </a>
</p>

---

## Descarga e instalación (Windows)

1. Entra a **[Releases](https://github.com/Will-M7/Doc_L/releases/latest)** y descarga `EstudioDocumental-1.0.0-windows.zip`.
2. Descomprímelo: clic derecho → **Extraer todo**.
3. Abre la carpeta `EstudioDocumental` y ejecuta **`EstudioDocumental.exe`**.

No necesitas instalar Java: ya viene incluido.

> **"Windows protegió su PC"**: la aplicación no tiene firma digital, por eso Windows puede mostrar este aviso la primera vez. Haz clic en **Más información → Ejecutar de todas formas**.

> **Importante:** no muevas el `.exe` fuera de su carpeta. Necesita las carpetas `app` y `runtime` que están a su lado.

## Funciones principales

- **Plantillas Word con variables**: importa un `.docx` o crea uno nuevo e inserta campos como `{{fecha}}` o `{{Persona_1.dni}}`.
- **Varias personas por documento**: `Persona_1`, `Persona_2`… cada una con su tarjeta en el formulario (por ejemplo, arrendador y arrendatario).
- **Generador**: llena un formulario y obtén el documento en **DOCX** o **PDF**, ábrelo en Word o imprímelo.
- **Clientes**: agenda local que recuerda los datos ya usados y los vuelve a completar.
- **Consulta de DNI/RUC**: autocompleta datos mediante servicios externos (Factiliza / APIsPERU) usando tu propio token.
- **Grupos de plantillas**, **historial** de documentos generados y **exportación/importación** en `.zip`.
- **Funciona sin internet**: todo se guarda en tu equipo (`%APPDATA%\DocumentStudio`).

## Primeros pasos

1. **Editor de plantillas → Importar**: abre un documento Word que tenga marcadores `{{...}}`.
2. **Plantillas**: crea un grupo y agrega tus plantillas.
3. **Generador**: elige el grupo y la plantilla, llena los datos y guarda el documento.

### Formato de las variables

| Marcador | Uso |
|---|---|
| `{{fecha}}`, `{{ciudad}}` | Datos generales del documento |
| `{{Persona_1.nombre_completo}}` | Dato de una persona concreta |
| `{{Persona_2.dni}}` | Dato de otra persona del mismo documento |

Los nombres solo pueden tener letras sin tilde, números y `_`, y deben empezar con una letra.

## Para desarrolladores

Requisitos: **JDK 17 o superior** y **Git**. No hace falta instalar Maven: el proyecto trae el wrapper `mvnw`.

```bash
# Descargar el proyecto
git clone https://github.com/Will-M7/Doc_L.git
cd Doc_L

# Ejecutar la aplicación
./mvnw javafx:run          # Windows: .\mvnw.cmd javafx:run

# Ejecutar las pruebas
./mvnw test
```

### Generar el ejecutable para Windows

```bat
:: Crea target\installer con un instalador .exe (requiere WiX Toolset 3.x y jpackage en el PATH)
package-windows.cmd
```

Versión portable sin WiX (requiere `jpackage` del JDK en el PATH):

```bat
mvnw.cmd -DskipTests package org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\package-input
copy target\estudio-documental-1.0.0.jar.original target\package-input\studio.jar
jpackage --type app-image --name EstudioDocumental --app-version 1.0.0 --input target\package-input --dest target\app --main-jar studio.jar --main-class com.ejemplo.crudpersona.MCLauncher --icon img\logo.ico
```

### Tecnologías

Java 17 · JavaFX 21 · AtlantaFX · Apache POI · OpenPDF · SQLite · Jackson

### Autor

Alejandro Chino Leon
