package win.transgirls.playervisibility.mixin.hitbox;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.transgirls.crossfabric.annotation.VersionedMixin;
import win.transgirls.playervisibility.PlayerVisibility;
import win.transgirls.playervisibility.config.ModConfig;
import win.transgirls.playervisibility.util.MiningLock;

@Mixin(GameRenderer.class)
@VersionedMixin({">=1.21.5", "<=1.21.8"})
public class HitboxRaycastMixinv1215m1218 {

    /**
     * Check if we should ignore an entity hit result based on visibility settings
     */
    private static boolean shouldIgnoreEntity(MinecraftClient client) {
        if (!ModConfig.hidePlayers || !ModConfig.hideHitboxes) return false;
        if (client.crosshairTarget == null) return false;
        if (client.crosshairTarget.getType() != HitResult.Type.ENTITY) return false;

        EntityHitResult ehr = (EntityHitResult) client.crosshairTarget;
        return PlayerVisibility.computeHideInfoForEntityOrState(ehr.getEntity()).hide;
    }

    /**
     * Replace ENTITY raycasts with BLOCK raycasts ONLY when needed
     * (fixes block outline without breaking vanilla behavior)
     */
    @Inject(method = "updateTargetedEntity", at = @At("TAIL"))
    private void playervisibility$fixBlockOutline(float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (client.crosshairTarget == null) return;
        
        // Only override when a hidden entity is blocking the ray
        if (!shouldIgnoreEntity(client)) return;

        BlockHitResult bhr = blockOnlyRaycast(client, tickDelta);
        if (bhr != null) {
            client.crosshairTarget = bhr;
        }
    }

    /**
     * Lock mining target + chain blocks while LEFT CLICK is held
     */
    @Inject(method = "updateTargetedEntity", at = @At("TAIL"))
    private void playervisibility$lockAndChainMining(float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (client.interactionManager == null) return;

        boolean attackHeld = client.options.attackKey.isPressed();

        // Establish lock when mining starts
        if (!MiningLock.active
                && client.interactionManager.isBreakingBlock()
                && client.crosshairTarget instanceof BlockHitResult bhr) {

            MiningLock.active = true;
            MiningLock.lockedPos = bhr.getBlockPos();
            MiningLock.lockedSide = bhr.getSide();
        }

        // While mining is locked, update visual target BUT keep mining on locked block
        if (MiningLock.active && client.interactionManager.isBreakingBlock()) {
            // Update visual outline dynamically based on camera direction
            BlockHitResult visualTarget = blockOnlyRaycast(client, tickDelta);
            if (visualTarget != null) {
                client.crosshairTarget = visualTarget;
            } else {
                // Fallback to locked target if no block in view
                client.crosshairTarget = new BlockHitResult(
                        Vec3d.ofCenter(MiningLock.lockedPos),
                        MiningLock.lockedSide,
                        MiningLock.lockedPos,
                        false
                );
            }
            return;
        }

        // Chain to next block if left click still held
        if (MiningLock.active && !client.interactionManager.isBreakingBlock() && attackHeld) {
            BlockHitResult next = blockOnlyRaycast(client, tickDelta);
            if (next != null && !next.getBlockPos().equals(MiningLock.lockedPos)) {
                MiningLock.lockedPos = next.getBlockPos();
                MiningLock.lockedSide = next.getSide();

                client.interactionManager.attackBlock(
                        MiningLock.lockedPos,
                        MiningLock.lockedSide
                );
            }
        }

        // Clear lock when left click released
        if (!attackHeld) {
            MiningLock.clear();
        }
    }

    // --- helper ---
    private static BlockHitResult blockOnlyRaycast(MinecraftClient client, float tickDelta) {
        Vec3d start = client.player.getCameraPosVec(tickDelta);
        Vec3d end = start.add(client.player.getRotationVec(tickDelta).multiply(5.0));

        BlockHitResult bhr = client.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));

        return bhr.getType() == HitResult.Type.BLOCK ? bhr : null;
    }
}
