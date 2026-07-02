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
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

import java.util.Random;

public class FluidParticlesAppValueRecord extends Application {

    // --- CONFIG ---
    static final int WIDTH = 1_200;
    static final int HEIGHT = 800;
    static final int N = 50_000;

    static final float DAMPING = 0.98f;
    static final float MOUSE_FORCE = 120.0f;
    static final float MAX_FORCE = 0.6f;

    static final Color FILL = Color.rgb(0, 0, 0, 0.25);

    // --- PARTICLE MODEL (record) ---
    @LooselyConsistentValue
    value record Particle(float x, float y, float vx, float vy) {

        Particle applyForce(float dx, float dy, float force, float damping) {
            float nvx = (vx + dx * force) * damping;
            float nvy = (vy + dy * force) * damping;
            return new Particle(x, y, nvx, nvy);
        }

        Particle integrate() {
            return new Particle(x + vx, y + vy, vx, vy);
        }

        Particle bounce(int width, int height) {

            float nx = x;
            float ny = y;
            float nvx = vx;
            float nvy = vy;

            if (nx < 0) {
                nx = 0;
                nvx = -nvx;
            } else if (nx > width) {
                nx = width;
                nvx = -nvx;
            }

            if (ny < 0) {
                ny = 0;
                nvy = -nvy;
            } else if (ny > height) {
                ny = height;
                nvy = -nvy;
            }

            return new Particle(nx, ny, nvx, nvy);
        }

        float speed() {
            return (float) Math.sqrt(vx * vx + vy * vy);
        }

        float normalizedSpeed(float max) {
            return Math.min(1.0f, speed() / max);
        }
    }

//    Particle[] particles = new Particle[N];
    // Particle![]
    Particle[] particles = (Particle[]) ValueClass.newNullRestrictedNonAtomicArray(
      Particle.class,
      N,
      new Particle(0, 0, 0, 0)
    );

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
        stage.setTitle("Fluid Particles (record version)");
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
            particles[i] = new Particle(
                    r.nextFloat() * WIDTH,
                    r.nextFloat() * HEIGHT,
                    (r.nextFloat() - 0.5f) * 1.5f,
                    (r.nextFloat() - 0.5f) * 1.5f
            );
        }
    }

    void update() {

        for (int i = 0; i < N; i++) {

            Particle p = particles[i];

            float dx = (float) mouseX - p.x();
            float dy = (float) mouseY - p.y();

            float dist2 = dx * dx + dy * dy;
            dist2 = Math.max(dist2, 0.0001f);

            float force = MOUSE_FORCE / dist2;

            if (!mouseDown) force *= 0.4f;

            force = Math.min(force, MAX_FORCE);

            p = p.applyForce(dx, dy, force, DAMPING);
            p = p.integrate();
            p = p.bounce(WIDTH, HEIGHT);

            particles[i] = p;
        }
    }

    void render(GraphicsContext g) {

        g.setFill(FILL);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        for (Particle p : particles) {
            // Color based on speed
            int idx = Math.clamp((int) (p.normalizedSpeed(6.0f) * 255), 0, 255);
            g.setFill(ColorUtils.COLOR_LEVELS[idx]);

            g.fillOval(p.x(), p.y(), 2, 2);
        }

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