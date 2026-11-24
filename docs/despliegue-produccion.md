# Despliegue Vercel + Render + Oracle

RutaVida publica el frontend estático en **Vercel** y la API Java/Javalin en **Render**. La base de datos puede continuar en **Oracle Autonomous Database**: no se migra a PostgreSQL ni cambia el motor A*/Dijkstra.

> RutaVida es un prototipo académico; no se debe utilizar para coordinar emergencias reales ni almacenar datos clínicos sensibles.

## Arquitectura

```text
Navegador o teléfono (HTTPS/WSS)
        │
        ▼
Vercel: HTML, CSS y JavaScript ─── HTTPS/WSS ───► Render: /api y /ws/gps
                                                        │ JDBC TLS/mTLS
                                                        ▼
                                           Oracle Autonomous Database
```

- Vercel no conoce ni almacena credenciales de Oracle.
- Render recibe los secretos de Oracle y publica solo la API.
- El GPS funciona porque Vercel y Render ofrecen HTTPS; el mapa en vivo usa WSS.
- Render tiene PostgreSQL administrado, pero no Oracle administrado. Oracle sigue siendo administrado por Oracle Cloud y Render se conecta como cliente JDBC.

## Archivos agregados

| Archivo | Función |
| --- | --- |
| `vercel.json` | Build del frontend y cabeceras de seguridad. |
| `scripts/build-frontend.mjs` | Genera `dist/` e inyecta el origen público de la API. |
| `Dockerfile` | Imagen Java 21 para Render. |
| `render.yaml` | Blueprint de Render sin secretos. |
| `.dockerignore` | Excluye wallets, claves, logs y compilados. |

No suba `backend/db.properties`, wallets, contraseñas ni `dist/` a Git.

## 1. Oracle Autonomous Database

1. Cree o use la instancia Autonomous Database del proyecto.
2. Ejecute los scripts de `database/` en el orden del `README.md`, incluido `14_gps_conductor.sql` y `98_verificar.sql`.
3. Cree el usuario de aplicación con privilegios mínimos; para la instalación actual es `RUTAVIDA`.
4. Descargue el wallet de Oracle y guárdelo solo en un equipo seguro.
5. Si se habilita una ACL de Oracle, permita las IP de salida del servicio Render o una IP de salida dedicada. No abra la base a `0.0.0.0/0`.

En Render, cargue los archivos descomprimidos del wallet como **Secret Files** con sus nombres originales. Los principales son:

```text
tnsnames.ora
sqlnet.ora
cwallet.sso
ewallet.p12
keystore.jks
truststore.jks
ojdbc.properties
```

Render los monta en `/etc/secrets/`. La URL JDBC debe apuntar allí, por ejemplo:

```text
jdbc:oracle:thin:@rutavida_low?TNS_ADMIN=/etc/secrets
```

## 2. Backend en Render

1. Fusione la configuración revisada a `develop` antes de desplegarla.
2. En Render seleccione **New → Blueprint** y conecte el repositorio. Detectará `render.yaml` y el `Dockerfile`.
3. En **Environment**, defina estos secretos. No los escriba en Git ni en `render.yaml`:

   | Variable | Valor de ejemplo |
   | --- | --- |
   | `RUTAVIDA_DB_URL` | `jdbc:oracle:thin:@rutavida_low?TNS_ADMIN=/etc/secrets` |
   | `RUTAVIDA_DB_USER` | `rutavida` |
   | `RUTAVIDA_DB_PASSWORD` | contraseña secreta de Oracle |
   | `RUTAVIDA_CORS_ORIGINS` | `https://rutavida.vercel.app` |

   Para varios frontends, use URLs exactas separadas por coma. No use `*`, rutas ni barra final.

4. Cargue los archivos del wallet como Secret Files y haga Deploy.
5. Render entrega una URL como `https://rutavida-api.onrender.com`. El backend lee automáticamente `PORT`, escucha en `0.0.0.0` y usa `/health` como comprobación.
6. Compruebe que inició correctamente:

   ```powershell
   Invoke-WebRequest https://rutavida-api.onrender.com/health
   ```

   Debe responder `{"status":"ok"}` con HTTP 200.

7. Mantenga inicialmente una sola instancia de Render. Los WebSockets de GPS y los límites de solicitudes están en memoria; escalar horizontalmente necesita un bus de eventos compartido.

## 3. Frontend en Vercel

1. En Vercel elija **Add New → Project** e importe el mismo repositorio.
2. Deje la raíz en la carpeta raíz del repositorio. `vercel.json` ejecuta el build y sirve `dist/`.
3. En **Settings → Environment Variables**, agregue en Production:

   | Variable | Valor |
   | --- | --- |
   | `RUTAVIDA_API_BASE_URL` | `https://rutavida-api.onrender.com` |

   Esta variable es pública por diseño: solo contiene el origen de la API. Nunca agregue allí tokens, contraseñas ni wallets. Debe ser HTTPS y no llevar `/api` ni barra final.

4. Haga Deploy. Vercel devolverá una URL como `https://rutavida.vercel.app`.
5. Verifique que esa URL coincide exactamente con `RUTAVIDA_CORS_ORIGINS` en Render. Si la cambia, redepliegue el backend.

El build agrega el origen de Render a la Content Security Policy para permitir REST y WSS. Sin variable configurada, el frontend local sigue usando `/api` del WebServer, como antes.

## 4. Dominios propios (opcional)

1. Añada `app.tudominio.com` en Vercel y `api.tudominio.com` en Render.
2. Configure los DNS que indique cada proveedor.
3. Actualice y redespliegue:

```text
RUTAVIDA_CORS_ORIGINS=https://app.tudominio.com
RUTAVIDA_API_BASE_URL=https://api.tudominio.com
```

4. Espere a que ambos certificados TLS estén activos. El GPS del teléfono no debe abrirse por HTTP.

## 5. Verificación final

1. Abra Vercel en una ventana privada e inicie sesión.
2. En DevTools → Network, confirme que las peticiones van a `https://...onrender.com/api/...`.
3. Abra el dashboard: el socket debe conectar a `wss://.../ws/gps`; el sondeo REST cada cinco segundos queda como respaldo.
4. Abra **Mi GPS de unidad** en un teléfono, conceda ubicación y confirme el marcador en vivo.
5. Pruebe CORS con la URL real:

   ```powershell
   curl.exe -i -X OPTIONS "https://rutavida-api.onrender.com/api/auth/login" `
     -H "Origin: https://rutavida.vercel.app" `
     -H "Access-Control-Request-Method: POST" `
     -H "Access-Control-Request-Headers: Authorization, Content-Type"
   ```

   La respuesta debe incluir `Access-Control-Allow-Origin` con la URL exacta de Vercel.
6. Cree una emergencia, despache una unidad y pruebe GPS y recálculo de ruta. A*/Dijkstra no cambia.

## Seguridad y operación

- HTTPS lo administran Vercel y Render; no se guardan certificados en el repositorio.
- CORS acepta únicamente orígenes explícitos y el WebSocket valida su `Origin` antes de autenticarse.
- La API envía `Cache-Control: no-store`; se aplican `nosniff`, anti-iframe, política de referencia y política de geolocalización.
- Los tokens viajan en `Authorization: Bearer`, nunca en la URL ni en cookies compartidas.
- Revise logs, cuotas de Oracle y respaldos. Rote de inmediato contraseña y wallet si se exponen.
- Antes de un uso real se requiere monitoreo, backups, recuperación ante desastres y revisión institucional de privacidad y cumplimiento.
