package vn.edu.hust.traffic.controller;

import vn.edu.hust.traffic.model.map.TrafficLight;
import vn.edu.hust.traffic.model.map.TrafficLight.State;
import vn.edu.hust.traffic.model.vehicle.Vehicle;

import java.util.List;

/**
 * Kịch bản B: Pha lệch giờ (Xanh sớm / Đỏ muộn) - 12 phases.
 * Chiều đi LTR Xanh cả thẳng và rẽ trái trước (Xanh sớm). Sau đó đèn trái LTR đỏ, dòng thẳng 2 chiều LTR và RTL cùng Xanh.
 * Sau đó LTR đỏ, RTL được Xanh rẽ trái (Đỏ muộn). Tương tự cho trục Dọc.
 *
 *   Phase 0: LTR Thẳng + Trái XANH      | (Base 5s, max 15s)
 *   Phase 1: LTR Trái VÀNG
 *   Phase 2: LTR & RTL Thẳng XANH       | (Base 10s, max 30s)
 *   Phase 3: LTR Thẳng VÀNG
 *   Phase 4: RTL Thẳng + Trái XANH      | (Base 5s, max 15s)
 *   Phase 5: RTL Thẳng + Trái VÀNG
 *   Phase 6: TTB Thẳng + Trái XANH      | (Base 5s, max 15s)
 *   Phase 7: TTB Trái VÀNG
 *   Phase 8: TTB & BTT Thẳng XANH       | (Base 10s, max 30s)
 *   Phase 9: TTB Thẳng VÀNG
 *   Phase 10: BTT Thẳng + Trái XANH     | (Base 5s, max 15s)
 *   Phase 11: BTT Thẳng + Trái VÀNG
 *
 *   Rẽ phải: LUÔN được phép.
 *
 * Index đèn: [0]=LTR, [1]=RTL, [2]=TTB, [3]=BTT
 */
public class IntersectionPhaseController {

    public static boolean isTestMode = false;

    private static double getBaseStraight() { return isTestMode ? 10.0 : 30.0; }
    private static double getMaxStraight() { return isTestMode ? 30.0 : 30.0; }
    private static double getBaseLeft() { return isTestMode ? 5.0 : 30.0; }
    private static double getMaxLeft() { return isTestMode ? 15.0 : 30.0; }
    private static final double DUR_YELLOW    =  3.0;

    private int currentPhase;
    private double phaseTimer;
    private final List<TrafficLight> lights;
    private boolean preemptionWasActive = false;

    public IntersectionPhaseController(List<TrafficLight> lights) {
        this.lights = lights;
        this.currentPhase = -1;
        advancePhase(null); // Init with default
    }

    public void update(double dt, List<Vehicle> vehicles) {
        update(dt, vehicles, null);
    }

    public void update(double dt, List<Vehicle> vehicles, List<vn.edu.hust.traffic.model.map.Intersection> intersections) {
        for (TrafficLight light : lights) {
            light.update(dt);
        }

        Vehicle pri = getApproachingPriorityVehicle(vehicles, intersections);
        if (pri != null) {
            int priLightIdx = pri.getLightIdx(pri.getDirection());
            for (int i = 0; i < lights.size(); i++) {
                TrafficLight light = lights.get(i);
                if (i == priLightIdx) {
                    light.forceState(State.GREEN, 999.0);
                    light.forceLeftTurnState(State.GREEN, 999.0);
                } else {
                    light.forceState(State.RED, 999.0);
                    light.forceLeftTurnState(State.RED, 999.0);
                }
            }
            phaseTimer = 5.0; // Freeze the timer while preemption is active
            preemptionWasActive = true;
            return;
        }

        if (preemptionWasActive) {
            reapplyCurrentPhase(vehicles);
            preemptionWasActive = false;
        }

        phaseTimer -= dt;
        if (phaseTimer <= 0) {
            advancePhase(vehicles);
        }
    }

