package com.kenjoy.kenjoycnc.note;

import java.awt.*;
import javax.swing.*;

/** Width tracking belongs to the actual sidebar viewport, whose child is the host. */
public final class NoteSidebarView extends JPanel implements Scrollable {
    private final JViewport viewport;
    private final Component host;
    private final int previousX;
    private NoteSidebarView(JViewport viewport,Component host){super(new BorderLayout());this.viewport=viewport;this.host=host;previousX=viewport.getViewPosition().x;add(host,BorderLayout.NORTH);}
    public static NoteSidebarView attach(Component tab){
        JViewport viewport=(JViewport)SwingUtilities.getAncestorOfClass(JViewport.class,tab);
        if(viewport==null)return null;
        if(viewport.getView() instanceof NoteSidebarView)return (NoteSidebarView)viewport.getView();
        Component host=viewport.getView();NoteSidebarView wrapper=new NoteSidebarView(viewport,host);viewport.setView(wrapper);return wrapper;
    }
    public void detach(){if(viewport.getView()==this){Point position=viewport.getViewPosition();remove(host);viewport.setView(host);viewport.setViewPosition(new Point(previousX,position.y));}}
    public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
    public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return Math.max(12,getFontMetrics(getFont()).getHeight());}
    public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(1,(orientation==SwingConstants.VERTICAL?visible.height:visible.width)-getScrollableUnitIncrement(visible,orientation,direction));}
    public boolean getScrollableTracksViewportWidth(){return true;}
    public boolean getScrollableTracksViewportHeight(){return false;}
}
