package vn.edu.hust.traffic.view;

import java.util.OptionalInt;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import vn.edu.hust.traffic.controller.SimulationMode;
import vn.edu.hust.traffic.controller.TrafficController;
import vn.edu.hust.traffic.utils.SoundPlayer;
import vn.edu.hust.traffic.view.camera.Camera;
import vn.edu.hust.traffic.view.renderer.DefaultRenderer;

public class SimulationWindow extends Application {
    private static final double DEFAULT_WIDTH = 1180;
    private static final double DEFAULT_HEIGHT = 720;

    private final SimulationViewSettings settings = new SimulationViewSettings();
    private final Camera camera = new Camera();
    private final Renderer renderer = new DefaultRenderer();

    private Canvas canvas;
    private GraphicsContext gc;
    private TrafficControllerAdapter controllerAdapter;
    private boolean running = true;

    @Override
    public void start(Stage primaryStage) {
        canvas = new Canvas(880, 680);
        gc = canvas.getGraphicsContext2D();

        StackPane simulationPane = new StackPane(canvas);
        simulationPane.setStyle("-fx-background-color: #dfe8df;");
        canvas.widthProperty().bind(simulationPane.widthProperty());
        canvas.heightProperty().bind(simulationPane.heightProperty());

        ControlPanel controlPanel = new ControlPanel(settings);
        configureControlPanel(controlPanel);

        BorderPane root = new BorderPane();
        root.setCenter(simulationPane);
        root.setRight(controlPanel);

        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        configureInput(scene);
        configureMouse();
        resetSimulation();

        primaryStage.setTitle("Smart City Traffic Simulation");
        primaryStage.setScene(scene);
        primaryStage.show();
        simulationPane.requestFocus();

        SoundPlayer.setVolume(settings.getVolume());
        SoundPlayer.setEnabled(settings.isSoundEnabled());
        startRenderLoop();
    }

    private void configureControlPanel(ControlPanel controlPanel) {
        controlPanel.setOnPlay(() -> running = true);
        controlPanel.setOnPause(() -> running = false);
        controlPanel.setOnReset(this::resetSimulation);
        controlPanel.setOnMapTypeChanged(mapType -> {
            settings.setMapType(mapType);
            resetSimulation();
        });
        controlPanel.setOnRenderModeChanged(settings::setRenderMode);
        controlPanel.setOnLightDisplayModeChanged(settings::setLightDisplayMode);
        controlPanel.setOnSpawnModeChanged(mode -> {
            settings.setSpawnMode(mode);
            controllerAdapter.setAutoSpawnEnabled(mode == ControlMode.AUTO);
        });
        controlPanel.setOnLightModeChanged(mode -> {
            settings.setLightMode(mode);
            controllerAdapter.setAutoMode(mode == ControlMode.AUTO);
        });
        controlPanel.setOnDensityChanged(value -> {
            settings.setTrafficDensity(value);
            controllerAdapter.setTrafficDensity(value);
        });
        controlPanel.setOnSpeedChanged(settings::setSimulationSpeed);
        controlPanel.setOnSoundChanged(value -> {
            settings.setSoundEnabled(value);
            SoundPlayer.setEnabled(value);
        });
        controlPanel.setOnVolumeChanged(value -> {
            settings.setVolume(value);
            SoundPlayer.setVolume(value);
        });
        controlPanel.setOnSpawnVehicle(type -> {
            if (controllerAdapter.spawnVehicle(type)) {
                playVehicleSound(type);
            }
        });
    }

    private void configureInput(Scene scene) {
        scene.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();
            if (code == KeyCode.SPACE) {
                running = !running;
                return;
            }
            if (code == KeyCode.R) {
                resetSimulation();
                return;
            }
            if (code == KeyCode.P) {
                settings.setLightMode(settings.getLightMode() == ControlMode.AUTO ? ControlMode.MANUAL : ControlMode.AUTO);
                controllerAdapter.setAutoMode(settings.getLightMode() == ControlMode.AUTO);
                return;
            }
            if (code == KeyCode.O) {
                settings.setSpawnMode(settings.getSpawnMode() == ControlMode.AUTO ? ControlMode.MANUAL : ControlMode.AUTO);
                controllerAdapter.setAutoSpawnEnabled(settings.getSpawnMode() == ControlMode.AUTO);
                return;
            }

            String type = switch (code) {
                case C -> "Car";
                case M -> "Motorbike";
                case B -> "Bus";
                case A -> "Ambulance";
                case E -> "Emergency";
                case F -> "FireTruck";
                default -> null;
            };
            if (type != null && controllerAdapter.spawnVehicle(type)) {
                playVehicleSound(type);
            }
        });
    }

    private void configureMouse() {
        canvas.setOnMouseClicked(event -> {
            if (settings.getLightMode() != ControlMode.MANUAL) {
                return;
            }
            SimulationSnapshot snapshot = controllerAdapter.snapshot();
            camera.fit(settings.getMapType(), canvas.getWidth(), canvas.getHeight());
            OptionalInt selectedLight = renderer.pickTrafficLight(
                    event.getX(), event.getY(), snapshot, camera, settings);
            selectedLight.ifPresent(controllerAdapter::toggleTrafficLight);
        });
    }

    private void startRenderLoop() {
        AnimationTimer timer = new AnimationTimer() {
            private long lastUpdate;

            @Override
            public void handle(long now) {
                if (lastUpdate == 0) {
                    lastUpdate = now;
                    render();
                    return;
                }

                double dt = (now - lastUpdate) / 1_000_000_000.0;
                lastUpdate = now;

                if (running) {
                    update(Math.min(dt, 0.05));
                }
                render();
            }
        };
        timer.start();
    }

    private void update(double dt) {
        controllerAdapter.getController().update(dt * settings.getSimulationSpeed());
    }

    private void render() {
        SimulationSnapshot snapshot = controllerAdapter.snapshot();
        camera.fit(settings.getMapType(), canvas.getWidth(), canvas.getHeight());
        renderer.render(gc, snapshot, camera, settings);
    }

    private void resetSimulation() {
        TrafficController controller = new TrafficController(toSimulationMode(settings.getMapType()));
        controllerAdapter = new TrafficControllerAdapter(controller);
        controllerAdapter.setAutoMode(settings.getLightMode() == ControlMode.AUTO);
        controllerAdapter.setAutoSpawnEnabled(settings.getSpawnMode() == ControlMode.AUTO);
        controllerAdapter.setTrafficDensity(settings.getTrafficDensity());
    }

    private SimulationMode toSimulationMode(MapType mapType) {
        return switch (mapType) {
            case T_INTERSECTION -> SimulationMode.THREE_WAY_INTERSECTION;
            case CROSS_INTERSECTION -> SimulationMode.CROSS_INTERSECTION;
            case FIVE_WAY_INTERSECTION -> SimulationMode.FIVE_WAY_ROUNDABOUT;
            case ROAD_NETWORK -> SimulationMode.ROAD_NETWORK;
        };
    }

    private void playVehicleSound(String type) {
        if (!settings.isSoundEnabled()) {
            return;
        }
        String lower = type.toLowerCase();
        if (lower.contains("amb") || lower.contains("emergency") || lower.contains("fire")) {
            SoundPlayer.playAmbulance();
            return;
        }
        if (lower.contains("motor") || lower.contains("bike")) {
            SoundPlayer.playSignal();
            return;
        }
        SoundPlayer.playHorn();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
