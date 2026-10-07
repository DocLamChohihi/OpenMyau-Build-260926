package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C07PacketPlayerDigging;

/**
 * Accelerates block breaking by injecting damage and shortening the hit delay.
 *
 * <p>Rewritten to match Myau-250910: instead of nudging the hit delay on every tick, it
 * reacts to the actual {@link C07PacketPlayerDigging} start/stop packets and applies a
 * configurable {@code chance}, so the boost lands proportionally instead of always.
 */
public class SpeedMine extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean startDiggingPending;
    private boolean stopDiggingPending;
    private int chanceAccumulator = 100;
    public final PercentProperty speed = new PercentProperty("speed", 15);
    public final IntProperty delay = new IntProperty("delay", 0, 0, 5);
    public final PercentProperty chance = new PercentProperty("chance", 50);

    public SpeedMine() {
        super("SpeedMine", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) {
            return;
        }

        if (this.startDiggingPending) {
            this.startDiggingPending = false;
            if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()
                    && this.chanceAccumulator >= 100) {
                float damage = ((IAccessorPlayerControllerMP) mc.playerController).getCurBlockDamageMP();
                float bonus = 0.3F * (this.speed.getValue().floatValue() / 100.0F);
                ((IAccessorPlayerControllerMP) mc.playerController).setCurBlockDamageMP(damage + bonus);
            }
        }

        if (!this.stopDiggingPending) {
            return;
        }
        this.stopDiggingPending = false;
        if (((IAccessorPlayerControllerMP) mc.playerController).getBlockHitDelay() != 5) {
            return;
        }

        this.chanceAccumulator = this.chanceAccumulator % 100 + this.chance.getValue();
        if (this.chanceAccumulator >= 100) {
            ((IAccessorPlayerControllerMP) mc.playerController).setBlockHitDelay(this.delay.getValue());
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            return;
        }
        if (event.getType() != EventType.SEND || !(event.getPacket() instanceof C07PacketPlayerDigging)) {
            return;
        }

        switch (((C07PacketPlayerDigging) event.getPacket()).getStatus()) {
            case START_DESTROY_BLOCK:
                this.startDiggingPending = true;
                break;
            case STOP_DESTROY_BLOCK:
                this.stopDiggingPending = true;
                break;
            default:
                break;
        }
    }

    @Override
    public void onDisabled() {
        this.startDiggingPending = false;
        this.stopDiggingPending = false;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%d%%", this.speed.getValue()), String.format("%s", this.delay.getValue())};
    }
}
