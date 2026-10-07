package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Smoothly nudges the player's aim toward eligible nearby players.
 *
 * <p>Reworked to match Myau-250910: adds {@code require-press} and {@code allow-mining}
 * gating, prefers the KillAura target when one exists, prioritises enemies, and rounds
 * the final angles to the mouse-sensitivity grid so the aim looks human.
 */
public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long ATTACK_COOLDOWN_MILLIS = 350L;

    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final float[] snappedRotations = {-180.0F, 0.0F};
    private final float[] smoothedRotations = {-180.0F, 0.0F};

    public final FloatProperty hSpeed = new FloatProperty("horizontal-speed", 3.0F, 0.0F, 30.0F);
    public final FloatProperty vSpeed = new FloatProperty("vertical-speed", 0.0F, 0.0F, 30.0F);
    public final PercentProperty smoothing = new PercentProperty("smoothing", 50);
    public final FloatProperty range = new FloatProperty("range", 4.5F, 3.0F, 8.0F);
    public final IntProperty fov = new IntProperty("fov", 90, 30, 360);
    public final BooleanProperty requirePress = new BooleanProperty("require-press", true);
    public final BooleanProperty allowMining = new BooleanProperty("allow-mining", true);
    public final BooleanProperty weaponOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponOnly::getValue);
    public final BooleanProperty botChecks = new BooleanProperty("bot-check", true);
    public final BooleanProperty team = new BooleanProperty("teams", true);

    private boolean isValidTarget(EntityLivingBase candidate) {
        if (candidate == mc.thePlayer || candidate == mc.thePlayer.ridingEntity) {
            return false;
        }
        if (candidate == mc.getRenderViewEntity() || candidate == mc.getRenderViewEntity().ridingEntity) {
            return false;
        }
        if (candidate.deathTime > 0) {
            return false;
        }
        if (RotationUtil.distanceToEntity(candidate) > (double) this.range.getValue()) {
            return false;
        }
        if (RotationUtil.entityBoxAimError(candidate) > (float) this.fov.getValue()) {
            return false;
        }
        if (RotationUtil.rayTraceToEntity(candidate) != null) {
            return false;
        }
        if (!(candidate instanceof EntityPlayer)) {
            return true;
        }

        EntityPlayer player = (EntityPlayer) candidate;
        if (TeamUtil.isFriend(player)) {
            return false;
        }
        if (TeamUtil.isTarget(player)) {
            return true;
        }
        if (this.team.getValue() && TeamUtil.isSameTeam(player)) {
            return false;
        }
        return !this.botChecks.getValue() || !TeamUtil.isBot(player);
    }

    private boolean isInReach(EntityPlayer entityPlayer) {
        Reach reach = (Reach) Myau.moduleManager.modules.get(Reach.class);
        double distance = reach.isEnabled() ? (double) reach.range.getValue() : 3.0;
        return RotationUtil.distanceToEntity(entityPlayer) <= distance;
    }

    private boolean isLookingAtBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    /**
     * Prefers KillAura's current target so the two modules agree, then falls back to the
     * nearest eligible player, restricted to weapon reach when any candidate is in reach.
     */
    private EntityLivingBase findTarget() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (killAura.isEnabled()) {
            EntityLivingBase auraTarget = killAura.getTarget();
            if (auraTarget != null && this.isValidTarget(auraTarget)) {
                return auraTarget;
            }
        }

        List<EntityPlayer> candidates = mc.theWorld
                .loadedEntityList
                .stream()
                .filter(EntityPlayer.class::isInstance)
                .map(EntityPlayer.class::cast)
                .filter(this::isValidTarget)
                .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.stream().anyMatch(this::isInReach)) {
            candidates.removeIf(entityPlayer -> !this.isInReach(entityPlayer));
        }
        return candidates.get(0);
    }

    public AimAssist() {
        super("AimAssist", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST || mc.currentScreen != null) {
            return;
        }

        if (this.weaponOnly.getValue()
                && !ItemUtil.isHoldingWeapon()
                && (!this.allowTools.getValue() || !ItemUtil.isHoldingTool())) {
            return;
        }

        boolean attackPressed = PlayerUtil.isAttackKeyDown();
        if (this.allowMining.getValue() && attackPressed && this.isLookingAtBlock()) {
            return;
        }
        if (this.requirePress.getValue() && !attackPressed && this.lastAttackTimer.hasTimeElapsed(ATTACK_COOLDOWN_MILLIS)) {
            return;
        }

        EntityLivingBase target = this.findTarget();
        if (target == null || RotationUtil.distanceToEntity(target) <= 0.0) {
            return;
        }

        // Re-anchor the smoothing state whenever the player's real view changed, so the
        // assist never fights the mouse.
        if (this.snappedRotations[0] != mc.thePlayer.rotationYaw) {
            this.snappedRotations[0] = mc.thePlayer.rotationYaw;
            this.smoothedRotations[0] = mc.thePlayer.rotationYaw;
        }
        if (this.snappedRotations[1] != mc.thePlayer.rotationPitch) {
            this.snappedRotations[1] = mc.thePlayer.rotationPitch;
            this.smoothedRotations[1] = mc.thePlayer.rotationPitch;
        }

        AxisAlignedBB bounds = target.getEntityBoundingBox();
        double border = target.getCollisionBorderSize();
        float[] targetRotation = RotationUtil.aimAtBox(bounds.expand(border, border, border));
        float yawDelta = MathHelper.wrapAngleTo180_float(targetRotation[0] - this.smoothedRotations[0]);
        float pitchDelta = targetRotation[1] - this.smoothedRotations[1];

        if (Math.abs(yawDelta) < 0.5F) {
            yawDelta = 0.0F;
        }
        if (Math.abs(pitchDelta) < 0.5F) {
            pitchDelta = 0.0F;
        }

        yawDelta = RotationUtil.clampAngle(yawDelta, this.hSpeed.getValue() * 6.0F * RandomUtil.nextFloat(0.75F, 1.0F));
        pitchDelta = RotationUtil.clampAngle(pitchDelta, this.vSpeed.getValue() * 6.0F * RandomUtil.nextFloat(0.75F, 1.0F) * 0.5F);

        float smoothingAmount = this.smoothing.getValue() / 100.0F;
        yawDelta = RotationUtil.smoothAngle(yawDelta, smoothingAmount + RandomUtil.nextFloat(-0.05F, 0.05F));
        pitchDelta = RotationUtil.smoothAngle(pitchDelta, smoothingAmount + RandomUtil.nextFloat(-0.05F, 0.05F));

        // Nudge the idle axis so a single-axis correction does not look mechanical.
        if (yawDelta == 0.0F && pitchDelta != 0.0F) {
            yawDelta += RandomUtil.nextFloat(0.1F, 0.2F) * (RandomUtil.nextBoolean() ? 1.0F : -1.0F);
        } else if (yawDelta != 0.0F && pitchDelta == 0.0F) {
            pitchDelta += RandomUtil.nextFloat(0.1F, 0.2F) * (RandomUtil.nextBoolean() ? 1.0F : -1.0F);
        }

        this.smoothedRotations[0] += yawDelta;
        this.smoothedRotations[1] += pitchDelta;
        this.smoothedRotations[1] = MathHelper.clamp_float(this.smoothedRotations[1], -90.0F, 90.0F);
        this.snappedRotations[0] = RotationUtil.snapToMouseSensitivity(this.smoothedRotations[0], this.snappedRotations[0]);
        this.snappedRotations[1] = RotationUtil.snapToMouseSensitivity(this.smoothedRotations[1], this.snappedRotations[1]);
        this.snappedRotations[1] = MathHelper.clamp_float(this.snappedRotations[1], -90.0F, 90.0F);

        Myau.rotationManager.setRotation(this.snappedRotations[0], this.snappedRotations[1], 0, false);
    }

    @EventTarget
    public void onPress(KeyEvent event) {
        if (event.getKey() != mc.gameSettings.keyBindAttack.getKeyCode()) {
            return;
        }
        if (!Myau.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
            this.lastAttackTimer.reset();
        }
    }
}
