package vn.edu.hust.traffic.behavior;

import vn.edu.hust.traffic.model.vehicle.Vehicle;
import vn.edu.hust.traffic.model.map.RoundaboutIntersection;
import java.util.List;

/**
 * Lớp trợ giúp quản lý việc điều khiển xe trong bùng binh, các lối vào và lối ra.
 */
public class RoundaboutNavigator {

    public static void updateRoundabout(Vehicle v, double dt, List<Vehicle> allVehicles, RoundaboutIntersection roundabout) {
        double cx = roundabout.getX();
        double cy = roundabout.getY();
        double[] roadAngles = roundabout.getRoadAngles();
        if (roadAngles.length == 0) {
            v.movePhysically(dt);
            return;
        }

        if (v.exitedRoundabout && !v.insideRoundabout
                && (isOnRoundaboutApproach(v, roundabout) || isInsideRoundaboutForbiddenIsland(v, roundabout))) {
            v.exitedRoundabout = false;
            v.setTurnIntention(0);
            v.passedStopLine = false;
            v.spawnSourceIndex = -1;
            v.targetExitIndex = -1;
        }

        // 1. Initialize roundabout target exit and source index if not set
        if (v.spawnSourceIndex == -1) {
            v.spawnSourceIndex = getApproachRoadIndex(v.getX(), v.getY(), v.getDirection(), cx, cy, roadAngles);

            // Calculate our offset from the road axis to know which lane we spawned in
            double dx = v.getX() - cx;
            double dy = v.getY() - cy;
            double roadTheta = roadAngles[v.spawnSourceIndex];
            double calculatedOffset = dx * Math.sin(roadTheta) - dy * Math.cos(roadTheta);
            v.laneOffsetVal = normalizeRoundaboutLaneOffset(Math.abs(calculatedOffset));

            if (v.targetExitIndex < 0 || v.targetExitIndex >= roadAngles.length) {
                java.util.Random rand = new java.util.Random();
                int exitCount = roadAngles.length;
                if (exitCount <= 1) {
                    v.targetExitIndex = v.spawnSourceIndex;
                } else {
                    do {
                        v.targetExitIndex = rand.nextInt(exitCount);
                    } while (v.targetExitIndex == v.spawnSourceIndex);
                }
            }
        }

        double thetaSource = roadAngles[v.spawnSourceIndex];
        double thetaTarget = roadAngles[v.targetExitIndex];
        double entryMergeDistance = roundaboutLaneMergeDistance(v);
        double targetExitAngle = getRoundaboutExitLaneAngle(v, thetaTarget);

        if (!v.insideRoundabout && isInsideRoundaboutBody(v, roundabout)) {
            v.insideRoundabout = true;
            v.roundaboutAngle = Math.atan2(v.getY() - cy, v.getX() - cx);
            double currentR = Math.hypot(v.getX() - cx, v.getY() - cy);
            double targetR = clampRoundaboutRadius(currentR);
            double newR = (currentR < Vehicle.ROUNDABOUT_MIN_DRIVE_RADIUS || v.getSpeed() <= 0.1)
                    ? targetR
                    : currentR + (targetR - currentR) * v.blendAlpha(dt, Vehicle.ROUNDABOUT_RADIUS_BLEND_RATE);
            v.setX(cx + newR * Math.cos(v.roundaboutAngle));
            v.setY(cy + newR * Math.sin(v.roundaboutAngle));
        }

        double safeDistance = (v.getWidth() > 30) ? 50 : 30;
        double currentTargetSpeed = v.baseSpeed;
        boolean shouldStop = false;
        boolean hardRoundaboutBlock = false;
        boolean hardApproachBlock = false;
        boolean speedAlreadyApplied = false;

        if (!v.insideRoundabout) {
            // APPROACHING THE ROUNDABOUT (yielding at distance 180)
            double dx = v.getX() - cx;
            double dy = v.getY() - cy;
            double d = dx * Math.cos(thetaSource) + dy * Math.sin(thetaSource);

            double stopDist = d - Vehicle.ROUNDABOUT_ENTRY_RADIUS;
            v.passedStopLine = d <= Vehicle.ROUNDABOUT_ENTRY_RADIUS;

            // Yield to circulating vehicles that will reach this entry while moving counter-clockwise.
            boolean yieldRequired = false;
            for (Vehicle other : allVehicles) {
                if (other != v && other.insideRoundabout) {
                    double diff = Vehicle.counterClockwiseDistance(other.roundaboutAngle, thetaSource);
                    double priorityWindow = (!v.isPriorityVehicle() && other.isPriorityVehicle()) ? 1.0 : 0.6;
                    // If the other vehicle is stopped or moving extremely slowly, reduce the window to prevent gridlock
                    if (other.getSpeed() < 2.0) {
                        priorityWindow = 0.25; 
                    }
                    if (diff > 0 && diff < priorityWindow) {
                        yieldRequired = true;
                        break;
                    }
                }
                if (!v.isPriorityVehicle()
                        && other != v
                        && other.isPriorityVehicle()
                        && !other.insideRoundabout
                        && !other.exitedRoundabout
                        && isOnRoundaboutApproach(other, roundabout)) {
                    int otherSourceIndex = getApproachRoadIndex(other.getX(), other.getY(), other.getDirection(),
                            cx, cy, roadAngles);
                    if (otherSourceIndex == v.spawnSourceIndex) {
                        double otherDx = other.getX() - cx;
                        double otherDy = other.getY() - cy;
                        double otherD = otherDx * Math.cos(thetaSource) + otherDy * Math.sin(thetaSource);
                        if (otherD >= d) {
                            continue;
                        }
                    }
                    yieldRequired = true;
                    break;
                }
            }

            if (yieldRequired && !v.passedStopLine) {
                if (stopDist <= 0) {
                    shouldStop = true;
                } else if (stopDist < 80.0) {
                    double ratio = stopDist / 80.0;
                    currentTargetSpeed = v.baseSpeed * ratio;
                    if (stopDist < 5) shouldStop = true;
                }
            }

            for (Vehicle other : allVehicles) {
                if (other == v || other.insideRoundabout || other.exitedRoundabout) {
                    continue;
                }

                double otherDx = other.getX() - cx;
                double otherDy = other.getY() - cy;
                double otherD = otherDx * Math.cos(thetaSource) + otherDy * Math.sin(thetaSource);
                double otherLateral = Math.abs(otherDx * Math.sin(thetaSource) - otherDy * Math.cos(thetaSource));
                double otherHeadingDiff = Math.abs(Vehicle.normalizeAngle(other.getDirection() - (thetaSource + Math.PI)));
                if (otherD >= d || otherLateral > Vehicle.ROAD_HALF_WIDTH || otherHeadingDiff > 0.65) {
                    continue;
                }

                double gap = (d - otherD) - v.getHalfLength() - other.getHalfLength();
                if (gap < safeDistance) {
                    if (gap <= 8.0) {
                        hardApproachBlock = true;
                        shouldStop = true;
                    } else {
                        double ratio = (gap - 8.0) / (safeDistance - 8.0);
                        currentTargetSpeed = Math.min(currentTargetSpeed, other.getSpeed() * Math.max(0.0, ratio));
                    }
                }
            }

            if (!hardApproachBlock && !shouldStop && d <= entryMergeDistance) {
                moveTowardRoundaboutEntryLane(v, cx, cy, thetaSource, Math.max(0.0, d), dt);
                v.insideRoundabout = true;
                v.roundaboutAngle = Math.atan2(v.getY() - cy, v.getX() - cx);
            } else {
                v.setX(v.getX()); // maintain position or move via speed
                v.direction = v.interpolateAngle(v.getDirection(), thetaSource + Math.PI, v.blendAlpha(dt, Vehicle.ROUNDABOUT_HEADING_BLEND_RATE));
                if (d <= Vehicle.ROUNDABOUT_ENTRY_RADIUS) {
                    moveTowardRoundaboutEntryLane(v, cx, cy, thetaSource, d, dt);
                }
            }
        }

        if (v.insideRoundabout) {
            // INSIDE THE ROUNDABOUT (3 concentric lanes: Outer=165, Middle=140, Inner=115)
            int exitsRemaining = getExitsRemaining(v.roundaboutAngle, thetaTarget, roadAngles);
            double targetR = 165.0;
            if (exitsRemaining > 2) {
                targetR = 115.0; // Inner lane
            } else if (exitsRemaining == 2) {
                targetR = 140.0; // Middle lane
            } else {
                targetR = 165.0; // Outer lane
            }

            double currentR = clampRoundaboutRadius(Math.hypot(v.getX() - cx, v.getY() - cy));
            double newR = clampRoundaboutRadius(
                    currentR + (targetR - currentR) * v.blendAlpha(dt, Vehicle.ROUNDABOUT_RADIUS_BLEND_RATE));

            for (Vehicle other : allVehicles) {
                if (other != v && other.insideRoundabout) {
                    double angleDiff = Vehicle.counterClockwiseDistance(v.roundaboutAngle, other.roundaboutAngle);
                    double otherR = clampRoundaboutRadius(Math.hypot(other.getX() - cx, other.getY() - cy));
                    double laneDistance = Math.abs(otherR - currentR);
                    if (angleDiff > 0 && angleDiff < 0.55 && laneDistance < 32.0) {
                        double gap = Math.min(newR, otherR) * angleDiff - v.getHalfLength() - other.getHalfLength();
                        if (gap < safeDistance) {
                            double minGap = 8.0;
                            if (gap <= minGap) {
                                if (gap <= 0.0) {
                                    // Tie-breaker to prevent mutual deadlock inside roundabout (only if different/crossing lanes)
                                    if (laneDistance >= 12.0 && v.getId().compareTo(other.getId()) < 0) {
                                        currentTargetSpeed = Math.max(currentTargetSpeed, v.baseSpeed * Vehicle.ROUNDABOUT_CRAWL_MIN_SPEED_FACTOR * 0.5);
                                    } else {
                                        hardRoundaboutBlock = true;
                                        shouldStop = true;
                                    }
                                } else {
                                    currentTargetSpeed = Math.min(currentTargetSpeed,
                                            Math.max(other.getSpeed(), v.baseSpeed * Vehicle.ROUNDABOUT_CRAWL_MIN_SPEED_FACTOR));
                                }
                            } else {
                                double ratio = (gap - minGap) / (safeDistance - minGap);
                                currentTargetSpeed = Math.min(currentTargetSpeed, other.getSpeed() * ratio);
                            }
                        }
                    }
                }
            }

            if (shouldStop && hardRoundaboutBlock) {
                v.speed = 0;
                newR = currentR;
            } else {
                double adjustedTargetSpeed = shouldStop
                        ? Math.max(currentTargetSpeed, v.baseSpeed * Vehicle.ROUNDABOUT_CRAWL_MIN_SPEED_FACTOR)
                        : currentTargetSpeed;
                v.speed = v.getSpeed() + (adjustedTargetSpeed - v.getSpeed()) * 0.1;
                if (shouldStop) {
                    v.speed = Math.max(v.getSpeed(), v.baseSpeed * Vehicle.ROUNDABOUT_CRAWL_MIN_SPEED_FACTOR);
                }
            }
            speedAlreadyApplied = true;

            double previousAngle = v.roundaboutAngle;
            double omega = v.getSpeed() / newR;
            v.roundaboutAngle = Vehicle.normalizeAngle(v.roundaboutAngle - omega * dt);

            v.setX(cx + newR * Math.cos(v.roundaboutAngle));
            v.setY(cy + newR * Math.sin(v.roundaboutAngle));
            v.direction = v.interpolateAngle(v.getDirection(), v.roundaboutAngle - Math.PI / 2.0,
                    v.blendAlpha(dt, Vehicle.ROUNDABOUT_HEADING_BLEND_RATE));

            // Exit condition
            if (hasReachedCounterClockwiseExit(previousAngle, v.roundaboutAngle, targetExitAngle)) {
                v.insideRoundabout = false;
                v.exitedRoundabout = true;
                v.passedStopLine = true;
                v.roundaboutAngle = thetaTarget;
                double targetX = cx + entryMergeDistance * Math.cos(thetaTarget) - v.laneOffsetVal * Math.sin(thetaTarget);
                double targetY = cy + entryMergeDistance * Math.sin(thetaTarget) + v.laneOffsetVal * Math.cos(thetaTarget);
                v.startSmoothTurn(targetX, targetY, thetaTarget, true, Vehicle.ROUNDABOUT_EXIT_TURN_DURATION);
                return;
            }
        } else if (v.exitedRoundabout) {
            // EXITING THE ROUNDABOUT
            double dx = v.getX() - cx;
            double dy = v.getY() - cy;
            double d = dx * Math.cos(thetaTarget) + dy * Math.sin(thetaTarget);

            v.direction = thetaTarget;
            placeOnRoundaboutExitLane(v, cx, cy, thetaTarget, d + v.getSpeed() * dt);
        }

        if (!speedAlreadyApplied) {
            if (shouldStop) {
                v.speed = 0;
            } else {
                v.speed = v.getSpeed() + (currentTargetSpeed - v.getSpeed()) * 0.1;
            }
        }

        if (!v.insideRoundabout && !v.exitedRoundabout) {
            advanceRoundaboutApproach(v, cx, cy, thetaSource, entryMergeDistance, dt);
        }
    }

