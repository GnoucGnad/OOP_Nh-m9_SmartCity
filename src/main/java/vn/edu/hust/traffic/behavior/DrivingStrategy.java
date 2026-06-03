package vn.edu.hust.traffic.behavior;

import vn.edu.hust.traffic.model.vehicle.Vehicle;
import vn.edu.hust.traffic.model.map.Intersection;
import java.util.List;

/**
 * Giao diện cho chiến lược lái xe của các phương tiện.
 */
public interface DrivingStrategy {
    void update(Vehicle vehicle, double dt, List<Vehicle> allVehicles, List<Intersection> intersections, int screenWidth, int screenHeight);
}
