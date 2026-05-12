package myau.research;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import myau.event.types.EventType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Safety-focused packet telemetry writer for local research.
 *
 * <p>This class records packet metadata and player state only. It intentionally
 * does not mutate, delay, cancel, replay, or synthesize packets.</p>
 */
public final class PacketResearchLogger {
    private static final int MAX_PACKET_FIELDS = 16;
    private static final SimpleDateFormat FILE_TIME = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);

    private final Minecraft mc = Minecraft.getMinecraft();
    private final File directory;
    private final Map<String, PacketStats> packetStats = new LinkedHashMap<>();

    private BufferedWriter writer;
    private long sessionStartedAt;
    private long lastSentAt;
    private long lastReceivedAt;
    private int sentPackets;
    private int receivedPackets;
    private int leftClicks;
    private int attacks;

    public PacketResearchLogger() {
        this.directory = new File(mc.mcDataDir, "myau/research");
    }

    public boolean isOpen() {
        return this.writer != null;
    }

    public void open() throws IOException {
        if (this.writer != null) {
            return;
        }
        if (!this.directory.exists() && !this.directory.mkdirs()) {
            throw new IOException("Unable to create research log directory: " + this.directory.getAbsolutePath());
        }
        this.sessionStartedAt = System.currentTimeMillis();
        File output = new File(this.directory, "packet-research-" + FILE_TIME.format(new Date(this.sessionStartedAt)) + ".jsonl");
        this.writer = new BufferedWriter(new FileWriter(output, true));
        this.packetStats.clear();
        this.lastSentAt = 0L;
        this.lastReceivedAt = 0L;
        this.sentPackets = 0;
        this.receivedPackets = 0;
        this.leftClicks = 0;
        this.attacks = 0;
        this.writeSessionEvent("session_start");
    }

    public void close() {
        if (this.writer == null) {
            return;
        }
        try {
            this.writeSessionEvent("session_stop");
            this.writer.flush();
            this.writer.close();
        } catch (IOException ignored) {
        } finally {
            this.writer = null;
        }
    }

    public void recordPacket(EventType direction, Packet<?> packet, boolean includeStructure) {
        if (this.writer == null || packet == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean sending = direction == EventType.SEND;
        long previous = sending ? this.lastSentAt : this.lastReceivedAt;
        long delta = previous == 0L ? 0L : now - previous;
        if (sending) {
            this.lastSentAt = now;
            this.sentPackets++;
        } else {
            this.lastReceivedAt = now;
            this.receivedPackets++;
        }

        String packetName = packet.getClass().getName();
        PacketStats stats = this.packetStats.get(packetName);
        if (stats == null) {
            stats = new PacketStats();
            this.packetStats.put(packetName, stats);
        }
        stats.count++;
        stats.lastSeenAt = now;

        JsonObject json = baseEvent("packet", now);
        json.addProperty("direction", direction.name().toLowerCase(Locale.US));
        json.addProperty("packetClass", packetName);
        json.addProperty("packetSimpleName", packet.getClass().getSimpleName());
        json.addProperty("intervalMs", delta);
        json.addProperty("sentPackets", this.sentPackets);
        json.addProperty("receivedPackets", this.receivedPackets);
        addPlayerState(json);
        if (includeStructure) {
            json.add("structure", describePacketStructure(packet));
        }
        writeJson(json);
    }

    public void recordLeftClick() {
        this.leftClicks++;
        writeCounterEvent("left_click", this.leftClicks);
    }

    public void recordAttack(String targetName) {
        this.attacks++;
        JsonObject json = baseEvent("attack", System.currentTimeMillis());
        json.addProperty("count", this.attacks);
        json.addProperty("target", targetName == null ? "unknown" : targetName);
        addPlayerState(json);
        writeJson(json);
    }

    public void writeTickSnapshot() {
        if (this.writer == null) {
            return;
        }
        JsonObject json = baseEvent("snapshot", System.currentTimeMillis());
        json.addProperty("sentPackets", this.sentPackets);
        json.addProperty("receivedPackets", this.receivedPackets);
        json.addProperty("uniquePacketTypes", this.packetStats.size());
        json.addProperty("leftClicks", this.leftClicks);
        json.addProperty("attacks", this.attacks);
        addPlayerState(json);
        writeJson(json);
    }

    public int getSentPackets() {
        return this.sentPackets;
    }

    public int getReceivedPackets() {
        return this.receivedPackets;
    }

    public int getUniquePacketTypes() {
        return this.packetStats.size();
    }

    public int getLeftClicks() {
        return this.leftClicks;
    }

    public int getAttacks() {
        return this.attacks;
    }

    public File getDirectory() {
        return this.directory;
    }

    private void writeCounterEvent(String type, int count) {
        JsonObject json = baseEvent(type, System.currentTimeMillis());
        json.addProperty("count", count);
        addPlayerState(json);
        writeJson(json);
    }

    private void writeSessionEvent(String type) throws IOException {
        JsonObject json = baseEvent(type, System.currentTimeMillis());
        json.addProperty("note", "metadata-only research log; packets are not modified, delayed, replayed, or synthesized");
        this.writer.write(json.toString());
        this.writer.newLine();
    }

    private JsonObject baseEvent(String type, long now) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("timestampMs", now);
        json.addProperty("sessionAgeMs", this.sessionStartedAt == 0L ? 0L : now - this.sessionStartedAt);
        return json;
    }

    private void addPlayerState(JsonObject json) {
        if (mc.thePlayer == null) {
            json.addProperty("hasPlayer", false);
            return;
        }
        json.addProperty("hasPlayer", true);
        json.addProperty("posX", round(mc.thePlayer.posX));
        json.addProperty("posY", round(mc.thePlayer.posY));
        json.addProperty("posZ", round(mc.thePlayer.posZ));
        json.addProperty("motionX", round(mc.thePlayer.motionX));
        json.addProperty("motionY", round(mc.thePlayer.motionY));
        json.addProperty("motionZ", round(mc.thePlayer.motionZ));
        json.addProperty("yaw", round(mc.thePlayer.rotationYaw));
        json.addProperty("pitch", round(mc.thePlayer.rotationPitch));
        json.addProperty("onGround", mc.thePlayer.onGround);
        json.addProperty("sprinting", mc.thePlayer.isSprinting());
        json.addProperty("sneaking", mc.thePlayer.isSneaking());
        json.addProperty("ticksExisted", mc.thePlayer.ticksExisted);
    }

    private JsonArray describePacketStructure(Packet<?> packet) {
        JsonArray fields = new JsonArray();
        Class<?> current = packet.getClass();
        int added = 0;
        while (current != null && current != Object.class && added < MAX_PACKET_FIELDS) {
            for (Field field : current.getDeclaredFields()) {
                if (added >= MAX_PACKET_FIELDS) {
                    break;
                }
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || field.isSynthetic()) {
                    continue;
                }
                JsonObject fieldJson = new JsonObject();
                fieldJson.addProperty("declaringClass", current.getSimpleName());
                fieldJson.addProperty("name", field.getName());
                fieldJson.addProperty("type", field.getType().getName());
                fields.add(fieldJson);
                added++;
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private void writeJson(JsonObject json) {
        if (this.writer == null) {
            return;
        }
        try {
            this.writer.write(json.toString());
            this.writer.newLine();
        } catch (IOException ignored) {
        }
    }

    private double round(double value) {
        return Math.round(value * 100000.0D) / 100000.0D;
    }

    private static final class PacketStats {
        private int count;
        private long lastSeenAt;
    }
}
