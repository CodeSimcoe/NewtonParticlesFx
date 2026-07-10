package com.codesimcoe.blackhole;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CPU-only Schwarzschild black-hole renderer.
 *
 * <p>The renderer traces null geodesics backward from a static camera using
 * the exact Schwarzschild radial equation in each ray's orbital plane:
 *
 * <pre>
 *   r''   = L^2 / r^3 - 3 M L^2 / r^4
 *   psi'  = L / r^2
 * </pre>
 *
 * <p>It renders an optically thick, thin accretion disk, applies gravitational
 * and Doppler frequency shifts, samples a procedural star field, adds bloom,
 * and writes a PNG. No GPU and no external dependency are used.
 *
 * <p>Compile:
 * <pre>javac BlackHoleCpu.java</pre>
 *
 * <p>Run:
 * <pre>java BlackHoleCpu 1280 720 4 black-hole.png</pre>
 *
 * <p>Arguments are: width height samplesPerPixel outputFile.
 */
public final class BlackHoleCpu {

    // Geometrized units: G = c = M = 1.
    private static final double M = 1.0;
    private static final double HORIZON = 2.0 * M;
    // Schwarzschild ISCO for a non-rotating black hole.
    private static final double DISK_INNER = 6.0 * M;
    private static final double DISK_OUTER = 18.0 * M;

    // Camera: 30° above the disk plane for a dramatic but readable view.
    private static final double CAMERA_R = 40.0 * M;
    private static final double CAMERA_THETA = Math.toRadians(60.0);
    private static final double CAMERA_PHI = Math.toRadians(0.0);
    private static final double VERTICAL_FOV = Math.toRadians(26.0);

    private static final double ESCAPE_R = 110.0 * M;
    private static final int MAX_STEPS = 6_000;
    private static final int TILE_SIZE = 16;

    // Rendering controls.
    private static final double EXPOSURE = 0.85;
    private static final double BLOOM_STRENGTH = 0.34;
    private static final int BLOOM_RADIUS = 10;
    private static final double BLOOM_SIGMA = 4.5;

    private BlackHoleCpu() {
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);

        int width = argument(args, 0, 960);
        int height = argument(args, 1, 540);
        int samplesPerPixel = argument(args, 2, 2);
        String output = args.length >= 4 ? args[3] : "black-hole.png";

        if (width <= 0 || height <= 0 || samplesPerPixel <= 0) {
            throw new IllegalArgumentException("width, height and samplesPerPixel must be positive");
        }

        System.out.printf(
                "Rendering %dx%d, %d sample(s)/pixel on %d CPU thread(s)%n",
                width,
                height,
                samplesPerPixel,
                Runtime.getRuntime().availableProcessors()
        );

        long start = System.nanoTime();
        Frame frame = render(width, height, samplesPerPixel);
        BufferedImage image = finish(frame);
        ImageIO.write(image, "png", new File(output));
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;

