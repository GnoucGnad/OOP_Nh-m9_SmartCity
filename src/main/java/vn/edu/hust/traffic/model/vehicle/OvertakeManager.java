package vn.edu.hust.traffic.model.vehicle;

import vn.edu.hust.traffic.model.map.Intersection;
import vn.edu.hust.traffic.model.map.RoundaboutIntersection;
import java.util.List;

/**
 * Helper class for managing vehicle overtaking, bypassing turning vehicles, and yielding to priority vehicles.
 */
public class OvertakeManager {

    static void movePriorityToLeastBusyLaneIfRedQueueAhead(Vehicle v, List<Vehicle> allVehicles,
            List<Intersection> intersections, Intersection intersection, int lightIdx, double dt) {
        if (!v.isPriorityVehicle || v.passedStopLine || v.hasTurned || v.isTurningDiagonally || v.isTurningSmoothly) {
            return;
        }
        if (!hasStoppedQueueAhead(v, allVehicles, intersections, intersection, lightIdx)) {
            return;
        }

        double targetOffset = leastBusyLaneOffset(v, allVehicles, intersections, intersection, lightIdx);
        v.moveTowardStandardLane(intersection, lightIdx, targetOffset, dt, 90.0);
    }

    static boolean updateNormalOvertakeIfNeeded(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx, double dt, boolean mustStopByLight, boolean isFleeing) {
        if (v.isPriorityVehicle || isFleeing || mustStopByLight || v.passedStopLine || v.hasTurned
                || v.isTurningDiagonally || v.isTurningSmoothly || v.distToStopLine < 120.0
                || hasRedLightStoppedVehicleAhead(v, allVehicles, intersections, intersection, lightIdx)
                || hasPriorityVehicleNearSameIntersection(v, allVehicles, intersection, intersections)) {
            if (v.overtakingSlowVehicle) {
                tryReturnToOriginalLane(v, allVehicles, intersections, intersection, lightIdx, dt);
            }
            return v.overtakingSlowVehicle;
        }

        if (!v.overtakingSlowVehicle) {
            Vehicle slowVehicle = findSlowVehicleAheadForOvertake(v, allVehicles, intersections, intersection, lightIdx);
            if (slowVehicle == null) {
                return false;
            }

            double currentOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, v.x, v.y));
            int currentLaneIndex = v.nearestStandardLaneIndex(currentOffset);
            int targetLaneIndex = chooseSafeOvertakeLane(v, allVehicles, intersections, intersection, lightIdx,
                    currentLaneIndex);
            if (targetLaneIndex < 0) {
                return false;
            }

