package vn.edu.hust.traffic.model.vehicle;



/**
 * Lớp Car mô phỏng xe ô tô trong hệ thống giao thông thông minh.
 * Xe có thể di chuyển, thay đổi tốc độ, hướng và tuân thủ luật giao thông.
 *
 * @author Người 1
 */
public class Car extends Vehicle {
    public Car(String id, double x, double y, double speed, double direction, double width, double height, boolean isPriorityVehicle) {
        super(id, x, y, speed, direction, width, height, isPriorityVehicle);
    }
}
