package vn.edu.hust.traffic.behavior;

import vn.edu.hust.traffic.model.vehicle.Vehicle;
import vn.edu.hust.traffic.model.map.Intersection;
import vn.edu.hust.traffic.model.map.RoundaboutIntersection;
import vn.edu.hust.traffic.model.map.TrafficLight;
import java.util.List;

/**
 * Hành vi lái xe mặc định của phương tiện trong thành phố thông minh.
 */
public class NormalDriver implements DrivingStrategy {

    @Override
    public void update(Vehicle vehicle, double dt, List<Vehicle> allVehicles, List<Intersection> intersections, int screenWidth, int screenHeight) {
        vehicle.lastX = vehicle.x;
        vehicle.lastY = vehicle.y;
        double safeDistance = (vehicle.width > 30) ? 50 : 30;
        final double SLOW_ZONE = 80.0;
        boolean shouldStop = false;
        boolean hardSameLaneBlockAhead = false;
        boolean turnExitMergeCrawl = false;
        double currentTargetSpeed = vehicle.baseSpeed;
        boolean forcingTurnExit = vehicle.hasTurned() && vehicle.turnExitClearanceTime > 0.0;
        vehicle.turnExitClearanceTime = Math.max(0.0, vehicle.turnExitClearanceTime - Math.max(0.0, dt));

        if (TrajectoryController.continueSmoothTurn(vehicle, dt, allVehicles, intersections)) {
            return;
        }

        if (TrajectoryController.continueDiagonalRightTurn(vehicle, dt, allVehicles)) {
            return;
        }

        Intersection targetInter = vehicle.getTargetIntersection(intersections);
        if (targetInter == null) {
            vehicle.activeIntersectionId = null;
            vehicle.activeIntersectionEntryLightIdx = -1;
            vehicle.activeIntersectionEntryOrder = Long.MAX_VALUE;
            OvertakeManager.resetOvertakeState(vehicle);
            OvertakeManager.resetYieldState(vehicle);
            OvertakeManager.resetTurningBypassState(vehicle);
            vehicle.speed = vehicle.baseSpeed;
            vehicle.movePhysically(dt);
            if (vehicle.isTurningDiagonally) {
                TrajectoryController.finishDiagonalRightTurnIfNeeded(vehicle, vehicle.diagonalTurnCenterX, vehicle.diagonalTurnCenterY);
            }
            return;
        }

        if (targetInter instanceof RoundaboutIntersection) {
            OvertakeManager.resetOvertakeState(vehicle);
            OvertakeManager.resetYieldState(vehicle);
            OvertakeManager.resetTurningBypassState(vehicle);
            RoundaboutNavigator.updateRoundabout(vehicle, dt, allVehicles, (RoundaboutIntersection) targetInter);
            return;
        }

        vehicle.beginIntersectionIfNeeded(targetInter);
        double cx = targetInter.getX();
        double cy = targetInter.getY();
        int lightIdx = vehicle.getLightIdx(vehicle.direction);
        int entryLightIdx = vehicle.getIntersectionEntryLightIdx();

        // BẢO VỆ NGÃ 3: RTL không rẽ trái xuống Nam, LTR không rẽ phải xuống Nam, TTB không đi thẳng xuống Nam
        if (targetInter instanceof vn.edu.hust.traffic.model.map.ThreeWayIntersection
                && !vehicle.hasTurned() && !vehicle.isTurningDiagonally) {
            if (entryLightIdx == 0 && vehicle.getTurnIntention() == 2) { // LTR (đi Đông) không thể rẽ phải (Nam)
                vehicle.setTurnIntention(Math.random() < 0.5 ? 0 : 1);
            } else if (entryLightIdx == 1 && vehicle.getTurnIntention() == 1) { // RTL (đi Tây) không thể rẽ trái (Nam)
                vehicle.setTurnIntention(Math.random() < 0.5 ? 0 : 2);
            } else if (entryLightIdx == 2 && vehicle.getTurnIntention() == 0) { // TTB (đi Nam) không thể đi thẳng (Nam)
                vehicle.setTurnIntention(Math.random() < 0.5 ? 1 : 2);
            }
        }

        TrafficLight light = null;
        if (targetInter instanceof vn.edu.hust.traffic.model.map.CrossIntersection) {
            light = targetInter.getLights().get(lightIdx);
        } else if (targetInter instanceof vn.edu.hust.traffic.model.map.ThreeWayIntersection
                && !vehicle.hasTurned() && !vehicle.isTurningDiagonally) {
            light = ((vn.edu.hust.traffic.model.map.ThreeWayIntersection)targetInter).getLightForDirection(vehicle.direction);
        }
        if (light == null) {
            vehicle.speed = vehicle.baseSpeed;
            vehicle.movePhysically(dt);
            return;
        }

        double stopX_LTR = cx - 120;
        double stopX_RTL = cx + 120;
        double stopY_TTB = cy - 120;
        double stopY_BTT = cy + 120;

        vehicle.distToStopLine = Double.MAX_VALUE;
        double hl = vehicle.width / 2.0; // getHalfLength()

        switch (lightIdx) {
            case 0:
                vehicle.distToStopLine = stopX_LTR - (vehicle.x + hl);
                vehicle.passedStopLine = (vehicle.x + hl) >= stopX_LTR;
                break;
            case 1:
                vehicle.distToStopLine = (vehicle.x - hl) - stopX_RTL;
                vehicle.passedStopLine = (vehicle.x - hl) <= stopX_RTL;
                break;
            case 2:
                vehicle.distToStopLine = stopY_TTB - (vehicle.y + hl);
                vehicle.passedStopLine = (vehicle.y + hl) >= stopY_TTB;
                break;
            case 3:
                vehicle.distToStopLine = (vehicle.y - hl) - stopY_BTT;
                vehicle.passedStopLine = (vehicle.y - hl) <= stopY_BTT;
                break;
        }
        if (forcingTurnExit) {
            vehicle.passedStopLine = true;
            vehicle.distToStopLine = -1.0;
        }
        vehicle.markIntersectionEntryIfNeeded(vehicle.passedStopLine);

        // BƯỚC 0.5: Kiểm tra và thực hiện rẽ nếu xe đang ở giữa ngã tư

        // THÊM MỚI: QUỸ ĐẠO RẼ PHẢI CHÉO GÓC (VÀO ĐƯỜNG RẼ TẮT)
        if (!vehicle.hasTurned() && vehicle.getTurnIntention() == 2) {
            double TURN_DIST = 233.0;
            double END_LANE = 65.0;

            if (!vehicle.isTurningDiagonally) {
                boolean readyToDiagonal = false;
                if (entryLightIdx == 0) readyToDiagonal = (vehicle.x >= cx - TURN_DIST);
                else if (entryLightIdx == 1) readyToDiagonal = (vehicle.x <= cx + TURN_DIST);
                else if (entryLightIdx == 2) readyToDiagonal = (vehicle.y >= cy - TURN_DIST);
                else if (entryLightIdx == 3) readyToDiagonal = (vehicle.y <= cy + TURN_DIST);

                if (readyToDiagonal && IntersectionNavigator.hasBlockedDiagonalRightTurnEntry(
                        vehicle, allVehicles, intersections, targetInter, entryLightIdx)) {
                    vehicle.speed = 0.0;
                    return;
                }

                if (readyToDiagonal) {
                    if (!vehicle.isPriorityVehicle() && IntersectionNavigator.hasUnsafeIntersectionEntryConflict(vehicle, allVehicles, intersections,
                            targetInter, entryLightIdx, TrafficLight.State.GREEN)) {
                        vehicle.speed = 0.0;
                        return;
                    }
                    vehicle.isTurningDiagonally = true;
                    vehicle.diagonalTurnCenterX = cx;
                    vehicle.diagonalTurnCenterY = cy;
                    vehicle.passedStopLine = true; // Bỏ qua đèn đỏ vì làn rẽ phải luôn thông
                    vehicle.markIntersectionEntryIfNeeded(true);
                    double targetX = vehicle.x;
                    double targetY = vehicle.y;
                    double targetDirection = vehicle.direction;
                    double advance = Math.min(32.0, Math.max(14.0, vehicle.baseSpeed * 0.22));
                    if (entryLightIdx == 0) {
                        targetX = Math.max(vehicle.x, cx - TURN_DIST) + Math.cos(Math.PI / 4) * advance;
                        targetY = vehicle.y + Math.sin(Math.PI / 4) * advance;
                        targetDirection = Math.PI / 4;
                    } else if (entryLightIdx == 1) {
                        targetX = Math.min(vehicle.x, cx + TURN_DIST) + Math.cos(-Math.PI * 3 / 4) * advance;
                        targetY = vehicle.y + Math.sin(-Math.PI * 3 / 4) * advance;
                        targetDirection = -Math.PI * 3 / 4;
                    } else if (entryLightIdx == 2) {
                        targetX = vehicle.x + Math.cos(Math.PI * 3 / 4) * advance;
                        targetY = Math.max(vehicle.y, cy - TURN_DIST) + Math.sin(Math.PI * 3 / 4) * advance;
                        targetDirection = Math.PI * 3 / 4;
                    } else if (entryLightIdx == 3) {
                        targetX = vehicle.x + Math.cos(-Math.PI / 4) * advance;
                        targetY = Math.min(vehicle.y, cy + TURN_DIST) + Math.sin(-Math.PI / 4) * advance;
                        targetDirection = -Math.PI / 4;
                    }
                    vehicle.startSmoothTurn(targetX, targetY, targetDirection, false, 0.22);
                    return;
                }
            }

            if (vehicle.isTurningDiagonally) {
                boolean endDiagonal = false;
                if (entryLightIdx == 0) endDiagonal = (vehicle.x >= cx - END_LANE);
                else if (entryLightIdx == 1) endDiagonal = (vehicle.x <= cx + END_LANE);
                else if (entryLightIdx == 2) endDiagonal = (vehicle.y >= cy - END_LANE);
                else if (entryLightIdx == 3) endDiagonal = (vehicle.y <= cy + END_LANE);

                if (endDiagonal) {
                    TrajectoryController.finishDiagonalRightTurnIfNeeded(vehicle, cx, cy);
                    if (vehicle.isTurningSmoothly) {
                        return;
                    }

                    lightIdx = vehicle.getLightIdx(vehicle.direction); // Cập nhật lại tín hiệu đèn sau khi nắn thẳng trục
                }
            }
        }

        // RẼ TRÁI Ở GIỮA NGÃ TƯ
        if (!vehicle.hasTurned() && vehicle.getTurnIntention() == 1 && vehicle.passedStopLine) {
            boolean readyToTurn = false;
            double targetCoord = 0;

            // Tính toán tọa độ chính xác để sau khi bẻ lái, xe nằm đúng boong giữa làn
            if (vehicle.getTurnIntention() == 1) { // Rẽ trái (vào làn priority offset 15)
                if (entryLightIdx == 0) { targetCoord = cx + 15; readyToTurn = (vehicle.x >= targetCoord); }
                else if (entryLightIdx == 1) { targetCoord = cx - 15; readyToTurn = (vehicle.x <= targetCoord); }
                else if (entryLightIdx == 2) { targetCoord = cy + 15; readyToTurn = (vehicle.y >= targetCoord); }
                else if (entryLightIdx == 3) { targetCoord = cy - 15; readyToTurn = (vehicle.y <= targetCoord); }
            }

            if (readyToTurn) {
                // Chỉnh thẳng góc tọa độ trục cũ vào đúng quỹ đạo trục mới
                double targetX = vehicle.x;
                double targetY = vehicle.y;
                double targetDirection = vehicle.direction;
                if (entryLightIdx == 0 || entryLightIdx == 1) targetX = targetCoord;
                else targetY = targetCoord;

                if (vehicle.getTurnIntention() == 1) { // Rẽ trái
                    if (entryLightIdx == 0) targetDirection = -Math.PI/2;
                    else if (entryLightIdx == 1) targetDirection = Math.PI/2;
                    else if (entryLightIdx == 2) targetDirection = 0;
                    else if (entryLightIdx == 3) targetDirection = Math.PI;
                }
                vehicle.startSmoothTurn(targetX, targetY, targetDirection, true, Vehicle.SMOOTH_TURN_DURATION);
                return;
            }
        }

        OvertakeManager.movePriorityToLeastBusyLaneIfRedQueueAhead(vehicle, allVehicles, intersections, targetInter, lightIdx, dt);

        // BƯỚC 1: Quét tìm cứu thương khẩn cấp (Emergency Ambulance) để tiến hành Flee Mode
        boolean isFleeing = false;
        boolean yieldingThisUpdate = false;

        for (Vehicle other : allVehicles) {
            if (other.isPriorityVehicle() && other != vehicle) {
                if (!vehicle.isPriorityVehicle()
                        && !vehicle.passedStopLine
                        && vehicle.isPriorityApproachingSameIntersection(other, targetInter, intersections)) {
                    if (vehicle.distToStopLine <= 45.0) {
                        shouldStop = true;
                    } else if (vehicle.distToStopLine < 160.0) {
                        currentTargetSpeed = Math.min(currentTargetSpeed,
                                vehicle.baseSpeed * Math.max(0.15, vehicle.distToStopLine / 160.0));
                    }
                }

                int otherLightIdx = other.getLightIdx(other.direction);

                // 1. Nhường đường nếu xe ưu tiên đang áp sát phía sau trên cùng hướng tiếp cận.
                if (!vehicle.isPriorityVehicle()
                        && !vehicle.passedStopLine
                        && otherLightIdx == lightIdx
                        && vehicle.isSameApproachToIntersection(other, intersections, targetInter, lightIdx)) {
                    double behindDist = -vehicle.longitudinalDistanceAhead(lightIdx, other.x, other.y);
                    if (behindDist > 0.0 && behindDist < 420.0
                            && (OvertakeManager.isContinuingYieldForPriority(vehicle, targetInter, lightIdx, other)
                                    || OvertakeManager.isBlockingPriorityLane(vehicle, targetInter, lightIdx, other))) {
                        isFleeing = true;
                        yieldingThisUpdate = true;
                        shouldStop = false;
                        currentTargetSpeed = Math.max(currentTargetSpeed, vehicle.baseSpeed * 0.65);

                        double targetOffset = OvertakeManager.stableYieldLaneOffset(vehicle, allVehicles, intersections, targetInter,
                                lightIdx, other);
                        vehicle.moveTowardStandardLane(targetInter, lightIdx, targetOffset, dt, Vehicle.YIELD_LANE_CHANGE_SPEED);
                    }
                }
            }
        }
        if (!yieldingThisUpdate && OvertakeManager.continueYieldLaneChangeToTargetIfNeeded(vehicle,
                allVehicles, intersections, targetInter, lightIdx, dt)) {
            isFleeing = true;
            yieldingThisUpdate = true;
            shouldStop = false;
            currentTargetSpeed = Math.max(currentTargetSpeed, vehicle.baseSpeed * 0.65);
        }
        if (!yieldingThisUpdate) {
            OvertakeManager.resetYieldState(vehicle);
        }

        // BƯỚC 2: Check đèn — dùng đèn PHÙ HỢP với ý định rẽ của xe
        //   turnIntention==0 (thẳng): xem đèn thẳng
        //   turnIntention==1 (rẽ trái): xem đèn mũi tên rẽ trái
        //   turnIntention==2 (rẽ phải): luôn GREEN (Right Turn on Red)
        TrafficLight.State myEffectiveLight = light.getStateForTurn(vehicle.getTurnIntention(), vehicle.hasTurned());
        boolean isRightTurnOnRed = (vehicle.getTurnIntention() == 2 && !vehicle.hasTurned());
        boolean mustStopByLight = false;
        if (!vehicle.isPriorityVehicle() && shouldObeyLights(vehicle) && !isFleeing && !forcingTurnExit) {
            if (myEffectiveLight == TrafficLight.State.RED ||
                (myEffectiveLight == TrafficLight.State.YELLOW && !vehicle.passedStopLine)) {
                mustStopByLight = true;
            }
        }

        // BƯỚC 3: Dừng mềm trước vạch theo đèn tín hiệu
        if (mustStopByLight && !vehicle.passedStopLine) {
            if (vehicle.distToStopLine <= 0) {
                shouldStop = true;
            } else if (vehicle.distToStopLine < SLOW_ZONE) {
                double ratio = vehicle.distToStopLine / SLOW_ZONE;
                currentTargetSpeed = vehicle.baseSpeed * ratio;
                if (vehicle.distToStopLine < 5) shouldStop = true;
            }
        }

        // BƯỚC 3.5: Xe rẽ phải khi đèn đỏ — giảm tốc cẩn thận trước ngã tư, không dừng hẳn
        if (isRightTurnOnRed && !vehicle.passedStopLine && light.getState() != TrafficLight.State.GREEN) {
            double cautionSpeed = vehicle.baseSpeed * 0.4;
            if (vehicle.distToStopLine < SLOW_ZONE && vehicle.distToStopLine > 0) {
                double ratio = vehicle.distToStopLine / SLOW_ZONE;
                currentTargetSpeed = Math.min(currentTargetSpeed, cautionSpeed * ratio + cautionSpeed * 0.3);
            } else if (vehicle.distToStopLine <= 0) {
                currentTargetSpeed = Math.min(currentTargetSpeed, cautionSpeed);
            }
        }

        boolean unsafeEntryConflict = !forcingTurnExit
                && !vehicle.passedStopLine
                && IntersectionNavigator.hasUnsafeIntersectionEntryConflict(vehicle, allVehicles, intersections, targetInter, lightIdx,
                        myEffectiveLight);
        if (unsafeEntryConflict) {
            double ratio = Math.max(0.0, (vehicle.distToStopLine - 8.0) / Vehicle.INTERSECTION_ENTRY_GUARD_DISTANCE);
            currentTargetSpeed = Math.min(currentTargetSpeed, vehicle.baseSpeed * Math.min(0.45, ratio));
            if (vehicle.distToStopLine <= Vehicle.INTERSECTION_ENTRY_STOP_DISTANCE) {
                shouldStop = true;
            }
        }

        if (OvertakeManager.updateNormalOvertakeIfNeeded(vehicle, allVehicles, intersections, targetInter, lightIdx, dt,
                mustStopByLight, isFleeing)) {
            currentTargetSpeed = Math.max(currentTargetSpeed, vehicle.baseSpeed * 0.95);
        }
        boolean bypassingTurningBlocker = OvertakeManager.updateTurningVehicleBypassIfNeeded(vehicle,
                allVehicles, intersections, targetInter, lightIdx, dt);
        if (bypassingTurningBlocker) {
            shouldStop = false;
            currentTargetSpeed = Math.max(currentTargetSpeed, vehicle.baseSpeed * (vehicle.isPriorityVehicle() ? 0.95 : 0.70));
        }

        // BƯỚC 4: Rà phanh động (Dynamic Right of Way) - Thuật toán giao tuyến quang học
        for (Vehicle other : allVehicles) {
            if (other == vehicle) continue;
            if (forcingTurnExit) continue;
            if (bypassingTurningBlocker && OvertakeManager.isActiveTurningBypassBlocker(vehicle, other, targetInter, lightIdx)) {
                continue;
            }

            int otherLightIdx = other.getLightIdx(other.direction);
            boolean sameAxis = (lightIdx < 2 && otherLightIdx < 2) || (lightIdx >= 2 && otherLightIdx >= 2);

            // Chỉ xét 2 xe có quỹ đạo chéo góc (cross-traffic)
            if (!sameAxis) {
                // Xác định tọa độ giao cắt của 2 quỹ đạo
                double intersectX, intersectY;
                if (lightIdx < 2) {
                    intersectY = vehicle.y;
                    intersectX = other.x;
                } else {
                    intersectX = vehicle.x;
                    intersectY = other.y;
                }

                // Khoảng cách từ mũi xe ĐẾN điểm giao cắt (dương = chưa tới, âm = đi lố qua rồi)
                double myDistToIntersect = 0;
                if (lightIdx == 0) myDistToIntersect = intersectX - vehicle.x;
                else if (lightIdx == 1) myDistToIntersect = vehicle.x - intersectX;
                else if (lightIdx == 2) myDistToIntersect = intersectY - vehicle.y;
                else if (lightIdx == 3) myDistToIntersect = vehicle.y - intersectY;

                double otherDistToIntersect = 0;
                if (otherLightIdx == 0) otherDistToIntersect = intersectX - other.x;
                else if (otherLightIdx == 1) otherDistToIntersect = other.x - intersectX;
                else if (otherLightIdx == 2) otherDistToIntersect = intersectY - other.y;
                else if (otherLightIdx == 3) otherDistToIntersect = other.y - intersectY;

                // Clearance phụ thuộc kích thước xe — xe lớn cần vùng lớn hơn
                double CLEARANCE = Math.max(40.0, Math.max(vehicle.width, other.width) * 0.7);

                // 1. Phá băng giao thông: Ai ĐÃ qua rồi thì thoát ra khỏi vùng ảnh hưởng tuyệt đối!
                if (myDistToIntersect < -CLEARANCE || otherDistToIntersect < -CLEARANCE) {
                    continue;
                }

                // KHÔNG BAO GIỜ xung đột với xe xuất phát từ cùng một nhánh đường
                if (vehicle.originalLightIdx == other.originalLightIdx) {
                    continue;
                }

                // 2. Chống lác (Deadlock anti-freeze): Bỏ qua xe đỗ chờ đèn đỏ
                //    - Xe đứng yên VÀ còn xa ngã tư (>50px) → chắc chắn đang chờ đèn
                //    - Xe đứng yên VÀ đèn của nó đang đỏ → đang tuân thủ đèn, không phải mối đe dọa
                if (other.getSpeed() < 0.5 && otherDistToIntersect > 50) {
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
                    TrafficLight.State otherEffState = otherLight.getStateForTurn(other.getTurnIntention(), other.hasTurned());
                    if (other.getSpeed() < 0.5 && otherEffState == TrafficLight.State.RED && otherDistToIntersect > 0) {
                        continue; // Xe đang dừng đèn đỏ đúng luật → không cần nhường
                    }
                }

                // So sánh phân nhánh ưu tiên
                boolean iMustYield = false;
                boolean myPri = vehicle.isPriorityVehicle();
                boolean otherPri = other.isPriorityVehicle();

                // Trạng thái đè mặt ngã tư (Giải phóng ngã tư):
                // LUẬT MỚI: Xe ĐÃ VÀO ngã tư (vượt qua vạch dừng) được ưu tiên TUYỆT ĐỐI để dọn đường
                // Xe vừa có đèn xanh PHẢI CHỜ xe vừa dính đèn đỏ đi nốt qua ngã tư.
                boolean iAmClearing = (vehicle.passedStopLine && myDistToIntersect > -CLEARANCE);
                boolean otherIsClearing = (other.passedStopLine && otherDistToIntersect > -CLEARANCE);

                // Ưu tiên hiện trạng trường vật lý:
                // Nếu xe kia đang "dọn đường", ta chưa vào ngã tư thì phải nhường tuyệt đối!
                if (!myPri && otherPri) {
                    iMustYield = true;
                } else if (myPri && !otherPri) {
                    iMustYield = false;
                } else if (!myPri && !otherPri && iAmClearing && otherIsClearing
                        && vehicle.activeIntersectionEntryOrder != Long.MAX_VALUE
                        && other.activeIntersectionEntryOrder != Long.MAX_VALUE) {
                    if (vehicle.activeIntersectionEntryOrder != other.activeIntersectionEntryOrder) {
                        iMustYield = vehicle.activeIntersectionEntryOrder > other.activeIntersectionEntryOrder;
                    } else {
                        iMustYield = (vehicle.getId().compareTo(other.getId()) > 0);
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
                        TrafficLight.State otherEffLight = otherLight.getStateForTurn(other.getTurnIntention(), other.hasTurned());
                        if (otherEffLight == TrafficLight.State.GREEN && !myPri) {
                            otherEffective -= 1000;
                        }
                    }

                    // Ai còn cách xa (hoặc kém ưu tiên) thì sẽ "tự cảm thấy" cần nhường
                    if (myEffective > otherEffective) {
                        iMustYield = true;
                    } else if (Math.abs(myEffective - otherEffective) < 1.0) {
                        iMustYield = (vehicle.getId().compareTo(other.getId()) > 0);
                    }
                }

                // Tuân lệnh giảm tốc
                if (iMustYield) {
                    // Tránh xe xa tít chân trời cũng phanh, chỉ phanh khi xe khẩn cấp đe doạ tiến vào
                    double yieldDistance = otherPri ? 320.0 : 200.0;
                    if (otherDistToIntersect < yieldDistance) {
                        if (myDistToIntersect < 45) {
                            currentTargetSpeed = Math.min(currentTargetSpeed,
                                    vehicle.baseSpeed * (otherPri ? 0.2 : Vehicle.CLEARING_MIN_SPEED_FACTOR));
                            shouldStop = true; // Chạm chân đến ngã tư thì lết bánh hẳn
                        } else {
                            // "vẫn có thể di chuyển nhưng với tốc độ an toàn và mức khoảng cách hợp lý"
                            double ratio = (myDistToIntersect - 45) / (otherPri ? 160.0 : 100.0);
                            currentTargetSpeed = Math.min(currentTargetSpeed, vehicle.baseSpeed * Math.max(0, ratio));
                        }
                    }
                }
            }
        }

        // BƯỚC 5: Giữ khoảng cách
        for (Vehicle other : allVehicles) {
            if (other == vehicle) continue;

            // Xử lý L-shaped following: Hai xe cùng nguồn, cùng hướng rẽ, nhưng xe kia đã rẽ
            if (bypassingTurningBlocker && OvertakeManager.isActiveTurningBypassBlocker(vehicle, other, targetInter, lightIdx)) {
                continue;
            }
            boolean isLShapedFollow = false;
            if (vehicle.originalLightIdx == other.originalLightIdx && vehicle.getTurnIntention() == other.getTurnIntention() && vehicle.getTurnIntention() != 0) {
                if (!vehicle.hasTurned()
                        && (other.hasTurned() || other.isTurningSmoothly || other.isTurningDiagonally)
                        && vehicle.isRelevantToCurrentIntersectionTurnFlow(other, intersections, targetInter)) {
                    isLShapedFollow = true;
                }
            }

            int otherLightIdx = other.getLightIdx(other.direction);
            boolean sameAxis = (lightIdx < 2 && otherLightIdx < 2) || (lightIdx >= 2 && otherLightIdx >= 2);

            if (!sameAxis && !isLShapedFollow) continue;

            // sameLane threshold mở rộng theo kích thước xe — tránh miss khi xe lớn hoặc flee
            double laneThreshold = Math.max(16, (vehicle.height + other.height) / 2.0);
            boolean sameLane = true;
            if (sameAxis) {
                sameLane = (lightIdx < 2)
                        ? Math.abs(other.y - vehicle.y) < laneThreshold
                        : Math.abs(other.x - vehicle.x) < laneThreshold;
            }

            // Xử lý collision khi cả 2 xe cùng đang đi trên đường chéo
            if (vehicle.isTurningDiagonally && other.isTurningDiagonally && vehicle.originalLightIdx == other.originalLightIdx) {
                sameAxis = true;
                sameLane = true;
            }
            if (!sameLane) continue;

            double gap = Double.MAX_VALUE;
            double myHL = vehicle.width / 2.0; // getHalfLength()
            double otherHL = other.width / 2.0; // getHalfLength()

            if (isLShapedFollow) {
                // L-shape gap = khoảng cách của tôi đến điểm rẽ + khoảng cách của xe kia tính từ điểm rẽ
                // Điểm rẽ của cả 2 xe là như nhau!
                double myDistToTurn = 0;
                double otherDistFromTurn = 0;
                double targetCoord = 0;

                if (vehicle.getTurnIntention() == 1) { // Left
                    if (vehicle.originalLightIdx == 0) targetCoord = cx + 15;
                    else if (vehicle.originalLightIdx == 1) targetCoord = cx - 15;
                    else if (vehicle.originalLightIdx == 2) targetCoord = cy + 15;
                    else if (vehicle.originalLightIdx == 3) targetCoord = cy - 15;
                } else if (vehicle.getTurnIntention() == 2) { // Right
                    if (vehicle.originalLightIdx == 0) targetCoord = cx - 65;
                    else if (vehicle.originalLightIdx == 1) targetCoord = cx + 65;
                    else if (vehicle.originalLightIdx == 2) targetCoord = cy - 65;
                    else if (vehicle.originalLightIdx == 3) targetCoord = cy + 65;
                }

                // My distance TO turn point (chưa rẽ nên myDistToTurn phải > 0)
                if (vehicle.originalLightIdx == 0) myDistToTurn = targetCoord - (vehicle.x + myHL);
                else if (vehicle.originalLightIdx == 1) myDistToTurn = (vehicle.x - myHL) - targetCoord;
                else if (vehicle.originalLightIdx == 2) myDistToTurn = targetCoord - (vehicle.y + myHL);
                else if (vehicle.originalLightIdx == 3) myDistToTurn = (vehicle.y - myHL) - targetCoord;

                // Other distance FROM turn point (đã rẽ nên dist phải > 0)
                // Lấy tọa độ xuất phát trên trục mới của xe đã rẽ
                double otherOriginPathCoord = 0;
                if (vehicle.originalLightIdx == 0) otherOriginPathCoord = cy + (vehicle.getTurnIntention() == 1 ? 15 : 65);
                else if (vehicle.originalLightIdx == 1) otherOriginPathCoord = cy - (vehicle.getTurnIntention() == 1 ? 15 : 65);
                else if (vehicle.originalLightIdx == 2) otherOriginPathCoord = cx - (vehicle.getTurnIntention() == 1 ? 15 : 65);
                else if (vehicle.originalLightIdx == 3) otherOriginPathCoord = cx + (vehicle.getTurnIntention() == 1 ? 15 : 65);

                if (other.isTurningSmoothly || other.isTurningDiagonally) {
                    otherDistFromTurn = 0.0;
                } else if (otherLightIdx == 0) otherDistFromTurn = (other.x - otherHL) - otherOriginPathCoord;
                else if (otherLightIdx == 1) otherDistFromTurn = otherOriginPathCoord - (other.x + otherHL);
                else if (otherLightIdx == 2) otherDistFromTurn = (other.y - otherHL) - otherOriginPathCoord;
                else if (otherLightIdx == 3) otherDistFromTurn = otherOriginPathCoord - (other.y + otherHL);

                if (myDistToTurn >= -myHL && otherDistFromTurn >= 0) {
                    gap = myDistToTurn + otherDistFromTurn;
                }

            } else {
                // Straight follow
                if (lightIdx == 0 && other.x > vehicle.x)
                    gap = (other.x - otherHL)  - (vehicle.x + myHL);
                else if (lightIdx == 1 && other.x < vehicle.x)
                    gap = (vehicle.x - myHL) - (other.x + otherHL);
                else if (lightIdx == 2 && other.y > vehicle.y)
                    gap = (other.y - otherHL) - (vehicle.y + myHL);
                else if (lightIdx == 3 && other.y < vehicle.y)
                    gap = (vehicle.y - myHL) - (other.y + otherHL);
            }

            if (gap < safeDistance) {
                double minGap = 8.0;
                if (gap <= minGap) {
                    if (forcingTurnExit && gap > 0.0) {
                        double dtSafe = Math.max(0.016, dt);
                        double crawlByGap = gap / dtSafe * 0.45;
                        double crawlSpeed = Math.min(vehicle.baseSpeed * Vehicle.TURN_EXIT_MIN_SPEED_FACTOR,
                                Math.max(vehicle.baseSpeed * Vehicle.TURN_EXIT_MERGE_CRAWL_MIN_SPEED_FACTOR, crawlByGap));
                        currentTargetSpeed = Math.min(currentTargetSpeed, crawlSpeed);
                        turnExitMergeCrawl = true;
                    } else {
                        shouldStop = true;
                        hardSameLaneBlockAhead = true;
                    }
                } else {
                    double ratio = (gap - minGap) / (safeDistance - minGap);
                    ratio = Math.max(0, Math.min(1, ratio));
                    double followSpeed = other.getSpeed() * ratio;
                    if (forcingTurnExit) {
                        followSpeed = Math.max(followSpeed, vehicle.baseSpeed * Vehicle.TURN_EXIT_MIN_SPEED_FACTOR);
                    }
                    currentTargetSpeed = Math.min(currentTargetSpeed, followSpeed);
                }
            }
        }

        // BƯỚC 6: Áp tốc độ vật lý
        boolean inIntersection = Math.hypot(vehicle.x - cx, vehicle.y - cy) < Vehicle.INTERSECTION_CLEAR_RADIUS;
        boolean clearingIntersection = vehicle.passedStopLine && inIntersection;
        if (clearingIntersection && shouldStop && !(forcingTurnExit && hardSameLaneBlockAhead)) {
            shouldStop = false;
            currentTargetSpeed = Math.max(currentTargetSpeed, vehicle.baseSpeed * Vehicle.CLEARING_MIN_SPEED_FACTOR);
        }

        if (Vehicle.IS_TEST_ENV()) {
            if (shouldStop) {
                vehicle.speed = 0;
            } else {
                boolean isClear = (Math.abs(currentTargetSpeed - vehicle.baseSpeed) < 1.0);
                if (vehicle.isPriorityVehicle()) {
                    if (inIntersection && isClear) {
                        vehicle.speed = currentTargetSpeed * 1.5;
                    } else if (inIntersection && !isClear) {
                        vehicle.speed = currentTargetSpeed;
                    } else {
                        vehicle.speed = currentTargetSpeed * 1.3;
                    }
                } else if (isFleeing) {
                    vehicle.speed = isClear ? currentTargetSpeed * 1.2 : currentTargetSpeed;
                } else {
                    if (inIntersection && isClear && light.getState() == TrafficLight.State.GREEN) {
                        vehicle.speed = currentTargetSpeed * 1.4;
                    } else if (inIntersection && isClear && vehicle.passedStopLine) {
                        vehicle.speed = currentTargetSpeed * 1.2;
                    } else {
                        vehicle.speed = currentTargetSpeed;
                    }
                }
                if (!forcingTurnExit) {
                    vehicle.speed = IntersectionNavigator.limitSpeedForPredictedIntersectionCollision(vehicle, dt, vehicle.speed, allVehicles, targetInter);
                } else if (turnExitMergeCrawl) {
                    vehicle.speed = Math.max(vehicle.speed, vehicle.baseSpeed * Vehicle.TURN_EXIT_MERGE_CRAWL_MIN_SPEED_FACTOR);
                } else {
                    vehicle.speed = Math.max(vehicle.speed, vehicle.baseSpeed * Vehicle.TURN_EXIT_MIN_SPEED_FACTOR);
                }
                vehicle.movePhysically(dt);
                TrajectoryController.finishDiagonalRightTurnIfNeeded(vehicle, cx, cy);
            }
        } else {
            double targetSpeed;
            if (shouldStop) {
                targetSpeed = 0.0;
            } else {
                boolean isClear = (Math.abs(currentTargetSpeed - vehicle.baseSpeed) < 1.0);
                double calculatedSpeed;
                if (vehicle.isPriorityVehicle()) {
                    if (inIntersection && isClear) {
                        calculatedSpeed = currentTargetSpeed * 1.5;
                    } else if (inIntersection && !isClear) {
                        calculatedSpeed = currentTargetSpeed;
                    } else {
                        calculatedSpeed = currentTargetSpeed * 1.3;
                    }
                } else if (isFleeing) {
                    calculatedSpeed = isClear ? currentTargetSpeed * 1.2 : currentTargetSpeed;
                } else {
                    if (inIntersection && isClear && light.getState() == TrafficLight.State.GREEN) {
                        calculatedSpeed = currentTargetSpeed * 1.4;
                    } else if (inIntersection && isClear && vehicle.passedStopLine) {
                        calculatedSpeed = currentTargetSpeed * 1.2;
                    } else {
                        calculatedSpeed = currentTargetSpeed;
                    }
                }
                if (!forcingTurnExit) {
                    calculatedSpeed = IntersectionNavigator.limitSpeedForPredictedIntersectionCollision(vehicle, dt, calculatedSpeed, allVehicles, targetInter);
                } else if (turnExitMergeCrawl) {
                    calculatedSpeed = Math.max(calculatedSpeed, vehicle.baseSpeed * Vehicle.TURN_EXIT_MERGE_CRAWL_MIN_SPEED_FACTOR);
                } else {
                    calculatedSpeed = Math.max(calculatedSpeed, vehicle.baseSpeed * Vehicle.TURN_EXIT_MIN_SPEED_FACTOR);
                }
                targetSpeed = calculatedSpeed;
            }

            double accelDecelFactor = (targetSpeed < vehicle.speed) ? 10.0 : 5.0;
            vehicle.speed = vehicle.speed + (targetSpeed - vehicle.speed) * dt * accelDecelFactor;
            if (Math.abs(vehicle.speed) < 0.1) {
                vehicle.speed = 0.0;
            }

            if (vehicle.speed > 0.0) {
                vehicle.movePhysically(dt);
            }
            TrajectoryController.finishDiagonalRightTurnIfNeeded(vehicle, cx, cy);
        }
    }

    protected boolean shouldObeyLights(Vehicle vehicle) {
        return true;
    }
}
