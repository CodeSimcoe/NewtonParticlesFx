package com.codesimcoe.blackhole;

public final class Schwarzschild {

    private Schwarzschild() {
    }

    /**
     * Returns radius from black hole center.
     */
    public static double radius(Vec3 p) {
        return p.length();
    }

    /**
     * True once the photon crosses the event horizon.
     */
    public static boolean insideEventHorizon(Vec3 p) {
        return radius(p) <= Constants.EVENT_HORIZON_RADIUS;
    }

    /**
     * Approximate Schwarzschild light bending in isotropic-style coordinates.
     *
     * This is intentionally practical for a visual JavaFX renderer:
     * - stable
     * - fast
     * - produces photon-ring-like bending
     * - easy to explain
     *
     * It is not yet the full 4D null-geodesic equation.
     */
    public static Vec3 curvatureAcceleration(Vec3 position, Vec3 direction) {

        double r = position.length();

        if (r <= Constants.EVENT_HORIZON_RADIUS) {
            return Vec3.ZERO;
        }

        Vec3 radialOut = position.div(r);

        /*
         * Component of the radial direction perpendicular to photon direction.
         * Only this perpendicular part bends the ray.
         */
        Vec3 perpendicular =
                radialOut.sub(direction.mul(radialOut.dot(direction)));

        /*
         * Schwarzschild-like light deflection scales roughly as 1 / r².
         *
         * The factor 1.5 * Rs gives a visually convincing photon sphere
         * without making the integrator explode near the horizon.
         */
        double strength =
                1.5 * Constants.SCHWARZSCHILD_RADIUS / (r * r);

        return perpendicular.mul(-strength);
    }

    /**
     * Gravitational redshift factor.
     *
     * sqrt(1 - Rs / r)
     */
    public static double gravitationalRedshift(Vec3 position) {

        double r = position.length();

        if (r <= Constants.EVENT_HORIZON_RADIUS) {
            return 0.0;
        }

        return Math.sqrt(1.0 - Constants.SCHWARZSCHILD_RADIUS / r);
    }

    /**
     * Crude lensing brightness boost near the photon sphere.
     */
    public static double lensingBoost(Vec3 position) {

        double r = position.length();

        double d = Math.abs(r - Constants.PHOTON_SPHERE_RADIUS);

        return 1.0 + 1.8 / (1.0 + 8.0 * d * d);
    }

    /**
     * Helps create a dark apparent shadow larger than the horizon.
     */
    public static boolean nearPhotonCaptureRegion(Vec3 position) {

        double r = position.length();

        return r < Constants.PHOTON_SPHERE_RADIUS * 0.92;
    }
}