    public static void advanceRoundaboutApproach(Vehicle v, double cx, double cy, double theta,
            double entryMergeDistance, double dt) {
        double dx = v.getX() - cx;
        double dy = v.getY() - cy;
        double d = dx * Math.cos(theta) + dy * Math.sin(theta);
        double travel = Math.max(0.0, v.getSpeed() * dt);

        v.direction = v.interpolateAngle(v.getDirection(), theta + Math.PI, v.blendAlpha(dt, Vehicle.ROUNDABOUT_HEADING_BLEND_RATE));
        if (travel <= 0.0) {
            double stoppedD = d <= entryMergeDistance ? entryMergeDistance : Math.max(0.0, d);
            if (d <= Vehicle.ROUNDABOUT_ENTRY_RADIUS) {
                placeOnRoundaboutEntryLane(v, cx, cy, theta, stoppedD);
            }
            return;
        }

        double nextD = d - travel;
        if (nextD > entryMergeDistance) {
            placeOnRoundaboutEntryLane(v, cx, cy, theta, Math.max(0.0, nextD));
            return;
        }

        double travelToMerge = Math.max(0.0, d - entryMergeDistance);
        double remainingTravel = Math.max(0.0, travel - travelToMerge);
        double entryD = d <= entryMergeDistance ? Math.max(0.0, d) : entryMergeDistance;
        placeOnRoundaboutEntryLane(v, cx, cy, theta, entryD);

        v.insideRoundabout = true;
        v.roundaboutAngle = Math.atan2(v.getY() - cy, v.getX() - cx);
        double radius = clampRoundaboutRadius(Math.hypot(v.getX() - cx, v.getY() - cy));
        if (remainingTravel > 0.0) {
            v.roundaboutAngle = Vehicle.normalizeAngle(v.roundaboutAngle - remainingTravel / radius);
        }
        v.setX(cx + radius * Math.cos(v.roundaboutAngle));
        v.setY(cy + radius * Math.sin(v.roundaboutAngle));
        v.direction = v.interpolateAngle(v.getDirection(), v.roundaboutAngle - Math.PI / 2.0,
                v.blendAlpha(dt, Vehicle.ROUNDABOUT_HEADING_BLEND_RATE));
    }

