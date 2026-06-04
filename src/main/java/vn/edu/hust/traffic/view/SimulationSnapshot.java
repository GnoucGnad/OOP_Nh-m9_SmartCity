package vn.edu.hust.traffic.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import vn.edu.hust.traffic.model.map.TrafficLight;
import vn.edu.hust.traffic.model.vehicle.Vehicle;

public class SimulationSnapshot {
    private final List<Vehicle> vehicles;
    private final List<TrafficLight> lights;
    private final Object intersection;
    private final int phaseIndex;
    private final double phaseTimeLeft;
    private final boolean autoSpawnEnabled;

    public SimulationSnapshot(
            List<Vehicle> vehicles,
            List<TrafficLight> lights,
            Object intersection,
            int phaseIndex,
            double phaseTimeLeft,
            boolean autoSpawnEnabled) {
        this.vehicles = Collections.unmodifiableList(new ArrayList<>(vehicles));
        this.lights = Collections.unmodifiableList(new ArrayList<>(lights));
        this.intersection = intersection;
        this.phaseIndex = phaseIndex;
        this.phaseTimeLeft = phaseTimeLeft;
        this.autoSpawnEnabled = autoSpawnEnabled;
    }

    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    public List<TrafficLight> getLights() {
        return lights;
    }

    public Object getIntersection() {
        return intersection;
    }

    public int getPhaseIndex() {
        return phaseIndex;
    }

    public double getPhaseTimeLeft() {
        return phaseTimeLeft;
    }

    public boolean isAutoSpawnEnabled() {
        return autoSpawnEnabled;
    }
}
