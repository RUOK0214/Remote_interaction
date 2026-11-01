package io.github.ruok0214.remoteinteraction.client;

/** Fractions of the GUI viewport keep the panel on screen after resolution/GUI-scale changes. */
public record QuickLayout(double x,double y,double width,double height) {
    public static final QuickLayout DEFAULT=new QuickLayout(.12,.25,.76,.5);
    public static final int MARGIN=8, MIN_WIDTH=160, MIN_HEIGHT=54;
    public QuickLayout {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(width)||!Double.isFinite(height))
            throw new IllegalArgumentException("Non-finite window layout");
        x=Math.clamp(x,0,1); y=Math.clamp(y,0,1); width=Math.clamp(width,.1,1); height=Math.clamp(height,.1,1);
    }
    public record Bounds(int x,int y,int width,int height) {
        public int right(){return x+width;}
        public int bottom(){return y+height;}
        public boolean contains(double px,double py){return px>=x&&px<right()&&py>=y&&py<bottom();}
    }
    public Bounds bounds(int screenWidth,int screenHeight) {
        int availableW=Math.max(1,screenWidth-2*MARGIN),availableH=Math.max(1,screenHeight-2*MARGIN);
        int w=Math.clamp((int)Math.round(width*screenWidth),Math.min(MIN_WIDTH,availableW),availableW);
        int h=Math.clamp((int)Math.round(height*screenHeight),Math.min(MIN_HEIGHT,availableH),availableH);
        return new Bounds(Math.clamp((int)Math.round(x*screenWidth),MARGIN,Math.max(MARGIN,screenWidth-MARGIN-w)),
                Math.clamp((int)Math.round(y*screenHeight),MARGIN,Math.max(MARGIN,screenHeight-MARGIN-h)),w,h);
    }
    public static QuickLayout fromBounds(Bounds rect,int screenWidth,int screenHeight) {
        return new QuickLayout(rect.x()/(double)screenWidth,rect.y()/(double)screenHeight,
                rect.width()/(double)screenWidth,rect.height()/(double)screenHeight);
    }
}
