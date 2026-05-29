package vn.edu.hust.traffic.view.renderer;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import vn.edu.hust.traffic.view.MapType;
import vn.edu.hust.traffic.view.camera.Camera;
import vn.edu.hust.traffic.controller.TrafficController;

public class RoadRenderer {
    private static final double CROSS_X = TrafficController.CROSS_X;
    private static final double THREE_WAY_X = TrafficController.THREE_WAY_X;
    private static final double TOP_CROSS_Y = TrafficController.TOP_CROSS_Y;
    private static final double BOTTOM_CROSS_Y = TrafficController.BOTTOM_CROSS_Y;
    private static final double CENTER_Y = BOTTOM_CROSS_Y;
    private static final double STOP_OFFSET = 120.0;
    private static final Color ROAD = Color.web("#3b3f46");
    private static final Color ROAD_DARK = Color.web("#30343a");
    private static final Color LANE = Color.web("#f1f3f2");
    private static final Color MEDIAN = Color.web("#f1c84b");
    private static final Color SIDEWALK = Color.web("#cdd4d1");

    public void render(GraphicsContext gc, Camera camera, MapType mapType) {
        drawWorldBackground(gc, camera);
        switch (mapType) {
            case T_INTERSECTION -> drawTIntersection(gc, camera);
            case CROSS_INTERSECTION -> drawCrossIntersection(gc, camera);
            case FIVE_WAY_INTERSECTION -> drawFiveWayIntersection(gc, camera);
            case ROAD_NETWORK -> drawRoadNetwork(gc, camera);
        }
    }

    private void drawWorldBackground(GraphicsContext gc, Camera camera) {
        fillWorldRect(gc, camera, camera.getWorldX(), camera.getWorldY(),
                camera.getWorldWidth(), camera.getWorldHeight(), Color.web("#dfe8df"));
    }

    private void drawCrossIntersection(GraphicsContext gc, Camera camera) {
        // Draw the connecting vertical road
        drawRoad(gc, camera, CROSS_X, (TOP_CROSS_Y + BOTTOM_CROSS_Y) / 2.0, (BOTTOM_CROSS_Y - TOP_CROSS_Y) + 1100, 160, 90);
        
        // Draw the bottom cross intersection at Y = BOTTOM_CROSS_Y
        drawCrossIntersectionAt(gc, camera, CROSS_X, BOTTOM_CROSS_Y, 0, 800, false);
        
        // Draw the top cross intersection at Y = TOP_CROSS_Y
        drawCrossIntersectionAt(gc, camera, CROSS_X, TOP_CROSS_Y, 0, 800, false);
    }

    private void drawTIntersection(GraphicsContext gc, Camera camera) {
        drawThreeWayIntersectionAt(gc, camera, THREE_WAY_X, 600, 1400);
    }

    private void drawFiveWayIntersection(GraphicsContext gc, Camera camera) {
        drawFiveWayRoundaboutAt(gc, camera, 600.0, 300.0);
    }

