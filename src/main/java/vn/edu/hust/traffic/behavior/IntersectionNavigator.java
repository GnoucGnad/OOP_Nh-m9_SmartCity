package vn.edu.hust.traffic.behavior;

import vn.edu.hust.traffic.model.vehicle.Vehicle;
import vn.edu.hust.traffic.model.map.Intersection;
import vn.edu.hust.traffic.model.map.RoundaboutIntersection;
import vn.edu.hust.traffic.model.map.TrafficLight;
import java.util.List;

/**
 * Lớp trợ giúp quản lý độ ưu tiên khi vào ngã tư, dự đoán va chạm và quyền đi trước.
 */
public class IntersectionNavigator {

    public static boolean hasBlockedDiagonalRightTurnEntry(Vehicle v, List<Vehicle> allVehicles,
            List<Intersection> intersections, Intersection intersection, int lightIdx) {
        if (intersection == null || intersection instanceof RoundaboutIntersection) {
            return false;
        }

        double myOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, v.getX(), v.getY()));
        double laneThreshold = Math.max(18.0, v.getHeight() + 8.0);
        double turnDirection = diagonalRightTurnDirectionForEntry(v.getDirection(), lightIdx);
        double turnDirX = Math.cos(turnDirection);
        double turnDirY = Math.sin(turnDirection);
        double turnPathLength = diagonalRightTurnRemainingDistance(v, intersection, lightIdx, turnDirX, turnDirY);
        double pathLaneThreshold = Math.max(22.0, v.getHeight() + 16.0);
        for (Vehicle other : allVehicles) {
            if (other == v || other.insideRoundabout || other.exitedRoundabout) {
                continue;
            }
            Intersection otherTarget = other.getTargetIntersection(intersections);
            boolean nearIntersection = v.isInsideStandardIntersectionGuardZone(intersection,
                    other.getX(), other.getY(), other.getX(), other.getY());
            if (otherTarget != intersection
                    && !intersection.getId().equals(other.activeIntersectionId)
                    && !nearIntersection) {
                continue;
            }

            double relX = other.getX() - v.getX();
            double relY = other.getY() - v.getY();
            double turnPathAhead = relX * turnDirX + relY * turnDirY;
            double turnPathLateral = Math.abs(relX * turnDirY - relY * turnDirX);
            double turnPathHardGap = v.getHalfLength() + other.getHalfLength() + 18.0;
            boolean occupiesTurnPath = turnPathAhead > -other.getHalfLength()
                    && turnPathAhead < turnPathLength + other.getHalfLength() + 55.0
                    && turnPathLateral < pathLaneThreshold;
            if (occupiesTurnPath
                    && (other.getSpeed() < v.baseSpeed * 0.65
                            || turnPathAhead < turnPathHardGap
                            || other.passedStopLine
                            || other.isTurningSmoothly
                            || other.isTurningDiagonally)) {
                return true;
            }

            if (other.getLightIdx(other.getDirection()) != lightIdx) {
                continue;
            }

            double otherOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, other.getX(), other.getY()));
            if (Math.abs(otherOffset - myOffset) > laneThreshold) {
                continue;
            }

            double ahead = v.longitudinalDistanceAhead(lightIdx, other.getX(), other.getY());
            double hardGap = v.getHalfLength() + other.getHalfLength() + 16.0;
            if (ahead > -other.getHalfLength() && ahead < 150.0
                    && (other.getSpeed() < v.baseSpeed * 0.45 || ahead < hardGap)) {
                return true;
            }
        }
        return false;
    }

    public static double diagonalRightTurnDirectionForEntry(double currentDirection, int lightIdx) {
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

    public static double diagonalRightTurnRemainingDistance(Vehicle v, Intersection intersection, int lightIdx,
            double dirX, double dirY) {
        double cx = intersection.getX();
        double cy = intersection.getY();
        double endLane = 65.0;
        if (lightIdx == 0) {
            return Math.max(40.0, (cx - endLane - v.getX()) / Math.max(0.01, dirX));
        } else if (lightIdx == 1) {
            return Math.max(40.0, (cx + endLane - v.getX()) / Math.min(-0.01, dirX));
        } else if (lightIdx == 2) {
            return Math.max(40.0, (cy - endLane - v.getY()) / Math.max(0.01, dirY));
        } else if (lightIdx == 3) {
            return Math.max(40.0, (cy + endLane - v.getY()) / Math.min(-0.01, dirY));
        }
        return 40.0;
    }

    public static boolean hasUnsafeIntersectionEntryConflict(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
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

            int otherLightIdx = other.getLightIdx(other.getDirection());
            boolean eitherTurning = v.isTurningSmoothly || v.isTurningDiagonally 
                    || other.isTurningSmoothly || other.isTurningDiagonally;
            boolean sameAxis = !eitherTurning && ((lightIdx < 2 && otherLightIdx < 2) || (lightIdx >= 2 && otherLightIdx >= 2));
            if (sameAxis) {
                continue;
            }

            TrafficLight.State otherEffectiveLight = v.effectiveLightForVehicle(other, intersections, otherLightIdx);
            if (!other.isPriorityVehicle()
                    && !other.passedStopLine
                    && other.getSpeed() < 0.5
                    && (otherEffectiveLight == TrafficLight.State.RED
                            || otherEffectiveLight == TrafficLight.State.YELLOW)) {
                continue;
            }

            double conflictX = lightIdx < 2 ? other.getX() : v.getX();
            double conflictY = lightIdx < 2 ? v.getY() : other.getY();
            double myDistance = distanceToConflictPoint(lightIdx, conflictX, conflictY, v.getX(), v.getY(), v.getHalfLength());
            double otherDistance = distanceToConflictPoint(otherLightIdx, conflictX, conflictY,
                    other.getX(), other.getY(), other.getHalfLength());
            if (myDistance < -10.0 || myDistance > Vehicle.INTERSECTION_ENTRY_CONFLICT_LOOKAHEAD) {
                continue;
            }
            if (otherDistance < -70.0 || otherDistance > Vehicle.INTERSECTION_ENTRY_CONFLICT_LOOKAHEAD) {
                continue;
            }

            boolean otherInside = other.passedStopLine
                    || other.isTurningSmoothly
                    || other.isTurningDiagonally
                    || v.isInsideStandardIntersection(intersection, other.getX(), other.getY());
            boolean mustYield = shouldYieldForEntryConflict(v, other, intersection, myEffectiveLight,
                    otherEffectiveLight, otherInside);
            if (!mustYield) {
                continue;
            }

            double myConflictSpeed = Math.max(1.0, Math.max(v.getSpeed(), v.baseSpeed * 0.45));
            double otherConflictSpeed = Math.max(1.0, Math.max(other.getSpeed(), other.baseSpeed * 0.35));
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

    public static boolean shouldYieldForEntryConflict(Vehicle v, Vehicle other, Intersection intersection,
            TrafficLight.State myEffectiveLight, TrafficLight.State otherEffectiveLight, boolean otherInside) {
        if (otherInside) {
            return true;
        }
        if (!v.isPriorityVehicle() && other.isPriorityVehicle()) {
            return true;
        }
        if (v.isPriorityVehicle() && !other.isPriorityVehicle()) {
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
        return v.getId().compareTo(other.getId()) > 0;
    }

    public static boolean isRelevantToStandardIntersectionConflict(Vehicle v, Vehicle other, List<Intersection> intersections,
            Intersection intersection) {
        if (intersection.getId().equals(other.activeIntersectionId)
                || v.isInsideStandardIntersectionGuardZone(intersection, other.getX(), other.getY(), other.getX(), other.getY())) {
            return true;
        }
        return other.getTargetIntersection(intersections) == intersection;
    }

    public static double distanceToConflictPoint(int lightIdx, double conflictX, double conflictY,
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

    public static double limitSpeedForPredictedIntersectionCollision(Vehicle v, double dt, double proposedSpeed,
            List<Vehicle> allVehicles, Intersection intersection) {
        if (proposedSpeed <= 0.0 || intersection instanceof RoundaboutIntersection) {
            return proposedSpeed;
        }

        double nextX = v.getX() + Math.cos(v.getDirection()) * proposedSpeed * dt;
        double nextY = v.getY() + Math.sin(v.getDirection()) * proposedSpeed * dt;
        if (!v.isInsideStandardIntersectionGuardZone(intersection, v.getX(), v.getY(), nextX, nextY)) {
            return proposedSpeed;
        }

        double limitedSpeed = proposedSpeed;
        for (Vehicle other : allVehicles) {
            if (other == v) {
                continue;
            }
            if (OvertakeManager.isActiveTurningBypassBlocker(v, other, intersection, v.getLightIdx(v.getDirection()))) {
                continue;
            }

            double otherNextX = other.getX() + Math.cos(other.getDirection()) * Math.max(0.0, other.getSpeed()) * dt;
            double otherNextY = other.getY() + Math.sin(other.getDirection()) * Math.max(0.0, other.getSpeed()) * dt;
            if (!v.isInsideStandardIntersectionGuardZone(intersection, other.getX(), other.getY(), otherNextX, otherNextY)) {
                continue;
            }

            int lightIdx = v.getLightIdx(v.getDirection());
            int otherLightIdx = other.getLightIdx(other.getDirection());
            boolean eitherTurning = v.isTurningSmoothly || v.isTurningDiagonally 
                    || other.isTurningSmoothly || other.isTurningDiagonally;
            boolean sameAxis = !eitherTurning && ((lightIdx < 2 && otherLightIdx < 2) || (lightIdx >= 2 && otherLightIdx >= 2));
            if (sameAxis && !isSameCollisionLane(v, other, lightIdx)) {
                continue;
            }

            double collisionGap = Math.max(24.0,
                    (v.getHalfLength() + other.getHalfLength()) * 0.9 + Math.max(v.getHeight(), other.getHeight()) * 0.25);
            double currentDistance = Math.hypot(v.getX() - other.getX(), v.getY() - other.getY());
            double nextDistance = Math.hypot(nextX - otherNextX, nextY - otherNextY);
            double pathDistance = v.segmentDistance(v.getX(), v.getY(), nextX, nextY, other.getX(), other.getY(), otherNextX, otherNextY);

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
            boolean otherClearing = other.passedStopLine || v.isInsideStandardIntersection(intersection, other.getX(), other.getY());
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

            boolean insideIntersection = Math.hypot(v.getX() - intersection.getX(), v.getY() - intersection.getY())
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

    public static boolean hasIntersectionClearPriorityOver(Vehicle v, Vehicle other, Intersection intersection) {
        if (v.isPriorityVehicle() != other.isPriorityVehicle()) {
            return v.isPriorityVehicle();
        }

        boolean thisClearing = v.passedStopLine || v.isInsideStandardIntersection(intersection, v.getX(), v.getY());
        boolean otherClearing = other.passedStopLine || v.isInsideStandardIntersection(intersection, other.getX(), other.getY());
        if (thisClearing != otherClearing) {
            return thisClearing;
        }

        if (v.activeIntersectionEntryOrder != Long.MAX_VALUE
                && other.activeIntersectionEntryOrder != Long.MAX_VALUE
                && v.activeIntersectionEntryOrder != other.activeIntersectionEntryOrder) {
            return v.activeIntersectionEntryOrder < other.activeIntersectionEntryOrder;
        }
        return v.getId().compareTo(other.getId()) <= 0;
    }

    public static boolean shouldYieldForIntersectionCollision(Vehicle v, Vehicle other) {
        if (!v.isPriorityVehicle() && other.isPriorityVehicle()) {
            return true;
        }
        if (v.isPriorityVehicle() && !other.isPriorityVehicle()) {
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
        return v.getId().compareTo(other.getId()) > 0;
    }

    public static boolean isSameCollisionLane(Vehicle v, Vehicle other, int lightIdx) {
        double laneThreshold = Math.max(16.0, (v.getHeight() + other.getHeight()) * 0.55);
        if (lightIdx < 2) {
            return Math.abs(other.getY() - v.getY()) <= laneThreshold;
        }
        return Math.abs(other.getX() - v.getX()) <= laneThreshold;
    }
}
