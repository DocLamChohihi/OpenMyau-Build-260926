package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.StrafeEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorEntityPlayerSP;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Client-side game-speed changer.
 *
 * <p>Ported from the Myau-250910 {@code TimerModule}. VANILLA simply multiplies the
 * client tick timer. HYPIXEL additionally withholds movement packets and zeroes
 * movement input until enough movement has been buffered, which keeps the server's
 * position checks satisfied while the client runs ahead.
 */
public class Timer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat speedFormat = new DecimalFormat("0.0#", new DecimalFormatSymbols(Locale.US));

    private boolean timerSpeedOwned;
    private boolean allowNextMovementPacket;
    private boolean movementInputCleared;
    private boolean motionStored;
    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;
    private int bufferedMovementPackets;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"VANILLA", "HYPIXEL"});
    public final FloatProperty speed = new FloatProperty("speed", 1.0F, 0.0F, 10.0F, () -> this.mode.getValue() == 0);

    public Timer() {
        super("Timer", false);
    }

    /** True once enough movement has been buffered for the server to accept a step. */
    private boolean readyToMove() {
        return this.bufferedMovementPackets >= 10
                || ((IAccessorEntityPlayerSP) Timer.mc.thePlayer).getPositionUpdateTicks() >= 20;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.POST) {
            return;
        }
        if (!this.isEnabled() || this.mode.getValue() != 0) {
            return;
        }
        if (Timer.mc.currentScreen != null) {
            this.onDisabled();
            return;
        }
        NoFall noFall = (NoFall) myau.Myau.moduleManager.modules.get(NoFall.class);
        if (noFall.isPacketModeActive()) {
            this.timerSpeedOwned = false;
            return;
        }
        this.timerSpeedOwned = true;
        ((IAccessorMinecraft) Timer.mc).getTimer().timerSpeed = this.speed.getValue();
    }

    private void clearMovementInput() {
        Timer.mc.thePlayer.movementInput.moveStrafe = 0.0F;
        Timer.mc.thePlayer.movementInput.moveForward = 0.0F;
        Timer.mc.thePlayer.movementInput.jump = false;
        Timer.mc.thePlayer.movementInput.sneak = false;
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled() && this.mode.getValue() == 1 && !this.readyToMove()) {
            this.movementInputCleared = true;
            this.clearMovementInput();
            return;
        }
        if (this.movementInputCleared) {
            this.movementInputCleared = false;
            this.clearMovementInput();
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled() && this.mode.getValue() == 1 && !this.readyToMove()) {
            if (!this.motionStored) {
                this.motionStored = true;
                this.savedMotionX = Timer.mc.thePlayer.motionX;
                this.savedMotionY = Timer.mc.thePlayer.motionY;
                this.savedMotionZ = Timer.mc.thePlayer.motionZ;
            }
            Timer.mc.thePlayer.motionX = 0.0;
            if (!Timer.mc.thePlayer.onGround) {
                Timer.mc.thePlayer.motionY = 0.0;
            }
            Timer.mc.thePlayer.motionZ = 0.0;
            return;
        }
        if (this.motionStored) {
            this.motionStored = false;
            Timer.mc.thePlayer.motionX = this.savedMotionX;
            Timer.mc.thePlayer.motionY = this.savedMotionY;
            Timer.mc.thePlayer.motionZ = this.savedMotionZ;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            return;
        }
        if (event.getType() == EventType.RECEIVE) {
            if (this.mode.getValue() == 1 && event.getPacket() instanceof S08PacketPlayerPosLook) {
                this.allowNextMovementPacket = true;
                this.motionStored = false;
            }
            return;
        }
        if (event.getType() != EventType.SEND || !(event.getPacket() instanceof C03PacketPlayer)) {
            return;
        }
        if (this.allowNextMovementPacket) {
            this.allowNextMovementPacket = false;
            return;
        }
        if (this.mode.getValue() == 1 && !this.readyToMove()) {
            this.bufferedMovementPackets++;
            if (!(event.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook)) {
                event.setCancelled(true);
            }
            return;
        }
        this.bufferedMovementPackets = 0;
    }

    @Override
    public void onDisabled() {
        if (this.timerSpeedOwned) {
            this.timerSpeedOwned = false;
            ((IAccessorMinecraft) Timer.mc).getTimer().timerSpeed = 1.0F;
        }
        this.bufferedMovementPackets = 0;
    }

    @Override
    public void verifyValue(String name) {
        if (this.isEnabled()) {
            this.onDisabled();
        }
    }

    @Override
    public String[] getSuffix() {
        switch (this.mode.getValue()) {
            case 0:
                return new String[]{Timer.speedFormat.format(this.speed.getValue())};
            case 1:
                return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
            default:
                return new String[0];
        }
    }
}
