package myau.mixin;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes Forge's {@code positionUpdateTicks} counter on the client player.
 *
 * <p>Ported from the Myau-250910 rewrite, where Timer's HYPIXEL mode uses the tick count
 * as a fallback readiness check alongside its own buffered-packet counter.
 */
@SideOnly(Side.CLIENT)
@Mixin({EntityPlayerSP.class})
public interface IAccessorEntityPlayerSP {
    @Accessor("positionUpdateTicks")
    int getPositionUpdateTicks();
}
