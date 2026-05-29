package vn.edu.hust.traffic.model.vehicle;

import vn.edu.hust.traffic.model.map.Intersection;
import java.util.List;

/**
 * Helper class for managing turning trajectories, including smooth left turns
 * and diagonal right turns.
 */
public class TrajectoryController {

    static boolean continueSmoothTurn(Vehicle v, double dt, List<Vehicle> allVehicles, List<Intersection> intersections) {
        if (!v.isTurningSmoothly) {
            return false;
        }

        double turnSpeedFactor = smoothTurnSpeedFactor(v, allVehicles, dt);
        if (turnSpeedFactor <= 0.0) {
            v.speed = 0;
            return true;
        }

        v.smoothTurnElapsed += Math.max(0.0, dt) * turnSpeedFactor;
        double rawT = v.smoothTurnElapsed / v.smoothTurnDuration;
        double t = Math.min(1.0, rawT);
        double eased = v.smoothStep(t);

        v.x = v.smoothTurnStartX + (v.smoothTurnEndX - v.smoothTurnStartX) * eased;
        v.y = v.smoothTurnStartY + (v.smoothTurnEndY - v.smoothTurnStartY) * eased;
        v.direction = v.interpolateAngle(v.smoothTurnStartDirection, v.smoothTurnEndDirection, eased);
        v.speed = v.baseSpeed * turnSpeedFactor;

        if (t >= 1.0) {
            v.x = v.smoothTurnEndX;
            v.y = v.smoothTurnEndY;
            v.direction = v.smoothTurnEndDirection;
            v.isTurningSmoothly = false;
            if (v.smoothTurnCompletesTurn) {
                v.hasTurned = true;
                v.passedStopLine = true;
                v.turnExitClearanceTime = Vehicle.TURN_EXIT_CLEARANCE_DURATION;
            }
        }

        return true;
    }

    static double smoothTurnSpeedFactor(Vehicle v, List<Vehicle> allVehicles, double dt) {
        double pathX = v.smoothTurnEndX - v.smoothTurnStartX;
        double pathY = v.smoothTurnEndY - v.smoothTurnStartY;
        double pathLength = Math.hypot(pathX, pathY);
        if (pathLength < 1.0) {
            return 1.0;
        }
        if ((v.passedStopLine || v.exitedRoundabout) && pathLength < Vehicle.SMOOTH_TURN_SHORT_PATH_DISTANCE) {
            for (Vehicle other : allVehicles) {
                if (other == v) {
                    continue;
                }
                double currentDistance = Math.hypot(other.x - v.x, other.y - v.y);
                double overlapDistance = Math.max(18.0,
                        (v.getHalfLength() + other.getHalfLength()) * 0.55);
                if (currentDistance < overlapDistance * 0.65) {
                    if (v.hasRoundaboutExitSmoothPriorityOver(other)) {
                        continue;
                    }
                    // Tie-breaker to prevent mutual deadlock
                    if (v.id.compareTo(other.id) < 0) {
                        continue;
                    }
                    double relX = other.x - v.x;
                    double relY = other.y - v.y;
                    double ahead = (relX * pathX + relY * pathY) / pathLength;
                    if (ahead < 0.0 && currentDistance > overlapDistance * 0.35) {
                        continue;
                    }
                    return 0.0;
                }
            }
            return 1.0;
        }

        double dirX = pathX / pathLength;
        double dirY = pathY / pathLength;
        double nextElapsed = Math.min(v.smoothTurnDuration, v.smoothTurnElapsed + Math.max(0.0, dt));
        double nextT = nextElapsed / v.smoothTurnDuration;
        double nextEased = v.smoothStep(nextT);
        double nextX = v.smoothTurnStartX + pathX * nextEased;
        double nextY = v.smoothTurnStartY + pathY * nextEased;
        double safeGap = Math.max(34.0, v.getHalfLength() + 24.0);
        double factor = 1.0;

        for (Vehicle other : allVehicles) {
            if (other == v) {
                continue;
            }

            double relX = other.x - v.x;
            double relY = other.y - v.y;
            double ahead = relX * dirX + relY * dirY;
            double lateral = Math.abs(relX * dirY - relY * dirX);
            double laneThreshold = Math.max(18.0, (v.height + other.height) * 0.7);
            double combinedGap = safeGap + other.getHalfLength();

            if (ahead > -other.getHalfLength() && ahead < combinedGap && lateral < laneThreshold) {
                double hardGap = v.getHalfLength() + other.getHalfLength() + 6.0;
                if (ahead <= hardGap) {
                    return 0.0;
                }
                double localFactor = (ahead - hardGap) / Math.max(1.0, combinedGap - hardGap);
                factor = Math.min(factor, Math.max(0.18, Math.min(1.0, localFactor)));
            }

            double nextDistance = Math.hypot(other.x - nextX, other.y - nextY);
            double overlapDistance = Math.max(18.0, (v.getHalfLength() + other.getHalfLength()) * 0.55);
            if (nextDistance < overlapDistance
                    && (other.isTurningSmoothly || other.isTurningDiagonally || other.insideRoundabout || other.exitedRoundabout)) {
                if (nextDistance < overlapDistance * 0.75) {
                    boolean sameTurnStream = v.originalLightIdx == other.originalLightIdx
                            && v.turnIntention == other.turnIntention
                            && v.turnIntention != 0;
                    // Tie-breaker to prevent mutual deadlock (only if not in same turn stream queue)
                    if (!sameTurnStream && v.id.compareTo(other.id) < 0) {
                        factor = Math.min(factor, 0.18);
                        continue;
                    }
                    return 0.0;
                }
                factor = Math.min(factor, 0.18);
            }
        }

        return factor;
    }

