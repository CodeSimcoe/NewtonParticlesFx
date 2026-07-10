package com.codesimcoe.blackhole;

import javafx.application.Platform;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import com.sun.management.ThreadMXBean;
import jdk.internal.value.ValueClass;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

public final class Renderer {

  private static final int ROWS_PER_PUBLISH = 16;

  private final int width;
  private final int height;
  private final Camera camera;
  private final WritableImage image;
  private final RenderListener listener;

  public Renderer(int width, int height) {
    this(width, height, RenderListener.NONE);
  }

  public Renderer(int width, int height, RenderListener listener) {
    this.width = width;
    this.height = height;
    this.listener = listener;

    this.camera = new Camera(
      Constants.CAMERA_POSITION,
      Constants.CAMERA_TARGET,
      Constants.CAMERA_UP,
      Constants.CAMERA_FOV_DEGREES,
      (double) width / height
    );

    this.image = new WritableImage(width, height);

    fillBlack();
  }

  public WritableImage image() {
    return image;
  }

  public void render() {

    long startedAt = System.nanoTime();
    int[] pixels = new int[width * height];
    Ray[] states = newStateArray(width * height);
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

  private void fillBlack() {
    Platform.runLater(() -> {
      PixelWriter writer = image.getPixelWriter();
      int[] black = new int[width * height];

      java.util.Arrays.fill(black, 0xFF000000);
      writer.setPixels(
        0,
        0,
        width,
        height,
        PixelFormat.getIntArgbInstance(),
        black,
        0,
        width
      );
    });
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

  private ColorRGB tracePixel(
    int x,
    int y,
    int index,
    Ray[] states,
    LongAdder tracedSteps) {

    Ray ray = camera.createRay(x, y, width, height);
    states[index] = ray;

    ColorRGB accumulatedDisk = ColorRGB.BLACK;

    Vec3 previousPosition = ray.position();

    for (int i = 0; i < Constants.MAX_STEPS; i++) {
      tracedSteps.increment();

      if (ray.absorbed()) {
        return applyVignette(
          horizonColor(previousPosition).add(accumulatedDisk),
          x,
          y
        );
      }

      if (ray.distance() > Constants.MAX_DISTANCE) {
        break;
      }

      double radius = ray.position().length();

      if (radius > Constants.ESCAPE_RADIUS) {
        break;
      }

      Ray next = Integrator.step(ray, Integrator.stepSize(radius));

      if (AccretionDisk.crossesDisk(ray.position(), next.position())) {
        ColorRGB diskColor =
          AccretionDisk.sample(ray.position(), next.position(), next);

        accumulatedDisk = accumulatedDisk.add(diskColor);

        // Continue tracing to allow secondary disk images.
        next = next.attenuate(0.72);
      }

      states[index] = next;
      previousPosition = ray.position();
      ray = next;
    }

    ColorRGB background = StarField.sample(ray.direction());

    double boost = Schwarzschild.lensingBoost(ray.position());

    return applyVignette(accumulatedDisk.add(background.mul(boost)), x, y);
  }

  private ColorRGB applyVignette(ColorRGB color, int x, int y) {
    double nx = (2.0 * x / (width - 1)) - 1.0;
    double ny = (2.0 * y / (height - 1)) - 1.0;
    double edge = Math.min(1.0, nx * nx + ny * ny);

    return color.mul(1.0 - 0.32 * edge * edge);
  }

  private static ThreadMXBean threadMxBean() {
    ThreadMXBean bean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);

    if (bean.isThreadAllocatedMemorySupported() && !bean.isThreadAllocatedMemoryEnabled()) {
      bean.setThreadAllocatedMemoryEnabled(true);
    }

    return bean;
  }

  private static Ray[] newStateArray(int length) {
    return (Ray[]) ValueClass.newNullRestrictedNonAtomicArray(
      Ray.class,
      length,
      Ray.create(Vec3.ZERO, Vec3.ZERO)
    );
  }

  private static long allocatedBytes(ThreadMXBean bean, long threadId) {
    return bean.isThreadAllocatedMemorySupported()
      ? bean.getThreadAllocatedBytes(threadId)
      : 0L;
  }

  private ColorRGB horizonColor(Vec3 lastPosition) {

    double r = lastPosition.length();

    double glow =
      Constants.HORIZON_GLOW
        / (1.0 + 8.0 * Math.abs(r - Constants.PHOTON_SPHERE_RADIUS));

    return new ColorRGB(
      glow * 0.9,
      glow * 0.55,
      glow * 0.22
    );
  }
}
