package rusticpipes.block;

import net.minecraft.item.EnumDyeColor;

/**
 * The 16 pipe colour variants, ordered to match {@link EnumDyeColor} ordinals
 * so that {@code PipeColor.values()[EnumDyeColor.ordinal()]} always works.
 */
public enum PipeColor {

    WHITE      ("white_pipe",       "White Pipe",       0xF9FFFE),
    ORANGE     ("orange_pipe",      "Orange Pipe",      0xF9801D),
    MAGENTA    ("magenta_pipe",     "Magenta Pipe",     0xC74EBD),
    LIGHT_BLUE ("light_blue_pipe",  "Light Blue Pipe",  0x3AB3DA),
    YELLOW     ("yellow_pipe",      "Yellow Pipe",      0xFED83D),
    LIME       ("lime_pipe",        "Lime Pipe",        0x80C71F),
    PINK       ("pink_pipe",        "Pink Pipe",        0xF38BAA),
    GRAY       ("gray_pipe",        "Gray Pipe",        0x474F52),
    LIGHT_GRAY ("light_gray_pipe",  "Light Gray Pipe",  0x9D9D97),
    CYAN       ("cyan_pipe",        "Cyan Pipe",        0x169C9C),
    PURPLE     ("purple_pipe",      "Purple Pipe",      0x8932B8),
    BLUE       ("blue_pipe",        "Blue Pipe",        0x3C44AA),
    BROWN      ("brown_pipe",       "Brown Pipe",       0x835432),
    GREEN      ("green_pipe",       "Green Pipe",       0x5E7C16),
    RED        ("red_pipe",         "Red Pipe",         0xB02E26),
    BLACK      ("black_pipe",       "Black Pipe",       0x1D1D21);

    /** Registry / resource-pack name, e.g. {@code "white_pipe"}. */
    public final String registryName;
    /** Human-readable display name for the lang file. */
    public final String displayName;
    /** 0xRRGGBB tint applied to the pipe body texture. */
    public final int tintColor;

    PipeColor(String registryName, String displayName, int tintColor) {
        this.registryName = registryName;
        this.displayName  = displayName;
        this.tintColor    = tintColor;
    }

    /** Returns the {@link PipeColor} that corresponds to the given dye color. */
    public static PipeColor fromDye(EnumDyeColor dye) {
        return values()[dye.ordinal()];
    }
}