    private void drawFiveWayRoundaboutAt(GraphicsContext gc, Camera camera, double cx, double cy) {
        double[] angles = {
            0,                  // 0: Đông
            -Math.PI / 2.0,     // 1: Bắc
            Math.PI,            // 2: Tây
            Math.PI / 2.0,      // 3: Nam
            -Math.PI / 4.0      // 4: Đông Bắc
        };

        // 1. Draw the 5 roads connecting to the roundabout (width = 160)
        for (double theta : angles) {
            double thetaDeg = Math.toDegrees(theta);
            double rx = cx + 380 * Math.cos(theta);
            double ry = cy + 380 * Math.sin(theta);
            drawRoad(gc, camera, rx, ry, 480, 160, thetaDeg);
        }

        // 2. Draw the Roundabout asphalt circle
        fillWorldCircle(gc, camera, cx, cy, 190, SIDEWALK);
        fillWorldCircle(gc, camera, cx, cy, 180, ROAD_DARK);
        fillWorldCircle(gc, camera, cx, cy, 177, ROAD);

        // 3. Draw road lane markings and decorations
        for (double theta : angles) {
            // White dashed lane dividers at offsets -53, -27, 27, 53
            double[] laneLines = { -53, -27, 27, 53 };
            for (double offsetVal : laneLines) {
                double sx = cx + 180 * Math.cos(theta) - offsetVal * Math.sin(theta);
                double sy = cy + 180 * Math.sin(theta) + offsetVal * Math.cos(theta);
                double ex = cx + 600 * Math.cos(theta) - offsetVal * Math.sin(theta);
                double ey = cy + 600 * Math.sin(theta) + offsetVal * Math.cos(theta);
                strokeWorldLine(gc, camera, sx, sy, ex, ey, LANE, 1.0, true);
            }

            // Central solid double-yellow median line
            double[] medianLines = { -2, 2 };
            for (double offsetVal : medianLines) {
                double sx = cx + 180 * Math.cos(theta) - offsetVal * Math.sin(theta);
                double sy = cy + 180 * Math.sin(theta) + offsetVal * Math.cos(theta);
                double ex = cx + 600 * Math.cos(theta) - offsetVal * Math.sin(theta);
                double ey = cy + 600 * Math.sin(theta) + offsetVal * Math.cos(theta);
                strokeWorldLine(gc, camera, sx, sy, ex, ey, MEDIAN, 2.0, false);
            }
            
            // Zebra crossings (crosswalks) at distance 230
            double cwD = 230.0;
            for (double offsetVal = -72; offsetVal <= 72; offsetVal += 10) {
                double sx = cx + (cwD - 8) * Math.cos(theta) + offsetVal * Math.cos(theta + Math.PI/2);
                double sy = cy + (cwD - 8) * Math.sin(theta) + offsetVal * Math.sin(theta + Math.PI/2);
                double ex = cx + (cwD + 8) * Math.cos(theta) + offsetVal * Math.cos(theta + Math.PI/2);
                double ey = cy + (cwD + 8) * Math.sin(theta) + offsetVal * Math.sin(theta + Math.PI/2);
                strokeWorldLine(gc, camera, sx, sy, ex, ey, LANE, 4.0, false);
            }

            // Yield markings at entry
            double yieldD = 180.0;
            double sx = cx + yieldD * Math.cos(theta) + 0 * Math.sin(theta);
            double sy = cy + yieldD * Math.sin(theta) - 0 * Math.cos(theta);
            double ex = cx + yieldD * Math.cos(theta) + 80 * Math.sin(theta);
            double ey = cy + yieldD * Math.sin(theta) - 80 * Math.cos(theta);
            strokeWorldLine(gc, camera, sx, sy, ex, ey, LANE, 2.5, true);

            // Yield triangles painted on asphalt for each lane (offsets 15, 40, 65)
            double[] spawnOffsets = { 15.0, 40.0, 65.0 };
            for (double offsetVal : spawnOffsets) {
                double triD = 205.0;
                double triX = cx + triD * Math.cos(theta) + offsetVal * Math.sin(theta);
                double triY = cy + triD * Math.sin(theta) - offsetVal * Math.cos(theta);
                double cosT = Math.cos(theta);
                double sinT = Math.sin(theta);
                double cosP = Math.cos(theta + Math.PI/2);
                double sinP = Math.sin(theta + Math.PI/2);
                double[] tx = {
                    triX + 8 * cosT,
                    triX - 6 * cosT - 5 * cosP,
                    triX - 6 * cosT + 5 * cosP
                };
                double[] ty = {
                    triY + 8 * sinT,
                    triY - 6 * sinT - 5 * sinP,
                    triY - 6 * sinT + 5 * sinP
                };
                fillWorldPolygon(gc, camera, tx, ty, LANE);
            }
        }

        // 4. Concentric lane markings inside roundabout
        strokeWorldCircle(gc, camera, cx, cy, 150, LANE, 1.2, true);
        strokeWorldCircle(gc, camera, cx, cy, 120, LANE, 1.2, true);

        // 5. Central Island (Green space)
        fillWorldCircle(gc, camera, cx, cy, 95, SIDEWALK);
        fillWorldCircle(gc, camera, cx, cy, 85, Color.web("#6ab04c")); // grass
        strokeWorldCircle(gc, camera, cx, cy, 85, Color.web("#ffffff"), 1.8, false);

        // Flower pattern in center
        fillWorldCircle(gc, camera, cx, cy, 45, Color.web("#fbc531")); // flowerbed
        fillWorldCircle(gc, camera, cx, cy, 20, Color.web("#448844")); // central shrub
    }

