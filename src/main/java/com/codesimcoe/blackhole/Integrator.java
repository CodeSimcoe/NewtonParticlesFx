package com.codesimcoe.blackhole;

public final class Integrator {

  private Integrator() {
  }

  public static Ray step(Ray ray, double ds) {

    if (ray.absorbed()) {
      return ray;
    }

    State s0 = new State(ray.position(), ray.direction());

    State k1 = derivative(s0);
    State k2 = derivative(s0.add(k1.mul(ds * 0.5)));
    State k3 = derivative(s0.add(k2.mul(ds * 0.5)));
    State k4 = derivative(s0.add(k3.mul(ds)));

    State delta = k1
      .add(k2.mul(2.0))
      .add(k3.mul(2.0))
      .add(k4)
      .mul(ds / 6.0);

    State next = s0.add(delta);

    Vec3 nextPosition = next.position;

    Ray advanced = ray.advance(
      nextPosition,
      next.direction,
      ds
    );

    if (Schwarzschild.insideEventHorizon(nextPosition)) {
      return advanced.absorb();
    }

    return advanced;
  }

  public static Ray integrate(Ray ray) {

    Ray current = ray;

    for (int i = 0; i < Constants.MAX_STEPS; i++) {

      if (current.absorbed()) {
        return current;
      }

      if (current.distance() >= Constants.MAX_DISTANCE) {
        return current;
      }

      double radius = current.position().length();

      if (radius >= Constants.ESCAPE_RADIUS
        && current.direction().dot(current.position().div(radius)) > 0.0) {
        return current;
      }

      current = step(current, stepSize(radius));
    }

    return current;
  }

  public static double stepSize(double radius) {
    // Curvature decays rapidly outside the photon sphere.
    double scale = 1.0 + Math.max(0.0, radius - Constants.PHOTON_SPHERE_RADIUS) * 0.2;

    return Math.min(Constants.MAX_STEP_SIZE, Constants.STEP_SIZE * scale);
  }

  private static State derivative(State s) {

    Vec3 dp = s.direction;

    Vec3 dd =
      Schwarzschild.curvatureAcceleration(
        s.position,
        s.direction
      );

    return new State(dp, dd);
  }

  private value record State(Vec3 position, Vec3 direction) {

    State add(State s) {
      return new State(
        position.add(s.position),
        direction.add(s.direction)
      );
    }

    State mul(double k) {
      return new State(
        position.mul(k),
        direction.mul(k)
      );
    }
  }
}