    public static void placeOnRoundaboutEntryLane(Vehicle v, double cx, double cy, double theta, double distanceFromCenter) {
        v.setX(cx + distanceFromCenter * Math.cos(theta) + v.laneOffsetVal * Math.sin(theta));
        v.setY(cy + distanceFromCenter * Math.sin(theta) - v.laneOffsetVal * Math.cos(theta));
    }

    public static void moveTowardRoundaboutEntryLane(Vehicle v, double cx, double cy, double theta, double distanceFromCenter,
            double dt) {
        double targetX = cx + distanceFromCenter * Math.cos(theta) + v.laneOffsetVal * Math.sin(theta);
        double targetY = cy + distanceFromCenter * Math.sin(theta) - v.laneOffsetVal * Math.cos(theta);
        if (v.getSpeed() <= 0.1) {
            v.setX(targetX);
            v.setY(targetY);
            return;
        }

        double alpha = v.blendAlpha(dt, Vehicle.ROUNDABOUT_LANE_BLEND_RATE);
        v.setX(v.getX() + (targetX - v.getX()) * alpha);
        v.setY(v.getY() + (targetY - v.getY()) * alpha);
    }

    public static void placeOnRoundaboutExitLane(Vehicle v, double cx, double cy, double theta, double distanceFromCenter) {
        v.setX(cx + distanceFromCenter * Math.cos(theta) - v.laneOffsetVal * Math.sin(theta));
        v.setY(cy + distanceFromCenter * Math.sin(theta) + v.laneOffsetVal * Math.cos(theta));
    }

