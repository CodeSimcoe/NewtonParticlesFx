package com.codesimcoe.newtonparticlesfx;

public record Vector2D(double x, double y) {
  Vector2D add(Vector2D other) {
    return new Vector2D(x + other.x, y + other.y);
  }

  Vector2D sub(Vector2D other) {
    return new Vector2D(x - other.x, y - other.y);
  }

  Vector2D scale(double factor) {
    return new Vector2D(x * factor, y * factor);
  }

  double norm2() {
    return x * x + y * y;
  }
}
