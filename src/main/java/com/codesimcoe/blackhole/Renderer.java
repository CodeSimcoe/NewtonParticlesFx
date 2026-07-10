package com.codesimcoe.blackhole;

import javafx.application.Platform;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.util.stream.IntStream;

public final class Renderer {

  private static final int ROWS_PER_PUBLISH = 16;

  private final int width;
  private final int height;
  private final Camera camera;
  private final WritableImage image;

  public Renderer(int width, int height) {
    this.width = width;
    this.height = height;

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

    int[] pixels = new int[width * height];

    int batchCount = (height + ROWS_PER_PUBLISH - 1) / ROWS_PER_PUBLISH;

    IntStream.range(0, batchCount)
      .parallel()
      .forEach(batch -> {

        int firstRow = batch * ROWS_PER_PUBLISH;
        int rowCount = Math.min(ROWS_PER_PUBLISH, height - firstRow);

        for (int y = firstRow; y < firstRow + rowCount; y++) {
          for (int x = 0; x < width; x++) {
            ColorRGB color = tracePixel(x, y);
            pixels[y * width + x] = color.toARGB();
          }
        }

        publishRows(pixels, firstRow, rowCount);
      });
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

  private ColorRGB tracePixel(int x, int y) {

    Ray ray = camera.createRay(x, y, width, height);

    ColorRGB accumulatedDisk = ColorRGB.BLACK;

    Vec3 previousPosition = ray.position();

    for (int i = 0; i < Constants.MAX_STEPS; i++) {

      if (ray.absorbed()) {
        return horizonColor(previousPosition).add(accumulatedDisk);
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

      previousPosition = ray.position();
      ray = next;
    }

    ColorRGB background = StarField.sample(ray.direction());

    double boost = Schwarzschild.lensingBoost(ray.position());

    return accumulatedDisk.add(background.mul(boost));
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
