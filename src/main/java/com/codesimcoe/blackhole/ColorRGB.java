package com.codesimcoe.blackhole;

public final class ColorRGB {

  public final double r;
  public final double g;
  public final double b;

  public static final ColorRGB BLACK = new ColorRGB(0.0, 0.0, 0.0);
  public static final ColorRGB WHITE = new ColorRGB(1.0, 1.0, 1.0);

  public ColorRGB(double r, double g, double b) {
    this.r = r;
    this.g = g;
    this.b = b;
  }

  public ColorRGB add(ColorRGB c) {
    return new ColorRGB(
      r + c.r,
      g + c.g,
      b + c.b
    );
  }

  public ColorRGB sub(ColorRGB c) {
    return new ColorRGB(
      r - c.r,
      g - c.g,
      b - c.b
    );
  }

  public ColorRGB mul(double s) {
    return new ColorRGB(
      r * s,
      g * s,
      b * s
    );
  }

  public ColorRGB mul(ColorRGB c) {
    return new ColorRGB(
      r * c.r,
      g * c.g,
      b * c.b
    );
  }

  public ColorRGB div(double s) {
    return new ColorRGB(
      r / s,
      g / s,
      b / s
    );
  }

  /**
   * Simple Reinhard tone mapping.
   */
  public ColorRGB toneMap() {

    return new ColorRGB(
      r / (1.0 + r),
      g / (1.0 + g),
      b / (1.0 + b)
    );
  }

  /**
   * Gamma correction (linear -> sRGB).
   */
  public ColorRGB gammaCorrect() {

    return new ColorRGB(
      gamma(r),
      gamma(g),
      gamma(b)
    );
  }

  private static double gamma(double x) {

    x = clamp(x);

    return Math.pow(x, 1.0 / 2.2);
  }

  public ColorRGB clamp() {

    return new ColorRGB(
      clamp(r),
      clamp(g),
      clamp(b)
    );
  }

  private static double clamp(double v) {

    if (v < 0.0)
      return 0.0;

    if (v > 1.0)
      return 1.0;

    return v;
  }

  /**
   * Converts to JavaFX-compatible ARGB integer.
   */
  public int toARGB() {

    ColorRGB c = toneMap().gammaCorrect();

    int rr = (int) (255.0 * c.r);
    int gg = (int) (255.0 * c.g);
    int bb = (int) (255.0 * c.b);

    return
      (255 << 24) |
        (rr << 16) |
        (gg << 8) |
        bb;
  }

  public static ColorRGB lerp(
    ColorRGB a,
    ColorRGB b,
    double t) {

    return new ColorRGB(

      a.r + (b.r - a.r) * t,

      a.g + (b.g - a.g) * t,

      a.b + (b.b - a.b) * t
    );
  }

  @Override
  public String toString() {
    return "ColorRGB[" + r + "," + g + "," + b + "]";
  }

}