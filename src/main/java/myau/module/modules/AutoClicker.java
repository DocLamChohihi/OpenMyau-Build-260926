package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LeftClickMouseEvent;
import myau.events.RightClickMouseEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

import java.util.Objects;

/**
 * Automatically generates left clicks, with an optional sword block-hit cycle.
 *
 * <p>Reworked to match Myau-250910. The old model used a single {@code block-hit-ticks}
 * delay; the new model separates a hold duration, a release delay and a hurt-time gate
 * driven by the shared {@code HurtTimeTracker}, so the block-hit cycle stays in step with
 * the server's hurt animation instead of drifting. Timing is millisecond based throughout.
 */
public class AutoClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private boolean restoreAttackKey;
    private long clickCooldownMillis;
    private boolean restoreUseItemKey;
    private long blockHitHoldMillis;
    private long blockHitCycleMillis;

    public final IntProperty minCPS = new IntProperty("min-cps", 8, 1, 20);
    public final IntProperty maxCPS = new IntProperty("max-cps", 12, 1, 20);
    public final BooleanProperty blockHit = new BooleanProperty("block-hit", false);
    public final FloatProperty blockHitHold = new FloatProperty("block-hit-hold", 1.5F, 1.0F, 20.0F, this.blockHit::getValue);
    public final FloatProperty blockHitReleaseDelay = new FloatProperty("block-hit-delay", 0.0F, 0.0F, 20.0F, this.blockHit::getValue);
    public final IntProperty blockHitHurtTime = new IntProperty("block-hit-hurt-time", 6, 0, 10, this.blockHit::getValue);
    public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty breakBlocks = new BooleanProperty("break-blocks", true);

    private long getNextClickDelay() {
        return 1000L / RandomUtil.nextLong(this.minCPS.getValue(), this.maxCPS.getValue());
    }

    private long blockHitHoldDurationMillis() {
        return (long) (50.0F * this.blockHitHold.getValue());
    }

    private long blockHitDelayDurationMillis() {
        return (long) (50.0F * this.blockHitReleaseDelay.getValue());
    }

    private boolean isLookingAtBlock() {
        MovingObjectPosition hitResult = mc.objectMouseOver;
        return hitResult != null && hitResult.typeOfHit == MovingObjectType.BLOCK;
    }

    private boolean canAutoClick() {
        if (this.weaponsOnly.getValue() && !ItemUtil.isHoldingWeapon()) {
            if (!this.allowTools.getValue() || !ItemUtil.isHoldingTool()) {
                return false;
            }
        }
        if (!this.breakBlocks.getValue() || !this.isLookingAtBlock()) {
            return true;
        }
        GameType gameType = mc.playerController.getCurrentGameType();
        return gameType != GameType.SURVIVAL && gameType != GameType.CREATIVE;
    }

    public AutoClicker() {
        super("AutoClicker", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }

        if (this.clickCooldownMillis > 0L) {
            this.clickCooldownMillis -= 50L;
        }
        if (this.blockHitHoldMillis > 0L) {
            this.blockHitHoldMillis -= 50L;
        }
        if (this.blockHitCycleMillis > 0L) {
            this.blockHitCycleMillis -= 50L;
        }

        if (mc.currentScreen != null) {
            this.restoreAttackKey = false;
            this.restoreUseItemKey = false;
            return;
        }

        if (this.restoreAttackKey) {
            this.restoreAttackKey = false;
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
        }
        if (this.restoreUseItemKey) {
            this.restoreUseItemKey = false;
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }

        if (!this.isEnabled() || !this.canAutoClick() || !mc.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }

        if (!mc.thePlayer.isUsingItem()) {
            while (this.clickCooldownMillis <= 0L) {
                this.restoreAttackKey = true;
                this.clickCooldownMillis += this.getNextClickDelay();
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
            }
        }

        if (!this.blockHit.getValue()
                || !mc.gameSettings.keyBindUseItem.isKeyDown()
                || !ItemUtil.isHoldingSword()) {
            return;
        }

        boolean hurtTimeTooHigh = Myau.hurtTimeTracker.remainingHurtTicks() > this.blockHitHurtTime.getValue();

        if (!(this.blockHitHoldMillis > 0L && !hurtTimeTooHigh)) {
            this.restoreUseItemKey = true;
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);

            if (this.blockHitCycleMillis > 0L || hurtTimeTooHigh) {
                return;
            }
        }

        if (this.blockHitHoldMillis > 0L || mc.thePlayer.isUsingItem()) {
            return;
        }

        long holdDurationMillis = this.blockHitHoldDurationMillis();
        this.blockHitHoldMillis += holdDurationMillis;
        this.blockHitCycleMillis += holdDurationMillis + 50L + this.blockHitDelayDurationMillis();
        KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
    }

    /**
     * A real click was delivered, so restart the auto-click cooldown instead of
     * doubling up on it.
     */
    @EventTarget(Priority.LOWEST)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled() && !event.isCancelled() && !this.restoreAttackKey) {
            this.clickCooldownMillis += this.getNextClickDelay();
        }
    }

    /** A real block was raised, so extend the current hold rather than restarting it. */
    @EventTarget(Priority.LOWEST)
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled() && !event.isCancelled()
                && ItemUtil.isHoldingSword() && !this.restoreUseItemKey) {
            this.blockHitHoldMillis += this.blockHitHoldDurationMillis();
        }
    }

    @Override
    public void onEnabled() {
        this.clickCooldownMillis = 0L;
        this.blockHitHoldMillis = 0L;
        this.blockHitCycleMillis = 0L;
    }

    @Override
    public void verifyValue(String name) {
        if (this.minCPS.getName().equals(name)) {
            if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                this.maxCPS.setValue(this.minCPS.getValue());
            }
        } else if (this.maxCPS.getName().equals(name) && this.minCPS.getValue() > this.maxCPS.getValue()) {
            this.minCPS.setValue(this.maxCPS.getValue());
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(this.minCPS.getValue(), this.maxCPS.getValue())
                ? new String[]{this.minCPS.getValue().toString()}
                : new String[]{String.format("%d-%d", this.minCPS.getValue(), this.maxCPS.getValue())};
    }
}
