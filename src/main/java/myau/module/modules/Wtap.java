package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.module.Module;
import myau.util.TimerUtil;
import myau.property.properties.FloatProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.potion.Potion;

public class Wtap extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    private boolean active = false;
    private boolean stopForward = false;
    private long delayTicks = 0L;
    private long durationTicks = 0L;
    private int chanceAccumulator = 0;
    public final FloatProperty delay = new FloatProperty("delay", 5.5F, 0.0F, 10.0F);
    public final FloatProperty duration = new FloatProperty("duration", 1.5F, 1.0F, 5.0F);
    public final PercentProperty chance = new PercentProperty("chance", 100);

    /**
     * Whether the current sprint state still supports continuing a W-tap.
     * Ported from the upstream {@code canContinueWTap}: sneaking keeps the tap alive,
     * whereas blindness or item use aborts it.
     */
    private boolean canTrigger() {
        if (mc.thePlayer.movementInput.moveForward < 0.8F || mc.thePlayer.isCollidedHorizontally) {
            return false;
        }
        if ((float) mc.thePlayer.getFoodStats().getFoodLevel() <= 6.0F
                && !mc.thePlayer.capabilities.allowFlying) {
            return false;
        }
        if (mc.thePlayer.isSneaking()) {
            return true;
        }
        if (mc.thePlayer.isUsingItem() || mc.thePlayer.isPotionActive(Potion.blindness)) {
            return false;
        }
        return mc.gameSettings.keyBindSprint.isKeyDown();
    }

    public Wtap() {
        super("WTap", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (this.active) {
            if (!this.stopForward && !this.canTrigger()) {
                this.active = false;
                while (this.delayTicks > 0L) {
                    this.delayTicks -= 50L;
                }
                while (this.durationTicks > 0L) {
                    this.durationTicks -= 50L;
                }
            } else if (this.delayTicks > 0L) {
                this.delayTicks -= 50L;
            } else {
                if (this.durationTicks > 0L) {
                    this.durationTicks -= 50L;
                    this.stopForward = true;
                    mc.thePlayer.movementInput.moveForward = 0.0F;
                }
                if (this.durationTicks <= 0L) {
                    this.active = false;
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled() && event.getType() == EventType.SEND) {
            if (event.getPacket() instanceof C02PacketUseEntity
                    && ((C02PacketUseEntity) event.getPacket()).getAction() == Action.ATTACK
                    && !this.active
                    && this.timer.hasTimeElapsed(500L)
                    && mc.thePlayer.isSprinting()) {
                this.timer.reset();
                // Accumulate the configured chance so sub-100 values are honoured
                // proportionally over many attacks rather than at random per hit.
                this.chanceAccumulator = this.chanceAccumulator % 100 + this.chance.getValue();
                if (this.chanceAccumulator < 100) {
                    return;
                }
                this.active = true;
                this.stopForward = false;
                this.delayTicks = this.delayTicks + (long) (50.0F * this.delay.getValue());
                this.durationTicks = this.durationTicks + (long) (50.0F * this.duration.getValue());
            }
        }
    }
}