    private Vehicle getApproachingPriorityVehicle(List<Vehicle> vehicles, List<vn.edu.hust.traffic.model.map.Intersection> intersections) {
        if (vehicles == null || intersections == null) return null;
        Vehicle closest = null;
        double minDist = Double.MAX_VALUE;
        for (Vehicle v : vehicles) {
            if (v.isPriorityVehicle()) {
                vn.edu.hust.traffic.model.map.Intersection target = v.getTargetIntersection(intersections);
                if (target != null && target.getLights() == this.lights) {
                    double dist = v.getDistToStopLine();
                    if (dist > -30 && dist < 350) {
                        if (dist < minDist) {
                            minDist = dist;
                            closest = v;
                        }
                    }
                }
            }
        }
        return closest;
    }

    private void reapplyCurrentPhase(List<Vehicle> vehicles) {
        applyPhase(currentPhase, vehicles);
    }

    private void advancePhase(List<Vehicle> vehicles) {
        int nextPhase = (currentPhase + 1) % 12;
        int checkCount = 0;
        while (checkCount < 12) {
            if (nextPhase % 2 == 1 && currentPhase == nextPhase - 1) {
                // Do not skip yellow transitions for the green phase that just ran
                break;
            }
            if (shouldSkipPhase(nextPhase, vehicles)) {
                nextPhase = (nextPhase + 1) % 12;
                checkCount++;
            } else {
                break;
            }
        }
        if (checkCount == 12) {
            nextPhase = (currentPhase + 1) % 12;
        }
        currentPhase = nextPhase;
        applyPhase(currentPhase, vehicles);
    }

    private boolean shouldSkipPhase(int phase, List<Vehicle> vehicles) {
        if (vehicles == null || vehicles.isEmpty()) {
            return false;
        }

        int greenPhase = phase;
        if (phase % 2 == 1) {
            greenPhase = phase - 1;
        }

        switch (greenPhase) {
            case 0: // LTR Thẳng + Trái
                return !hasApproachingVehicles(vehicles, 0, new int[]{0, 1});
            case 2: // LTR & RTL Thẳng
                return !hasApproachingVehicles(vehicles, 0, new int[]{0}) && !hasApproachingVehicles(vehicles, 1, new int[]{0});
            case 4: // RTL Thẳng + Trái
                return !hasApproachingVehicles(vehicles, 1, new int[]{0, 1});
            case 6: // TTB Thẳng + Trái
                return !hasApproachingVehicles(vehicles, 2, new int[]{0, 1});
            case 8: // TTB & BTT Thẳng
                return !hasApproachingVehicles(vehicles, 2, new int[]{0}) && !hasApproachingVehicles(vehicles, 3, new int[]{0});
            case 10: // BTT Thẳng + Trái
                return !hasApproachingVehicles(vehicles, 3, new int[]{0, 1});
            default:
                return false;
        }
    }

    private boolean hasApproachingVehicles(List<Vehicle> vehicles, int targetLightIdx, int[] intentions) {
        for (Vehicle v : vehicles) {
            if (v.hasTurned()) continue;
            int vLightIdx = v.getLightIdx(v.getDirection());
            if (vLightIdx != targetLightIdx) continue;

            boolean intentionMatches = false;
            for (int intent : intentions) {
                if (v.getTurnIntention() == intent) {
                    intentionMatches = true;
                    break;
                }
            }
            if (!intentionMatches) continue;

            double dist = v.getDistToStopLine();
            if (dist > -10 && dist < 250) {
                return true;
            }
        }
        return false;
    }

