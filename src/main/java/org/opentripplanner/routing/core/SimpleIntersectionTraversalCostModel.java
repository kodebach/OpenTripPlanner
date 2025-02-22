package org.opentripplanner.routing.core;

import java.io.Serializable;
import org.opentripplanner.graph_builder.module.osm.WayPropertySetSource.DrivingDirection;
import org.opentripplanner.routing.api.request.RoutingRequest;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleIntersectionTraversalCostModel extends AbstractIntersectionTraversalCostModel
        implements Serializable {

    public static final Logger LOG =
            LoggerFactory.getLogger(SimpleIntersectionTraversalCostModel.class);
    private final DrivingDirection drivingDirection;
    private final int minRightTurnAngle = 45;
    private final int maxRightTurnAngle = 135;

    private final int minLeftTurnAngle = 225;
    private final int maxLeftTurnAngle = 315;
    /**
     * Expected time it takes to make a right at a light.
     */
    private final double expectedRightAtLightTimeSec = 15.0;
    /**
     * Expected time it takes to continue straight at a light.
     */
    private final double expectedStraightAtLightTimeSec = 15.0;
    /**
     * Expected time it takes to turn left at a light.
     */
    private final double expectedLeftAtLightTimeSec = 15.0;
    /**
     * Expected time it takes to make a right without a stop light.
     */
    private final double expectedRightNoLightTimeSec = 8.0;
    /**
     * Expected time it takes to continue straight without a stop light.
     */
    private final double expectedStraightNoLightTimeSec = 5.0;
    /**
     * Expected time it takes to turn left without a stop light.
     */
    private final double expectedLeftNoLightTimeSec = 8.0;
    private final double safeBicycleTurnModifier = 5;
    /**
     * Since doing a left turn on a bike is quite dangerous we add a cost for it
     **/
    private final double acrossTrafficBicyleTurnMultiplier = safeBicycleTurnModifier * 3;

    public SimpleIntersectionTraversalCostModel(DrivingDirection drivingDirection) {
        this.drivingDirection = drivingDirection;
    }

    @Override
    public double computeTraversalCost(
            IntersectionVertex v, StreetEdge from, StreetEdge to, TraverseMode mode,
            RoutingRequest request, float fromSpeed, float toSpeed
    ) {

        // If the vertex is free-flowing then (by definition) there is no cost to traverse it.
        if (v.inferredFreeFlowing()) {
            return 0;
        }

        if (mode.isDriving()) {
            return computeDrivingTraversalCost(v, from, to, request);
        }
        else if (mode.isCycling()) {
            var c = computeCyclingTraversalCost(v, from, to, fromSpeed, toSpeed, request);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Turning from {} to {} has a cost of {}", from, to, c);
            }
            return c;
        }
        else {
            return computeNonDrivingTraversalCost(v, from, to, fromSpeed, toSpeed);
        }
    }

    /**
     * Returns if this angle represents a safe turn were incoming traffic does not have to be
     * crossed.
     * <p>
     * In right hand traffic countries (US, mainland Europe), this is a right turn.
     * In left hand traffic countries (UK, Japan) this is a left turn.
     */
    protected boolean isSafeTurn(int turnAngle) {
        switch (drivingDirection) {
            case RIGHT_HAND_TRAFFIC:
                return isRightTurn(turnAngle);
            case LEFT_HAND_TRAFFIC:
                return isLeftTurn(turnAngle);
            default:
                throw new RuntimeException("New driving direction introduced!");
        }
    }

    /**
     * Returns if this angle represents a turn across incoming traffic.
     * <p>
     * In right hand traffic countries (US) this is a left turn. In left hand traffic (UK) countries
     * this is a right turn.
     */
    protected boolean isTurnAcrossTraffic(int turnAngle) {
        switch (drivingDirection) {
            case RIGHT_HAND_TRAFFIC:
                return isLeftTurn(turnAngle);
            case LEFT_HAND_TRAFFIC:
                return isRightTurn(turnAngle);
            default:
                throw new RuntimeException("New driving direction introduced!");
        }
    }

    private double computeDrivingTraversalCost(
            IntersectionVertex v,
            StreetEdge from,
            StreetEdge to,
            RoutingRequest request
    ) {
        double turnCost = 0;

        int turnAngle = calculateTurnAngle(from, to, request);
        if (v.trafficLight) {
            // Use constants that apply when there are stop lights.
            if (isSafeTurn(turnAngle)) {
                turnCost = expectedRightAtLightTimeSec;
            }
            else if (isTurnAcrossTraffic(turnAngle)) {
                turnCost = expectedLeftAtLightTimeSec;
            }
            else {
                turnCost = expectedStraightAtLightTimeSec;
            }
        }
        else {

            //assume highway vertex
            if (from.getCarSpeed() > 25 && to.getCarSpeed() > 25) {
                return 0;
            }

            // Use constants that apply when no stop lights.
            if (isSafeTurn(turnAngle)) {
                turnCost = expectedRightNoLightTimeSec;
            }
            else if (isTurnAcrossTraffic(turnAngle)) {
                turnCost = expectedLeftNoLightTimeSec;
            }
            else {
                turnCost = expectedStraightNoLightTimeSec;
            }
        }

        return turnCost;
    }

    private double computeCyclingTraversalCost(
            IntersectionVertex v, StreetEdge from,
            StreetEdge to, float fromSpeed, float toSpeed, RoutingRequest request
    ) {
        var turnAngle = calculateTurnAngle(from, to, request);
        final var baseCost = computeNonDrivingTraversalCost(v, from, to, fromSpeed, toSpeed);

        if (isTurnAcrossTraffic(turnAngle)) {
            return baseCost * acrossTrafficBicyleTurnMultiplier;
        }
        else if (isSafeTurn(turnAngle)) {
            return baseCost * safeBicycleTurnModifier;
        }
        else {
            return baseCost;
        }

    }

    private boolean isLeftTurn(int turnAngle) {
        return turnAngle >= minLeftTurnAngle && turnAngle < maxLeftTurnAngle;
    }

    private boolean isRightTurn(int turnAngle) {
        return turnAngle >= minRightTurnAngle && turnAngle < maxRightTurnAngle;
    }
}
