package com.codesimcoe.blackhole;

import com.sun.management.ThreadMXBean;
import javafx.application.Platform;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

/**
 * Identity-bearing reference implementation for the value-array comparison.
 */
public final class ReferenceRenderer {

  private static final int ROWS_PER_PUBLISH = 16;

  private final int width;
  private final int height;
  private final ReferenceCamera camera;
  private final WritableImage image;
  private final RenderListener listener;

  public ReferenceRenderer(int width, int height, RenderListener listener) {
    this.width = width;
    this.height = height;
    this.listener = listener;
    this.camera = new ReferenceCamera(
      ReferenceVec3.from(Constants.CAMERA_POSITION),
      ReferenceVec3.from(Constants.CAMERA_TARGET),
      ReferenceVec3.from(Constants.CAMERA_UP),
      Constants.CAMERA_FOV_DEGREES,
      (double) width / height
    );
    this.image = new WritableImage(width, height);
  }

  public WritableImage image() {
    return image;
  }

  public void render() {
    long startedAt = System.nanoTime();
    int[] pixels = new int[width * height];
    ReferenceRay[] states = new ReferenceRay[width * height];
    int batchCount = (height + ROWS_PER_PUBLISH - 1) / ROWS_PER_PUBLISH;
    AtomicInteger completedBatches = new AtomicInteger();
    LongAdder tracedSteps = new LongAdder();
    LongAdder allocatedBytes = new LongAdder();
    ThreadMXBean threadMxBean = threadMxBean();

    IntStream.range(0, batchCount)
      .parallel()
      .forEach(batch -> {
        long threadId = Thread.currentThread().threadId();
        long allocatedBefore = allocatedBytes(threadMxBean, threadId);
        int firstRow = batch * ROWS_PER_PUBLISH;
        int rowCount = Math.min(ROWS_PER_PUBLISH, height - firstRow);

        for (int y = firstRow; y < firstRow + rowCount; y++) {
          for (int x = 0; x < width; x++) {
            int index = y * width + x;
            ColorRGB color = tracePixel(x, y, index, states, tracedSteps);
            pixels[index] = color.toARGB();
          }
        }

        publishRows(pixels, firstRow, rowCount);
        allocatedBytes.add(allocatedBytes(threadMxBean, threadId) - allocatedBefore);
        listener.onProgress(completedBatches.incrementAndGet(), batchCount);
      });

    CinematicPostProcessor.apply(pixels, width, height);
    publishRows(pixels, 0, height);
    listener.onComplete(new RenderStats(
      System.nanoTime() - startedAt,
      tracedSteps.sum(),
      allocatedBytes.sum()
    ));
  }

  private ColorRGB tracePixel(
    int x,
    int y,
    int index,
    ReferenceRay[] states,
    LongAdder tracedSteps) {

    ReferenceRay ray = camera.createRay(x, y, width, height);
    states[index] = ray;
    ColorRGB accumulatedDisk = ColorRGB.BLACK;
    ReferenceVec3 previousPosition = ray.position;

    for (int i = 0; i < Constants.MAX_STEPS; i++) {
      tracedSteps.increment();

      if (ray.absorbed) {
        return applyVignette(horizonColor(previousPosition).add(accumulatedDisk), x, y);
      }

      if (ray.distance > Constants.MAX_DISTANCE) {
        break;
      }

      double radius = ray.position.length();

      if (radius > Constants.ESCAPE_RADIUS) {
        break;
      }

      ReferenceRay next = step(ray, Integrator.stepSize(radius));

      if (AccretionDisk.crossesDisk(ray.position.toValue(), next.position.toValue())) {
        ColorRGB diskColor = AccretionDisk.sample(
          ray.position.toValue(),
          next.position.toValue(),
          next.toValueRay()
        );
        accumulatedDisk = accumulatedDisk.add(diskColor);
        next = next.attenuate(0.72);
      }

      states[index] = next;
      previousPosition = ray.position;
      ray = next;
    }

    ColorRGB background = StarField.sample(ray.direction.toValue());
    double boost = Schwarzschild.lensingBoost(ray.position.toValue());

    return applyVignette(accumulatedDisk.add(background.mul(boost)), x, y);
  }