    public static double normalizeRoundaboutLaneOffset(double offset) {
        double closest = Vehicle.ROUNDABOUT_LANE_OFFSETS[0];
        double minDistance = Math.abs(offset - closest);
        for (double laneOffset : Vehicle.ROUNDABOUT_LANE_OFFSETS) {
            double distance = Math.abs(offset - laneOffset);
            if (distance < minDistance) {
                closest = laneOffset;
                minDistance = distance;
            }
        }
        return closest;
    }

    public static double clampRoundaboutRadius(double radius) {
        return Math.max(Vehicle.ROUNDABOUT_MIN_DRIVE_RADIUS, Math.min(Vehicle.ROUNDABOUT_MAX_DRIVE_RADIUS, radius));
    }

    public static boolean isOnRoundaboutApproach(Vehicle v, RoundaboutIntersection roundabout) {
        if (v.insideRoundabout) {
            return true;
        }

        double[] roadAngles = roundabout.getRoadAngles();
        if (roadAngles.length == 0) {
            return false;
        }

        double cx = roundabout.getX();
        double cy = roundabout.getY();
        int roadIndex = getApproachRoadIndex(v.getX(), v.getY(), v.getDirection(), cx, cy, roadAngles);
        double theta = roadAngles[roadIndex];
        double dx = v.getX() - cx;
        double dy = v.getY() - cy;
        double forwardDistance = dx * Math.cos(theta) + dy * Math.sin(theta);
        double lateralDistance = Math.abs(dx * Math.sin(theta) - dy * Math.cos(theta));
        double headingDiff = Math.abs(Vehicle.normalizeAngle(v.getDirection() - (theta + Math.PI)));

        return forwardDistance > 0
                && forwardDistance < Vehicle.ROUNDABOUT_CAPTURE_DISTANCE
                && lateralDistance <= Vehicle.ROAD_HALF_WIDTH + v.getWidth() / 2.0
                && headingDiff < Vehicle.ROUNDABOUT_APPROACH_HEADING_TOLERANCE;
    }

