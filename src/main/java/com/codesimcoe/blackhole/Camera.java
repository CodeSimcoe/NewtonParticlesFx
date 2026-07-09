package com.codesimcoe.blackhole;

public final class Camera {

  private final Vec3 position;

  private final Vec3 forward;
  private final Vec3 right;
  private final Vec3 up;

  private final double tanHalfFov;
  private final double aspect;

  /**
   * @param position           Camera position
   * @param target             Point the camera looks at
   * @param upVector           Usually (0,1,0)
   * @param verticalFovDegrees Vertical field of view
   * @param aspect             width / height
   */
  public Camera(
    Vec3 position,
    Vec3 target,
    Vec3 upVector,
    double verticalFovDegrees,
    double aspect) {

    this.position = position;
    this.aspect = aspect;

    this.forward = target.sub(position).normalize();

    this.right = forward.cross(upVector).normalize();

    this.up = right.cross(forward).normalize();

    this.tanHalfFov =
      Math.tan(Math.toRadians(verticalFovDegrees * 0.5));
  }

  /**
   * Creates one primary ray.
   * <p>
   * pixelX and pixelY are integer coordinates.
   */
  public Ray createRay(
    int pixelX,
    int pixelY,
    int width,
    int height) {

    // Pixel center

    double u = (pixelX + 0.5) / width;
    double v = (pixelY + 0.5) / height;

    // Convert to NDC

    u = 2.0 * u - 1.0;
    v = 1.0 - 2.0 * v;

    // Apply projection

    u *= aspect * tanHalfFov;
    v *= tanHalfFov;

    Vec3 direction = forward
      .add(right.mul(u))
      .add(up.mul(v))
      .normalize();

    return Ray.create(position, direction);
  }

  public Vec3 position() {
    return position;
  }

  public Vec3 forward() {
    return forward;
  }

  public Vec3 right() {
    return right;
  }

  public Vec3 up() {
    return up;
  }

}