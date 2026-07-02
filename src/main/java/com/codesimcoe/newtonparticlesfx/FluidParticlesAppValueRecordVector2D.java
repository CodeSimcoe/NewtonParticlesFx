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

public class FluidParticlesAppValueRecordVector2D extends Application {

  // --- CONFIG ---
  static final int WIDTH = 1_200;
  static final int HEIGHT = 800;
  static final int N = 50_000;

  static final float DAMPING = 0.98f;
  static final float MOUSE_FORCE = 120.0f;
  static final float MAX_FORCE = 0.6f;

  // --- PARTICLE MODEL (record) ---
  @LooselyConsistentValue
  value record Vector2D(float x, float y) {
    Vector2D add(Vector2D other) {
      return new Vector2D(this.x + other.x, this.y + other.y);
    }

    Vector2D negate() {
      return new Vector2D(-x, -y);
    }

    float norm2() {
      return x * x + y * y;
    }

    double norm() {
      return Math.sqrt(norm2());
    }
  }

  @LooselyConsistentValue
  value record Particle(Vector2D! position, Vector2D! velocity) {

    Particle applyForce(Vector2D! delta, float force, float damping) {
      float nvx = (velocity.x + delta.x * force) * damping;
      float nvy = (velocity.y + delta.y * force) * damping;
      return new Particle(position, new Vector2D(nvx, nvy));
    }

    Particle integrate() {
      Vector2D position = this.position.add(this.velocity);
      return new Particle(position, this.velocity);
    }

    Particle bounce(int width, int height) {

      float nx = position.x;
      float ny = position.y;
      float nvx = velocity.x;
      float nvy = velocity.y;

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

      return new Particle(new Vector2D(nx, ny), new Vector2D(nvx, nvy));
    }

    float speed() {
      return (float) velocity.norm();
    }

    float normalizedSpeed(float max) {
      return Math.min(1.0f, speed() / max);
    }
  }

//  Particle[] particles = new Particle[N];

  // Particle![]
  Particle[] particles = (Particle[]) ValueClass.newNullRestrictedNonAtomicArray(
    Particle.class,
    N,
    new Particle(new Vector2D(0, 0), new Vector2D(0, 0))
  );

  Vector2D mouse = new Vector2D(WIDTH / 2.0f, HEIGHT / 2.0f);
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
        new Vector2D(
        r.nextFloat() * WIDTH,
        r.nextFloat() * HEIGHT),
        new Vector2D(
        (r.nextFloat() - 0.5f) * 1.5f,
        (r.nextFloat() - 0.5f) * 1.5f)
      );
    }
  }

  void update() {

    for (int i = 0; i < N; i++) {

      Particle p = particles[i];

      Vector2D delta = p.position.negate().add(mouse);

//      float dx = (float) mouseX - p.position.x();
//      float dy = (float) mouseY - p.position.y();

//      float dist2 = dx * dx + dy * dy;
      float dist2 = delta.norm2();
      dist2 = Math.max(dist2, 0.0001f);

      float force = MOUSE_FORCE / dist2;

      if (!mouseDown) force *= 0.4f;

      force = Math.min(force, MAX_FORCE);

      p = p.applyForce(delta, force, DAMPING);
      p = p.integrate();
      p = p.bounce(WIDTH, HEIGHT);

      particles[i] = p;
    }
  }

  void render(GraphicsContext g) {

    g.setFill(Color.rgb(0, 0, 0, 0.25));
    g.fillRect(0, 0, WIDTH, HEIGHT);

    for (Particle p : particles) {
      // Color based on speed
      int idx = Math.clamp((int) (p.normalizedSpeed(6.0f) * 255), 0, 255);
      g.setFill(ColorUtils.COLOR_LEVELS[idx]);
      g.fillOval(p.position().x(), p.position().y(), 2, 2);
    }

    g.setFill(Color.WHITE);

    Vector2D center = mouse.add(new Vector2D(-4, -4));
    g.fillOval(center.x, center.y, 8, 8);
  }

  void onMouseMove(MouseEvent e) {
    mouse = new Vector2D((float) e.getX(), (float) e.getY());
  }

  public static void main(String[] args) {
    launch();
  }
}