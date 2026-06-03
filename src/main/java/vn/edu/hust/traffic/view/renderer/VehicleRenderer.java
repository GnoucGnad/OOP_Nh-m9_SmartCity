package vn.edu.hust.traffic.view.renderer;

import java.util.List;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import vn.edu.hust.traffic.model.vehicle.Vehicle;
import vn.edu.hust.traffic.view.RenderMode;
import vn.edu.hust.traffic.view.SimulationViewSettings;
import vn.edu.hust.traffic.view.assets.SpriteManager;
import vn.edu.hust.traffic.view.camera.Camera;

public class VehicleRenderer {
    private final SpriteManager spriteManager = new SpriteManager();
    private long animationFrame;

    public void tick(long now) {
        animationFrame = now / 80_000_000L;
    }

    public void render(GraphicsContext gc, List<Vehicle> vehicles, Camera camera, SimulationViewSettings settings) {
        for (Vehicle vehicle : vehicles) {
            renderVehicle(gc, vehicle, camera, settings);
        }
    }

    private void renderVehicle(GraphicsContext gc, Vehicle vehicle, Camera camera, SimulationViewSettings settings) {
        Point2D p = camera.worldToScreen(vehicle.getX(), vehicle.getY());
        double scale = camera.getScale();
        double width = Math.max(5.0, vehicle.getWidth() * scale);
        double height = Math.max(4.0, vehicle.getHeight() * scale);
        String type = vehicleType(vehicle);

        gc.save();
        gc.translate(p.getX(), p.getY());
        gc.rotate(Math.toDegrees(vehicle.getVisualDirection()));
        if (settings.getRenderMode() == RenderMode.GRAPHIC) {
            gc.setEffect(new DropShadow(Math.max(2.0, 4.0 * scale), 1.5, 1.5, Color.color(0, 0, 0, 0.35)));
        }

        if (settings.getRenderMode() == RenderMode.GRAPHIC) {
            renderGraphic(gc, vehicle, type, width, height);
        } else {
            renderBasic(gc, vehicle, type, width, height);
        }

        gc.setEffect(null);
        gc.restore();

        if (camera.getScale() > 0.55 && settings.getMapType() != vn.edu.hust.traffic.view.MapType.ROAD_NETWORK) {
            drawVehicleId(gc, vehicle, p, height);
        }
    }

