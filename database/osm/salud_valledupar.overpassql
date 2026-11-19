// RutaVida - Consulta de Overpass: hospitales y clinicas del area urbana (DT-14)
// Misma zona de valledupar_ciudad.overpassql. Sirve para tomar la posicion real
// de cada hospital o clinica (con su id de OpenStreetMap como fuente).
// No se carga tal cual: el equipo escoge los lugares en 10_hospitales_y_unidades.sql.
// Datos: (c) OpenStreetMap contributors, licencia ODbL.
[out:json][timeout:60];
(
  nwr["amenity"~"^(hospital|clinic)$"](10.405,-73.305,10.510,-73.215);
  nwr["healthcare"~"^(hospital|clinic)$"](10.405,-73.305,10.510,-73.215);
  nwr["emergency"="ambulance_station"](10.405,-73.305,10.510,-73.215);
  nwr["amenity"="fire_station"](10.405,-73.305,10.510,-73.215);
);
out center tags;
