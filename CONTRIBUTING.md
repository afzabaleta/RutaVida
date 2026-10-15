# Cómo trabajar en RutaVida (guía para el equipo)

## 1. Preparar tu computador (una sola vez)

**Requisitos:** Git, IntelliJ IDEA, un JDK 21 o superior, y Oracle Database XE con la base `XEPDB1`.

```
git clone https://github.com/afzabaleta/RutaVida.git
cd RutaVida
git checkout develop
```

**Base de datos** (cada uno tiene la suya, en su computador). En la consola, dentro de `RutaVida\database`:

```
sqlplus /nolog
```
```
CONNECT system/TU_CONTRASEÑA_DE_SYSTEM@localhost:1521/XEPDB1
@00_crear_usuario.sql
CONNECT rutavida/LA_CONTRASEÑA_QUE_ELEGISTE@localhost:1521/XEPDB1
@01_esquema.sql
@02_datos_iniciales.sql
@98_verificar.sql
exit
```

En `98_verificar.sql` todo debe decir `SI` (23 tablas, 21 secuencias, 24 índices, 0 objetos inválidos). Las dos pruebas marcadas "DEBE FALLAR" muestran `ORA-02290`, y eso es lo correcto.

`SYSTEM` solo se usa para crear el usuario. La aplicación y los demás scripts trabajan siempre con el usuario `RUTAVIDA`.

> `99_eliminar_esquema.sql` **borra todas las tablas**. No se ejecuta ni se modifica sin autorización explícita del líder.

**Backend:**
1. En IntelliJ: *File → Open* → carpeta `RutaVida\backend` (así IntelliJ carga el `pom.xml` como proyecto Maven). Si se abrió la carpeta `RutaVida` completa y `src/test/java` no sale en verde: clic derecho en `backend/pom.xml` → *Add as Maven Project*.
2. Copia `db.properties.example` como `db.properties` y escribe tu contraseña. Este archivo **nunca** se sube a GitHub.
3. Ejecuta `DatabaseCheck`: debe decir "23 de 23".
4. Clic derecho en `src/test/java` → *Run 'All Tests'* (o en la ventana *Maven*: *Lifecycle → test*): todas en verde.

---

## 2. Idiomas

| Qué | Idioma | Ejemplo |
|---|---|---|
| Nombres de ramas | Español | `funcionalidad/dao-emergencias` |
| Mensajes de commit | **Inglés** | `feature: dao-emergencies` |
| Título y descripción de los Pull Requests | Español | "Agregar DAO de emergencias" |
| Código: clases, métodos, variables, comentarios | Inglés | `EmergencyService`, `calculateRoute()` |
| Textos que ve el usuario | Español | "Registrar emergencia" |
| Documentación (`README`, `docs/`) | Español | — |
| Tablas y columnas de Oracle | Español (ya aprobadas) | `TRAMO_VIAL`, `id_emergencia` |

---

## 3. Ramas

| Rama | Para qué | Cómo recibe cambios |
|---|---|---|
| `main` | Versión estable que se presenta. Es la rama por defecto del repositorio | Solo por Pull Request desde `develop`, cuando el equipo lo decide |
| `develop` | Integración del trabajo de todos | Solo por Pull Request revisado por otra persona |
| `funcionalidad/<nombre>` | Una funcionalidad nueva (incluye scripts de datos) | Su responsable |
| `correccion/<nombre>` | Arreglar un error | Quien lo arregla |
| `documentacion/<nombre>` | Documentos, diagramas y mockups | Quien los escribe |
| `pruebas/<nombre>` | Agregar o mejorar pruebas | Quien las escribe |
| `refactorizacion/<nombre>` | Mejorar código sin cambiar lo que hace | Quien lo mejora |
| `estructura/<nombre>` | Carpetas, configuración, `pom.xml` | Quien lo cambia (avisando antes al grupo) |

Los nombres de las ramas van en español, en minúsculas, sin tildes ni espacios: `funcionalidad/motor-rutas`, `correccion/validacion-incidentes`, `documentacion/actualizar-modelo-datos`, `pruebas/servicio-despacho`.

**Nadie hace commit ni push directo a `main` ni a `develop`.**

### Las ramas no se eliminan

Regla del equipo: **las ramas se conservan** después de fusionarlas, para mantener la trazabilidad del trabajo. Están prohibidos, salvo autorización explícita del líder:

- `git branch -d` y `git branch -D`
- `git push origin --delete <rama>`
- El botón *Delete branch* que GitHub muestra después del merge
- Activar en GitHub el borrado automático de ramas fusionadas
- Borrar la rama de otro integrante

Tampoco se usan `git reset --hard`, `git clean -fd` ni `git push --force` sin aprobación del líder: pueden borrar trabajo propio o de un compañero sin posibilidad de recuperarlo.

---

## 4. El ciclo de cada tarea

