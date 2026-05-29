package vn.edu.hust.traffic.model.vehicle;

/**
 * Lớp Motorbike đại diện cho xe máy.
 * Di chuyển ở làn sát lề đường.
 */
public class Motorbike extends Vehicle {
    public Motorbike(String id, double x, double y, double speed, double direction, boolean isPriorityVehicle) {
        // Kích thước thu nhỏ: 16x8
        super(id, x, y, speed, direction, 16, 8, isPriorityVehicle);
    }
}
