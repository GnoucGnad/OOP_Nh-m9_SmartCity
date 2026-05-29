package vn.edu.hust.traffic.model.vehicle;

/**
 * Lớp Bus đại diện cho xe buýt.
 * Kích thước lớn, đi ở làn ô tô.
 */
public class Bus extends Vehicle {
    public Bus(String id, double x, double y, double speed, double direction) {
        // Kích thước thu nhỏ cho vừa làn: 52x18
        super(id, x, y, speed, direction, 52, 18, false);
    }
}
