
package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.exception.ValidationException;
import co.edu.unicesar.rutavida.model.enums.NodeType;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.util.ValidationUtil;

/** A crossing or connection point of the road network (NODO_VIAL). */
public class RoadNode implements Locatable {

    private long id;
    private final Long osmId;
    private final String reference;
    private final GeoPoint location;
    private final NodeType nodeType;
    private final RecordStatus status;

    public RoadNode(long id, Long osmId, String reference,
                    GeoPoint location, NodeType nodeType,
                    RecordStatus status) {

        if (id < 0) {
            throw new ValidationException(
                    "El identificador no puede ser negativo.");
        }

        if (osmId != null && osmId <= 0) {
            throw new ValidationException(
                    "El identificador de OpenStreetMap no es válido.");
        }

        this.id = id;
        this.osmId = osmId;
        this.reference = ValidationUtil.optionalText(
                reference, "referencia", 120);
        this.location = ValidationUtil.requireNonNull(
                location, "ubicación");
        this.nodeType = ValidationUtil.requireNonNull(
                nodeType, "tipo de nodo");
        this.status = ValidationUtil.requireNonNull(
                status, "estado");
    }

    /** Assigns an identifier only to an unsaved node. */
    public void assignId(long newId) {
        if (id != 0) {
            throw new IllegalStateException(
                    "The entity already has id " + id);
        }

        id = ValidationUtil.requirePositiveId(
                newId, "identificador");
    }

    public long getId() {
        return id;
    }

    public Long getOsmId() {
        return osmId;
    }

    public String getReference() {
        return reference;
    }

    @Override
    public GeoPoint getLocation() {
        return location;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public RecordStatus getStatus() {
        return status;
    }

    /** Only active nodes are loaded into the routing graph. */
    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        return other instanceof RoadNode that
                && id != 0
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "RoadNode{id=" + id
                + ", osmId=" + osmId
                + ", type=" + nodeType + "}";
    }
}