            v.overtakingSlowVehicle = true;
            v.overtakeOriginalLaneOffset = Math.min(Vehicle.STANDARD_LANE_OFFSETS[currentLaneIndex], v.roadCenterOffsetLimit());
            v.overtakeTargetLaneOffset = Math.min(Vehicle.STANDARD_LANE_OFFSETS[targetLaneIndex], v.roadCenterOffsetLimit());
            v.overtakeLightIdx = lightIdx;
            v.overtakeIntersectionId = intersection.getId();
            v.overtakeVehicleId = slowVehicle.id;
        }

        if (v.overtakeLightIdx != lightIdx || !intersection.getId().equals(v.overtakeIntersectionId)) {
            resetOvertakeState(v);
            return false;
        }

        if (hasPassedOvertakeTarget(v, allVehicles, lightIdx)) {
            tryReturnToOriginalLane(v, allVehicles, intersections, intersection, lightIdx, dt);
        } else {
            if (v.isLaneSafeForChange(allVehicles, intersections, intersection, lightIdx, v.overtakeTargetLaneOffset)) {
                v.moveTowardStandardLane(intersection, lightIdx, v.overtakeTargetLaneOffset, dt, 75.0);
            }
        }

        return v.overtakingSlowVehicle;
    }

    static Vehicle findSlowVehicleAheadForOvertake(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx) {
        Vehicle closest = null;
        double closestAhead = Double.MAX_VALUE;
        double currentOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, v.x, v.y));
        int currentLaneIndex = v.nearestStandardLaneIndex(currentOffset);

        for (Vehicle other : allVehicles) {
            if (other == v || other.isPriorityVehicle) {
                continue;
            }
            if (!v.isSameApproachToIntersection(other, intersections, intersection, lightIdx)) {
                continue;
            }
            double otherOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, other.x, other.y));
            if (v.nearestStandardLaneIndex(otherOffset) != currentLaneIndex) {
                continue;
            }

            double ahead = v.longitudinalDistanceAhead(lightIdx, other.x, other.y);
            if (ahead <= 0.0 || ahead > 170.0) {
                continue;
            }
            if (other.speed > v.baseSpeed * 0.72 && other.speed > v.speed - 12.0) {
                continue;
            }
            if (ahead < closestAhead) {
                closestAhead = ahead;
                closest = other;
            }
        }
        return closest;
    }

    static boolean hasRedLightStoppedVehicleAhead(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx) {
        for (Vehicle other : allVehicles) {
            if (other == v || other.isPriorityVehicle) {
                continue;
            }
            if (!v.isSameApproachToIntersection(other, intersections, intersection, lightIdx)) {
                continue;
            }

            double ahead = v.longitudinalDistanceAhead(lightIdx, other.x, other.y);
            if (ahead > 0.0 && ahead < 500.0 && isVehicleStoppedByRedLight(other, intersections)) {
                return true;
            }
        }
        return false;
    }

    static boolean isVehicleStoppedByRedLight(Vehicle vehicle, List<Intersection> intersections) {
        if (vehicle.speed >= 2.0 || vehicle.passedStopLine) {
            return false;
        }

        Intersection target = vehicle.getTargetIntersection(intersections);
        if (target == null || target instanceof RoundaboutIntersection) {
            return false;
        }

        int lightIdx = vehicle.getLightIdx(vehicle.direction);
        vn.edu.hust.traffic.model.map.TrafficLight targetLight = null;
        if (target instanceof vn.edu.hust.traffic.model.map.CrossIntersection) {
            targetLight = target.getLights().get(lightIdx);
        } else if (target instanceof vn.edu.hust.traffic.model.map.ThreeWayIntersection) {
            targetLight = ((vn.edu.hust.traffic.model.map.ThreeWayIntersection) target)
                    .getLightForDirection(vehicle.direction);
        }
        if (targetLight == null) {
            return false;
        }

        vn.edu.hust.traffic.model.map.TrafficLight.State state = targetLight.getStateForTurn(vehicle.turnIntention, vehicle.hasTurned);
        return state == vn.edu.hust.traffic.model.map.TrafficLight.State.RED || state == vn.edu.hust.traffic.model.map.TrafficLight.State.YELLOW;
    }

    static int chooseSafeOvertakeLane(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx, int currentLaneIndex) {
        int bestIndex = -1;
        int bestCount = Integer.MAX_VALUE;
        double bestMove = Double.MAX_VALUE;
        for (int i = 0; i < Vehicle.STANDARD_LANE_OFFSETS.length; i++) {
            if (i == currentLaneIndex || Math.abs(i - currentLaneIndex) != 1) {
                continue;
            }
            double offset = Math.min(Vehicle.STANDARD_LANE_OFFSETS[i], v.roadCenterOffsetLimit());
            if (!v.isLaneSafeForChange(allVehicles, intersections, intersection, lightIdx, offset)) {
                continue;
            }

            int count = v.countVehiclesInLaneWindow(allVehicles, intersections, intersection, lightIdx, offset);
            double move = Math.abs(i - currentLaneIndex);
            if (count < bestCount || (count == bestCount && move < bestMove)) {
                bestIndex = i;
                bestCount = count;
                bestMove = move;
            }
        }
        return bestIndex;
    }

    static boolean hasPassedOvertakeTarget(Vehicle v, List<Vehicle> allVehicles, int lightIdx) {
        if (v.overtakeVehicleId == null) {
            return true;
        }
        for (Vehicle other : allVehicles) {
            if (!v.overtakeVehicleId.equals(other.id)) {
                continue;
            }
            double ahead = v.longitudinalDistanceAhead(lightIdx, other.x, other.y);
            return ahead < -(v.getHalfLength() + other.getHalfLength() + 30.0);
        }
        return true;
    }

    static void tryReturnToOriginalLane(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx, double dt) {
        if (!v.isLaneSafeForChange(allVehicles, intersections, intersection, lightIdx, v.overtakeOriginalLaneOffset)) {
            return;
        }
        v.moveTowardStandardLane(intersection, lightIdx, v.overtakeOriginalLaneOffset, dt, 70.0);
        double currentOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, v.x, v.y));
        if (Math.abs(currentOffset - v.overtakeOriginalLaneOffset) < 2.0) {
            resetOvertakeState(v);
        }
    }

    static boolean hasPriorityVehicleNearSameIntersection(Vehicle v, List<Vehicle> allVehicles, Intersection intersection,
            List<Intersection> intersections) {
        for (Vehicle other : allVehicles) {
            if (other != v && other.isPriorityVehicle
                    && v.isPriorityApproachingSameIntersection(other, intersection, intersections)) {
                return true;
            }
        }
        return false;
    }

    static void resetOvertakeState(Vehicle v) {
        v.overtakingSlowVehicle = false;
        v.overtakeOriginalLaneOffset = 0.0;
        v.overtakeTargetLaneOffset = 0.0;
        v.overtakeLightIdx = -1;
        v.overtakeIntersectionId = null;
        v.overtakeVehicleId = null;
    }

    static void resetYieldState(Vehicle v) {
        v.yieldingToPriorityVehicle = false;
        v.yieldTargetLaneOffset = 0.0;
        v.yieldLightIdx = -1;
        v.yieldIntersectionId = null;
        v.yieldPriorityVehicleId = null;
    }

    static void resetTurningBypassState(Vehicle v) {
        v.bypassingTurningVehicle = false;
        v.bypassTargetLaneOffset = 0.0;
        v.bypassLightIdx = -1;
        v.bypassIntersectionId = null;
        v.bypassVehicleId = null;
    }

    static boolean updateTurningVehicleBypassIfNeeded(Vehicle v, List<Vehicle> allVehicles,
            List<Intersection> intersections, Intersection intersection, int lightIdx, double dt) {
        boolean insideIntersection = intersection != null && v.isInsideStandardIntersection(intersection, v.x, v.y);
        boolean canBypassInCurrentPosition = v.isPriorityVehicle
                ? (v.passedStopLine || insideIntersection)
                : (v.passedStopLine && insideIntersection);
        if (intersection == null || intersection instanceof RoundaboutIntersection
                || v.isTurningDiagonally || v.isTurningSmoothly || !canBypassInCurrentPosition) {
            resetTurningBypassState(v);
            return false;
        }

        Vehicle blocker = findTurningVehicleBlockingCurrentLane(v, allVehicles, intersection, lightIdx);
        if (blocker == null && v.bypassingTurningVehicle
                && lightIdx == v.bypassLightIdx
                && intersection.getId().equals(v.bypassIntersectionId)) {
            v.moveTowardStandardLane(intersection, lightIdx, v.bypassTargetLaneOffset, dt, 115.0);
            if (Math.abs(Math.abs(v.standardLaneOffset(intersection, lightIdx, v.x, v.y))
                    - v.bypassTargetLaneOffset) < 2.0) {
                resetTurningBypassState(v);
            }
            return true;
        }
        if (blocker == null) {
            resetTurningBypassState(v);
            return false;
        }

        if (!v.bypassingTurningVehicle
                || lightIdx != v.bypassLightIdx
                || !intersection.getId().equals(v.bypassIntersectionId)
                || !blocker.id.equals(v.bypassVehicleId)) {
            double currentOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, v.x, v.y));
            int currentLaneIndex = v.nearestStandardLaneIndex(currentOffset);
            int targetLaneIndex = chooseTurningBypassLaneIndex(v, allVehicles, intersections, intersection,
                    lightIdx, currentLaneIndex, blocker);
            if (targetLaneIndex < 0) {
                resetTurningBypassState(v);
                return false;
            }

            v.bypassingTurningVehicle = true;
            v.bypassTargetLaneOffset = Math.min(Vehicle.STANDARD_LANE_OFFSETS[targetLaneIndex],
                    v.roadCenterOffsetLimit());
            v.bypassLightIdx = lightIdx;
            v.bypassIntersectionId = intersection.getId();
            v.bypassVehicleId = blocker.id;
        }

        v.moveTowardStandardLane(intersection, lightIdx, v.bypassTargetLaneOffset, dt, 115.0);
        return true;
    }

    static Vehicle findTurningVehicleBlockingCurrentLane(Vehicle v, List<Vehicle> allVehicles,
            Intersection intersection, int lightIdx) {
        Vehicle closest = null;
        double closestAhead = Double.MAX_VALUE;
        double dirX = Math.cos(v.direction);
        double dirY = Math.sin(v.direction);
        double laneThreshold = Math.max(20.0, v.height + 10.0);
        for (Vehicle other : allVehicles) {
            if (other == v || other.isPriorityVehicle) {
                continue;
            }
            if (!v.isPriorityVehicle
                    && other.originalLightIdx == v.originalLightIdx
                    && other.turnIntention == v.turnIntention) {
                continue;
            }
            boolean turningInIntersection = (other.isTurningSmoothly || other.isTurningDiagonally || other.hasTurned)
                    && v.isInsideStandardIntersection(intersection, other.x, other.y);
            if (!turningInIntersection) {
                continue;
            }

            double relX = other.x - v.x;
            double relY = other.y - v.y;
            double ahead = relX * dirX + relY * dirY;
            if (ahead <= 0.0 || ahead > 150.0) {
                continue;
            }
            double lateral = Math.abs(relX * dirY - relY * dirX);
            if (lateral > laneThreshold) {
                continue;
            }
            double offset = Math.abs(v.standardLaneOffset(intersection, lightIdx, other.x, other.y));
            double myOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, v.x, v.y));
            if (v.nearestStandardLaneIndex(offset) != v.nearestStandardLaneIndex(myOffset)) {
                continue;
            }
            if (ahead < closestAhead) {
                closestAhead = ahead;
                closest = other;
            }
        }
        return closest;
    }

    static int chooseTurningBypassLaneIndex(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx, int currentLaneIndex, Vehicle blocker) {
        int bestIndex = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int candidate = 0; candidate < Vehicle.STANDARD_LANE_OFFSETS.length; candidate++) {
            if (candidate == currentLaneIndex || Math.abs(candidate - currentLaneIndex) != 1) {
                continue;
            }
            double offset = Math.min(Vehicle.STANDARD_LANE_OFFSETS[candidate], v.roadCenterOffsetLimit());
            if (!isTurningBypassLaneSafe(v, allVehicles, intersections, intersection, lightIdx, offset, blocker)) {
                continue;
            }

            int count = v.countVehiclesInLaneWindow(allVehicles, intersections, intersection, lightIdx, offset);
            if (count < bestCount) {
                bestCount = count;
                bestIndex = candidate;
            }
        }
        return bestIndex;
    }

    static boolean isTurningBypassLaneSafe(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx, double targetOffset, Vehicle blocker) {
        if (!v.isLaneSafeForChange(allVehicles, intersections, intersection, lightIdx, targetOffset)) {
            return false;
        }

        double targetLaneCoord = v.standardLaneCoordinate(intersection, lightIdx, targetOffset);
        double myLongitudinal = v.longitudinalCoordinate(lightIdx, v.x, v.y);
        for (Vehicle other : allVehicles) {
            if (other == v || other == blocker) {
                continue;
            }
            if (!v.isInsideStandardIntersectionGuardZone(intersection, other.x, other.y, other.x, other.y)
                    && !v.isSameApproachToIntersection(other, intersections, intersection, lightIdx)) {
                continue;
            }

            double otherLongitudinal = v.longitudinalCoordinate(lightIdx, other.x, other.y);
            double relative = v.signedLongitudinalDelta(lightIdx, myLongitudinal, otherLongitudinal);
            if (relative < -70.0 || relative > 180.0) {
                continue;
            }

            double lateral = lightIdx < 2
                    ? Math.abs(other.y - targetLaneCoord)
                    : Math.abs(other.x - targetLaneCoord);
            if (lateral < Math.max(18.0, (v.height + other.height) * 0.65)) {
                return false;
            }
        }
        return true;
    }

    static boolean isActiveTurningBypassBlocker(Vehicle v, Vehicle other, Intersection intersection, int lightIdx) {
        return v.bypassingTurningVehicle
                && lightIdx == v.bypassLightIdx
                && intersection.getId().equals(v.bypassIntersectionId)
                && other.id.equals(v.bypassVehicleId);
    }

    static boolean hasStoppedQueueAhead(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx) {
        for (Vehicle other : allVehicles) {
            if (other == v || other.isPriorityVehicle) {
                continue;
            }
            if (!v.isSameApproachToIntersection(other, intersections, intersection, lightIdx)) {
                continue;
            }

            double ahead = v.longitudinalDistanceAhead(lightIdx, other.x, other.y);
            if (ahead > 0.0 && ahead < 420.0 && other.speed < 2.0 && !other.passedStopLine) {
                return true;
            }
        }
        return false;
    }

    static double leastBusyLaneOffset(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx) {
        return leastBusyLaneOffset(v, allVehicles, intersections, intersection, lightIdx, -1);
    }

    static double stableYieldLaneOffset(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx, Vehicle priorityVehicle) {
        if (v.yieldingToPriorityVehicle
                && lightIdx == v.yieldLightIdx
                && intersection.getId().equals(v.yieldIntersectionId)
                && priorityVehicle.id.equals(v.yieldPriorityVehicleId)) {
            return v.yieldTargetLaneOffset;
        }

        int targetLaneIndex = chooseAdjacentYieldLaneIndex(v, allVehicles, intersections, intersection, lightIdx,
                priorityVehicle);
        if (targetLaneIndex < 0) {
            resetYieldState(v);
            return Math.abs(v.standardLaneOffset(intersection, lightIdx, v.x, v.y));
        }

        v.yieldingToPriorityVehicle = true;
        v.yieldTargetLaneOffset = Math.min(Vehicle.STANDARD_LANE_OFFSETS[targetLaneIndex], v.roadCenterOffsetLimit());
        v.yieldLightIdx = lightIdx;
        v.yieldIntersectionId = intersection.getId();
        v.yieldPriorityVehicleId = priorityVehicle.id;
        return v.yieldTargetLaneOffset;
    }

    static boolean continueYieldLaneChangeToTargetIfNeeded(Vehicle v, List<Vehicle> allVehicles,
            List<Intersection> intersections, Intersection intersection, int lightIdx, double dt) {
        if (!v.yieldingToPriorityVehicle || intersection == null || intersection instanceof RoundaboutIntersection
                || lightIdx != v.yieldLightIdx || !intersection.getId().equals(v.yieldIntersectionId)) {
            return false;
        }

        double currentOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, v.x, v.y));
        if (Math.abs(currentOffset - v.yieldTargetLaneOffset) <= 1.5) {
            resetYieldState(v);
            return false;
        }

        if (v.isLaneSafeForChange(allVehicles, intersections, intersection, lightIdx, v.yieldTargetLaneOffset)) {
            v.moveTowardStandardLane(intersection, lightIdx, v.yieldTargetLaneOffset, dt, Vehicle.YIELD_LANE_CHANGE_SPEED);
        }
        return true;
    }

    static int chooseAdjacentYieldLaneIndex(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx, Vehicle priorityVehicle) {
        double currentOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, v.x, v.y));
        int currentLaneIndex = v.nearestStandardLaneIndex(currentOffset);
        double priorityOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx,
                priorityVehicle.x, priorityVehicle.y));
        int priorityLaneIndex = v.nearestStandardLaneIndex(priorityOffset);

        int bestIndex = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int candidate = 0; candidate < Vehicle.STANDARD_LANE_OFFSETS.length; candidate++) {
            if (candidate == currentLaneIndex
                    || candidate == priorityLaneIndex
                    || Math.abs(candidate - currentLaneIndex) != 1) {
                continue;
            }

            double offset = Math.min(Vehicle.STANDARD_LANE_OFFSETS[candidate], v.roadCenterOffsetLimit());
            if (!v.isLaneSafeForChange(allVehicles, intersections, intersection, lightIdx, offset)) {
                continue;
            }

            int count = v.countVehiclesInLaneWindow(allVehicles, intersections, intersection, lightIdx, offset);
            if (count < bestCount) {
                bestCount = count;
                bestIndex = candidate;
            }
        }
        return bestIndex;
    }

    static double leastBusyLaneOffset(Vehicle v, List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx, int avoidLaneIndex) {
        int[] counts = new int[Vehicle.STANDARD_LANE_OFFSETS.length];
        double[] nearestDistances = new double[Vehicle.STANDARD_LANE_OFFSETS.length];
        for (int i = 0; i < nearestDistances.length; i++) {
            nearestDistances[i] = Double.MAX_VALUE;
        }
        if (avoidLaneIndex >= 0 && avoidLaneIndex < counts.length) {
            counts[avoidLaneIndex] += 1000;
        }

        for (Vehicle other : allVehicles) {
            if (other == v || !v.isSameApproachToIntersection(other, intersections, intersection, lightIdx)) {
                continue;
            }

            double ahead = v.longitudinalDistanceAhead(lightIdx, other.x, other.y);
            if (ahead < -20.0 || ahead > 500.0) {
                continue;
            }

            double offset = v.standardLaneOffset(intersection, lightIdx, other.x, other.y);
            if (Math.abs(offset) > Vehicle.ROAD_HALF_WIDTH + other.width / 2.0) {
                continue;
            }

            int laneIndex = v.nearestStandardLaneIndex(Math.abs(offset));
            counts[laneIndex]++;
            nearestDistances[laneIndex] = Math.min(nearestDistances[laneIndex], Math.max(0.0, ahead));
        }

        double currentOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, v.x, v.y));
        int bestIndex = 0;
        for (int i = 1; i < Vehicle.STANDARD_LANE_OFFSETS.length; i++) {
            if (counts[i] < counts[bestIndex]) {
                bestIndex = i;
            } else if (counts[i] == counts[bestIndex]) {
                double currentMove = Math.abs(Vehicle.STANDARD_LANE_OFFSETS[i] - currentOffset);
                double bestMove = Math.abs(Vehicle.STANDARD_LANE_OFFSETS[bestIndex] - currentOffset);
                if (nearestDistances[i] > nearestDistances[bestIndex] + 1.0
                        || (Math.abs(nearestDistances[i] - nearestDistances[bestIndex]) <= 1.0
                                && currentMove < bestMove)) {
                    bestIndex = i;
                }
            }
        }
        return Math.min(Vehicle.STANDARD_LANE_OFFSETS[bestIndex], v.roadCenterOffsetLimit());
    }

    static boolean isBlockingPriorityLane(Vehicle v, Intersection intersection, int lightIdx, Vehicle priorityVehicle) {
        double currentOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx, v.x, v.y));
        double priorityOffset = Math.abs(v.standardLaneOffset(intersection, lightIdx,
                priorityVehicle.x, priorityVehicle.y));
        return v.nearestStandardLaneIndex(currentOffset) == v.nearestStandardLaneIndex(priorityOffset);
    }

    static boolean isContinuingYieldForPriority(Vehicle v, Intersection intersection, int lightIdx, Vehicle priorityVehicle) {
        return v.yieldingToPriorityVehicle
                && lightIdx == v.yieldLightIdx
                && intersection.getId().equals(v.yieldIntersectionId)
                && priorityVehicle.id.equals(v.yieldPriorityVehicleId);
    }
}
