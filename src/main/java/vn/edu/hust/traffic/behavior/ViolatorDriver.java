package vn.edu.hust.traffic.behavior;

import vn.edu.hust.traffic.model.vehicle.Vehicle;

/**
 * Chiến lược lái xe vi phạm luật: Không tuân thủ đèn giao thông (vượt đèn đỏ/vàng).
 */
public class ViolatorDriver extends NormalDriver {

    @Override
    protected boolean shouldObeyLights(Vehicle vehicle) {
        return false;
    }
}
