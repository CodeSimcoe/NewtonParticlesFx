package com.codesimcoe.newtonparticlesfx;

import javafx.scene.paint.Color;

public final class ColorUtils {

  static final Color[] COLOR_LEVELS = new Color[256];

  static {
    for (int i = 0; i < COLOR_LEVELS.length; i++) {
      float t = i / 255.0f;

      double hue = (1.0 - t) * 220.0;
      double brightness = 0.4 + t * 0.6;

      COLOR_LEVELS[i] = Color.hsb(hue, 1.0, brightness);
    }
  }

  private ColorUtils() {
    //
  }
}
