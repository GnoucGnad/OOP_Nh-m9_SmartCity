package vn.edu.hust.traffic.model.vehicle;

/**
 * Lớp Ambulance đại diện cho xe ưu tiên.
 * Xe cứu thương luôn là xe ưu tiên: được phép vượt đèn đỏ và xin ngã tư trống.
 */
public class Ambulance extends Vehicle {
    public Ambulance(String id, double x, double y, double speed, double direction, boolean isEmergency) {
        // Kích thước thu nhỏ: 30x14; Ambulance luôn là xe ưu tiên.
        super(id, x, y, speed, direction, 30, 14, true);
    }
}


