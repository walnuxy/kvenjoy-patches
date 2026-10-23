package com.kenjoy.uitrial;
import java.awt.Color;

/**
 * 0.018 cool-neutral ladder, authored in OKLCH (hue 262, chroma <= 0.016 for neutrals) and stored
 * as sRGB screen tokens. Reference values live in {@link Ref}; components consume semantic roles.
 * Elevation: WELL L.193 (recessed input) -> SURFACE L.215 -> CANVAS L.245 -> PANEL L.265 -> BUTTON L.278 -> INPUT L.302 -> HOVER L.342.
 */
public final class UiPalette {
    private UiPalette(){}
    private static final class Ref {
        static final Color L193=new Color(0x121518), L215=new Color(0x171A1D), L245=new Color(0x1E2125), L265=new Color(0x23252A),
            L278=new Color(0x252830), L302=new Color(0x2B2E35), L308=new Color(0x2D323B), L318=new Color(0x2F3239),
            L335=new Color(0x33373D), L335_GUIDE=new Color(0x33373E), L342=new Color(0x343940), L343_EDGE=new Color(0x343942),
            L405=new Color(0x444952), L455=new Color(0x525760),
            L575=new Color(0x757980), L745=new Color(0xA8ADB5), L835=new Color(0xC5C9D1), L875=new Color(0xD3D6DB),
            STEEL_BLUE_735=new Color(0x84ADDB), STEEL_BLUE_558=new Color(0x4C78A8),
            STEEL_BLUE_388=new Color(0x2E4661), STEEL_BLUE_362=new Color(0x2D3F55), STEEL_BLUE_338=new Color(0x25384E);
    }
    // Surfaces (recessed well -> flat surface -> canvas -> panel -> button -> hover)
    public static final Color WELL=Ref.L193, SURFACE=Ref.L215, CANVAS=Ref.L245, PANEL=Ref.L265, BUTTON=Ref.L278, INPUT=Ref.L302, HOVER=Ref.L342;
    // Lines: WELL_EDGE = recessed input outline, BUTTON_EDGE = quiet button outline, HAIRLINE = section divider
    public static final Color WELL_EDGE=Ref.L308, BUTTON_EDGE=Ref.L343_EDGE, HAIRLINE=Ref.L318, EDGE=Ref.L405, DISABLED_EDGE=Ref.L335, GUIDE=Ref.L335_GUIDE, THUMB=Ref.L455;
    // Text
    public static final Color TEXT=Ref.L875, MUTED=Ref.L745, DISABLED_TEXT=Ref.L575, INK=Ref.L835;
    // Accent / selection / primary button (restrained steel blue)
    public static final Color ACCENT=Ref.STEEL_BLUE_735, SELECTION=Ref.STEEL_BLUE_362,
        PRIMARY=Ref.STEEL_BLUE_338, PRIMARY_HOVER=Ref.STEEL_BLUE_388, PRIMARY_EDGE=Ref.STEEL_BLUE_558;
}
