package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AttackEvent;
import myau.events.LeftClickMouseEvent;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.research.OfflineTraceGenerator;
import myau.research.PacketResearchLogger;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GlStateManager;

import java.io.File;
import java.io.IOException;

public class PacketResearch extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty packetLogging = new BooleanProperty("Packet-Logging", true);
    public final BooleanProperty includeStructure = new BooleanProperty("Packet-Structure", true);
    public final BooleanProperty behaviorLogging = new BooleanProperty("Behavior-Logging", true);
    public final BooleanProperty showOverlay = new BooleanProperty("Show-Overlay", true);
    public final BooleanProperty generateOfflineTrace = new BooleanProperty("Generate-Offline-Trace", false);
    public final ModeProperty offlineProfile = new ModeProperty("Offline-Profile", 0, new String[]{"Baseline", "Boundary", "Anomaly"});
    public final IntProperty offlineSamples = new IntProperty("Offline-Samples", 240, 20, 5000);
    public final IntProperty snapshotTicks = new IntProperty("Snapshot-Ticks", 20, 1, 200);

    private final PacketResearchLogger logger = new PacketResearchLogger();
    private int ticks;
    private boolean warnedNonLocal;

    public PacketResearch() {
        super("PacketResearch", false, false);
    }

    @Override
    public void onEnabled() {
        this.ticks = 0;
        this.warnedNonLocal = false;
        try {
            this.logger.open();
            ChatUtil.sendFormatted("&7[&bPacketResearch&7] &aLogging metadata to &f" + this.logger.getDirectory().getPath());
        } catch (IOException e) {
            ChatUtil.sendFormatted("&7[&bPacketResearch&7] &cUnable to open research log: " + e.getMessage());
            this.setEnabled(false);
        }
    }

    @Override
    public void onDisabled() {
        this.logger.close();
    }

    @Override
    public void verifyValue(String string) {
        if ("Generate-Offline-Trace".equalsIgnoreCase(string) && this.generateOfflineTrace.getValue()) {
            try {
                File output = OfflineTraceGenerator.generate(this.offlineProfile.getModeString(), this.offlineSamples.getValue());
                ChatUtil.sendFormatted("&7[&bPacketResearch&7] &aGenerated offline trace: &f" + output.getName());
            } catch (IOException e) {
                ChatUtil.sendFormatted("&7[&bPacketResearch&7] &cUnable to generate offline trace: " + e.getMessage());
            } finally {
                this.generateOfflineTrace.setValue(false);
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || !this.packetLogging.getValue() || !this.isLocalResearchContext()) {
            return;
        }
        this.logger.recordPacket(event.getType(), event.getPacket(), this.includeStructure.getValue());
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || !this.isLocalResearchContext()) {
            return;
        }
        this.ticks++;
        if (this.ticks >= this.snapshotTicks.getValue()) {
            this.ticks = 0;
            this.logger.writeTickSnapshot();
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (!this.isEnabled() || !this.behaviorLogging.getValue() || !this.isLocalResearchContext()) {
            return;
        }
        this.logger.recordLeftClick();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || !this.behaviorLogging.getValue() || !this.isLocalResearchContext()) {
            return;
        }
        this.logger.recordAttack(event.getTarget() == null ? "unknown" : event.getTarget().getName());
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || !this.showOverlay.getValue()) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        int x = sr.getScaledWidth() - 152;
        int y = 8;
        GlStateManager.pushMatrix();
        mc.fontRendererObj.drawStringWithShadow("PacketResearch", x, y, 0x55FFFF);
        mc.fontRendererObj.drawStringWithShadow("S: " + this.logger.getSentPackets() + "  R: " + this.logger.getReceivedPackets(), x, y + 10, 0xFFFFFF);
        mc.fontRendererObj.drawStringWithShadow("Types: " + this.logger.getUniquePacketTypes(), x, y + 20, 0xFFFFFF);
        mc.fontRendererObj.drawStringWithShadow("Clicks: " + this.logger.getLeftClicks() + "  Attacks: " + this.logger.getAttacks(), x, y + 30, 0xFFFFFF);
        mc.fontRendererObj.drawStringWithShadow("Mode: local metadata-only", x, y + 40, 0xAAAAAA);
        GlStateManager.popMatrix();
    }

    private boolean isLocalResearchContext() {
        if (mc.isSingleplayer()) {
            return true;
        }
        ServerData serverData = mc.getCurrentServerData();
        if (serverData == null || serverData.serverIP == null) {
            return true;
        }
        String server = serverData.serverIP.toLowerCase();
        if (server.startsWith("[::1]") || "::1".equals(server)) {
            return true;
        }
        int colon = server.indexOf(':');
        if (colon >= 0) {
            server = server.substring(0, colon);
        }
        boolean local = "localhost".equals(server) || "127.0.0.1".equals(server) || "0.0.0.0".equals(server);
        if (!local && !this.warnedNonLocal) {
            this.warnedNonLocal = true;
            ChatUtil.sendFormatted("&7[&bPacketResearch&7] &eTelemetry is limited to singleplayer or localhost test servers.");
        }
        return local;
    }

}
