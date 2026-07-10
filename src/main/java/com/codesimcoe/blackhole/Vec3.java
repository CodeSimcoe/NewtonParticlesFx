package com.codesimcoe.blackhole;

public value record Vec3(double x, double y, double z) {

  public static final Vec3 ZERO = new Vec3(0, 0, 0);

  public Vec3(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public Vec3 add(Vec3 v) {
    return new Vec3(
      x + v.x,
      y + v.y,
      z + v.z
    );
  }

  public Vec3 sub(Vec3 v) {
    return new Vec3(
      x - v.x,
      y - v.y,
      z - v.z
    );
  }

  public Vec3 mul(double s) {
    return new Vec3(
      x * s,
      y * s,
      z * s
    );
  }

  public Vec3 div(double s) {
    return new Vec3(
      x / s,
      y / s,
      z / s
    );
  }

  public double dot(Vec3 v) {
    return
      x * v.x +
        y * v.y +
        z * v.z;
  }

  public Vec3 cross(Vec3 v) {

    return new Vec3(

      y * v.z - z * v.y,

      z * v.x - x * v.z,

      x * v.y - y * v.x
    );
  }

  public double lengthSquared() {
    return dot(this);
  }

  public double length() {
    return Math.sqrt(lengthSquared());
  }

  public Vec3 normalize() {

    double len = length();

    if (len == 0)
      return this;

    return div(len);
  }

  public double distance(Vec3 v) {
    return sub(v).length();
  }

  public Vec3 negate() {
    return new Vec3(-x, -y, -z);
  }

  public Vec3 lerp(Vec3 b, double t) {

    return new Vec3(
      x + (b.x - x) * t,
      y + (b.y - y) * t,
      z + (b.z - z) * t
    );
  }

  public static Vec3 of(double x, double y, double z) {
    return new Vec3(x, y, z);
  }

  @Override
  public String toString() {
    return "Vec3[" + x + "," + y + "," + z + "]";
  }

}