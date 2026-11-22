/**
 * <b>Responsibility.</b> Development tool that converts an OpenStreetMap extract
 * (.osm XML downloaded from Overpass) into the SQL script that loads NODO_VIAL and
 * TRAMO_VIAL ({@code database/05_red_vial_valledupar.sql}). It reads a file and writes
 * a file: it never connects to Oracle.
 *
 * <p><b>Allowed dependencies.</b> May depend on {@code model} ({@code GeoPoint},
 * {@code NodeType}) and the JDK. Must NOT import java.sql, dao, service, graph, traffic,
 * controller, view or app.
 *
 * <p><b>Principles.</b> GRASP Pure Fabrication and High Cohesion: reading (OsmParser),
 * building the network (RoadNetworkBuilder) and writing SQL (RoadNetworkSqlWriter) are
 * separate classes that can be tested on their own.
 *
 * <p><b>Owner.</b> Kendrick
 */
package co.edu.unicesar.rutavida.osm;