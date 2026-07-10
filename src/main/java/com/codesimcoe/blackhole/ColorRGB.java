package com.codesimcoe.blackhole;

public value record ColorRGB(double r, double g, double b) {

  public static final ColorRGB BLACK = new ColorRGB(0.0, 0.0, 0.0);
  public static final ColorRGB WHITE = new ColorRGB(1.0, 1.0, 1.0);

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
   * Filmic ACES approximation for high-contrast scene lighting.
   */
  public ColorRGB toneMap() {

    return new ColorRGB(
      toneMap(r),
      toneMap(g),
      toneMap(b)
    );
  }

  private static double toneMap(double x) {
    double a = 2.51;
    double b = 0.03;
    double c = 2.43;
    double d = 0.59;
    double e = 0.14;

    return clamp((x * (a * x + b)) / (x * (c * x + d) + e));
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
