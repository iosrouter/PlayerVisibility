package win.transgirls.playervisibility.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class MiningLock {
    public static boolean active = false;
    public static BlockPos lockedPos = null;
    public static Direction lockedSide = null;
    public static long lastBreakTick = 0;


    public static void clear() {
        active = false;
        lockedPos = null;
        lockedSide = null;
    }
}
