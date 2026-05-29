package vn.edu.hust.traffic.model.vehicle;

/**
 * Lớp FireTruck đại diện cho xe cứu hỏa trong hệ thống giao thông thông minh.
 * Xe cứu hỏa là loại xe ưu tiên, có khả năng vượt đèn đỏ và ưu tiên qua ngã tư.
 */
public class FireTruck extends Vehicle {
    /**
     * Tạo một FireTruck mới.
     *
     * @param id               Mã định danh duy nhất của xe.
     * @param x                Tọa độ x ban đầu.
     * @param y                Tọa độ y ban đầu.
     * @param speed            Tốc độ ban đầu.
     * @param direction        Hướng di chuyển (radian).
     */
    public FireTruck(String id, double x, double y, double speed, double direction) {
        // Kích thước thu nhỏ: 40x20, là xe ưu tiên => isPriorityVehicle = true
        super(id, x, y, speed, direction, 40, 20, true);
    }
}
