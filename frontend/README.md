# RutaVida — Frontend Web

Aplicación web del **Sistema Inteligente de Rutas de Emergencia para Valledupar**.

El frontend está desarrollado con HTML, CSS y JavaScript. Utiliza Leaflet para la visualización geográfica y se comunica con el backend mediante una API REST desarrollada en Java.

## 1. Funcionalidades implementadas

La aplicación web permite:

- Iniciar sesión mediante la API REST.
- Consultar los tipos de emergencia disponibles en Oracle.
- Registrar emergencias y almacenarlas en Oracle.
- Consultar emergencias registradas.
- Consultar unidades de atención.
- Visualizar información geográfica de demostración.

**Importante:** los recursos identificados como `EJEMPLO` son datos de demostración. No representan unidades operativas reales.

El despacho operativo de unidades y la integración definitiva de rutas corresponden a la etapa K7 y continúan pendientes.

## 2. Estructura del frontend

```text
frontend/
├── css/
│   └── Estilos de la aplicación
├── data/
│   └── Datos geográficos de demostración
├── js/
│   ├── api-client.js
│   ├── example-data.js
│   ├── dashboard.js
│   ├── emergencia.js
│   └── Otros módulos JavaScript
├── index.html
├── dashboard.html
├── emergencia.html
├── despacho.html
└── README.md
```

## 3. Requisitos

Antes de ejecutar la aplicación se necesita:

- Java configurado en el entorno de desarrollo.
- Oracle Database disponible.
- Backend de RutaVida configurado correctamente.
- IntelliJ IDEA o un entorno compatible para ejecutar el servidor Java.

La configuración de conexión a Oracle debe mantenerse en el backend. No se deben publicar credenciales ni contraseñas.

## 4. Ejecución de la aplicación

El frontend se sirve desde el servidor Java del proyecto. No se debe abrir directamente el archivo HTML ni utilizar otro servidor independiente para estas pruebas.

### Paso 1. Iniciar Oracle

Verifica que Oracle Database esté iniciado y que la conexión configurada para RutaVida esté disponible.

### Paso 2. Iniciar el backend

1. Abre el proyecto en IntelliJ IDEA.
2. Abre el módulo `backend`.
3. Localiza la clase `WebServer`:

   `co.edu.unicesar.rutavida.app.WebServer`

4. Ejecuta su método `main`.
5. Comprueba que la consola muestre el mensaje:

   `[OK] RutaVida está en http://localhost:8080`

Mantén el servidor ejecutándose mientras utilizas la aplicación web.

### Paso 3. Abrir el frontend

En el navegador ingresa a:

`http://localhost:8080/index.html`

La aplicación mostrará la pantalla de inicio de sesión.

Después de iniciar sesión correctamente, podrás acceder al panel principal y a las páginas de registro y consulta.

También puedes comprobar el control de acceso abriendo directamente:

`http://localhost:8080/dashboard.html`

Si no existe una sesión válida, la aplicación debe redirigir al inicio de sesión.

## 5. Verificación funcional

Para comprobar la conexión entre el frontend, el backend y Oracle, realiza estas pruebas:

| Prueba | Resultado esperado |
|---|---|
| Abrir `index.html` | Se muestra el inicio de sesión |
| Iniciar sesión | Se accede al panel con el usuario autenticado |
| Consultar tipos de emergencia | Se muestran los tipos provenientes de Oracle |
| Registrar una emergencia | La API confirma el registro y lo almacena en Oracle |
| Consultar emergencias | Se muestran los registros disponibles |
| Consultar unidades | Se muestran los recursos disponibles y los datos de demostración identificados como `EJEMPLO` |
| Abrir el panel sin sesión | Se redirige al inicio de sesión |
| Detener el backend | Se muestra un mensaje de error de conexión |

Para revisar posibles errores, abre las herramientas del navegador con `F12` y consulta la pestaña **Console**.

## 6. Detener la aplicación

Cuando termines las pruebas:

1. Regresa a IntelliJ IDEA.
2. Detén la ejecución de `WebServer` mediante el botón **Stop**.
3. Cierra el navegador si ya no necesitas la aplicación.

## 7. Tecnologías utilizadas

- HTML5.
- CSS3.
- JavaScript (módulos ES).
- Leaflet.
- OpenStreetMap.
- API REST desarrollada en Java.
- Oracle Database.

## 8. Documentación relacionada

- `README.md`: documentación general del proyecto.
- `backend/README.md`: configuración y ejecución del backend.
- `docs/`: documentación técnica, arquitectura y registros de implementación.

**Proyecto académico:** Sistema Inteligente de Rutas de Emergencia para Valledupar — RutaVida.