    private void drawRoadNetwork(GraphicsContext gc, Camera camera) {
        // Draw horizontal roads: main at Y = BOTTOM_CROSS_Y, top at Y = TOP_CROSS_Y
        drawRoad(gc, camera, 800, BOTTOM_CROSS_Y, 1900, 160, 0);
        drawRoad(gc, camera, CROSS_X, TOP_CROSS_Y, 1100, 160, 0);

        // Draw vertical connecting road at X = CROSS_X
        drawRoad(gc, camera, CROSS_X, (TOP_CROSS_Y + BOTTOM_CROSS_Y) / 2.0, (BOTTOM_CROSS_Y - TOP_CROSS_Y) + 1100, 160, 90);

        // Draw vertical road connecting three1 up to roundabout (X = THREE_WAY_X)
        double verticalCenterY = (BOTTOM_CROSS_Y + TOP_CROSS_Y + 190) / 2.0;
        double verticalLength = BOTTOM_CROSS_Y - (TOP_CROSS_Y + 190) + 160;
        drawRoad(gc, camera, THREE_WAY_X, verticalCenterY, verticalLength, 160, 90);

        // Draw the 5-way roundabout at (THREE_WAY_X, TOP_CROSS_Y)
        drawFiveWayRoundaboutAt(gc, camera, THREE_WAY_X, TOP_CROSS_Y);

        drawNetworkTurnSlipRoads(gc, camera);

        drawHorizontalNetworkLaneMarkings(gc, camera);
        
        // Vertical lane markings for both cross intersections and T-junction
        drawVerticalIntersectionLaneMarkings(gc, camera, CROSS_X, BOTTOM_CROSS_Y, true, true);
        drawVerticalIntersectionLaneMarkings(gc, camera, CROSS_X, TOP_CROSS_Y, true, true);
        drawVerticalIntersectionLaneMarkings(gc, camera, THREE_WAY_X, BOTTOM_CROSS_Y, true, false);

        // Lane markings for the vertical road connecting three1 to roundabout
        drawLaneMarkings(gc, camera, false, THREE_WAY_X, TOP_CROSS_Y + 190, BOTTOM_CROSS_Y - STOP_OFFSET);

        // Arrows for both cross intersections and T-junction
        drawCrossIntersectionArrows(gc, camera, CROSS_X, BOTTOM_CROSS_Y);
        drawCrossIntersectionArrows(gc, camera, CROSS_X, TOP_CROSS_Y);
        drawThreeWayIntersectionArrows(gc, camera, THREE_WAY_X, BOTTOM_CROSS_Y);

        // Stop lines and crosswalks
        drawStopLines(gc, camera, CROSS_X, BOTTOM_CROSS_Y, true, true);
        drawStopLines(gc, camera, CROSS_X, TOP_CROSS_Y, true, true);
        drawStopLines(gc, camera, THREE_WAY_X, BOTTOM_CROSS_Y, true, false);

        drawCrosswalks(gc, camera, CROSS_X, BOTTOM_CROSS_Y, true, true);
        drawCrosswalks(gc, camera, CROSS_X, TOP_CROSS_Y, true, true);
        drawCrosswalks(gc, camera, THREE_WAY_X, BOTTOM_CROSS_Y, true, false);
    }

    private void drawNetworkTurnSlipRoads(GraphicsContext gc, Camera camera) {
        // Bottom cross at Y = BOTTOM_CROSS_Y
        drawIntersectionCorner(gc, camera, CROSS_X, BOTTOM_CROSS_Y, -1, -1);
        drawIntersectionCorner(gc, camera, CROSS_X, BOTTOM_CROSS_Y, 1, -1);
        drawIntersectionCorner(gc, camera, CROSS_X, BOTTOM_CROSS_Y, -1, 1);
        drawIntersectionCorner(gc, camera, CROSS_X, BOTTOM_CROSS_Y, 1, 1);

        // Top cross at Y = TOP_CROSS_Y
        drawIntersectionCorner(gc, camera, CROSS_X, TOP_CROSS_Y, -1, -1);
        drawIntersectionCorner(gc, camera, CROSS_X, TOP_CROSS_Y, 1, -1);
        drawIntersectionCorner(gc, camera, CROSS_X, TOP_CROSS_Y, -1, 1);
        drawIntersectionCorner(gc, camera, CROSS_X, TOP_CROSS_Y, 1, 1);

        // T-junction at Y = BOTTOM_CROSS_Y
        drawIntersectionCorner(gc, camera, THREE_WAY_X, BOTTOM_CROSS_Y, -1, -1);
        drawIntersectionCorner(gc, camera, THREE_WAY_X, BOTTOM_CROSS_Y, 1, -1);
    }

