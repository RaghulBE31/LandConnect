package in.tamiland.market;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.util.List;

/**
 * A land listing as stored in the plots.payload JSON column.
 *
 * <p>{@code approved} and {@code approvalLabel} are what the owner <em>claims</em>; they are never
 * proof of an official approval. {@code verification} is set by the server only:
 * DEMO (seed data), SELF_DECLARED (owner-provided, awaiting review) or ADMIN_REVIEWED
 * (an admin approved the listing text; this still does not certify title or boundaries).
 */
public record Plot(
        String id, String title, String type, String city, String district, String locality, String address,
        double latitude, double longitude, int cents, double acres, long price, long pricePerCent,
        String roadWidth, String facing, String zoning, boolean approved, String approvalLabel,
        String patta, String surveyNo, boolean electricity, boolean water, boolean fenced,
        String owner, String ownerRole, String phone, String description, String image,
        List<String> highlights, List<Point> boundary, String verification) {
    /** Accepts {"lat":..,"lng":..} (stored/API form) and [lat,lng] (what the owner form submits). */
    @JsonDeserialize(using = Point.Reader.class)
    public record Point(double lat, double lng) {
        static final class Reader extends JsonDeserializer<Point> {
            @Override public Point deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
                JsonNode node = parser.getCodec().readTree(parser);
                if (node.isArray() && node.size() == 2 && node.get(0).isNumber() && node.get(1).isNumber())
                    return new Point(node.get(0).asDouble(), node.get(1).asDouble());
                if (node.isObject() && node.path("lat").isNumber() && node.path("lng").isNumber())
                    return new Point(node.get("lat").asDouble(), node.get("lng").asDouble());
                throw com.fasterxml.jackson.databind.exc.MismatchedInputException.from(parser, Point.class, "A boundary point must be [lat, lng] or {lat, lng}");
            }
        }
    }

    /** Seed-data constructor: verification is assigned by the server on insert. */
    public Plot(String id, String title, String type, String city, String district, String locality, String address,
                double latitude, double longitude, int cents, double acres, long price, long pricePerCent,
                String roadWidth, String facing, String zoning, boolean approved, String approvalLabel,
                String patta, String surveyNo, boolean electricity, boolean water, boolean fenced,
                String owner, String ownerRole, String phone, String description, String image,
                List<String> highlights, List<Point> boundary) {
        this(id,title,type,city,district,locality,address,latitude,longitude,cents,acres,price,pricePerCent,roadWidth,facing,zoning,
                approved,approvalLabel,patta,surveyNo,electricity,water,fenced,owner,ownerRole,phone,description,image,highlights,boundary,null);
    }

    public Plot withContact(String newOwner, String newRole, String newPhone) {
        return new Plot(id,title,type,city,district,locality,address,latitude,longitude,cents,acres,price,pricePerCent,roadWidth,facing,zoning,
                approved,approvalLabel,patta,surveyNo,electricity,water,fenced,newOwner,newRole,newPhone,description,image,highlights,boundary,verification);
    }

    public Plot withVerification(String status) {
        return new Plot(id,title,type,city,district,locality,address,latitude,longitude,cents,acres,price,pricePerCent,roadWidth,facing,zoning,
                approved,approvalLabel,patta,surveyNo,electricity,water,fenced,owner,ownerRole,phone,description,image,highlights,boundary,status);
    }
}
