package io.github.ruok0214.remoteinteraction.client;

import net.minecraft.gizmos.GizmoStyle;

public record MarkerAppearance(int color,int transparency,float lineWidth) {
    public static final MarkerAppearance DEFAULT=new MarkerAppearance(0x43E7D4,0,2.5f);
    public MarkerAppearance {
        color&=0xFFFFFF;
        transparency=Math.clamp(transparency,0,100);
        if(!Float.isFinite(lineWidth))throw new IllegalArgumentException("Non-finite marker line width");
        lineWidth=Math.clamp(lineWidth,1f,6f);
    }
    public int argb(){return Math.round(255*(100-transparency)/100f)<<24|color;}
    public GizmoStyle style(){return GizmoStyle.stroke(argb(),lineWidth);}
}
