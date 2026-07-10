package com.codesimcoe.blackhole;

public record RenderStats(long elapsedNanos, long tracedSteps, long allocatedBytes) {

  public double elapsedSeconds() {
    return elapsedNanos / 1_000_000_000.0;
  }

  public double allocatedMegabytes() {
    return allocatedBytes / (1024.0 * 1024.0);
  }
}
