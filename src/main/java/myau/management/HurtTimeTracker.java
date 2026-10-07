package myau.management;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.DataWatcher;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;

/**
 * Tracks the remaining hurt-animation ticks of the local player from health updates.
 *
 * <p>Ported from the Myau-250910 rewrite ({@code HurtTimeTracker}). KillAura's auto-block
 * uses {@link #remainingHurtTicks()} to suppress blocking and attacks while the player is
 * still in hurt animation, which is more reliable than reading {@code hurtTime} directly
 * because the client value is reset before the server health packet arrives.
 */
public class HurtTimeTracker {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int HEALTH_METADATA_ID = 6;

    private boolean healthDecreasePending;
    private int remainingHurtTicks;

    public int remainingHurtTicks() {
        return this.remainingHurtTicks;
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (this.remainingHurtTicks > 0) {
            this.remainingHurtTicks--;
        }
        if (this.healthDecreasePending) {
            this.healthDecreasePending = false;
            this.remainingHurtTicks = HurtTimeTracker.mc.thePlayer.hurtTime;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE || event.isCancelled()) {
            return;
        }

        if (event.getPacket() instanceof S06PacketUpdateHealth) {
            S06PacketUpdateHealth packet = (S06PacketUpdateHealth) event.getPacket();
            if (packet.getHealth() < HurtTimeTracker.mc.thePlayer.getHealth()) {
                this.healthDecreasePending = true;
            }
            return;
        }

        if (!(event.getPacket() instanceof S1CPacketEntityMetadata)) {
            return;
        }

        S1CPacketEntityMetadata packet = (S1CPacketEntityMetadata) event.getPacket();
        if (packet.getEntityId() != HurtTimeTracker.mc.thePlayer.getEntityId()) {
            return;
        }

        for (DataWatcher.WatchableObject metadata : packet.func_149376_c()) {
            if (metadata.getDataValueId() == HEALTH_METADATA_ID
                    && (Float) metadata.getObject() < HurtTimeTracker.mc.thePlayer.getHealth()) {
                this.healthDecreasePending = true;
                break;
            }
        }
    }
}
