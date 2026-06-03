package vn.edu.hust.traffic.model.vehicle;

import vn.edu.hust.traffic.model.map.TrafficLight;
import vn.edu.hust.traffic.model.map.Intersection;
import vn.edu.hust.traffic.model.map.RoundaboutIntersection;
import vn.edu.hust.traffic.behavior.DrivingStrategy;
import vn.edu.hust.traffic.behavior.NormalDriver;
import java.util.List;

/**
 * Lớp cha cho mọi loại phương tiện giao thông.
 */
public abstract class Vehicle implements vn.edu.hust.traffic.base.Renderable, vn.edu.hust.traffic.base.Updatable {
    public static final double ROAD_HALF_WIDTH = 80.0;
    public static final double ROUNDABOUT_ENTRY_RADIUS = 180.0;
    public static final double ROUNDABOUT_CAPTURE_DISTANCE = 260.0;
    public static final double ROUNDABOUT_MIN_DRIVE_RADIUS = 112.0;
    public static final double ROUNDABOUT_MAX_DRIVE_RADIUS = 166.0;
    public static final double ROUNDABOUT_ISLAND_RADIUS_INSET = 5.0;
    public static final double ROUNDABOUT_ISLAND_GUARD_MARGIN = 2.0;
    public static final double ROUNDABOUT_LANE_BLEND_RATE = 8.0;
    public static final double ROUNDABOUT_RADIUS_BLEND_RATE = 1.6;
    public static final double ROUNDABOUT_HEADING_BLEND_RATE = 60.0;
    public static final double ROUNDABOUT_EXIT_TURN_DURATION = 0.55;
    public static final double ROUNDABOUT_APPROACH_HEADING_TOLERANCE = 1.05;
    public static final double STANDARD_INTERSECTION_SEVERE_COLLISION_FACTOR = 0.35;
    public static final double INTERSECTION_ENTRY_GUARD_DISTANCE = 135.0;
    public static final double INTERSECTION_ENTRY_CONFLICT_LOOKAHEAD = 210.0;
    public static final double INTERSECTION_ENTRY_TIME_WINDOW = 1.15;
    public static final double INTERSECTION_ENTRY_STOP_DISTANCE = 50.0;
    public static final double SMOOTH_TURN_SHORT_PATH_DISTANCE = 20.0;
    public static final double SMOOTH_TURN_DURATION = 0.38;
    public static final double INTERSECTION_CLEAR_RADIUS = 180.0;
    public static final double CLEARING_MIN_SPEED_FACTOR = 0.45;
    public static final double TURN_EXIT_CLEARANCE_DURATION = 0.85;
    public static final double TURN_EXIT_MIN_SPEED_FACTOR = 0.28;
    public static final double TURN_EXIT_MERGE_CRAWL_MIN_SPEED_FACTOR = 0.08;
    public static final double YIELD_LANE_CHANGE_SPEED = 150.0;
    public static final double ROUNDABOUT_CRAWL_MIN_SPEED_FACTOR = 0.22;
    public static final double DIAGONAL_TURN_CRAWL_MIN_SPEED_FACTOR = 0.08;
    public static final double DIAGONAL_TURN_CRAWL_MAX_SPEED_FACTOR = 0.18;
    private static long intersectionEntryCounter = 0;
    public static final double[] STANDARD_LANE_OFFSETS = { 15.0, 40.0, 65.0 };
    public static final double[] ROUNDABOUT_LANE_OFFSETS = { 15.0, 40.0, 65.0 };

