package myau.util;

import myau.Myau;
import myau.module.modules.KeepSprint;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.stats.AchievementList;
import net.minecraft.stats.StatList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.ForgeHooks;

public class PlayerUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean isJumping() {
        return mc.currentScreen == null && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode());
    }

    public static boolean isSneaking() {
        return mc.currentScreen == null && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());
    }

    public static boolean isMovingLeft() {
        return mc.currentScreen == null && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode());
    }

    public static boolean isMovingRight() {
        return mc.currentScreen == null && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode());
    }

    public static boolean isAttacking() {
        return mc.currentScreen == null && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
    }

    public static boolean isUsingItem() {
        return mc.currentScreen == null && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindUseItem.getKeyCode());
    }

    public static boolean canFly(float fallThreshold) {
        if (!mc.thePlayer.capabilities.allowFlying && !mc.thePlayer.capabilities.disableDamage) {
            PotionEffect jumpEffect = mc.thePlayer.getActivePotionEffect(Potion.jump);
            float jumpBoost = jumpEffect != null ? (float) (jumpEffect.getAmplifier() + 1) : 0.0F;
            float fallDistance = mc.thePlayer.fallDistance;
            if (mc.thePlayer.motionY < -0.67 || !isAirBelow()) {
                fallDistance -= (float) mc.thePlayer.motionY;
            }
            return MathHelper.ceiling_float_int(fallDistance - fallThreshold - jumpBoost) > 0;
        } else {
            return false;
        }
    }

    public static boolean canFly(int checkHeight) {
        if (!mc.thePlayer.capabilities.allowFlying && !mc.thePlayer.capabilities.disableDamage) {
            int playerY = MathHelper.floor_double(mc.thePlayer.posY);
            for (int offset = 0; offset <= checkHeight; ++offset) {
                int currentY = playerY - offset;
                if (currentY < 0) {
                    break;
                }
                Block block = mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, currentY, mc.thePlayer.posZ)).getBlock();
                if (!(block instanceof BlockAir)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public static boolean isInWater() {
        return checkInWater(mc.thePlayer.getEntityBoundingBox().expand(-1.0E-6, 0.0, -1.0E-6));
    }

    public static boolean checkInWater(AxisAlignedBB boundingBox) {
        if (!mc.thePlayer.isInWater() && !mc.thePlayer.isInLava()) {
            int minY = MathHelper.floor_double(boundingBox.minY);
            if (minY < 0) {
                return true;
            } else {
                int minX = MathHelper.floor_double(boundingBox.minX);
                int maxX = MathHelper.floor_double(boundingBox.maxX + 1.0);
                int minZ = MathHelper.floor_double(boundingBox.minZ);
                int maxZ = MathHelper.floor_double(boundingBox.maxZ + 1.0);
                for (int x = minX; x < maxX; ++x) {
                    for (int z = minZ; z < maxZ; ++z) {
                        for (int y = minY; y >= 0; --y) {
                            if (!BlockUtil.isReplaceable(new BlockPos(x, y, z))) {
                                return false;
                            }
                        }
                    }
                }
                return true;
            }
        } else {
            return false;
        }
    }

    public static boolean canMove(double x, double z) {
        return PlayerUtil.canMove(x, z, -1.0);
    }

    public static boolean canMove(double x, double z, double y) {
        AxisAlignedBB boundingBox = PlayerUtil.mc.thePlayer.getEntityBoundingBox().offset(x, y, z);
        return PlayerUtil.mc.theWorld.getCollidingBoundingBoxes(PlayerUtil.mc.thePlayer, boundingBox).isEmpty();
    }

    public static boolean isAirBelow() {
        AxisAlignedBB axisAlignedBB = PlayerUtil.mc.thePlayer.getEntityBoundingBox().offset(0.0, -1.0, 0.0);
        return !PlayerUtil.mc.theWorld.getCollidingBoundingBoxes(PlayerUtil.mc.thePlayer, axisAlignedBB).isEmpty();
    }

    public static boolean isAirAbove() {
        AxisAlignedBB axisAlignedBB = PlayerUtil.mc.thePlayer.getEntityBoundingBox().offset(0.0, 1.0, 0.0);
        return !PlayerUtil.mc.theWorld.getCollidingBoundingBoxes(PlayerUtil.mc.thePlayer, axisAlignedBB).isEmpty();
    }

    public static boolean canReach(BlockPos blockPos, double reach) {
        return PlayerUtil.isBlockWithinReach(blockPos, PlayerUtil.mc.thePlayer.posX, PlayerUtil.mc.thePlayer.posY + (double) PlayerUtil.mc.thePlayer.getEyeHeight(), PlayerUtil.mc.thePlayer.posZ, reach);
    }

    public static boolean isBlockWithinReach(BlockPos blockPos, double x, double y, double z, double reach) {
        return blockPos.distanceSqToCenter(x, y, z) < Math.pow(reach, 2.0);
    }

    // ------------------------------------------------------------------
    // Ported from the Myau-250910 rewrite (KillAura / Scaffold / NoFall / Eagle).
    // Upstream renamed these helpers; note the semantic differences from the
    // older canFly()/isAirBelow() pair above.
    // ------------------------------------------------------------------

    /** True when the game is not paused by a screen and the attack key is held. */
    public static boolean isAttackKeyDown() {
        return PlayerUtil.mc.currentScreen == null
                && KeyBindUtil.isKeyDown(PlayerUtil.mc.gameSettings.keyBindAttack.getKeyCode());
    }

    /** True when the game is not paused by a screen and the use-item key is held. */
    public static boolean isUseItemKeyDown() {
        return PlayerUtil.mc.currentScreen == null
                && KeyBindUtil.isKeyDown(PlayerUtil.mc.gameSettings.keyBindUseItem.getKeyCode());
    }

    /** True when the game is not paused by a screen and the sneak key is held. */
    public static boolean isSneakKeyDown() {
        return PlayerUtil.mc.currentScreen == null
                && KeyBindUtil.isKeyDown(PlayerUtil.mc.gameSettings.keyBindSneak.getKeyCode());
    }

    /**
     * True when the projected fall distance exceeds the safe threshold.
     * Equivalent to the upstream {@code PlayerUtils.wouldTakeFallDamage}.
     */
    public static boolean wouldTakeFallDamage(float safeFallDistance) {
        if (PlayerUtil.mc.thePlayer.capabilities.allowFlying || PlayerUtil.mc.thePlayer.capabilities.disableDamage) {
            return false;
        }
        PotionEffect jumpEffect = PlayerUtil.mc.thePlayer.getActivePotionEffect(Potion.jump);
        float jumpBoost = jumpEffect != null ? (float) (jumpEffect.getAmplifier() + 1) : 0.0f;
        float projectedFallDistance = PlayerUtil.mc.thePlayer.fallDistance;
        if (PlayerUtil.mc.thePlayer.motionY < -0.67 || !PlayerUtil.hasCollisionBelow()) {
            projectedFallDistance -= (float) PlayerUtil.mc.thePlayer.motionY;
        }
        return MathHelper.ceiling_float_int(projectedFallDistance - safeFallDistance - jumpBoost) > 0;
    }

    /**
     * True when every block straight below the player down to {@code depth} is air.
     * This is the upstream {@code isAirBelow(int)}; it returns the *opposite* polarity
     * of {@link #canFly(int)}, which returns false as soon as solid ground is found.
     */
    public static boolean isAirBelow(int depth) {
        if (PlayerUtil.mc.thePlayer.capabilities.allowFlying || PlayerUtil.mc.thePlayer.capabilities.disableDamage) {
            return false;
        }
        int playerY = MathHelper.floor_double(PlayerUtil.mc.thePlayer.posY);
        for (int offset = 0; offset <= depth; ++offset) {
            int y = playerY - offset;
            if (y < 0) {
                break;
            }
            Block block = PlayerUtil.mc.theWorld
                    .getBlockState(new BlockPos(PlayerUtil.mc.thePlayer.posX, y, PlayerUtil.mc.thePlayer.posZ))
                    .getBlock();
            if (!(block instanceof BlockAir)) {
                return false;
            }
        }
        return true;
    }

    /** True when the given box sits entirely over a void column. */
    public static boolean isBoundsOverVoid(AxisAlignedBB boundingBox) {        if (PlayerUtil.mc.thePlayer.isInWater() || PlayerUtil.mc.thePlayer.isInLava()) {
            return false;
        }
        int startY = MathHelper.floor_double(boundingBox.minY);
        if (startY < 0) {
            return true;
        }
        int minX = MathHelper.floor_double(boundingBox.minX);
        int maxX = MathHelper.floor_double(boundingBox.maxX + 1.0);
        int minZ = MathHelper.floor_double(boundingBox.minZ);
        int maxZ = MathHelper.floor_double(boundingBox.maxZ + 1.0);
        for (int x = minX; x < maxX; ++x) {
            for (int z = minZ; z < maxZ; ++z) {
                for (int y = startY; y >= 0; --y) {
                    if (!BlockUtil.isReplaceable(new BlockPos(x, y, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** True when the player's own box sits entirely over a void column. */
    public static boolean isOverVoid() {
        return PlayerUtil.isBoundsOverVoid(
                PlayerUtil.mc.thePlayer.getEntityBoundingBox().expand(-1.0E-6, 0.0, -1.0E-6));
    }

    /** True when the player's box offset by the given vector is free of collisions. */
    public static boolean isOffsetCollisionFree(double offsetX, double offsetY, double offsetZ) {
        AxisAlignedBB boundingBox = PlayerUtil.mc.thePlayer.getEntityBoundingBox().offset(offsetX, offsetY, offsetZ);
        return PlayerUtil.mc.theWorld.getCollidingBoundingBoxes(PlayerUtil.mc.thePlayer, boundingBox).isEmpty();
    }

    /**
     * Like {@link #isOffsetCollisionFree} but narrows the box along X/Z by
     * {@code widthFactor} first, matching the upstream Eagle edge test.
     */
    public static boolean isNarrowOffsetCollisionFree(double offsetX, double offsetY, double offsetZ, float widthFactor) {
        double horizontalInset = PlayerUtil.mc.thePlayer.width / -2.0F * (1.0F - widthFactor);
        AxisAlignedBB boundingBox = PlayerUtil.mc.thePlayer.getEntityBoundingBox()
                .offset(offsetX, offsetY, offsetZ)
                .expand(horizontalInset, 0.0, horizontalInset);
        return PlayerUtil.mc.theWorld.getCollidingBoundingBoxes(PlayerUtil.mc.thePlayer, boundingBox).isEmpty();
    }

    /** True when there is a collision one block below the player. */
    public static boolean hasCollisionBelow() {
        AxisAlignedBB axisAlignedBB = PlayerUtil.mc.thePlayer.getEntityBoundingBox().offset(0.0, -1.0, 0.0);
        return !PlayerUtil.mc.theWorld.getCollidingBoundingBoxes(PlayerUtil.mc.thePlayer, axisAlignedBB).isEmpty();
    }

    /** True when there is a collision one block above the player. */
    public static boolean hasCollisionAbove() {
        AxisAlignedBB axisAlignedBB = PlayerUtil.mc.thePlayer.getEntityBoundingBox().offset(0.0, 1.0, 0.0);
        return !PlayerUtil.mc.theWorld.getCollidingBoundingBoxes(PlayerUtil.mc.thePlayer, axisAlignedBB).isEmpty();
    }

    /** Estimate the damage the player's held item would deal to the target. */
    public static float estimateAttackDamage(Entity target) {
        float baseDamage = (float) PlayerUtil.mc.thePlayer
                .getEntityAttribute(SharedMonsterAttributes.attackDamage)
                .getAttributeValue();
        if (target instanceof EntityLivingBase) {
            baseDamage += EnchantmentHelper.getModifierForCreature(
                    PlayerUtil.mc.thePlayer.getHeldItem(), ((EntityLivingBase) target).getCreatureAttribute());
        }
        if (PlayerUtil.mc.thePlayer.fallDistance > 0.0F
                && !PlayerUtil.mc.thePlayer.onGround
                && !PlayerUtil.mc.thePlayer.isOnLadder()
                && !PlayerUtil.mc.thePlayer.isInWater()
                && !PlayerUtil.mc.thePlayer.isPotionActive(Potion.blindness)
                && PlayerUtil.mc.thePlayer.ridingEntity == null
                && baseDamage > 0.0F) {
            baseDamage *= 1.5F;
        }
        return baseDamage;
    }

    /** Length of the swing animation in ticks, adjusted for haste/mining fatigue. */
    public static int swingDuration() {
        if (PlayerUtil.mc.thePlayer.isPotionActive(Potion.digSpeed)) {
            return 6 - (1 + PlayerUtil.mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier());
        }
        if (PlayerUtil.mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
            return 6 + (1 + PlayerUtil.mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) * 2;
        }
        return 6;
    }

    /**
     * Plays only the local swing animation, without sending anything to the server.
     * Used by HitSelect to preserve the appearance of an attack it withholds.
     */
    public static void swingLocally() {
        if (PlayerUtil.mc.thePlayer.isSwingInProgress
                && PlayerUtil.mc.thePlayer.swingProgressInt < PlayerUtil.swingDuration() / 2
                && PlayerUtil.mc.thePlayer.swingProgressInt >= 0) {
            return;
        }
        PlayerUtil.mc.thePlayer.swingProgressInt = -1;
        PlayerUtil.mc.thePlayer.isSwingInProgress = true;
    }

    public static void attackEntity(Entity target) {
        if (ForgeHooks.onPlayerAttackTarget(mc.thePlayer, target)) {
            if (target.canAttackWithItem() && !target.hitByEntity(mc.thePlayer)) {
                float baseDamage = (float) mc.thePlayer.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue();
                float enchantmentBonus = EnchantmentHelper.getModifierForCreature(
                        mc.thePlayer.getHeldItem(),
                        target instanceof EntityLivingBase ? ((EntityLivingBase) target).getCreatureAttribute() : EnumCreatureAttribute.UNDEFINED
                );
                int knockbackLevel = EnchantmentHelper.getKnockbackModifier(mc.thePlayer);
                if (mc.thePlayer.isSprinting()) {
                    ++knockbackLevel;
                }
                if (baseDamage > 0.0F || enchantmentBonus > 0.0F) {
                    boolean isCritical = mc.thePlayer.fallDistance > 0.0F
                            && !mc.thePlayer.onGround
                            && !mc.thePlayer.isOnLadder()
                            && !mc.thePlayer.isInWater()
                            && !mc.thePlayer.isPotionActive(Potion.blindness)
                            && mc.thePlayer.ridingEntity == null;
                    if (isCritical && baseDamage > 0.0F) {
                        baseDamage *= 1.5F;
                    }
                    baseDamage += enchantmentBonus;
                    boolean isFireAspectApplied = false;
                    int fireAspectLevel = EnchantmentHelper.getFireAspectModifier(mc.thePlayer);
                    if (target instanceof EntityLivingBase && fireAspectLevel > 0 && !target.isBurning()) {
                        isFireAspectApplied = true;
                        target.setFire(1);
                    }
                    double originalMotionX = target.motionX;
                    double originalMotionY = target.motionY;
                    double originalMotionZ = target.motionZ;
                    if (target.attackEntityFrom(DamageSource.causePlayerDamage(mc.thePlayer), baseDamage)) {
                        if (knockbackLevel > 0) {
                            target.addVelocity(
                                    -MathHelper.sin(mc.thePlayer.rotationYaw * (float) Math.PI / 180.0F) * (float) knockbackLevel * 0.5F,
                                    0.1,
                                    MathHelper.cos(mc.thePlayer.rotationYaw * (float) Math.PI / 180.0F) * (float) knockbackLevel * 0.5F
                            );
                            KeepSprint keepSprint = (KeepSprint) Myau.moduleManager.modules.get(KeepSprint.class);
                            if (keepSprint.isEnabled()
                                    && (!keepSprint.groundOnly.getValue() || mc.thePlayer.onGround)
                                    && (!keepSprint.reachOnly.getValue() || !(RotationUtil.distanceToEntity(target) <= 3.0))) {
                                mc.thePlayer.motionX *= 0.6 + 0.4 * (1.0 - keepSprint.slowdown.getValue().doubleValue() / 100.0);
                                mc.thePlayer.motionZ *= 0.6 + 0.4 * (1.0 - keepSprint.slowdown.getValue().doubleValue() / 100.0);
                            } else {
                                mc.thePlayer.motionX *= 0.6;
                                mc.thePlayer.motionZ *= 0.6;
                                mc.thePlayer.setSprinting(false);
                            }
                        }
                        if (target instanceof EntityPlayerMP && target.velocityChanged) {
                            ((EntityPlayerMP) target).playerNetServerHandler.sendPacket(new S12PacketEntityVelocity(target));
                            target.velocityChanged = false;
                            target.motionX = originalMotionX;
                            target.motionY = originalMotionY;
                            target.motionZ = originalMotionZ;
                        }
                        if (isCritical) {
                            mc.thePlayer.onCriticalHit(target);
                        }
                        if (enchantmentBonus > 0.0F) {
                            mc.thePlayer.onEnchantmentCritical(target);
                        }
                        if (baseDamage >= 18.0F) {
                            mc.thePlayer.triggerAchievement(AchievementList.overkill);
                        }
                        mc.thePlayer.setLastAttacker(target);
                        if (target instanceof EntityLivingBase) {
                            EnchantmentHelper.applyThornEnchantments((EntityLivingBase) target, mc.thePlayer);
                        }
                        EnchantmentHelper.applyArthropodEnchantments(mc.thePlayer, target);
                        if (target instanceof EntityLivingBase) {
                            mc.thePlayer.addStat(StatList.damageDealtStat, Math.round(baseDamage * 10.0F));
                            if (fireAspectLevel > 0) {
                                target.setFire(fireAspectLevel * 4);
                            }
                        }
                        mc.thePlayer.addExhaustion(0.3F);
                    } else if (isFireAspectApplied) {
                        target.extinguish();
                    }
                }
            }
        }
    }
}
