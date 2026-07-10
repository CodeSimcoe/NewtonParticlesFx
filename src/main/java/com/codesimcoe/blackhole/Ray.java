package com.codesimcoe.blackhole;

public value record Ray(
  Vec3 position,
  Vec3 direction,
  double distance,
  int steps,
  boolean absorbed,
  double intensity) {

  public Ray {
    direction = direction.normalize();
  }

  public static Ray create(Vec3 origin, Vec3 direction) {
    return new Ray(
      origin,
      direction.normalize(),
      0.0,
      0,
      false,
      1.0
    );
  }

  public Ray advance(
    Vec3 newPosition,
    Vec3 newDirection,
    double ds) {

    return new Ray(
      newPosition,
      newDirection.normalize(),
      distance + ds,
      steps + 1,
      absorbed,
      intensity
    );
  }

  public Ray absorb() {

    return new Ray(
      position,
      direction,
      distance,
      steps,
      true,
      intensity
    );
  }

  public Ray attenuate(double factor) {
    return new Ray(
      position,
      direction,
      distance,
      steps,
      absorbed,
      intensity * factor
    );
  }

  @Override
  public String toString() {
    return "Ray{" +
      "position=" + position +
      ", direction=" + direction +
      ", distance=" + distance +
      ", steps=" + steps +
      ", absorbed=" + absorbed +
      ", intensity=" + intensity +
      '}';
  }
}