package movement;

import java.util.*;
import movement.map.MapNode;
import core.Coord;
import core.DTNHost;
import core.Settings;


public class RouterPlacementMovement1 extends MapBasedMovement implements SwitchableMovement{
	/** a way to get a hold of this... */	
	public static final String ROUTER_ID_PREFIX_S = "groupID";
	
    /** Module name in configuration of Simulation*/
    public static final String RouterPlacement_NS = "PARouterPlacement";
//    /** Divides the map with cubeSize*/
//    public static final String CUBE_SIZE = "cubeSize";
    public static final String Gap = "gap";
    protected Random rng;
    private Coord loc;
    private static int lastDV;
    private int worldSizeX;
    private int worldSizeY;
    private int tRange;
    private static Set<Coord> usedLoc;
    //for generating trajectory path
    private static Set<MapNode> routerLocForMap;
    private static int TR;
    
    
    public RouterPlacementMovement1(Settings settings){
	    super(settings);
	    String idPrefix = settings.getSetting(ROUTER_ID_PREFIX_S);
	    Settings s = new Settings("MovementModel");
	    int [] worldSize = s.getCsvInts(MovementModel.WORLD_SIZE, 2);
		this.worldSizeX = worldSize[0];
		this.worldSizeY = worldSize[1];
	    this.rng = new Random(idPrefix.hashCode());  
	    double x = generateXY()*this.worldSizeX;
		double y = generateXY()*this.worldSizeY;
		this.loc = new Coord(x,y);
		RouterPlacementMovement1.lastDV = 0;
		this.tRange = Integer.parseInt(settings.getSetting(Gap));
		//cross group check
		if(settings.contains(idPrefix))
		RouterPlacementMovement1.usedLoc = new HashSet<Coord>();
		RouterPlacementMovement1.routerLocForMap = new HashSet<MapNode>();
		RouterPlacementMovement1.TR = this.tRange;
	}
	public RouterPlacementMovement1(RouterPlacementMovement1 r) {
		super(r);
		this.rng = r.rng;
//		lastDV = r.lastDV;
		this.worldSizeX =r.worldSizeX;
		this.worldSizeY = r.worldSizeY;
		this.tRange =r.tRange;
//		usedLoc = r.usedLoc;
//		routerLocForMap = r.routerLocForMap;
//		TR = r.TR;
	}
	/**
	 * Returns the only location of this movement model
	 * @return the only location of this movement model
	 */
	@Override
	public Coord getInitialLocation() {
		//RSU should be located along the road side.
//		for(int i=0;i<this.lastDV;i++) {
//			double t = rng.nextDouble();
//		}
		double x = generateXY()*this.worldSizeX;
		double y = generateXY()*this.worldSizeY;
		this.loc = new Coord(x,y);
		List<MapNode> nodes = map.getNodes();
		MapNode mn;
		Coord RouterLoc;
		//Locate the network relay device, which satisfy 
		boolean full = true;
		do {
			full = true;
			Coord mnLoc = nodes.get(rng.nextInt(nodes.size())).getLocation();
			mn = new MapNode(mnLoc);
			for(Coord c:usedLoc) {
				if(mnLoc.distance(c)<this.tRange) {
					full = false;
					break;
				}
			}
		}while(!full);
		RouterLoc = mn.getLocation();
		this.loc = mn.getLocation();
		this.lastMapNode = mn;
		this.initiallocation=mn;
//		this.lastDV+=2;
		RouterPlacementMovement1.usedLoc.add(RouterLoc);
		RouterPlacementMovement1.routerLocForMap.add(mn);
		return RouterLoc;
	}
	
    public double generateXY() {
    	return rng.nextDouble();
    }
    
    public static Set<MapNode> getRoutersLocForMap(){
    	return routerLocForMap;
    }
    
    public static int getTRange() {
    	return TR;
    }
    
    /**
	 * Returns a single coordinate path (using the only possible coordinate)
	 * @return a single coordinate path
	 */
	@Override
	public Path getPath() 
	{
		Path p = new Path(0);
		return p;
	}
	
	@Override
	public double nextPathAvailable() {
		return Double.MAX_VALUE;	// no new paths available
	}
	public void setRouterLocs(HashSet<MapNode> routerLocs) {
		RouterPlacementMovement1.routerLocForMap.addAll(routerLocs);
		if(RouterPlacementMovement1.usedLoc==null)
			RouterPlacementMovement1.usedLoc = new HashSet<Coord>();
		for(MapNode n : routerLocs) {
			Coord loc = n.getLocation();
			RouterPlacementMovement1.usedLoc.add(loc);
		}
	}
    
    @Override
    public RouterPlacementMovement1 replicate() {
    	return new RouterPlacementMovement1(this);
    }
    
    
    
}
