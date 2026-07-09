package com.codesimcoe.blackhole;

import javafx.application.Platform;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public final class Renderer {

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

    AtomicInteger completedRows = new AtomicInteger();

    IntStream.range(0, height)
      .parallel()
      .forEach(y -> {

        for (int x = 0; x < width; x++) {
          ColorRGB color = tracePixel(x, y);
          pixels[y * width + x] = color.toARGB();
        }

        int done = completedRows.incrementAndGet();

        if (done % 8 == 0 || done == height) {
          publishRows(pixels);
        }
      });

    publishRows(pixels);
  }

  private void fillBlack() {
    Platform.runLater(() -> {
      PixelWriter writer = image.getPixelWriter();

      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          writer.setArgb(x, y, 0xFF000000);
        }
      }
    });
  }

  private void publishRows(int[] pixels) {
    Platform.runLater(() -> {
      PixelWriter writer = image.getPixelWriter();

      for (int y = 0; y < height; y++) {
        int rowOffset = y * width;

        for (int x = 0; x < width; x++) {
          int argb = pixels[rowOffset + x];

          if (argb != 0) {
            writer.setArgb(x, y, argb);
          }
        }
      }
    });
  }

  private ColorRGB tracePixel(int x, int y) {

    Ray ray = camera.createRay(x, y, width, height);

    ColorRGB accumulatedDisk = ColorRGB.BLACK;

    Vec3 previousPosition = ray.position;

    for (int i = 0; i < Constants.MAX_STEPS; i++) {

      if (ray.absorbed) {
        return horizonColor(previousPosition).add(accumulatedDisk);
      }

      if (ray.distance > Constants.MAX_DISTANCE) {
        break;
      }

      if (ray.position.length() > Constants.ESCAPE_RADIUS) {
        break;
      }

      Ray next = Integrator.step(ray, Constants.STEP_SIZE);

      if (AccretionDisk.crossesDisk(ray.position, next.position)) {
        ColorRGB diskColor =
          AccretionDisk.sample(ray.position, next.position, next);

        accumulatedDisk = accumulatedDisk.add(diskColor);

        // Continue tracing to allow secondary disk images.
        next = next.attenuate(0.72);
      }

      previousPosition = ray.position;
      ray = next;
    }

    ColorRGB background = StarField.sample(ray.direction);

    double boost = Schwarzschild.lensingBoost(ray.position);

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