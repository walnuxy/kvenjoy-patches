package com.kenjoy.kenjoycnc.note;

import java.awt.Color;

/** Screen colors shared by the continuous preview and its text editor. */
public final class NotePreviewColors {
    private NotePreviewColors(){}
    public static final Color CANVAS=new Color(0x090B0D),PAPER=new Color(0x111417),INK=new Color(0xD9DEE5),
        GUIDE=new Color(0x2B3036),EDGE=new Color(0x39414A),MUTED=new Color(0x9BA7B5),
        ACCENT=new Color(0x99C6FF),PENDING=new Color(0xA8B4C3),MISSING=new Color(0xFF7575),REPLACED=new Color(0x62A9FF),
        SELECTION=new Color(90,155,240,85),SHADOW=new Color(0,0,0,100),
        WRITE_CONFIRMED_FILL=new Color(46,160,110,42),WRITE_CONFIRMED_BAR=new Color(65,195,135,185),
        WRITE_INFLIGHT_FILL=new Color(245,165,36,78),WRITE_INFLIGHT_EDGE=new Color(255,190,60,230),
        WRITE_RESUME_FILL=new Color(56,189,248,70),WRITE_RESUME_EDGE=new Color(56,215,255),
        WRITE_BADGE_BG=new Color(14,24,34,215);
}
