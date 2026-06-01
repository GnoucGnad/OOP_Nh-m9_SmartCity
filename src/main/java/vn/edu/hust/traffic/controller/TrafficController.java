package vn.edu.hust.traffic.controller;

import vn.edu.hust.traffic.model.map.CrossIntersection;
import vn.edu.hust.traffic.model.map.Intersection;
import vn.edu.hust.traffic.model.map.ThreeWayIntersection;
import vn.edu.hust.traffic.model.map.TrafficLight;
import vn.edu.hust.traffic.model.vehicle.Ambulance;
import vn.edu.hust.traffic.model.vehicle.Bus;
import vn.edu.hust.traffic.model.vehicle.Car;
import vn.edu.hust.traffic.model.vehicle.FireTruck;
import vn.edu.hust.traffic.model.vehicle.Motorbike;
import vn.edu.hust.traffic.model.vehicle.Vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TrafficController {
    private static final int NETWORK_WIDTH = 1400;
    private static final int CROSS_WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int THREE_WAY_WORLD_MIN_X = 600;

    public static final double CROSS_X = 400.0;
    public static final double THREE_WAY_X = 1200.0;
    public static final double TOP_CROSS_Y = -500.0;
    public static final double BOTTOM_CROSS_Y = 300.0;

    private static final double LANE_PRIORITY = 15;
    private static final double LANE_CAR = 40;
    private static final double LANE_BIKE = 65;
    private static final int LOW_DENSITY_MAX_VEHICLES = 20;
    private static final int MEDIUM_DENSITY_MAX_VEHICLES = 30;
    private static final int HIGH_DENSITY_MAX_VEHICLES = 40;
    private static final int VEHICLE_TYPE_ROLLS = 200;
    private static final int FIRE_TRUCK_ROLLS = 5;     // 5/200 = 1/40 vehicles
    private static final int AMBULANCE_ROLLS = 10;     // 10/200 = 1/20 vehicles
    private static final int BUS_ROLLS = 20;           // 20/200 = 10%
    private static final int CAR_ROLLS = 60;           // 60/200 = 30%

    private final SimulationMode mode;
    private final int worldMinX;
    private final int worldMaxX;

    private final double[] spawnTimers = new double[] { 0, 0, 0, 0, 0 };
    private final Random random = new Random();

    private int vehicleCount = 0;
    private List<Intersection> intersections;
    private List<TrafficLight> lights1;
    private List<TrafficLight> lights2;
    private List<TrafficLight> lights3;
    private List<Vehicle> vehicles;

    private IntersectionPhaseController phaseController1;
    private ThreeWayPhaseController phaseController2;
    private IntersectionPhaseController phaseController3;
    private boolean autoSpawnEnabled = true;
    private boolean autoMode = true;
    private int trafficDensity = 2;

    public TrafficController() {
        this(SimulationMode.ROAD_NETWORK);
    }

    public TrafficController(SimulationMode mode) {
        this.mode = mode == null ? SimulationMode.ROAD_NETWORK : mode;
        this.worldMinX = this.mode == SimulationMode.THREE_WAY_INTERSECTION ? THREE_WAY_WORLD_MIN_X : 0;
        this.worldMaxX = this.mode == SimulationMode.CROSS_INTERSECTION ? CROSS_WIDTH : NETWORK_WIDTH;
        setupSimulation();
    }

    private void setupSimulation() {
        intersections = new ArrayList<>();
        lights1 = new ArrayList<>();
        lights2 = new ArrayList<>();
        lights3 = new ArrayList<>();
        vehicles = new ArrayList<>();
        phaseController1 = null;
        phaseController2 = null;
        phaseController3 = null;

        if (mode == SimulationMode.CROSS_INTERSECTION || mode == SimulationMode.ROAD_NETWORK) {
            for (int i = 0; i < 4; i++) {
                lights1.add(new TrafficLight());
            }
            phaseController1 = new IntersectionPhaseController(lights1);
            intersections.add(new CrossIntersection("cross1", CROSS_X, BOTTOM_CROSS_Y, lights1));

            for (int i = 0; i < 4; i++) {
                lights3.add(new TrafficLight());
            }
            phaseController3 = new IntersectionPhaseController(lights3);
            intersections.add(new CrossIntersection("cross2", CROSS_X, TOP_CROSS_Y, lights3));
        }

        if (mode == SimulationMode.THREE_WAY_INTERSECTION || mode == SimulationMode.ROAD_NETWORK) {
            for (int i = 0; i < 3; i++) {
                lights2.add(new TrafficLight());
            }
            phaseController2 = new ThreeWayPhaseController(lights2);
            double threeWayY = BOTTOM_CROSS_Y;
            intersections.add(new ThreeWayIntersection("three1", THREE_WAY_X, threeWayY, lights2));
        }

        if (mode == SimulationMode.ROAD_NETWORK) {
            double[] roadNetworkAngles = new double[] {
                0,              // 0: Đông (toward three1)
                -Math.PI / 2,   // 1: Bắc (from top vertical road)
                Math.PI,        // 2: Tây (from cross2)
                Math.PI / 2,    // 3: Nam (toward bottom)
                -Math.PI / 4    // 4: Dong Bac
            };
            intersections.add(new vn.edu.hust.traffic.model.map.RoundaboutIntersection("roundabout1", THREE_WAY_X, TOP_CROSS_Y, 100.0, roadNetworkAngles));
        }

        if (mode == SimulationMode.FIVE_WAY_ROUNDABOUT) {
            intersections.add(new vn.edu.hust.traffic.model.map.RoundaboutIntersection("roundabout1", 600.0, BOTTOM_CROSS_Y, 100.0));
        }
    }

    public void update(double dt) {
        if (autoMode) {
            if (phaseController1 != null) {
                phaseController1.update(dt, vehicles, intersections);
            }
            if (phaseController2 != null) {
                phaseController2.update(dt, vehicles, intersections);
            }
            if (phaseController3 != null) {
                phaseController3.update(dt, vehicles, intersections);
            }
        } else {
            for (TrafficLight light : getLights()) {
                light.update(dt);
            }
        }

        if (autoSpawnEnabled) {
            for (int sourceIdx : activeSourceIndices()) {
                spawnTimers[sourceIdx] += dt;
                if (spawnTimers[sourceIdx] >= 5.0 && canSpawnMoreVehicles()) {
                    spawnTimers[sourceIdx] = 0;
                    spawnVehicle(sourceIdx);
                }
            }
        }

        for (Vehicle v : vehicles) {
            v.update(dt, vehicles, intersections, worldMaxX, HEIGHT);
        }

        vehicles.removeIf(v -> v.getX() < worldMinX - 200 || v.getX() > worldMaxX + 200
                || v.getY() < TOP_CROSS_Y - 400 || v.getY() > HEIGHT + 400);

        for (Intersection inter : intersections) {
            inter.update();
        }
    }

    private void spawnVehicle(int sourceIdx) {
        if (!canSpawnMoreVehicles()) {
            return;
        }
        double speed = 60 + random.nextInt(40);
        SpawnPoint spawnPoint = createSpawnPoint(sourceIdx);
        if (spawnPoint == null) {
            return;
        }

        vehicleCount++;
        Vehicle v = randomVehicle(spawnPoint.x, spawnPoint.y, speed, spawnPoint.direction);
        v.setTurnIntention(spawnPoint.turnIntention);
        vehicles.add(v);
    }

    public void spawnVehicleManually(String typeStr) {
        int[] sources = activeSourceIndices();
        int dirIdx = sources[random.nextInt(sources.length)];
        double speed = 60 + random.nextInt(40);
        SpawnPoint spawnPoint = createSpawnPoint(dirIdx);
        if (spawnPoint == null) {
            return;
        }

        vehicleCount++;
        Vehicle v = manualVehicle(typeStr, spawnPoint.x, spawnPoint.y, speed, spawnPoint.direction);
        if (v != null) {
            v.setTurnIntention(spawnPoint.turnIntention);
            vehicles.add(v);
        }
    }

    private SpawnPoint createSpawnPoint(int sourceIdx) {
        int turnIntention = randomTurnIntention(sourceIdx);
        turnIntention = normalizeTurnIntentionForSource(sourceIdx, turnIntention);
        double offset = laneOffset(turnIntention);
        double x;
        double y;
        double direction;

        if (mode == SimulationMode.FIVE_WAY_ROUNDABOUT) {
            double cx = 600.0;
            double cy = 300.0;
            double d = 550.0;
            double theta = 0;
            switch (sourceIdx) {
                case 0: theta = 0; break;
                case 1: theta = -Math.PI / 2.0; break;
                case 2: theta = Math.PI; break;
                case 3: theta = Math.PI / 2.0; break;
                case 4: theta = -Math.PI / 4.0; break;
            }
            x = cx + d * Math.cos(theta) + offset * Math.sin(theta);
            y = cy + d * Math.sin(theta) - offset * Math.cos(theta);
            direction = theta + Math.PI;
            return new SpawnPoint(x, y, direction, turnIntention);
        }

        switch (sourceIdx) {
            case 0:
                x = worldMinX - 50;
                y = (mode == SimulationMode.CROSS_INTERSECTION || mode == SimulationMode.ROAD_NETWORK ? (random.nextBoolean() ? TOP_CROSS_Y : BOTTOM_CROSS_Y) : (HEIGHT / 2.0)) + offset;
                direction = 0;
                break;
            case 1:
                double spawnY = (mode == SimulationMode.CROSS_INTERSECTION || mode == SimulationMode.ROAD_NETWORK) ? (random.nextBoolean() ? TOP_CROSS_Y : BOTTOM_CROSS_Y) : (HEIGHT / 2.0);
                if (spawnY == TOP_CROSS_Y) {
                    x = worldMaxX + 50.0;
                } else {
                    x = worldMaxX + 50.0;
                }
                y = spawnY - offset;
                direction = Math.PI;
                break;
            case 2:
                x = CROSS_X - offset;
                y = TOP_CROSS_Y - 250;
                direction = Math.PI / 2;
                break;
            case 3:
                x = CROSS_X + offset;
                y = 650;
                direction = -Math.PI / 2;
                break;
            case 4:
                double northSpawnY;
                if (mode == SimulationMode.ROAD_NETWORK) {
                    northSpawnY = TOP_CROSS_Y - 500;
                } else {
                    northSpawnY = -610;
                }
                x = THREE_WAY_X - offset;
                y = northSpawnY;
                direction = Math.PI / 2;
                break;
            default:
                return null;
        }
        return new SpawnPoint(x, y, direction, turnIntention);
    }

    private int randomTurnIntention(int sourceIdx) {
        int turnRand = random.nextInt(10);
        int turnIntention = 0;
        if (turnRand < 2) {
            turnIntention = 1;
        } else if (turnRand < 4) {
            turnIntention = 2;
        }

        return turnIntention;
    }

    private int normalizeTurnIntentionForSource(int sourceIdx, int turnIntention) {
        if (mode == SimulationMode.THREE_WAY_INTERSECTION) {
            if (sourceIdx == 0 && turnIntention == 2) {
                return random.nextBoolean() ? 0 : 1;
            }
            if (sourceIdx == 1 && turnIntention == 1) {
                return random.nextBoolean() ? 0 : 2;
            }
        }

        if (sourceIdx == 4 && turnIntention == 0) {
            return random.nextBoolean() ? 1 : 2;
        }
        return turnIntention;
    }

    private double laneOffset(int turnIntention) {
        if (turnIntention == 1) {
            return LANE_PRIORITY;
        }
        if (turnIntention == 2) {
            return LANE_BIKE;
        }
        return random.nextBoolean() ? LANE_CAR : LANE_BIKE;
    }

    private Vehicle randomVehicle(double x, double y, double speed, double direction) {
        int type = random.nextInt(VEHICLE_TYPE_ROLLS);
        if (type < FIRE_TRUCK_ROLLS) {
            return new FireTruck("Fire" + vehicleCount, x, y, speed, direction);
        }
        if (type < FIRE_TRUCK_ROLLS + AMBULANCE_ROLLS) {
            return new Ambulance("Amb" + vehicleCount, x, y, speed, direction, true);
        }
        if (type < FIRE_TRUCK_ROLLS + AMBULANCE_ROLLS + BUS_ROLLS) {
            return new Bus("Bus" + vehicleCount, x, y, speed * 0.7, direction);
        }
        if (type < FIRE_TRUCK_ROLLS + AMBULANCE_ROLLS + BUS_ROLLS + CAR_ROLLS) {
            return new Car("Car" + vehicleCount, x, y, speed, direction, 26, 13, false);
        }
        return new Motorbike("Bike" + vehicleCount, x, y, speed, direction, false);
    }

    private Vehicle manualVehicle(String typeStr, double x, double y, double speed, double direction) {
        return switch (typeStr) {
            case "Emergency" -> new Ambulance("Amb" + vehicleCount, x, y, speed, direction, true);
            case "Ambulance" -> new Ambulance("Amb" + vehicleCount, x, y, speed, direction, true);
            case "FireTruck" -> new FireTruck("Fire" + vehicleCount, x, y, speed, direction);
            case "Bus" -> new Bus("Bus" + vehicleCount, x, y, speed * 0.7, direction);
            case "Car" -> new Car("Car" + vehicleCount, x, y, speed, direction, 26, 13, false);
            case "Motorbike", "Bicycle" -> new Motorbike("Bike" + vehicleCount, x, y, speed, direction, false);
            default -> null;
        };
    }

    private int[] activeSourceIndices() {
        return switch (mode) {
            case CROSS_INTERSECTION -> new int[] { 0, 1, 2, 3 };
            case THREE_WAY_INTERSECTION -> new int[] { 0, 1, 4 };
            case ROAD_NETWORK, FIVE_WAY_ROUNDABOUT -> new int[] { 0, 1, 2, 3, 4 };
        };
    }

    public void toggleAutoSpawn() {
        autoSpawnEnabled = !autoSpawnEnabled;
    }

    public void setAutoSpawnEnabled(boolean autoSpawnEnabled) {
        this.autoSpawnEnabled = autoSpawnEnabled;
    }

    public boolean isAutoSpawnEnabled() {
        return autoSpawnEnabled;
    }

    public boolean isAutoMode() {
        return autoMode;
    }

    public void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    public void setTrafficDensity(int trafficDensity) {
        this.trafficDensity = Math.max(1, Math.min(3, trafficDensity));
    }

    public int getTrafficDensity() {
        return trafficDensity;
    }

    public int getMaxVehicles() {
        return switch (trafficDensity) {
            case 1 -> LOW_DENSITY_MAX_VEHICLES;
            case 3 -> HIGH_DENSITY_MAX_VEHICLES;
            default -> MEDIUM_DENSITY_MAX_VEHICLES;
        };
    }

    private boolean canSpawnMoreVehicles() {
        return vehicles.size() < getMaxVehicles();
    }

    public SimulationMode getMode() {
        return mode;
    }

    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    public List<Intersection> getIntersections() {
        return intersections;
    }

    public List<TrafficLight> getLights() {
        List<TrafficLight> all = new ArrayList<>();
        if (lights1 != null) all.addAll(lights1);
        if (lights2 != null) all.addAll(lights2);
        if (lights3 != null) all.addAll(lights3);
        return all;
    }

    public List<TrafficLight> getLights1() {
        return lights1;
    }

    public List<TrafficLight> getLights2() {
        return lights2;
    }

    public List<TrafficLight> getLights3() {
        return lights3;
    }

    public IntersectionPhaseController getPhaseController1() {
        return phaseController1;
    }

    public ThreeWayPhaseController getPhaseController2() {
        return phaseController2;
    }

    public IntersectionPhaseController getPhaseController3() {
        return phaseController3;
    }

    private record SpawnPoint(double x, double y, double direction, int turnIntention) {
    }
}
