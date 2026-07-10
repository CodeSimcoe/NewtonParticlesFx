package com.codesimcoe.blackhole;

public final class Constants {

    private Constants() {
    }

    /*
     * We use dimensionless units.
     *
     * This keeps the renderer simple:
     *
     *   c = 1
     *   G = 1
     *   M = 1
     *
     * Therefore:
     *
     *   Schwarzschild radius Rs = 2GM / c² = 2
     *   event horizon radius       = 2
     *   photon sphere radius       = 3
     *
     * Distances in the scene are expressed in multiples of GM/c².
     */

    public static final double C = 1.0;
    public static final double G = 1.0;
    public static final double M = 1.0;

    public static final double SCHWARZSCHILD_RADIUS =
            2.0 * G * M / (C * C);

    public static final double EVENT_HORIZON_RADIUS =
            SCHWARZSCHILD_RADIUS;

    public static final double PHOTON_SPHERE_RADIUS =
            1.5 * SCHWARZSCHILD_RADIUS;

    /*
     * Numerical integration settings.
     */

    public static final double STEP_SIZE = 0.035;

    public static final double MAX_STEP_SIZE = 0.14;

    public static final int MAX_STEPS = 4_000;

    public static final double MAX_DISTANCE = 90.0;

    public static final double ESCAPE_RADIUS = 60.0;

    /*
     * Scene layout.
     */

    public static final double BLACK_HOLE_RADIUS =
            EVENT_HORIZON_RADIUS;

    public static final double DISK_INNER_RADIUS =
            PHOTON_SPHERE_RADIUS * 1.15;

    public static final double DISK_OUTER_RADIUS =
            16.0;

    public static final double DISK_HALF_THICKNESS =
            0.035;

    /*
     * Rendering parameters.
     */

    public static final int DEFAULT_WIDTH = 1280;
    public static final int DEFAULT_HEIGHT = 720;

    public static final int COMPARISON_WIDTH = 640;
    public static final int COMPARISON_HEIGHT = 360;

    public static final double CAMERA_FOV_DEGREES = 42.0;

    public static final Vec3 CAMERA_POSITION =
            new Vec3(0.0, 6.0, -35.0);

    public static final Vec3 CAMERA_TARGET =
            new Vec3(0.0, 0.0, 0.0);

    public static final Vec3 CAMERA_UP =
            new Vec3(0.0, 1.0, 0.0);

    /*
     * Visual tuning.
     */

    public static final double DISK_BRIGHTNESS = 7.0;

    public static final double STAR_BRIGHTNESS = 1.8;

    public static final double BACKGROUND_BRIGHTNESS = 0.035;

    public static final double HORIZON_GLOW = 0.18;
}
