package com.codesimcoe.newtonparticlesfx;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.Random;

public class FluidParticlesAppPrimitives extends Application {

    // --- CONFIG ---
    static final int WIDTH = 1_200;
    static final int HEIGHT = 800;
    static final int N = 50_000; // baisse à 5000 si machine lente

    static final float DAMPING = 0.98f;
    static final float MOUSE_FORCE = 120.0f;
    static final float MAX_FORCE = 0.6f;

    // --- PARTICLES ---
    float[] x = new float[N];
    float[] y = new float[N];
    float[] vx = new float[N];
    float[] vy = new float[N];

    double mouseX = WIDTH / 2.0;
    double mouseY = HEIGHT / 2.0;
    boolean mouseDown = false;

    Random r = new Random();

    @Override
    public void start(Stage stage) {

        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext g = canvas.getGraphicsContext2D();

        initParticles();

        Scene scene = new Scene(new StackPane(canvas));

        scene.setOnMouseMoved(this::onMouseMove);
        scene.setOnMouseDragged(this::onMouseMove);
        scene.setOnMousePressed(_ -> mouseDown = true);
        scene.setOnMouseReleased(_ -> mouseDown = false);

        stage.setScene(scene);
        stage.setTitle("Fluid Particles (Valhalla demo style)");
        stage.show();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                update();
                render(g);
            }
        }.start();
    }

    void initParticles() {
        for (int i = 0; i < N; i++) {
            x[i] = r.nextFloat() * WIDTH;
            y[i] = r.nextFloat() * HEIGHT;
            vx[i] = (r.nextFloat() - 0.5f) * 1.5f;
            vy[i] = (r.nextFloat() - 0.5f) * 1.5f;
        }
    }

    void update() {
        for (int i = 0; i < N; i++) {

            float dx = (float) mouseX - x[i];
            float dy = (float) mouseY - y[i];

            float dist2 = dx * dx + dy * dy + 0.0001f;

            float force = MOUSE_FORCE / dist2;

            if (!mouseDown) force *= 0.4f;

            force = Math.min(force, MAX_FORCE);

            vx[i] += dx * force;
            vy[i] += dy * force;

            vx[i] *= DAMPING;
            vy[i] *= DAMPING;

            x[i] += vx[i];
            y[i] += vy[i];

//            // wrap screen
//            if (x[i] < 0) x[i] = WIDTH;
//            if (x[i] > WIDTH) x[i] = 0;
//            if (y[i] < 0) y[i] = HEIGHT;
//            if (y[i] > HEIGHT) y[i] = 0;

            // bounce on walls
            if (x[i] < 0) {
                x[i] = 0;
                vx[i] = -vx[i];
            } else if (x[i] > WIDTH) {
                x[i] = WIDTH;
                vx[i] = -vx[i];
            }

            if (y[i] < 0) {
                y[i] = 0;
                vy[i] = -vy[i];
            } else if (y[i] > HEIGHT) {
                y[i] = HEIGHT;
                vy[i] = -vy[i];
            }
        }
    }

    void render(GraphicsContext g) {

        // fade trail effect
        g.setFill(Color.rgb(0, 0, 0, 0.25));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        for (int i = 0; i < N; i++) {

//            float speed = vx[i] * vx[i] + vy[i] * vy[i];
//
//            double brightness = Math.min(1.0, speed * 2.0);
//
//            g.setFill(Color.hsb(200 + brightness * 160, 1.0, brightness));
//
//            g.fillOval(x[i], y[i], 2, 2);

            float vx2 = vx[i] * vx[i];
            float vy2 = vy[i] * vy[i];

            float speed = (float) Math.sqrt(vx2 + vy2);

            // normalize (tune max speed)
            float t = Math.min(1.0f, speed / 6.0f);

            // hue: blue -> cyan -> yellow -> red
            float hue = (1.0f - t) * 220.0f;

            float brightness = 0.4f + t * 0.6f;

            g.setFill(Color.hsb(hue, 1.0, brightness));
            g.fillOval(x[i], y[i], 2, 2);
        }

        // mouse attractor visualization
        g.setFill(Color.WHITE);
        g.fillOval(mouseX - 4, mouseY - 4, 8, 8);
    }

    void onMouseMove(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    void main() {
        launch();
    }
}