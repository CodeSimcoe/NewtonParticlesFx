package com.codesimcoe.blackhole;

public final class CinematicPostProcessor {

  private CinematicPostProcessor() {
  }

  public static void apply(int[] pixels, int width, int height) {
    int bloomWidth = (width + 1) / 2;
    int bloomHeight = (height + 1) / 2;
    float[] bloom = new float[bloomWidth * bloomHeight];
    float[] blurred = new float[bloom.length];

    for (int y = 0; y < bloomHeight; y++) {
      for (int x = 0; x < bloomWidth; x++) {
        int argb = pixels[(y * 2) * width + x * 2];
        float brightness = Math.max(
          (argb >>> 16) & 0xFF,
          Math.max((argb >>> 8) & 0xFF, argb & 0xFF)
        ) / 255.0f;

        bloom[y * bloomWidth + x] = Math.max(0.0f, brightness - 0.62f);
      }
    }

    blur(bloom, blurred, bloomWidth, bloomHeight, true);
    blur(blurred, bloom, bloomWidth, bloomHeight, false);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int index = y * width + x;
        int argb = pixels[index];
        int bloomIndex = (y / 2) * bloomWidth + (x / 2);
        int glow = (int) (bloom[bloomIndex] * 92.0f);

        int red = Math.min(255, ((argb >>> 16) & 0xFF) + glow);
        int green = Math.min(255, ((argb >>> 8) & 0xFF) + (int) (glow * 0.42));
        int blue = Math.min(255, (argb & 0xFF) + (int) (glow * 0.18));

        pixels[index] = 0xFF000000 | (red << 16) | (green << 8) | blue;
      }
    }
  }

  private static void blur(
    float[] source,
    float[] target,
    int bloomWidth,
    int bloomHeight,
    boolean horizontal) {

    for (int y = 0; y < bloomHeight; y++) {
      for (int x = 0; x < bloomWidth; x++) {
        float sum = 0.0f;

        for (int offset = -2; offset <= 2; offset++) {
          int sampleX = horizontal ? Math.clamp(x + offset, 0, bloomWidth - 1) : x;
          int sampleY = horizontal ? y : Math.clamp(y + offset, 0, bloomHeight - 1);
          int weight = offset == 0 ? 6 : Math.abs(offset) == 1 ? 4 : 1;

          sum += source[sampleY * bloomWidth + sampleX] * weight;
        }

        target[y * bloomWidth + x] = sum / 16.0f;
      }
    }
  }
}
