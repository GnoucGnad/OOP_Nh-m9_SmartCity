package vn.edu.hust.traffic.model.vehicle;

import vn.edu.hust.traffic.model.map.Intersection;
import vn.edu.hust.traffic.model.map.RoundaboutIntersection;
import vn.edu.hust.traffic.model.map.TrafficLight;
import java.util.List;

/**
 * Helper class for managing intersection entry priorities, collision lookahead, and right-of-way logic.
 */
public class IntersectionNavigator {

    static boolean hasBlockedDiagonalRightTurnEntry(Vehicle v, List<Vehicle> allVehicles,
            List<Intersection> intersections, Intersection intersection, int lightIdx) {
        if (intersection == null || intersection instanceof RoundaboutIntersection) {
            return false;
        }

        double myOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, v.x, v.y));
        double laneThreshold = Math.max(18.0, v.height + 8.0);
        double turnDirection = diagonalRightTurnDirectionForEntry(v.direction, lightIdx);
        double turnDirX = Math.cos(turnDirection);
        double turnDirY = Math.sin(turnDirection);
        double turnPathLength = diagonalRightTurnRemainingDistance(v, intersection, lightIdx, turnDirX, turnDirY);
        double pathLaneThreshold = Math.max(22.0, v.height + 16.0);
        for (Vehicle other : allVehicles) {
            if (other == v || other.insideRoundabout || other.exitedRoundabout) {
                continue;
            }
            Intersection otherTarget = other.getTargetIntersection(intersections);
            boolean nearIntersection = v.isInsideStandardIntersectionGuardZone(intersection,
                    other.x, other.y, other.x, other.y);
            if (otherTarget != intersection
                    && !intersection.getId().equals(other.activeIntersectionId)
                    && !nearIntersection) {
                continue;
            }

            double relX = other.x - v.x;
            double relY = other.y - v.y;
            double turnPathAhead = relX * turnDirX + relY * turnDirY;
            double turnPathLateral = Math.abs(relX * turnDirY - relY * turnDirX);
            double turnPathHardGap = v.getHalfLength() + other.getHalfLength() + 18.0;
            boolean occupiesTurnPath = turnPathAhead > -other.getHalfLength()
                    && turnPathAhead < turnPathLength + other.getHalfLength() + 55.0
                    && turnPathLateral < pathLaneThreshold;
            if (occupiesTurnPath
                    && (other.speed < v.baseSpeed * 0.65
                            || turnPathAhead < turnPathHardGap
                            || other.passedStopLine
                            || other.isTurningSmoothly
                            || other.isTurningDiagonally)) {
                return true;
            }

            if (other.getLightIdx(other.direction) != lightIdx) {
                continue;
            }

            double otherOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, other.x, other.y));
            if (Math.abs(otherOffset - myOffset) > laneThreshold) {
                continue;
            }

            double ahead = v.longitudinalDistanceAhead(lightIdx, other.x, other.y);
            double hardGap = v.getHalfLength() + other.getHalfLength() + 16.0;
            if (ahead > -other.getHalfLength() && ahead < 150.0
                    && (other.speed < v.baseSpeed * 0.45 || ahead < hardGap)) {
                return true;
            }
        }
        return false;
    }

    static double diagonalRightTurnDirectionForEntry(double currentDirection, int lightIdx) {
        if (lightIdx == 0) {
            return Math.PI / 4.0;
        }
        if (lightIdx == 1) {
            return -Math.PI * 3.0 / 4.0;
        }
        if (lightIdx == 2) {
            return Math.PI * 3.0 / 4.0;
        }
        if (lightIdx == 3) {
            return -Math.PI / 4.0;
        }
        return currentDirection;
    }

    static double diagonalRightTurnRemainingDistance(Vehicle v, Intersection intersection, int lightIdx,
            double dirX, double dirY) {
        double cx = intersection.getX();
        double cy = intersection.getY();
        double endLane = 65.0;
        if (lightIdx == 0) {
            return Math.max(40.0, (cx - endLane - v.x) / Math.max(0.01, dirX));
        } else if (lightIdx == 1) {
            return Math.max(40.0, (cx + endLane - v.x) / Math.min(-0.01, dirX));
        } else if (lightIdx == 2) {
            return Math.max(40.0, (cy - endLane - v.y) / Math.max(0.01, dirY));
        } else if (lightIdx == 3) {
            return Math.max(40.0, (cy + endLane - v.y) / Math.min(-0.01, dirY));
        }
        return 40.0;
    }

    static boolean hasUnsafeIntersectionEntryConflict(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx, TrafficLight.State myEffectiveLight) {
        if (intersection == null || intersection instanceof RoundaboutIntersection) {
            return false;
        }

        for (Vehicle other : allVehicles) {
            if (other == v) {
                continue;
            }
            if (!isRelevantToStandardIntersectionConflict(v, other, intersections, intersection)) {
                continue;
            }

            int otherLightIdx = other.getLightIdx(other.direction);
            boolean eitherTurning = v.isTurningSmoothly || v.isTurningDiagonally 
                    || other.isTurningSmoothly || other.isTurningDiagonally;
            boolean sameAxis = !eitherTurning && ((lightIdx < 2 && otherLightIdx < 2) || (lightIdx >= 2 && otherLightIdx >= 2));
            if (sameAxis) {
                continue;
            }

            TrafficLight.State otherEffectiveLight = v.effectiveLightForVehicle(other, intersections, otherLightIdx);
            if (!other.isPriorityVehicle
                    && !other.passedStopLine
                    && other.speed < 0.5
                    && (otherEffectiveLight == TrafficLight.State.RED
                            || otherEffectiveLight == TrafficLight.State.YELLOW)) {
                continue;
            }

            double conflictX = lightIdx < 2 ? other.x : v.x;
            double conflictY = lightIdx < 2 ? v.y : other.y;
            double myDistance = distanceToConflictPoint(lightIdx, conflictX, conflictY, v.x, v.y, v.getHalfLength());
            double otherDistance = distanceToConflictPoint(otherLightIdx, conflictX, conflictY,
                    other.x, other.y, other.getHalfLength());
            if (myDistance < -10.0 || myDistance > Vehicle.INTERSECTION_ENTRY_CONFLICT_LOOKAHEAD) {
                continue;
            }
            if (otherDistance < -70.0 || otherDistance > Vehicle.INTERSECTION_ENTRY_CONFLICT_LOOKAHEAD) {
                continue;
            }

            boolean otherInside = other.passedStopLine
                    || other.isTurningSmoothly
                    || other.isTurningDiagonally
                    || v.isInsideStandardIntersection(intersection, other.x, other.y);
            boolean mustYield = shouldYieldForEntryConflict(v, other, intersection, myEffectiveLight,
                    otherEffectiveLight, otherInside);
            if (!mustYield) {
                continue;
            }

            double myConflictSpeed = Math.max(1.0, Math.max(v.speed, v.baseSpeed * 0.45));
            double otherConflictSpeed = Math.max(1.0, Math.max(other.speed, other.baseSpeed * 0.35));
            double myTime = Math.max(0.0, myDistance) / myConflictSpeed;
            double otherTime = Math.max(0.0, otherDistance) / otherConflictSpeed;
            boolean occupiedConflict = otherInside
                    && myDistance < Vehicle.INTERSECTION_ENTRY_GUARD_DISTANCE
                    && otherDistance < Vehicle.INTERSECTION_ENTRY_CONFLICT_LOOKAHEAD;
            boolean convergingConflict = Math.abs(myTime - otherTime) < Vehicle.INTERSECTION_ENTRY_TIME_WINDOW;
            if (occupiedConflict || convergingConflict) {
                return true;
            }
        }
        return false;
    }

    static boolean shouldYieldForEntryConflict(Vehicle v, Vehicle other, Intersection intersection,
            TrafficLight.State myEffectiveLight, TrafficLight.State otherEffectiveLight, boolean otherInside) {
        if (otherInside) {
            return true;
        }
        if (!v.isPriorityVehicle && other.isPriorityVehicle) {
            return true;
        }
        if (v.isPriorityVehicle && !other.isPriorityVehicle) {
            return false;
        }

        boolean myGreen = myEffectiveLight == TrafficLight.State.GREEN;
        boolean otherGreen = otherEffectiveLight == TrafficLight.State.GREEN;
        if (myGreen != otherGreen) {
            return otherGreen;
        }

        if (v.activeIntersectionEntryOrder != Long.MAX_VALUE
                && other.activeIntersectionEntryOrder != Long.MAX_VALUE
                && v.activeIntersectionEntryOrder != other.activeIntersectionEntryOrder) {
            return v.activeIntersectionEntryOrder > other.activeIntersectionEntryOrder;
        }
        return v.id.compareTo(other.id) > 0;
    }

    static boolean isRelevantToStandardIntersectionConflict(Vehicle v, Vehicle other, List<Intersection> intersections,
            Intersection intersection) {
        if (intersection.getId().equals(other.activeIntersectionId)
                || v.isInsideStandardIntersectionGuardZone(intersection, other.x, other.y, other.x, other.y)) {
            return true;
        }
        return other.getTargetIntersection(intersections) == intersection;
    }

    static double distanceToConflictPoint(int lightIdx, double conflictX, double conflictY,
            double vehicleX, double vehicleY, double halfLength) {
        if (lightIdx == 0) {
            return conflictX - (vehicleX + halfLength);
        }
        if (lightIdx == 1) {
            return (vehicleX - halfLength) - conflictX;
        }
        if (lightIdx == 2) {
            return conflictY - (vehicleY + halfLength);
        }
        return (vehicleY - halfLength) - conflictY;
    }

    static double limitSpeedForPredictedIntersectionCollision(Vehicle v, double dt, double proposedSpeed,
            List<Vehicle> allVehicles, Intersection intersection) {
        if (proposedSpeed <= 0.0 || intersection instanceof RoundaboutIntersection) {
            return proposedSpeed;
        }

        double nextX = v.x + Math.cos(v.direction) * proposedSpeed * dt;
        double nextY = v.y + Math.sin(v.direction) * proposedSpeed * dt;
        if (!v.isInsideStandardIntersectionGuardZone(intersection, v.x, v.y, nextX, nextY)) {
            return proposedSpeed;
        }

        double limitedSpeed = proposedSpeed;
        for (Vehicle other : allVehicles) {
            if (other == v) {
                continue;
            }
            if (OvertakeManager.isActiveTurningBypassBlocker(v, other, intersection, v.getLightIdx(v.direction))) {
                continue;
            }

            double otherNextX = other.x + Math.cos(other.direction) * Math.max(0.0, other.speed) * dt;
            double otherNextY = other.y + Math.sin(other.direction) * Math.max(0.0, other.speed) * dt;
            if (!v.isInsideStandardIntersectionGuardZone(intersection, other.x, other.y, otherNextX, otherNextY)) {
                continue;
            }

            int lightIdx = v.getLightIdx(v.direction);
            int otherLightIdx = other.getLightIdx(other.direction);
            boolean eitherTurning = v.isTurningSmoothly || v.isTurningDiagonally 
                    || other.isTurningSmoothly || other.isTurningDiagonally;
            boolean sameAxis = !eitherTurning && ((lightIdx < 2 && otherLightIdx < 2) || (lightIdx >= 2 && otherLightIdx >= 2));
            if (sameAxis && !isSameCollisionLane(v, other, lightIdx)) {
                continue;
            }

            double collisionGap = Math.max(24.0,
                    (v.getHalfLength() + other.getHalfLength()) * 0.9 + Math.max(v.height, other.height) * 0.25);
            double currentDistance = Math.hypot(v.x - other.x, v.y - other.y);
            double nextDistance = Math.hypot(nextX - otherNextX, nextY - otherNextY);
            double pathDistance = v.segmentDistance(v.x, v.y, nextX, nextY, other.x, other.y, otherNextX, otherNextY);

            if (currentDistance > collisionGap * 1.35
                    && nextDistance > collisionGap * 1.25
                    && pathDistance > collisionGap) {
                continue;
            }

            boolean mustYield = shouldYieldForIntersectionCollision(v, other);
            boolean immediateCollision = currentDistance < collisionGap || nextDistance < collisionGap;
            boolean severeImmediateCollision = currentDistance < collisionGap * Vehicle.STANDARD_INTERSECTION_SEVERE_COLLISION_FACTOR
                    || nextDistance < collisionGap * Vehicle.STANDARD_INTERSECTION_SEVERE_COLLISION_FACTOR;
            boolean hardCollision = immediateCollision || pathDistance < collisionGap * 0.85;

            // Resolve mutual approach deadlocks at intersections
            boolean otherClearing = other.passedStopLine || v.isInsideStandardIntersection(intersection, other.x, other.y);
            if (!mustYield) {
                if (hardCollision && otherClearing) {
                    // We must yield to the vehicle already clearing the intersection to prevent accidents
                } else {
                    // We have priority, and the other vehicle is not inside, so we proceed (we do not stop)
                    continue;
                }
            } else {
                if (!hardCollision) {
                    continue;
                }
            }

            boolean insideIntersection = Math.hypot(v.x - intersection.getX(), v.y - intersection.getY())
                    < Vehicle.INTERSECTION_CLEAR_RADIUS;
            boolean clearingIntersection = v.passedStopLine && insideIntersection;
            if (!clearingIntersection) {
                limitedSpeed = 0.0;
            } else {
                boolean clearFirst = hasIntersectionClearPriorityOver(v, other, intersection);
                if (severeImmediateCollision && !clearFirst) {
                    limitedSpeed = 0.0;
                    continue;
                }
                double crawlFactor = clearFirst
                        ? (severeImmediateCollision ? Vehicle.CLEARING_MIN_SPEED_FACTOR * 0.55 : Vehicle.CLEARING_MIN_SPEED_FACTOR)
                        : Vehicle.CLEARING_MIN_SPEED_FACTOR * 0.75;
                limitedSpeed = Math.min(limitedSpeed, v.baseSpeed * crawlFactor);
            }
        }
        return limitedSpeed;
    }

    static boolean hasIntersectionClearPriorityOver(Vehicle v, Vehicle other, Intersection intersection) {
        if (v.isPriorityVehicle != other.isPriorityVehicle) {
            return v.isPriorityVehicle;
        }

        boolean thisClearing = v.passedStopLine || v.isInsideStandardIntersection(intersection, v.x, v.y);
        boolean otherClearing = other.passedStopLine || v.isInsideStandardIntersection(intersection, other.x, other.y);
        if (thisClearing != otherClearing) {
            return thisClearing;
        }

        if (v.activeIntersectionEntryOrder != Long.MAX_VALUE
                && other.activeIntersectionEntryOrder != Long.MAX_VALUE
                && v.activeIntersectionEntryOrder != other.activeIntersectionEntryOrder) {
            return v.activeIntersectionEntryOrder < other.activeIntersectionEntryOrder;
        }
        return v.id.compareTo(other.id) <= 0;
    }

    static boolean shouldYieldForIntersectionCollision(Vehicle v, Vehicle other) {
        if (!v.isPriorityVehicle && other.isPriorityVehicle) {
            return true;
        }
        if (v.isPriorityVehicle && !other.isPriorityVehicle) {
            return false;
        }
        if (v.passedStopLine != other.passedStopLine) {
            return !v.passedStopLine && other.passedStopLine;
        }
        if (v.activeIntersectionEntryOrder != Long.MAX_VALUE
                && other.activeIntersectionEntryOrder != Long.MAX_VALUE
                && v.activeIntersectionEntryOrder != other.activeIntersectionEntryOrder) {
            return v.activeIntersectionEntryOrder > other.activeIntersectionEntryOrder;
        }
        return v.id.compareTo(other.id) > 0;
    }

    static boolean isSameCollisionLane(Vehicle v, Vehicle other, int lightIdx) {
        double laneThreshold = Math.max(16.0, (v.height + other.height) * 0.55);
        if (lightIdx < 2) {
            return Math.abs(other.y - v.y) <= laneThreshold;
        }
        return Math.abs(other.x - v.x) <= laneThreshold;
    }
}
