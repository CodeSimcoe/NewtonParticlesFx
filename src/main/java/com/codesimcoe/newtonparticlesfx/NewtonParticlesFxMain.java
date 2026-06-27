package com.codesimcoe.newtonparticlesfx;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class NewtonParticlesFxMain extends Application {

  private static final int W = 800;
  private static final int H = 600;

  private List<Body> bodies;

  @Override
  public void start(Stage primaryStage) {

    primaryStage.setTitle("Newton N-Body FX");

    Canvas canvas = new Canvas(W, H);
    GraphicsContext gc = canvas.getGraphicsContext2D();

    Pane root = new Pane(canvas);
    Scene scene = new Scene(root, W, H);

    primaryStage.setScene(scene);
    primaryStage.show();

    bodies = initBodies();

    AnimationTimer timer = new AnimationTimer() {

      long last = System.nanoTime();

      @Override
      public void handle(long now) {
        double dt = (now - last) * 1e-9;
        last = now;

        stepSimulation(dt);
        render(gc);
      }
    };

    timer.start();
  }

  // ----------------------------
  // Simulation
  // ----------------------------

  void stepSimulation(double dt) {

    List<Body> next = new ArrayList<>(bodies.size());

    for (Body b : bodies) {
      next.add(step(b, bodies, dt));
    }

    bodies = next;
  }

  Body step(Body b, List<Body> all, double dt) {

    Vector2D force = new Vector2D(0, 0);

    for (Body other : all) {
      if (other != b) {
        force = force.add(computeForce(b, other));
      }
    }

    Vector2D acc = force.scale(1.0 / b.mass());

    Vector2D vel = b.velocity().add(acc.scale(dt));
    Vector2D pos = b.position().add(vel.scale(dt));

    return new Body(pos, vel, b.mass());
  }

  Vector2D computeForce(Body a, Body b) {

    Vector2D dir = b.position().sub(a.position());

    double dist2 = Math.max(dir.norm2(), 25.0);
    double strength = (1.0 * a.mass() * b.mass()) / dist2;
//    double strength = (6.67e-11 * a.mass() * b.mass()) / dist2;

    double dist = Math.sqrt(dist2);

    return dir.scale(strength / dist);
  }

  // ----------------------------
  // Rendering
  // ----------------------------

  void render(GraphicsContext gc) {

    gc.setFill(Color.rgb(0, 0, 0, 0.12));
    gc.fillRect(0, 0, W, H);

    gc.setFill(Color.WHITE);

    for (Body b : bodies) {
      gc.fillOval(b.position().x(), b.position().y(), 2, 2);
    }
  }

  // ----------------------------
  // Init scene
  // ----------------------------

  List<Body> initBodies() {

    List<Body> list = new ArrayList<>();

    // body central massif
    list.add(new Body(
      new Vector2D(W / 2.0, H / 2.0),
      new Vector2D(0, 0),
      1e6
    ));

    // particules orbitales
    for (int i = 0; i < 5_000; i++) {

      double angle = Math.random() * 2 * Math.PI;
      double radius = 100 + Math.random() * 200;

      double x = W / 2.0 + Math.cos(angle) * radius;
      double y = H / 2.0 + Math.sin(angle) * radius;

      // vitesse tangentielle
      double vx = -Math.sin(angle) * 50;
      double vy = Math.cos(angle) * 50;

      list.add(new Body(
        new Vector2D(x, y),
        new Vector2D(vx, vy),
        1.0
      ));
    }

    return list;
  }
}