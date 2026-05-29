package vn.edu.hust.traffic.view;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import vn.edu.hust.traffic.controller.TrafficController;
import vn.edu.hust.traffic.model.map.TrafficLight;
import vn.edu.hust.traffic.model.vehicle.Vehicle;

public class TrafficControllerAdapter {
    private static final Object VOID_RESULT = new Object();

    private final TrafficController controller;

    public TrafficControllerAdapter(TrafficController controller) {
        this.controller = controller;
    }

    public TrafficController getController() {
        return controller;
    }

    public SimulationSnapshot snapshot() {
        Object intersection = invokeNoArg(controller, "getIntersections");
        if (intersection == null) {
            intersection = invokeNoArg(controller, "getIntersection");
        }
        List<Vehicle> vehicles = readVehicles();
        List<TrafficLight> lights = readLights();

        Object phaseController = invokeNoArg(controller, "getPhaseController1");
        if (phaseController == null) {
            phaseController = invokeNoArg(controller, "getPhaseController2");
        }
        if (phaseController == null) {
            phaseController = invokeNoArg(controller, "getPhaseController");
        }
        int phaseIndex = readInt(phaseController, "getCurrentPhase", -1);
        double phaseTimeLeft = readDouble(phaseController, "getPhaseTimeLeft", -1.0);
        boolean autoSpawn = readBoolean(controller, "isAutoSpawnEnabled", true);

        return new SimulationSnapshot(vehicles, lights, intersection, phaseIndex, phaseTimeLeft, autoSpawn);
    }

    public boolean spawnVehicle(String type) {
        return invoke(controller, "spawnVehicleManually", new Class<?>[] { String.class }, type) != null;
    }

    public void setAutoMode(boolean autoMode) {
        if (!autoMode) {
            List<TrafficLight> lights = readLights();
            for (TrafficLight light : lights) {
                try {
                    Field timerField = light.getClass().getDeclaredField("timer");
                    timerField.setAccessible(true);
                    timerField.set(light, 20.0);

                    Field leftTurnTimerField = light.getClass().getDeclaredField("leftTurnTimer");
                    leftTurnTimerField.setAccessible(true);
                    leftTurnTimerField.set(light, 20.0);
                } catch (Exception ex) {
                    // Ignore reflection errors
                }
            }
        }

        if (invoke(controller, "setAutoMode", new Class<?>[] { boolean.class }, autoMode) != null) {
            return;
        }
        if (invoke(controller, "setAutoSpawnEnabled", new Class<?>[] { boolean.class }, autoMode) != null) {
            return;
        }

        Object current = invokeNoArg(controller, "isAutoSpawnEnabled");
        if (current instanceof Boolean enabled && enabled != autoMode) {
            invokeNoArg(controller, "toggleAutoSpawn");
        }
    }

    public void setTrafficDensity(int density) {
        if (invoke(controller, "setTrafficDensity", new Class<?>[] { int.class }, density) != null) {
            return;
        }
        invoke(controller, "setDensity", new Class<?>[] { int.class }, density);
    }

    public boolean toggleTrafficLight(int index) {
        boolean isLeftTurn = false;
        if (index >= 100) {
            index -= 100;
            isLeftTurn = true;
        }

        if (!isLeftTurn) {
            if (invoke(controller, "switchLight", new Class<?>[] { int.class }, index) != null) {
                return true;
            }
            if (invoke(controller, "toggleLight", new Class<?>[] { int.class }, index) != null) {
                return true;
            }
            if (invoke(controller, "cycleLight", new Class<?>[] { int.class }, index) != null) {
                return true;
            }
        }

        List<TrafficLight> lights = readLights();
        if (index < 0 || index >= lights.size()) {
            return false;
        }
        TrafficLight light = lights.get(index);
        if (isLeftTurn) {
            return cycleLeftTurnLightObject(light);
        } else {
            return cycleLightObject(light);
        }
    }

