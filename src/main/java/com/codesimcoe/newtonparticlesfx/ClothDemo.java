package com.codesimcoe.newtonparticlesfx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class ClothDemo extends Application {

    // --- simulation settings ---
    static final int W = 900;
    static final int H = 600;

    static final int COLS = 45;
    static final int ROWS = 30;

    static final double SPACING = 12;
    static final double GRAVITY = 0.6;
    static final int ITERATIONS = 18;

    static final double MOUSE_RADIUS = 80;
    static final double MOUSE_STRENGTH = 1.2;

    // --- data ---
    Particle[] particles;
    List<Constraint> constraints = new ArrayList<>();

    double mouseX, mouseY;
    double prevMouseX, prevMouseY;
    boolean mouseDown = false;

    @Override
    public void start(Stage stage) {

        Canvas canvas = new Canvas(W, H);
        GraphicsContext g = canvas.getGraphicsContext2D();

        initCloth();

        canvas.setOnMouseMoved(this::onMouseMoved);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMousePressed(e -> mouseDown = true);
        canvas.setOnMouseReleased(e -> mouseDown = false);

        Pane root = new Pane(canvas);
        stage.setScene(new Scene(root));
        stage.setTitle("Verlet Cloth Demo (Valhalla-friendly)");
        stage.show();

        new javafx.animation.AnimationTimer() {
            @Override
            public void handle(long now) {
                update();
                render(g);
            }
        }.start();
    }

    // ----------------------------
    // Initialization
    // ----------------------------
    void initCloth() {
        particles = new Particle[COLS * ROWS];

        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {

                double px = 200 + x * SPACING;
                double py = 50 + y * SPACING;

                boolean pinned = (y == 0 && x % 2 == 0); // top row pinned

                particles[index(x, y)] = new Particle(px, py, pinned);
            }
        }

        // structural constraints
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {

                if (x < COLS - 1)
                    constraints.add(new Constraint(index(x, y), index(x + 1, y)));

                if (y < ROWS - 1)
                    constraints.add(new Constraint(index(x, y), index(x, y + 1)));
            }
        }
    }

    int index(int x, int y) {
        return y * COLS + x;
    }

    // ----------------------------
    // Simulation
    // ----------------------------
    void update() {

        // integrate
        for (Particle p : particles) {
            if (p.pinned) continue;

            double vx = p.x - p.px;
            double vy = p.y - p.py;

            p.px = p.x;
            p.py = p.y;

            p.x += vx;
            p.y += vy + GRAVITY;
        }

        // mouse interaction
        if (mouseDown) {
            double dx = mouseX - prevMouseX;
            double dy = mouseY - prevMouseY;

            for (Particle p : particles) {
                double distX = p.x - mouseX;
                double distY = p.y - mouseY;

                double dist2 = distX * distX + distY * distY;

                if (dist2 < MOUSE_RADIUS * MOUSE_RADIUS) {
                    p.x += dx * MOUSE_STRENGTH;
                    p.y += dy * MOUSE_STRENGTH;
                }
            }
        }

        prevMouseX = mouseX;
        prevMouseY = mouseY;

        // constraints solver (XPBD-style)
        for (int i = 0; i < ITERATIONS; i++) {
            for (Constraint c : constraints) {

                Particle a = particles[c.a];
                Particle b = particles[c.b];

                double dx = b.x - a.x;
                double dy = b.y - a.y;

                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist == 0) continue;

                double diff = (dist - c.restLength) / dist;

                double offsetX = dx * 0.5 * diff;
                double offsetY = dy * 0.5 * diff;

                if (!a.pinned) {
                    a.x += offsetX;
                    a.y += offsetY;
                }

                if (!b.pinned) {
                    b.x -= offsetX;
                    b.y -= offsetY;
                }
            }
        }
    }

    // ----------------------------
    // Rendering
    // ----------------------------
    void render(GraphicsContext g) {
        g.setFill(Color.rgb(15, 15, 20));
        g.fillRect(0, 0, W, H);

        g.setStroke(Color.LIGHTGRAY);
        g.setLineWidth(1.0);

        for (Constraint c : constraints) {
            Particle a = particles[c.a];
            Particle b = particles[c.b];

            g.strokeLine(a.x, a.y, b.x, b.y);
        }

        // draw pinned points
        g.setFill(Color.ORANGE);
        for (Particle p : particles) {
            if (p.pinned) {
                g.fillOval(p.x - 3, p.y - 3, 6, 6);
            }
        }
    }

    // ----------------------------
    // Input
    // ----------------------------
    void onMouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    void onMouseDragged(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    // ----------------------------
    // Data types
    // ----------------------------
    static class Particle {
        double x, y;
        double px, py;
        boolean pinned;

        Particle(double x, double y, boolean pinned) {
            this.x = this.px = x;
            this.y = this.py = y;
            this.pinned = pinned;
        }
    }

    static class Constraint {
        int a, b;
        double restLength;

        Constraint(int a, int b) {
            this.a = a;
            this.b = b;
            this.restLength = SPACING;
        }
    }

    public static void main(String[] args) {
        launch();
    }
}