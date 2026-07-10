package com.codesimcoe.blackhole;

public interface RenderListener {

  RenderListener NONE = new RenderListener() {
  };

  default void onProgress(int completedBatches, int totalBatches) {
  }

  default void onComplete(RenderStats stats) {
  }
}