    private void renderBasic(GraphicsContext gc, Vehicle vehicle, String type, double width, double height) {
        gc.setFill(colorFor(type, vehicle.isPriorityVehicle()));
        gc.fillRoundRect(-width / 2.0, -height / 2.0, width, height, 4, 4);
        gc.setStroke(Color.web("#20242a"));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(-width / 2.0, -height / 2.0, width, height, 4, 4);

        if (width >= 22 && height >= 10) {
            gc.setFill(textColor(type));
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, Math.max(7, Math.min(11, height * 0.62))));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText(labelFor(type), 0, 0);
        }
    }

    private void renderGraphic(GraphicsContext gc, Vehicle vehicle, String type, double width, double height) {
        Image image = spriteManager.getVehicleImage(type);
        if (image != null) {
            gc.drawImage(image, -width / 2.0, -height / 2.0, width, height);
        } else {
            renderVectorSprite(gc, vehicle, type, width, height);
        }

        if (vehicle.isPriorityVehicle() || "firetruck".equals(type)) {
            renderEmergencyFlash(gc, width, height);
        }
    }

    private void renderVectorSprite(GraphicsContext gc, Vehicle vehicle, String type, double width, double height) {
        if ("motorbike".equals(type) || "bicycle".equals(type)) {
            renderTwoWheeler(gc, type, width, height);
            return;
        }
        if ("bus".equals(type)) {
            renderBus(gc, width, height);
            return;
        }
        if ("ambulance".equals(type)) {
            renderAmbulance(gc, vehicle.isPriorityVehicle(), width, height);
            return;
        }
        if ("firetruck".equals(type)) {
            renderFireTruck(gc, width, height);
            return;
        }
        if ("violator".equals(type)) {
            renderCar(gc, width, height, Color.web("#e84393"));
            return;
        }
        renderCar(gc, width, height, colorFor(type, false));
    }

    private void renderCar(GraphicsContext gc, double width, double height, Color body) {
        // 1. Wheels (draw underneath body)
        drawWheels(gc, width, height, 4);

        // 2. Body Gradient
        // Top-down 3D cylindrical shine gradient
        LinearGradient bodyGrad = new LinearGradient(
            0, -height / 2.0, 0, height / 2.0, false, CycleMethod.NO_CYCLE,
            new Stop(0, body.deriveColor(0, 1.0, 1.25, 1.0)),
            new Stop(0.3, body),
            new Stop(0.7, body),
            new Stop(1.0, body.darker())
        );
        gc.setFill(bodyGrad);
        gc.fillRoundRect(-width / 2.0, -height / 2.0, width, height, 8, 7);

        // Body outline for crisp definition
        gc.setStroke(body.darker().darker());
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(-width / 2.0, -height / 2.0, width, height, 8, 7);

        // 3. Roof / Cabin
        // Slightly smaller than the main body, centered
        double roofW = width * 0.55;
        double roofH = height * 0.75;
        double roofX = -width * 0.15;
        double roofY = -roofH / 2.0;
        
        LinearGradient roofGrad = new LinearGradient(
            0, roofY, 0, roofY + roofH, false, CycleMethod.NO_CYCLE,
            new Stop(0, body.deriveColor(0, 1.0, 1.15, 1.0)),
            new Stop(1.0, body.deriveColor(0, 1.0, 0.85, 1.0))
        );
        gc.setFill(roofGrad);
        gc.fillRoundRect(roofX, roofY, roofW, roofH, 4, 4);
        
        // Roof outline
        gc.setStroke(body.darker());
        gc.setLineWidth(0.8);
        gc.strokeRoundRect(roofX, roofY, roofW, roofH, 4, 4);

        // 4. Sunroof (premium detail)
        gc.setFill(Color.web("#1e272e", 0.75));
        gc.fillRoundRect(roofX + roofW * 0.25, -height * 0.18, roofW * 0.35, height * 0.36, 2, 2);

        // 5. Windows
        // Windshield (front glass, curved)
        double wsX = roofX + roofW - 2;
        double wsW = width * 0.09;
        double wsH = height * 0.65;
        gc.setFill(new LinearGradient(
            0, -wsH / 2.0, 0, wsH / 2.0, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#80c4e8", 0.95)),
            new Stop(1, Color.web("#2f80ed", 0.95))
        ));
        gc.fillRoundRect(wsX, -wsH / 2.0, wsW, wsH, 3, 3);
        
        // Windshield reflection glare line
        gc.setStroke(Color.web("#ffffff", 0.6));
        gc.setLineWidth(1.0);
        gc.strokeLine(wsX + wsW * 0.2, -wsH * 0.3, wsX + wsW * 0.8, wsH * 0.3);

        // Rear window
        double rwX = roofX + 2;
        double rwW = width * 0.07;
        double rwH = height * 0.6;
        gc.setFill(Color.web("#57606f", 0.9));
        gc.fillRoundRect(rwX, -rwH / 2.0, rwW, rwH, 2, 2);

        // Side windows
        gc.setFill(Color.web("#2f3542", 0.95));
        double swW = roofW - 8;
        double swH = height * 0.08;
        // Left side window
        gc.fillRoundRect(roofX + 4, roofY + 1, swW, swH, 1, 1);
        // Right side window
        gc.fillRoundRect(roofX + 4, roofY + roofH - swH - 1, swW, swH, 1, 1);

        // 6. Side mirrors (matching body color, with a silver mirror surface)
        double mirrorW = width * 0.08;
        double mirrorH = height * 0.12;
        double mirrorX = roofX + roofW - mirrorW - 2;
        // Left mirror
        gc.setFill(body.darker());
        gc.fillRoundRect(mirrorX, -height / 2.0 - mirrorH + 1, mirrorW, mirrorH, 1.5, 1.5);
        gc.setFill(Color.web("#ffffff", 0.9)); // mirror glass
        gc.fillRect(mirrorX + 1, -height / 2.0 - 1, mirrorW - 2, 1);
        
        // Right mirror
        gc.setFill(body.darker());
        gc.fillRoundRect(mirrorX, height / 2.0 - 1, mirrorW, mirrorH, 1.5, 1.5);
        gc.setFill(Color.web("#ffffff", 0.9)); // mirror glass
        gc.fillRect(mirrorX + 1, height / 2.0 - 1 + mirrorH - 2, mirrorW - 2, 1);

        // 7. Lights
        drawHeadTailLights(gc, width, height);
    }

    private void renderBus(GraphicsContext gc, double width, double height) {
        // 1. Wheels (6 wheels for bus)
        drawWheels(gc, width, height, 6);

        // 2. Body Gradient
        Color baseGreen = Color.web("#2ca25f");
        LinearGradient bodyGrad = new LinearGradient(
            0, -height / 2.0, 0, height / 2.0, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#4cd137")),
            new Stop(0.4, baseGreen),
            new Stop(0.7, baseGreen),
            new Stop(1.0, Color.web("#1b8a45"))
        );
        gc.setFill(bodyGrad);
        gc.fillRoundRect(-width / 2.0, -height / 2.0, width, height, 6, 6);

        // Body outline
        gc.setStroke(Color.web("#0e4d25"));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(-width / 2.0, -height / 2.0, width, height, 6, 6);

        // Decorative orange/yellow accent stripes on the sides
        gc.setFill(Color.web("#fbc531"));
        gc.fillRect(-width * 0.44, -height * 0.44, width * 0.88, height * 0.06);
        gc.fillRect(-width * 0.44, height * 0.38, width * 0.88, height * 0.06);

        // 3. Windows
        // Side windows: stylish continuous glossy strip with dividers
        double winAreaX = -width * 0.4;
        double winAreaW = width * 0.76;
        double winH = height * 0.12;
        
        // Upper side window strip
        gc.setFill(Color.web("#1e272e", 0.95));
        gc.fillRect(winAreaX, -height * 0.38, winAreaW, winH);
        // Lower side window strip
        gc.fillRect(winAreaX, height * 0.38 - winH, winAreaW, winH);

        // Window dividers
        gc.setStroke(Color.web("#718093", 0.7));
        gc.setLineWidth(0.8);
        int numDividers = 6;
        for (int i = 1; i < numDividers; i++) {
            double divX = winAreaX + (winAreaW * i / numDividers);
            gc.strokeLine(divX, -height * 0.38, divX, -height * 0.38 + winH);
            gc.strokeLine(divX, height * 0.38 - winH, divX, height * 0.38);
        }

        // Front windshield (large glossy pane)
        double wsX = width * 0.38;
        double wsW = width * 0.08;
        double wsH = height * 0.8;
        gc.setFill(new LinearGradient(
            0, -wsH / 2.0, 0, wsH / 2.0, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#80c4e8", 0.95)),
            new Stop(1, Color.web("#2f80ed", 0.95))
        ));
        gc.fillRoundRect(wsX, -wsH / 2.0, wsW, wsH, 2, 2);
        
        // Glare on front windshield
        gc.setStroke(Color.web("#ffffff", 0.5));
        gc.setLineWidth(1.0);
        gc.strokeLine(wsX + wsW * 0.3, -wsH * 0.3, wsX + wsW * 0.7, wsH * 0.3);

        // Rear window
        double rwX = -width * 0.48;
        double rwW = width * 0.04;
        double rwH = height * 0.7;
        gc.setFill(Color.web("#2f3542"));
        gc.fillRoundRect(rwX, -rwH / 2.0, rwW, rwH, 1.5, 1.5);

        // 4. Roof AC/Ventilation Units
        gc.setFill(Color.web("#f5f6fa"));
        gc.setStroke(Color.web("#dcdde1"));
        gc.setLineWidth(0.8);
        
        // Mid-front AC Unit
        double acW = width * 0.16;
        double acH = height * 0.4;
        gc.fillRoundRect(-width * 0.18, -acH / 2.0, acW, acH, 3, 3);
        gc.strokeRoundRect(-width * 0.18, -acH / 2.0, acW, acH, 3, 3);
        
        // Mid-rear AC Unit
        gc.fillRoundRect(width * 0.05, -acH / 2.0, acW, acH, 3, 3);
        gc.strokeRoundRect(width * 0.05, -acH / 2.0, acW, acH, 3, 3);
        
        // Tiny grille lines on AC units
        gc.setStroke(Color.web("#b2bec3"));
        gc.setLineWidth(0.5);
        for (double gx = -width * 0.15; gx < -width * 0.05; gx += 3) {
            gc.strokeLine(gx, -acH * 0.3, gx, acH * 0.3);
        }
        for (double gx = width * 0.08; gx < width * 0.18; gx += 3) {
            gc.strokeLine(gx, -acH * 0.3, gx, acH * 0.3);
        }

        // 5. Extended side mirrors
        double mirrorW = width * 0.04;
        double mirrorH = height * 0.14;
        double mirrorX = width * 0.44;
        // Left mirror
        gc.setFill(Color.web("#2f3542"));
        gc.fillRect(mirrorX, -height * 0.52, mirrorW, mirrorH);
        gc.setFill(Color.web("#ffffff"));
        gc.fillRect(mirrorX + 1, -height * 0.52 + 1, mirrorW - 2, 1);
        
        // Right mirror
        gc.setFill(Color.web("#2f3542"));
        gc.fillRect(mirrorX, height * 0.38, mirrorW, mirrorH);
        gc.setFill(Color.web("#ffffff"));
        gc.fillRect(mirrorX + 1, height * 0.38 + mirrorH - 2, mirrorW - 2, 1);

        // 6. Lights
        drawHeadTailLights(gc, width, height);
    }

    private void renderAmbulance(GraphicsContext gc, boolean emergency, double width, double height) {
        // 1. Wheels (4 wheels)
        drawWheels(gc, width, height, 4);

        // 2. Body Gradient (Sạch sẽ, trắng sáng)
        LinearGradient bodyGrad = new LinearGradient(
            0, -height / 2.0, 0, height / 2.0, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#ffffff")),
            new Stop(0.5, Color.web("#f8fafc")),
            new Stop(1.0, Color.web("#f1f5f9"))
        );
        gc.setFill(bodyGrad);
        gc.fillRoundRect(-width / 2.0, -height / 2.0, width, height, 8, 8);

        // Body outline
        gc.setStroke(Color.web("#a5b1c2"));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(-width / 2.0, -height / 2.0, width, height, 8, 8);

        // Vạch sọc màu đỏ phản quang (Red) đặc trưng của xe cứu thương
        gc.setFill(Color.web("#eb3b5a"));
        gc.fillRect(-width * 0.4, -height * 0.46, width * 0.75, height * 0.08);
        gc.fillRect(-width * 0.4, height * 0.38, width * 0.75, height * 0.08);

        // 3. Biểu tượng chữ thập đỏ trên nóc xe (phía sau) - tăng kích thước để nhìn rõ hơn
        gc.setFill(Color.web("#eb3b5a"));
        double crossSize = height * 0.45; // Tăng kích thước để biểu tượng chữ thập hiển thị rõ nét
        double crossX = -width * 0.12;
        double crossY = 0;
        double thickness = Math.max(2.0, crossSize * 0.32);
        // Thanh ngang
        gc.fillRect(crossX - crossSize / 2.0, crossY - thickness / 2.0, crossSize, thickness);
        // Thanh dọc
        gc.fillRect(crossX - thickness / 2.0, crossY - crossSize / 2.0, thickness, crossSize);

        // Biểu tượng chữ thập đỏ trên capo xe (phía trước) - tăng kích thước để dễ nhận dạng
        double frontCrossSize = height * 0.30;
        double frontCrossX = width * 0.36;
        double frontCrossY = 0;
        double frontThickness = Math.max(1.5, frontCrossSize * 0.32);
        // Thanh ngang
        gc.fillRect(frontCrossX - frontCrossSize / 2.0, frontCrossY - frontThickness / 2.0, frontCrossSize, frontThickness);
        // Thanh dọc
        gc.fillRect(frontCrossX - frontThickness / 2.0, frontCrossY - frontCrossSize / 2.0, frontThickness, frontCrossSize);

        // 4. Windows
        // Front windshield
        double wsX = width * 0.24;
        double wsW = width * 0.11;
        double wsH = height * 0.72;
        gc.setFill(new LinearGradient(
            0, -wsH / 2.0, 0, wsH / 2.0, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#80c4e8", 0.95)),
            new Stop(1, Color.web("#2f80ed", 0.95))
        ));
        gc.fillRoundRect(wsX, -wsH / 2.0, wsW, wsH, 2.5, 2.5);
        
        // Windshield reflection
        gc.setStroke(Color.web("#ffffff", 0.5));
        gc.setLineWidth(1.0);
        gc.strokeLine(wsX + wsW * 0.3, -wsH * 0.3, wsX + wsW * 0.7, wsH * 0.3);

        // Side windows
        gc.setFill(Color.web("#2f3542", 0.9));
        // Cabin windows (front)
        gc.fillRect(width * 0.1, -height * 0.42, width * 0.1, height * 0.06);
        gc.fillRect(width * 0.1, height * 0.36, width * 0.1, height * 0.06);
        
        // Patient room side windows
        gc.setFill(Color.web("#747d8c", 0.8));
        gc.fillRoundRect(-width * 0.28, -height * 0.42, width * 0.22, height * 0.06, 1, 1);
        gc.fillRoundRect(-width * 0.28, height * 0.36, width * 0.22, height * 0.06, 1, 1);

        // Rear window
        gc.setFill(Color.web("#2f3542"));
        gc.fillRoundRect(-width * 0.48, -height * 0.25, width * 0.04, height * 0.5, 1.5, 1.5);

        // 5. Side Mirrors
        double mirrorW = width * 0.06;
        double mirrorH = height * 0.12;
        double mirrorX = width * 0.22;
        gc.setFill(Color.web("#dcdde1"));
        gc.fillRoundRect(mirrorX, -height / 2.0 - mirrorH + 1, mirrorW, mirrorH, 1.5, 1.5);
        gc.fillRoundRect(mirrorX, height / 2.0 - 1, mirrorW, mirrorH, 1.5, 1.5);
        gc.setFill(Color.web("#ffffff"));
        gc.fillRect(mirrorX + 1, -height / 2.0 - 1, mirrorW - 2, 1);
        gc.fillRect(mirrorX + 1, height / 2.0 - 1 + mirrorH - 2, mirrorW - 2, 1);

        // 6. Lights
        drawHeadTailLights(gc, width, height);

        // 7. Active Emergency Lightbar
        if (emergency) {
            renderEmergencyFlash(gc, width, height);
        }
    }

    private void renderFireTruck(GraphicsContext gc, double width, double height) {
        // 1. Wheels (6 wheels for heavy truck)
        drawWheels(gc, width, height, 6);

        // 2. Body Gradient
        LinearGradient bodyGrad = new LinearGradient(
            0, -height / 2.0, 0, height / 2.0, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#ff4757")),
            new Stop(0.5, Color.web("#ee5253")),
            new Stop(1.0, Color.web("#c23616"))
        );
        gc.setFill(bodyGrad);
        gc.fillRoundRect(-width / 2.0, -height / 2.0, width, height, 6, 6);

        // Body outline
        gc.setStroke(Color.web("#801500"));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(-width / 2.0, -height / 2.0, width, height, 6, 6);

        // Reflective stripes
        gc.setStroke(Color.web("#fbc531"));
        gc.setLineWidth(1.5);
        gc.strokeLine(-width * 0.38, -height * 0.42, width * 0.18, -height * 0.42);
        gc.strokeLine(-width * 0.38, height * 0.38, width * 0.18, height * 0.38);

        // 3. Cabin & Windshield
        double wsX = width * 0.3;
        double wsW = width * 0.12;
        double wsH = height * 0.76;
        gc.setFill(new LinearGradient(
            0, -wsH / 2.0, 0, wsH / 2.0, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#80c4e8", 0.95)),
            new Stop(1, Color.web("#2f80ed", 0.95))
        ));
        gc.fillRoundRect(wsX, -wsH / 2.0, wsW, wsH, 2, 2);
        
        // Windshield glare
        gc.setStroke(Color.web("#ffffff", 0.5));
        gc.setLineWidth(1.0);
        gc.strokeLine(wsX + wsW * 0.3, -wsH * 0.3, wsX + wsW * 0.7, wsH * 0.3);

        // Side windows of the cab
        gc.setFill(Color.web("#2f3542", 0.95));
        gc.fillRect(width * 0.14, -height * 0.44, width * 0.12, height * 0.08);
        gc.fillRect(width * 0.14, height * 0.36, width * 0.12, height * 0.08);

        // 4. Equipment compartment / Roller shutters
        double compX = -width * 0.44;
        double compY = -height * 0.32;
        double compW = width * 0.54;
        double compH = height * 0.64;
        gc.setFill(new LinearGradient(
            0, compY, 0, compY + compH, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#f5f6fa")),
            new Stop(1.0, Color.web("#dcdde1"))
        ));
        gc.fillRoundRect(compX, compY, compW, compH, 2, 2);
        gc.setStroke(Color.web("#718093"));
        gc.setLineWidth(0.8);
        gc.strokeRoundRect(compX, compY, compW, compH, 2, 2);
        
        // Compartment horizontal shutter lines
        gc.setStroke(Color.web("#a4b0be"));
        gc.setLineWidth(0.6);
        for (double gx = compX + 4; gx < compX + compW - 4; gx += 5) {
            gc.strokeLine(gx, compY + 2, gx, compY + compH - 2);
        }

        // 5. Ladder on the roof
        double ladderX = -width * 0.4;
        double ladderY = -height * 0.15;
        double ladderW = width * 0.56;
        double ladderH = height * 0.3;
        
        gc.setStroke(Color.web("#7f8c8d"));
        gc.setLineWidth(1.2);
        gc.strokeLine(ladderX, ladderY, ladderX + ladderW, ladderY);
        gc.strokeLine(ladderX, ladderY + ladderH, ladderX + ladderW, ladderY + ladderH);
        
        // Rungs
        gc.setStroke(Color.web("#bdc3c7"));
        gc.setLineWidth(0.8);
        int rungs = 8;
        for (int i = 0; i <= rungs; i++) {
            double rx = ladderX + (ladderW * i / rungs);
            gc.strokeLine(rx, ladderY, rx, ladderY + ladderH);
        }

        // 6. Side Mirrors
        double mirrorW = width * 0.05;
        double mirrorH = height * 0.12;
        double mirrorX = width * 0.28;
        gc.setFill(Color.web("#2f3542"));
        gc.fillRect(mirrorX, -height * 0.52, mirrorW, mirrorH);
        gc.fillRect(mirrorX, height * 0.40, mirrorW, mirrorH);
        gc.setFill(Color.web("#ffffff"));
        gc.fillRect(mirrorX + 1, -height * 0.52 + 1, mirrorW - 2, 1);
        gc.fillRect(mirrorX + 1, height * 0.40 + mirrorH - 2, mirrorW - 2, 1);

        // 7. Lights
        drawHeadTailLights(gc, width, height);

        // 8. Emergency Lightbar
        renderEmergencyFlash(gc, width, height);
    }

    private void renderTwoWheeler(GraphicsContext gc, String type, double width, double height) {
        boolean isBike = "bicycle".equals(type);
        
        // Draw tires first
        double tireRadius = height * 0.3;
        double rearTireX = -width * 0.35;
        double frontTireX = width * 0.35;
        
        gc.setFill(Color.web("#1e272e"));
        gc.fillOval(rearTireX - tireRadius, -tireRadius, tireRadius * 2, tireRadius * 2);
        gc.fillOval(frontTireX - tireRadius, -tireRadius, tireRadius * 2, tireRadius * 2);
        
        gc.setFill(Color.web("#dcdde1"));
        gc.fillOval(rearTireX - tireRadius * 0.4, -tireRadius * 0.4, tireRadius * 0.8, tireRadius * 0.8);
        gc.fillOval(frontTireX - tireRadius * 0.4, -tireRadius * 0.4, tireRadius * 0.8, tireRadius * 0.8);

        // Frame and Body
        if (isBike) {
            gc.setStroke(Color.web("#2ed573"));
            gc.setLineWidth(1.8);
            gc.strokeLine(rearTireX, 0, -width * 0.05, 0);
            gc.strokeLine(-width * 0.05, 0, 0, height * 0.05);
            gc.strokeLine(0, height * 0.05, frontTireX * 0.6, -height * 0.1);
            gc.strokeLine(frontTireX * 0.6, -height * 0.1, -width * 0.05, 0);
            gc.strokeLine(frontTireX * 0.6, -height * 0.1, frontTireX, 0);
            
            gc.setStroke(Color.web("#747d8c"));
            gc.setLineWidth(1.5);
            gc.strokeLine(frontTireX * 0.6, -height * 0.3, frontTireX * 0.6, height * 0.3);
            
            // Rider shoulders and helmet
            gc.setFill(Color.web("#54a0ff"));
            gc.fillOval(-width * 0.18, -height * 0.25, width * 0.32, height * 0.5);
            gc.setFill(Color.web("#feca57"));
            gc.fillOval(-width * 0.08, -height * 0.18, width * 0.2, height * 0.36);
            gc.setFill(Color.web("#2f3542"));
            gc.fillRect(width * 0.02, -height * 0.06, width * 0.06, height * 0.12);
        } else {
            LinearGradient motoGrad = new LinearGradient(
                0, -height * 0.3, 0, height * 0.3, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ff9f43")),
                new Stop(1, Color.web("#ee5253"))
            );
            gc.setFill(motoGrad);
            gc.fillRoundRect(-width * 0.28, -height * 0.26, width * 0.56, height * 0.52, 4, 4);
            gc.setStroke(Color.web("#d35400"));
            gc.setLineWidth(0.8);
            gc.strokeRoundRect(-width * 0.28, -height * 0.26, width * 0.56, height * 0.52, 4, 4);
            
            // Handlebars
            gc.setStroke(Color.web("#2f3542"));
            gc.setLineWidth(2.0);
            gc.strokeLine(width * 0.16, -height * 0.35, width * 0.16, height * 0.35);
            gc.setFill(Color.web("#1e272e"));
            gc.fillRect(width * 0.14, -height * 0.42, width * 0.04, height * 0.1);
            gc.fillRect(width * 0.14, height * 0.32, width * 0.04, height * 0.1);

            // Exhaust pipe
            gc.setFill(new LinearGradient(
                0, height * 0.2, 0, height * 0.36, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#f5f6fa")),
                new Stop(1, Color.web("#718093"))
            ));
            gc.fillRoundRect(-width * 0.4, height * 0.22, width * 0.25, height * 0.12, 1, 1);

            // Rider
            gc.setFill(Color.web("#2f3542"));
            gc.fillOval(-width * 0.14, -height * 0.3, width * 0.32, height * 0.6);
            gc.setFill(Color.web("#ffffff"));
            gc.fillOval(-width * 0.02, -height * 0.2, width * 0.2, height * 0.4);
            gc.setFill(Color.web("#1e272e"));
            gc.fillRoundRect(width * 0.08, -height * 0.12, width * 0.08, height * 0.24, 2, 2);
        }
    }

    private void drawWheels(GraphicsContext gc, double width, double height, int wheelCount) {
        double wheelWidth = Math.max(4, width * 0.16);
        double wheelHeight = Math.max(3, height * 0.24);
        
        double leftX = -width / 2.0 + width * 0.14;
        double rightX = width / 2.0 - width * 0.24;

        // Front-left wheel
        drawSingleWheel(gc, rightX, -height / 2.0 - wheelHeight * 0.25, wheelWidth, wheelHeight);
        // Front-right wheel
        drawSingleWheel(gc, rightX, height / 2.0 - wheelHeight * 0.75, wheelWidth, wheelHeight);
        // Rear-left wheel
        drawSingleWheel(gc, leftX, -height / 2.0 - wheelHeight * 0.25, wheelWidth, wheelHeight);
        // Rear-right wheel
        drawSingleWheel(gc, leftX, height / 2.0 - wheelHeight * 0.75, wheelWidth, wheelHeight);

        if (wheelCount >= 6) {
            double midX = -wheelWidth / 2.0;
            drawSingleWheel(gc, midX, -height / 2.0 - wheelHeight * 0.25, wheelWidth, wheelHeight);
            drawSingleWheel(gc, midX, height / 2.0 - wheelHeight * 0.75, wheelWidth, wheelHeight);
        }
    }

    private void drawSingleWheel(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setFill(Color.web("#1e272e"));
        gc.fillRoundRect(x, y, w, h, 2, 2);
        
        gc.setFill(Color.web("#dcdde1"));
        gc.fillRect(x + w * 0.15, y + h * 0.22, w * 0.7, h * 0.56);
        
        gc.setStroke(Color.web("#2f3542"));
        gc.setLineWidth(0.6);
        gc.strokeLine(x, y + h * 0.5, x + w, y + h * 0.5);
    }

    private void drawHeadTailLights(GraphicsContext gc, double width, double height) {
        gc.setFill(Color.web("#fff200"));
        double headW = Math.max(3, width * 0.08);
        double headH = Math.max(3, height * 0.18);
        double headX = width / 2.0 - headW;
        
        // Left headlight
        gc.fillOval(headX, -height * 0.32, headW, headH);
        // Right headlight
        gc.fillOval(headX, height * 0.32 - headH, headW, headH);
        
        // Subtle soft headlight beam cone extending forward
        double beamLength = width * 0.35;
        double beamSpread = height * 0.35;
        gc.setFill(Color.web("#fff200", 0.15));
        
        gc.fillPolygon(
            new double[]{ width / 2.0, width / 2.0 + beamLength, width / 2.0 + beamLength },
            new double[]{ -height * 0.25, -height * 0.25 - beamSpread, -height * 0.25 + beamSpread * 0.5 },
            3
        );
        gc.fillPolygon(
            new double[]{ width / 2.0, width / 2.0 + beamLength, width / 2.0 + beamLength },
            new double[]{ height * 0.25, height * 0.25 - beamSpread * 0.5, height * 0.25 + beamSpread },
            3
        );

        gc.setFill(Color.web("#ff3838"));
        double tailW = Math.max(2, width * 0.06);
        double tailH = Math.max(3, height * 0.18);
        double tailX = -width / 2.0;
        
        // Left tail light
        gc.fillRect(tailX, -height * 0.32, tailW, tailH);
        // Right tail light
        gc.fillRect(tailX, height * 0.32 - tailH, tailW, tailH);
    }

    private void renderEmergencyFlash(GraphicsContext gc, double width, double height) {
        boolean flashLeft = animationFrame % 6 < 3;
        
        gc.setFill(Color.web("#2f3542"));
        double barW = width * 0.06;
        double barH = height * 0.6;
        gc.fillRoundRect(-barW / 2.0, -barH / 2.0, barW, barH, 1, 1);

        gc.setEffect(new Glow(0.85));
        
        Color redColor = flashLeft ? Color.web("#ff3838", 1.0) : Color.web("#ff3838", 0.2);
        gc.setFill(redColor);
        gc.fillRect(-barW / 2.0, -barH / 2.0, barW, barH / 2.0);
        
        Color blueColor = !flashLeft ? Color.web("#17c0eb", 1.0) : Color.web("#17c0eb", 0.2);
        gc.setFill(blueColor);
        gc.fillRect(-barW / 2.0, 0, barW, barH / 2.0);
        
        if (flashLeft) {
            gc.setFill(Color.web("#ff3838", 0.22));
            gc.fillOval(-width * 0.3, -height * 0.7, width * 0.6, height * 1.4);
        } else {
            gc.setFill(Color.web("#17c0eb", 0.22));
            gc.fillOval(-width * 0.3, -height * 0.7, width * 0.6, height * 1.4);
        }

        gc.setEffect(null);
    }

    private void drawVehicleId(GraphicsContext gc, Vehicle vehicle, Point2D p, double height) {
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 10));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.BOTTOM);
        gc.setFill(Color.color(1, 1, 1, 0.85));
        gc.fillText(vehicle.getId(), p.getX(), p.getY() - height / 2.0 - 4);
    }

    private String vehicleType(Vehicle vehicle) {
        String simpleName = vehicle.getClass().getSimpleName().toLowerCase();
        String id = vehicle.getId() == null ? "" : vehicle.getId().toLowerCase();
        if (id.contains("violator") || (vehicle.getDrivingStrategy() instanceof vn.edu.hust.traffic.behavior.ViolatorDriver)) {
            return "violator";
        }
        String value = simpleName + " " + id;
        if (value.contains("fire")) {
            return "firetruck";
        }
        if (value.contains("ambulance") || value.contains("ambu") || value.contains("amb")) {
            return "ambulance";
        }
        if (value.contains("bus")) {
            return "bus";
        }
        if (value.contains("motor") || value.contains("moto")) {
            return "motorbike";
        }
        if (value.contains("bike") || value.contains("bicycle")) {
            return "bicycle";
        }
        return "car";
    }

    private Color colorFor(String type, boolean priority) {
        if ("ambulance".equals(type)) {
            return Color.WHITE;
        }
        if ("firetruck".equals(type)) {
            return Color.web("#d83a34");
        }
        if ("bus".equals(type)) {
            return Color.web("#2ca25f");
        }
        if ("motorbike".equals(type)) {
            return Color.web("#f2994a");
        }
        if ("bicycle".equals(type)) {
            return Color.web("#2d9c68");
        }
        if ("violator".equals(type)) {
            return Color.web("#e84393");
        }
        return Color.web("#2f80ed");
    }

    private Color textColor(String type) {
        if ("ambulance".equals(type)) {
            return Color.web("#9b1c1f");
        }
        return Color.WHITE;
    }

    private String labelFor(String type) {
        return switch (type) {
            case "ambulance" -> "Ambu";
            case "firetruck" -> "Fire";
            case "bus" -> "Bus";
            case "motorbike" -> "Moto";
            case "bicycle" -> "Bike";
            case "violator" -> "Viol";
            default -> "Car";
        };
    }
}
