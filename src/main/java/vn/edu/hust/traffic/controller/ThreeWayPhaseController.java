package vn.edu.hust.traffic.controller;

import vn.edu.hust.traffic.model.map.TrafficLight;
import vn.edu.hust.traffic.model.map.TrafficLight.State;
import vn.edu.hust.traffic.model.vehicle.Vehicle;

import java.util.List;

/**
 * Điều khiển pha đèn cho Ngã 3 (T-junction).
 * 3 Đèn: [0]=LTR, [1]=RTL, [2]=TTB.
 * 
 * Phase 0: LTR Thẳng XANH, RTL Thẳng/Phải XANH
 * Phase 1: LTR & RTL VÀNG
 * Phase 2: LTR Trái XANH (RTL Đỏ)
 * Phase 3: LTR Trái VÀNG
 * Phase 4: TTB Trái/Phải XANH
 * Phase 5: TTB VÀNG
 */
public class ThreeWayPhaseController {
    private static final double BASE_STRAIGHT = 10.0;
    private static final double MAX_STRAIGHT  = 20.0;
    private static final double BASE_LEFT     = 6.0;
    private static final double MAX_LEFT      = 12.0;
    private static final double DUR_YELLOW    = 3.0;

    private int currentPhase;
    private double phaseTimer;
    private final List<TrafficLight> lights;
    private boolean preemptionWasActive = false;

    public ThreeWayPhaseController(List<TrafficLight> lights) {
        this.lights = lights;
        this.currentPhase = -1;
        advancePhase(null);
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
        int nextPhase = (currentPhase + 1) % 6;
        int checkCount = 0;
        while (checkCount < 6) {
            if (nextPhase % 2 == 1 && currentPhase == nextPhase - 1) {
                // Do not skip yellow transitions for the green phase that just ran
                break;
            }
            if (shouldSkipPhase(nextPhase, vehicles)) {
                nextPhase = (nextPhase + 1) % 6;
                checkCount++;
            } else {
                break;
            }
        }
        if (checkCount == 6) {
            nextPhase = (currentPhase + 1) % 6;
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
            case 0: // LTR Thẳng, RTL Thẳng/Phải
                return !hasApproachingVehicles(vehicles, 0, new int[]{0}) && !hasApproachingVehicles(vehicles, 1, new int[]{0});
            case 2: // LTR Trái
                return !hasApproachingVehicles(vehicles, 0, new int[]{1});
            case 4: // TTB Trái/Phải
                return !hasApproachingVehicles(vehicles, 2, new int[]{0, 1, 2});
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
        double duration = DUR_YELLOW;

        switch (phase) {
            case 0: // LTR Thẳng XANH, RTL Thẳng/Phải XANH
                ltrS = State.GREEN; rtlS = State.GREEN;
                duration = BASE_STRAIGHT; 
                break;
            case 1:
                ltrS = State.YELLOW; rtlS = State.YELLOW; duration = DUR_YELLOW;
                break;
            case 2: // LTR Trái XANH
                ltrL = State.GREEN; duration = BASE_LEFT;
                break;
            case 3:
                ltrL = State.YELLOW; duration = DUR_YELLOW;
                break;
            case 4: // TTB (Trên xuống) Trái/Phải XANH
                ttbS = State.GREEN; ttbL = State.GREEN; duration = BASE_LEFT;
                break;
            case 5:
                ttbS = State.YELLOW; ttbL = State.YELLOW; duration = DUR_YELLOW;
                break;
        }

        phaseTimer = duration;

        lights.get(0).forceState(ltrS, duration);
        lights.get(0).forceLeftTurnState(ltrL, duration);
        lights.get(1).forceState(rtlS, duration);
        lights.get(1).forceLeftTurnState(rtlL, duration);
        lights.get(2).forceState(ttbS, duration);
        lights.get(2).forceLeftTurnState(ttbL, duration);
    }

    public int getCurrentPhase() { return currentPhase; }
    public double getPhaseTimeLeft() { return phaseTimer; }
}