    public static boolean shouldTargetRoundabout(Vehicle v, RoundaboutIntersection roundabout) {
        if (v.insideRoundabout) {
            return true;
        }
        if (!v.exitedRoundabout) {
            return isInsideRoundaboutBody(v, roundabout) || isOnRoundaboutApproach(v, roundabout);
        }
        return isOnRoundaboutApproach(v, roundabout) || isInsideRoundaboutForbiddenIsland(v, roundabout);
    }

    public static boolean isInsideRoundaboutForbiddenIsland(Vehicle v, RoundaboutIntersection roundabout) {
        double radius = Math.hypot(v.getX() - roundabout.getX(), v.getY() - roundabout.getY());
        double islandBoundary = Math.max(0.0, roundabout.getRadius() - Vehicle.ROUNDABOUT_ISLAND_RADIUS_INSET)
                + v.getHeight() / 2.0 + Vehicle.ROUNDABOUT_ISLAND_GUARD_MARGIN;
        return radius < islandBoundary;
    }

    public static boolean isInsideRoundaboutBody(Vehicle v, RoundaboutIntersection roundabout) {
        return Math.hypot(v.getX() - roundabout.getX(), v.getY() - roundabout.getY())
                <= Vehicle.ROUNDABOUT_MAX_DRIVE_RADIUS + v.getHalfLength();
    }

