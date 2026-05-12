package myau.research;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Generates offline-only sample traces for anti-cheat validation exercises.
 *
 * <p>The generated data is written to disk and is never sent to a server.</p>
 */
public final class OfflineTraceGenerator {
    private static final SimpleDateFormat FILE_TIME = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);

    private OfflineTraceGenerator() {
    }

    public static File generate(String profile, int samples) throws IOException {
        File directory = new File(Minecraft.getMinecraft().mcDataDir, "myau/research");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create research log directory: " + directory.getAbsolutePath());
        }
        File output = new File(directory, "offline-trace-" + sanitize(profile) + "-" + FILE_TIME.format(new Date()) + ".jsonl");
        BufferedWriter writer = new BufferedWriter(new FileWriter(output));
        try {
            Random random = new Random(0x4D594155L + profile.hashCode());
            double x = 0.0D;
            double y = 64.0D;
            double z = 0.0D;
            float yaw = 0.0F;
            float pitch = 0.0F;
            long time = 0L;
            for (int i = 0; i < samples; i++) {
                SyntheticStep step = nextStep(profile, random, i);
                x += step.motionX;
                y += step.motionY;
                z += step.motionZ;
                yaw += step.yawDelta;
                pitch = clamp(pitch + step.pitchDelta, -90.0F, 90.0F);
                time += step.intervalMs;

                JsonObject json = new JsonObject();
                json.addProperty("type", "offline_trace_sample");
                json.addProperty("profile", profile);
                json.addProperty("sample", i);
                json.addProperty("timestampMs", time);
                json.addProperty("intervalMs", step.intervalMs);
                json.addProperty("posX", round(x));
                json.addProperty("posY", round(y));
                json.addProperty("posZ", round(z));
                json.addProperty("motionX", round(step.motionX));
                json.addProperty("motionY", round(step.motionY));
                json.addProperty("motionZ", round(step.motionZ));
                json.addProperty("yaw", round(yaw));
                json.addProperty("pitch", round(pitch));
                json.addProperty("onGround", step.onGround);
                json.addProperty("click", step.click);
                writer.write(json.toString());
                writer.newLine();
            }
        } finally {
            writer.close();
        }
        return output;
    }

    private static SyntheticStep nextStep(String profile, Random random, int sample) {
        SyntheticStep step = new SyntheticStep();
        step.intervalMs = 50L;
        step.onGround = true;
        if ("Boundary".equalsIgnoreCase(profile)) {
            step.motionX = 0.19D + random.nextDouble() * 0.05D;
            step.motionZ = 0.19D + random.nextDouble() * 0.05D;
            step.yawDelta = (float) ((random.nextDouble() - 0.5D) * 18.0D);
            step.pitchDelta = (float) ((random.nextDouble() - 0.5D) * 8.0D);
            step.click = sample % 6 == 0;
        } else if ("Anomaly".equalsIgnoreCase(profile)) {
            step.motionX = sample % 20 == 0 ? 1.2D : 0.12D + random.nextDouble() * 0.08D;
            step.motionZ = sample % 25 == 0 ? 1.2D : 0.12D + random.nextDouble() * 0.08D;
            step.yawDelta = sample % 15 == 0 ? 120.0F : (float) ((random.nextDouble() - 0.5D) * 12.0D);
            step.pitchDelta = sample % 17 == 0 ? 45.0F : (float) ((random.nextDouble() - 0.5D) * 6.0D);
            step.intervalMs = sample % 30 == 0 ? 5L : 50L;
            step.onGround = sample % 11 != 0;
            step.click = sample % 2 == 0;
        } else {
            step.motionX = 0.08D + random.nextDouble() * 0.08D;
            step.motionZ = 0.08D + random.nextDouble() * 0.08D;
            step.yawDelta = (float) ((random.nextDouble() - 0.5D) * 6.0D);
            step.pitchDelta = (float) ((random.nextDouble() - 0.5D) * 3.0D);
            step.click = sample % (8 + random.nextInt(7)) == 0;
        }
        return step;
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double round(double value) {
        return Math.round(value * 100000.0D) / 100000.0D;
    }

    private static final class SyntheticStep {
        private long intervalMs;
        private double motionX;
        private double motionY;
        private double motionZ;
        private float yawDelta;
        private float pitchDelta;
        private boolean onGround;
        private boolean click;
    }
}
