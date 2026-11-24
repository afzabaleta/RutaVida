# Mapa enfocado para Compartir ubicación

## Análisis y componentes afectados

`frontend/conductor.html` ya es la pantalla exclusiva del conductor. `frontend/js/conductor.js`
consulta su unidad y su misión mediante `/api/gps/unit` y `/api/gps/mission`; por ello no consume ni
dibuja la flota completa. Filtra además estaciones de bomberos, policía y Defensa Civil: solo quedan
las bases operativas de ambulancia. La ruta activa sigue viniendo de `listMissionRoutes` y el GPS
conserva la misma Geolocation API, frecuencia de envío y endpoints existentes.

Los catálogos ya disponibles son `/api/hospitals` y `/api/stations`. En los datos actuales, las
clínicas y centros médicos se registran en el catálogo de hospitales; se clasifican visualmente por
el nombre. No se requieren cambios de backend ni de base de datos.

## Cambios de Leaflet

- La ambulancia propia conserva el marcador grande y prioritario; la emergencia asignada conserva
  su marcador destacado y la polilínea de la ruta no se modifica.
- Hospitales, clínicas, centros médicos y bases usan `L.divIcon` de 11 px al alejarse y 15 px al
  acercarse. Los colores son rojo, azul, verde y naranja, respectivamente.
- Se quitaron los `circleMarker` genéricos que originaban los puntos azules sin significado claro.
- A zoom menor de 14 se aplica una agrupación ligera basada en cuadrícula. El grupo muestra una
  cantidad y, al tocarlo, acerca el mapa para revelar cada referencia individual. No agrega una
  dependencia externa a Leaflet.

## Hospitales más cercanos

La lista toma la última lectura GPS de la ambulancia, calcula la distancia en línea recta a cada
hospital y los ordena de menor a mayor distancia. El ETA usa la velocidad real si el dispositivo la
entrega; cuando no existe, utiliza 35 km/h como estimación prudente. Cada nueva lectura GPS vuelve
a ordenar la lista. El conductor puede seleccionar un hospital como destino de referencia en el
mapa; el cambio oficial a traslado continúa protegido en el flujo de coordinación y no reemplaza la
ruta activa de la emergencia.

## Vista conceptual

```text
 Ambulancia propia (principal)       🚑 marcador grande
 Emergencia asignada                 !  marcador destacado
 Hospital / Clínica / Centro / Base  ·  puntos pequeños rojo / azul / verde / naranja
 Zoom lejano                         3  grupo de referencias cercanas
```

## Casos de prueba

1. Iniciar sesión como conductor: solo aparece su ambulancia, su emergencia asignada y las
   referencias médicas/bases; no aparecen policías, bomberos ni otras ambulancias.
2. Alejar el mapa: los puntos cercanos se agrupan; tocar un grupo debe acercar y mostrar sus puntos.
3. Acercar a nivel 14 o mayor: los iconos individuales deben medir entre 11 y 15 px y conservar su
   color por tipo.
4. Activar GPS y abrir **Hospitales más cercanos**: verificar orden, distancia y ETA; mover el
   teléfono y comprobar la actualización.
5. Seleccionar un hospital: el mapa debe enfocarlo y marcarlo, sin eliminar ni recalcular la ruta
   existente de la emergencia.