    private void drawCrossIntersectionAt(GraphicsContext gc, Camera camera, double centerX, double centerY, double fromX, double toX, boolean drawVerticalRoad) {
        drawRoad(gc, camera, centerX, centerY, toX - fromX + 100, 160, 0);
        if (drawVerticalRoad) {
            drawRoad(gc, camera, centerX, centerY, 720, 160, 90);
        }
        drawIntersectionCorner(gc, camera, centerX, centerY, -1, -1);
        drawIntersectionCorner(gc, camera, centerX, centerY, 1, -1);
        drawIntersectionCorner(gc, camera, centerX, centerY, -1, 1);
        drawIntersectionCorner(gc, camera, centerX, centerY, 1, 1);
        drawLaneMarkings(gc, camera, true, centerY, fromX - 50, centerX - STOP_OFFSET);
        drawLaneMarkings(gc, camera, true, centerY, centerX + STOP_OFFSET, toX + 50);
        drawVerticalIntersectionLaneMarkings(gc, camera, centerX, centerY, true, true);
        drawCrossIntersectionArrows(gc, camera, centerX, centerY);
        drawStopLines(gc, camera, centerX, centerY, true, true);
        drawCrosswalks(gc, camera, centerX, centerY, true, true);
    }

    private void drawThreeWayIntersectionAt(GraphicsContext gc, Camera camera, double centerX, double fromX, double toX) {
        drawRoad(gc, camera, (fromX + toX) / 2.0, CENTER_Y, toX - fromX + 100, 160, 0);
        drawRoad(gc, camera, centerX, CENTER_Y - 165, 430, 160, 90);
        fillWorldRect(gc, camera, centerX - 80, CENTER_Y - 80, 160, 160, ROAD);
        drawIntersectionCorner(gc, camera, centerX, CENTER_Y, -1, -1);
        drawIntersectionCorner(gc, camera, centerX, CENTER_Y, 1, -1);
        drawLaneMarkings(gc, camera, true, CENTER_Y, fromX - 50, centerX - STOP_OFFSET);
        drawLaneMarkings(gc, camera, true, CENTER_Y, centerX + STOP_OFFSET, toX + 50);
        drawVerticalIntersectionLaneMarkings(gc, camera, centerX, CENTER_Y, true, false);
        drawThreeWayIntersectionArrows(gc, camera, centerX, CENTER_Y);
        drawStopLines(gc, camera, centerX, CENTER_Y, true, false);
        drawCrosswalks(gc, camera, centerX, CENTER_Y, true, false);
    }

    private void drawHorizontalNetworkLaneMarkings(GraphicsContext gc, Camera camera) {
        // Bottom horizontal road (at Y = BOTTOM_CROSS_Y)
        drawLaneMarkings(gc, camera, true, BOTTOM_CROSS_Y, -150, CROSS_X - STOP_OFFSET);
        drawLaneMarkings(gc, camera, true, BOTTOM_CROSS_Y, CROSS_X + STOP_OFFSET, THREE_WAY_X - STOP_OFFSET);
        drawLaneMarkings(gc, camera, true, BOTTOM_CROSS_Y, THREE_WAY_X + STOP_OFFSET, 1750);

        // Top horizontal road (at Y = TOP_CROSS_Y)
        drawLaneMarkings(gc, camera, true, TOP_CROSS_Y, -150, CROSS_X - STOP_OFFSET);
        drawLaneMarkings(gc, camera, true, TOP_CROSS_Y, CROSS_X + STOP_OFFSET, 850);
    }

    private void drawVerticalIntersectionLaneMarkings(GraphicsContext gc, Camera camera,
            double centerX, double centerY, boolean includeNorth, boolean includeSouth) {
        if (includeNorth) {
            drawLaneMarkings(gc, camera, false, centerX, TOP_CROSS_Y - 600, centerY - STOP_OFFSET);
        }
        if (includeSouth) {
            drawLaneMarkings(gc, camera, false, centerX, centerY + STOP_OFFSET, 900);
        }
    }