  private static ReferenceRay step(ReferenceRay ray, double ds) {
    ReferenceState s0 = new ReferenceState(ray.position, ray.direction);
    ReferenceState k1 = derivative(s0);
    ReferenceState k2 = derivative(s0.add(k1.mul(ds * 0.5)));
    ReferenceState k3 = derivative(s0.add(k2.mul(ds * 0.5)));
    ReferenceState k4 = derivative(s0.add(k3.mul(ds)));
    ReferenceState delta = k1.add(k2.mul(2.0)).add(k3.mul(2.0)).add(k4).mul(ds / 6.0);
    ReferenceState next = s0.add(delta);
    ReferenceRay advanced = ray.advance(next.position, next.direction, ds);

    return next.position.length() <= Constants.EVENT_HORIZON_RADIUS
      ? advanced.absorb()
      : advanced;
  }

  private static ReferenceState derivative(ReferenceState state) {
    return new ReferenceState(
      state.direction,
      curvatureAcceleration(state.position, state.direction)
    );
  }

  private static ReferenceVec3 curvatureAcceleration(
    ReferenceVec3 position,
    ReferenceVec3 direction) {

    double radius = position.length();

    if (radius <= Constants.EVENT_HORIZON_RADIUS) {
      return ReferenceVec3.ZERO;
    }

    ReferenceVec3 radialOut = position.div(radius);
    ReferenceVec3 perpendicular = radialOut.sub(direction.mul(radialOut.dot(direction)));
    double strength = 1.5 * Constants.SCHWARZSCHILD_RADIUS / (radius * radius);

    return perpendicular.mul(-strength);
  }

  private ColorRGB applyVignette(ColorRGB color, int x, int y) {
    double nx = (2.0 * x / (width - 1)) - 1.0;
    double ny = (2.0 * y / (height - 1)) - 1.0;
    double edge = Math.min(1.0, nx * nx + ny * ny);

    return color.mul(1.0 - 0.32 * edge * edge);
  }

  private ColorRGB horizonColor(ReferenceVec3 position) {
    double glow = Constants.HORIZON_GLOW /
      (1.0 + 8.0 * Math.abs(position.length() - Constants.PHOTON_SPHERE_RADIUS));

    return new ColorRGB(glow * 0.9, glow * 0.55, glow * 0.22);
  }

  private void publishRows(int[] pixels, int firstRow, int rowCount) {
    Platform.runLater(() -> {
      PixelWriter writer = image.getPixelWriter();
      writer.setPixels(
        0,
        firstRow,
        width,
        rowCount,
        PixelFormat.getIntArgbInstance(),
        pixels,
        firstRow * width,
        width
      );
    });
  }

  private static ThreadMXBean threadMxBean() {
    ThreadMXBean bean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);

    if (bean.isThreadAllocatedMemorySupported() && !bean.isThreadAllocatedMemoryEnabled()) {
      bean.setThreadAllocatedMemoryEnabled(true);
    }

