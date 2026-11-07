package co.edu.unicesar.rutavida.api;

import co.edu.unicesar.rutavida.model.Route;
import co.edu.unicesar.rutavida.model.RouteSegment;
import co.edu.unicesar.rutavida.model.enums.AlertType;
import co.edu.unicesar.rutavida.model.enums.AssignmentStatus;
import co.edu.unicesar.rutavida.model.enums.AuditResult;
import co.edu.unicesar.rutavida.model.enums.CodedEnum;
import co.edu.unicesar.rutavida.model.enums.EmergencyStatus;
import co.edu.unicesar.rutavida.model.enums.HospitalLevel;
import co.edu.unicesar.rutavida.model.enums.IncidentOrigin;
import co.edu.unicesar.rutavida.model.enums.IncidentStatus;
import co.edu.unicesar.rutavida.model.enums.IncidentType;
import co.edu.unicesar.rutavida.model.enums.NodeType;
import co.edu.unicesar.rutavida.model.enums.PositionSource;
import co.edu.unicesar.rutavida.model.enums.RecordStatus;
import co.edu.unicesar.rutavida.model.enums.RoadSegmentStatus;
import co.edu.unicesar.rutavida.model.enums.RouteStatus;
import co.edu.unicesar.rutavida.model.enums.RouteType;
import co.edu.unicesar.rutavida.model.enums.Severity;
import co.edu.unicesar.rutavida.model.enums.TrafficLevel;
import co.edu.unicesar.rutavida.model.enums.TrafficLightState;
import co.edu.unicesar.rutavida.model.enums.TrafficSource;
import co.edu.unicesar.rutavida.model.enums.VehicleOperationalStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.List;

/**
 * The JSON rules of docs/api.md, section 1, in one place:
 * <ul>
 *   <li>catalog values travel with their Spanish code ({@code Severity.CRITICAL} → "CRITICA"),
 *       the same as in Oracle, and are read back with {@code fromCode};</li>
 *   <li>dates as ISO text without zone ("2026-11-04T09:30:15");</li>
 *   <li>unknown fields in a request are ignored (for example a reportedAt sent by the web);</li>
 *   <li>a Route travels without its segments (docs/api.md, 5.5): the history is for reading.</li>
 * </ul>
 * The model is not annotated: it cannot depend on the JSON library.
 */
public final class JsonMapperFactory {

    private JsonMapperFactory() {
    }

    public static ObjectMapper create() {
        SimpleModule codes = new SimpleModule("RutaVidaCodes");
        codes.addSerializer(CodedEnum.class, new CodedEnumSerializer());
        addCodedEnum(codes, AlertType.class);
        addCodedEnum(codes, AssignmentStatus.class);
        addCodedEnum(codes, AuditResult.class);
        addCodedEnum(codes, EmergencyStatus.class);
        addCodedEnum(codes, HospitalLevel.class);
        addCodedEnum(codes, IncidentOrigin.class);
        addCodedEnum(codes, IncidentStatus.class);
        addCodedEnum(codes, IncidentType.class);
        addCodedEnum(codes, NodeType.class);
        addCodedEnum(codes, PositionSource.class);
        addCodedEnum(codes, RecordStatus.class);
        addCodedEnum(codes, RoadSegmentStatus.class);
        addCodedEnum(codes, RouteStatus.class);
        addCodedEnum(codes, RouteType.class);
        addCodedEnum(codes, Severity.class);
        addCodedEnum(codes, TrafficLevel.class);
        addCodedEnum(codes, TrafficLightState.class);
        addCodedEnum(codes, TrafficSource.class);
        addCodedEnum(codes, VehicleOperationalStatus.class);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(codes);
        mapper.addMixIn(Route.class, RouteWithoutSegments.class);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    /**
     * Jackson mix-in: the annotations are written here, outside the model, and Jackson applies
     * them to Route as if they were on it.
     */
    abstract static class RouteWithoutSegments {
        @JsonIgnore
        abstract List<RouteSegment> getSegments();
    }

    private static <E extends Enum<E> & CodedEnum> void addCodedEnum(SimpleModule module, Class<E> type) {
        module.addDeserializer(type, new CodedEnumDeserializer<>(type));
    }

    /** Writes any CodedEnum as its code. */
    static final class CodedEnumSerializer extends StdSerializer<CodedEnum> {

        private static final long serialVersionUID = 1L;

        CodedEnumSerializer() {
            super(CodedEnum.class);
        }

        @Override
        public void serialize(CodedEnum value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString(value.code());
        }
    }

    /** Reads a code back into its constant; an unknown code is a bad request. */
    static final class CodedEnumDeserializer<E extends Enum<E> & CodedEnum> extends StdDeserializer<E> {

        private static final long serialVersionUID = 1L;

        private final Class<E> type;

        CodedEnumDeserializer(Class<E> type) {
            super(type);
            this.type = type;
        }

        @Override
        public E deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String code = parser.getValueAsString();
            try {
                return CodedEnum.fromCode(type, code);
            } catch (IllegalArgumentException e) {
                throw JsonMappingException.from(parser,
                        "Unknown code '" + code + "' for " + type.getSimpleName(), e);
            }
        }
    }
}