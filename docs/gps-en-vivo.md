# GPS en vivo de unidades

RutaVida conserva su backend Java/Javalin y su mapa Leaflet. No usa un segundo backend Node.js ni React: el teléfono usa la Geolocation API, envía una lectura protegida cada cinco segundos a `POST /api/gps/position` y Javalin difunde la ubicación confirmada por WebSocket nativo en `/ws/gps`.

## Preparación de la demostración

1. Ejecute `database/14_gps_conductor.sql` y después `database/15_operacion_conductor.sql` después de los scripts de base ya instalados. El segundo agrega la hora de finalización y el permiso `GPS/OPERAR`.
2. En Administración cree el usuario con rol `BOMBERO_PARAMEDICO` y registre la ambulancia.
3. Consulte sus identificadores y cree la relación indicada al final de `14_gps_conductor.sql`. Una persona solo puede tener una unidad y una unidad solo un conductor.
4. Cierre sesión y vuelva a entrar con el conductor para que se lean los permisos nuevos. Inicie `WebServer`, entre con el conductor y abra **Mi GPS de unidad**. Pulse **Iniciar GPS** y acepte ubicación precisa.
5. El módulo muestra solo la ambulancia propia, su emergencia, hospitales y bases. La ruta, distancia y ETA proceden del motor de rutas ya existente; si la ruta cambia por un desvío, el mismo monitor la vuelve a publicar.
6. **Confirmar llegada al sitio** registra la llegada y deja el GPS activo. **Finalizar atención** pide confirmación, registra `hora_finalizacion`, libera la unidad y cierra la emergencia solamente si no quedan otras asignaciones abiertas. Ninguna de las dos acciones detiene GPS; solo lo hace **Detener GPS**.
7. Desde la sesión de coordinación abra el dashboard o Unidades. El marcador cambia a `GPS`, muestra hora, velocidad y rumbo y se mueve sin recargar la página. Si el WebSocket cae, el mapa se reconecta y mantiene su consulta REST de respaldo cada cinco segundos.

## HTTPS en un teléfono real

Android/iOS solo entregan GPS en un contexto seguro. `http://localhost` sirve para un navegador del computador, pero una IP de red como `http://192.168.x.x:8080` no basta. Publique el mismo servidor detrás de HTTPS con un certificado confiable para el teléfono (por ejemplo, un proxy inverso de desarrollo con certificado local instalado en el teléfono). No se suben certificados, llaves ni wallets al repositorio.

## Seguridad y límites

- El endpoint exige `GPS/REPORTAR` y siempre toma el vehículo de `UNIDAD_CONDUCTOR`; el teléfono nunca envía un `vehicleId`.
- El servidor valida latitud, longitud, velocidad entre 0 y 250 km/h y rumbo entre 0 y 359 grados.
- La ubicación se persiste en `UBICACION_VEHICULO` con fuente `GPS`, y se actualiza `VEHICULO_EMERGENCIA` para el mapa.
- Una posición real no recalcula, modifica ni invalida rutas. El motor de rutas queda independiente.
