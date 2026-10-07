package myau.management;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;

/**
 * Tracks action packets that were sent since the last movement packet.
 *
 * <p>Ported from the Myau-250910 rewrite ({@code PacketActionTracker}). Modules use this to
 * avoid issuing conflicting interactions within a single server tick: once a digging or
 * placement packet is on the wire, KillAura/BedNuker will not attack or block until the next
 * {@link C03PacketPlayer} resets the flags.
 */
public class PacketActionTracker {
    public boolean sentUseEntity;
    public boolean sentDigging;
    public boolean sentBlockPlacement;
    public boolean sentHeldItemChange;
    public boolean sentAnimation;

    /**
     * Records a sent packet, clearing every flag when the packet is a movement update.
     * That mirrors the upstream {@code record} method exactly.
     */
    public void record(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) {
            this.sentUseEntity = true;
        }
        if (packet instanceof C07PacketPlayerDigging) {
            this.sentDigging = true;
        }
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            this.sentBlockPlacement = true;
        }
        if (packet instanceof C09PacketHeldItemChange) {
            this.sentHeldItemChange = true;
        }
        if (packet instanceof C0APacketAnimation) {
            this.sentAnimation = true;
        }
        if (packet instanceof C03PacketPlayer) {
            this.sentUseEntity = false;
            this.sentDigging = false;
            this.sentBlockPlacement = false;
            this.sentHeldItemChange = false;
            this.sentAnimation = false;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND) {
            this.record(event.getPacket());
        }
    }
}
