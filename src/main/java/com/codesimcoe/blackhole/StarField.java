package com.codesimcoe.blackhole;

public final class StarField {

  private StarField() {
  }

  public static ColorRGB sample(Vec3 direction) {

    direction = direction.normalize();

    double stars = starLayer(direction, 120.0, 0.985, 18.0)
      + starLayer(direction, 260.0, 0.992, 26.0)
      + starLayer(direction, 520.0, 0.9965, 40.0);

    double nebula =
      0.5 + 0.5 * noise(direction.mul(2.5));

    ColorRGB background =
      new ColorRGB(0.015, 0.018, 0.035)
        .mul(Constants.BACKGROUND_BRIGHTNESS * (0.6 + nebula));

    ColorRGB starColor =
      new ColorRGB(0.85, 0.92, 1.0)
        .mul(Constants.STAR_BRIGHTNESS * stars);

    return background.add(starColor);
  }

  private static double starLayer(
    Vec3 direction,
    double scale,
    double threshold,
    double sharpness) {

    Vec3 p = direction.mul(scale);

    double n = noise(p);

    if (n < threshold) {
      return 0.0;
    }

    return Math.pow((n - threshold) / (1.0 - threshold), sharpness);
  }

  private static double noise(Vec3 p) {

    double x = Math.sin(p.x * 12.9898 + p.y * 78.233 + p.z * 37.719)
      * 43758.5453123;

    return fract(x);
  }

  private static double fract(double x) {
    return x - Math.floor(x);
  }
}