package net.minecraft.util;

/** Minimal test stub of vanilla's math helper — only the methods the tested code touches. */
public final class Mth {
    private Mth() {}

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public static long clamp(long value, long min, long max) {
        return Math.min(Math.max(value, min), max);
    }

    public static float clamp(float value, float min, float max) {
        return Math.min(Math.max(value, min), max);
    }

    public static double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    public static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static int ceil(double value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }
}
