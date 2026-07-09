package com.codesimcoe.blackhole;

public final class AccretionDisk {

  private AccretionDisk() {
  }

  public static boolean crossesDisk(Vec3 previous, Vec3 current) {

    // Disk lies in the XZ plane, y = 0.

    if (Math.signum(previous.y) == Math.signum(current.y)) {
      return false;
    }

    Vec3 p = intersectionWithPlaneY0(previous, current);

    double r = Math.sqrt(p.x * p.x + p.z * p.z);

    return r >= Constants.DISK_INNER_RADIUS
      && r <= Constants.DISK_OUTER_RADIUS;
  }

  public static ColorRGB sample(Vec3 previous, Vec3 current, Ray ray) {

    Vec3 p = intersectionWithPlaneY0(previous, current);

    double r = Math.sqrt(p.x * p.x + p.z * p.z);
    double angle = Math.atan2(p.z, p.x);

    double radial =
      1.0 - (r - Constants.DISK_INNER_RADIUS)
        / (Constants.DISK_OUTER_RADIUS - Constants.DISK_INNER_RADIUS);

    radial = clamp(radial);

    double turbulence =
      0.5
        + 0.5 * Math.sin(18.0 * angle + 2.5 * r)
        * Math.sin(7.0 * r - 3.0 * angle);

    double rings =
      0.65 + 0.35 * Math.sin(12.0 * Math.log(r + 1.0));

    double brightness =
      Constants.DISK_BRIGHTNESS
        * Math.pow(radial, 1.7)
        * (0.65 + 0.35 * turbulence)
        * rings;

    /*
     * Simple Keplerian rotation around Y axis.
     * Used to fake relativistic Doppler beaming.
     */
    Vec3 tangent = new Vec3(-p.z, 0.0, p.x).normalize();

    double orbitalSpeed =
      Math.sqrt(Constants.M / Math.max(r, Constants.DISK_INNER_RADIUS));

    orbitalSpeed = Math.min(0.55, orbitalSpeed);

    double doppler =
      1.0 / (1.0 - orbitalSpeed * tangent.dot(ray.direction.negate()));

    doppler = clamp(doppler, 0.35, 2.8);

    double redshift = Schwarzschild.gravitationalRedshift(p);

    brightness *= Math.pow(doppler, 3.0);
    brightness *= redshift;

    ColorRGB hot =
      new ColorRGB(1.0, 0.72, 0.32);

    ColorRGB whiteHot =
      new ColorRGB(1.0, 0.95, 0.78);

    ColorRGB deepOrange =
      new ColorRGB(0.95, 0.28, 0.05);

    ColorRGB base =
      ColorRGB.lerp(deepOrange, hot, radial);

    base = ColorRGB.lerp(base, whiteHot, Math.pow(radial, 4.0));

    return base.mul(brightness * ray.intensity);
  }

  private static Vec3 intersectionWithPlaneY0(Vec3 a, Vec3 b) {

    double t = a.y / (a.y - b.y);

    return a.lerp(b, t);
  }

  private static double clamp(double v) {

    if (v < 0.0) {
      return 0.0;
    }

    if (v > 1.0) {
      return 1.0;
    }

    return v;
  }

  private static double clamp(double v, double min, double max) {

    if (v < min) {
      return min;
    }

    if (v > max) {
      return max;
    }

    return v;
  }
}