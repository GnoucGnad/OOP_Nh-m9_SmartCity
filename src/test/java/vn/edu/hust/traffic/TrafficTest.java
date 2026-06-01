package vn.edu.hust.traffic;

import org.junit.jupiter.api.Test;
import vn.edu.hust.traffic.controller.SimulationMode;
import vn.edu.hust.traffic.controller.TrafficController;
import vn.edu.hust.traffic.model.map.CrossIntersection;
import vn.edu.hust.traffic.model.map.Intersection;
import vn.edu.hust.traffic.model.map.RoundaboutIntersection;
import vn.edu.hust.traffic.model.vehicle.Ambulance;
import vn.edu.hust.traffic.model.vehicle.Bus;
import vn.edu.hust.traffic.model.vehicle.Car;
import vn.edu.hust.traffic.model.vehicle.FireTruck;
import vn.edu.hust.traffic.model.vehicle.Motorbike;
import vn.edu.hust.traffic.model.vehicle.Vehicle;
import vn.edu.hust.traffic.model.map.ThreeWayIntersection;
import vn.edu.hust.traffic.model.map.TrafficLight;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrafficTest {
    @Test
    public void testEmergencyPreemptionInIntersection() throws Exception {
        List<TrafficLight> lights = redLights(4);
        CrossIntersection cross = new CrossIntersection("cross1", 400.0, 300.0, lights);
        List<Intersection> intersections = List.of(cross);
        
        Vehicle ambulance = new Ambulance("Amb1", 200.0, 340.0, 80.0, 0.0, true);
        List<Vehicle> vehicles = List.of(ambulance);
        
        ambulance.update(0.016, vehicles, intersections, 1400, 600);
        
        vn.edu.hust.traffic.controller.IntersectionPhaseController phaseController = 
            new vn.edu.hust.traffic.controller.IntersectionPhaseController(lights);
            
        phaseController.update(0.016, vehicles, intersections);
        
        assertEquals(TrafficLight.State.GREEN, lights.get(0).getState());
        assertEquals(TrafficLight.State.GREEN, lights.get(0).getLeftTurnState());
        
        assertEquals(TrafficLight.State.RED, lights.get(1).getState());
        assertEquals(TrafficLight.State.RED, lights.get(1).getLeftTurnState());
        
        setBooleanField(ambulance, "passedStopLine", true);
        setDoubleField(ambulance, "x", 600.0);
        ambulance.update(0.016, vehicles, intersections, 1400, 600);
        
        phaseController.update(0.016, vehicles, intersections);
        
        assertEquals(TrafficLight.State.GREEN, lights.get(0).getState());
        assertEquals(TrafficLight.State.RED, lights.get(1).getState());
    }

    @Test
    public void testSmartActuatedPhaseSkipping() throws Exception {
        List<TrafficLight> lights = redLights(4);
        CrossIntersection cross = new CrossIntersection("cross1", 400.0, 300.0, lights);
        List<Intersection> intersections = List.of(cross);
        
        Vehicle car = new Car("CarRTL", 600.0, 260.0, 80.0, Math.PI, 26, 13, false);
        car.setTurnIntention(0);
        List<Vehicle> vehicles = List.of(car);
        car.update(0.016, vehicles, intersections, 1400, 600);
        
        vn.edu.hust.traffic.controller.IntersectionPhaseController phaseController = 
            new vn.edu.hust.traffic.controller.IntersectionPhaseController(lights);
            
        Field currentPhaseField = phaseController.getClass().getDeclaredField("currentPhase");
        currentPhaseField.setAccessible(true);
        currentPhaseField.setInt(phaseController, 5);
        
        Method advancePhaseMethod = phaseController.getClass().getDeclaredMethod("advancePhase", List.class);
        advancePhaseMethod.setAccessible(true);
        advancePhaseMethod.invoke(phaseController, vehicles);
        
        assertEquals(2, phaseController.getCurrentPhase());
    }

    @Test
    public void roadNetworkRoundaboutWithFourExitsDoesNotCrash() throws Exception {
        double[] roadNetworkAngles = new double[] {
                0,
                -Math.PI / 2,
                Math.PI,
                Math.PI / 2
        };
        List<Intersection> intersections = List.of(
                new RoundaboutIntersection("roundabout1", 1200.0, -500.0, 100.0, roadNetworkAngles));
        List<Vehicle> vehicles = new ArrayList<>();
        Vehicle vehicle = new Car("Car1", 1200.0, -1000.0, 80.0, Math.PI / 2, 26, 13, false);
        vehicles.add(vehicle);
        Field targetExitIndex = Vehicle.class.getDeclaredField("targetExitIndex");
        targetExitIndex.setAccessible(true);
        targetExitIndex.setInt(vehicle, 4);

        assertDoesNotThrow(() -> vehicle.update(0.016, vehicles, intersections, 1400, 600));
    }

    @Test
    public void threeWaySpawnDoesNotChooseMissingRoadTurns() throws Exception {
        TrafficController controller = new TrafficController(SimulationMode.THREE_WAY_INTERSECTION);
        Method createSpawnPoint = TrafficController.class.getDeclaredMethod("createSpawnPoint", int.class);
        createSpawnPoint.setAccessible(true);

        for (int i = 0; i < 200; i++) {
            assertNotEquals(2, turnIntention(createSpawnPoint.invoke(controller, 0)));
            assertNotEquals(1, turnIntention(createSpawnPoint.invoke(controller, 1)));
            assertNotEquals(0, turnIntention(createSpawnPoint.invoke(controller, 4)));
        }
    }

    @Test
    public void roadNetworkRoundaboutHasFiveLogicalBranches() {
        TrafficController controller = new TrafficController(SimulationMode.ROAD_NETWORK);
        RoundaboutIntersection roundabout = controller.getIntersections().stream()
                .filter(RoundaboutIntersection.class::isInstance)
                .map(RoundaboutIntersection.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals(5, roundabout.getRoadAngles().length);
    }

    @Test
    public void trafficDensityMapsToRequestedVehicleLimits() throws Exception {
        TrafficController controller = new TrafficController(SimulationMode.ROAD_NETWORK);

        controller.setTrafficDensity(1);
        assertEquals(20, controller.getMaxVehicles());

        controller.setTrafficDensity(2);
        assertEquals(30, controller.getMaxVehicles());

        controller.setTrafficDensity(3);
        assertEquals(40, controller.getMaxVehicles());

        controller.setTrafficDensity(1);
        Method spawnVehicle = TrafficController.class.getDeclaredMethod("spawnVehicle", int.class);
        spawnVehicle.setAccessible(true);
        for (int i = 0; i < 60; i++) {
            spawnVehicle.invoke(controller, i % 5);
        }

        assertEquals(20, controller.getVehicles().size());
    }

    @Test
    public void crossIntersectionVehicleClearsInsteadOfStoppingInMiddle() {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle follower = new Car("Follower", 390.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle leader = new Car("Leader", 420.0, 340.0, 0.0, 0.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, leader));

        follower.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(follower.getSpeed() > 0.0);
        assertTrue(follower.getX() > 390.0);
    }

    @Test
    public void closeVehicleInsideIntersectionKeepsCrawlingToClearDeadlock() {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle clearingVehicle = new Car("Clearing", 390.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle crossTraffic = new Car("CrossTraffic", 390.0, 350.0, 0.0, Math.PI / 2.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(clearingVehicle, crossTraffic));

        clearingVehicle.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(clearingVehicle.getSpeed() > 0.0, "speed=" + clearingVehicle.getSpeed());
        assertTrue(clearingVehicle.getX() > 390.0, "x=" + clearingVehicle.getX());
    }

    @Test
    public void crossIntersectionClearingVehicleDoesNotHardStopForNearConflict() throws Exception {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle clearingVehicle = new Car("ZClearing", 390.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle crossTraffic = new Car("ACrossing", 400.0, 324.0, 80.0, Math.PI / 2.0, 26, 13, false);
        setBooleanField(clearingVehicle, "passedStopLine", true);
        setBooleanField(crossTraffic, "passedStopLine", true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(clearingVehicle, crossTraffic));

        clearingVehicle.update(0.02, vehicles, intersections, 1400, 600);

        assertTrue(clearingVehicle.getSpeed() > 0.0, "speed=" + clearingVehicle.getSpeed());
        assertTrue(clearingVehicle.getX() > 390.0, "x=" + clearingVehicle.getX());
    }

    @Test
    public void crossIntersectionSevereConflictLetsEarlierVehicleClearFirst() throws Exception {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle earlier = new Car("Earlier", 390.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle later = new Car("Later", 394.0, 342.0, 80.0, Math.PI / 2.0, 26, 13, false);
        setBooleanField(earlier, "passedStopLine", true);
        setBooleanField(later, "passedStopLine", true);
        setStringField(earlier, "activeIntersectionId", "cross1");
        setStringField(later, "activeIntersectionId", "cross1");
        setIntField(earlier, "activeIntersectionEntryLightIdx", 0);
        setIntField(later, "activeIntersectionEntryLightIdx", 2);
        setLongField(earlier, "activeIntersectionEntryOrder", 1L);
        setLongField(later, "activeIntersectionEntryOrder", 2L);
        List<Vehicle> vehicles = new ArrayList<>(List.of(earlier, later));

        earlier.update(0.05, vehicles, intersections, 1400, 600);
        later.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(earlier.getSpeed() > 0.0, "earlier speed=" + earlier.getSpeed());
        assertTrue(earlier.getX() > 390.0, "earlier x=" + earlier.getX());
        assertEquals(0.0, later.getSpeed(), 0.01);
    }

    @Test
    public void vehicleAlreadyInsideIntersectionDoesNotChangeLaneToYield() {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle normal = new Car("Normal", 300.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle ambulance = new Ambulance("AmbBehind", 120.0, 340.0, 80.0, 0.0, true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(normal, ambulance));

        normal.update(0.2, vehicles, intersections, 1400, 600);

        assertEquals(340.0, normal.getY(), 0.01);
        assertTrue(normal.getX() > 300.0, "x=" + normal.getX());
    }

    @Test
    public void threeWayVehicleClearsInsteadOfStoppingInMiddle() {
        List<TrafficLight> lights = greenLights(3);
        List<Intersection> intersections = List.of(new ThreeWayIntersection("three1", 1200.0, 300.0, lights));
        Vehicle follower = new Car("Follower", 1190.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle leader = new Car("Leader", 1220.0, 340.0, 0.0, 0.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, leader));

        follower.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(follower.getSpeed() > 0.0);
        assertTrue(follower.getX() > 1190.0);
    }

    @Test
    public void threeWayClearingVehicleCrawlsThroughPathOnlyConflict() {
        List<TrafficLight> lights = greenLights(3);
        List<Intersection> intersections = List.of(new ThreeWayIntersection("three1", 1200.0, 300.0, lights));
        Vehicle clearingVehicle = new Car("ZClearing", 1160.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle crossingVehicle = new Car("ACrossing", 1180.0, 300.0, 80.0, Math.PI / 2.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(clearingVehicle, crossingVehicle));

        clearingVehicle.update(0.5, vehicles, intersections, 1400, 600);

        assertTrue(clearingVehicle.getSpeed() > 0.0, "speed=" + clearingVehicle.getSpeed());
        assertTrue(clearingVehicle.getX() > 1160.0, "x=" + clearingVehicle.getX());
    }

    @Test
    public void threeWayClearingVehicleDoesNotHardStopForNearConflict() throws Exception {
        List<TrafficLight> lights = greenLights(3);
        List<Intersection> intersections = List.of(new ThreeWayIntersection("three1", 1200.0, 300.0, lights));
        Vehicle clearingVehicle = new Car("ZClearing", 1190.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle crossingVehicle = new Car("ACrossing", 1200.0, 324.0, 80.0, Math.PI / 2.0, 26, 13, false);
        setBooleanField(clearingVehicle, "passedStopLine", true);
        setBooleanField(crossingVehicle, "passedStopLine", true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(clearingVehicle, crossingVehicle));

        clearingVehicle.update(0.02, vehicles, intersections, 1400, 600);

        assertTrue(clearingVehicle.getSpeed() > 0.0, "speed=" + clearingVehicle.getSpeed());
        assertTrue(clearingVehicle.getX() > 1190.0, "x=" + clearingVehicle.getX());
    }

    @Test
    public void normalVehicleYieldsToPriorityBeforeEnteringIntersection() {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle normal = new Car("Normal", 260.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle ambulance = new Ambulance("Amb1", 360.0, 120.0, 80.0, Math.PI / 2.0, true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(normal, ambulance));

        normal.update(0.05, vehicles, intersections, 1400, 600);

        assertEquals(0.0, normal.getSpeed(), 0.01);
    }

    @Test
    public void priorityVehicleDoesNotYieldToNormalVehicleAtIntersection() {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle ambulance = new Ambulance("Amb1", 260.0, 340.0, 80.0, 0.0, true);
        Vehicle normal = new Car("Normal", 360.0, 120.0, 80.0, Math.PI / 2.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(ambulance, normal));

        ambulance.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(ambulance.getSpeed() > 0.0);
        assertTrue(ambulance.getX() > 260.0);
    }

    @Test
    public void priorityVehicleChangesLaneAroundTurningVehicleInsideIntersection() throws Exception {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle ambulance = new Ambulance("AmbBypass", 300.0, 340.0, 80.0, 0.0, true);
        Vehicle turningVehicle = new Car("Turning", 335.0, 340.0, 0.0, 0.0, 26, 13, false);
        setBooleanField(turningVehicle, "isTurningSmoothly", true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(ambulance, turningVehicle));

        ambulance.update(0.2, vehicles, intersections, 1400, 600);

        assertTrue(ambulance.getSpeed() > 0.0, "speed=" + ambulance.getSpeed());
        assertTrue(ambulance.getX() > 300.0, "x=" + ambulance.getX());
        assertTrue(Math.abs(ambulance.getY() - 340.0) > 8.0, "y=" + ambulance.getY());
    }

    @Test
    public void normalVehicleChangesLaneAroundDifferentTurnInsideIntersection() throws Exception {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle normal = new Car("NormalBypass", 300.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle turningVehicle = new Car("Turning", 335.0, 340.0, 0.0, 0.0, 26, 13, false);
        turningVehicle.setTurnIntention(1);
        setBooleanField(turningVehicle, "isTurningSmoothly", true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(normal, turningVehicle));

        normal.update(0.2, vehicles, intersections, 1400, 600);

        assertTrue(normal.getSpeed() > 0.0, "speed=" + normal.getSpeed());
        assertTrue(normal.getX() > 300.0, "x=" + normal.getX());
        assertTrue(Math.abs(normal.getY() - 340.0) > 8.0, "y=" + normal.getY());
    }

    @Test
    public void normalVehicleDoesNotChangeLaneBeforeEnteringIntersection() throws Exception {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle normal = new Car("NormalBefore", 240.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle turningVehicle = new Car("Turning", 300.0, 340.0, 0.0, 0.0, 26, 13, false);
        turningVehicle.setTurnIntention(1);
        setBooleanField(turningVehicle, "isTurningSmoothly", true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(normal, turningVehicle));

        normal.update(0.05, vehicles, intersections, 1400, 600);

        assertEquals(340.0, normal.getY(), 0.01);
    }

    @Test
    public void ambulanceAndFireTruckCrossRedLight() {
        List<TrafficLight> lights = redLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));

        Vehicle ambulance = new Ambulance("AmbRed", 260.0, 340.0, 80.0, 0.0, false);
        List<Vehicle> ambulanceOnly = new ArrayList<>(List.of(ambulance));
        ambulance.update(0.15, ambulanceOnly, intersections, 1400, 600);

        assertTrue(ambulance.isPriorityVehicle());
        assertTrue(ambulance.getX() + ambulance.getWidth() / 2.0 > 280.0,
                "x=" + ambulance.getX() + ", speed=" + ambulance.getSpeed());

        Vehicle fireTruck = new FireTruck("FireRed", 250.0, 365.0, 80.0, 0.0);
        List<Vehicle> fireTruckOnly = new ArrayList<>(List.of(fireTruck));
        fireTruck.update(0.15, fireTruckOnly, intersections, 1400, 600);

        assertTrue(fireTruck.isPriorityVehicle());
        assertTrue(fireTruck.getX() + fireTruck.getWidth() / 2.0 > 280.0,
                "x=" + fireTruck.getX() + ", speed=" + fireTruck.getSpeed());
    }

    @Test
    public void priorityVehicleChangesToLeastBusyLaneBeforeRedQueue() {
        List<TrafficLight> lights = redLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle ambulance = new Ambulance("AmbLane", 80.0, 340.0, 80.0, 0.0, true);
        Vehicle middleLaneQueue = new Car("Middle", 250.0, 340.0, 0.0, 0.0, 26, 13, false);
        Vehicle outerLaneQueue1 = new Car("Outer1", 230.0, 365.0, 0.0, 0.0, 26, 13, false);
        Vehicle outerLaneQueue2 = new Car("Outer2", 260.0, 365.0, 0.0, 0.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(
                ambulance, middleLaneQueue, outerLaneQueue1, outerLaneQueue2));

        ambulance.update(0.2, vehicles, intersections, 1400, 600);

        assertTrue(ambulance.getY() < 340.0, "y=" + ambulance.getY());
    }

    @Test
    public void redLightStoppedVehicleCanCrossStopLineToYieldToPriorityBehind() {
        List<TrafficLight> lights = redLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle stoppedCar = new Car("Stopped", 260.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle ambulance = new Ambulance("AmbBehind", 120.0, 340.0, 80.0, 0.0, true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(stoppedCar, ambulance));

        stoppedCar.update(0.15, vehicles, intersections, 1400, 600);

        assertTrue(stoppedCar.getX() + stoppedCar.getWidth() / 2.0 > 280.0,
                "x=" + stoppedCar.getX() + ", speed=" + stoppedCar.getSpeed());
    }

    @Test
    public void yieldingVehicleMovesToLeastBusyLaneAwayFromPriorityVehicle() {
        List<TrafficLight> lights = redLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle stoppedCar = new Car("Yielding", 260.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle ambulance = new Ambulance("AmbBehind", 120.0, 340.0, 80.0, 0.0, true);
        Vehicle outerLaneQueue1 = new Car("Outer1", 220.0, 365.0, 0.0, 0.0, 26, 13, false);
        Vehicle outerLaneQueue2 = new Car("Outer2", 250.0, 365.0, 0.0, 0.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(
                stoppedCar, ambulance, outerLaneQueue1, outerLaneQueue2));

        stoppedCar.update(0.2, vehicles, intersections, 1400, 600);

        assertEquals(315.0, stoppedCar.getY(), 0.01);
        assertTrue(stoppedCar.getX() + stoppedCar.getWidth() / 2.0 > 280.0,
                "x=" + stoppedCar.getX() + ", speed=" + stoppedCar.getSpeed());
    }

    @Test
    public void yieldingVehicleFinishesFullLaneChangeAfterPriorityTriggerEnds() throws Exception {
        List<TrafficLight> lights = redLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle yieldingCar = new Car("Yielding", 260.0, 355.0, 80.0, 0.0, 26, 13, false);
        setBooleanField(yieldingCar, "yieldingToPriorityVehicle", true);
        setDoubleField(yieldingCar, "yieldTargetLaneOffset", 40.0);
        setIntField(yieldingCar, "yieldLightIdx", 0);
        setStringField(yieldingCar, "yieldIntersectionId", "cross1");
        setStringField(yieldingCar, "yieldPriorityVehicleId", "AmbGone");
        List<Vehicle> vehicles = new ArrayList<>(List.of(yieldingCar));

        yieldingCar.update(0.2, vehicles, intersections, 1400, 600);

        assertEquals(340.0, yieldingCar.getY(), 0.01);
    }

    @Test
    public void yieldingVehicleOnlyMovesOneAdjacentLane() {
        List<TrafficLight> lights = redLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle yieldingCar = new Car("Yielding", 240.0, 365.0, 80.0, 0.0, 26, 13, false);
        Vehicle ambulance = new Ambulance("AmbBehind", 120.0, 365.0, 80.0, 0.0, true);
        Vehicle middleLaneQueue1 = new Car("Middle1", 420.0, 340.0, 0.0, 0.0, 26, 13, false);
        Vehicle middleLaneQueue2 = new Car("Middle2", 450.0, 340.0, 0.0, 0.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(
                yieldingCar, ambulance, middleLaneQueue1, middleLaneQueue2));

        for (int i = 0; i < 12; i++) {
            yieldingCar.update(0.1, vehicles, intersections, 1400, 600);
        }

        assertEquals(340.0, yieldingCar.getY(), 2.0);
        assertTrue(yieldingCar.getY() > 330.0, "y=" + yieldingCar.getY());
    }

    @Test
    public void vehicleOutsidePriorityLaneDoesNotYieldAcrossLanes() {
        List<TrafficLight> lights = redLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle sideLaneCar = new Car("Side", 260.0, 315.0, 80.0, 0.0, 26, 13, false);
        Vehicle ambulance = new Ambulance("AmbBehind", 120.0, 365.0, 80.0, 0.0, true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(sideLaneCar, ambulance));

        sideLaneCar.update(0.2, vehicles, intersections, 1400, 600);

        assertEquals(315.0, sideLaneCar.getY(), 0.01);
    }

    @Test
    public void laterNormalVehicleSlowsForEarlierNormalVehicleClearingIntersection() {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle first = new Car("First", 285.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle second = new Car("Second", 390.0, 320.0, 80.0, Math.PI / 2.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(first, second));

        first.update(0.05, vehicles, intersections, 1400, 600);
        second.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(second.getSpeed() < 80.0, "speed=" + second.getSpeed());
    }

    @Test
    public void vehicleDoesNotAdvanceIntoOccupiedIntersectionConflict() {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle crossingVehicle = new Car("Crossing", 390.0, 340.0, 0.0, 0.0, 26, 13, false);
        Vehicle enteringVehicle = new Car("Entering", 390.0, 330.0, 80.0, Math.PI / 2.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(crossingVehicle, enteringVehicle));

        enteringVehicle.update(0.05, vehicles, intersections, 1400, 600);

        assertEquals(0.0, enteringVehicle.getSpeed(), 0.01);
        assertEquals(330.0, enteringVehicle.getY(), 0.01);
    }

    @Test
    public void crossIntersectionVehicleWaitsAtStopLineForClearingCrossTraffic() throws Exception {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle enteringVehicle = new Car("Entering", 260.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle clearingVehicle = new Car("Clearing", 390.0, 250.0, 80.0, Math.PI / 2.0, 26, 13, false);
        setBooleanField(clearingVehicle, "passedStopLine", true);
        setStringField(clearingVehicle, "activeIntersectionId", "cross1");
        List<Vehicle> vehicles = new ArrayList<>(List.of(enteringVehicle, clearingVehicle));

        enteringVehicle.update(0.05, vehicles, intersections, 1400, 600);

        assertEquals(0.0, enteringVehicle.getSpeed(), 0.01);
        assertEquals(260.0, enteringVehicle.getX(), 0.01);
    }

    @Test
    public void threeWayVehicleWaitsAtStopLineForClearingCrossTraffic() throws Exception {
        List<TrafficLight> lights = greenLights(3);
        List<Intersection> intersections = List.of(new ThreeWayIntersection("three1", 1200.0, 300.0, lights));
        Vehicle enteringVehicle = new Car("Entering", 1060.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle clearingVehicle = new Car("Clearing", 1190.0, 250.0, 80.0, Math.PI / 2.0, 26, 13, false);
        setBooleanField(clearingVehicle, "passedStopLine", true);
        setStringField(clearingVehicle, "activeIntersectionId", "three1");
        List<Vehicle> vehicles = new ArrayList<>(List.of(enteringVehicle, clearingVehicle));

        enteringVehicle.update(0.05, vehicles, intersections, 1400, 600);

        assertEquals(0.0, enteringVehicle.getSpeed(), 0.01);
        assertEquals(1060.0, enteringVehicle.getX(), 0.01);
    }

    @Test
    public void clearingVehicleCrawlsInsteadOfStoppingForNonImmediatePriorityConflict() {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle clearingVehicle = new Car("Clearing", 380.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle ambulance = new Ambulance("AmbConflict", 400.0, 308.0, 80.0, Math.PI / 2.0, true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(clearingVehicle, ambulance));

        clearingVehicle.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(clearingVehicle.getSpeed() > 0.0, "speed=" + clearingVehicle.getSpeed());
        assertTrue(clearingVehicle.getX() > 380.0, "x=" + clearingVehicle.getX());
    }

    @Test
    public void adjacentLaneVehicleDoesNotBlockGreenLightDeparture() {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle adjacentLaneVehicle = new Car("A", 260.0, 340.0, 0.0, 0.0, 26, 13, false);
        Vehicle departingVehicle = new Car("B", 260.0, 365.0, 80.0, 0.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(adjacentLaneVehicle, departingVehicle));

        departingVehicle.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(departingVehicle.getSpeed() > 0.0, "speed=" + departingVehicle.getSpeed());
        assertTrue(departingVehicle.getX() > 260.0, "x=" + departingVehicle.getX());
    }

    @Test
    public void normalVehicleChangesLaneToOvertakeSlowerVehicle() {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle follower = new Car("Follower", 120.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle slowLeader = new Car("Slow", 190.0, 340.0, 20.0, 0.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, slowLeader));

        follower.update(0.2, vehicles, intersections, 1400, 600);

        assertTrue(follower.getY() < 340.0, "y=" + follower.getY());
        assertTrue(follower.getSpeed() > slowLeader.getSpeed(), "speed=" + follower.getSpeed());
    }

    @Test
    public void normalVehicleDoesNotOvertakeWhenTargetLaneIsUnsafe() {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle follower = new Car("Follower", 120.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle slowLeader = new Car("Slow", 160.0, 340.0, 20.0, 0.0, 26, 13, false);
        Vehicle innerLaneCar = new Car("Inner", 125.0, 315.0, 80.0, 0.0, 26, 13, false);
        Vehicle outerLaneCar = new Car("Outer", 125.0, 365.0, 80.0, 0.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, slowLeader, innerLaneCar, outerLaneCar));

        follower.update(0.2, vehicles, intersections, 1400, 600);

        assertEquals(340.0, follower.getY(), 0.01);
        assertTrue(follower.getSpeed() < 80.0, "speed=" + follower.getSpeed());
    }

    @Test
    public void normalVehicleDoesNotOvertakeVehicleStoppedByRedLight() {
        List<TrafficLight> lights = greenStraightRedLeftLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle follower = new Car("Follower", 120.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle stoppedLeftTurner = new Car("StoppedLeft", 160.0, 340.0, 0.0, 0.0, 26, 13, false);
        stoppedLeftTurner.setTurnIntention(1);
        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, stoppedLeftTurner));

        follower.update(0.2, vehicles, intersections, 1400, 600);

        assertEquals(340.0, follower.getY(), 0.01);
        assertTrue(follower.getSpeed() < 80.0, "speed=" + follower.getSpeed());
    }

    @Test
    public void normalOvertakeOnlyMovesOneAdjacentLane() {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle follower = new Car("Follower", 120.0, 365.0, 80.0, 0.0, 26, 13, false);
        Vehicle slowLeader = new Car("Slow", 190.0, 365.0, 20.0, 0.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, slowLeader));

        for (int i = 0; i < 4; i++) {
            follower.update(0.2, vehicles, intersections, 1400, 600);
        }

        assertEquals(340.0, follower.getY(), 2.0);
        assertTrue(follower.getY() > 330.0, "y=" + follower.getY());
    }

    @Test
    public void overtakingVehicleReturnsToOriginalLaneAfterPassing() {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle follower = new Car("Follower", 20.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle slowLeader = new Car("Slow", 85.0, 340.0, 10.0, 0.0, 26, 13, false);
        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, slowLeader));

        for (int i = 0; i < 60; i++) {
            follower.update(0.05, vehicles, intersections, 1400, 600);
        }

        assertTrue(follower.getX() > slowLeader.getX() + 35.0,
                "follower=" + follower.getX() + ", slow=" + slowLeader.getX());
        assertEquals(340.0, follower.getY(), 3.0);
    }

    @Test
    public void threeWayVehiclesExitOntoExistingRoads() {
        Vehicle leftToNorth = runThreeWayVehicle("leftToNorth", 550.0, 315.0, 0.0, 1, 12.0);
        assertEquals(-Math.PI / 2.0, leftToNorth.getDirection(), 0.01);
        assertTrue(leftToNorth.getX() > 1200.0);
        assertTrue(leftToNorth.getY() < 250.0);

        Vehicle rightToNorth = runThreeWayVehicle("rightToNorth", 1450.0, 235.0, Math.PI, 2, 12.0);
        assertEquals(-Math.PI / 2.0, rightToNorth.getDirection(), 0.01,
                "x=" + rightToNorth.getX() + ", y=" + rightToNorth.getY()
                        + ", speed=" + rightToNorth.getSpeed());
        assertTrue(rightToNorth.getX() > 1200.0);
        assertTrue(rightToNorth.getY() < 250.0);

        Vehicle northToEast = runThreeWayVehicle("northToEast", 1185.0, -250.0, Math.PI / 2.0, 1, 12.0);
        assertEquals(0.0, northToEast.getDirection(), 0.01);
        assertTrue(northToEast.getX() > 1250.0);
        assertTrue(northToEast.getY() > 300.0);

        Vehicle northToWest = runThreeWayVehicle("northToWest", 1135.0, -250.0, Math.PI / 2.0, 2, 12.0);
        assertEquals(Math.PI, northToWest.getDirection(), 0.01);
        assertTrue(northToWest.getX() < 1150.0);
        assertTrue(northToWest.getY() < 300.0);
    }

    @Test
    public void threeWayAndFiveWayModesRunWithoutRuntimeErrors() {
        assertDoesNotThrow(() -> runControllerForSeconds(
                new TrafficController(SimulationMode.THREE_WAY_INTERSECTION), 90));
        assertDoesNotThrow(() -> runControllerForSeconds(
                new TrafficController(SimulationMode.FIVE_WAY_ROUNDABOUT), 90));
    }

    @Test
    public void fiveWayRoundaboutKeepsVehiclesInMotion() {
        TrafficController controller = new TrafficController(SimulationMode.FIVE_WAY_ROUNDABOUT);
        runControllerForSeconds(controller, 45);

        assertFalse(controller.getVehicles().isEmpty());
        assertFalse(controller.getVehicles().stream().allMatch(vehicle -> vehicle.getSpeed() == 0));
    }

    @Test
    public void roadNetworkRunsForThreeMinutesAndKeepsVehiclesInMotion() {
        TrafficController controller = new TrafficController(SimulationMode.ROAD_NETWORK);
        runControllerForSeconds(controller, 180);

        assertFalse(controller.getVehicles().isEmpty(), "Vehicles list should not be empty");
        long movingCount = controller.getVehicles().stream().filter(v -> v.getSpeed() > 1.0).count();
        assertTrue(movingCount > 0, "There should be moving vehicles. Moving count = " + movingCount + "/" + controller.getVehicles().size());
    }

    @Test
    public void fiveWayRoundaboutCirculatesCounterClockwise() {
        List<Intersection> intersections = List.of(new RoundaboutIntersection("roundabout1", 600.0, 300.0, 100.0));
        List<Vehicle> vehicles = new ArrayList<>();
        Vehicle vehicle = new Car("Car1", 781.0, 260.0, 80.0, Math.PI, 26, 13, false);
        vehicles.add(vehicle);

        for (int i = 0; i < 10 && !vehicle.insideRoundabout; i++) {
            vehicle.update(0.05, vehicles, intersections, 1400, 600);
        }

        assertTrue(vehicle.insideRoundabout);
        assertTrue(vehicle.roundaboutAngle < 0.0);
        assertEquals(-Math.PI / 2.0, vehicle.getDirection(), 0.35);
    }

    @Test
    public void fiveWayRoundaboutExitsOnRedDotLaneOfTargetRoad() throws Exception {
        List<Intersection> intersections = List.of(new RoundaboutIntersection("roundabout1", 600.0, 300.0, 100.0));
        List<Vehicle> vehicles = new ArrayList<>();
        Vehicle vehicle = new Car("Car1", 781.0, 260.0, 80.0, Math.PI, 26, 13, false);
        setTargetExitIndex(vehicle, 1);
        vehicles.add(vehicle);

        double dt = 0.05;
        for (int i = 0; i < 400 && !vehicle.hasTurned(); i++) {
            vehicle.update(dt, vehicles, intersections, 1400, 600);
        }

        assertTrue(vehicle.hasTurned());
        assertEquals(-Math.PI / 2.0, vehicle.getDirection(), 0.01);
        assertTrue(vehicle.getX() > 600.0, "x=" + vehicle.getX() + ", y=" + vehicle.getY());
    }

    @Test
    public void fiveWayRoundaboutApproachIsClampedToPaintedLane() throws Exception {
        List<Intersection> intersections = List.of(new RoundaboutIntersection("roundabout1", 600.0, 300.0, 100.0));
        List<Vehicle> vehicles = new ArrayList<>();
        Vehicle vehicle = new Car("Car1", 770.0, 210.0, 0.0, Math.PI, 26, 13, false);
        setTargetExitIndex(vehicle, 1);
        vehicles.add(vehicle);

        vehicle.update(0.05, vehicles, intersections, 1400, 600);

        assertEquals(770.0, vehicle.getX(), 0.01);
        assertEquals(235.0, vehicle.getY(), 0.01);
    }

    @Test
    public void roadNetworkVehicleBelowRoundaboutDoesNotTurnAtOffAxisCrossIntersection() {
        List<TrafficLight> lights = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            TrafficLight light = new TrafficLight();
            light.forceState(TrafficLight.State.GREEN, 999);
            light.forceLeftTurnState(TrafficLight.State.GREEN, 999);
            lights.add(light);
        }
        List<Intersection> intersections = List.of(
                new CrossIntersection("cross2", 400.0, -500.0, lights),
                new RoundaboutIntersection("roundabout1", 1200.0, -500.0, 100.0,
                        new double[] { 0, -Math.PI / 2, Math.PI, Math.PI / 2, -Math.PI / 4 }));
        Vehicle vehicle = new Car("Car1", 1215.0, 80.0, 80.0, -Math.PI / 2, 26, 13, false);
        vehicle.setTurnIntention(2);
        List<Vehicle> vehicles = new ArrayList<>(List.of(vehicle));

        for (int i = 0; i < 60 && vehicle.getY() > -230.0; i++) {
            vehicle.update(0.05, vehicles, intersections, 1400, 600);
        }

        assertEquals(-Math.PI / 2, vehicle.getDirection(), 0.01);
        assertEquals(1215.0, vehicle.getX(), 0.01);
    }

    @Test
    public void threeWayDiagonalRightTurnLocksOntoExitLane() {
        List<TrafficLight> lights = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TrafficLight light = new TrafficLight();
            light.forceState(TrafficLight.State.GREEN, 999);
            light.forceLeftTurnState(TrafficLight.State.GREEN, 999);
            lights.add(light);
        }
        List<Intersection> intersections = List.of(new ThreeWayIntersection("three1", 1200.0, 300.0, lights));
        Vehicle vehicle = new Car("Car1", 1265.0, 540.0, 80.0, -Math.PI / 2.0, 26, 13, false);
        vehicle.setTurnIntention(2);
        List<Vehicle> vehicles = new ArrayList<>(List.of(vehicle));

        for (int i = 0; i < 120 && !vehicle.hasTurned(); i++) {
            vehicle.update(0.05, vehicles, intersections, 1400, 600);
        }

        assertTrue(vehicle.hasTurned(), "x=" + vehicle.getX() + ", y=" + vehicle.getY());
        assertEquals(0.0, vehicle.getDirection(), 0.01);
        assertEquals(365.0, vehicle.getY(), 0.01);
    }

    @Test
    public void smoothTurningVehicleSlowsBehindVehicleAlreadyOnTurnPath() throws Exception {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle follower = new Car("Follower", 400.0, 340.0, 80.0, -Math.PI / 2.0, 26, 13, false);
        Vehicle leader = new Car("Leader", 400.0, 298.0, 0.0, -Math.PI / 2.0, 26, 13, false);
        setBooleanField(follower, "isTurningSmoothly", true);
        setDoubleField(follower, "smoothTurnStartX", 400.0);
        setDoubleField(follower, "smoothTurnStartY", 340.0);
        setDoubleField(follower, "smoothTurnEndX", 400.0);
        setDoubleField(follower, "smoothTurnEndY", 240.0);
        setDoubleField(follower, "smoothTurnStartDirection", -Math.PI / 2.0);
        setDoubleField(follower, "smoothTurnEndDirection", -Math.PI / 2.0);
        setDoubleField(follower, "smoothTurnDuration", 0.5);
        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, leader));

        follower.update(0.1, vehicles, intersections, 1400, 600);

        assertTrue(follower.getSpeed() > 0.0, "speed=" + follower.getSpeed());
        assertTrue(follower.getSpeed() < 80.0, "speed=" + follower.getSpeed());
        assertEquals(400.0, follower.getX(), 0.01);
        assertTrue(follower.getY() < 340.0, "y=" + follower.getY());
    }

    @Test
    public void smoothTurningVehicleStopsOnlyWhenTooCloseOnTurnPath() throws Exception {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle follower = new Car("Follower", 400.0, 340.0, 80.0, -Math.PI / 2.0, 26, 13, false);
        Vehicle leader = new Car("Leader", 400.0, 318.0, 0.0, -Math.PI / 2.0, 26, 13, false);
        setBooleanField(follower, "isTurningSmoothly", true);
        setDoubleField(follower, "smoothTurnStartX", 400.0);
        setDoubleField(follower, "smoothTurnStartY", 340.0);
        setDoubleField(follower, "smoothTurnEndX", 400.0);
        setDoubleField(follower, "smoothTurnEndY", 240.0);
        setDoubleField(follower, "smoothTurnStartDirection", -Math.PI / 2.0);
        setDoubleField(follower, "smoothTurnEndDirection", -Math.PI / 2.0);
        setDoubleField(follower, "smoothTurnDuration", 0.5);
        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, leader));

        follower.update(0.1, vehicles, intersections, 1400, 600);

        assertEquals(0.0, follower.getSpeed(), 0.01);
        assertEquals(400.0, follower.getX(), 0.01);
        assertEquals(340.0, follower.getY(), 0.01);
    }

    @Test
    public void shortSmoothTurnInsideIntersectionCompletesInsteadOfDeadlocking() throws Exception {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, -500.0, lights));
        Vehicle turning = new Car("Turning", 385.0, -483.6, 80.0, Math.PI / 2.0, 26, 13, false);
        Vehicle waitingBehind = new Car("Behind", 385.0, -516.1, 0.0, Math.PI / 2.0, 26, 13, false);
        turning.setTurnIntention(1);
        waitingBehind.setTurnIntention(1);
        setBooleanField(turning, "passedStopLine", true);
        setStringField(turning, "activeIntersectionId", "cross1");
        setIntField(turning, "activeIntersectionEntryLightIdx", 2);
        setLongField(turning, "activeIntersectionEntryOrder", 1L);
        setBooleanField(turning, "isTurningSmoothly", true);
        setDoubleField(turning, "smoothTurnStartX", 385.0);
        setDoubleField(turning, "smoothTurnStartY", -483.6);
        setDoubleField(turning, "smoothTurnEndX", 385.0);
        setDoubleField(turning, "smoothTurnEndY", -485.0);
        setDoubleField(turning, "smoothTurnStartDirection", Math.PI / 2.0);
        setDoubleField(turning, "smoothTurnEndDirection", 0.0);
        setDoubleField(turning, "smoothTurnElapsed", 0.18);
        setDoubleField(turning, "smoothTurnDuration", 0.38);
        List<Vehicle> vehicles = new ArrayList<>(List.of(turning, waitingBehind));

        for (int i = 0; i < 6 && !turning.hasTurned(); i++) {
            turning.update(0.05, vehicles, intersections, 1400, 600);
        }

        assertTrue(turning.hasTurned(), "speed=" + turning.getSpeed()
                + ", x=" + turning.getX() + ", y=" + turning.getY());
        assertEquals(0.0, turning.getDirection(), 0.01);
    }

    @Test
    public void shortSmoothDiagonalEntryIgnoresCloseVehicleBehindPath() throws Exception {
        Vehicle turning = new Motorbike("Turning", 1425.9, 229.9, 80.0, -2.80, false);
        Vehicle closeBehind = new Motorbike("Behind", 1431.0, 235.5, 0.0, Math.PI, false);
        turning.setTurnIntention(2);
        closeBehind.setTurnIntention(2);
        setBooleanField(turning, "passedStopLine", true);
        setBooleanField(turning, "isTurningDiagonally", true);
        setBooleanField(turning, "isTurningSmoothly", true);
        setStringField(turning, "activeIntersectionId", "three1");
        setIntField(turning, "activeIntersectionEntryLightIdx", 1);
        setLongField(turning, "activeIntersectionEntryOrder", 1L);
        setDoubleField(turning, "smoothTurnStartX", 1431.0);
        setDoubleField(turning, "smoothTurnStartY", 235.0);
        setDoubleField(turning, "smoothTurnEndX", 1419.1771746185611);
        setDoubleField(turning, "smoothTurnEndY", 223.1771746185609);
        setDoubleField(turning, "smoothTurnStartDirection", Math.PI);
        setDoubleField(turning, "smoothTurnEndDirection", -Math.PI * 3.0 / 4.0);
        setDoubleField(turning, "smoothTurnElapsed", 0.10);
        setDoubleField(turning, "smoothTurnDuration", 0.22);
        setDoubleField(turning, "diagonalTurnCenterX", 1200.0);
        setDoubleField(turning, "diagonalTurnCenterY", 300.0);
        List<Vehicle> vehicles = new ArrayList<>(List.of(turning, closeBehind));

        turning.update(0.05, vehicles, List.of(), 1400, 600);

        assertTrue(turning.getSpeed() > 0.0, "speed=" + turning.getSpeed());
        assertTrue(turning.getX() < 1425.9, "x=" + turning.getX());
        assertTrue(turning.getY() < 229.9, "y=" + turning.getY());
    }

    @Test
    public void diagonalRightTurnVehicleSlowsBehindVehicleOnTurnRoad() throws Exception {
        Vehicle follower = new Car("Follower", 300.0, 340.0, 80.0, Math.PI / 4.0, 26, 13, false);
        Vehicle leader = new Car("Leader", 330.0, 370.0, 0.0, Math.PI / 4.0, 26, 13, false);
        follower.setTurnIntention(2);
        leader.setTurnIntention(2);
        setBooleanField(follower, "isTurningDiagonally", true);
        setBooleanField(leader, "isTurningDiagonally", true);
        setDoubleField(follower, "diagonalTurnCenterX", 400.0);
        setDoubleField(follower, "diagonalTurnCenterY", 300.0);
        setDoubleField(leader, "diagonalTurnCenterX", 400.0);
        setDoubleField(leader, "diagonalTurnCenterY", 300.0);
        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, leader));

        follower.update(0.2, vehicles, List.of(), 1400, 600);

        assertTrue(follower.getSpeed() > 0.0, "speed=" + follower.getSpeed());
        assertTrue(follower.getSpeed() < 80.0, "speed=" + follower.getSpeed());
        assertTrue(follower.getX() > 300.0, "x=" + follower.getX());
        assertTrue(follower.getY() > 340.0, "y=" + follower.getY());
    }

    @Test
    public void diagonalRightTurnVehicleStopsOnlyWhenTooCloseOnTurnRoad() throws Exception {
        Vehicle follower = new Car("Follower", 300.0, 340.0, 80.0, Math.PI / 4.0, 26, 13, false);
        Vehicle leader = new Car("Leader", 315.0, 355.0, 0.0, Math.PI / 4.0, 26, 13, false);
        follower.setTurnIntention(2);
        leader.setTurnIntention(2);
        setBooleanField(follower, "isTurningDiagonally", true);
        setBooleanField(leader, "isTurningDiagonally", true);
        setDoubleField(follower, "diagonalTurnCenterX", 400.0);
        setDoubleField(follower, "diagonalTurnCenterY", 300.0);
        setDoubleField(leader, "diagonalTurnCenterX", 400.0);
        setDoubleField(leader, "diagonalTurnCenterY", 300.0);
        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, leader));

        follower.update(0.2, vehicles, List.of(), 1400, 600);

        assertEquals(0.0, follower.getSpeed(), 0.01);
        assertEquals(300.0, follower.getX(), 0.01);
        assertEquals(340.0, follower.getY(), 0.01);
    }

    @Test
    public void diagonalRightTurnVehicleCrawlsBehindSameStreamLeaderWhenGapIsPositive() throws Exception {
        Vehicle follower = new Motorbike("Follower", 483.3, 85.6, 80.0, Math.PI, false);
        Vehicle leader = new Car("Leader", 465.0, 66.2, 0.0, Math.PI, 26, 13, false);
        follower.setTurnIntention(2);
        leader.setTurnIntention(2);
        setDoubleField(follower, "direction", -Math.PI * 3.0 / 4.0);
        setDoubleField(leader, "direction", -Math.PI / 2.0);
        setBooleanField(follower, "passedStopLine", true);
        setBooleanField(follower, "isTurningDiagonally", true);
        setStringField(follower, "activeIntersectionId", "cross1");
        setIntField(follower, "activeIntersectionEntryLightIdx", 1);
        setLongField(follower, "activeIntersectionEntryOrder", 1L);
        setDoubleField(follower, "diagonalTurnCenterX", 400.0);
        setDoubleField(follower, "diagonalTurnCenterY", 300.0);
        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, leader));

        follower.update(0.05, vehicles, List.of(), 1400, 600);

        assertTrue(follower.getSpeed() > 0.0, "speed=" + follower.getSpeed());
        assertTrue(follower.getX() < 483.3, "x=" + follower.getX());
        assertTrue(follower.getY() < 85.6, "y=" + follower.getY());
    }

    @Test
    public void vehicleDoesNotFollowTurnVehicleFromDifferentIntersection() throws Exception {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(
                new CrossIntersection("cross1", 400.0, 300.0, lights),
                new CrossIntersection("cross2", 400.0, -500.0, greenLights(4)));
        Vehicle nextIntersectionVehicle = new Car("Next", 465.0, 66.2, 80.0, Math.PI, 26, 13, false);
        Vehicle oldIntersectionTurner = new Motorbike("Old", 479.1, 81.4, 0.0, Math.PI, false);
        nextIntersectionVehicle.setTurnIntention(2);
        oldIntersectionTurner.setTurnIntention(2);
        setDoubleField(nextIntersectionVehicle, "direction", -Math.PI / 2.0);
        setDoubleField(oldIntersectionTurner, "direction", -Math.PI * 3.0 / 4.0);
        setStringField(nextIntersectionVehicle, "activeIntersectionId", "cross2");
        setIntField(nextIntersectionVehicle, "activeIntersectionEntryLightIdx", 3);
        setStringField(oldIntersectionTurner, "activeIntersectionId", "cross1");
        setIntField(oldIntersectionTurner, "activeIntersectionEntryLightIdx", 1);
        setBooleanField(oldIntersectionTurner, "passedStopLine", true);
        setBooleanField(oldIntersectionTurner, "isTurningDiagonally", true);
        setDoubleField(oldIntersectionTurner, "diagonalTurnCenterX", 400.0);
        setDoubleField(oldIntersectionTurner, "diagonalTurnCenterY", 300.0);
        List<Vehicle> vehicles = new ArrayList<>(List.of(nextIntersectionVehicle, oldIntersectionTurner));

        nextIntersectionVehicle.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(nextIntersectionVehicle.getSpeed() > 0.0, "speed=" + nextIntersectionVehicle.getSpeed());
        assertTrue(nextIntersectionVehicle.getY() < 66.2, "y=" + nextIntersectionVehicle.getY());
    }

    @Test
    public void rightTurnVehicleDoesNotEnterDiagonalBranchWhenEntryIsBlocked() throws Exception {
        List<TrafficLight> lights = greenLights(3);
        List<Intersection> intersections = List.of(new ThreeWayIntersection("three1", 1200.0, 300.0, lights));
        Vehicle turning = new Car("Turning", 1425.0, 235.0, 80.0, Math.PI, 26, 13, false);
        Vehicle stoppedAhead = new Car("Stopped", 1405.0, 235.0, 0.0, Math.PI, 26, 13, false);
        turning.setTurnIntention(2);
        stoppedAhead.setTurnIntention(0);
        List<Vehicle> vehicles = new ArrayList<>(List.of(turning, stoppedAhead));

        turning.update(0.05, vehicles, intersections, 1400, 600);

        assertFalse(getBooleanField(turning, "isTurningDiagonally"));
        assertEquals(Math.PI, turning.getDirection(), 0.01);
    }

    @Test
    public void rightTurnVehicleDoesNotEnterDiagonalBranchWhenTurnPathIsBlocked() throws Exception {
        List<TrafficLight> lights = greenLights(3);
        List<Intersection> intersections = List.of(new ThreeWayIntersection("three1", 1200.0, 300.0, lights));
        Vehicle turning = new Car("Turning", 1425.0, 235.0, 80.0, Math.PI, 26, 13, false);
        Vehicle stoppedOnBranch = new Car("StoppedBranch", 1415.0, 245.0, 0.0, 0.0, 26, 13, false);
        turning.setTurnIntention(2);
        stoppedOnBranch.setTurnIntention(0);
        List<Vehicle> vehicles = new ArrayList<>(List.of(turning, stoppedOnBranch));

        turning.update(0.05, vehicles, intersections, 1400, 600);

        assertFalse(getBooleanField(turning, "isTurningDiagonally"));
        assertEquals(0.0, turning.getSpeed(), 0.01);
        assertEquals(Math.PI, turning.getDirection(), 0.01);
    }

    @Test
    public void diagonalRightTurnVehicleCrawlsPastDifferentStreamBlockerInsideIntersection() throws Exception {
        Vehicle turning = new Car("Turning", 1414.7, 246.8, 80.0, -Math.PI * 3.0 / 4.0, 26, 13, false);
        Vehicle stoppedDifferentStream = new Car("Stopped", 1416.4, 235.0, 0.0, Math.PI, 26, 13, false);
        turning.setTurnIntention(2);
        stoppedDifferentStream.setTurnIntention(0);
        setBooleanField(turning, "passedStopLine", true);
        setBooleanField(turning, "isTurningDiagonally", true);
        setStringField(turning, "activeIntersectionId", "three1");
        setIntField(turning, "activeIntersectionEntryLightIdx", 1);
        setLongField(turning, "activeIntersectionEntryOrder", 1L);
        setStringField(stoppedDifferentStream, "activeIntersectionId", "three1");
        setIntField(stoppedDifferentStream, "activeIntersectionEntryLightIdx", 1);
        setLongField(stoppedDifferentStream, "activeIntersectionEntryOrder", 2L);
        setDoubleField(turning, "diagonalTurnCenterX", 1200.0);
        setDoubleField(turning, "diagonalTurnCenterY", 300.0);
        List<Vehicle> vehicles = new ArrayList<>(List.of(turning, stoppedDifferentStream));

        turning.update(0.05, vehicles, List.of(), 1400, 600);

        assertTrue(turning.getSpeed() > 0.0, "speed=" + turning.getSpeed());
        assertTrue(turning.getX() < 1414.7, "x=" + turning.getX());
        assertTrue(turning.getY() < 246.8, "y=" + turning.getY());
    }

    @Test
    public void vehicleLeavingTurnKeepsMovingPastOldIntersectionConflict() throws Exception {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle exiting = new Car("Exit", 390.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle crossTraffic = new Car("Cross", 390.0, 350.0, 0.0, Math.PI / 2.0, 26, 13, false);
        setBooleanField(exiting, "hasTurned", true);
        setDoubleField(exiting, "turnExitClearanceTime", 0.6);
        List<Vehicle> vehicles = new ArrayList<>(List.of(exiting, crossTraffic));

        exiting.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(exiting.getSpeed() > 0.0, "speed=" + exiting.getSpeed());
        assertTrue(exiting.getX() > 390.0, "x=" + exiting.getX());
    }

    @Test
    public void vehicleLeavingTurnKeepsCrawlingBehindStoppedVehicleWithSafeGap() throws Exception {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle exiting = new Car("Exit", 390.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle leader = new Car("Leader", 440.0, 340.0, 0.0, 0.0, 26, 13, false);
        setBooleanField(exiting, "hasTurned", true);
        setDoubleField(exiting, "turnExitClearanceTime", 0.6);
        List<Vehicle> vehicles = new ArrayList<>(List.of(exiting, leader));

        exiting.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(exiting.getSpeed() > 0.0, "speed=" + exiting.getSpeed());
        assertTrue(exiting.getX() > 390.0, "x=" + exiting.getX());
    }

    @Test
    public void threeWayBranchMergeCrawlsWhenGapIsSmallButPositive() throws Exception {
        List<TrafficLight> lights = greenLights(3);
        List<Intersection> intersections = List.of(new ThreeWayIntersection("three1", 1200.0, 300.0, lights));
        Vehicle merging = new Car("Merge", 1190.0, 315.0, 80.0, 0.0, 26, 13, false);
        Vehicle leader = new Car("Leader", 1222.0, 315.0, 0.0, 0.0, 26, 13, false);
        setBooleanField(merging, "hasTurned", true);
        setDoubleField(merging, "turnExitClearanceTime", 0.6);
        List<Vehicle> vehicles = new ArrayList<>(List.of(merging, leader));

        merging.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(merging.getSpeed() > 0.0, "speed=" + merging.getSpeed());
        assertTrue(merging.getX() > 1190.0, "x=" + merging.getX());
    }

    @Test
    public void vehicleLeavingTurnStillStopsWhenVehicleAheadTooClose() throws Exception {
        List<TrafficLight> lights = greenLights(4);
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle exiting = new Car("Exit", 390.0, 340.0, 80.0, 0.0, 26, 13, false);
        Vehicle leader = new Car("Leader", 415.0, 340.0, 0.0, 0.0, 26, 13, false);
        setBooleanField(exiting, "hasTurned", true);
        setDoubleField(exiting, "turnExitClearanceTime", 0.6);
        List<Vehicle> vehicles = new ArrayList<>(List.of(exiting, leader));

        exiting.update(0.05, vehicles, intersections, 1400, 600);

        assertEquals(0.0, exiting.getSpeed(), 0.01);
        assertEquals(390.0, exiting.getX(), 0.01);
    }

    @Test
    public void roadNetworkVehicleLeavingRoundaboutDoesNotGoStraightThroughThreeWayMissingRoad() throws Exception {
        List<TrafficLight> lights = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TrafficLight light = new TrafficLight();
            light.forceState(TrafficLight.State.GREEN, 999);
            light.forceLeftTurnState(TrafficLight.State.GREEN, 999);
            lights.add(light);
        }
        List<Intersection> intersections = List.of(new ThreeWayIntersection("three1", 1200.0, 300.0, lights));
        Vehicle vehicle = new Car("Car1", 1215.0, 120.0, 80.0, Math.PI / 2.0, 26, 13, false);
        vehicle.setTurnIntention(0);
        setBooleanField(vehicle, "hasTurned", true);
        setBooleanField(vehicle, "exitedRoundabout", true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(vehicle));

        for (int i = 0; i < 160 && Math.abs(vehicle.getDirection() - Math.PI / 2.0) < 0.01; i++) {
            vehicle.update(0.05, vehicles, intersections, 1400, 600);
        }

        assertNotEquals(Math.PI / 2.0, vehicle.getDirection(), 0.01);
        assertTrue(vehicle.getY() < 380.0, "x=" + vehicle.getX() + ", y=" + vehicle.getY());
    }

    @Test
    public void fiveWayRoundaboutKeepsCirculatingVehicleInsideRoadBand() throws Exception {
        List<Intersection> intersections = List.of(new RoundaboutIntersection("roundabout1", 600.0, 300.0, 100.0));
        List<Vehicle> vehicles = new ArrayList<>();
        Vehicle vehicle = new Car("Car1", 850.0, 300.0, 80.0, Math.PI, 26, 13, false);
        vehicle.insideRoundabout = true;
        vehicle.roundaboutAngle = 0.0;
        setTargetExitIndex(vehicle, 1);
        vehicles.add(vehicle);

        vehicle.update(0.05, vehicles, intersections, 1400, 600);

        double radius = Math.hypot(vehicle.getX() - 600.0, vehicle.getY() - 300.0);
        assertTrue(radius >= 112.0 && radius <= 166.0, "radius=" + radius);
    }

    @Test
    public void fiveWayRoundaboutStopsForCloseVehicleAhead() throws Exception {
        double cx = 600.0;
        double cy = 300.0;
        double radius = 140.0;
        List<Intersection> intersections = List.of(new RoundaboutIntersection("roundabout1", cx, cy, 100.0));
        Vehicle follower = new Car("Follower", cx + radius, cy, 80.0, -Math.PI / 2.0, 26, 13, false);
        follower.insideRoundabout = true;
        follower.roundaboutAngle = 0.0;
        setTargetExitIndex(follower, 1);

        double leaderAngle = -0.12;
        Vehicle leader = new Car("Leader",
                cx + radius * Math.cos(leaderAngle),
                cy + radius * Math.sin(leaderAngle),
                0.0,
                -Math.PI / 2.0,
                26,
                13,
                false);
        leader.insideRoundabout = true;
        leader.roundaboutAngle = leaderAngle;
        setTargetExitIndex(leader, 1);

        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, leader));

        follower.update(0.05, vehicles, intersections, 1400, 600);

        assertEquals(0.0, follower.getSpeed(), 0.01);
        assertEquals(0.0, follower.roundaboutAngle, 0.01);
    }

    @Test
    public void fiveWayRoundaboutCrawlsForSmallPositiveGapAhead() throws Exception {
        double cx = 600.0;
        double cy = 300.0;
        double radius = 140.0;
        List<Intersection> intersections = List.of(new RoundaboutIntersection("roundabout1", cx, cy, 100.0));
        Vehicle follower = new Car("Follower", cx + radius, cy, 80.0, -Math.PI / 2.0, 26, 13, false);
        follower.insideRoundabout = true;
        follower.roundaboutAngle = 0.0;
        setTargetExitIndex(follower, 1);

        double leaderAngle = -0.22;
        Vehicle leader = new Car("Leader",
                cx + radius * Math.cos(leaderAngle),
                cy + radius * Math.sin(leaderAngle),
                0.0,
                -Math.PI / 2.0,
                26,
                13,
                false);
        leader.insideRoundabout = true;
        leader.roundaboutAngle = leaderAngle;
        setTargetExitIndex(leader, 1);

        List<Vehicle> vehicles = new ArrayList<>(List.of(follower, leader));

        follower.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(follower.getSpeed() > 0.0, "speed=" + follower.getSpeed());
        assertTrue(follower.roundaboutAngle < 0.0, "angle=" + follower.roundaboutAngle);
    }

    @Test
    public void fiveWayRoundaboutCapturesVehicleBeforeItCrossesGreenIsland() throws Exception {
        List<Intersection> intersections = List.of(new RoundaboutIntersection("roundabout1", 600.0, 300.0, 100.0));
        List<Vehicle> vehicles = new ArrayList<>();
        Vehicle vehicle = new Car("Car1", 560.0, 300.0, 80.0, 0.0, 26, 13, false);
        setTargetExitIndex(vehicle, 0);
        vehicles.add(vehicle);

        vehicle.update(0.05, vehicles, intersections, 1400, 600);

        double radius = Math.hypot(vehicle.getX() - 600.0, vehicle.getY() - 300.0);
        assertTrue(vehicle.insideRoundabout);
        assertTrue(radius >= 112.0, "radius=" + radius);
        assertNotEquals(0.0, vehicle.getDirection(), 0.01);
    }

    @Test
    public void fiveWayRoundaboutApproachOvershootCannotCrossGreenIsland() throws Exception {
        List<Intersection> intersections = List.of(new RoundaboutIntersection("roundabout1", 600.0, 300.0, 100.0));
        List<Vehicle> vehicles = new ArrayList<>();
        Vehicle vehicle = new Car("Car1", 785.0, 285.0, 120.0, Math.PI, 26, 13, false);
        setTargetExitIndex(vehicle, 2);
        vehicles.add(vehicle);

        vehicle.update(1.0, vehicles, intersections, 1400, 600);

        double radius = Math.hypot(vehicle.getX() - 600.0, vehicle.getY() - 300.0);
        assertTrue(vehicle.insideRoundabout, "vehicle should be captured by roundabout");
        assertTrue(radius >= 112.0 && radius <= 166.0, "radius=" + radius);
        assertNotEquals(Math.PI, vehicle.getDirection(), 0.01);
    }

    @Test
    public void fiveWayRoundaboutEntryDoesNotTeleportAtMergePoint() throws Exception {
        List<Intersection> intersections = List.of(new RoundaboutIntersection("roundabout1", 600.0, 300.0, 100.0));
        List<Vehicle> vehicles = new ArrayList<>();
        Vehicle vehicle = new Car("Car1", 790.0, 235.0, 80.0, Math.PI, 26, 13, false);
        setTargetExitIndex(vehicle, 1);
        vehicles.add(vehicle);

        double maxStep = 0.0;
        double previousX = vehicle.getX();
        double previousY = vehicle.getY();
        for (int i = 0; i < 20 && !vehicle.insideRoundabout; i++) {
            vehicle.update(0.05, vehicles, intersections, 1400, 600);
            maxStep = Math.max(maxStep, Math.hypot(vehicle.getX() - previousX, vehicle.getY() - previousY));
            previousX = vehicle.getX();
            previousY = vehicle.getY();
        }

        assertTrue(vehicle.insideRoundabout);
        assertTrue(maxStep < 15.0, "maxStep=" + maxStep);
    }

    @Test
    public void roundaboutEntryDoesNotYieldToPriorityBehindSameApproach() throws Exception {
        List<Intersection> intersections = List.of(new RoundaboutIntersection("roundabout1", 600.0, 300.0, 100.0));
        Vehicle entering = new Bus("Bus", 782.6, 235.0, 80.0, Math.PI);
        Vehicle priorityBehind = new Ambulance("Amb", 830.3, 260.0, 0.0, Math.PI, true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(entering, priorityBehind));

        entering.update(0.05, vehicles, intersections, 1400, 600);

        assertTrue(entering.getSpeed() > 0.0, "speed=" + entering.getSpeed());
        assertTrue(entering.getX() < 782.6, "x=" + entering.getX());
    }

    @Test
    public void roundaboutExitShortSmoothIgnoresCloseVehicleBehindPath() throws Exception {
        Vehicle exiting = new Car("Exit", 753.2, 236.1, 80.0, -1.80, 26, 13, false);
        Vehicle behind = new Car("Behind", 749.1, 227.0, 0.0, -1.40, 26, 13, false);
        setBooleanField(exiting, "exitedRoundabout", true);
        setBooleanField(exiting, "isTurningSmoothly", true);
        setDoubleField(exiting, "smoothTurnStartX", 752.9757485084084);
        setDoubleField(exiting, "smoothTurnStartY", 235.7434757679132);
        setDoubleField(exiting, "smoothTurnEndX", 753.9688849983191);
        setDoubleField(exiting, "smoothTurnEndY", 237.95499655593213);
        setDoubleField(exiting, "smoothTurnStartDirection", -1.80);
        setDoubleField(exiting, "smoothTurnEndDirection", -Math.PI / 4.0);
        setDoubleField(exiting, "smoothTurnElapsed", 0.15);
        setDoubleField(exiting, "smoothTurnDuration", 0.55);
        setBooleanField(behind, "exitedRoundabout", true);
        setBooleanField(behind, "isTurningSmoothly", true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(exiting, behind));

        exiting.update(0.05, vehicles, List.of(), 1400, 600);

        assertTrue(exiting.getSpeed() > 0.0, "speed=" + exiting.getSpeed());
        assertTrue(exiting.getY() > 236.1, "y=" + exiting.getY());
    }

    @Test
    public void roundaboutExitSmoothVehicleWithMoreProgressClearsOverlap() throws Exception {
        Vehicle exiting = new Motorbike("Exit", 662.3, 146.2, 80.0, -2.71, false);
        Vehicle closeOverlap = new Car("Overlap", 668.8, 154.3, 0.0, -2.19, 26, 13, false);
        setBooleanField(exiting, "exitedRoundabout", true);
        setBooleanField(exiting, "isTurningSmoothly", true);
        setDoubleField(exiting, "smoothTurnStartX", 662.0321164808353);
        setDoubleField(exiting, "smoothTurnStartY", 146.10881421275408);
        setDoubleField(exiting, "smoothTurnEndX", 665.0);
        setDoubleField(exiting, "smoothTurnEndY", 147.2551146519138);
        setDoubleField(exiting, "smoothTurnStartDirection", -2.71);
        setDoubleField(exiting, "smoothTurnEndDirection", -Math.PI / 2.0);
        setDoubleField(exiting, "smoothTurnElapsed", 0.50);
        setDoubleField(exiting, "smoothTurnDuration", 0.55);
        setBooleanField(closeOverlap, "exitedRoundabout", true);
        setBooleanField(closeOverlap, "isTurningSmoothly", true);
        setDoubleField(closeOverlap, "smoothTurnElapsed", 0.10);
        setDoubleField(closeOverlap, "smoothTurnDuration", 0.55);
        List<Vehicle> vehicles = new ArrayList<>(List.of(exiting, closeOverlap));

        exiting.update(0.05, vehicles, List.of(), 1400, 600);

        assertTrue(exiting.getSpeed() > 0.0, "speed=" + exiting.getSpeed());
        assertTrue(exiting.hasTurned(), "exiting vehicle should finish clearing the exit lane");
    }

    @Test
    public void fiveWayRoundaboutExitDoesNotTeleportToRoadLane() throws Exception {
        List<Intersection> intersections = List.of(new RoundaboutIntersection("roundabout1", 600.0, 300.0, 100.0));
        List<Vehicle> vehicles = new ArrayList<>();
        Vehicle vehicle = new Car("Car1", 781.0, 260.0, 80.0, Math.PI, 26, 13, false);
        setTargetExitIndex(vehicle, 1);
        vehicles.add(vehicle);

        double maxStep = 0.0;
        double previousX = vehicle.getX();
        double previousY = vehicle.getY();
        for (int i = 0; i < 400 && !vehicle.hasTurned(); i++) {
            vehicle.update(0.05, vehicles, intersections, 1400, 600);
            maxStep = Math.max(maxStep, Math.hypot(vehicle.getX() - previousX, vehicle.getY() - previousY));
            previousX = vehicle.getX();
            previousY = vehicle.getY();
        }

        assertTrue(vehicle.hasTurned());
        assertTrue(maxStep < 20.0, "maxStep=" + maxStep);
    }

    @Test
    public void yieldingToEmergencyVehicleStaysInsideRoadWidth() {
        List<TrafficLight> lights = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            TrafficLight light = new TrafficLight();
            light.forceState(TrafficLight.State.GREEN, 999);
            light.forceLeftTurnState(TrafficLight.State.GREEN, 999);
            lights.add(light);
        }
        List<Intersection> intersections = List.of(new CrossIntersection("cross1", 400.0, 300.0, lights));
        Vehicle car = new Car("Car1", 120.0, 360.0, 80.0, 0.0, 26, 13, false);
        Vehicle ambulance = new Ambulance("Amb1", 80.0, 360.0, 80.0, 0.0, true);
        List<Vehicle> vehicles = new ArrayList<>(List.of(car, ambulance));

        car.update(1.0, vehicles, intersections, 1400, 600);

        assertTrue(car.getY() <= 300.0 + 80.0 - car.getHeight() / 2.0 + 0.01, "y=" + car.getY());
    }

    @Test
    public void roadNetworkRoundaboutUsesSameEntryAndExitLaneLogicAfterPreviousTurn() throws Exception {
        double[] roadNetworkAngles = new double[] {
                0,
                -Math.PI / 2,
                Math.PI,
                Math.PI / 2,
                -Math.PI / 4
        };
        List<Intersection> intersections = List.of(
                new RoundaboutIntersection("roundabout1", 1200.0, -500.0, 100.0, roadNetworkAngles));
        List<Vehicle> vehicles = new ArrayList<>();
        Vehicle vehicle = new Car("Car1", 1185.0, -1050.0, 80.0, Math.PI / 2.0, 26, 13, false);
        setTargetExitIndex(vehicle, 0);
        setBooleanField(vehicle, "hasTurned", true);
        vehicles.add(vehicle);

        double dt = 0.05;
        for (int i = 0; i < 500 && vehicle.getDirection() != 0.0; i++) {
            vehicle.update(dt, vehicles, intersections, 1400, 600);
        }

        assertEquals(0.0, vehicle.getDirection(), 0.01);
        assertTrue(vehicle.getX() > 1200.0, "x=" + vehicle.getX() + ", y=" + vehicle.getY());
        assertTrue(vehicle.getY() > -500.0, "x=" + vehicle.getX() + ", y=" + vehicle.getY());
    }

    @Test
    public void roadNetworkRoundaboutRecapturesExitedVehicleBeforeCrossingGreenIsland() throws Exception {
        double[] roadNetworkAngles = new double[] {
                0,
                -Math.PI / 2,
                Math.PI,
                Math.PI / 2,
                -Math.PI / 4
        };
        RoundaboutIntersection roundabout =
                new RoundaboutIntersection("roundabout1", 1200.0, -500.0, 100.0, roadNetworkAngles);
        List<Intersection> intersections = List.of(
                new CrossIntersection("cross2", 400.0, -500.0, greenLights(4)),
                new ThreeWayIntersection("three1", 1200.0, 300.0, greenLights(3)),
                roundabout);
        Vehicle vehicle = new Motorbike("Bike", 1215.0, -502.0, 80.0, Math.PI, false);
        setBooleanField(vehicle, "exitedRoundabout", true);
        setBooleanField(vehicle, "hasTurned", true);
        setIntField(vehicle, "spawnSourceIndex", 3);
        setTargetExitIndex(vehicle, 2);
        List<Vehicle> vehicles = new ArrayList<>(List.of(vehicle));

        vehicle.update(0.05, vehicles, intersections, 1400, 600);

        double radius = Math.hypot(vehicle.getX() - roundabout.getX(), vehicle.getY() - roundabout.getY());
        assertTrue(vehicle.insideRoundabout, "vehicle should be re-captured by roundabout");
        assertFalse(getBooleanField(vehicle, "exitedRoundabout"));
        assertTrue(radius >= 112.0, "radius=" + radius);
    }

    private int turnIntention(Object spawnPoint) throws Exception {
        Method turnIntention = spawnPoint.getClass().getDeclaredMethod("turnIntention");
        turnIntention.setAccessible(true);
        return (Integer) turnIntention.invoke(spawnPoint);
    }

    private void setTargetExitIndex(Vehicle vehicle, int exitIndex) throws Exception {
        Field targetExitIndex = Vehicle.class.getDeclaredField("targetExitIndex");
        targetExitIndex.setAccessible(true);
        targetExitIndex.setInt(vehicle, exitIndex);
    }

    private void setBooleanField(Vehicle vehicle, String fieldName, boolean value) throws Exception {
        Field field = Vehicle.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(vehicle, value);
    }

    private boolean getBooleanField(Vehicle vehicle, String fieldName) throws Exception {
        Field field = Vehicle.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(vehicle);
    }

    private void setDoubleField(Vehicle vehicle, String fieldName, double value) throws Exception {
        Field field = Vehicle.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(vehicle, value);
    }

    private void setIntField(Vehicle vehicle, String fieldName, int value) throws Exception {
        Field field = Vehicle.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(vehicle, value);
    }

    private void setLongField(Vehicle vehicle, String fieldName, long value) throws Exception {
        Field field = Vehicle.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setLong(vehicle, value);
    }

    private void setStringField(Vehicle vehicle, String fieldName, String value) throws Exception {
        Field field = Vehicle.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(vehicle, value);
    }

    private List<TrafficLight> greenLights(int count) {
        List<TrafficLight> lights = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TrafficLight light = new TrafficLight();
            light.forceState(TrafficLight.State.GREEN, 999);
            light.forceLeftTurnState(TrafficLight.State.GREEN, 999);
            lights.add(light);
        }
        return lights;
    }

    private List<TrafficLight> redLights(int count) {
        List<TrafficLight> lights = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TrafficLight light = new TrafficLight();
            light.forceState(TrafficLight.State.RED, 999);
            light.forceLeftTurnState(TrafficLight.State.RED, 999);
            lights.add(light);
        }
        return lights;
    }

    private List<TrafficLight> greenStraightRedLeftLights(int count) {
        List<TrafficLight> lights = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TrafficLight light = new TrafficLight();
            light.forceState(TrafficLight.State.GREEN, 999);
            light.forceLeftTurnState(TrafficLight.State.RED, 999);
            lights.add(light);
        }
        return lights;
    }

    private Vehicle runThreeWayVehicle(String id, double x, double y, double direction, int turnIntention,
            double seconds) {
        List<TrafficLight> lights = greenLights(3);
        List<Intersection> intersections = List.of(new ThreeWayIntersection("three1", 1200.0, 300.0, lights));
        List<Vehicle> vehicles = new ArrayList<>();
        Vehicle vehicle = new Car(id, x, y, 80.0, direction, 26, 13, false);
        vehicle.setTurnIntention(turnIntention);
        vehicles.add(vehicle);

        double dt = 0.05;
        for (int i = 0; i < seconds / dt; i++) {
            vehicle.update(dt, vehicles, intersections, 1400, 600);
        }
        return vehicle;
    }

    private void runControllerForSeconds(TrafficController controller, int seconds) {
        double dt = 0.05;
        for (int i = 0; i < seconds / dt; i++) {
            controller.update(dt);
        }
    }
}