    private List<Vehicle> readVehicles() {
        Object vehicles = invokeNoArg(controller, "getVehicles");
        if (!(vehicles instanceof List<?>)) {
            vehicles = invokeNoArg(controller, "getCars");
        }

        List<Vehicle> result = new ArrayList<>();
        if (vehicles instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Vehicle vehicle) {
                    result.add(vehicle);
                }
            }
        }
        return result;
    }

    private List<TrafficLight> readLights() {
        List<TrafficLight> result = new ArrayList<>();

        Object lights = invokeNoArg(controller, "getLights");
        addTrafficLights(result, lights);

        if (result.isEmpty()) {
            addTrafficLights(result, invokeNoArg(controller, "getLights1"));
            addTrafficLights(result, invokeNoArg(controller, "getLights2"));
        }
        return result;
    }

    private void addTrafficLights(List<TrafficLight> result, Object lights) {
        if (!(lights instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item instanceof TrafficLight trafficLight) {
                result.add(trafficLight);
            }
        }
    }

    private boolean cycleLightObject(TrafficLight light) {
        Object currentState = invokeNoArg(light, "getState");
        if (!(currentState instanceof Enum<?> currentEnum)) {
            return false;
        }

        Class<?> enumType = currentEnum.getDeclaringClass();
        Object nextState = nextState(enumType, currentEnum.name());
        if (nextState == null) {
            return false;
        }

        if (invoke(light, "forceState", new Class<?>[] { enumType, double.class }, nextState, 20.0) != null) {
            return true;
        }

        try {
            Field stateField = light.getClass().getDeclaredField("state");
            stateField.setAccessible(true);
            stateField.set(light, nextState);

            Field timerField = light.getClass().getDeclaredField("timer");
            timerField.setAccessible(true);
            timerField.set(light, 20.0);
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private boolean cycleLeftTurnLightObject(TrafficLight light) {
        Object currentState = invokeNoArg(light, "getLeftTurnState");
        if (!(currentState instanceof Enum<?> currentEnum)) {
            return false;
        }

        Class<?> enumType = currentEnum.getDeclaringClass();
        Object nextState = nextState(enumType, currentEnum.name());
        if (nextState == null) {
            return false;
        }

        if (invoke(light, "forceLeftTurnState", new Class<?>[] { enumType, double.class }, nextState, 20.0) != null) {
            return true;
        }

        try {
            Field stateField = light.getClass().getDeclaredField("leftTurnState");
            stateField.setAccessible(true);
            stateField.set(light, nextState);

            Field timerField = light.getClass().getDeclaredField("leftTurnTimer");
            timerField.setAccessible(true);
            timerField.set(light, 20.0);
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private Object nextState(Class<?> enumType, String currentName) {
        String preferred;
        if ("GREEN".equals(currentName)) {
            preferred = enumHasValue(enumType, "YELLOW") ? "YELLOW" : "RED";
        } else if ("YELLOW".equals(currentName)) {
            preferred = "RED";
        } else {
            preferred = "GREEN";
        }

        for (Object constant : enumType.getEnumConstants()) {
            if (constant instanceof Enum<?> enumConstant && enumConstant.name().equals(preferred)) {
                return constant;
            }
        }
        return null;
    }

    private boolean enumHasValue(Class<?> enumType, String name) {
        for (Object constant : enumType.getEnumConstants()) {
            if (constant instanceof Enum<?> enumConstant && enumConstant.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        return invoke(target, methodName, new Class<?>[0]);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            Object result = method.invoke(target, args);
            return method.getReturnType() == Void.TYPE ? VOID_RESULT : result;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private int readInt(Object target, String methodName, int fallback) {
        Object value = invokeNoArg(target, methodName);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private double readDouble(Object target, String methodName, double fallback) {
        Object value = invokeNoArg(target, methodName);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private boolean readBoolean(Object target, String methodName, boolean fallback) {
        Object value = invokeNoArg(target, methodName);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return fallback;
    }
}
