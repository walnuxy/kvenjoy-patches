package com.kenjoy.uitrial;

/**
 * 0.017 shape and rhythm tokens (logical px). Radii are expressed as arc diameters for Java2D.
 * Spacing tokens are paint-time only: component bounds and Insets stay frozen to the original
 * layout so business geometry, regression fixtures and restore behaviour are unchanged.
 */
public final class UiMetrics {
    private UiMetrics(){}
    /** 8px base grid; half step for hairline-level offsets. */
    public static final int GRID=8, HALF=4;
    /** Corner radii: controls 5, cards 8, outer window 10. */
    public static final int CONTROL_ARC=14, CARD_ARC=16, WINDOW_ARC=20, PILL_ARC=8;
    /** Section header: left text inset inside a card, gap kept above the first child. */
    public static final int SECTION_TEXT_X=10, SECTION_GAP=2;
    /** Type scale: body 12, section header 11 bold. */
    public static final float BODY_SIZE=12f, SECTION_SIZE=11f;
}
