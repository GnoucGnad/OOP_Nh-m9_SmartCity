package vn.edu.hust.traffic.model.vehicle;

import vn.edu.hust.traffic.model.map.TrafficLight;
import vn.edu.hust.traffic.model.map.Intersection;
import vn.edu.hust.traffic.model.map.RoundaboutIntersection;
import java.util.List;

/**
 * Lớp cha cho mọi loại phương tiện giao thông.
 */
public abstract class Vehicle implements vn.edu.hust.traffic.base.Renderable, vn.edu.hust.traffic.base.Updatable {
    static final double ROAD_HALF_WIDTH = 80.0;
    static final double ROUNDABOUT_ENTRY_RADIUS = 180.0;
    static final double ROUNDABOUT_CAPTURE_DISTANCE = 260.0;
    static final double ROUNDABOUT_MIN_DRIVE_RADIUS = 112.0;
    static final double ROUNDABOUT_MAX_DRIVE_RADIUS = 166.0;
    static final double ROUNDABOUT_ISLAND_RADIUS_INSET = 5.0;
    static final double ROUNDABOUT_ISLAND_GUARD_MARGIN = 2.0;
    static final double ROUNDABOUT_LANE_BLEND_RATE = 8.0;
    static final double ROUNDABOUT_RADIUS_BLEND_RATE = 1.6;
    static final double ROUNDABOUT_HEADING_BLEND_RATE = 60.0;
    static final double ROUNDABOUT_EXIT_TURN_DURATION = 0.55;
    static final double ROUNDABOUT_APPROACH_HEADING_TOLERANCE = 1.05;
    static final double STANDARD_INTERSECTION_SEVERE_COLLISION_FACTOR = 0.35;
    static final double INTERSECTION_ENTRY_GUARD_DISTANCE = 135.0;
    static final double INTERSECTION_ENTRY_CONFLICT_LOOKAHEAD = 210.0;
    static final double INTERSECTION_ENTRY_TIME_WINDOW = 1.15;
    static final double INTERSECTION_ENTRY_STOP_DISTANCE = 50.0;
    static final double SMOOTH_TURN_SHORT_PATH_DISTANCE = 20.0;
    static final double SMOOTH_TURN_DURATION = 0.38;
    static final double INTERSECTION_CLEAR_RADIUS = 180.0;
    static final double CLEARING_MIN_SPEED_FACTOR = 0.45;
    static final double TURN_EXIT_CLEARANCE_DURATION = 0.85;
    static final double TURN_EXIT_MIN_SPEED_FACTOR = 0.28;
    static final double TURN_EXIT_MERGE_CRAWL_MIN_SPEED_FACTOR = 0.08;
    static final double YIELD_LANE_CHANGE_SPEED = 150.0;
    static final double ROUNDABOUT_CRAWL_MIN_SPEED_FACTOR = 0.22;
    static final double DIAGONAL_TURN_CRAWL_MIN_SPEED_FACTOR = 0.08;
    static final double DIAGONAL_TURN_CRAWL_MAX_SPEED_FACTOR = 0.18;
    private static long intersectionEntryCounter = 0;
    static final double[] STANDARD_LANE_OFFSETS = { 15.0, 40.0, 65.0 };
    static final double[] ROUNDABOUT_LANE_OFFSETS = { 15.0, 40.0, 65.0 };

    protected String id;
    protected double x, y, speed, direction, width, height;
    protected boolean isPriorityVehicle;
    protected double baseSpeed;
    protected boolean passedStopLine = false;
    protected double distToStopLine = Double.MAX_VALUE;
    protected int turnIntention = 0; // 0: Thẳng, 1: Rẽ Trái, 2: Rẽ Phải
    protected boolean hasTurned = false;
    protected final int originalLightIdx;
    protected String activeIntersectionId = null;
    protected int activeIntersectionEntryLightIdx = -1;
    protected long activeIntersectionEntryOrder = Long.MAX_VALUE;
    protected boolean isTurningDiagonally = false;
    protected double diagonalTurnCenterX = 0.0;
    protected double diagonalTurnCenterY = 0.0;
    protected boolean isTurningSmoothly = false;
    protected double smoothTurnStartX = 0.0;
    protected double smoothTurnStartY = 0.0;
    protected double smoothTurnEndX = 0.0;
    protected double smoothTurnEndY = 0.0;
    protected double smoothTurnStartDirection = 0.0;
    protected double smoothTurnEndDirection = 0.0;
    protected double smoothTurnElapsed = 0.0;
    protected double smoothTurnDuration = SMOOTH_TURN_DURATION;
    protected boolean smoothTurnCompletesTurn = true;
    protected double turnExitClearanceTime = 0.0;
    protected boolean overtakingSlowVehicle = false;
    protected double overtakeOriginalLaneOffset = 0.0;
    protected double overtakeTargetLaneOffset = 0.0;
    protected int overtakeLightIdx = -1;
    protected String overtakeIntersectionId = null;
    protected String overtakeVehicleId = null;
    protected boolean yieldingToPriorityVehicle = false;
    protected double yieldTargetLaneOffset = 0.0;
    protected int yieldLightIdx = -1;
    protected String yieldIntersectionId = null;
    protected String yieldPriorityVehicleId = null;
    protected boolean bypassingTurningVehicle = false;
    protected double bypassTargetLaneOffset = 0.0;
    protected int bypassLightIdx = -1;
    protected String bypassIntersectionId = null;
    protected String bypassVehicleId = null;

    // Roundabout routing and state fields
    protected int targetExitIndex = -1;
    public boolean insideRoundabout = false;
    public double roundaboutAngle = 0.0;
    protected boolean exitedRoundabout = false;
    protected int spawnSourceIndex = -1;
    protected double laneOffsetVal = 40.0;

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
    double getHalfLength() {
        return width / 2.0;
    }

