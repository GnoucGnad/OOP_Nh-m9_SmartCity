package vn.edu.hust.traffic.view.renderer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import vn.edu.hust.traffic.model.map.Intersection;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import vn.edu.hust.traffic.model.map.TrafficLight;
import vn.edu.hust.traffic.view.LightDisplayMode;
import vn.edu.hust.traffic.view.MapType;
import vn.edu.hust.traffic.view.SimulationViewSettings;
import vn.edu.hust.traffic.view.camera.Camera;
import vn.edu.hust.traffic.controller.TrafficController;

public class TrafficLightRenderer {
    private static final double CROSS_X = TrafficController.CROSS_X;
    private static final double THREE_WAY_X = TrafficController.THREE_WAY_X;
    private static final double TOP_CROSS_Y = TrafficController.TOP_CROSS_Y;
    private static final double BOTTOM_CROSS_Y = TrafficController.BOTTOM_CROSS_Y;

    private record LightVisual(int index, double x, double y, double width, double height) {
    }

    public void render(GraphicsContext gc, List<TrafficLight> lights, Camera camera, SimulationViewSettings settings) {
        List<LightVisual> visuals = buildVisuals(settings.getMapType(), lights.size());
        for (LightVisual visual : visuals) {
            if (visual.index() < lights.size()) {
                drawLight(gc, lights.get(visual.index()), visual, camera, settings);
            }
        }
    }


    public OptionalInt pick(double screenX, double screenY, List<TrafficLight> lights, Camera camera,
            SimulationViewSettings settings) {
        for (LightVisual visual : buildVisuals(settings.getMapType(), lights.size())) {
            Point2D p = camera.worldToScreen(visual.x(), visual.y());
            double scale = camera.getScale();
            double width = visual.width() * scale;
            double height = visual.height() * scale;
            double pad = Math.max(8.0, 8.0 * scale);

            if (visual.index() < lights.size()) {
                TrafficLight light = lights.get(visual.index());
                if (hasMethod(light, "getLeftTurnState")) {
                    double turnX = p.getX() + width + 5.0 * scale;
                    double turnY = p.getY() + 2.0 * scale;
                    double turnW = Math.max(15.0, 25.0 * scale);
                    double turnH = Math.max(26.0, 46.0 * scale);
                    double leftTurnClickMinX = Math.max(p.getX() + width, turnX - pad);
                    if (screenX >= leftTurnClickMinX && screenX <= turnX + turnW + pad
                            && screenY >= turnY - pad && screenY <= turnY + turnH + pad) {
                        return OptionalInt.of(visual.index() + 100);
                    }
                }
            }

            if (screenX >= p.getX() - pad && screenX <= p.getX() + width + pad
                    && screenY >= p.getY() - pad && screenY <= p.getY() + height + pad) {
                return OptionalInt.of(visual.index());
            }
        }
        return OptionalInt.empty();
    }



    private void drawLight(GraphicsContext gc, TrafficLight light, LightVisual visual, Camera camera,
            SimulationViewSettings settings) {
        Point2D p = camera.worldToScreen(visual.x(), visual.y());
        double scale = camera.getScale();
        double width = Math.max(16.0, visual.width() * scale);
        double height = Math.max(44.0, visual.height() * scale);
        double lens = Math.max(8.0, 18.0 * scale);
        double x = p.getX();
        double y = p.getY();

        gc.setFill(Color.web("#24272c"));
        gc.fillRoundRect(x, y, width, height, 7, 7);
        gc.setStroke(Color.web("#101214"));
        gc.strokeRoundRect(x, y, width, height, 7, 7);

        String state = stateName(light, "getState");
        drawLens(gc, x + width / 2.0 - lens / 2.0, y + 8.0 * scale, lens, "RED".equals(state),
                Color.RED, Color.web("#4d1010"));
        drawLens(gc, x + width / 2.0 - lens / 2.0, y + 33.0 * scale, lens, "YELLOW".equals(state),
                Color.YELLOW, Color.web("#4d4710"));
        drawLens(gc, x + width / 2.0 - lens / 2.0, y + 58.0 * scale, lens, "GREEN".equals(state),
                Color.LIMEGREEN, Color.web("#0d421b"));

        maybeDrawCountdown(gc, light, x + width / 2.0, y + height + 14.0 * scale, scale, settings);

        if (hasMethod(light, "getLeftTurnState")) {
            drawTurnLight(gc, light, x + width + 5.0 * scale, y + 2.0 * scale, scale, settings);
        }
    }

