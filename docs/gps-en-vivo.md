# GPS en vivo de unidades

RutaVida conserva su backend Java/Javalin y su mapa Leaflet. No usa un segundo backend Node.js ni React: el teléfono usa la Geolocation API, envía una lectura protegida cada cinco segundos a `POST /api/gps/position` y Javalin difunde la ubicación confirmada por WebSocket nativo en `/ws/gps`.

## Preparación de la demostración

1. Ejecute `database/14_gps_conductor.sql` y después `database/15_operacion_conductor.sql` después de los scripts de base ya instalados. El segundo agrega la hora de finalización y el permiso `GPS/OPERAR`.
2. En Administración cree el usuario con rol `BOMBERO_PARAMEDICO` y registre la ambulancia.
3. Consulte sus identificadores y cree la relación indicada al final de `14_gps_conductor.sql`. Una persona solo puede tener una unidad y una unidad solo un conductor.
4. Cierre sesión y vuelva a entrar con el conductor para que se lean los permisos nuevos. Inicie `WebServer`, entre con el conductor y abra **Mi GPS de unidad**. Pulse **Iniciar GPS** y acepte ubicación precisa.
5. El módulo muestra solo la ambulancia propia, su emergencia, hospitales y bases. La ruta, distancia y ETA proceden del motor de rutas ya existente; si la ruta cambia por un desvío, el mismo monitor la vuelve a publicar.
6. **Confirmar llegada al sitio** requiere una lectura reciente y registra la hora y las coordenadas en auditoría. **Finalizar atención** pide confirmación, registra `hora_finalizacion`, libera la unidad y cierra la emergencia cuando termina su última asignación. El seguimiento de la misión termina y el conductor puede conservar su GPS para recibir otra emergencia. **Detener GPS** queda protegido durante una misión activa.
7. Desde la sesión de coordinación abra el dashboard o Unidades. El marcador cambia a `GPS`, muestra hora, velocidad y rumbo y se mueve sin recargar la página. Si el WebSocket cae, el mapa se reconecta y mantiene su consulta REST de respaldo cada cinco segundos.

## HTTPS en un teléfono real

Android/iOS solo entregan GPS en un contexto seguro. `http://localhost` sirve para un navegador del computador, pero una IP de red como `http://192.168.x.x:8080` no basta. Publique el mismo servidor detrás de HTTPS con un certificado confiable para el teléfono (por ejemplo, un proxy inverso de desarrollo con certificado local instalado en el teléfono). No se suben certificados, llaves ni wallets al repositorio.

## Seguridad y límites

- El endpoint exige `GPS/REPORTAR` y siempre toma el vehículo de `UNIDAD_CONDUCTOR`; el teléfono nunca envía un `vehicleId`.
- El servidor valida latitud, longitud, velocidad entre 0 y 250 km/h y rumbo entre 0 y 359 grados.
- La ubicación se persiste en `UBICACION_VEHICULO` con fuente `GPS`, y se actualiza `VEHICULO_EMERGENCIA` para el mapa.
- El monitor reutiliza el motor actual para recalcular ante un desvío. Si la red vial no ofrece otra ruta, ambas pantallas conservan la anterior y muestran el aviso.

## Despacho automático desde el botón de pánico

1. Exponga el frontend y el backend por HTTPS. Configure el origen HTTPS del frontend en
   `RUTAVIDA_CORS_ORIGINS` del backend y el origen HTTPS del backend en
   `RUTAVIDA_API_BASE_URL` de Vercel. El navegador abre el mismo canal Javalin por WSS.
2. Use una ambulancia disponible con un código que no empiece por `EJEMPLO-`, vinculada a
   un conductor activo con `GPS/REPORTAR` y `GPS/OPERAR`. Abra la pantalla del conductor
   en el teléfono, acepte ubicación precisa y compruebe «Transmitiendo».
3. Desde otra sesión, abra el dashboard de coordinación. Con una cuenta ciudadana
   autenticada, envíe un reporte médico en la cobertura de Valledupar.
4. Compruebe que el conductor y coordinación reciben la misma emergencia, ruta y ETA.
   Repita un reporte del mismo tipo a menos de 300 m dentro de 10 minutos y compruebe que
   se vincula al incidente abierto. La cercanía, el tipo y la ventana son un criterio
   operativo y pueden agrupar incidentes distintos que coincidan en esas condiciones.
5. Simule o recorra un desvío con la pantalla abierta, luego confirme llegada y finalice
   la atención. Para probar el caso sin disponibilidad, repita con un incidente distinto
   mientras todas las ambulancias estén ocupadas o sin GPS reciente. El incidente quedará
   reportado y coordinación recibirá un aviso.

`RUTAVIDA_PANIC_GPS_MAX_AGE_SECONDS` es opcional y tiene un valor predeterminado de 15.
La disponibilidad GPS reciente se conserva en memoria de una sola instancia del backend.
Tras reiniciarla, el teléfono debe enviar una nueva lectura. El despliegue de Render
configura una instancia. Se reutilizan las migraciones `07`, `08`, `14` y `15` existentes.

Si coordinación identifica una falsa alarma ya validada, primero debe cerrar su
emergencia según el flujo de despacho y después usar **Marcar reporte falso** en la lista
SOS. Se registra el enlace original en auditoría y se sigue aplicando el bloqueo vigente.