    private void drawCrossIntersectionArrows(GraphicsContext gc, Camera camera, double centerX, double centerY) {
        drawLaneArrows(gc, camera, centerX - STOP_OFFSET, centerY, 0, "left", "straight", "straight_right");
        drawLaneArrows(gc, camera, centerX + STOP_OFFSET, centerY, 180, "left", "straight", "straight_right");
        drawLaneArrows(gc, camera, centerX, centerY - STOP_OFFSET, 90, "left", "straight", "straight_right");
        drawLaneArrows(gc, camera, centerX, centerY + STOP_OFFSET, 270, "left", "straight", "straight_right");
    }

    private void drawThreeWayIntersectionArrows(GraphicsContext gc, Camera camera, double centerX, double centerY) {
        drawLaneArrows(gc, camera, centerX - STOP_OFFSET, centerY, 0, "left", "straight", "straight");
        drawLaneArrows(gc, camera, centerX + STOP_OFFSET, centerY, 180, "straight", "straight", "straight_right");
        drawLaneArrows(gc, camera, centerX, centerY - STOP_OFFSET, 90, "left", "left_right", "right");
    }

    private void drawRoad(GraphicsContext gc, Camera camera, double centerX, double centerY,
            double length, double width, double angleDegrees) {
        Point2D p = camera.worldToScreen(centerX, centerY);
        double scale = camera.getScale();

        gc.save();
        gc.translate(p.getX(), p.getY());
        gc.rotate(angleDegrees);
        gc.setFill(SIDEWALK);
        gc.fillRect(-length * scale / 2.0, -width * scale / 2.0 - 10 * scale, length * scale, (width + 20) * scale);
        gc.setFill(ROAD_DARK);
        gc.fillRect(-length * scale / 2.0, -width * scale / 2.0, length * scale, width * scale);
        gc.setFill(ROAD);
        gc.fillRect(-length * scale / 2.0, -width * scale / 2.0 + 3 * scale, length * scale, (width - 6) * scale);
        gc.restore();
    }

    private void drawLaneMarkings(GraphicsContext gc, Camera camera, boolean horizontal,
            double center, double from, double to) {
        double[] offsets = { -53, -27, 27, 53 };
        for (double offset : offsets) {
            if (horizontal) {
                strokeWorldLine(gc, camera, from, center + offset, to, center + offset, LANE, 1.0, true);
            } else {
                strokeWorldLine(gc, camera, center + offset, from, center + offset, to, LANE, 1.0, true);
            }
        }

        if (horizontal) {
            strokeWorldLine(gc, camera, from, center - 2, to, center - 2, MEDIAN, 2.0, false);
            strokeWorldLine(gc, camera, from, center + 2, to, center + 2, MEDIAN, 2.0, false);
        } else {
            strokeWorldLine(gc, camera, center - 2, from, center - 2, to, MEDIAN, 2.0, false);
            strokeWorldLine(gc, camera, center + 2, from, center + 2, to, MEDIAN, 2.0, false);
        }
    }

    private void drawStopLines(GraphicsContext gc, Camera camera, double centerX, double centerY, boolean includeNorth, boolean includeSouth) {
        strokeWorldLine(gc, camera, centerX - STOP_OFFSET, centerY + 5, centerX - STOP_OFFSET, centerY + 75, LANE, 4.0, false);
        strokeWorldLine(gc, camera, centerX + STOP_OFFSET, centerY - 5, centerX + STOP_OFFSET, centerY - 75, LANE, 4.0, false);
        if (includeNorth) {
            strokeWorldLine(gc, camera, centerX - 75, centerY - STOP_OFFSET, centerX - 5, centerY - STOP_OFFSET, LANE, 4.0, false);
        }
        if (includeSouth) {
            strokeWorldLine(gc, camera, centerX + 75, centerY + STOP_OFFSET, centerX + 5, centerY + STOP_OFFSET, LANE, 4.0, false);
        }
    }

    private void drawLaneArrows(GraphicsContext gc, Camera camera, double x, double y, double rotationDegree,
            String lane1, String lane2, String lane3) {
        Point2D p = camera.worldToScreen(x, y);
        double scale = camera.getScale();

        gc.save();
        gc.translate(p.getX(), p.getY());
        gc.rotate(rotationDegree);
        gc.scale(scale, scale);
        gc.setStroke(LANE);
        gc.setLineWidth(2.0);
        gc.setLineDashes(new double[0]);

        double arrowX = -25;
        drawSingleArrow(gc, arrowX, 15, lane1);
        drawSingleArrow(gc, arrowX, 40, lane2);
        drawSingleArrow(gc, arrowX, 65, lane3);

        gc.restore();
    }

