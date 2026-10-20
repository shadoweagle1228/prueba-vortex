# Diagramas

| Archivo | Qué contiene | Uso |
|---|---|---|
| `infraestructura-aws.jpg` | Vista renderizada de la infraestructura propuesta en AWS | Es la imagen que muestra `ARCHITECTURE.md` (sección 3.4) |
| `infraestructura-aws.drawio` | Fuente editable del mismo diagrama, con los iconos oficiales de AWS embebidos | Editarlo y volver a exportar el JPG/PNG cuando cambie la infraestructura |

La imagen se referencia desde `ARCHITECTURE.md` con una ruta relativa, así que
GitHub la renderiza directamente en el documento.

## Editar y volver a exportar

1. Entrar a <https://app.diagrams.net> → **Open Existing Diagram** y elegir `infraestructura-aws.drawio`.
2. Hacer los cambios.
3. **File → Export as → JPEG/PNG**, y guardar sobre `docs/diagrams/infraestructura-aws.jpg` con el mismo nombre para que el documento lo tome sin tocar nada más.

## Notas

- Los iconos van incrustados en el propio `.drawio`, así que se abre completo sin conexión y sin depender de librerías externas de iconos.
- Si cambia la infraestructura propuesta, el orden es actualizar el `.drawio`, reexportar el JPG y revisar que la tabla de la sección 3.4 siga coincidiendo con lo que muestra el dibujo.