        System.out.printf("Wrote %s in %.2f s%n", new File(output).getAbsolutePath(), seconds);
    }

    private static int argument(String[] args, int index, int defaultValue) {
        return args.length > index ? Integer.parseInt(args[index]) : defaultValue;
    }

    private static Frame render(int width, int height, int spp) throws InterruptedException {
        Frame frame = new Frame(width, height);
        Camera camera = new Camera(width, height);

        int tilesX = (width + TILE_SIZE - 1) / TILE_SIZE;
        int tilesY = (height + TILE_SIZE - 1) / TILE_SIZE;
        int tileCount = tilesX * tilesY;
        AtomicInteger nextTile = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int workerIndex = 0; workerIndex < threads; workerIndex++) {
            pool.submit(() -> {
                Sample sample = new Sample();
                Sample sum = new Sample();

                while (true) {
                    int tile = nextTile.getAndIncrement();
                    if (tile >= tileCount) {
                        return;
                    }

                    int tileX = tile % tilesX;
                    int tileY = tile / tilesX;
                    int minX = tileX * TILE_SIZE;
                    int minY = tileY * TILE_SIZE;
                    int maxX = Math.min(width, minX + TILE_SIZE);
                    int maxY = Math.min(height, minY + TILE_SIZE);

                    for (int y = minY; y < maxY; y++) {
                        for (int x = minX; x < maxX; x++) {
                            sum.clear();

                            for (int s = 0; s < spp; s++) {
                                // Deterministic low-discrepancy-like jitter.
                                double jx = radicalInverse(s + 1, 2) - 0.5;
                                double jy = radicalInverse(s + 1, 3) - 0.5;
                                trace(camera, x + 0.5 + jx, y + 0.5 + jy, sample);
                                sum.add(sample);
                            }

                            double scale = 1.0 / spp;
                            int index = y * width + x;
                            frame.r[index] = (float) (sum.r * scale);
                            frame.g[index] = (float) (sum.g * scale);
                            frame.b[index] = (float) (sum.b * scale);
                        }
                    }

                    int done = completed.incrementAndGet();
                    if (done % Math.max(1, tileCount / 20) == 0 || done == tileCount) {
                        System.out.printf("\rProgress: %5.1f%%", 100.0 * done / tileCount);
                    }
                }
            });
        }

        pool.shutdown();
        if (!pool.awaitTermination(1, TimeUnit.DAYS)) {
            throw new IllegalStateException("rendering did not terminate");
        }
        System.out.println();
        return frame;
    }

    private static void trace(Camera camera, double pixelX, double pixelY, Sample out) {
        // Camera ray in global Cartesian coordinates.
        double screenX = (2.0 * pixelX / camera.width - 1.0) * camera.aspect * camera.tanHalfFov;
        double screenY = (1.0 - 2.0 * pixelY / camera.height) * camera.tanHalfFov;

        double dx = camera.forwardX + screenX * camera.rightX + screenY * camera.upX;
        double dy = camera.forwardY + screenX * camera.rightY + screenY * camera.upY;
        double dz = camera.forwardZ + screenX * camera.rightZ + screenY * camera.upZ;
        double invLength = invSqrt(dx * dx + dy * dy + dz * dz);
        dx *= invLength;
        dy *= invLength;
        dz *= invLength;

        // Radial and tangential components in the camera's local static frame.
        double radialDirection = dx * camera.q0x + dy * camera.q0y + dz * camera.q0z;
        double tx = dx - radialDirection * camera.q0x;
        double ty = dy - radialDirection * camera.q0y;
        double tz = dz - radialDirection * camera.q0z;
        double tangentMagnitude = Math.sqrt(tx * tx + ty * ty + tz * tz);

        final double q1x;
        final double q1y;
        final double q1z;
        if (tangentMagnitude < 1.0e-12) {
            q1x = camera.rightX;
            q1y = camera.rightY;
            q1z = camera.rightZ;
            tangentMagnitude = 0.0;
        } else {
            double inverse = 1.0 / tangentMagnitude;
            q1x = tx * inverse;
            q1y = ty * inverse;
            q1z = tz * inverse;
        }

        double fCamera = schwarzschildF(CAMERA_R);
        double energy = Math.sqrt(fCamera);              // E = -p_t, local photon energy = 1.
        double angularMomentum = CAMERA_R * tangentMagnitude;
        double angularMomentumSquared = angularMomentum * angularMomentum;
        double radialVelocity = Math.sqrt(fCamera) * radialDirection;

        // Direction of the ray's conserved angular momentum vector.
        double normalZ = camera.q0x * q1y - camera.q0y * q1x;
        double angularMomentumZ = angularMomentum * normalZ;

        double r = CAMERA_R;
        double psi = 0.0;
        double previousR = r;
        double previousPsi = psi;
        double previousZ = CAMERA_R * camera.q0z;

        for (int step = 0; step < MAX_STEPS; step++) {
            if (r <= HORIZON * 1.0002) {
                // Captured by the event horizon.
                out.set(0.0, 0.0, 0.0);
                return;
            }

            if (r >= ESCAPE_R && radialVelocity > 0.0) {
                sampleSky(
                        escapedDirectionComponent(r, radialVelocity, psi, angularMomentum, camera.q0x, q1x),
                        escapedDirectionComponent(r, radialVelocity, psi, angularMomentum, camera.q0y, q1y),
                        escapedDirectionComponent(r, radialVelocity, psi, angularMomentum, camera.q0z, q1z),
                        out
                );
                return;
            }

            previousR = r;
            previousPsi = psi;
            previousZ = positionZ(r, psi, camera.q0z, q1z);

            // Limit both radial displacement and angular displacement per RK4 step.
            double hAngular = angularMomentum > 1.0e-10
                    ? 0.024 * r * r / angularMomentum
                    : 0.5;
            double hRadial = 0.045 * r / Math.max(0.25, Math.abs(radialVelocity));
            double h = clamp(Math.min(hAngular, hRadial), 0.002, 0.5);

            // Classical RK4 for (r, r', psi).
            double k1r = radialVelocity;
            double k1v = radialAcceleration(r, angularMomentumSquared);
            double k1p = angularMomentum / (r * r);

            double r2 = r + 0.5 * h * k1r;
            double v2 = radialVelocity + 0.5 * h * k1v;
            double k2r = v2;
            double k2v = radialAcceleration(r2, angularMomentumSquared);
            double k2p = angularMomentum / (r2 * r2);

            double r3 = r + 0.5 * h * k2r;
            double v3 = radialVelocity + 0.5 * h * k2v;
            double k3r = v3;
            double k3v = radialAcceleration(r3, angularMomentumSquared);
            double k3p = angularMomentum / (r3 * r3);

            double r4 = r + h * k3r;
            double v4 = radialVelocity + h * k3v;
            double k4r = v4;
            double k4v = radialAcceleration(r4, angularMomentumSquared);
            double k4p = angularMomentum / (r4 * r4);

            r += h * (k1r + 2.0 * k2r + 2.0 * k3r + k4r) / 6.0;
            radialVelocity += h * (k1v + 2.0 * k2v + 2.0 * k3v + k4v) / 6.0;
            psi += h * (k1p + 2.0 * k2p + 2.0 * k3p + k4p) / 6.0;

            if (!Double.isFinite(r) || !Double.isFinite(radialVelocity) || !Double.isFinite(psi)) {
                out.set(0.0, 0.0, 0.0);
                return;
            }

            double currentZ = positionZ(r, psi, camera.q0z, q1z);
            if (crossedEquatorialPlane(previousZ, currentZ)) {
                double interpolation = previousZ / (previousZ - currentZ);
                interpolation = clamp(interpolation, 0.0, 1.0);
                double crossingR = lerp(previousR, r, interpolation);
                double crossingPsi = lerp(previousPsi, psi, interpolation);

                if (crossingR >= DISK_INNER && crossingR <= DISK_OUTER) {
                    double cosine = Math.cos(crossingPsi);
                    double sine = Math.sin(crossingPsi);
                    double px = crossingR * (cosine * camera.q0x + sine * q1x);
                    double py = crossingR * (cosine * camera.q0y + sine * q1y);
                    double azimuth = Math.atan2(py, px);

                    shadeDisk(crossingR, azimuth, energy, angularMomentumZ, out);
                    return;
                }
            }
        }

        // Extremely long-lived rays near the unstable photon orbit.
        // Treat them as captured rather than leaking numerical noise.
        out.set(0.0, 0.0, 0.0);
    }

    private static double escapedDirectionComponent(
            double r,
            double radialVelocity,
            double psi,
            double angularMomentum,
            double q0,
            double q1
    ) {
        double cosine = Math.cos(psi);
        double sine = Math.sin(psi);
        double radialBasis = cosine * q0 + sine * q1;
        double tangentBasis = -sine * q0 + cosine * q1;
        double f = Math.max(1.0e-9, schwarzschildF(r));
        return (radialVelocity / Math.sqrt(f)) * radialBasis
                + (angularMomentum / r) * tangentBasis;
    }

    private static boolean crossedEquatorialPlane(double previousZ, double currentZ) {
        if (Math.abs(previousZ) < 1.0e-8) {
            return false;
        }
        return previousZ * currentZ <= 0.0;
    }

    private static double positionZ(double r, double psi, double q0z, double q1z) {
        return r * (Math.cos(psi) * q0z + Math.sin(psi) * q1z);
    }

    private static double radialAcceleration(double r, double angularMomentumSquared) {
        if (r <= 0.0) {
            return 0.0;
        }
        double r2 = r * r;
        double r3 = r2 * r;
        return angularMomentumSquared * (1.0 - 3.0 * M / r) / r3;
    }

    private static double schwarzschildF(double r) {
        return 1.0 - 2.0 * M / r;
    }

    private static void shadeDisk(
            double r,
            double azimuth,
            double photonEnergy,
            double photonAngularMomentumZ,
            Sample out
    ) {
        // Circular equatorial geodesic around a Schwarzschild black hole.
        double orbitalOmega = Math.sqrt(M / (r * r * r));
        double emitterUt = 1.0 / Math.sqrt(1.0 - 3.0 * M / r);
        double emittedFrequency = emitterUt * (photonEnergy - orbitalOmega * photonAngularMomentumZ);
        double redshift = 1.0 / Math.max(0.08, emittedFrequency); // observer's local photon energy is 1.
        redshift = clamp(redshift, 0.20, 3.5);

        // Thin-disk radial emissivity/temperature profile.
        double noTorque = Math.max(0.0, 1.0 - Math.sqrt(DISK_INNER / r));
        double flux = noTorque / (r * r * r);
        double normalizedFlux = flux * 7_500.0;
        double temperature = 1_000.0 + 6_800.0 * Math.pow(Math.max(0.0, normalizedFlux), 0.25);
        temperature *= redshift;
        temperature = clamp(temperature, 900.0, 10_500.0);

        // Procedural turbulent filaments in polar disk coordinates.
        double spiral = azimuth
                + 7.5 * Math.log(r / DISK_INNER)
                + 0.55 * noise(r * 0.24, azimuth * 1.7);
        double filaments = 0.5 + 0.5 * Math.sin(28.0 * Math.log(r) + 10.0 * spiral);
        filaments = Math.pow(filaments, 3.0);
        double turbulence = fbm(
                Math.cos(azimuth) * r * 0.46,
                Math.sin(azimuth) * r * 0.46
        );
        double texture = 0.20 + 0.72 * turbulence + 0.18 * filaments;

        // Lorentz-invariant I_nu / nu^3: observed specific intensity scales as g^3.
        double relativisticBrightness = redshift * redshift * redshift;
        double radialFade = smoothstep(DISK_OUTER, DISK_OUTER * 0.72, r);
        double innerFade = smoothstep(DISK_INNER, DISK_INNER * 1.12, r);
        double brightness = 0.62 * texture * relativisticBrightness * radialFade * innerFade;

        blackBodyRgb(temperature, out);
        out.multiply(brightness);

        // Hot, optically thin-looking highlights close to the inner edge.
        double innerGlow = Math.exp(-(r - DISK_INNER) / 2.2);
        out.r += 0.30 * innerGlow * relativisticBrightness;
        out.g += 0.12 * innerGlow * relativisticBrightness;
        out.b += 0.025 * innerGlow * relativisticBrightness;
    }

    private static void sampleSky(double x, double y, double z, Sample out) {
        double inverse = invSqrt(x * x + y * y + z * z);
        x *= inverse;
        y *= inverse;
        z *= inverse;

        // Rotate the procedural galaxy so its bright band is diagonal in frame.
        double gx = 0.80 * x + 0.60 * z;
        double gy = y;
        double gz = -0.60 * x + 0.80 * z;

        double longitude = Math.atan2(gz, gx);
        double latitude = Math.asin(clamp(gy, -1.0, 1.0));

        double bandDistance = Math.abs(latitude + 0.10 * Math.sin(2.0 * longitude));
        double galacticBand = Math.exp(-bandDistance * bandDistance / 0.018);
        double cloud = fbm(longitude * 2.2 + 7.0, latitude * 7.0 - 3.0);
        double dust = fbm(longitude * 5.0 - 12.0, latitude * 13.0 + 4.0);

        double background = 0.0018 + 0.018 * galacticBand * (0.25 + 0.75 * cloud);
        out.r = background * (0.72 + 0.40 * dust);
        out.g = background * (0.82 + 0.28 * dust);
        out.b = background * (1.12 + 0.25 * cloud);

        // Stable, sparse procedural stars on an equirectangular grid.
        double u = (longitude + Math.PI) / (2.0 * Math.PI);
        double v = (latitude + Math.PI * 0.5) / Math.PI;
        int gridX = floorToInt(u * 6_000.0);
        int gridY = floorToInt(v * 3_000.0);
        long hash = hash(gridX, gridY);
        double probability = toUnit(hash);
        double starThreshold = 0.9986 - 0.0009 * galacticBand;

        if (probability > starThreshold) {
            double intensity = Math.pow((probability - starThreshold) / (1.0 - starThreshold), 5.0);
            intensity = 1.8 + 15.0 * intensity;
            double temperatureSelector = toUnit(mix64(hash ^ 0x9E3779B97F4A7C15L));
            double starTemperature = 2_700.0 + 8_500.0 * temperatureSelector;
            Sample star = new Sample();
            blackBodyRgb(starTemperature, star);
            out.r += intensity * star.r;
            out.g += intensity * star.g;
            out.b += intensity * star.b;
        }
    }

    /** Approximate black-body colour in linear RGB. */
    private static void blackBodyRgb(double kelvin, Sample out) {
        double temperature = kelvin / 100.0;
        double red;
        double green;
        double blue;

        if (temperature <= 66.0) {
            red = 255.0;
            green = 99.4708025861 * Math.log(Math.max(1.0, temperature)) - 161.1195681661;
            blue = temperature <= 19.0
                    ? 0.0
                    : 138.5177312231 * Math.log(temperature - 10.0) - 305.0447927307;
        } else {
            red = 329.698727446 * Math.pow(temperature - 60.0, -0.1332047592);
            green = 288.1221695283 * Math.pow(temperature - 60.0, -0.0755148492);
            blue = 255.0;
        }

        // The common Kelvin approximation above is display-encoded; decode to linear.
        out.r = srgbToLinear(clamp(red / 255.0, 0.0, 1.0));
        out.g = srgbToLinear(clamp(green / 255.0, 0.0, 1.0));
        out.b = srgbToLinear(clamp(blue / 255.0, 0.0, 1.0));
    }

    private static BufferedImage finish(Frame frame) {
        int width = frame.width;
        int height = frame.height;
        int count = width * height;

        float[] brightR = new float[count];
        float[] brightG = new float[count];
        float[] brightB = new float[count];

        for (int i = 0; i < count; i++) {
            double luminance = 0.2126 * frame.r[i] + 0.7152 * frame.g[i] + 0.0722 * frame.b[i];
            double amount = Math.max(0.0, luminance - 0.85) / Math.max(1.0e-6, luminance);
            brightR[i] = (float) (frame.r[i] * amount);
            brightG[i] = (float) (frame.g[i] * amount);
            brightB[i] = (float) (frame.b[i] * amount);
        }

        gaussianBlur(brightR, width, height, BLOOM_RADIUS, BLOOM_SIGMA);
        gaussianBlur(brightG, width, height, BLOOM_RADIUS, BLOOM_SIGMA);
        gaussianBlur(brightB, width, height, BLOOM_RADIUS, BLOOM_SIGMA);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[count];

        for (int i = 0; i < count; i++) {
            double r = frame.r[i] + BLOOM_STRENGTH * brightR[i];
            double g = frame.g[i] + BLOOM_STRENGTH * brightG[i];
            double b = frame.b[i] + BLOOM_STRENGTH * brightB[i];

            int ir = toDisplayByte(aces(r * EXPOSURE));
            int ig = toDisplayByte(aces(g * EXPOSURE));
            int ib = toDisplayByte(aces(b * EXPOSURE));
            pixels[i] = (ir << 16) | (ig << 8) | ib;
        }

        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private static void gaussianBlur(float[] data, int width, int height, int radius, double sigma) {
        double[] kernel = new double[2 * radius + 1];
        double total = 0.0;
        for (int i = -radius; i <= radius; i++) {
            double value = Math.exp(-(i * (double) i) / (2.0 * sigma * sigma));
            kernel[i + radius] = value;
            total += value;
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= total;
        }

        float[] temporary = new float[data.length];

        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                double sum = 0.0;
                for (int k = -radius; k <= radius; k++) {
                    int sx = clampInt(x + k, 0, width - 1);
                    sum += data[row + sx] * kernel[k + radius];
                }
                temporary[row + x] = (float) sum;
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sum = 0.0;
                for (int k = -radius; k <= radius; k++) {
                    int sy = clampInt(y + k, 0, height - 1);
                    sum += temporary[sy * width + x] * kernel[k + radius];
                }
                data[y * width + x] = (float) sum;
            }
        }
    }

    private static double aces(double x) {
        x = Math.max(0.0, x);
        return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
    }

    private static int toDisplayByte(double linear) {
        double srgb = linear <= 0.0031308
                ? 12.92 * linear
                : 1.055 * Math.pow(linear, 1.0 / 2.4) - 0.055;
        return clampInt((int) Math.round(255.0 * srgb), 0, 255);
    }

    private static double srgbToLinear(double value) {
        return value <= 0.04045
                ? value / 12.92
                : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double radicalInverse(int index, int base) {
        double inverseBase = 1.0 / base;
        double inversePower = inverseBase;
        double result = 0.0;
        while (index > 0) {
            int digit = index % base;
            result += digit * inversePower;
            index /= base;
            inversePower *= inverseBase;
        }
        return result;
    }

    private static double fbm(double x, double y) {
        double value = 0.0;
        double amplitude = 0.5;
        double frequency = 1.0;
        for (int octave = 0; octave < 5; octave++) {
            value += amplitude * noise(x * frequency, y * frequency);
            frequency *= 2.03;
            amplitude *= 0.5;
        }
        return value / 0.96875;
    }

    private static double noise(double x, double y) {
        int x0 = floorToInt(x);
        int y0 = floorToInt(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        double tx = x - x0;
        double ty = y - y0;
        double sx = tx * tx * (3.0 - 2.0 * tx);
        double sy = ty * ty * (3.0 - 2.0 * ty);

        double n00 = toUnit(hash(x0, y0));
        double n10 = toUnit(hash(x1, y0));
        double n01 = toUnit(hash(x0, y1));
        double n11 = toUnit(hash(x1, y1));

        return lerp(lerp(n00, n10, sx), lerp(n01, n11, sx), sy);
    }

    private static long hash(int x, int y) {
        long value = 0x9E3779B97F4A7C15L;
        value ^= (long) x * 0xBF58476D1CE4E5B9L;
        value ^= (long) y * 0x94D049BB133111EBL;
        return mix64(value);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    private static double toUnit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static int floorToInt(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double invSqrt(double value) {
        return 1.0 / Math.sqrt(value);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clampInt(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Camera {
        final int width;
        final int height;
        final double aspect;
        final double tanHalfFov;

        final double q0x;
        final double q0y;
        final double q0z;

        final double forwardX;
        final double forwardY;
        final double forwardZ;

        final double rightX;
        final double rightY;
        final double rightZ;

        final double upX;
        final double upY;
        final double upZ;

        Camera(int width, int height) {
            this.width = width;
            this.height = height;
            aspect = width / (double) height;
            tanHalfFov = Math.tan(VERTICAL_FOV * 0.5);

            q0x = Math.sin(CAMERA_THETA) * Math.cos(CAMERA_PHI);
            q0y = Math.sin(CAMERA_THETA) * Math.sin(CAMERA_PHI);
            q0z = Math.cos(CAMERA_THETA);

            forwardX = -q0x;
            forwardY = -q0y;
            forwardZ = -q0z;

            // right = normalize(forward x worldUp)
            double rx = forwardY;
            double ry = -forwardX;
            double rz = 0.0;
            double inverseRightLength = invSqrt(rx * rx + ry * ry + rz * rz);
            rightX = rx * inverseRightLength;
            rightY = ry * inverseRightLength;
            rightZ = rz;

            // up = right x forward
            double ux = rightY * forwardZ - rightZ * forwardY;
            double uy = rightZ * forwardX - rightX * forwardZ;
            double uz = rightX * forwardY - rightY * forwardX;
            double inverseUpLength = invSqrt(ux * ux + uy * uy + uz * uz);
            upX = ux * inverseUpLength;
            upY = uy * inverseUpLength;
            upZ = uz * inverseUpLength;
        }
    }

    private static final class Frame {
        final int width;
        final int height;
        final float[] r;
        final float[] g;
        final float[] b;

        Frame(int width, int height) {
            this.width = width;
            this.height = height;
            int count = width * height;
            r = new float[count];
            g = new float[count];
            b = new float[count];
        }
    }

    private static final class Sample {
        double r;
        double g;
        double b;

        void set(double red, double green, double blue) {
            r = red;
            g = green;
            b = blue;
        }

        void clear() {
            r = 0.0;
            g = 0.0;
            b = 0.0;
        }

        void add(Sample other) {
            r += other.r;
            g += other.g;
            b += other.b;
        }

        void multiply(double scalar) {
            r *= scalar;
            g *= scalar;
            b *= scalar;
        }
    }
}