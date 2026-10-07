package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.RightClickMouseEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.ItemUtil;
import myau.util.MoveUtil;
import myau.util.PlayerUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import org.apache.commons.lang3.RandomUtils;

import java.util.Objects;

/**
 * Automatically sneaks at block edges so the player cannot walk off.
 *
 * <p>Reworked to match Myau-250910: the edge test now honours a configurable
 * {@code offset} margin, and an optional {@code align} mode cancels right-clicks that
 * would be sent while the player is misaligned with the block grid.
 */
public class Eagle extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int sneakDelay = 0;
    public final IntProperty minDelay = new IntProperty("min-delay", 2, 0, 10);
    public final IntProperty maxDelay = new IntProperty("max-delay", 3, 0, 10);
    public final PercentProperty offset = new PercentProperty("offset", 100);
    public final BooleanProperty align = new BooleanProperty("align", true);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty pitchCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);

    /**
     * True when stepping forward would leave support, tested against a box narrowed by
     * the configured {@code offset} so the player can hug the edge more tightly.
     */
    private boolean isNearEdge() {
        double[] offsetMovement = MoveUtil.predictMovement();
        return PlayerUtil.isNarrowOffsetCollisionFree(
                mc.thePlayer.motionX + offsetMovement[0],
                -1.0,
                mc.thePlayer.motionZ + offsetMovement[1],
                this.offset.getValue() / 100.0F
        );
    }

    /** Applies the module's direction, pitch, held-block and grounded preconditions. */
    private boolean canActivate() {
        if (this.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
            return false;
        }
        if (this.pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
            return false;
        }
        if (this.blocksOnly.getValue() && !ItemUtil.isHoldingPlaceableBlock()) {
            return false;
        }
        return mc.thePlayer.onGround;
    }

    public Eagle() {
        super("Eagle", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (this.sneakDelay > 0) {
            this.sneakDelay--;
        }
        if (this.sneakDelay != 0) {
            return;
        }
        if (this.isNearEdge()) {
            this.sneakDelay = RandomUtils.nextInt(this.minDelay.getValue(), this.maxDelay.getValue() + 1);
        }
    }

    /**
     * Suppresses right-clicks while the player is misaligned with the block grid, which
     * otherwise places blocks at a diagonal offset when bridging.
     */
    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (!this.isEnabled()
                || mc.currentScreen != null
                || !this.align.getValue()
                || mc.gameSettings.keyBindSneak.isKeyDown()
                || !this.canActivate()) {
            return;
        }
        if (this.sneakDelay <= 0 && !this.isNearEdge()) {
            return;
        }

        float movementYaw = (float) Math.toRadians(
                MoveUtil.adjustYaw(
                        mc.thePlayer.rotationYaw,
                        MoveUtil.getForwardValue(),
                        MoveUtil.getLeftValue()
                ) - 180.0F
        );
        float xDirection = -MathHelper.sin(movementYaw);
        float zDirection = MathHelper.cos(movementYaw);

        if (Math.abs(xDirection) < 0.1F || Math.abs(zDirection) < 0.1F) {
            return;
        }

        AxisAlignedBB bounds = mc.thePlayer.getEntityBoundingBox();
        double xEdge = xDirection >= 0.0F ? bounds.maxX : bounds.minX;
        double zEdge = zDirection >= 0.0F ? bounds.maxZ : bounds.minZ;
        double xAlignment = Math.abs(xEdge - Math.rint(xEdge));
        double zAlignment = Math.abs(zEdge - Math.rint(zEdge));

        if (Math.abs(xAlignment - zAlignment) > 0.125) {
            event.setCancelled(true);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled()
                || mc.currentScreen != null
                || mc.thePlayer.movementInput.sneak
                || !this.canActivate()) {
            return;
        }
        if (this.sneakDelay <= 0 && !this.isNearEdge()) {
            return;
        }
        mc.thePlayer.movementInput.sneak = true;
        mc.thePlayer.movementInput.moveStrafe *= 0.3F;
        mc.thePlayer.movementInput.moveForward *= 0.3F;
    }

    @Override
    public void onDisabled() {
        this.sneakDelay = 0;
    }

    @Override
    public void verifyValue(String name) {
        switch (name) {
            case "min-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "max-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(this.minDelay.getValue(), this.maxDelay.getValue())
                ? new String[]{this.minDelay.getValue().toString()}
                : new String[]{String.format("%d-%d", this.minDelay.getValue(), this.maxDelay.getValue())};
    }
}