    private void drawSingleArrow(GraphicsContext gc, double x, double y, String type) {
        if ("none".equals(type)) {
            return;
        }

        gc.save();
        gc.translate(x, y);

        if ("straight".equals(type)) {
            gc.strokeLine(-10, 0, 10, 0);
            gc.strokeLine(10, 0, 5, -4);
            gc.strokeLine(10, 0, 5, 4);
        } else if ("left".equals(type)) {
            gc.strokeLine(-10, 0, 3, 0);
            gc.strokeLine(3, 0, 3, -10);
            gc.strokeLine(3, -10, 0, -7);
            gc.strokeLine(3, -10, 6, -7);
        } else if ("right".equals(type)) {
            gc.strokeLine(-10, 0, 3, 0);
            gc.strokeLine(3, 0, 3, 10);
            gc.strokeLine(3, 10, 0, 7);
            gc.strokeLine(3, 10, 6, 7);
        } else if ("left_right".equals(type)) {
            gc.strokeLine(-10, 0, 3, 0);
            gc.strokeLine(3, 0, 3, -10);
            gc.strokeLine(3, -10, 0, -7);
            gc.strokeLine(3, -10, 6, -7);
            gc.strokeLine(3, 0, 3, 10);
            gc.strokeLine(3, 10, 0, 7);
            gc.strokeLine(3, 10, 6, 7);
        } else if ("straight_right".equals(type)) {
            gc.strokeLine(-10, 0, 10, 0);
            gc.strokeLine(10, 0, 5, -4);
            gc.strokeLine(10, 0, 5, 4);
            gc.strokeLine(3, 0, 3, 10);
            gc.strokeLine(3, 10, 0, 7);
            gc.strokeLine(3, 10, 6, 7);
        }

        gc.restore();
    }

    private void drawCrosswalks(GraphicsContext gc, Camera camera, double centerX, double centerY, boolean includeNorth, boolean includeSouth) {
        drawZebraCrossing(gc, camera, centerX - 95, centerY - 75, centerX - 85, centerY + 75, true);
        drawZebraCrossing(gc, camera, centerX + 85, centerY - 75, centerX + 95, centerY + 75, true);
        if (includeNorth) {
            drawZebraCrossing(gc, camera, centerX - 75, centerY - 95, centerX + 75, centerY - 85, false);
        }
        if (includeSouth) {
            drawZebraCrossing(gc, camera, centerX - 75, centerY + 85, centerX + 75, centerY + 95, false);
        }
    }

    private void drawZebraCrossing(GraphicsContext gc, Camera camera, double startX, double startY,
            double endX, double endY, boolean horizontalRoad) {
        if (horizontalRoad) {
            for (double y = startY; y <= endY; y += 12) {
                strokeWorldLine(gc, camera, startX, y, endX, y, LANE, 5.0, false);
            }
        } else {
            for (double x = startX; x <= endX; x += 12) {
                strokeWorldLine(gc, camera, x, startY, x, endY, LANE, 5.0, false);
            }
        }
    }

    private void drawIntersectionCorner(GraphicsContext gc, Camera camera, double centerX, double centerY,
            double signX, double signY) {
        fillWorldPolygon(gc, camera,
                new double[] { centerX + signX * 250, centerX + signX * 80, centerX + signX * 80 },
                new double[] { centerY + signY * 80, centerY + signY * 250, centerY + signY * 80 },
                ROAD_DARK);
        strokeWorldLine(gc, camera,
                centerX + signX * 250, centerY + signY * 80,
                centerX + signX * 80, centerY + signY * 250,
                Color.web("#8f969d"), 2.0, false);

        fillWorldPolygon(gc, camera,
                new double[] { centerX + signX * 186, centerX + signX * 80, centerX + signX * 80 },
                new double[] { centerY + signY * 80, centerY + signY * 186, centerY + signY * 80 },
                Color.web("#a06050"));
        strokeWorldPolygon(gc, camera,
                new double[] { centerX + signX * 186, centerX + signX * 80, centerX + signX * 80 },
                new double[] { centerY + signY * 80, centerY + signY * 186, centerY + signY * 80 },
                Color.web("#dddddd"), 2.0);

        strokeWorldLine(gc, camera,
                centerX + signX * 218, centerY + signY * 80,
                centerX + signX * 80, centerY + signY * 218,
                LANE, 2.0, true);
    }