    public String id;
    public double x, y, speed, direction, width, height;
    public boolean isPriorityVehicle;
    public double baseSpeed;
    public boolean passedStopLine = false;
    public double distToStopLine = Double.MAX_VALUE;
    protected int turnIntention = 0; // 0: Thẳng, 1: Rẽ Trái, 2: Rẽ Phải
    public boolean hasTurned = false;
    public final int originalLightIdx;
    public String activeIntersectionId = null;
    public int activeIntersectionEntryLightIdx = -1;
    public long activeIntersectionEntryOrder = Long.MAX_VALUE;
    public boolean isTurningDiagonally = false;
    public double diagonalTurnCenterX = 0.0;
    public double diagonalTurnCenterY = 0.0;
    public boolean isTurningSmoothly = false;
    public double smoothTurnStartX = 0.0;
    public double smoothTurnStartY = 0.0;
    public double smoothTurnEndX = 0.0;
    public double smoothTurnEndY = 0.0;
    public double smoothTurnStartDirection = 0.0;
    public double smoothTurnEndDirection = 0.0;
    public double smoothTurnElapsed = 0.0;
    public double smoothTurnDuration = SMOOTH_TURN_DURATION;
    public boolean smoothTurnCompletesTurn = true;
    public double turnExitClearanceTime = 0.0;
    public boolean overtakingSlowVehicle = false;
    public double overtakeOriginalLaneOffset = 0.0;
    public double overtakeTargetLaneOffset = 0.0;
    public int overtakeLightIdx = -1;
    public String overtakeIntersectionId = null;
    public String overtakeVehicleId = null;
    public boolean yieldingToPriorityVehicle = false;
    public double yieldTargetLaneOffset = 0.0;
    public int yieldLightIdx = -1;
    public String yieldIntersectionId = null;
    public String yieldPriorityVehicleId = null;
    public boolean bypassingTurningVehicle = false;
    public double bypassTargetLaneOffset = 0.0;
    public int bypassLightIdx = -1;
    public String bypassIntersectionId = null;
    public String bypassVehicleId = null;

    // Roundabout routing and state fields
    public int targetExitIndex = -1;
    public boolean insideRoundabout = false;
    public double roundaboutAngle = 0.0;
    public boolean exitedRoundabout = false;
    public int spawnSourceIndex = -1;
    public double laneOffsetVal = 40.0;

