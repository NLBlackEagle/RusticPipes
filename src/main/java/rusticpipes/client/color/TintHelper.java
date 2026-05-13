package rusticpipes.client.color;

/**
 * Utility for blending a tint color toward white so the underlying
 * texture contributes to the final look.
 */
public final class TintHelper {

    private TintHelper() {}

    /**
     * Blends {@code color} toward white (0xFFFFFF) by {@code strength}.
     *
     * @param color    0xRRGGBB source tint
     * @param strength 0.0 = no tint (pure white), 1.0 = full tint color
     * @return blended 0xRRGGBB value
     */
    public static int attenuate(int color, float strength) {
        int r = (color >> 16) & 0xFF;
        int g = (color >>  8) & 0xFF;
        int b =  color        & 0xFF;
        r = (int)(r * strength + 255 * (1f - strength));
        g = (int)(g * strength + 255 * (1f - strength));
        b = (int)(b * strength + 255 * (1f - strength));
        return (r << 16) | (g << 8) | b;
    }
}
