# Diagramas

| Archivo | Qué contiene | Uso |
|---|---|---|
| `infraestructura-aws.drawio` | Diagrama de infraestructura propuesta en AWS, con los iconos oficiales de servicios embebidos en el propio archivo | Editarlo o exportarlo a PNG/PDF desde draw.io |

El diagrama vive también como Mermaid dentro de `ARCHITECTURE.md` (sección 3.4),
que es la versión que GitHub renderiza. Este `.drawio` es la versión editable con
la estética de draw.io para la exposición.

## Abrir y exportar

1. Entrar a <https://app.diagrams.net> → **Open Existing Diagram** y elegir este archivo.
2. Para PNG/PDF: **File → Export as → PNG/PDF** (activar *Selection Only* o *Crop* según se necesite).
3. Los iconos van incrustados en base64, así que el archivo se abre completo sin conexión y sin depender de librerías externas de iconos.

## Mantenerlo sincronizado

Si cambia la infraestructura, el orden es editar el diagrama Mermaid de
`ARCHITECTURE.md` y regenerar este `.drawio` con el mismo contenido, para que
ambas versiones cuenten lo mismo.
