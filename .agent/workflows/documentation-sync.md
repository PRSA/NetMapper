---
description: Sincronizar los 4 ficheros markdown globales tras cambios en el proyecto.
---

Este workflow asegura que la documentación del proyecto se mantenga al día con el desarrollo.

1. **Identificar Cambios**: Revisa los archivos modificados en el desarrollo o diseño actual.
2. **Actualizar TASKS.md**: 
    - Marca como `[x]` las tareas completadas.
    - Asegúrate de incluir los IDs de las tareas (`<!-- id: XX -->`).
3. **Actualizar README.md**:
    - Refleja nuevas características o cambios en la arquitectura.
    - Verifica que las instrucciones de instalación/uso sigan siendo válidas.
4. **Actualizar WALKTHROUGH.md**:
    - Documenta cómo probar los nuevos cambios.
    - Actualiza capturas o pasos manuales si aplica.
5. **Actualizar IMPLEMENTATION_PLAN.md**:
    - Si hubo cambios estructurales significativos, documenta la nueva realidad técnica.
