package com.kenjoy.kenjoycnc;
import javax.swing.JDialog;
/** Compatibility entry for the original startup call; discovery owns the nonmodal UI. */
public final class Re_d extends JDialog {
    public Re_d(){}
    @Override public void setVisible(boolean visible){if(visible)com.kenjoy.kenjoycnc.connection.ConnectionService.startup();}
}
