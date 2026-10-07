package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.RotationState;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.DataWatcher.WatchableObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;

public class KillAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat df = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
    private static final long TICK_MILLIS = 50L;
    private static final float TICK_MILLIS_FLOAT = 50.0F;
    private static final int AUTO_BLOCK_NONE = 0;
    private static final int AUTO_BLOCK_VANILLA = 1;
    private static final int AUTO_BLOCK_HYPIXEL = 2;
    private static final int AUTO_BLOCK_BLINK = 3;
    private static final int AUTO_BLOCK_INTERACT = 4;
    private static final int AUTO_BLOCK_SPOOF = 5;
    private static final int AUTO_BLOCK_SWAP = 6;
    private static final int AUTO_BLOCK_LEGIT = 7;
    private static final int AUTO_BLOCK_FAKE = 8;

    private final TimerUtil timer = new TimerUtil();
    private TargetSnapshot target = null;
    private boolean hasTargetInAutoBlockRange = false;
    private int switchTick = 0;
    private boolean hitRegistered = false;
    private boolean blockingState = false;
    private boolean autoBlockActive = false;
    private boolean fakeBlockState = false;
    private boolean bufferRestartPending = false;
    private long attackDelayMS = 0L;
    private long blockHoldMS = 0L;
    private long blockReleaseDelayMS = 0L;
    private int blockTick = 0;
    private int lastTickProcessed;

    public final ModeProperty mode;
    public final ModeProperty sort;
    public final ModeProperty autoBlock;
    public final BooleanProperty autoBlockRequirePress;
    public final BooleanProperty autoBlockNoSlow;
    public final FloatProperty autoBlockHold;
    public final FloatProperty autoBlockDelay;
    public final IntProperty autoBlockHurtTime;
    public final FloatProperty autoBlockRange;
    public final FloatProperty swingRange;
    public final FloatProperty attackRange;
    public final IntProperty fov;
    public final IntProperty minCPS;
    public final IntProperty maxCPS;
    public final IntProperty switchDelay;
    public final ModeProperty rotations;
    public final ModeProperty moveFix;
    public final PercentProperty smoothing;
    public final IntProperty angleStep;
    public final BooleanProperty throughWalls;
    public final BooleanProperty requirePress;
    public final BooleanProperty allowMining;
    public final BooleanProperty weaponsOnly;
    public final BooleanProperty allowTools;
    public final BooleanProperty inventoryCheck;
    public final BooleanProperty botCheck;
    public final BooleanProperty players;
    public final BooleanProperty bosses;
    public final BooleanProperty mobs;
    public final BooleanProperty animals;
    public final BooleanProperty golems;
    public final BooleanProperty silverfish;
    public final BooleanProperty teams;
    public final ModeProperty showTarget;
    public final ModeProperty debugLog;

    private long nextAttackDelayMillis() {
        return 1000L / RandomUtil.nextLong(this.minCPS.getValue().longValue(), this.maxCPS.getValue().longValue());
    }

    private long blockHoldDurationMillis() {
        return (long) (this.autoBlockHold.getValue() * TICK_MILLIS_FLOAT);
    }

    private long blockReleaseDurationMillis() {
        return (long) (this.autoBlockDelay.getValue() * TICK_MILLIS_FLOAT);
    }

    private boolean tryAttackTarget(float yaw, float pitch) {
        if (Myau.packetActionTracker.sentDigging || Myau.packetActionTracker.sentBlockPlacement) {
            return false;
        }
        if (this.isPlayerBlocking() && this.autoBlock.getValue() != AUTO_BLOCK_VANILLA) {
            return false;
        }
        if (this.attackDelayMS > 0L) {
            return false;
        }
        HitSelect hitSelect = (HitSelect) Myau.moduleManager.modules.get(HitSelect.class);
        if (hitSelect.isEnabled() && hitSelect.shouldDelayAttack(this.target.getEntity())) {
            return false;
        }
        this.attackDelayMS += this.nextAttackDelayMillis();
        mc.thePlayer.swingItem();

        boolean canAttack;
        if (this.rotations.getValue() == 0) {
            canAttack = this.isWithinAttackRange(this.target.getDistance());
        } else {
            Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
            if (this.throughWalls.getValue()) {
                canAttack = RotationUtil.boundsRayTrace(
                        this.target.getBox(), eyes, yaw, pitch, this.attackRange.getValue().doubleValue()
                ) != null;
            } else {
                canAttack = !RotationUtil.isBoxRayObstructed(
                        this.target.getBox(), eyes, yaw, pitch, this.attackRange.getValue().doubleValue()
                );
            }
        }
        if (!canAttack) {
            return false;
        }

        AttackEvent event = new AttackEvent(this.target.getEntity());
        EventManager.call(event);
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.ATTACK));
        if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
            PlayerUtil.attackEntity(this.target.getEntity());
        }
        this.hitRegistered = true;
        return true;
    }

    private void sendUseItem() {
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        this.startBlock(mc.thePlayer.getHeldItem());
    }

    private void startBlock(ItemStack itemStack) {
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
        mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
        this.blockingState = true;
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        this.blockingState = false;
    }

    private void interactAttack(float yaw, float pitch) {
        if (this.target == null) {
            return;
        }
        MovingObjectPosition mop = RotationUtil.boundsRayTrace(this.target.getBox(), yaw, pitch, 8.0);
        if (mop != null) {
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
            PacketUtil.sendPacket(
                    new C02PacketUseEntity(
                            this.target.getEntity(),
                            new Vec3(
                                    mop.hitVec.xCoord - this.target.getX(),
                                    mop.hitVec.yCoord - this.target.getY(),
                                    mop.hitVec.zCoord - this.target.getZ()
                            )
                    )
            );
            PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.INTERACT));
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
            this.blockingState = true;
        }
    }

    private boolean canRunCombat() {
        if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) {
            return false;
        } else if (!(Boolean) this.weaponsOnly.getValue()
                || ItemUtil.isHoldingWeapon()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) {
                return false;
            } else {
                AutoHeal autoHeal = (AutoHeal) Myau.moduleManager.modules.get(AutoHeal.class);
                if (autoHeal.isEnabled() && autoHeal.isSwitching()) {
                    return false;
                } else {
                    BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
                    AutoBlockIn autoBlockIn = (AutoBlockIn) Myau.moduleManager.modules.get(AutoBlockIn.class);
                    if (bedNuker.isEnabled() && bedNuker.isReady()) {
                        return false;
                    } else if (Myau.moduleManager.modules.get(Scaffold.class).isEnabled()) {
                        return false;
                    } else if (autoBlockIn.isEnabled()) {
                        return false;
                    } else if (this.requirePress.getValue()) {
                        return PlayerUtil.isAttackKeyDown();
                    } else {
                        return !this.allowMining.getValue()
                                || !mc.objectMouseOver.typeOfHit.equals(MovingObjectType.BLOCK)
                                || !PlayerUtil.isAttackKeyDown();
                    }
                }
            }
        } else {
            return false;
        }
    }

    private boolean canAutoBlock() {
        if (!ItemUtil.isHoldingSword()) {
            return false;
        } else {
            int autoBlockMode = this.autoBlock.getValue();
            if (autoBlockMode == AUTO_BLOCK_NONE) {
                return true;
            } else if (autoBlockMode == AUTO_BLOCK_FAKE) {
                return this.hasTargetInAutoBlockRange;
            } else if (!this.autoBlockRequirePress.getValue() && this.hasTargetInAutoBlockRange) {
                return true;
            } else {
                return PlayerUtil.isUseItemKeyDown();
            }
        }
    }

    private boolean isValidTarget(EntityLivingBase entityLivingBase) {
        if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) {
            return false;
        } else if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.thePlayer.ridingEntity) {
            if (entityLivingBase == mc.getRenderViewEntity() || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else {
                return this.passesEntityCategoryFilters(entityLivingBase);
            }
        } else {
            return false;
        }
    }

    private boolean passesEntityCategoryFilters(EntityLivingBase entityLivingBase) {
        if (entityLivingBase.deathTime > 0) {
            return false;
        } else if (entityLivingBase instanceof EntityOtherPlayerMP) {
            if (!this.players.getValue()) {
                return false;
            } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                return false;
            } else {
                return (!this.teams.getValue() || !TeamUtil.isSameTeam((EntityPlayer) entityLivingBase))
                        && (!this.botCheck.getValue() || !TeamUtil.isBot((EntityPlayer) entityLivingBase));
            }
        } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
            return this.bosses.getValue();
        } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
            if (entityLivingBase instanceof EntityAnimal
                    || entityLivingBase instanceof EntityBat
                    || entityLivingBase instanceof EntitySquid
                    || entityLivingBase instanceof EntityVillager) {
                return this.animals.getValue();
            } else if (!(entityLivingBase instanceof EntityIronGolem)) {
                return false;
            } else {
                return this.golems.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
            }
        } else if (!(entityLivingBase instanceof EntitySilverfish)) {
            return this.mobs.getValue();
        } else {
            return this.silverfish.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
        }
    }

    private boolean isWithinAnyCombatRange(double distance) {
        return this.isWithinAutoBlockRange(distance)
                || this.isWithinSwingRange(distance)
                || this.isWithinAttackRange(distance);
    }

    private boolean isWithinAutoBlockRange(double distance) {
        return this.autoBlock.getValue() != AUTO_BLOCK_NONE && distance <= (double) this.autoBlockRange.getValue();
    }

    private boolean isWithinSwingRange(double distance) {
        return distance <= (double) this.swingRange.getValue();
    }

    private boolean isWithinAttackRange(double distance) {
        return distance <= (double) this.attackRange.getValue();
    }

    private boolean isEnemyPlayer(EntityLivingBase entityLivingBase) {
        return entityLivingBase instanceof EntityPlayer && TeamUtil.isTarget((EntityPlayer) entityLivingBase);
    }

    private int findEmptySlot(int currentSlot) {
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot && mc.thePlayer.inventory.getStackInSlot(i) == null) {
                return i;
            }
        }
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack != null && !stack.hasDisplayName()) {
                    return i;
                }
            }
        }
        return Math.floorMod(currentSlot - 1, 9);
    }

    private int findSwordSlot(int currentSlot) {
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot) {
                ItemStack item = mc.thePlayer.inventory.getStackInSlot(i);
                if (item != null && item.getItem() instanceof ItemSword) {
                    return i;
                }
            }
        }
        return -1;
    }

    public KillAura() {
        super("KillAura", false);
        this.lastTickProcessed = 0;
        this.mode = new ModeProperty("mode", 0, new String[]{"SINGLE", "SWITCH"});
        this.sort = new ModeProperty("sort", 0, new String[]{"DISTANCE", "HEALTH", "HURT_TIME", "FOV"});
        this.autoBlock = new ModeProperty(
                "auto-block", 7, new String[]{"NONE", "VANILLA", "HYPIXEL", "BLINK", "INTERACT", "SPOOF", "SWAP", "LEGIT", "FAKE"}
        );
        this.autoBlockRequirePress = new BooleanProperty("auto-block-require-press", true, this::isAutoBlockRequirePressVisible);
        this.autoBlockNoSlow = new BooleanProperty("auto-block-no-slow", false, this::isAutoBlockNoSlowVisible);
        this.autoBlockHold = new FloatProperty("auto-block-hold", 1.5F, 1.0F, 20.0F, this::isAutoBlockHoldVisible);
        this.autoBlockDelay = new FloatProperty("auto-block-delay", 0.0F, 0.0F, 20.0F, this::isAutoBlockDelayVisible);
        this.autoBlockHurtTime = new IntProperty("auto-block-hurt-time", 6, 0, 10, this::isAutoBlockHurtTimeVisible);
        this.autoBlockRange = new FloatProperty("auto-block-range", 4.0F, 3.0F, 8.0F, this::isAutoBlockRangeVisible);
        this.swingRange = new FloatProperty("swing-range", 4.0F, 3.0F, 6.0F);
        this.attackRange = new FloatProperty("attack-range", 3.0F, 3.0F, 6.0F);
        this.fov = new IntProperty("fov", 360, 30, 360);
        this.minCPS = new IntProperty("min-aps", 14, 1, 20);
        this.maxCPS = new IntProperty("max-aps", 14, 1, 20);
        this.switchDelay = new IntProperty("switch-delay", 150, 0, 1000);
        this.rotations = new ModeProperty("rotations", 2, new String[]{"NONE", "LEGIT", "SILENT", "LOCK_VIEW"});
        this.moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT", "STRICT"});
        this.smoothing = new PercentProperty("smoothing", 0);
        this.angleStep = new IntProperty("angle-step", 90, 30, 180);
        this.throughWalls = new BooleanProperty("through-walls", true);
        this.requirePress = new BooleanProperty("require-press", false);
        this.allowMining = new BooleanProperty("allow-mining", true);
        this.weaponsOnly = new BooleanProperty("weapons-only", true);
        this.allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
        this.inventoryCheck = new BooleanProperty("inventory-check", true);
        this.botCheck = new BooleanProperty("bot-check", true);
        this.players = new BooleanProperty("players", true);
        this.bosses = new BooleanProperty("bosses", false);
        this.mobs = new BooleanProperty("mobs", false);
        this.animals = new BooleanProperty("animals", false);
        this.golems = new BooleanProperty("golems", false);
        this.silverfish = new BooleanProperty("silverfish", false);
        this.teams = new BooleanProperty("teams", true);
        this.showTarget = new ModeProperty("show-target", 0, new String[]{"NONE", "DEFAULT", "HUD"});
        this.debugLog = new ModeProperty("debug-log", 0, new String[]{"NONE", "HEALTH"});
    }

    public EntityLivingBase getTarget() {
        return this.target != null ? this.target.getEntity() : null;
    }

    public boolean isAttackAllowed() {
        if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) {
            return false;
        } else if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()) {
            return false;
        } else {
            Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
            if (scaffold.isEnabled()) {
                return false;
            } else if (!(Boolean) this.weaponsOnly.getValue()
                    || ItemUtil.isHoldingWeapon()
                    || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
                return !this.requirePress.getValue() || PlayerUtil.isAttackKeyDown();
            } else {
                return false;
            }
        }
    }

    public boolean isAutoBlockActive() {
        return this.autoBlockActive;
    }

    /** Retained for {@link Velocity}: true while a non-fake auto-block is actively blocking. */
    public boolean shouldAutoBlock() {
        if (this.isPlayerBlocking() && this.autoBlockActive) {
            return !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava() && (this.autoBlock.getValue() == AUTO_BLOCK_HYPIXEL
                    || this.autoBlock.getValue() == AUTO_BLOCK_BLINK
                    || this.autoBlock.getValue() == AUTO_BLOCK_INTERACT
                    || this.autoBlock.getValue() == AUTO_BLOCK_SPOOF
                    || this.autoBlock.getValue() == AUTO_BLOCK_SWAP
                    || this.autoBlock.getValue() == AUTO_BLOCK_LEGIT);
        } else {
            return false;
        }
    }

    public boolean isBlocking() {
        return this.fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword();
    }

    private float[] calculateCombatRotation(float previousYaw, float previousPitch) {
        float yawDelta = MathHelper.wrapAngleTo180_float(this.target.getYaw() - previousYaw);
        float pitchDelta = this.target.getPitch() - previousPitch;
        if (Math.abs(yawDelta) < 0.5F) {
            yawDelta = 0.0F;
        }
        if (Math.abs(pitchDelta) < 0.5F) {
            pitchDelta = 0.0F;
        }
        yawDelta = RotationUtil.clampAngle(yawDelta, (float) this.angleStep.getValue() * RandomUtil.nextFloat(0.75F, 1.0F));
        pitchDelta = RotationUtil.clampAngle(pitchDelta, (float) this.angleStep.getValue() * RandomUtil.nextFloat(0.75F, 1.0F) * 0.5F);
        yawDelta = RotationUtil.smoothAngle(yawDelta, (float) this.smoothing.getValue() / 100.0F + RandomUtil.nextFloat(-0.05F, 0.05F));
        pitchDelta = RotationUtil.smoothAngle(pitchDelta, (float) this.smoothing.getValue() / 100.0F + RandomUtil.nextFloat(-0.05F, 0.05F));
        float mouseStep = RotationUtil.mouseSensitivityIncrement();
        if (yawDelta == 0.0F && pitchDelta != 0.0F) {
            yawDelta += mouseStep * 0.15F * (RandomUtil.nextBoolean() ? 1.0F : -1.0F);
        }
        if (yawDelta != 0.0F && pitchDelta == 0.0F) {
            pitchDelta += mouseStep * 0.15F * (RandomUtil.nextBoolean() ? 1.0F : -1.0F);
        }
        float yaw = RotationUtil.snapToMouseSensitivity(previousYaw + yawDelta, previousYaw);
        float pitch = RotationUtil.snapToMouseSensitivity(previousPitch + pitchDelta, previousPitch);
        return new float[]{yaw, MathHelper.clamp_float(pitch, -90.0F, 90.0F)};
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.POST && this.bufferRestartPending) {
            this.bufferRestartPending = false;
            Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
            Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        }
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            int controllerSlot = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
            boolean combatEligible = this.target != null && this.canRunCombat();
            boolean canAttackThisTick = combatEligible;
            boolean canBlockThisTick = combatEligible && this.canAutoBlock();
            int autoBlockMode = this.autoBlock.getValue();
            boolean blockSuppressed = autoBlockMode != AUTO_BLOCK_NONE
                    && autoBlockMode != AUTO_BLOCK_FAKE
                    && canBlockThisTick
                    && (Myau.hurtTimeTracker.remainingHurtTicks() > this.autoBlockHurtTime.getValue()
                    || this.blockReleaseDelayMS > 0L);

            if (this.attackDelayMS > 0L) {
                this.attackDelayMS -= TICK_MILLIS;
            }
            if (this.blockHoldMS > 0L) {
                this.blockHoldMS -= TICK_MILLIS;
            }
            if (this.blockReleaseDelayMS > 0L) {
                this.blockReleaseDelayMS -= TICK_MILLIS;
            }

            // Keep the current blocking phase only while blocking is eligible and not suppressed.
            if (!canBlockThisTick || blockSuppressed) {
                if (this.autoBlockActive
                        && this.isPlayerBlocking()
                        && !Myau.packetActionTracker.sentDigging
                        && !Myau.packetActionTracker.sentBlockPlacement) {
                    if (autoBlockMode == AUTO_BLOCK_INTERACT || autoBlockMode == AUTO_BLOCK_SPOOF) {
                        int swapSlot = this.findEmptySlot(controllerSlot);
                        PacketUtil.sendPacket(new C09PacketHeldItemChange(swapSlot));
                        ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(swapSlot);
                    } else {
                        this.stopBlock();
                    }
                    canAttackThisTick = false;
                }
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.autoBlockActive = false;
                this.fakeBlockState = false;
                if (this.blockHoldMS > 0L) {
                    this.blockHoldMS = 0L;
                }
                this.blockTick = 0;
            }

            // The attack-eligibility flag above may be cleared by a release/swap packet,
            // so only exit here when the initial combat gate failed.
            if (!combatEligible) {
                return;
            }

            if (blockSuppressed) {
                canBlockThisTick = false;
                this.autoBlockActive = true;
                this.fakeBlockState = true;
            }

            boolean shouldStartBlocking = false;
            if (canBlockThisTick) {
                switch (autoBlockMode) {
                    case AUTO_BLOCK_NONE:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        this.autoBlockActive = false;
                        this.fakeBlockState = false;
                        if (PlayerUtil.isUseItemKeyDown()
                                && !this.isPlayerBlocking()
                                && !Myau.packetActionTracker.sentDigging
                                && !Myau.packetActionTracker.sentBlockPlacement) {
                            shouldStartBlocking = true;
                        }
                        break;
                    case AUTO_BLOCK_VANILLA:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        this.autoBlockActive = true;
                        this.fakeBlockState = false;
                        if (!this.isPlayerBlocking()
                                && !Myau.packetActionTracker.sentDigging
                                && !Myau.packetActionTracker.sentBlockPlacement) {
                            shouldStartBlocking = true;
                        }
                        break;
                    case AUTO_BLOCK_HYPIXEL:
                        this.autoBlockActive = true;
                        this.fakeBlockState = true;
                        if (!Myau.packetActionTracker.sentDigging && !Myau.packetActionTracker.sentBlockPlacement) {
                            switch (this.blockTick) {
                                case 0:
                                    if (!this.isPlayerBlocking()) {
                                        shouldStartBlocking = true;
                                        this.blockHoldMS += this.blockHoldDurationMillis();
                                    }
                                    this.bufferRestartPending = true;
                                    this.blockTick = 1;
                                    break;
                                case 1:
                                    if (this.blockHoldMS <= 0L) {
                                        if (this.isPlayerBlocking()) {
                                            if (Myau.moduleManager.modules.get(NoSlow.class).isEnabled()) {
                                                java.util.Random random = new java.util.Random();
                                                int randomSlot = random.nextInt(9);
                                                while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                                    randomSlot = random.nextInt(9);
                                                }
                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                            }
                                            this.stopBlock();
                                            canAttackThisTick = false;
                                        }
                                        this.blockReleaseDelayMS += this.blockReleaseDurationMillis();
                                        this.blockTick = 0;
                                    } else {
                                        this.bufferRestartPending = true;
                                    }
                                    break;
                                default:
                                    this.blockTick = 0;
                            }
                        }
                        break;
                    case AUTO_BLOCK_BLINK:
                        this.autoBlockActive = true;
                        this.fakeBlockState = true;
                        if (!Myau.packetActionTracker.sentDigging && !Myau.packetActionTracker.sentBlockPlacement) {
                            switch (this.blockTick) {
                                case 0:
                                    if (!this.isPlayerBlocking()) {
                                        shouldStartBlocking = true;
                                        this.blockHoldMS += this.blockHoldDurationMillis();
                                    }
                                    this.bufferRestartPending = true;
                                    this.blockTick = 1;
                                    break;
                                case 1:
                                    if (this.isPlayerBlocking()) {
                                        this.stopBlock();
                                        canAttackThisTick = false;
                                    }
                                    if (this.blockHoldMS <= 0L) {
                                        this.blockReleaseDelayMS += this.blockReleaseDurationMillis();
                                        this.blockTick = 0;
                                    }
                                    break;
                                default:
                                    this.blockTick = 0;
                            }
                        }
                        break;
                    case AUTO_BLOCK_INTERACT:
                        this.autoBlockActive = true;
                        this.fakeBlockState = true;
                        if (mc.thePlayer.inventory.currentItem == controllerSlot
                                && !Myau.packetActionTracker.sentDigging
                                && !Myau.packetActionTracker.sentBlockPlacement) {
                            switch (this.blockTick) {
                                case 0:
                                    if (!this.isPlayerBlocking()) {
                                        shouldStartBlocking = true;
                                        this.blockHoldMS += this.blockHoldDurationMillis();
                                    }
                                    this.bufferRestartPending = true;
                                    this.blockTick = 1;
                                    break;
                                case 1:
                                    if (this.isPlayerBlocking()) {
                                        int slot = this.findEmptySlot(controllerSlot);
                                        PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
                                        ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(slot);
                                        canAttackThisTick = false;
                                    }
                                    if (this.blockHoldMS <= 0L) {
                                        this.blockReleaseDelayMS += this.blockReleaseDurationMillis();
                                        this.blockTick = 0;
                                    }
                                    break;
                                default:
                                    this.blockTick = 0;
                            }
                        }
                        break;
                    case AUTO_BLOCK_SPOOF:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        this.autoBlockActive = true;
                        this.fakeBlockState = false;
                        if (!Myau.packetActionTracker.sentDigging
                                && !Myau.packetActionTracker.sentBlockPlacement
                                && mc.thePlayer.inventory.currentItem == controllerSlot
                                && (!this.isPlayerBlocking() || this.blockHoldMS <= 0L)) {
                            int slot = this.findEmptySlot(controllerSlot);
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
                            PacketUtil.sendPacket(new C09PacketHeldItemChange(controllerSlot));
                            shouldStartBlocking = true;
                            this.blockHoldMS += this.blockHoldDurationMillis();
                        }
                        break;
                    case AUTO_BLOCK_SWAP:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        this.autoBlockActive = true;
                        this.fakeBlockState = true;
                        if (mc.thePlayer.inventory.currentItem == controllerSlot
                                && !Myau.packetActionTracker.sentDigging
                                && !Myau.packetActionTracker.sentBlockPlacement) {
                            switch (this.blockTick) {
                                case 0:
                                    if (!this.isPlayerBlocking()) {
                                        shouldStartBlocking = true;
                                        this.blockHoldMS += this.blockHoldDurationMillis();
                                    }
                                    this.blockTick = 1;
                                    break;
                                case 1:
                                    if (this.blockHoldMS <= 0L) {
                                        if (this.isPlayerBlocking()) {
                                            int swordSlot = this.findSwordSlot(controllerSlot);
                                            if (swordSlot != -1) {
                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(swordSlot));
                                                ((IAccessorPlayerControllerMP) mc.playerController).setCurrentPlayerItem(swordSlot);
                                                this.startBlock(mc.thePlayer.inventory.getStackInSlot(swordSlot));
                                            } else {
                                                this.stopBlock();
                                            }
                                            canAttackThisTick = false;
                                        }
                                        this.blockReleaseDelayMS += this.blockReleaseDurationMillis();
                                        this.blockTick = 0;
                                    }
                                    break;
                                default:
                                    this.blockTick = 0;
                            }
                        }
                        break;
                    case AUTO_BLOCK_LEGIT:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        this.autoBlockActive = true;
                        this.fakeBlockState = true;
                        if (!Myau.packetActionTracker.sentDigging && !Myau.packetActionTracker.sentBlockPlacement) {
                            switch (this.blockTick) {
                                case 0:
                                    if (!this.isPlayerBlocking()) {
                                        shouldStartBlocking = true;
                                        this.blockHoldMS += this.blockHoldDurationMillis();
                                    }
                                    this.blockTick = 1;
                                    break;
                                case 1:
                                    if (this.blockHoldMS <= 0L) {
                                        if (this.isPlayerBlocking()) {
                                            this.stopBlock();
                                            canAttackThisTick = false;
                                        }
                                        this.blockReleaseDelayMS += this.blockReleaseDurationMillis();
                                        this.blockTick = 0;
                                    }
                                    break;
                                default:
                                    this.blockTick = 0;
                            }
                        }
                        break;
                    case AUTO_BLOCK_FAKE:
                        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                        this.autoBlockActive = false;
                        this.fakeBlockState = true;
                        if (PlayerUtil.isUseItemKeyDown()
                                && !this.isPlayerBlocking()
                                && !Myau.packetActionTracker.sentDigging
                                && !Myau.packetActionTracker.sentBlockPlacement) {
                            shouldStartBlocking = true;
                        }
                        break;
                    default:
                        break;
                }
            }

            boolean attacked = false;
            if (this.target.hasUsableAim() && this.isWithinSwingRange(this.target.getDistance())) {
                int rotationMode = this.rotations.getValue();
                if (rotationMode == 2 || rotationMode == 3) {
                    float[] rotation = this.calculateCombatRotation(event.getNewYaw(), event.getNewPitch());
                    event.setRotation(rotation[0], rotation[1], 1);
                    if (rotationMode == 3) {
                        Myau.rotationManager.setRotation(rotation[0], rotation[1], 1, true);
                    }
                    if (this.moveFix.getValue() != 0 || rotationMode == 3) {
                        event.setPervRotation(rotation[0], 1);
                    }
                }
                if (canAttackThisTick) {
                    attacked = this.tryAttackTarget(event.getNewYaw(), event.getNewPitch());
                }
            }

            if (shouldStartBlocking) {
                if (attacked) {
                    this.interactAttack(event.getNewYaw(), event.getNewPitch());
                } else {
                    this.sendUseItem();
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    ArrayList<TargetSnapshot> candidates = this.collectTargetCandidates();
                    if (candidates.isEmpty()) {
                        this.target = null;
                        this.hasTargetInAutoBlockRange = false;
                        break;
                    }
                    this.hasTargetInAutoBlockRange = candidates.stream().anyMatch(this::isCandidateWithinAutoBlockRange);
                    if (candidates.stream().anyMatch(TargetSnapshot::hasUsableAim)) {
                        candidates.removeIf(snapshot -> !snapshot.hasUsableAim());
                    }
                    if (candidates.stream().anyMatch(this::isCandidateWithinSwingRange)) {
                        candidates.removeIf(this::shouldDiscardOutsideSwingRange);
                    }
                    if (candidates.stream().anyMatch(this::isCandidateWithinAttackRange)) {
                        candidates.removeIf(this::shouldDiscardOutsideAttackRange);
                    }
                    if (candidates.stream().anyMatch(this::isEnemyCandidate)) {
                        candidates.removeIf(this::shouldDiscardNonEnemyCandidate);
                    }
                    if (candidates.isEmpty()) {
                        this.target = null;
                        break;
                    }
                    if (this.target != null && !this.timer.hasTimeElapsed(this.switchDelay.getValue().longValue())) {
                        Optional<TargetSnapshot> existing = candidates.stream().filter(this::matchesCurrentTarget).findFirst();
                        if (existing.isPresent()) {
                            this.target = existing.get();
                            break;
                        }
                    }
                    candidates.sort(this::compareTargetCandidates);
                    if (this.mode.getValue() == 1 && this.hitRegistered) {
                        this.hitRegistered = false;
                        this.switchTick++;
                    }
                    if (this.mode.getValue() == 0 || this.switchTick >= candidates.size()) {
                        this.switchTick = 0;
                    }
                    this.target = candidates.get(this.switchTick);
                    this.timer.reset();
                    break;
                case POST:
                    if (this.isPlayerBlocking() && !mc.thePlayer.isBlocking()) {
                        ItemStack heldItem = mc.thePlayer.getHeldItem();
                        if (heldItem != null) {
                            mc.thePlayer.setItemInUse(heldItem, heldItem.getMaxItemUseDuration());
                        }
                    }
            }
        }
    }

    /** Collects valid combat targets in the same filter order used by target selection. */
    private ArrayList<TargetSnapshot> collectTargetCandidates() {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        ArrayList<TargetSnapshot> candidates = new ArrayList<>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!this.isValidTarget(living)) {
                continue;
            }
            double borderSize = living.getCollisionBorderSize();
            AxisAlignedBB box = living.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
            double distance = RotationUtil.clampVecToBox(box, eyes);
            if (!this.isWithinAnyCombatRange(distance)) {
                continue;
            }
            float angularDistance = RotationUtil.boxAimErrorFrom(box, eyes);
            if (angularDistance > (float) this.fov.getValue()) {
                continue;
            }
            float[] aim = RotationUtil.aimAtBox(box);
            float yaw = aim[0];
            float pitch = aim[1];
            boolean hasUsableAim = true;
            if (!this.throughWalls.getValue()
                    && RotationUtil.isBoxRayObstructed(box, eyes, yaw, pitch, 8.0)) {
                if (RotationUtil.isBoxRayObstructed(box, eyes, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, 8.0)) {
                    hasUsableAim = false;
                } else {
                    yaw = mc.thePlayer.rotationYaw;
                    pitch = mc.thePlayer.rotationPitch;
                }
            }
            candidates.add(
                    new TargetSnapshot(
                            living, box, living.posX, living.posY, living.posZ, yaw, pitch, distance, angularDistance, hasUsableAim
                    )
            );
        }
        return candidates;
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled() && mc.thePlayer != null && mc.theWorld != null) {
            if (event.getPacket() instanceof C07PacketPlayerDigging) {
                C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
                if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                    this.blockingState = false;
                }
            }
            if (event.getPacket() instanceof C09PacketHeldItemChange) {
                this.blockingState = false;
                if (this.autoBlockActive) {
                    mc.thePlayer.stopUsingItem();
                }
            }
            if (this.debugLog.getValue() == 1 && this.isAttackAllowed()) {
                if (event.getPacket() instanceof S06PacketUpdateHealth) {
                    this.logHealthDelta(((S06PacketUpdateHealth) event.getPacket()).getHealth() - mc.thePlayer.getHealth());
                }
                if (event.getPacket() instanceof S1CPacketEntityMetadata) {
                    S1CPacketEntityMetadata packet = (S1CPacketEntityMetadata) event.getPacket();
                    if (packet.getEntityId() == mc.thePlayer.getEntityId()) {
                        for (WatchableObject watchableObject : packet.func_149376_c()) {
                            if (watchableObject.getDataValueId() == 6) {
                                this.logHealthDelta((Float) watchableObject.getObject() - mc.thePlayer.getHealth());
                            }
                        }
                    }
                }
            }
        }
    }

    private void logHealthDelta(float delta) {
        if (delta == 0.0F || this.lastTickProcessed == mc.thePlayer.ticksExisted) {
            return;
        }
        this.lastTickProcessed = mc.thePlayer.ticksExisted;
        ChatUtil.sendFormatted(
                String.format(
                        "%sHealth: %s&l%s&r (&otick: %d&r)&r",
                        Myau.clientName,
                        delta > 0.0F ? "&a" : "&c",
                        df.format(delta),
                        mc.thePlayer.ticksExisted
                )
        );
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.moveFix.getValue() == 1
                    && this.rotations.getValue() != 3
                    && RotationState.isActived()
                    && RotationState.getPriority() == 1.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled() && this.target != null) {
            if (this.showTarget.getValue() != 0
                    && TeamUtil.isEntityLoaded(this.target.getEntity())
                    && this.isAttackAllowed()) {
                Color color = new Color(-1);
                switch (this.showTarget.getValue()) {
                    case 1:
                        if (this.target.getEntity().hurtTime > 0) {
                            color = new Color(16733525);
                        } else {
                            color = new Color(5635925);
                        }
                        break;
                    case 2:
                        color = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
                }
                RenderUtil.enableRenderState();
                RenderUtil.drawEntityBox(this.target.getEntity(), color.getRed(), color.getGreen(), color.getBlue());
                RenderUtil.disableRenderState();
            }
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.autoBlockActive) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled()
                    && this.target != null
                    && this.canRunCombat()
                    && this.isWithinSwingRange(this.target.getDistance())) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.autoBlockActive) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled()
                    && this.target != null
                    && this.canRunCombat()
                    && this.isWithinSwingRange(this.target.getDistance())) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.autoBlockActive) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled()
                    && this.target != null
                    && this.canRunCombat()
                    && this.isWithinSwingRange(this.target.getDistance())) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onCancelUse(CancelUseEvent event) {
        if (this.autoBlockActive) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        this.target = null;
        this.hasTargetInAutoBlockRange = false;
        this.switchTick = 0;
        this.hitRegistered = false;
        this.attackDelayMS = 0L;
        this.blockHoldMS = 0L;
        this.blockReleaseDelayMS = 0L;
        this.blockTick = 0;
    }

    @Override
    public void onDisabled() {
        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        if (this.blockingState) {
            mc.thePlayer.stopUsingItem();
        }
        this.target = null;
        this.hasTargetInAutoBlockRange = false;
        this.bufferRestartPending = false;
        this.blockingState = false;
        this.autoBlockActive = false;
        this.fakeBlockState = false;
        this.blockTick = 0;
        this.blockHoldMS = 0L;
        this.blockReleaseDelayMS = 0L;
    }

    @Override
    public void verifyValue(String value) {
        if (this.swingRange.getName().equals(value)) {
            if (this.swingRange.getValue() < this.attackRange.getValue()) {
                this.attackRange.setValue(this.swingRange.getValue());
            }
        } else if (this.attackRange.getName().equals(value)) {
            if (this.swingRange.getValue() < this.attackRange.getValue()) {
                this.swingRange.setValue(this.attackRange.getValue());
            }
        } else if (this.minCPS.getName().equals(value)) {
            if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                this.maxCPS.setValue(this.minCPS.getValue());
            }
        } else if (this.maxCPS.getName().equals(value)) {
            if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                this.minCPS.setValue(this.maxCPS.getValue());
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }

    private int compareTargetCandidates(TargetSnapshot first, TargetSnapshot second) {
        int sortBase;
        switch (this.sort.getValue()) {
            case 1:
                sortBase = Float.compare(TeamUtil.getHealthScore(first.getEntity()), TeamUtil.getHealthScore(second.getEntity()));
                break;
            case 2:
                sortBase = Integer.compare(first.getEntity().hurtResistantTime, second.getEntity().hurtResistantTime);
                break;
            case 3:
                sortBase = Float.compare(first.getAngularDistance(), second.getAngularDistance());
                break;
            default:
                sortBase = Double.compare(first.getDistance(), second.getDistance());
        }
        return sortBase != 0 ? sortBase : Double.compare(first.getDistance(), second.getDistance());
    }

    private boolean matchesCurrentTarget(TargetSnapshot candidate) {
        return this.target != null && candidate.getEntity() == this.target.getEntity();
    }

    private boolean isEnemyCandidate(TargetSnapshot candidate) {
        return this.isEnemyPlayer(candidate.getEntity());
    }

    private boolean shouldDiscardNonEnemyCandidate(TargetSnapshot candidate) {
        return !this.isEnemyPlayer(candidate.getEntity());
    }

    private boolean isCandidateWithinSwingRange(TargetSnapshot candidate) {
        return this.isWithinSwingRange(candidate.getDistance());
    }

    private boolean shouldDiscardOutsideSwingRange(TargetSnapshot candidate) {
        return !this.isWithinSwingRange(candidate.getDistance());
    }

    private boolean isCandidateWithinAttackRange(TargetSnapshot candidate) {
        return this.isWithinAttackRange(candidate.getDistance());
    }

    private boolean shouldDiscardOutsideAttackRange(TargetSnapshot candidate) {
        return !this.isWithinAttackRange(candidate.getDistance());
    }

    private boolean isCandidateWithinAutoBlockRange(TargetSnapshot candidate) {
        return this.isWithinAutoBlockRange(candidate.getDistance());
    }

    private boolean isAutoBlockRangeVisible() {
        return this.autoBlock.getValue() != AUTO_BLOCK_NONE;
    }

    private boolean isAutoBlockHurtTimeVisible() {
        int mode = this.autoBlock.getValue();
        return mode != AUTO_BLOCK_NONE && mode != AUTO_BLOCK_FAKE;
    }

    private boolean isAutoBlockDelayVisible() {
        int mode = this.autoBlock.getValue();
        return mode != AUTO_BLOCK_NONE && mode != AUTO_BLOCK_VANILLA && mode != AUTO_BLOCK_FAKE;
    }

    private boolean isAutoBlockHoldVisible() {
        int mode = this.autoBlock.getValue();
        return mode != AUTO_BLOCK_NONE && mode != AUTO_BLOCK_FAKE;
    }

    private boolean isAutoBlockNoSlowVisible() {
        return this.autoBlock.getValue() != AUTO_BLOCK_NONE;
    }

    private boolean isAutoBlockRequirePressVisible() {
        return this.autoBlock.getValue() != AUTO_BLOCK_NONE;
    }

    private static class TargetSnapshot {
        private final EntityLivingBase entity;
        private final AxisAlignedBB box;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final double distance;
        private final float angularDistance;
        private final boolean hasUsableAim;

        public TargetSnapshot(
                EntityLivingBase entityLivingBase,
                AxisAlignedBB axisAlignedBB,
                double d,
                double e,
                double f,
                float g,
                float h,
                double i,
                float j,
                boolean bl
        ) {
            this.entity = entityLivingBase;
            this.box = axisAlignedBB;
            this.x = d;
            this.y = e;
            this.z = f;
            this.yaw = g;
            this.pitch = h;
            this.distance = i;
            this.angularDistance = j;
            this.hasUsableAim = bl;
        }

        public EntityLivingBase getEntity() {
            return this.entity;
        }

        public AxisAlignedBB getBox() {
            return this.box;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }

        public float getYaw() {
            return this.yaw;
        }

        public float getPitch() {
            return this.pitch;
        }

        public double getDistance() {
            return this.distance;
        }

        public float getAngularDistance() {
            return this.angularDistance;
        }

        public boolean hasUsableAim() {
            return this.hasUsableAim;
        }
    }
}
