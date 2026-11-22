// RutaVida - Consulta de Overpass para la red vial de Valledupar (DT-14)
// Area urbana acordada el 19/11/2026 (sur, oeste, norte, este):
//   10.405, -73.305, 10.510, -73.215
// Reemplaza la zona de la demostracion del 21/10/2026 (10.435, -73.280, 10.480, -73.235).
// Trae las vias para vehiculos y sus puntos. OsmImporter vuelve a filtrar los tipos.
// Datos: (c) OpenStreetMap contributors, licencia ODbL.
[out:xml][timeout:180];
(
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary)(_link)?$|^(unclassified|residential|living_street)$"](10.405,-73.305,10.510,-73.215);
  >;
);
out body;