    private void applyPhase(int phase, List<Vehicle> vehicles) {
        State ltrS = State.RED, ltrL = State.RED;
        State rtlS = State.RED, rtlL = State.RED;
        State ttbS = State.RED, ttbL = State.RED;
        State bttS = State.RED, bttL = State.RED;
        double duration = DUR_YELLOW;

        switch (phase) {
            case 0: // LTR Xanh sớm
                ltrS = State.GREEN; ltrL = State.GREEN;
                duration = calculateDynamicDuration(vehicles, 0, 1, getBaseLeft(), getMaxLeft());
                break;
            case 1: // LTR Trái vàng
                ltrS = State.GREEN; ltrL = State.YELLOW; duration = DUR_YELLOW;
                break;
            case 2: // Hai dòng đi thẳng ngang
                ltrS = State.GREEN; rtlS = State.GREEN;
                duration = calculateDynamicDuration(vehicles, -1, 0, getBaseStraight(), getMaxStraight());
                break;
            case 3: // LTR Thẳng vàng
                ltrS = State.YELLOW; rtlS = State.GREEN; duration = DUR_YELLOW;
                break;
            case 4: // RTL Xanh toàn bộ (Đỏ muộn)
                rtlS = State.GREEN; rtlL = State.GREEN;
                duration = calculateDynamicDuration(vehicles, 1, 1, getBaseLeft(), getMaxLeft());
                break;
            case 5: // RTL Thẳng + Trái vàng
                rtlS = State.YELLOW; rtlL = State.YELLOW; duration = DUR_YELLOW;
                break;
            case 6: // TTB Xanh sớm
                ttbS = State.GREEN; ttbL = State.GREEN;
                duration = calculateDynamicDuration(vehicles, 2, 1, getBaseLeft(), getMaxLeft());
                break;
            case 7: // TTB Trái vàng
                ttbS = State.GREEN; ttbL = State.YELLOW; duration = DUR_YELLOW;
                break;
            case 8: // Hai dòng đi thẳng dọc
                ttbS = State.GREEN; bttS = State.GREEN;
                duration = calculateDynamicDuration(vehicles, -2, 0, getBaseStraight(), getMaxStraight());
                break;
            case 9: // TTB Thẳng vàng
                ttbS = State.YELLOW; bttS = State.GREEN; duration = DUR_YELLOW;
                break;
            case 10: // BTT Xanh toàn bộ (Đỏ muộn)
                bttS = State.GREEN; bttL = State.GREEN;
                duration = calculateDynamicDuration(vehicles, 3, 1, getBaseLeft(), getMaxLeft());
                break;
            case 11: // BTT Thẳng + Trái vàng
                bttS = State.YELLOW; bttL = State.YELLOW; duration = DUR_YELLOW;
                break;
        }

        phaseTimer = duration;

        lights.get(0).forceState(ltrS, duration);          // LTR thẳng
        lights.get(0).forceLeftTurnState(ltrL, duration);   // LTR rẽ trái
        lights.get(1).forceState(rtlS, duration);          // RTL thẳng
        lights.get(1).forceLeftTurnState(rtlL, duration);   // RTL rẽ trái
        lights.get(2).forceState(ttbS, duration);          // TTB thẳng
        lights.get(2).forceLeftTurnState(ttbL, duration);   // TTB rẽ trái
        lights.get(3).forceState(bttS, duration);          // BTT thẳng
        lights.get(3).forceLeftTurnState(bttL, duration);   // BTT rẽ trái
    }

    /**
     * Tính toán thời gian đèn xanh dựa trên số lượng xe đang chờ.
     * @param targetLightIdx -1: Ngang (0,1), -2: Dọc (2,3), hoặc 0/1/2/3 cho làn cụ thể
     * @param intention 0: Đi thẳng, 1: Rẽ trái
     */
    private double calculateDynamicDuration(List<Vehicle> vehicles, int targetLightIdx, int intention, double baseDur, double maxDur) {
        if (vehicles == null || vehicles.isEmpty()) return baseDur;

        int waitingCount = 0;
        for (Vehicle v : vehicles) {
            if (v.hasTurned()) continue;
            
            int vLightIdx = v.getLightIdx(v.getDirection());
            
            // Check lightIdx matches requirement
            if (targetLightIdx == -1 && vLightIdx >= 2) continue; // Phải là ngang
            if (targetLightIdx == -2 && vLightIdx < 2) continue;  // Phải là dọc
            if (targetLightIdx >= 0 && vLightIdx != targetLightIdx) continue;

            // Check intention
            if (v.getTurnIntention() != intention) continue;

            // Check if vehicle is approaching or waiting at the stop line
            // We only count vehicles that are near the intersection (within 300px)
            double dist = v.getDistToStopLine();
            if (dist > -10 && dist < 300) {
                waitingCount++;
            }
        }

        // Mỗi xe chờ cộng thêm 1.5 giây
        double calculatedDur = baseDur + (waitingCount * 1.5);
        return Math.min(calculatedDur, maxDur);
    }

    public int getCurrentPhase() { return currentPhase; }
    public double getPhaseTimeLeft() { return phaseTimer; }
    public List<TrafficLight> getLights() { return lights; }
}