    public static double roundaboutLaneMergeDistance(Vehicle v) {
        double r = Vehicle.ROUNDABOUT_MAX_DRIVE_RADIUS;
        return Math.sqrt(Math.max(0.0, r * r - v.laneOffsetVal * v.laneOffsetVal));
    }

    public static double getRoundaboutExitLaneAngle(Vehicle v, double theta) {
        double d = roundaboutLaneMergeDistance(v);
        double localX = d * Math.cos(theta) - v.laneOffsetVal * Math.sin(theta);
        double localY = d * Math.sin(theta) + v.laneOffsetVal * Math.cos(theta);
        return Math.atan2(localY, localX);
    }

    public static int getExitsRemaining(double currentAngle, double targetExitAngle, double[] roadAngles) {
        double targetNorm = Vehicle.normalizeAngle(targetExitAngle);
        double currentNorm = Vehicle.normalizeAngle(currentAngle);
        double diff = Vehicle.counterClockwiseDistance(currentNorm, targetNorm);

        int count = 0;
        for (double roadAngle : roadAngles) {
            double d = Vehicle.counterClockwiseDistance(currentNorm, roadAngle);
            if (d <= diff) {
                count++;
            }
        }
        return count;
    }

    public static int getClosestRoadIndex(double x, double y, double cx, double cy, double[] roadAngles) {
        double dx = x - cx;
        double dy = y - cy;
        double currentAngle = Math.atan2(dy, dx);
        int closestIdx = 0;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < roadAngles.length; i++) {
            double diff = Math.abs(Vehicle.normalizeAngle(currentAngle - roadAngles[i]));
            if (diff < minDist) {
                minDist = diff;
                closestIdx = i;
            }
        }
        return closestIdx;
    }

    public static int getApproachRoadIndex(double x, double y, double direction, double cx, double cy, double[] roadAngles) {
        int bestHeadingIdx = 0;
        double bestHeadingDiff = Double.MAX_VALUE;
        for (int i = 0; i < roadAngles.length; i++) {
            double incomingDirection = Vehicle.normalizeAngle(roadAngles[i] + Math.PI);
            double diff = Math.abs(Vehicle.normalizeAngle(direction - incomingDirection));
            if (diff < bestHeadingDiff) {
                bestHeadingDiff = diff;
                bestHeadingIdx = i;
            }
        }

        if (bestHeadingDiff < 0.65) {
            return bestHeadingIdx;
        }

        return getClosestRoadIndex(x, y, cx, cy, roadAngles);
    }

    public static boolean isNextExit(double currentAngle, double targetExitAngle, double[] roadAngles) {
        double minCCWDiff = Double.MAX_VALUE;
        int nextExitIdx = -1;
        for (int i = 0; i < roadAngles.length; i++) {
            double diff = Vehicle.counterClockwiseDistance(currentAngle, roadAngles[i]);
            if (diff > 0 && diff < minCCWDiff) {
                minCCWDiff = diff;
                nextExitIdx = i;
            }
        }
        if (nextExitIdx != -1) {
            return Math.abs(Vehicle.normalizeAngle(roadAngles[nextExitIdx] - targetExitAngle)) < 0.05;
        }
        return false;
    }

    public static boolean hasReachedCounterClockwiseExit(double previousAngle, double currentAngle, double targetAngle) {
        double travelled = Vehicle.counterClockwiseDistance(previousAngle, currentAngle);
        double distanceToTarget = Vehicle.counterClockwiseDistance(previousAngle, targetAngle);
        return distanceToTarget <= travelled + 0.03
                || Math.abs(Vehicle.normalizeAngle(currentAngle - targetAngle)) < 0.08;
    }
}