    return bean;
  }

  private static long allocatedBytes(ThreadMXBean bean, long threadId) {
    return bean.isThreadAllocatedMemorySupported()
      ? bean.getThreadAllocatedBytes(threadId)
      : 0L;
  }

  private static final class ReferenceState {
    final ReferenceVec3 position;
    final ReferenceVec3 direction;

    ReferenceState(ReferenceVec3 position, ReferenceVec3 direction) {
      this.position = position;
      this.direction = direction;
    }

    ReferenceState add(ReferenceState other) {
      return new ReferenceState(position.add(other.position), direction.add(other.direction));
    }

    ReferenceState mul(double scale) {
      return new ReferenceState(position.mul(scale), direction.mul(scale));
    }
  }

  private static final class ReferenceRay {
    final ReferenceVec3 position;
    final ReferenceVec3 direction;
    final double distance;
    final int steps;
    final boolean absorbed;
    final double intensity;

    ReferenceRay(
      ReferenceVec3 position,
      ReferenceVec3 direction,
      double distance,
      int steps,
      boolean absorbed,
      double intensity) {

      this.position = position;
      this.direction = direction.normalize();
      this.distance = distance;
      this.steps = steps;
      this.absorbed = absorbed;
      this.intensity = intensity;
    }

    ReferenceRay advance(ReferenceVec3 position, ReferenceVec3 direction, double distance) {
      return new ReferenceRay(position, direction, this.distance + distance, steps + 1, absorbed, intensity);
    }

    ReferenceRay absorb() {
      return new ReferenceRay(position, direction, distance, steps, true, intensity);
    }

    ReferenceRay attenuate(double factor) {
      return new ReferenceRay(position, direction, distance, steps, absorbed, intensity * factor);
    }

    Ray toValueRay() {
      return new Ray(position.toValue(), direction.toValue(), distance, steps, absorbed, intensity);
    }
  }

  private static final class ReferenceCamera {
    final ReferenceVec3 position;
    final ReferenceVec3 forward;
    final ReferenceVec3 right;
    final ReferenceVec3 up;
    final double tanHalfFov;
    final double aspect;

    ReferenceCamera(
      ReferenceVec3 position,
      ReferenceVec3 target,
      ReferenceVec3 upVector,
      double verticalFovDegrees,
      double aspect) {

      this.position = position;
      this.forward = target.sub(position).normalize();
      this.right = forward.cross(upVector).normalize();
      this.up = right.cross(forward).normalize();
      this.tanHalfFov = Math.tan(Math.toRadians(verticalFovDegrees * 0.5));
      this.aspect = aspect;
    }

    ReferenceRay createRay(int x, int y, int width, int height) {
      double u = (2.0 * (x + 0.5) / width - 1.0) * aspect * tanHalfFov;
      double v = (1.0 - 2.0 * (y + 0.5) / height) * tanHalfFov;
      ReferenceVec3 direction = forward.add(right.mul(u)).add(up.mul(v)).normalize();

      return new ReferenceRay(position, direction, 0.0, 0, false, 1.0);
    }
  }

  private static final class ReferenceVec3 {
    static final ReferenceVec3 ZERO = new ReferenceVec3(0.0, 0.0, 0.0);

    final double x;
    final double y;
    final double z;

    ReferenceVec3(double x, double y, double z) {
      this.x = x;
      this.y = y;
      this.z = z;
    }

    static ReferenceVec3 from(Vec3 vector) {
      return new ReferenceVec3(vector.x(), vector.y(), vector.z());
    }

    ReferenceVec3 add(ReferenceVec3 other) {
      return new ReferenceVec3(x + other.x, y + other.y, z + other.z);
    }

    ReferenceVec3 sub(ReferenceVec3 other) {
      return new ReferenceVec3(x - other.x, y - other.y, z - other.z);
    }

    ReferenceVec3 mul(double scale) {
      return new ReferenceVec3(x * scale, y * scale, z * scale);
    }

    ReferenceVec3 div(double scale) {
      return new ReferenceVec3(x / scale, y / scale, z / scale);
    }

    ReferenceVec3 cross(ReferenceVec3 other) {
      return new ReferenceVec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
      );
    }

    double dot(ReferenceVec3 other) {
      return x * other.x + y * other.y + z * other.z;
    }

    double length() {
      return Math.sqrt(dot(this));
    }

    ReferenceVec3 normalize() {
      double length = length();

      return length == 0.0 ? this : div(length);
    }

    Vec3 toValue() {
      return new Vec3(x, y, z);
    }
  }
}
