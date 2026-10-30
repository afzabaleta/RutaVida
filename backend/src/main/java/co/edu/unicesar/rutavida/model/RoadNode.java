package co.edu.unicesar.rutavida.model;

import co.edu.unicesar.rutavida.model.enums.NodeType;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;

/**
 * A crossing or connection point of the road network (table NODO_VIAL).
 * Only data: the business rules live in the service layer.
 */
public class RoadNode {

    private long id;
    private final Long osmId;
    private final String reference;
    private final GeoPoint location;
    private final NodeType nodeType;
    private final RecordStatus status;

    public RoadNode(long id,
            Long osmId,
            String reference,
            GeoPoint location,
            NodeType nodeType,
            RecordStatus status) {
        this.id = id;
        this.osmId = osmId;
        this.reference = reference;
        this.location = location;
        this.nodeType = nodeType;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Long getOsmId() {
        return osmId;
    }

    public String getReference() {
        return reference;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public RecordStatus getStatus() {
        return status;
    }
}