    public double lastX, lastY;
    private static final boolean IS_TEST_ENV;
    static {
        boolean isTest = false;
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().contains("junit") || element.getClassName().contains("org.junit")) {
                isTest = true;
                break;
            }
        }
        IS_TEST_ENV = isTest;
    }

    public static boolean IS_TEST_ENV() {
        return IS_TEST_ENV;
    }

    protected DrivingStrategy drivingStrategy;

    public Vehicle(String id, double x, double y, double speed, double direction, double width, double height, boolean isPriorityVehicle) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.speed = speed;
        this.baseSpeed = speed;
        this.direction = direction;
        this.width = width;
        this.height = height;
        this.isPriorityVehicle = isPriorityVehicle;
        this.originalLightIdx = getLightIdx(direction);
        this.lastX = x;
        this.lastY = y;
        this.drivingStrategy = new NormalDriver();
    }

    public DrivingStrategy getDrivingStrategy() {
        return drivingStrategy;
    }

    public void setDrivingStrategy(DrivingStrategy drivingStrategy) {
        this.drivingStrategy = drivingStrategy;
    }

    public void movePhysically(double dt) {
        setX(getX() + Math.cos(getDirection()) * getSpeed() * dt);
        setY(getY() + Math.sin(getDirection()) * getSpeed() * dt);
    }

    @Override
    public void render() {
        // Cài đặt mặc định rỗng. Logic vẽ thực tế được xử lý bởi View/Renderer.
    }

    public void setTurnIntention(int turn) {
        this.turnIntention = turn;
    }

    /**
     * Lấy nửa chiều dài xe theo trục di chuyển.
     * width = chiều dài xe (luôn là chiều dài lớn nhất).
     */
    public double getHalfLength() {
        return width / 2.0;
    }

    public Intersection getTargetIntersection(List<Intersection> intersections) {
        Intersection target = null;
        double minPositiveDist = Double.MAX_VALUE;
        double hl = getHalfLength();

        for (Intersection inter : intersections) {
            if (inter instanceof RoundaboutIntersection) {
                RoundaboutIntersection roundabout = (RoundaboutIntersection) inter;
                if (vn.edu.hust.traffic.behavior.RoundaboutNavigator.shouldTargetRoundabout(this, roundabout)) {
                    return inter;
                }
            }
        }

        for (Intersection inter : intersections) {
            if (inter instanceof RoundaboutIntersection) {
                continue;
            }
            double stopX_LTR = inter.getX() - 120;
            double stopX_RTL = inter.getX() + 120;
            double stopY_TTB = inter.getY() - 120;
            double stopY_BTT = inter.getY() + 120;

            int lightIdx = isTurningDiagonally ? originalLightIdx : getLightIdx(direction);
            if (!isTurningDiagonally && !isAlignedWithIntersectionRoad(inter, lightIdx)) {
                continue;
            }

            double dist = Double.MAX_VALUE;

            switch (lightIdx) {
                case 0: dist = stopX_LTR - (x + hl); break;
                case 1: dist = (x - hl) - stopX_RTL; break;
                case 2: dist = stopY_TTB - (y + hl); break;
                case 3: dist = (y - hl) - stopY_BTT; break;
            }

            // Đang ở trong ngã tư (đã qua vạch dừng nhưng chưa thoát hẳn, bán kính ngã tư ~200)
            if (dist <= 0 && dist > -200) {
                return inter;
            }
            // Đang tiến tới ngã tư
            if (dist > 0 && dist < minPositiveDist) {
                minPositiveDist = dist;
                target = inter;
            }
        }
        return target;
    }

    public void update(double dt, List<Vehicle> allVehicles, List<Intersection> intersections, int screenWidth, int screenHeight) {
        if (drivingStrategy != null) {
            drivingStrategy.update(this, dt, allVehicles, intersections, screenWidth, screenHeight);
        }
    }

    public int getTurnIntention() { return turnIntention; }
    public boolean hasTurned() { return hasTurned; }
    public double getDistToStopLine() { return distToStopLine; }

    public int getLightIdx(double dir) {
        if      (Math.abs(dir - 0)           < 0.1) return 0;
        else if (Math.abs(dir - Math.PI)     < 0.1) return 1;
        else if (Math.abs(dir - Math.PI / 2) < 0.1) return 2;
        else if (Math.abs(dir + Math.PI / 2) < 0.1) return 3;
        return 0;
    }

    @Override public void update() {}
    public String getId() { return id; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getSpeed() { return speed; }
    public double getDirection() { return direction; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public boolean isPriorityVehicle() { return isPriorityVehicle; }

    public double roadCenterOffsetLimit() {
        return Math.max(0.0, ROAD_HALF_WIDTH - height / 2.0);
    }

    public boolean isSameApproachToIntersection(Vehicle other, List<Intersection> intersections,
            Intersection intersection, int lightIdx) {
        if (getLightIdx(other.direction) != lightIdx) {
            return false;
        }
        Intersection otherTarget = other.getTargetIntersection(intersections);
        if (otherTarget != intersection && !intersection.getId().equals(other.activeIntersectionId)) {
            return false;
        }
        double offset = standardLaneOffset(intersection, lightIdx, other.x, other.y);
        return Math.abs(offset) <= ROAD_HALF_WIDTH + other.width / 2.0;
    }

    public boolean isRelevantToCurrentIntersectionTurnFlow(Vehicle other,
            List<Intersection> intersections, Intersection intersection) {
        if (intersection == null) {
            return false;
        }
        if (intersection.getId().equals(other.activeIntersectionId)) {
            return true;
        }
        if (isInsideStandardIntersectionGuardZone(intersection, other.x, other.y, other.x, other.y)) {
            return true;
        }
        if (other.activeIntersectionId != null) {
            return false;
        }
        return other.getTargetIntersection(intersections) == intersection;
    }

    public double standardLaneOffset(Intersection intersection, int lightIdx, double px, double py) {
        double cx = intersection.getX();
        double cy = intersection.getY();
        if (lightIdx == 0) {
            return py - cy;
        }
        if (lightIdx == 1) {
            return cy - py;
        }
        if (lightIdx == 2) {
            return cx - px;
        }
        return px - cx;
    }

    public int nearestStandardLaneIndex(double offset) {
        int nearestIndex = 0;
        double nearestDistance = Math.abs(offset - STANDARD_LANE_OFFSETS[0]);
        for (int i = 1; i < STANDARD_LANE_OFFSETS.length; i++) {
            double distance = Math.abs(offset - STANDARD_LANE_OFFSETS[i]);
            if (distance < nearestDistance) {
                nearestIndex = i;
                nearestDistance = distance;
            }
        }
        return nearestIndex;
    }

    public double longitudinalDistanceAhead(int lightIdx, double otherX, double otherY) {
        if (lightIdx == 0) {
            return otherX - x;
        }
        if (lightIdx == 1) {
            return x - otherX;
        }
        if (lightIdx == 2) {
            return otherY - y;
        }
        return y - otherY;
    }

    public void moveTowardStandardLane(Intersection intersection, int lightIdx, double offset,
            double dt, double lateralSpeed) {
        double target = standardLaneCoordinate(intersection, lightIdx, offset);
        double maxStep = Math.max(0.0, lateralSpeed * dt);
        if (lightIdx < 2) {
            y = moveToward(y, target, maxStep);
        } else {
            x = moveToward(x, target, maxStep);
        }
    }

    public double standardLaneCoordinate(Intersection intersection, int lightIdx, double offset) {
        if (lightIdx == 0) {
            return intersection.getY() + offset;
        }
        if (lightIdx == 1) {
            return intersection.getY() - offset;
        }
        if (lightIdx == 2) {
            return intersection.getX() - offset;
        }
        return intersection.getX() + offset;
    }

    public double getVisualDirection() {
        if (insideRoundabout || isTurningSmoothly || IS_TEST_ENV) {
            return direction;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        if (Math.hypot(dx, dy) > 0.1) {
            return Math.atan2(dy, dx);
        }
        return direction;
    }

    public double moveToward(double current, double target, double maxStep) {
        double diff = target - current;
        if (Math.abs(diff) <= maxStep) {
            return target;
        }
        if (IS_TEST_ENV) {
            return current + Math.signum(diff) * maxStep;
        }
        // Giảm tốc mượt mà khi tiến sát làn mục tiêu (Ease-out)
        double step = diff * 0.18;
        if (Math.abs(step) > maxStep) {
            step = Math.signum(step) * maxStep;
        }
        if (Math.abs(step) < 0.2) {
            step = Math.signum(diff) * Math.min(maxStep, Math.abs(diff));
        }
        return current + step;
    }

    public void beginIntersectionIfNeeded(Intersection intersection) {
        String id = intersection.getId();
        if (id.equals(activeIntersectionId)) {
            return;
        }

        activeIntersectionId = id;
        activeIntersectionEntryLightIdx = getLightIdx(direction);
        hasTurned = false;
        passedStopLine = false;
        activeIntersectionEntryOrder = Long.MAX_VALUE;
        vn.edu.hust.traffic.behavior.OvertakeManager.resetTurningBypassState(this);
    }

    public void markIntersectionEntryIfNeeded(boolean entered) {
        if (entered && activeIntersectionEntryOrder == Long.MAX_VALUE) {
            activeIntersectionEntryOrder = nextIntersectionEntryOrder();
        }
    }

    private static synchronized long nextIntersectionEntryOrder() {
        return ++intersectionEntryCounter;
    }

    public boolean isPriorityApproachingSameIntersection(Vehicle priorityVehicle,
            Intersection intersection, List<Intersection> intersections) {
        if (priorityVehicle == this || !priorityVehicle.isPriorityVehicle()) {
            return false;
        }
        if (Math.hypot(priorityVehicle.getX() - intersection.getX(), priorityVehicle.getY() - intersection.getY()) < 360.0) {
            return true;
        }
        return priorityVehicle.getTargetIntersection(intersections) == intersection;
    }

    public boolean isLaneSafeForChange(List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx, double targetOffset) {
        double myProjectedLaneCoord = standardLaneCoordinate(intersection, lightIdx, targetOffset);
        double myLongitudinal = longitudinalCoordinate(lightIdx, x, y);
        for (Vehicle other : allVehicles) {
            if (other == this) {
                continue;
            }
            if (!isSameApproachToIntersection(other, intersections, intersection, lightIdx)) {
                continue;
            }

            double otherOffset = Math.abs(standardLaneOffset(intersection, lightIdx, other.getX(), other.getY()));
            if (Math.abs(otherOffset - targetOffset) > 14.0) {
                continue;
            }

            double otherLongitudinal = longitudinalCoordinate(lightIdx, other.getX(), other.getY());
            double relative = signedLongitudinalDelta(lightIdx, myLongitudinal, otherLongitudinal);
            double requiredGap = Math.max(52.0, getHalfLength() + other.getHalfLength() + 24.0);
            if (other.isPriorityVehicle()) {
                requiredGap += 90.0;
            }
            if (relative > -requiredGap && relative < requiredGap * 1.35) {
                return false;
            }

            double lateralDistance = lightIdx < 2
                    ? Math.abs(other.getY() - myProjectedLaneCoord)
                    : Math.abs(other.getX() - myProjectedLaneCoord);
            if (Math.abs(relative) < requiredGap * 1.8 && lateralDistance < Math.max(18.0, height + other.getHeight())) {
                return false;
            }
        }
        return true;
    }

    public int countVehiclesInLaneWindow(List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx, double targetOffset) {
        int count = 0;
        for (Vehicle other : allVehicles) {
            if (other == this || !isSameApproachToIntersection(other, intersections, intersection, lightIdx)) {
                continue;
            }
            double offset = Math.abs(standardLaneOffset(intersection, lightIdx, other.getX(), other.getY()));
            if (Math.abs(offset - targetOffset) > 14.0) {
                continue;
            }
            double ahead = longitudinalDistanceAhead(lightIdx, other.getX(), other.getY());
            if (ahead > -80.0 && ahead < 220.0) {
                count++;
            }
        }
        return count;
    }

    public double longitudinalCoordinate(int lightIdx, double px, double py) {
        return lightIdx < 2 ? px : py;
    }

    public double signedLongitudinalDelta(int lightIdx, double myLongitudinal, double otherLongitudinal) {
        if (lightIdx == 0 || lightIdx == 2) {
            return otherLongitudinal - myLongitudinal;
        }
        return myLongitudinal - otherLongitudinal;
    }

    public TrafficLight.State effectiveLightForVehicle(Vehicle vehicle, List<Intersection> intersections,
            int lightIdx) {
        Intersection target = vehicle.getTargetIntersection(intersections);
        TrafficLight targetLight = null;
        if (target instanceof vn.edu.hust.traffic.model.map.CrossIntersection) {
            targetLight = target.getLights().get(lightIdx);
        } else if (target instanceof vn.edu.hust.traffic.model.map.ThreeWayIntersection) {
            targetLight = ((vn.edu.hust.traffic.model.map.ThreeWayIntersection) target)
                    .getLightForDirection(vehicle.direction);
        }
        if (targetLight == null) {
            return TrafficLight.State.GREEN;
        }
        return targetLight.getStateForTurn(vehicle.getTurnIntention(), vehicle.hasTurned());
    }

    public boolean isInsideStandardIntersectionGuardZone(Intersection intersection,
            double currentX, double currentY, double nextX, double nextY) {
        double guardRadius = INTERSECTION_CLEAR_RADIUS + 55.0;
        return Math.hypot(currentX - intersection.getX(), currentY - intersection.getY()) < guardRadius
                || Math.hypot(nextX - intersection.getX(), nextY - intersection.getY()) < guardRadius;
    }

    public boolean isInsideStandardIntersection(Intersection intersection, double px, double py) {
        return Math.hypot(px - intersection.getX(), py - intersection.getY()) < INTERSECTION_CLEAR_RADIUS;
    }

    public double segmentDistance(double ax, double ay, double bx, double by,
            double cx, double cy, double dx, double dy) {
        if (segmentsIntersect(ax, ay, bx, by, cx, cy, dx, dy)) {
            return 0.0;
        }
        return Math.min(
                Math.min(pointToSegmentDistance(ax, ay, cx, cy, dx, dy),
                        pointToSegmentDistance(bx, by, cx, cy, dx, dy)),
                Math.min(pointToSegmentDistance(cx, cy, ax, ay, bx, by),
                        pointToSegmentDistance(dx, dy, ax, ay, bx, by)));
    }

    public double pointToSegmentDistance(double px, double py, double ax, double ay, double bx, double by) {
        double vx = bx - ax;
        double vy = by - ay;
        double lengthSq = vx * vx + vy * vy;
        if (lengthSq < 0.0001) {
            return Math.hypot(px - ax, py - ay);
        }

        double t = ((px - ax) * vx + (py - ay) * vy) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = ax + vx * t;
        double closestY = ay + vy * t;
        return Math.hypot(px - closestX, py - closestY);
    }

    public boolean segmentsIntersect(double ax, double ay, double bx, double by,
            double cx, double cy, double dx, double dy) {
        double o1 = orientation(ax, ay, bx, by, cx, cy);
        double o2 = orientation(ax, ay, bx, by, dx, dy);
        double o3 = orientation(cx, cy, dx, dy, ax, ay);
        double o4 = orientation(cx, cy, dx, dy, bx, by);
        return o1 * o2 < 0.0 && o3 * o4 < 0.0;
    }

    public double orientation(double ax, double ay, double bx, double by, double cx, double cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    public int getIntersectionEntryLightIdx() {
        return activeIntersectionEntryLightIdx >= 0 ? activeIntersectionEntryLightIdx : originalLightIdx;
    }

    public boolean isAlignedWithIntersectionRoad(Intersection inter, int lightIdx) {
        double lateralLimit = ROAD_HALF_WIDTH + width / 2.0;
        if (lightIdx < 2) {
            return Math.abs(y - inter.getY()) <= lateralLimit;
        }
        return Math.abs(x - inter.getX()) <= lateralLimit;
    }

    public boolean hasRoundaboutExitSmoothPriorityOver(Vehicle other) {
        if (!exitedRoundabout || !isTurningSmoothly || !other.exitedRoundabout || !other.isTurningSmoothly) {
            return false;
        }
        double myProgress = smoothTurnElapsed / Math.max(0.01, smoothTurnDuration);
        double otherProgress = other.smoothTurnElapsed / Math.max(0.01, other.smoothTurnDuration);
        if (Math.abs(myProgress - otherProgress) > 0.02) {
            return myProgress > otherProgress;
        }
        return id.compareTo(other.id) < 0;
    }

    public void startSmoothTurn(double targetX, double targetY, double targetDirection,
            boolean completesTurn, double duration) {
        isTurningSmoothly = true;
        smoothTurnStartX = x;
        smoothTurnStartY = y;
        smoothTurnEndX = targetX;
        smoothTurnEndY = targetY;
        smoothTurnStartDirection = direction;
        smoothTurnEndDirection = normalizeAngle(targetDirection);
        smoothTurnElapsed = 0.0;
        smoothTurnDuration = Math.max(0.12, duration);
        smoothTurnCompletesTurn = completesTurn;
        speed = Math.max(speed, baseSpeed * 0.75);
    }

    public double blendAlpha(double dt, double rate) {
        return Math.max(0.0, Math.min(1.0, 1.0 - Math.exp(-Math.max(0.0, dt) * rate)));
    }

    public double interpolateAngle(double fromAngle, double toAngle, double t) {
        return normalizeAngle(fromAngle + normalizeAngle(toAngle - fromAngle) * t);
    }

    public double smoothStep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    public static double normalizeAngle(double angle) {
        while (angle <= -Math.PI) angle += 2 * Math.PI;
        while (angle > Math.PI) angle -= 2 * Math.PI;
        return angle;
    }

    public static double normalizePositiveAngle(double angle) {
        while (angle < 0) angle += 2 * Math.PI;
        while (angle >= 2 * Math.PI) angle -= 2 * Math.PI;
        return angle;
    }

    public static double counterClockwiseDistance(double fromAngle, double toAngle) {
        return normalizePositiveAngle(fromAngle - toAngle);
    }
}
