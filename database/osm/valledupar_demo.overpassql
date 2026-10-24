// RutaVida - Consulta de Overpass para la red vial de la demostracion (K4)
// Zona acordada el 21/10/2026 (sur, oeste, norte, este):
//   10.435, -73.280, 10.480, -73.235
// Trae las vias para vehiculos y sus puntos. OsmImporter vuelve a filtrar los tipos.
// Datos: (c) OpenStreetMap contributors, licencia ODbL.
[out:xml][timeout:90];
(
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary)(_link)?$|^(unclassified|residential|living_street)$"](10.435,-73.280,10.480,-73.235);
  >;
);
out body;