    static boolean continueDiagonalRightTurn(Vehicle v, double dt, List<Vehicle> allVehicles) {
        if (!v.isTurningDiagonally) {
            return false;
        }

        double turnSpeedFactor = diagonalRightTurnSpeedFactor(v, allVehicles, dt);
        if (turnSpeedFactor <= 0.0) {
            v.speed = 0;
            return true;
        }

        v.speed = v.baseSpeed * turnSpeedFactor;
        v.movePhysically(dt);
        finishDiagonalRightTurnIfNeeded(v, v.diagonalTurnCenterX, v.diagonalTurnCenterY);
        return true;
    }

    static double diagonalRightTurnSpeedFactor(Vehicle v, List<Vehicle> allVehicles, double dt) {
        double dirX = Math.cos(v.direction);
        double dirY = Math.sin(v.direction);
        double nextX = v.x + dirX * v.baseSpeed * dt;
        double nextY = v.y + dirY * v.baseSpeed * dt;
        double safeGap = Math.max(34.0, v.getHalfLength() + 24.0);
        double factor = 1.0;

        for (Vehicle other : allVehicles) {
            if (other == v) {
                continue;
            }

            double relX = other.x - v.x;
            double relY = other.y - v.y;
            double ahead = relX * dirX + relY * dirY;
            double lateral = Math.abs(relX * dirY - relY * dirX);
            double laneThreshold = Math.max(18.0, (v.height + other.height) * 0.8);
            double combinedGap = safeGap + other.getHalfLength();
            double currentDistance = Math.hypot(other.x - v.x, other.y - v.y);
            double overlapDistance = Math.max(18.0, (v.getHalfLength() + other.getHalfLength()) * 0.55);
            boolean sameTurnStream = v.originalLightIdx == other.originalLightIdx
                    && v.turnIntention == other.turnIntention
                    && v.turnIntention != 0;

            if (ahead > -other.getHalfLength() && ahead < combinedGap && lateral < laneThreshold) {
                double hardGap = v.getHalfLength() + other.getHalfLength() + 6.0;
                if (ahead <= hardGap) {
                    double physicalGap = ahead - v.getHalfLength() - other.getHalfLength();
                    if (sameTurnStream) {
                        if (physicalGap <= 0.0 || currentDistance < overlapDistance * 0.6) {
                            return 0.0;
                        }
                        factor = Math.min(factor, diagonalTurnCrawlFactor(v, ahead, other, dt));
                        continue;
                    }
                    if (!v.passedStopLine || currentDistance < overlapDistance * 0.6) {
                        if (v.id.compareTo(other.id) < 0) {
                            factor = Math.min(factor, diagonalTurnCrawlFactor(v, ahead, other, dt));
                            continue;
                        }
                        return 0.0;
                    }
                    factor = Math.min(factor, diagonalTurnCrawlFactor(v, ahead, other, dt));
                    continue;
                }
                double localFactor = (ahead - hardGap) / Math.max(1.0, combinedGap - hardGap);
                factor = Math.min(factor, Math.max(0.18, Math.min(1.0, localFactor)));
            }

            double nextDistance = Math.hypot(other.x - nextX, other.y - nextY);
            if (sameTurnStream
                    && nextDistance < overlapDistance
                    && (other.isTurningDiagonally || other.isTurningSmoothly || other.hasTurned)) {
                if (nextDistance < overlapDistance * 0.75) {
                    return 0.0;
                }
                factor = Math.min(factor, 0.18);
            }
        }

        return factor;
    }

    static double diagonalTurnCrawlFactor(Vehicle v, double ahead, Vehicle other, double dt) {
        double physicalGap = ahead - v.getHalfLength() - other.getHalfLength();
        if (physicalGap > 0.0) {
            double crawlByGap = physicalGap / Math.max(0.016, dt) * 0.45;
            double factorByGap = crawlByGap / Math.max(1.0, v.baseSpeed);
            return Math.max(Vehicle.DIAGONAL_TURN_CRAWL_MIN_SPEED_FACTOR,
                    Math.min(Vehicle.DIAGONAL_TURN_CRAWL_MAX_SPEED_FACTOR, factorByGap));
        }
        return Vehicle.DIAGONAL_TURN_CRAWL_MIN_SPEED_FACTOR;
    }

    static void finishDiagonalRightTurnIfNeeded(Vehicle v, double cx, double cy) {
        if (!v.isTurningDiagonally || v.hasTurned || v.turnIntention != 2) {
            return;
        }

        int entryLightIdx = v.getIntersectionEntryLightIdx();
        double endLane = 65.0;
        boolean endDiagonal = false;
        if (entryLightIdx == 0) endDiagonal = (v.x >= cx - endLane);
        else if (entryLightIdx == 1) endDiagonal = (v.x <= cx + endLane);
        else if (entryLightIdx == 2) endDiagonal = (v.y >= cy - endLane);
        else if (entryLightIdx == 3) endDiagonal = (v.y <= cy + endLane);

        if (!endDiagonal) {
            return;
        }

        v.isTurningDiagonally = false;
        double targetX = v.x;
        double targetY = v.y;
        double targetDirection = v.direction;
        if (entryLightIdx == 0) { targetX = cx - endLane; targetDirection = Math.PI / 2; }
        else if (entryLightIdx == 1) { targetX = cx + endLane; targetDirection = -Math.PI / 2; }
        else if (entryLightIdx == 2) { targetY = cy - endLane; targetDirection = Math.PI; }
        else if (entryLightIdx == 3) { targetY = cy + endLane; targetDirection = 0; }
        v.startSmoothTurn(targetX, targetY, targetDirection, true, Vehicle.SMOOTH_TURN_DURATION);
    }
}
