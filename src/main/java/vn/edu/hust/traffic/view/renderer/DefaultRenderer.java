package vn.edu.hust.traffic.view.renderer;

import java.util.OptionalInt;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import vn.edu.hust.traffic.view.Renderer;
import vn.edu.hust.traffic.view.SimulationSnapshot;
import vn.edu.hust.traffic.view.SimulationViewSettings;
import vn.edu.hust.traffic.view.camera.Camera;

public class DefaultRenderer implements Renderer {
    private final RoadRenderer roadRenderer = new RoadRenderer();
    private final VehicleRenderer vehicleRenderer = new VehicleRenderer();
    private final TrafficLightRenderer trafficLightRenderer = new TrafficLightRenderer();
    private final OverlayRenderer overlayRenderer = new OverlayRenderer();

    @Override
    public void render(GraphicsContext gc, SimulationSnapshot snapshot, Camera camera, SimulationViewSettings settings) {
        gc.setFill(Color.web("#eef1ed"));
        gc.fillRect(0, 0, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());

        gc.save();
        javafx.geometry.Point2D p = camera.worldToScreen(camera.getWorldX(), camera.getWorldY());
        double clipW = camera.getWorldWidth() * camera.getScale();
        double clipH = camera.getWorldHeight() * camera.getScale();
        
        gc.beginPath();
        gc.rect(p.getX(), p.getY(), clipW, clipH);
        gc.clip();

        roadRenderer.render(gc, camera, settings.getMapType());
        vehicleRenderer.tick(System.nanoTime());
        vehicleRenderer.render(gc, snapshot.getVehicles(), camera, settings);
        trafficLightRenderer.render(gc, snapshot.getLights(), camera, settings);
        gc.restore();

        if (settings.isShowOverlay()) {
            overlayRenderer.render(gc, snapshot, settings);
        }
    }

    @Override
    public OptionalInt pickTrafficLight(
            double screenX,
            double screenY,
            SimulationSnapshot snapshot,
            Camera camera,
            SimulationViewSettings settings) {
        return trafficLightRenderer.pick(screenX, screenY, snapshot.getLights(), camera, settings);
    }
}