**1. Partir siempre de un `develop` actualizado**
```
git status
git checkout develop
git pull origin develop
git checkout -b funcionalidad/dao-emergencias
```
`git status` primero: si tienes cambios sin guardar, haz commit en tu rama actual antes de cambiar de rama.

**2. Trabajar y hacer commits pequeños**
```
git status
git add <archivos>
git commit -m "feature: dao-emergencies"
```
Antes de cada commit, revisa en `git status` que no aparezcan `db.properties`, `.env`, `target/`, `.idea/` ni archivos `.log`. Agrega los archivos por nombre o carpeta, no a ciegas.

**3. Subir la rama**
```
git push -u origin funcionalidad/dao-emergencias
```
La primera vez se usa `-u`. Después basta con `git push`.

**4. Si `develop` avanzó mientras trabajabas** (otro compañero ya integró algo):
```
git checkout develop
git pull origin develop
git checkout funcionalidad/dao-emergencias
git merge develop
```
Si hay conflictos, IntelliJ los muestra en *Git → Resolve Conflicts*. Cuando quede resuelto, haz commit y `git push`. Nunca resuelvas un conflicto borrando el trabajo de un compañero: si no sabes cuál versión queda, pregúntale.

**5. Abrir el Pull Request en GitHub**
- *Pull requests → New pull request*. Como `main` es la rama por defecto, **cambia `base` a `develop`** y en `compare` elige tu rama.
- Título en español, por ejemplo "Agregar DAO de emergencias".
- En la descripción (en español): qué cambia, cómo se prueba y qué tarea del plan cierra (por ejemplo, "Cierra A2").
- Asigna a un compañero como revisor. Andrés revisa los PR que tocan `database/`, `dao/`, `app/` o `pom.xml`.

**6. Revisión e integración**
- Quien revisa descarga la rama, corre las pruebas y deja comentarios o aprueba.
- Con la aprobación, se hace *Merge* en GitHub. **La rama no se borra.**
- Todos actualizan su `develop` con `git checkout develop` y `git pull origin develop`.

---

## 5. Reglas del equipo

**Commits:** `type: short-description`, con el tipo y la descripción **en inglés**, en minúsculas y con guiones.

| Tipo | Uso | Ejemplo |
|---|---|---|
| `feature:` | Algo nuevo | `feature: dao-emergencies` |
| `fix:` | Arreglar un error | `fix: validate-incident-status` |
| `docs:` | Documentos | `docs: update-database-dictionary` |
| `test:` | Agregar o cambiar pruebas | `test: add-route-engine-tests` |
| `refactor:` | Mejorar código sin cambiar lo que hace | `refactor: separate-persistence-logic` |
| `chore:` | Configuración, carpetas, `pom.xml`, `.gitignore` | `chore: organize-backend-packages` |

Los commits que se hicieron antes de esta regla (en español o directamente en `develop`) se quedan como están: **no se reescribe el historial**.

**Antes de abrir un PR:**
1. Todas las pruebas en verde. Estas pruebas avisan si rompiste algo:
   - `ArchitectureTest`: una capa importó algo que no le corresponde.
   - `ClassDiagramSyncTest`: creaste una clase y no la agregaste a `docs/class-diagram.md`.
   - `EnumSqlContractTest`: un estado de Java no coincide con la base de datos.
   - `SqlScriptStyleTest`: un script tiene un comentario después del `;`.
2. Si cambiaste una firma pública (un método que otros usan), lo avisaste en el grupo y actualizaste el diagrama de clases en el mismo PR.
3. Si cambiaste la base de datos, el cambio está en los scripts de `database/` (no solo en SQL Developer) y `98_verificar.sql` sigue saliendo bien.

**Cada uno trabaja en sus paquetes** (ver `docs/arquitectura-software.md`). Los archivos que tocan todos (`AppContext`, `pom.xml`, `docs/class-diagram.md`) se cambian avisando antes en el grupo, para evitar conflictos. No se duplican clases ya existentes.

**Credenciales:** nunca en el código ni en GitHub (`db.properties`, `.env`, wallets de Oracle, contraseñas, tokens). Cada uno usa su `db.properties` local.

**Datos simulados:** el tráfico y las posiciones generados por el simulador se guardan con fuente `SIMULACION` y nunca se presentan como datos reales.

---

## 6. Configuración del repositorio (solo Andrés, una vez)

1. **Colaboradores:** en GitHub, *Settings → Collaborators → Add people*. Agrega a Kendrick, Diego y Sherly con su usuario de GitHub.
2. **Rama por defecto:** se queda en `main`. No se cambia.
3. **Borrado automático de ramas:** en *Settings → General → Pull Requests*, la opción *Automatically delete head branches* debe quedar **desactivada**.
4. **Proteger `main` y `develop`:** *Settings → Branches → Add branch protection rule*. Marca *Require a pull request before merging* y *Require approvals: 1*. No marques nada que permita borrar ramas.
   - Con una cuenta gratuita de GitHub, esta protección solo está disponible si el repositorio es **público**. Si es privado, la regla de "nadie hace push directo" queda como acuerdo del equipo.