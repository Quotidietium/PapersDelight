package dev.tako.papersdelight.mechanic.stove;

import java.util.List;

final class StoveBurnArea {

    private static final int PIXELS_PER_BLOCK = 16;

    private static final int DEFAULT_MIN_Y_PIXEL = 0;
    private static final int DEFAULT_MAX_Y_PIXEL = 1;

    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    private StoveBurnArea(double minX, double minY, double minZ,
                          double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    static StoveBurnArea fromPixels(List<?> pixels) {
        if (pixels == null || (pixels.size() != 4 && pixels.size() != 6)) {
            throw new IllegalArgumentException(
                    dev.tako.papersdelight.config.ConfigManager.getOr(
                            "stove_burn_area_length_invalid", "burn_area 需 4 个像素值 [minX, minZ, maxX, maxZ] 或 6 个 [minX, minY, minZ, maxX, maxY, maxZ]，实际: %pixels%")
                            .replace("%pixels%", String.valueOf(pixels)));
        }
        int minX;
        int minY;
        int minZ;
        int maxX;
        int maxY;
        int maxZ;
        if (pixels.size() == 6) {
            minX = toPixel(pixels.get(0));
            minY = toPixel(pixels.get(1));
            minZ = toPixel(pixels.get(2));
            maxX = toPixel(pixels.get(3));
            maxY = toPixel(pixels.get(4));
            maxZ = toPixel(pixels.get(5));
        } else {
            minX = toPixel(pixels.get(0));
            minZ = toPixel(pixels.get(1));
            maxX = toPixel(pixels.get(2));
            maxZ = toPixel(pixels.get(3));
            minY = DEFAULT_MIN_Y_PIXEL;
            maxY = DEFAULT_MAX_Y_PIXEL;
        }
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            throw new IllegalArgumentException(
                    dev.tako.papersdelight.config.ConfigManager.getOr(
                            "stove_burn_area_bounds_invalid", "burn_area 需满足每轴 min<max，实际: %pixels%")
                            .replace("%pixels%", String.valueOf(pixels)));
        }
        return new StoveBurnArea(
                toRatio(minX), toRatio(minY), toRatio(minZ),
                toRatio(maxX), toRatio(maxY), toRatio(maxZ));
    }

    private static double toRatio(int pixel) {
        return pixel / (double) PIXELS_PER_BLOCK;
    }

    private static int toPixel(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(dev.tako.papersdelight.config.ConfigManager.getOr(
                    "stove_burn_area_element_not_number", "burn_area 元素必须是数字，实际: %value%").replace("%value%", String.valueOf(value)));
        }
        int pixel = number.intValue();
        if (pixel < 0 || pixel > PIXELS_PER_BLOCK) {
            throw new IllegalArgumentException(dev.tako.papersdelight.config.ConfigManager.getOr(
                    "stove_burn_area_pixel_out_of_range", "burn_area 像素值需在 [0,16] 内，实际: %pixel%").replace("%pixel%", String.valueOf(pixel)));
        }
        return pixel;
    }

    boolean intersects(double entityX, double entityY, double entityZ,
                       double entityWidth, double entityHeight,
                       int blockX, int blockY, int blockZ) {
        double half = entityWidth / 2.0;
        double eMinX = entityX - half;
        double eMaxX = entityX + half;
        double eMinZ = entityZ - half;
        double eMaxZ = entityZ + half;
        double eMinY = entityY;
        double eMaxY = entityY + entityHeight;

        int originY = blockY + 1;
        double aMinX = blockX + minX;
        double aMaxX = blockX + maxX;
        double aMinY = originY + minY;
        double aMaxY = originY + maxY;
        double aMinZ = blockZ + minZ;
        double aMaxZ = blockZ + maxZ;

        return eMinX < aMaxX && eMaxX > aMinX
                && eMinY < aMaxY && eMaxY > aMinY
                && eMinZ < aMaxZ && eMaxZ > aMinZ;
    }
}
