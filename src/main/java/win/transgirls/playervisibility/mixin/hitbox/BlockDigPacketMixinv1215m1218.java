package win.transgirls.playervisibility.mixin.hitbox;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import win.transgirls.crossfabric.annotation.VersionedMixin;
import win.transgirls.playervisibility.config.ModConfig;
import win.transgirls.playervisibility.util.MiningLock;

@Mixin(PlayerActionC2SPacket.class)
@VersionedMixin({">=1.21.5", "<=1.21.8"})
public class BlockDigPacketMixinv1215m1218 {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void playervisibility$rewriteDigTarget(
            PlayerActionC2SPacket.Action action,
            BlockPos pos,
            net.minecraft.util.math.Direction direction,
            CallbackInfo ci
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        // 🔴 STOP / ABORT → clear mining lock
        if (action == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
                || action == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
            MiningLock.clear();
            return;
        }

        // Only rewrite START_DESTROY_BLOCK when hiding players
        if (!ModConfig.hidePlayers) return;
        if (action != PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) return;

        Vec3d start = client.player.getCameraPosVec(1.0f);
        Vec3d end = start.add(client.player.getRotationVec(1.0f).multiply(5.0));

        BlockHitResult bhr = client.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));

        if (bhr.getType() == BlockHitResult.Type.BLOCK) {
            // ✅ Rewrite packet target (Cosmic-style)
            ((PlayerActionC2SPacketAccessor) this).setPos(bhr.getBlockPos());
            ((PlayerActionC2SPacketAccessor) this).setDirection(bhr.getSide());

            // 🔒 LOCK mining so it doesn't cancel when someone walks in front
            MiningLock.active = true;
            MiningLock.lockedPos = bhr.getBlockPos();
            MiningLock.lockedSide = bhr.getSide();
        }
    }
}
