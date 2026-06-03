package vn.edu.hust.traffic.view;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ControlPanel extends VBox {
    private Runnable onPlay = () -> { };
    private Runnable onPause = () -> { };
    private Runnable onReset = () -> { };
    private Consumer<MapType> onMapTypeChanged = value -> { };
    private Consumer<RenderMode> onRenderModeChanged = value -> { };
    private Consumer<ControlMode> onSpawnModeChanged = value -> { };
    private Consumer<ControlMode> onLightModeChanged = value -> { };
    private Consumer<LightDisplayMode> onLightDisplayModeChanged = value -> { };
    private Consumer<Integer> onDensityChanged = value -> { };
    private Consumer<Double> onSpeedChanged = value -> { };
    private Consumer<Boolean> onSoundChanged = value -> { };
    private Consumer<Double> onVolumeChanged = value -> { };
    private Consumer<String> onSpawnVehicle = value -> { };

    public ControlPanel(SimulationViewSettings settings) {
        setPrefWidth(270);
        setMinWidth(250);
        setPadding(new Insets(14));
        setSpacing(10);
        setStyle("-fx-background-color: transparent;");

        Label title = new Label("CONTROL PANEL");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

        Button play = commandButton("Play");
        Button pause = commandButton("Pause");
        Button reset = commandButton("Reset");
        play.setOnAction(event -> onPlay.run());
        pause.setOnAction(event -> onPause.run());
        reset.setOnAction(event -> onReset.run());
        HBox runRow = new HBox(8, play, pause, reset);
        runRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(play, Priority.ALWAYS);
        HBox.setHgrow(pause, Priority.ALWAYS);
        HBox.setHgrow(reset, Priority.ALWAYS);

        ComboBox<MapType> mapBox = enumBox(MapType.values(), settings.getMapType());
        mapBox.setOnAction(event -> onMapTypeChanged.accept(mapBox.getValue()));

        ComboBox<ControlMode> spawnBox = enumBox(ControlMode.values(), settings.getSpawnMode());
        spawnBox.setOnAction(event -> onSpawnModeChanged.accept(spawnBox.getValue()));

        ComboBox<ControlMode> lightControlBox = enumBox(ControlMode.values(), settings.getLightMode());
        lightControlBox.setOnAction(event -> onLightModeChanged.accept(lightControlBox.getValue()));

        ComboBox<RenderMode> renderBox = enumBox(RenderMode.values(), settings.getRenderMode());
        renderBox.setOnAction(event -> onRenderModeChanged.accept(renderBox.getValue()));

        ComboBox<LightDisplayMode> lightBox = enumBox(LightDisplayMode.values(), settings.getLightDisplayMode());
        lightBox.setOnAction(event -> onLightDisplayModeChanged.accept(lightBox.getValue()));

        Label densityValue = valueLabel(densityLabel(settings.getTrafficDensity()));
        Slider density = new Slider(1, 3, settings.getTrafficDensity());
        density.setMajorTickUnit(1);
        density.setMinorTickCount(0);
        density.setSnapToTicks(true);
        density.valueProperty().addListener((obs, oldValue, newValue) -> {
            int value = newValue.intValue();
            densityValue.setText(densityLabel(value));
            onDensityChanged.accept(value);
        });


        CheckBox sound = new CheckBox("Sound enabled");
        sound.setSelected(settings.isSoundEnabled());
        sound.setStyle("-fx-text-fill: #d7dde2;");
        sound.setOnAction(event -> onSoundChanged.accept(sound.isSelected()));

        Label volumeValue = valueLabel((int) Math.round(settings.getVolume() * 100) + "%");
        Slider volume = new Slider(0, 1, settings.getVolume());
        volume.valueProperty().addListener((obs, oldValue, newValue) -> {
            double value = newValue.doubleValue();
            volumeValue.setText((int) Math.round(value * 100) + "%");
            onVolumeChanged.accept(value);
        });

        getChildren().addAll(
                title,
                runRow,
                section("Map"),
                labeled("Loai ban do", mapBox),
                section("Mode"),
                labeled("Sinh xe", spawnBox),
                labeled("Den giao thong", lightControlBox),
                labeled("Hien thi", renderBox),
                labeled("Kieu den", lightBox),
                section("Traffic"),
                labeled("Luu luong", density, densityValue),
                section("Sound"),
                sound,
                labeled("Am luong", volume, volumeValue),
                section("Spawn test"),
                spawnGrid());
    }

    public void setOnPlay(Runnable onPlay) {
        this.onPlay = onPlay;
    }

    public void setOnPause(Runnable onPause) {
        this.onPause = onPause;
    }

    public void setOnReset(Runnable onReset) {
        this.onReset = onReset;
    }

    public void setOnMapTypeChanged(Consumer<MapType> onMapTypeChanged) {
        this.onMapTypeChanged = onMapTypeChanged;
    }

    public void setOnRenderModeChanged(Consumer<RenderMode> onRenderModeChanged) {
        this.onRenderModeChanged = onRenderModeChanged;
    }

    public void setOnSpawnModeChanged(Consumer<ControlMode> onSpawnModeChanged) {
        this.onSpawnModeChanged = onSpawnModeChanged;
    }

    public void setOnLightModeChanged(Consumer<ControlMode> onLightModeChanged) {
        this.onLightModeChanged = onLightModeChanged;
    }

    public void setOnLightDisplayModeChanged(Consumer<LightDisplayMode> onLightDisplayModeChanged) {
        this.onLightDisplayModeChanged = onLightDisplayModeChanged;
    }

    public void setOnDensityChanged(Consumer<Integer> onDensityChanged) {
        this.onDensityChanged = onDensityChanged;
    }

    public void setOnSpeedChanged(Consumer<Double> onSpeedChanged) {
        this.onSpeedChanged = onSpeedChanged;
    }

    public void setOnSoundChanged(Consumer<Boolean> onSoundChanged) {
        this.onSoundChanged = onSoundChanged;
    }

    public void setOnVolumeChanged(Consumer<Double> onVolumeChanged) {
        this.onVolumeChanged = onVolumeChanged;
    }

    public void setOnSpawnVehicle(Consumer<String> onSpawnVehicle) {
        this.onSpawnVehicle = onSpawnVehicle;
    }


    private Button commandButton(String text) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-font-weight: bold;");
        return button;
    }

    private <T> ComboBox<T> enumBox(T[] values, T selected) {
        ComboBox<T> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(values);
        comboBox.setValue(selected);
        comboBox.setMaxWidth(Double.MAX_VALUE);
        return comboBox;
    }

    private Label section(String text) {
        Label label = new Label(text.toUpperCase());
        label.setPadding(new Insets(8, 0, 0, 0));
        label.setStyle("-fx-text-fill: #93a4b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        return label;
    }

    private VBox labeled(String label, javafx.scene.Node control) {
        Label text = new Label(label);
        text.setStyle("-fx-text-fill: #d7dde2; -fx-font-size: 12px;");
        VBox box = new VBox(4, text, control);
        return box;
    }

    private VBox labeled(String label, javafx.scene.Node control, Label value) {
        Label text = new Label(label);
        text.setStyle("-fx-text-fill: #d7dde2; -fx-font-size: 12px;");
        HBox heading = new HBox(text, value);
        heading.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);
        VBox box = new VBox(4, heading, control);
        return box;
    }

    private Label valueLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
        return label;
    }

    private VBox spawnGrid() {
        HBox row1 = new HBox(7,
                spawnButton("Car", "Car"),
                spawnButton("Moto", "Motorbike"),
                spawnButton("Bus", "Bus"));
        HBox row2 = new HBox(7,
                spawnButton("Ambu", "Ambulance"),
                spawnButton("Fire", "FireTruck"),
                spawnButton("Viol", "Violator"));
        row1.setAlignment(Pos.CENTER);
        row2.setAlignment(Pos.CENTER);
        VBox box = new VBox(7, row1, row2);
        Separator separator = new Separator();
        VBox wrapper = new VBox(10, separator, box);
        return wrapper;
    }

    private Button spawnButton(String text, String type) {
        Button button = commandButton(text);
        button.setOnAction(event -> onSpawnVehicle.accept(type));
        return button;
    }

    private String densityLabel(int value) {
        return switch (value) {
            case 1 -> "Th\u1ea5p";
            case 3 -> "Cao";
            default -> "Trung b\u00ecnh";
        };
    }
}
