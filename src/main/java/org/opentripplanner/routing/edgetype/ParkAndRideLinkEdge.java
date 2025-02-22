package org.opentripplanner.routing.edgetype;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.common.geometry.GeometryUtils;
import org.opentripplanner.common.geometry.SphericalDistanceLibrary;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.StateEditor;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.vertextype.ParkAndRideVertex;

import java.util.Locale;

/**
 * This represents the connection between a P+R and the street access.
 * 
 * @author laurent
 */
public class ParkAndRideLinkEdge extends Edge {

    private static final long serialVersionUID = 1L;

    /*
     * By how much we have to really walk compared to straight line distance. This is a magic factor
     * as we really can't guess, unless we know 1) where the user will park, and 2) we route inside
     * the parking lot.
     *
     * TODO: perhaps all of this obstruction and distance calculation should just be reduced to
     * a single static cost. Parking lots are not that big, and these are all guesses.
     */
    private double WALK_OBSTRUCTION_FACTOR = 2.0;

    private double DRIVE_OBSTRUCTION_FACTOR = 2.0;

    /* This is magic too. Driver tend to drive slowly in P+R. */
    private double DRIVE_SPEED_MS = 3;

    private ParkAndRideVertex parkAndRideVertex;

    private boolean exit;

    @SuppressWarnings("unused")
    private LineString geometry = null;

    /** The estimated distance between the center of the P+R envelope and the street access. */
    private double linkDistance;

    public ParkAndRideLinkEdge(ParkAndRideVertex from, Vertex to) {
        super(from, to);
        parkAndRideVertex = from;
        exit = true;
        initGeometry();
    }

    public ParkAndRideLinkEdge(Vertex from, ParkAndRideVertex to) {
        super(from, to);
        parkAndRideVertex = to;
        exit = false;
        initGeometry();
    }

    private void initGeometry() {
        Coordinate fromc = fromv.getCoordinate();
        Coordinate toc = tov.getCoordinate();
        geometry = GeometryUtils.getGeometryFactory().createLineString(
                new Coordinate[] { fromc, toc });
        linkDistance = SphericalDistanceLibrary.distance(fromc, toc);
    }

    @Override
    public String getName() {
        // TODO I18n
        return parkAndRideVertex.getName() + (exit ? " (exit)" : " (entrance)");
    }

    @Override
    public String getName(Locale locale) {
        //TODO: localize
        return this.getName();
    }

    @Override
    public State traverse(State s0) {
        // Do not enter park and ride mechanism if it's not activated in the routing request.
        if ( ! s0.getOptions().parkAndRide) {
            return null;
        }
        Edge backEdge = s0.getBackEdge();
        boolean back = s0.getOptions().arriveBy;
        // If we are exiting (or entering-backward), check if we
        // really parked a car: this will prevent using P+R as
        // shortcut.
        if ((back != exit) && !(backEdge instanceof ParkAndRideEdge))
            return null;

        StateEditor s1 = s0.edit(this);
        TraverseMode mode = s0.getNonTransitMode();
        if (mode == TraverseMode.WALK) {
            // Walking
            double walkTime = linkDistance * WALK_OBSTRUCTION_FACTOR
                    / s0.getOptions().walkSpeed;
            s1.incrementTimeInSeconds((int) Math.round(walkTime));
            s1.incrementWeight(walkTime);
            s1.incrementWalkDistance(linkDistance);
            s1.setBackMode(TraverseMode.WALK);
        } else if (mode == TraverseMode.CAR) {
            // Driving
            double driveTime = linkDistance * DRIVE_OBSTRUCTION_FACTOR / DRIVE_SPEED_MS;
            s1.incrementTimeInSeconds((int) Math.round(driveTime));
            s1.incrementWeight(driveTime);
            s1.setBackMode(TraverseMode.CAR);
        } else {
            // Can't cycle in/out a P+R.
            return null;
        }
        return s1.makeState();
    }

    @Override
    public LineString getGeometry() {
        return null;
    }

    @Override
    public String toString() {
        return "ParkAndRideLinkEdge(" + fromv + " -> " + tov + ")";
    }
}