    private void drawTurnLight(GraphicsContext gc, TrafficLight light, double x, double y, double scale,
            SimulationViewSettings settings) {
        double width = Math.max(15.0, 25.0 * scale);
        double height = Math.max(26.0, 46.0 * scale);
        String state = stateName(light, "getLeftTurnState");
        Color color = switch (state) {
            case "GREEN" -> Color.LIMEGREEN;
            case "YELLOW" -> Color.YELLOW;
            default -> Color.web("#581414");
        };

        gc.setFill(Color.web("#1f2227"));
        gc.fillRoundRect(x, y, width, height, 6, 6);
        gc.setFill(color);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, Math.max(9, 16 * scale)));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("L", x + width / 2.0, y + 20.0 * scale);

        int seconds = readInt(light, "getLeftTurnTimeLeft", -1);
        if (shouldShowCountdown(seconds, settings.getLightDisplayMode())) {
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, Math.max(7, 9 * scale)));
            gc.fillText(Integer.toString(seconds), x + width / 2.0, y + 38.0 * scale);
        }
    }

    private void drawLens(GraphicsContext gc, double x, double y, double size, boolean active,
            Color activeColor, Color inactiveColor) {
        gc.setFill(active ? activeColor : inactiveColor);
        gc.fillOval(x, y, size, size);
        if (active) {
            gc.setStroke(activeColor.deriveColor(0, 1.0, 1.4, 0.45));
            gc.setLineWidth(2);
            gc.strokeOval(x - 2, y - 2, size + 4, size + 4);
        }
    }

    private void maybeDrawCountdown(GraphicsContext gc, TrafficLight light, double x, double y, double scale,
            SimulationViewSettings settings) {
        int seconds = readInt(light, "getTimeLeft", -1);
        if (!shouldShowCountdown(seconds, settings.getLightDisplayMode())) {
            return;
        }
        gc.setFill(Color.web("#111317", 0.85));
        gc.fillRoundRect(x - 15 * scale, y - 13 * scale, 30 * scale, 18 * scale, 5, 5);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, Math.max(8, 12 * scale)));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(Integer.toString(seconds), x, y + 1);
    }

    private boolean shouldShowCountdown(int seconds, LightDisplayMode mode) {
        if (seconds < 0 || mode == LightDisplayMode.NO_COUNTDOWN) {
            return false;
        }
        return mode == LightDisplayMode.FULL_COUNTDOWN || seconds <= 10;
    }

    private List<LightVisual> buildVisuals(MapType mapType, int lightCount) {
        List<LightVisual> visuals = new ArrayList<>();
        double cy = BOTTOM_CROSS_Y;

        if (mapType == MapType.CROSS_INTERSECTION && lightCount >= 8) {
            addCrossVisuals(visuals, 0, CROSS_X, BOTTOM_CROSS_Y);
            addCrossVisuals(visuals, 4, CROSS_X, TOP_CROSS_Y);
        } else if (mapType == MapType.ROAD_NETWORK && lightCount >= 11) {
            addCrossVisuals(visuals, 0, CROSS_X, BOTTOM_CROSS_Y);
            addThreeWayVisuals(visuals, 4, THREE_WAY_X, BOTTOM_CROSS_Y);
            addCrossVisuals(visuals, 7, CROSS_X, TOP_CROSS_Y);
        } else if (mapType == MapType.T_INTERSECTION) {
            int baseIndex = lightCount >= 7 ? 4 : 0;
            addThreeWayVisuals(visuals, baseIndex, THREE_WAY_X, cy);
        } else if (mapType == MapType.ROAD_NETWORK && lightCount >= 7) {
            addCrossVisuals(visuals, 0, CROSS_X, cy);
            addThreeWayVisuals(visuals, 4, THREE_WAY_X, cy);
        } else {
            addCrossVisuals(visuals, 0, CROSS_X, cy);
        }

        if (mapType == MapType.FIVE_WAY_INTERSECTION && lightCount > 4) {
            visuals.add(new LightVisual(4, CROSS_X + 175, cy - 135, 30, 90));
        }

        return visuals.stream().filter(visual -> visual.index() < lightCount).toList();
    }

    private void addCrossVisuals(List<LightVisual> visuals, int baseIndex, double cx, double cy) {
        visuals.add(new LightVisual(baseIndex, cx - 136, cy - 148, 30, 90));
        visuals.add(new LightVisual(baseIndex + 1, cx + 94, cy - 148, 30, 90));
        visuals.add(new LightVisual(baseIndex + 2, cx - 136, cy + 82, 30, 90));
        visuals.add(new LightVisual(baseIndex + 3, cx + 94, cy + 82, 30, 90));
    }

    private void addThreeWayVisuals(List<LightVisual> visuals, int baseIndex, double cx, double cy) {
        visuals.add(new LightVisual(baseIndex, cx - 136, cy + 82, 30, 90));
        visuals.add(new LightVisual(baseIndex + 1, cx + 94, cy - 148, 30, 90));
        visuals.add(new LightVisual(baseIndex + 2, cx - 136, cy - 148, 30, 90));
    }

    private String stateName(TrafficLight light, String methodName) {
        Object state = invokeNoArg(light, methodName);
        if (state instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return "RED";
    }

    private int readInt(TrafficLight light, String methodName, int fallback) {
        Object value = invokeNoArg(light, methodName);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private boolean hasMethod(TrafficLight light, String methodName) {
        try {
            light.getClass().getMethod(methodName);
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }

    private Object invokeNoArg(TrafficLight light, String methodName) {
        try {
            Method method = light.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(light);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }
}
