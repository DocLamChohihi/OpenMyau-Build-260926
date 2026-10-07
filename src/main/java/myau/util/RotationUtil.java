package myau.util;

import myau.mixin.IAccessorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class RotationUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static float wrapAngleDiff(float angle, float target) {
        return target + MathHelper.wrapAngleTo180_float(angle - target);
    }

    public static float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0f, Math.min(180.0f, maxAngle));
        if (angle > maxAngle) {
            angle = maxAngle;
        } else if (angle < -maxAngle) {
            angle = -maxAngle;
        }
        return angle;
    }

    public static float smoothAngle(float angle, float smoothFactor) {
        return angle * (0.5f + 0.5f * (1.0f - Math.max(0.0f, Math.min(1.0f, smoothFactor + RandomUtil.nextFloat(-0.1f, 0.1f)))));
    }

    public static float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % (double) 0.0096f);
    }

    public static float[] getRotationsToBox(AxisAlignedBB boundingBox, float yaw, float pitch, float maxAngle, float smoothFactor) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        double minTargetY = boundingBox.minY + 0.05 * (boundingBox.maxY - boundingBox.minY);
        double maxTargetY = boundingBox.minY + 0.75 * (boundingBox.maxY - boundingBox.minY);
        double deltaX = (boundingBox.minX + boundingBox.maxX) / 2.0 - eyePos.xCoord;
        double deltaY = eyePos.yCoord >= maxTargetY ? maxTargetY - eyePos.yCoord : (eyePos.yCoord <= minTargetY ? minTargetY - eyePos.yCoord : 0.0);
        double deltaZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0 - eyePos.zCoord;
        return RotationUtil.getRotations(deltaX, deltaY, deltaZ, yaw, pitch, maxAngle, smoothFactor);
    }

    public static float[] getRotationsTo(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch) {
        return RotationUtil.getRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0f, 0.0f);
    }

    public static float[] getRotations(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        float yawDelta = MathHelper.wrapAngleTo180_float((float) (Math.atan2(targetZ, targetX) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float((float) (-Math.atan2(targetY, horizontalDistance) * 180.0 / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(pitchDelta, maxAngle), smoothFactor);
        return new float[]{RotationUtil.quantizeAngle(currentYaw + yawDelta), RotationUtil.quantizeAngle(currentPitch + pitchDelta)};
    }

    public static Vec3 clampVecToBox(Vec3 vector, AxisAlignedBB boundingBox) {
        double[] coords = new double[]{vector.xCoord, vector.yCoord, vector.zCoord};
        double[] minCoords = new double[]{boundingBox.minX, boundingBox.minY, boundingBox.minZ};
        double[] maxCoords = new double[]{boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ};
        for (int i = 0; i < 3; ++i) {
            if (coords[i] > maxCoords[i]) {
                coords[i] = maxCoords[i];
                continue;
            }
            if (!(coords[i] < minCoords[i])) continue;
            coords[i] = minCoords[i];
        }
        return new Vec3(coords[0], coords[1], coords[2]);
    }

    public static double distanceToEntity(Entity entity) {
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        return RotationUtil.distanceToBox(boundingBox);
    }

    public static double distanceToBox(Entity entity, Vec3 point) {
        float borderSize = entity.getCollisionBorderSize();
        return RotationUtil.clampVecToBox(entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize), point);
    }

    public static double distanceToBox(AxisAlignedBB boundingBox) {
        return RotationUtil.clampVecToBox(boundingBox, RotationUtil.mc.thePlayer.getPositionEyes(1.0f));
    }

    public static double clampVecToBox(AxisAlignedBB boundingBox, Vec3 point) {
        if (boundingBox.isVecInside(point)) {
            return 0.0;
        }
        Vec3 clampedPoint = RotationUtil.clampVecToBox(point, boundingBox);
        double deltaX = clampedPoint.xCoord - point.xCoord;
        double deltaY = clampedPoint.yCoord - point.yCoord;
        double deltaZ = clampedPoint.zCoord - point.zCoord;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    public static float angleToEntity(Entity entity) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        if (boundingBox.isVecInside(eyePos)) {
            return 0.0f;
        }
        double deltaX = entity.posX - eyePos.xCoord;
        double deltaZ = entity.posZ - eyePos.zCoord;
        return Math.abs(MathHelper.wrapAngleTo180_float((float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f - RotationUtil.mc.thePlayer.rotationYaw)) * 2.0f;
    }

    public static float getYawBetween(double x1, double z1, double x2, double z2) {
        return MathHelper.wrapAngleTo180_float((float) (Math.atan2(z2 - z1, x2 - x1) * 180.0 / Math.PI) - 90.0f - RotationUtil.mc.thePlayer.rotationYaw);
    }

    public static MovingObjectPosition rayTrace(float yaw, float pitch, double distance, float partialTicks) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(partialTicks);
        Vec3 lookVec = ((IAccessorEntity) RotationUtil.mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        Vec3 targetPos = eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        return RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, targetPos);
    }

    public static MovingObjectPosition rayTrace(Entity entity) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        Vec3 targetPos = RotationUtil.clampVecToBox(eyePos, entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize));
        return RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, targetPos);
    }

    public static MovingObjectPosition rayTrace(AxisAlignedBB boundingBox, float yaw, float pitch, double distance) {
        return RotationUtil.boundsRayTrace(boundingBox, RotationUtil.mc.thePlayer.getPositionEyes(1.0f), yaw, pitch, distance);
    }

    // ------------------------------------------------------------------
    // Ported from the Myau-250910 KillAura / AimAssist / Scaffold rewrite.
    // The upstream build renamed these helpers; the math is unchanged.
    // ------------------------------------------------------------------

    /** Divide one whole mouse-sensitivity step for the current game setting. */
    public static float mouseSensitivityIncrement() {
        float sensitivity = RotationUtil.mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        return sensitivity * sensitivity * sensitivity * 8.0f;
    }

    /**
     * Round an angle to the nearest whole mouse-sensitivity step measured from a
     * reference angle. Mirrors the upstream {@code snapToMouseSensitivity}.
     */
    public static float snapToMouseSensitivity(float targetAngle, float referenceAngle) {
        float change = MathHelper.wrapAngleTo180_float(targetAngle - referenceAngle);
        float increment = RotationUtil.mouseSensitivityIncrement() * 0.15f;
        if (increment == 0.0f) {
            return targetAngle;
        }
        double roundedChange = Math.round(change / increment) * increment;
        return referenceAngle + (float) roundedChange;
    }

    /** Convert a relative position into yaw and pitch without any smoothing. */
    public static float[] rotationsFromDelta(double deltaX, double deltaY, double deltaZ) {
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-Math.atan2(deltaY, horizontalDistance) * 180.0 / Math.PI);
        return new float[]{yaw, pitch};
    }

    /** Center point of a bounding box. */
    public static Vec3 boxCenter(AxisAlignedBB boundingBox) {
        return new Vec3(
                (boundingBox.minX + boundingBox.maxX) / 2.0,
                (boundingBox.minY + boundingBox.maxY) / 2.0,
                (boundingBox.minZ + boundingBox.maxZ) / 2.0
        );
    }

    /** Distance from a position to the closest point of an entity's expanded box. */
    public static double distanceToBoxFrom(Entity entity, Vec3 point) {
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        return RotationUtil.clampVecToBox(boundingBox, point);
    }

    /**
     * Aim between the box center and the closest point to the player, biased toward the
     * closest point, then apply a vertical offset that grows as the player looks down.
     */
    public static float[] aimAtBox(AxisAlignedBB boundingBox) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        Vec3 center = RotationUtil.boxCenter(boundingBox);
        Vec3 closest = RotationUtil.clampVecToBox(eyePos, boundingBox);
        double deltaX = center.xCoord + (closest.xCoord - center.xCoord) * 0.875 - eyePos.xCoord;
        double deltaY = closest.yCoord - eyePos.yCoord;
        double deltaZ = center.zCoord + (closest.zCoord - center.zCoord) * 0.875 - eyePos.zCoord;
        float[] rotation = RotationUtil.rotationsFromDelta(deltaX, deltaY, deltaZ);
        double height = center.yCoord - boundingBox.minY;
        if (height != 0.0) {
            rotation[1] = rotation[1] + 10.0f * MathHelper.clamp_float(
                    (float) ((eyePos.yCoord - center.yCoord) / height), -0.5f, 0.5f);
        }
        return rotation;
    }

    /**
     * Twice the larger yaw/pitch error between the current view and the box, or zero
     * when the current sight line already intersects the box.
     */
    public static float boxAimError(AxisAlignedBB boundingBox) {
        return RotationUtil.boxAimErrorFrom(boundingBox, RotationUtil.mc.thePlayer.getPositionEyes(1.0f));
    }

    public static float boxAimErrorFrom(AxisAlignedBB boundingBox, Vec3 eyePos) {
        if (boundingBox.isVecInside(eyePos)) {
            return 0.0f;
        }
        double traceDistance = 30.0
                + (boundingBox.maxX - boundingBox.minX)
                + (boundingBox.maxZ - boundingBox.minZ)
                + (boundingBox.maxY - boundingBox.minY);
        MovingObjectPosition hit = RotationUtil.boundsRayTrace(
                boundingBox, eyePos, RotationUtil.mc.thePlayer.rotationYaw, RotationUtil.mc.thePlayer.rotationPitch, traceDistance);
        if (hit != null) {
            return 0.0f;
        }
        float[] rotation = RotationUtil.aimAtBox(boundingBox);
        float yawError = Math.abs(MathHelper.wrapAngleTo180_float(rotation[0] - RotationUtil.mc.thePlayer.rotationYaw));
        float pitchError = Math.abs(rotation[1] - RotationUtil.mc.thePlayer.rotationPitch);
        return Math.max(yawError * 2.0f, pitchError * 2.0f);
    }

    /** Bounding-box aim error for an entity, including its collision border. */
    public static float entityBoxAimError(Entity entity) {
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        return RotationUtil.boxAimError(boundingBox);
    }

    /** Intersect a bounding box with a yaw/pitch ray cast from the player's eyes. */
    public static MovingObjectPosition boundsRayTrace(AxisAlignedBB boundingBox, float yaw, float pitch, double distance) {
        return RotationUtil.boundsRayTrace(boundingBox, RotationUtil.mc.thePlayer.getPositionEyes(1.0f), yaw, pitch, distance);
    }

    /** Intersect a bounding box with a yaw/pitch ray cast from an arbitrary position. */
    public static MovingObjectPosition boundsRayTrace(AxisAlignedBB boundingBox, Vec3 position, float yaw, float pitch, double distance) {
        Vec3 direction = ((IAccessorEntity) RotationUtil.mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        Vec3 end = position.addVector(direction.xCoord * distance, direction.yCoord * distance, direction.zCoord * distance);
        return boundingBox.calculateIntercept(position, end);
    }

    /**
     * True when a ray either misses the box outright or reaches a block before the box.
     * Used by KillAura/AimAssist to reject aims that cannot actually see the target.
     */
    public static boolean isBoxRayObstructed(AxisAlignedBB boundingBox, Vec3 position, float yaw, float pitch, double distance) {
        if (boundingBox.isVecInside(position)) {
            return false;
        }
        MovingObjectPosition boxHit = RotationUtil.boundsRayTrace(boundingBox, position, yaw, pitch, distance);
        if (boxHit == null) {
            return true;
        }
        MovingObjectPosition blockHit = RotationUtil.rayTraceFrom(position, yaw, pitch, distance);
        if (blockHit == null) {
            return false;
        }
        return position.distanceTo(boxHit.hitVec) > position.distanceTo(blockHit.hitVec);
    }

    /** Trace blocks along a yaw/pitch ray from an arbitrary position. */
    public static MovingObjectPosition rayTraceFrom(Vec3 position, float yaw, float pitch, double distance) {
        Vec3 direction = ((IAccessorEntity) RotationUtil.mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        Vec3 end = position.addVector(direction.xCoord * distance, direction.yCoord * distance, direction.zCoord * distance);
        return RotationUtil.mc.theWorld.rayTraceBlocks(position, end);
    }

    /** Trace blocks to the closest point on an entity. */
    public static MovingObjectPosition rayTraceToEntity(Entity entity) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        Vec3 targetPos = RotationUtil.clampVecToBox(
                eyePos, entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize));
        return RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, targetPos);
    }
}