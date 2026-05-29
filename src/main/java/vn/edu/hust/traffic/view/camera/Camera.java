package vn.edu.hust.traffic.view.camera;

import javafx.geometry.Point2D;
import vn.edu.hust.traffic.view.MapType;

public class Camera {
    private double scale = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double worldX = 0.0;
    private double worldY = 0.0;
    private double worldWidth = 800.0;
    private double worldHeight = 600.0;

    public void fit(MapType mapType, double viewportWidth, double viewportHeight) {
        double[] bounds = worldBounds(mapType);
        worldX = bounds[0];
        worldY = bounds[1];
        worldWidth = bounds[2];
        worldHeight = bounds[3];

        double padding = mapType == MapType.ROAD_NETWORK ? 18.0 : 28.0;
        double availableWidth = Math.max(100.0, viewportWidth - padding * 2.0);
        double availableHeight = Math.max(100.0, viewportHeight - padding * 2.0);

        scale = Math.min(availableWidth / worldWidth, availableHeight / worldHeight);
        if (mapType != MapType.ROAD_NETWORK) {
            scale = Math.min(scale, 1.35);
        }
        scale = Math.max(0.15, scale);

        offsetX = (viewportWidth - worldWidth * scale) / 2.0 - worldX * scale;
        offsetY = (viewportHeight - worldHeight * scale) / 2.0 - worldY * scale;
    }

    public Point2D worldToScreen(double x, double y) {
        return new Point2D(x * scale + offsetX, y * scale + offsetY);
    }

    public Point2D screenToWorld(double x, double y) {
        return new Point2D((x - offsetX) / scale, (y - offsetY) / scale);
    }

    public double worldToScreenLength(double value) {
        return value * scale;
    }

    public double getScale() {
        return scale;
    }

    public double getWorldX() {
        return worldX;
    }

    public double getWorldY() {
        return worldY;
    }

    public double getWorldWidth() {
        return worldWidth;
    }

    public double getWorldHeight() {
        return worldHeight;
    }

    public static double[] worldBounds(MapType mapType) {
        return switch (mapType) {
            case CROSS_INTERSECTION -> new double[] { 0.0, -800.0, 800.0, 1500.0 };
            case T_INTERSECTION -> new double[] { 600.0, 0.0, 800.0, 600.0 };
            case FIVE_WAY_INTERSECTION -> new double[] { 100.0, -80.0, 1000.0, 760.0 };
            case ROAD_NETWORK -> new double[] { -100.0, -900.0, 1700.0, 1700.0 };
        };
    }
}