    private void drawDashedCenterLine(GraphicsContext gc, Camera camera, double centerX, double centerY,
            double length, double angleDegrees, Color color) {
        Point2D p = camera.worldToScreen(centerX, centerY);
        double scale = camera.getScale();

        gc.save();
        gc.translate(p.getX(), p.getY());
        gc.rotate(angleDegrees);
        gc.setStroke(color);
        gc.setLineWidth(Math.max(1.0, 2.0 * scale));
        gc.setLineDashes(18.0 * scale, 14.0 * scale);
        gc.strokeLine(-length * scale / 2.0, 0, length * scale / 2.0, 0);
        gc.setLineDashes(new double[0]);
        gc.restore();
    }

    private void fillWorldRect(GraphicsContext gc, Camera camera, double x, double y,
            double width, double height, Color color) {
        Point2D p = camera.worldToScreen(x, y);
        gc.setFill(color);
        gc.fillRect(p.getX(), p.getY(), width * camera.getScale(), height * camera.getScale());
    }

    private void fillWorldPolygon(GraphicsContext gc, Camera camera, double[] worldX, double[] worldY, Color color) {
        double[] screenX = new double[worldX.length];
        double[] screenY = new double[worldY.length];
        for (int i = 0; i < worldX.length; i++) {
            Point2D p = camera.worldToScreen(worldX[i], worldY[i]);
            screenX[i] = p.getX();
            screenY[i] = p.getY();
        }
        gc.setFill(color);
        gc.fillPolygon(screenX, screenY, worldX.length);
    }

    private void strokeWorldPolygon(GraphicsContext gc, Camera camera, double[] worldX, double[] worldY,
            Color color, double width) {
        double[] screenX = new double[worldX.length];
        double[] screenY = new double[worldY.length];
        for (int i = 0; i < worldX.length; i++) {
            Point2D p = camera.worldToScreen(worldX[i], worldY[i]);
            screenX[i] = p.getX();
            screenY[i] = p.getY();
        }
        gc.setStroke(color);
        gc.setLineWidth(Math.max(1.0, width * camera.getScale()));
        gc.strokePolygon(screenX, screenY, worldX.length);
    }

    private void strokeWorldLine(GraphicsContext gc, Camera camera, double x1, double y1,
            double x2, double y2, Color color, double width, boolean dashed) {
        Point2D p1 = camera.worldToScreen(x1, y1);
        Point2D p2 = camera.worldToScreen(x2, y2);
        gc.setStroke(color);
        gc.setLineWidth(Math.max(1.0, width * camera.getScale()));
        if (dashed) {
            gc.setLineDashes(14.0 * camera.getScale(), 12.0 * camera.getScale());
        } else {
            gc.setLineDashes(new double[0]);
        }
        gc.strokeLine(p1.getX(), p1.getY(), p2.getX(), p2.getY());
        gc.setLineDashes(new double[0]);
    }

    private void fillWorldCircle(GraphicsContext gc, Camera camera, double cx, double cy, double r, Color color) {
        Point2D screenCenter = camera.worldToScreen(cx, cy);
        double screenR = r * camera.getScale();
        gc.setFill(color);
        gc.fillOval(screenCenter.getX() - screenR, screenCenter.getY() - screenR, screenR * 2, screenR * 2);
    }

    private void strokeWorldCircle(GraphicsContext gc, Camera camera, double cx, double cy, double r, Color color, double width, boolean dashed) {
        Point2D screenCenter = camera.worldToScreen(cx, cy);
        double screenR = r * camera.getScale();
        gc.setStroke(color);
        gc.setLineWidth(Math.max(1.0, width * camera.getScale()));
        if (dashed) {
            gc.setLineDashes(14.0 * camera.getScale(), 12.0 * camera.getScale());
        } else {
            gc.setLineDashes(new double[0]);
        }
        gc.strokeOval(screenCenter.getX() - screenR, screenCenter.getY() - screenR, screenR * 2, screenR * 2);
        gc.setLineDashes(new double[0]);
    }
}