    public Intersection getTargetIntersection(List<Intersection> intersections) {
        Intersection target = null;
        double minPositiveDist = Double.MAX_VALUE;
        double hl = getHalfLength();

        for (Intersection inter : intersections) {
            if (inter instanceof RoundaboutIntersection) {
                RoundaboutIntersection roundabout = (RoundaboutIntersection) inter;
                if (RoundaboutNavigator.shouldTargetRoundabout(this, roundabout)) {
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
        double safeDistance = (width > 30) ? 50 : 30;
        final double SLOW_ZONE = 80.0;
        boolean shouldStop = false;
        boolean hardSameLaneBlockAhead = false;
        boolean turnExitMergeCrawl = false;
        double currentTargetSpeed = baseSpeed;
        boolean forcingTurnExit = hasTurned && turnExitClearanceTime > 0.0;
        turnExitClearanceTime = Math.max(0.0, turnExitClearanceTime - Math.max(0.0, dt));

        if (TrajectoryController.continueSmoothTurn(this, dt, allVehicles, intersections)) {
            return;
        }

        if (TrajectoryController.continueDiagonalRightTurn(this, dt, allVehicles)) {
            return;
        }

        Intersection targetInter = getTargetIntersection(intersections);
        if (targetInter == null) {
            activeIntersectionId = null;
            activeIntersectionEntryLightIdx = -1;
            activeIntersectionEntryOrder = Long.MAX_VALUE;
            OvertakeManager.resetOvertakeState(this);
            OvertakeManager.resetYieldState(this);
            OvertakeManager.resetTurningBypassState(this);
            this.speed = baseSpeed;
            movePhysically(dt);
            if (isTurningDiagonally) {
                TrajectoryController.finishDiagonalRightTurnIfNeeded(this, diagonalTurnCenterX, diagonalTurnCenterY);
            }
            return;
        }

        if (targetInter instanceof RoundaboutIntersection) {
            OvertakeManager.resetOvertakeState(this);
            OvertakeManager.resetYieldState(this);
            OvertakeManager.resetTurningBypassState(this);
            RoundaboutNavigator.updateRoundabout(this, dt, allVehicles, (RoundaboutIntersection) targetInter);
            return;
        }

        beginIntersectionIfNeeded(targetInter);
        double cx = targetInter.getX();
        double cy = targetInter.getY();
        int lightIdx = getLightIdx(direction);
        int entryLightIdx = getIntersectionEntryLightIdx();

        // BẢO VỆ NGÃ 3: RTL không rẽ trái xuống Nam, LTR không rẽ phải xuống Nam, TTB không đi thẳng xuống Nam
        if (targetInter instanceof vn.edu.hust.traffic.model.map.ThreeWayIntersection
                && !hasTurned && !isTurningDiagonally) {
            if (entryLightIdx == 0 && turnIntention == 2) { // LTR (đi Đông) không thể rẽ phải (Nam)
                turnIntention = Math.random() < 0.5 ? 0 : 1;
            } else if (entryLightIdx == 1 && turnIntention == 1) { // RTL (đi Tây) không thể rẽ trái (Nam)
                turnIntention = Math.random() < 0.5 ? 0 : 2;
            } else if (entryLightIdx == 2 && turnIntention == 0) { // TTB (đi Nam) không thể đi thẳng (Nam)
                turnIntention = Math.random() < 0.5 ? 1 : 2;
            }
        }

        TrafficLight light = null;
        if (targetInter instanceof vn.edu.hust.traffic.model.map.CrossIntersection) {
            light = targetInter.getLights().get(lightIdx);
        } else if (targetInter instanceof vn.edu.hust.traffic.model.map.ThreeWayIntersection
                && !hasTurned && !isTurningDiagonally) {
            light = ((vn.edu.hust.traffic.model.map.ThreeWayIntersection)targetInter).getLightForDirection(direction);
        }
        if (light == null) {
            this.speed = baseSpeed;
            movePhysically(dt);
            return;
        }

        double stopX_LTR = cx - 120;
        double stopX_RTL = cx + 120;
        double stopY_TTB = cy - 120;
        double stopY_BTT = cy + 120;

        distToStopLine = Double.MAX_VALUE;
        double hl = getHalfLength();

        switch (lightIdx) {
            case 0:
                distToStopLine = stopX_LTR - (x + hl);
                passedStopLine = (x + hl) >= stopX_LTR;
                break;
            case 1:
                distToStopLine = (x - hl) - stopX_RTL;
                passedStopLine = (x - hl) <= stopX_RTL;
                break;
            case 2:
                distToStopLine = stopY_TTB - (y + hl);
                passedStopLine = (y + hl) >= stopY_TTB;
                break;
            case 3:
                distToStopLine = (y - hl) - stopY_BTT;
                passedStopLine = (y - hl) <= stopY_BTT;
                break;
        }
        if (forcingTurnExit) {
            passedStopLine = true;
            distToStopLine = -1.0;
        }
        markIntersectionEntryIfNeeded(passedStopLine);

        // BƯỚC 0.5: Kiểm tra và thực hiện rẽ nếu xe đang ở giữa ngã tư

        // THÊM MỚI: QUỸ ĐẠO RẼ PHẢI CHÉO GÓC (VÀO ĐƯỜNG RẼ TẮT)
        if (!hasTurned && turnIntention == 2) {
            double TURN_DIST = 233.0;
            double END_LANE = 65.0;

            if (!isTurningDiagonally) {
                boolean readyToDiagonal = false;
                if (entryLightIdx == 0) readyToDiagonal = (x >= cx - TURN_DIST);
                else if (entryLightIdx == 1) readyToDiagonal = (x <= cx + TURN_DIST);
                else if (entryLightIdx == 2) readyToDiagonal = (y >= cy - TURN_DIST);
                else if (entryLightIdx == 3) readyToDiagonal = (y <= cy + TURN_DIST);

                if (readyToDiagonal && IntersectionNavigator.hasBlockedDiagonalRightTurnEntry(
                        this, allVehicles, intersections, targetInter, entryLightIdx)) {
                    speed = 0.0;
                    return;
                }

                if (readyToDiagonal) {
                    if (!isPriorityVehicle && IntersectionNavigator.hasUnsafeIntersectionEntryConflict(this, allVehicles, intersections,
                            targetInter, entryLightIdx, TrafficLight.State.GREEN)) {
                        speed = 0.0;
                        return;
                    }
                    isTurningDiagonally = true;
                    diagonalTurnCenterX = cx;
                    diagonalTurnCenterY = cy;
                    passedStopLine = true; // Bỏ qua đèn đỏ vì làn rẽ phải luôn thông
                    markIntersectionEntryIfNeeded(true);
                    double targetX = x;
                    double targetY = y;
                    double targetDirection = direction;
                    double advance = Math.min(32.0, Math.max(14.0, baseSpeed * 0.22));
                    if (entryLightIdx == 0) {
                        targetX = Math.max(x, cx - TURN_DIST) + Math.cos(Math.PI / 4) * advance;
                        targetY = y + Math.sin(Math.PI / 4) * advance;
                        targetDirection = Math.PI / 4;
                    } else if (entryLightIdx == 1) {
                        targetX = Math.min(x, cx + TURN_DIST) + Math.cos(-Math.PI * 3 / 4) * advance;
                        targetY = y + Math.sin(-Math.PI * 3 / 4) * advance;
                        targetDirection = -Math.PI * 3 / 4;
                    } else if (entryLightIdx == 2) {
                        targetX = x + Math.cos(Math.PI * 3 / 4) * advance;
                        targetY = Math.max(y, cy - TURN_DIST) + Math.sin(Math.PI * 3 / 4) * advance;
                        targetDirection = Math.PI * 3 / 4;
                    } else if (entryLightIdx == 3) {
                        targetX = x + Math.cos(-Math.PI / 4) * advance;
                        targetY = Math.min(y, cy + TURN_DIST) + Math.sin(-Math.PI / 4) * advance;
                        targetDirection = -Math.PI / 4;
                    }
                    startSmoothTurn(targetX, targetY, targetDirection, false, 0.22);
                    return;
                }
            }

            if (isTurningDiagonally) {
                boolean endDiagonal = false;
                if (entryLightIdx == 0) endDiagonal = (x >= cx - END_LANE);
                else if (entryLightIdx == 1) endDiagonal = (x <= cx + END_LANE);
                else if (entryLightIdx == 2) endDiagonal = (y >= cy - END_LANE);
                else if (entryLightIdx == 3) endDiagonal = (y <= cy + END_LANE);

                if (endDiagonal) {
                    TrajectoryController.finishDiagonalRightTurnIfNeeded(this, cx, cy);
                    if (isTurningSmoothly) {
                        return;
                    }

                    lightIdx = getLightIdx(direction); // Cập nhật lại tín hiệu đèn sau khi nắn thẳng trục
                }
            }
        }

        // RẼ TRÁI Ở GIỮA NGÃ TƯ
        if (!hasTurned && turnIntention == 1 && passedStopLine) {
            boolean readyToTurn = false;
            double targetCoord = 0;

            // Tính toán tọa độ chính xác để sau khi bẻ lái, xe nằm đúng boong giữa làn
            if (turnIntention == 1) { // Rẽ trái (vào làn priority offset 15)
                if (entryLightIdx == 0) { targetCoord = cx + 15; readyToTurn = (x >= targetCoord); }
                else if (entryLightIdx == 1) { targetCoord = cx - 15; readyToTurn = (x <= targetCoord); }
                else if (entryLightIdx == 2) { targetCoord = cy + 15; readyToTurn = (y >= targetCoord); }
                else if (entryLightIdx == 3) { targetCoord = cy - 15; readyToTurn = (y <= targetCoord); }
            }

            if (readyToTurn) {
                // Chỉnh thẳng góc tọa độ trục cũ vào đúng quỹ đạo trục mới
                double targetX = x;
                double targetY = y;
                double targetDirection = direction;
                if (entryLightIdx == 0 || entryLightIdx == 1) targetX = targetCoord;
                else targetY = targetCoord;

                if (turnIntention == 1) { // Rẽ trái
                    if (entryLightIdx == 0) targetDirection = -Math.PI/2;
                    else if (entryLightIdx == 1) targetDirection = Math.PI/2;
                    else if (entryLightIdx == 2) targetDirection = 0;
                    else if (entryLightIdx == 3) targetDirection = Math.PI;
                }
                startSmoothTurn(targetX, targetY, targetDirection, true, SMOOTH_TURN_DURATION);
                return;
            }
        }

        OvertakeManager.movePriorityToLeastBusyLaneIfRedQueueAhead(this, allVehicles, intersections, targetInter, lightIdx, dt);

        // BƯỚC 1: Quét tìm cứu thương khẩn cấp (Emergency Ambulance) để tiến hành Flee Mode
        boolean isFleeing = false;
        boolean yieldingThisUpdate = false;

        for (Vehicle other : allVehicles) {
            if (other.isPriorityVehicle && other != this) {
                if (!this.isPriorityVehicle
                        && !this.passedStopLine
                        && isPriorityApproachingSameIntersection(other, targetInter, intersections)) {
                    if (distToStopLine <= 45.0) {
                        shouldStop = true;
                    } else if (distToStopLine < 160.0) {
                        currentTargetSpeed = Math.min(currentTargetSpeed,
                                baseSpeed * Math.max(0.15, distToStopLine / 160.0));
                    }
                }

                int otherLightIdx = getLightIdx(other.direction);

                // 1. Nhường đường nếu xe ưu tiên đang áp sát phía sau trên cùng hướng tiếp cận.
                if (!this.isPriorityVehicle
                        && !this.passedStopLine
                        && otherLightIdx == lightIdx
                        && isSameApproachToIntersection(other, intersections, targetInter, lightIdx)) {
                    double behindDist = -longitudinalDistanceAhead(lightIdx, other.x, other.y);
                    if (behindDist > 0.0 && behindDist < 420.0
                            && (OvertakeManager.isContinuingYieldForPriority(this, targetInter, lightIdx, other)
                                    || OvertakeManager.isBlockingPriorityLane(this, targetInter, lightIdx, other))) {
                        isFleeing = true;
                        yieldingThisUpdate = true;
                        shouldStop = false;
                        currentTargetSpeed = Math.max(currentTargetSpeed, baseSpeed * 0.65);

                        double targetOffset = OvertakeManager.stableYieldLaneOffset(this, allVehicles, intersections, targetInter,
                                lightIdx, other);
                        moveTowardStandardLane(targetInter, lightIdx, targetOffset, dt, YIELD_LANE_CHANGE_SPEED);
                    }
                }
            }
        }
        if (!yieldingThisUpdate && OvertakeManager.continueYieldLaneChangeToTargetIfNeeded(this,
                allVehicles, intersections, targetInter, lightIdx, dt)) {
            isFleeing = true;
            yieldingThisUpdate = true;
            shouldStop = false;
            currentTargetSpeed = Math.max(currentTargetSpeed, baseSpeed * 0.65);
        }
        if (!yieldingThisUpdate) {
            OvertakeManager.resetYieldState(this);
        }

        // BƯỚC 2: Check đèn — dùng đèn PHÙ HỢP với ý định rẽ của xe
        //   turnIntention==0 (thẳng): xem đèn thẳng
        //   turnIntention==1 (rẽ trái): xem đèn mũi tên rẽ trái
        //   turnIntention==2 (rẽ phảI): luôn GREEN (Right Turn on Red)
        TrafficLight.State myEffectiveLight = light.getStateForTurn(turnIntention, hasTurned);
        boolean isRightTurnOnRed = (turnIntention == 2 && !hasTurned);
        boolean mustStopByLight = false;
        if (!isPriorityVehicle && !isFleeing && !forcingTurnExit) {
            if (myEffectiveLight == TrafficLight.State.RED ||
                (myEffectiveLight == TrafficLight.State.YELLOW && !passedStopLine)) {
                mustStopByLight = true;
            }
        }

        // BƯỚC 3: Dừng mềm trước vạch theo đèn tín hiệu
        if (mustStopByLight && !passedStopLine) {
            if (distToStopLine <= 0) {
                shouldStop = true;
            } else if (distToStopLine < SLOW_ZONE) {
                double ratio = distToStopLine / SLOW_ZONE;
                currentTargetSpeed = baseSpeed * ratio;
                if (distToStopLine < 5) shouldStop = true;
            }
        }

        // BƯỚC 3.5: Xe rẽ phải khi đèn đỏ — giảm tốc cẩn thận trước ngã tư, không dừng hẳn
        if (isRightTurnOnRed && !passedStopLine && light.getState() != TrafficLight.State.GREEN) {
            double cautionSpeed = baseSpeed * 0.4;
            if (distToStopLine < SLOW_ZONE && distToStopLine > 0) {
                double ratio = distToStopLine / SLOW_ZONE;
                currentTargetSpeed = Math.min(currentTargetSpeed, cautionSpeed * ratio + cautionSpeed * 0.3);
            } else if (distToStopLine <= 0) {
                currentTargetSpeed = Math.min(currentTargetSpeed, cautionSpeed);
            }
        }

        boolean unsafeEntryConflict = !forcingTurnExit
                && !passedStopLine
                && IntersectionNavigator.hasUnsafeIntersectionEntryConflict(this, allVehicles, intersections, targetInter, lightIdx,
                        myEffectiveLight);
        if (unsafeEntryConflict) {
            double ratio = Math.max(0.0, (distToStopLine - 8.0) / INTERSECTION_ENTRY_GUARD_DISTANCE);
            currentTargetSpeed = Math.min(currentTargetSpeed, baseSpeed * Math.min(0.45, ratio));
            if (distToStopLine <= INTERSECTION_ENTRY_STOP_DISTANCE) {
                shouldStop = true;
            }
        }

        if (OvertakeManager.updateNormalOvertakeIfNeeded(this, allVehicles, intersections, targetInter, lightIdx, dt,
                mustStopByLight, isFleeing)) {
            currentTargetSpeed = Math.max(currentTargetSpeed, baseSpeed * 0.95);
        }
        boolean bypassingTurningBlocker = OvertakeManager.updateTurningVehicleBypassIfNeeded(this,
                allVehicles, intersections, targetInter, lightIdx, dt);
        if (bypassingTurningBlocker) {
            shouldStop = false;
            currentTargetSpeed = Math.max(currentTargetSpeed, baseSpeed * (isPriorityVehicle ? 0.95 : 0.70));
        }

        // BƯỚC 4: Rà phanh động (Dynamic Right of Way) - Thuật toán giao tuyến quang học
        for (Vehicle other : allVehicles) {
            if (other == this) continue;
            if (forcingTurnExit) continue;
            if (bypassingTurningBlocker && OvertakeManager.isActiveTurningBypassBlocker(this, other, targetInter, lightIdx)) {
                continue;
            }

            int otherLightIdx = getLightIdx(other.direction);
            boolean sameAxis = (lightIdx < 2 && otherLightIdx < 2) || (lightIdx >= 2 && otherLightIdx >= 2);

            // Chỉ xét 2 xe có quỹ đạo chéo góc (cross-traffic)
            if (!sameAxis) {
                // Xác định tọa độ giao cắt của 2 quỹ đạo
                double intersectX, intersectY;
                if (lightIdx < 2) {
                    intersectY = this.y;
                    intersectX = other.x;
                } else {
                    intersectX = this.x;
                    intersectY = other.y;
                }

                // Khoảng cách từ mũi xe ĐẾN điểm giao cắt (dương = chưa tới, âm = đi lố qua rồi)
                double myDistToIntersect = 0;
                if (lightIdx == 0) myDistToIntersect = intersectX - this.x;
                else if (lightIdx == 1) myDistToIntersect = this.x - intersectX;
                else if (lightIdx == 2) myDistToIntersect = intersectY - this.y;
                else if (lightIdx == 3) myDistToIntersect = this.y - intersectY;

                double otherDistToIntersect = 0;
                if (otherLightIdx == 0) otherDistToIntersect = intersectX - other.x;
                else if (otherLightIdx == 1) otherDistToIntersect = other.x - intersectX;
                else if (otherLightIdx == 2) otherDistToIntersect = intersectY - other.y;
                else if (otherLightIdx == 3) otherDistToIntersect = other.y - intersectY;

                // Clearance phụ thuộc kích thước xe — xe lớn cần vùng lớn hơn
                double CLEARANCE = Math.max(40.0, Math.max(this.width, other.width) * 0.7);

                // 1. Phá băng giao thông: Ai ĐÃ qua rồi thì thoát ra khỏi vùng ảnh hưởng tuyệt đối!
                if (myDistToIntersect < -CLEARANCE || otherDistToIntersect < -CLEARANCE) {
                    continue;
                }

                // KHÔNG BAO GIỜ xung đột với xe xuất phát từ cùng một nhánh đường
                if (this.originalLightIdx == other.originalLightIdx) {
                    continue;
                }

                // 2. Chống lác (Deadlock anti-freeze): Bỏ qua xe đỗ chờ đèn đỏ
                //    - Xe đứng yên VÀ còn xa ngã tư (>50px) → chắc chắn đang chờ đèn
                //    - Xe đứng yên VÀ đèn của nó đang đỏ → đang tuân thủ đèn, không phải mối đe dọa
                if (other.speed < 0.5 && otherDistToIntersect > 50) {
                    continue;
                }
                TrafficLight otherLight = null;
                Intersection otherTarget = other.getTargetIntersection(intersections);
                if (otherTarget instanceof vn.edu.hust.traffic.model.map.CrossIntersection) {
                    otherLight = otherTarget.getLights().get(otherLightIdx);
                } else if (otherTarget instanceof vn.edu.hust.traffic.model.map.ThreeWayIntersection) {
                    otherLight = ((vn.edu.hust.traffic.model.map.ThreeWayIntersection)otherTarget).getLightForDirection(other.direction);
                }

                if (otherLight != null) {
                    TrafficLight.State otherEffState = otherLight.getStateForTurn(other.turnIntention, other.hasTurned);
                    if (other.speed < 0.5 && otherEffState == TrafficLight.State.RED && otherDistToIntersect > 0) {
                        continue; // Xe đang dừng đèn đỏ đúng luật → không cần nhường
                    }
                }

                // So sánh phân nhánh ưu tiên
                boolean iMustYield = false;
                boolean myPri = this.isPriorityVehicle;
                boolean otherPri = other.isPriorityVehicle;

                // Trạng thái đè mặt ngã tư (Giải phóng ngã tư):
                // LUẬT MỚI: Xe ĐÃ VÀO ngã tư (vượt qua vạch dừng) được ưu tiên TUYỆT ĐỐI để dọn đường
                // Xe vừa có đèn xanh PHẢI CHỜ xe vừa dính đèn đỏ đi nốt qua ngã tư.
                boolean iAmClearing = (this.passedStopLine && myDistToIntersect > -CLEARANCE);
                boolean otherIsClearing = (other.passedStopLine && otherDistToIntersect > -CLEARANCE);

                // Ưu tiên hiện trạng trường vật lý:
                // Nếu xe kia đang "dọn đường", ta chưa vào ngã tư thì phải nhường tuyệt đối!
                if (!myPri && otherPri) {
                    iMustYield = true;
                } else if (myPri && !otherPri) {
                    iMustYield = false;
                } else if (!myPri && !otherPri && iAmClearing && otherIsClearing
                        && this.activeIntersectionEntryOrder != Long.MAX_VALUE
                        && other.activeIntersectionEntryOrder != Long.MAX_VALUE) {
                    if (this.activeIntersectionEntryOrder != other.activeIntersectionEntryOrder) {
                        iMustYield = this.activeIntersectionEntryOrder > other.activeIntersectionEntryOrder;
                    } else {
                        iMustYield = (this.id.compareTo(other.id) > 0);
                    }
                } else if (iAmClearing && !otherIsClearing) {
                    iMustYield = false;
                } else if (!iAmClearing && otherIsClearing) {
                    iMustYield = true;
                } else {
                    // Cả 2 cùng chưa vào hoặc cùng vào rồi (hiếm): Đấu độ ưu tiên dựa trên cự ly tiếp cận
                    double myEffective = myDistToIntersect - (myPri ? 120 : 0);
                    double otherEffective = otherDistToIntersect - (otherPri ? 120 : 0);

                    // Đèn xanh ưu tiên qua trước — dùng myEffectiveLight thay vì light.getState()
                    if (myEffectiveLight == TrafficLight.State.GREEN && !otherPri) {
                        myEffective -= 1000;
                    }
                    if (otherLight != null) {
                        TrafficLight.State otherEffLight = otherLight.getStateForTurn(other.turnIntention, other.hasTurned);
                        if (otherEffLight == TrafficLight.State.GREEN && !myPri) {
                            otherEffective -= 1000;
                        }
                    }

                    // Ai còn cách xa (hoặc kém ưu tiên) thì sẽ "tự cảm thấy" cần nhường
                    if (myEffective > otherEffective) {
                        iMustYield = true;
                    } else if (Math.abs(myEffective - otherEffective) < 1.0) {
                        iMustYield = (this.id.compareTo(other.id) > 0);
                    }
                }

                // Tuân lệnh giảm tốc
                if (iMustYield) {
                    // Tránh xe xa tít chân trời cũng phanh, chỉ phanh khi xe khẩn cấp đe doạ tiến vào
                    double yieldDistance = otherPri ? 320.0 : 200.0;
                    if (otherDistToIntersect < yieldDistance) {
                        if (myDistToIntersect < 45) {
                            currentTargetSpeed = Math.min(currentTargetSpeed,
                                    baseSpeed * (otherPri ? 0.2 : CLEARING_MIN_SPEED_FACTOR));
                            shouldStop = true; // Chạm chân đến ngã tư thì lết bánh hẳn
                        } else {
                            // "vẫn có thể di chuyển nhưng với tốc độ an toàn và mức khoảng cách hợp lý"
                            double ratio = (myDistToIntersect - 45) / (otherPri ? 160.0 : 100.0);
                            currentTargetSpeed = Math.min(currentTargetSpeed, baseSpeed * Math.max(0, ratio));
                        }
                    }
                }
            }
        }

        // BƯỚC 5: Giữ khoảng cách
        for (Vehicle other : allVehicles) {
            if (other == this) continue;

            // Xử lý L-shaped following: Hai xe cùng nguồn, cùng hướng rẽ, nhưng xe kia đã rẽ
            if (bypassingTurningBlocker && OvertakeManager.isActiveTurningBypassBlocker(this, other, targetInter, lightIdx)) {
                continue;
            }
            boolean isLShapedFollow = false;
            if (this.originalLightIdx == other.originalLightIdx && this.turnIntention == other.turnIntention && this.turnIntention != 0) {
                if (!this.hasTurned
                        && (other.hasTurned || other.isTurningSmoothly || other.isTurningDiagonally)
                        && isRelevantToCurrentIntersectionTurnFlow(other, intersections, targetInter)) {
                    isLShapedFollow = true;
                }
            }

            int otherLightIdx = getLightIdx(other.direction);
            boolean sameAxis = (lightIdx < 2 && otherLightIdx < 2) || (lightIdx >= 2 && otherLightIdx >= 2);

            if (!sameAxis && !isLShapedFollow) continue;

            // sameLane threshold mở rộng theo kích thước xe — tránh miss khi xe lớn hoặc flee
            double laneThreshold = Math.max(16, (this.height + other.height) / 2.0);
            boolean sameLane = true;
            if (sameAxis) {
                sameLane = (lightIdx < 2)
                        ? Math.abs(other.y - this.y) < laneThreshold
                        : Math.abs(other.x - this.x) < laneThreshold;
            }

            // Xử lý collision khi cả 2 xe cùng đang đi trên đường chéo
            if (this.isTurningDiagonally && other.isTurningDiagonally && this.originalLightIdx == other.originalLightIdx) {
                sameAxis = true;
                sameLane = true;
            }
            if (!sameLane) continue;

            double gap = Double.MAX_VALUE;
            double myHL = this.getHalfLength();
            double otherHL = other.getHalfLength();

            if (isLShapedFollow) {
                // L-shape gap = khoảng cách của tôi đến điểm rẽ + khoảng cách của xe kia tính từ điểm rẽ
                // Điểm rẽ của cả 2 xe là như nhau!
                double myDistToTurn = 0;
                double otherDistFromTurn = 0;
                double targetCoord = 0;

                if (turnIntention == 1) { // Left
                    if (originalLightIdx == 0) targetCoord = cx + 15;
                    else if (originalLightIdx == 1) targetCoord = cx - 15;
                    else if (originalLightIdx == 2) targetCoord = cy + 15;
                    else if (originalLightIdx == 3) targetCoord = cy - 15;
                } else if (turnIntention == 2) { // Right
                    if (originalLightIdx == 0) targetCoord = cx - 65;
                    else if (originalLightIdx == 1) targetCoord = cx + 65;
                    else if (originalLightIdx == 2) targetCoord = cy - 65;
                    else if (originalLightIdx == 3) targetCoord = cy + 65;
                }

                // My distance TO turn point (chưa rẽ nên myDistToTurn phải > 0)
                if (originalLightIdx == 0) myDistToTurn = targetCoord - (this.x + myHL);
                else if (originalLightIdx == 1) myDistToTurn = (this.x - myHL) - targetCoord;
                else if (originalLightIdx == 2) myDistToTurn = targetCoord - (this.y + myHL);
                else if (originalLightIdx == 3) myDistToTurn = (this.y - myHL) - targetCoord;

                // Other distance FROM turn point (đã rẽ nên dist phải > 0)
                // Lấy tọa độ xuất phát trên trục mới của xe đã rẽ
                double otherOriginPathCoord = 0;
                if (originalLightIdx == 0) otherOriginPathCoord = cy + (turnIntention == 1 ? 15 : 65);
                else if (originalLightIdx == 1) otherOriginPathCoord = cy - (turnIntention == 1 ? 15 : 65);
                else if (originalLightIdx == 2) otherOriginPathCoord = cx - (turnIntention == 1 ? 15 : 65);
                else if (originalLightIdx == 3) otherOriginPathCoord = cx + (turnIntention == 1 ? 15 : 65);

                if (other.isTurningSmoothly || other.isTurningDiagonally) {
                    otherDistFromTurn = 0.0;
                } else if (otherLightIdx == 0) otherDistFromTurn = (other.x - otherHL) - otherOriginPathCoord;
                else if (otherLightIdx == 1) otherDistFromTurn = otherOriginPathCoord - (other.x + otherHL);
                else if (otherLightIdx == 2) otherDistFromTurn = (other.y - otherHL) - otherOriginPathCoord;
                else if (otherLightIdx == 3) otherDistFromTurn = otherOriginPathCoord - (other.y + otherHL);

                if (myDistToTurn >= -this.getHalfLength() && otherDistFromTurn >= 0) {
                    gap = myDistToTurn + otherDistFromTurn;
                }

            } else {
                // Straight follow
                if (lightIdx == 0 && other.x > x)
                    gap = (other.x - otherHL)  - (x + myHL);
                else if (lightIdx == 1 && other.x < x)
                    gap = (x - myHL) - (other.x + otherHL);
                else if (lightIdx == 2 && other.y > y)
                    gap = (other.y - otherHL) - (y + myHL);
                else if (lightIdx == 3 && other.y < y)
                    gap = (y - myHL) - (other.y + otherHL);
            }

            if (gap < safeDistance) {
                double minGap = 8.0;
                if (gap <= minGap) {
                    if (forcingTurnExit && gap > 0.0) {
                        double dtSafe = Math.max(0.016, dt);
                        double crawlByGap = gap / dtSafe * 0.45;
                        double crawlSpeed = Math.min(baseSpeed * TURN_EXIT_MIN_SPEED_FACTOR,
                                Math.max(baseSpeed * TURN_EXIT_MERGE_CRAWL_MIN_SPEED_FACTOR, crawlByGap));
                        currentTargetSpeed = Math.min(currentTargetSpeed, crawlSpeed);
                        turnExitMergeCrawl = true;
                    } else {
                        shouldStop = true;
                        hardSameLaneBlockAhead = true;
                    }
                } else {
                    double ratio = (gap - minGap) / (safeDistance - minGap);
                    ratio = Math.max(0, Math.min(1, ratio));
                    double followSpeed = other.speed * ratio;
                    if (forcingTurnExit) {
                        followSpeed = Math.max(followSpeed, baseSpeed * TURN_EXIT_MIN_SPEED_FACTOR);
                    }
                    currentTargetSpeed = Math.min(currentTargetSpeed, followSpeed);
                }
            }
        }

        // BƯỚC 6: Áp tốc độ vật lý
        boolean inIntersection = Math.hypot(x - cx, y - cy) < INTERSECTION_CLEAR_RADIUS;
        boolean clearingIntersection = passedStopLine && inIntersection;
        if (clearingIntersection && shouldStop && !(forcingTurnExit && hardSameLaneBlockAhead)) {
            shouldStop = false;
            currentTargetSpeed = Math.max(currentTargetSpeed, baseSpeed * CLEARING_MIN_SPEED_FACTOR);
        }

        if (shouldStop) {
            this.speed = 0;
        } else {
            boolean isClear = (Math.abs(currentTargetSpeed - baseSpeed) < 1.0);

            if (isPriorityVehicle) {
                // Tăng bứt tốc ngã tư (Intersection Clear Burst)
                if (inIntersection && isClear) {
                    // Không có chướng ngại vật -> Xe khẩn cấp rít ga phóng 1.5x tốc độ qua ngã tư
                    this.speed = currentTargetSpeed * 1.5;
                } else if (inIntersection && !isClear) {
                    // Đang vướng xe phải nhường -> Chay chuẩn theo biểu đồ rà phanh
                    this.speed = currentTargetSpeed;
                } else {
                    // Trên đường thẳng ngoài ngã tư -> Duy trì tốc độ tuần tra 1.3x
                    this.speed = currentTargetSpeed * 1.3;
                }
            } else if (isFleeing) {
                // Xe dân sự đang hoảng loạn lách đường, vọt lẹ hơn tí nếu trống
                this.speed = isClear ? currentTargetSpeed * 1.2 : currentTargetSpeed;
            } else {
                // Xe dân sự đang đèn xanh đi qua ngã tư thì tăng tốc để thoát nhanh, tránh bị đì
                if (inIntersection && isClear && light.getState() == TrafficLight.State.GREEN) {
                    this.speed = currentTargetSpeed * 1.4; // Tăng 40% tốc độ
                } else if (inIntersection && isClear && passedStopLine) {
                    this.speed = currentTargetSpeed * 1.2; // Lỡ dở đèn vàng thì rít nhanh cho qua
                } else {
                    this.speed = currentTargetSpeed;
                }
            }
            if (!forcingTurnExit) {
                this.speed = IntersectionNavigator.limitSpeedForPredictedIntersectionCollision(this, dt, this.speed, allVehicles, targetInter);
            } else if (turnExitMergeCrawl) {
                this.speed = Math.max(this.speed, baseSpeed * TURN_EXIT_MERGE_CRAWL_MIN_SPEED_FACTOR);
            } else {
                this.speed = Math.max(this.speed, baseSpeed * TURN_EXIT_MIN_SPEED_FACTOR);
            }
            movePhysically(dt);
            TrajectoryController.finishDiagonalRightTurnIfNeeded(this, cx, cy);
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

    double roadCenterOffsetLimit() {
        return Math.max(0.0, ROAD_HALF_WIDTH - height / 2.0);
    }

    boolean isSameApproachToIntersection(Vehicle other, List<Intersection> intersections,
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

    boolean isRelevantToCurrentIntersectionTurnFlow(Vehicle other,
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

    double standardLaneOffset(Intersection intersection, int lightIdx, double px, double py) {
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

    int nearestStandardLaneIndex(double offset) {
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

    double longitudinalDistanceAhead(int lightIdx, double otherX, double otherY) {
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

    void moveTowardStandardLane(Intersection intersection, int lightIdx, double offset,
            double dt, double lateralSpeed) {
        double target = standardLaneCoordinate(intersection, lightIdx, offset);
        double maxStep = Math.max(0.0, lateralSpeed * dt);
        if (lightIdx < 2) {
            y = moveToward(y, target, maxStep);
        } else {
            x = moveToward(x, target, maxStep);
        }
    }

    double standardLaneCoordinate(Intersection intersection, int lightIdx, double offset) {
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

    double moveToward(double current, double target, double maxStep) {
        if (Math.abs(target - current) <= maxStep) {
            return target;
        }
        return current + Math.signum(target - current) * maxStep;
    }

    void beginIntersectionIfNeeded(Intersection intersection) {
        String id = intersection.getId();
        if (id.equals(activeIntersectionId)) {
            return;
        }

        activeIntersectionId = id;
        activeIntersectionEntryLightIdx = getLightIdx(direction);
        hasTurned = false;
        passedStopLine = false;
        activeIntersectionEntryOrder = Long.MAX_VALUE;
        OvertakeManager.resetTurningBypassState(this);
    }

    void markIntersectionEntryIfNeeded(boolean entered) {
        if (entered && activeIntersectionEntryOrder == Long.MAX_VALUE) {
            activeIntersectionEntryOrder = nextIntersectionEntryOrder();
        }
    }

    private static synchronized long nextIntersectionEntryOrder() {
        return ++intersectionEntryCounter;
    }

    boolean isPriorityApproachingSameIntersection(Vehicle priorityVehicle,
            Intersection intersection, List<Intersection> intersections) {
        if (priorityVehicle == this || !priorityVehicle.isPriorityVehicle) {
            return false;
        }
        if (Math.hypot(priorityVehicle.x - intersection.getX(), priorityVehicle.y - intersection.getY()) < 360.0) {
            return true;
        }
        return priorityVehicle.getTargetIntersection(intersections) == intersection;
    }

    boolean isLaneSafeForChange(List<Vehicle> allVehicles, List<Intersection> intersections,
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

            double otherOffset = Math.abs(standardLaneOffset(intersection, lightIdx, other.x, other.y));
            if (Math.abs(otherOffset - targetOffset) > 14.0) {
                continue;
            }

            double otherLongitudinal = longitudinalCoordinate(lightIdx, other.x, other.y);
            double relative = signedLongitudinalDelta(lightIdx, myLongitudinal, otherLongitudinal);
            double requiredGap = Math.max(52.0, getHalfLength() + other.getHalfLength() + 24.0);
            if (other.isPriorityVehicle) {
                requiredGap += 90.0;
            }
            if (relative > -requiredGap && relative < requiredGap * 1.35) {
                return false;
            }

            double lateralDistance = lightIdx < 2
                    ? Math.abs(other.y - myProjectedLaneCoord)
                    : Math.abs(other.x - myProjectedLaneCoord);
            if (Math.abs(relative) < requiredGap * 1.8 && lateralDistance < Math.max(18.0, height + other.height)) {
                return false;
            }
        }
        return true;
    }

    int countVehiclesInLaneWindow(List<Vehicle> allVehicles, List<Intersection> intersections,
            Intersection intersection, int lightIdx, double targetOffset) {
        int count = 0;
        for (Vehicle other : allVehicles) {
            if (other == this || !isSameApproachToIntersection(other, intersections, intersection, lightIdx)) {
                continue;
            }
            double offset = Math.abs(standardLaneOffset(intersection, lightIdx, other.x, other.y));
            if (Math.abs(offset - targetOffset) > 14.0) {
                continue;
            }
            double ahead = longitudinalDistanceAhead(lightIdx, other.x, other.y);
            if (ahead > -80.0 && ahead < 220.0) {
                count++;
            }
        }
        return count;
    }

    double longitudinalCoordinate(int lightIdx, double px, double py) {
        return lightIdx < 2 ? px : py;
    }

    double signedLongitudinalDelta(int lightIdx, double myLongitudinal, double otherLongitudinal) {
        if (lightIdx == 0 || lightIdx == 2) {
            return otherLongitudinal - myLongitudinal;
        }
        return myLongitudinal - otherLongitudinal;
    }

    TrafficLight.State effectiveLightForVehicle(Vehicle vehicle, List<Intersection> intersections,
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
        return targetLight.getStateForTurn(vehicle.turnIntention, vehicle.hasTurned);
    }

    boolean isInsideStandardIntersectionGuardZone(Intersection intersection,
            double currentX, double currentY, double nextX, double nextY) {
        double guardRadius = INTERSECTION_CLEAR_RADIUS + 55.0;
        return Math.hypot(currentX - intersection.getX(), currentY - intersection.getY()) < guardRadius
                || Math.hypot(nextX - intersection.getX(), nextY - intersection.getY()) < guardRadius;
    }

    boolean isInsideStandardIntersection(Intersection intersection, double px, double py) {
        return Math.hypot(px - intersection.getX(), py - intersection.getY()) < INTERSECTION_CLEAR_RADIUS;
    }

    double segmentDistance(double ax, double ay, double bx, double by,
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

    double pointToSegmentDistance(double px, double py, double ax, double ay, double bx, double by) {
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

    boolean segmentsIntersect(double ax, double ay, double bx, double by,
            double cx, double cy, double dx, double dy) {
        double o1 = orientation(ax, ay, bx, by, cx, cy);
        double o2 = orientation(ax, ay, bx, by, dx, dy);
        double o3 = orientation(cx, cy, dx, dy, ax, ay);
        double o4 = orientation(cx, cy, dx, dy, bx, by);
        return o1 * o2 < 0.0 && o3 * o4 < 0.0;
    }

    double orientation(double ax, double ay, double bx, double by, double cx, double cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    int getIntersectionEntryLightIdx() {
        return activeIntersectionEntryLightIdx >= 0 ? activeIntersectionEntryLightIdx : originalLightIdx;
    }

    boolean isAlignedWithIntersectionRoad(Intersection inter, int lightIdx) {
        double lateralLimit = ROAD_HALF_WIDTH + width / 2.0;
        if (lightIdx < 2) {
            return Math.abs(y - inter.getY()) <= lateralLimit;
        }
        return Math.abs(x - inter.getX()) <= lateralLimit;
    }

    boolean hasRoundaboutExitSmoothPriorityOver(Vehicle other) {
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

    void startSmoothTurn(double targetX, double targetY, double targetDirection,
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

    double blendAlpha(double dt, double rate) {
        return Math.max(0.0, Math.min(1.0, 1.0 - Math.exp(-Math.max(0.0, dt) * rate)));
    }

    double interpolateAngle(double fromAngle, double toAngle, double t) {
        return normalizeAngle(fromAngle + normalizeAngle(toAngle - fromAngle) * t);
    }

    double smoothStep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    static double normalizeAngle(double angle) {
        while (angle <= -Math.PI) angle += 2 * Math.PI;
        while (angle > Math.PI) angle -= 2 * Math.PI;
        return angle;
    }

    static double normalizePositiveAngle(double angle) {
        while (angle < 0) angle += 2 * Math.PI;
        while (angle >= 2 * Math.PI) angle -= 2 * Math.PI;
        return angle;
    }

    static double counterClockwiseDistance(double fromAngle, double toAngle) {
        return normalizePositiveAngle(fromAngle - toAngle);
    }
}
