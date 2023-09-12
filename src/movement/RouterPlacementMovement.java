package movement;

import core.Coord;
import core.DTNSim;
import core.Settings;
import core.SettingsError;
import movement.map.MapNode;
import movement.map.SimMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
//import interfaces.ConnectivityGridRouter;

/**
 * Map based placement model which distributes routers with road.wkt
 *
 * 
 * 
 */
public class RouterPlacementMovement extends MapBasedMovement implements SwitchableMovement {

  /**
   * a way to get a hold of this...
   */
  private static RouterPlacementMovement myRPMInstance = null;
  public static final String ROUTER_ID_PREFIX_S = "groupID";

  /**
   * Module name in configuration of Simulation
   */
  public static final String RouterPlacement_NS = "PARouterPlacement";
  /**
   * Divides the map with cubeSize
   */
  public static final String CUBE_SIZE = "cubeSize";
  /**
   * The number of BasicalNode that used for computing the number of routers
   */
  public static final String NOFR_BASICAL_NODE = "nrofBasicalNode";
  /**
   * The number of FloatNode that used for increasing or decreasing the number of routers
   */
  public static final String NROF_FLOAT_NODE = "nrofFloatNode";
  /**
   * A threshold value for adjusting the function F(n) that used for modifying the number of
   * routers
   */
  public static final String FLOAT_THRESHOLD = "floatThreshold";
  /**
   * The validity of probability of floatNode
   */
  public static final String PRO_FLOAT_NODE = "probabilityFloatNode";
  /**
   * The probability of router position
   */
  public static final String PRO_DRIFT_X = "probabilityDriftX";
  public static final String PRO_DRIFT_Y = "probabilityDriftY";


  /**
   * Nodes in map for counting the number of the same location
   */
  private HashMap<Coord, Integer> nodes;
  /**
   * Intersections create for store the number of intersections in every area
   */
  private HashMap<String, Integer> intersections;
  /**
   * nrofRouters for store the number of routers
   */
  private HashMap<String, Integer> nrofRouters;
  /**
   * nrofNodes for store the number of nodes in areas
   */
  private HashMap<String, Integer> nrofNodes;
  /**
   * routers in areas
   */
  private HashMap<String, List<Coord>> routerLoc;
  private List<MapNode> routerNodes;

  /**
   * The parameter for computing the number of routers
   */
  private int cubeSize;
  private int nrofBasicalNode;
  private int nrofFloatNode;
  private int floatThreshold;
  private double probabilityFloatNode;
  private double probabilityDriftX;
  private double probabilityDriftY;
  private int nrofRoutersCount;

  /**
   * the length of every area's side
   */
  private double cubeLength;

  /**
   * Moving window
   */
  private double windowX;
  private double windowY;
  private double windowSizeX;
  private double windowSizeY;
  private int windowNowPosition;

  /**
   * record the lastRouterLocation
   */
  private int lastRouterLoc;

  /**
   * for output
   */
  private static int OcubeSize;
  private static double OwindowSizeX;
  private static double OwindowSizeY;
  public static HashMap<String, Integer> Ointersections;
  public static HashMap<String, Integer> OnrofRouters;
  public static HashMap<String, Integer> OnrofNodes;
  private static HashMap<String, List<Coord>> OrouterLoc;
  /**
   * convert to MapNode
   */
  private static List<MapNode> OrouterNodes;

  protected Random rng;

  static {
    DTNSim.registerForReset(RouterPlacementMovement.class.getCanonicalName());
    reset();
  }

  /**
   * Constructor 1
   *
   * @param settings for providing the configuration file
   * 
   * 
   * @time: 2021/05/19
   */
  public RouterPlacementMovement(Settings settings) {
    super(settings);
    String idPrefix = settings.getSetting(ROUTER_ID_PREFIX_S);
    this.rng = new Random(idPrefix.hashCode());

    /** 1 Initializes the parameter of RouterPlacement*/
    init();

    /** 2 Updating configuration information*/
    updateVauleFromConfig(settings);
    /** 3 Counting the number of intersection*/
    CountingIntersection(map, settings);
    /** 4 Calculate the number of routers*/
    areaNrofRouter();
    /** */
    RandomPlacement(map, settings);

    /** for the other file to use this parameters*/
    setAllStaticParam();
  }

  /**
   * Constructor 2 copy constructor
   *
   * 
   * 
   * @time: 2021/05/19
   */
  public RouterPlacementMovement(RouterPlacementMovement sm) {
    super(sm);
    this.nodes = sm.nodes;
    this.cubeSize = sm.cubeSize;
    this.nrofBasicalNode = sm.nrofBasicalNode;
    this.nrofFloatNode = sm.nrofFloatNode;
    this.floatThreshold = sm.floatThreshold;
    this.probabilityFloatNode = sm.probabilityFloatNode;
    this.probabilityDriftX = sm.probabilityDriftX;
    this.probabilityDriftY = sm.probabilityDriftY;
    this.nrofRoutersCount = sm.nrofRoutersCount;
    this.intersections = sm.intersections;
    this.nrofRouters = sm.nrofRouters;
    this.nrofNodes = sm.nrofNodes;
    this.routerLoc = sm.routerLoc;
    this.cubeLength = sm.cubeLength;
    this.windowX = sm.windowX;
    this.windowY = sm.windowY;
    this.windowSizeX = sm.windowSizeX;
    this.windowSizeY = sm.windowSizeY;
    this.windowNowPosition = sm.windowNowPosition;
    this.lastRouterLoc = sm.lastRouterLoc;
    this.routerNodes = sm.routerNodes;
    this.rng = sm.rng;
    setAllStaticParam();
  }


  /**
   * set the parameters for the other module using related data
   *
   * 
   * 
   * @time: 2021/05/19
   */
  public void setAllStaticParam() {
    setOwindowSizeY();
    setOwindowSizeX();
    setOcubeSize();
    setOrouterLoc();
    setOinersections();
    setOnrofRouters();
    setOnrofNodes();
    setOrouterNodes();
  }


  /**
   * Initializes a new RouterPlacement parameter
   *
   * 
   * 
   * @time: 2021/04/29
   */
  private void init() {
    this.nodes = new HashMap<Coord, Integer>();
    this.nrofBasicalNode = 4;
    this.nrofFloatNode = 2;
    this.floatThreshold = 5;
    this.probabilityFloatNode = 0.7;
    this.probabilityDriftX = 0.5;
    this.probabilityDriftY = 0.5;
    this.nrofRoutersCount = 0;
    this.lastRouterLoc = 0;
    this.windowNowPosition = 0;
    this.windowX = 0.0;
    this.windowY = 0.0;
    this.windowSizeX = 0.0;
    this.windowSizeY = 0.0;
    this.intersections = new HashMap<String, Integer>();
    this.nrofRouters = new HashMap<String, Integer>();
    this.nrofNodes = new HashMap<String, Integer>();
    this.routerLoc = new HashMap<String, List<Coord>>();
    this.routerNodes = new ArrayList<MapNode>();
    //        this.rng = new Random(new Date().getTime());
    this.rng = new Random("Initializes a new RouterPlacement parameter".hashCode());
  }

  public static void reset() {
    OwindowSizeX = 0.0;
    OwindowSizeY = 0.0;
    OcubeSize = 16;
    OrouterLoc = new HashMap<String, List<Coord>>();
    Ointersections = new HashMap<String, Integer>();
    OnrofRouters = new HashMap<String, Integer>();
    OnrofNodes = new HashMap<String, Integer>();
    OrouterNodes = new ArrayList<MapNode>();
  }

  /**
   * Update the information of configuration and set the computation of the number of routers
   *
   * @param settings to get parameters in setting file
   * 
   * 
   * @time: 2021/04/29
   */
  public void updateVauleFromConfig(Settings settings) {
    //    	System.out.println(settings);
    if (settings.contains(CUBE_SIZE)) {
      /** Deal with the problem of the non-square number*/
      double tmp = Math.sqrt(settings.getInt(CUBE_SIZE));
      int mid = (int) Math.round(tmp);
      this.cubeSize = mid * mid;
    }

    if (settings.contains(NOFR_BASICAL_NODE)) {
      this.nrofBasicalNode = settings.getInt(NOFR_BASICAL_NODE);
    }

    if (settings.contains(NROF_FLOAT_NODE)) {
      this.nrofFloatNode = settings.getInt(NROF_FLOAT_NODE);
    }

    if (settings.contains(FLOAT_THRESHOLD)) {
      this.floatThreshold = settings.getInt(FLOAT_THRESHOLD);
    }

    if (settings.contains(PRO_FLOAT_NODE)) {
      this.probabilityFloatNode = settings.getDouble(PRO_FLOAT_NODE);
    }

    if (settings.contains(PRO_DRIFT_X)) {
      this.probabilityDriftX = settings.getDouble(PRO_DRIFT_X);
    }

    if (settings.contains(PRO_DRIFT_Y)) {
      this.probabilityDriftY = settings.getDouble(PRO_DRIFT_Y);
    }
  }

  /**
   * Count the number of intersections within one divided area
   *
   * @param simmap   to get map information
   * @param settings to get parameters in setting file
   * 
   * 
   * @time: 2021/05/03
   */
  private void CountingIntersection(SimMap simmap, Settings settings) {

    Coord minBound = simmap.getMinBound();
    Coord maxBound = simmap.getMaxBound();
    List<MapNode> Smallnodes;
    List<MapNode> nodesNeb;
    //        System.out.println("minBound="+minBound+" || maxBound="+maxBound);
    double areas = (maxBound.getX() - minBound.getX()) * (maxBound.getY() - minBound.getY());

    initMovingWindow(minBound, maxBound);
    this.cubeLength = Math.sqrt(areas / this.cubeSize);
    //        System.out.println("areas = " +areas +" || cubeLength ="+cubeLength);
    //        System.out.println("windowSizeX = "+this.windowSizeX+" || windowSizeY = "+this.windowSizeY);
    int rowCount = (int) (Math.sqrt(this.cubeSize));
    /** count the number of same location*/
    Smallnodes = getMap().getNodes();
    for (int i = 0, n = Smallnodes.size(); i < n; i++) {
      Coord tmpNode = Smallnodes.get(i).getLocation();
      /** get the neighbor nodes*/
      nodesNeb = Smallnodes.get(i).getNeighbors();

      /** 因为mapnode是用hashtable实现的，相同的location会被替换掉，所以用邻居列表存了相关的信息，如果邻居列表存在至少三个，那么就是intersection*/
      int numNeb = nodesNeb.size();
      if (numNeb > 2) {
        nodes.put(tmpNode, numNeb);
      }
    }
    /** divide the area of map by Moving Window
     * and count the number of intersections in this window*/
    initMovingWindow(minBound, maxBound);
    for (int i = 0; i < rowCount; i++) {
      for (int j = 0; j < rowCount; j++) {
        String name = "N" + this.windowNowPosition;
        intersections.put(name, 0);
        nrofNodes.put(name, 0);
        /** calculate the number of intersections*/
        nodes.forEach((k, v) -> {
          /** the node is located in this window*/
          if (k.getX() > this.windowX && k.getX() < this.windowX + this.windowSizeX
              && k.getY() > this.windowY && k.getY() < this.windowY + this.windowSizeY) {
            intersections.put(name, intersections.get(name) + 1);
          }
        });
        /** counting the number of nodes in areas*/
        for (int k = 0, n = Smallnodes.size(); k < n; k++) {
          Coord tmp = Smallnodes.get(k).getLocation();
          if (tmp.getX() > this.windowX && tmp.getX() < this.windowX + this.windowSizeX
              && tmp.getY() > this.windowY && tmp.getY() < this.windowY + this.windowSizeY) {
            nrofNodes.put(name, nrofNodes.get(name) + 1);
          }
        }
        this.windowX += this.windowSizeX;
        this.windowNowPosition += 1;
      }
      this.windowX = minBound.getX();
      this.windowY += this.windowSizeY;
    }
  }

  /**
   * Calculate the value of FN that is used for adjusting the number of routes dynamically
   *
   * @param N is the number of the intersections in specific area
   * @param T is the floatThreshold value
   * @return The value of the FN
   * 
   * 
   * @time: 2021/05/04
   */
  public double calculateFN(int N, int T) {
    int point = N - T;
    double Fn = 2 / (1 + Math.pow(Math.E, -point)) - 1;
    return Fn;
  }

  /**
   * Calculate the number of router in each area
   *
   * 
   * 
   * @time: 2021/05/06
   */
  private void areaNrofRouter() {
    intersections.forEach((k, v) -> {
      double FN = calculateFN(v, this.floatThreshold);
      int routerNum = (int) Math.round(this.nrofBasicalNode + this.nrofFloatNode * FN);
      //    		System.out.println(routerNum+"|"+(this.nrofBasicalNode + this.nrofFloatNode*FN)+"|");
      if (v == 0) {
        if (nrofNodes.get(k) > 0) {
          nrofRouters.put(k, 1);
        } else {
          //    				double randomNum = Math.random();
          if (halvingJudge(rng.nextDouble(), this.probabilityFloatNode)) {
            nrofRouters.put(k, 0);
          } else {
            nrofRouters.put(k, 1);
          }
        }
      } else {
        if (routerNum > 0) {
          nrofRouters.put(k, routerNum);
        } else {
          nrofRouters.put(k, 0);
        }
      }

    });
  }

  /**
   * Is the number halving by boundary value?
   *
   * @param num      is the input number
   * @param boundary is the boundary value for judging the num
   * @return False if num is less than or equal to boundary value, true if num is more than boundary
   * value
   * 
   * 
   * @time: 2021/05/06
   */
  public boolean halvingJudge(Double num, Double boundary) {
    return num <= boundary ? false : true;
  }

  /**
   * Random router placement in areas, according to the number of routers
   *
   * @param simmap   for getting the map information
   * @param settings for getting the configuration information
   * 
   * 
   * @time: 2021/05/08
   */
  private void RandomPlacement(SimMap simmap, Settings settings) {
    /** initialize the moving window*/
    initMovingWindow(simmap.getMinBound(), simmap.getMaxBound());
    /** List for count the number of router*/
    List<Integer> routerCount = new ArrayList<Integer>();
    routerCount.add(0);
    double maxWorldX = simmap.getMaxBound().getX();
    double maxWorldY = simmap.getMaxBound().getY();
    double minWorldX = simmap.getMinBound().getX();
    double minWorldY = simmap.getMinBound().getY();
    /** generate the coord of routers in areas*/
    this.nrofRouters.forEach((k, v) -> {
      /** variance*/
      double b = Math.sqrt(this.cubeLength / 8);
      /** expectation*/
      double miu = this.cubeLength / 4;
      double randomX;
      double randomY;
      double randomDriftX = 0.0;
      double randomDriftY = 0.0;
      int row = (int) Math.sqrt(this.cubeSize);
      int rCount = 0;
      int cCount = 0;
      /** judge the generated location whether is beyond bounds*/
      boolean overX = false, overY = false;
      this.windowNowPosition = Integer.parseInt(k.substring(1));
      rCount = this.windowNowPosition % row;
      cCount = (this.windowNowPosition - cCount) / row;
      this.windowX = this.windowSizeX * rCount;
      this.windowY = this.windowSizeY * cCount;
      List<Coord> areaRoutersLoc = new ArrayList<Coord>();
      for (int i = 0; i < v; ) {
        overX = overY = false;
        //    			randomDriftX = Math.random()*this.windowSizeX/4;
        //        		randomDriftY = Math.random()*this.windowSizeY/4;
        //        		randomDriftX = Math.random()*(this.windowSizeX/3-this.windowSizeX/5)+this.windowSizeX/5;
        //        		randomDriftY = Math.random()*(this.windowSizeY/3-this.windowSizeY/5)+this.windowSizeY/5;
        randomDriftX =
            rng.nextDouble() * (this.windowSizeX / 3 - this.windowSizeX / 5) + this.windowSizeX / 5;
        randomDriftY =
            rng.nextDouble() * (this.windowSizeY / 3 - this.windowSizeY / 5) + this.windowSizeY / 5;
        //    			randomX = Math.random()*this.windowSizeX;
        //        		randomY = Math.random()*this.windowSizeY;
        randomX = rng.nextDouble() * this.windowSizeX;
        randomY = rng.nextDouble() * this.windowSizeY;

        /** generate the random number by Gaussian*/
        //    			Random r1 = new Random();
        //        		randomX = b*r1.nextGaussian()+miu;
        //        		Random r2 = new Random();
        //        		randomY = b*r2.nextGaussian()+miu;

        /** coord's probability drift*/
        double probabilityDrift = rng.nextDouble();
        if (!halvingJudge(probabilityDrift, this.probabilityDriftX)) {
          randomX += (this.windowSizeX / 2 - randomX);
        }
        probabilityDrift = rng.nextDouble();
        if (!halvingJudge(probabilityDrift, this.probabilityDriftY)) {
          randomY += (this.windowSizeY / 2 - randomY);
        }

        randomX += this.windowX;
        randomY += this.windowY;

        double pdx = rng.nextDouble();
        if (!halvingJudge(pdx, 0.5) && randomX - randomDriftX > 0) {
          randomDriftX *= -1;
        }
        double pdy = rng.nextDouble();
        if (!halvingJudge(pdy, 0.5) && randomY - randomDriftY > 0) {
          randomDriftY *= -1;
        }

        randomX += randomDriftX;
        randomY += randomDriftY;
        //        		System.out.println(maxWorldX+"||"+maxWorldY);
        if (randomX <= minWorldX || randomX > maxWorldX || randomX <= this.windowX) {
          overX = true;
        }
        if (randomY <= minWorldY || randomY > maxWorldY || randomY <= this.windowY) {
          overY = true;
        }

        //        		randomX = rng.nextDouble()*this.windowSizeX + this.windowX;
        //        		randomY = rng.nextDouble()*this.windowSizeY + this.windowY;

        if (!overX && !overY) {
          /** the position of the generated router*/
          Coord routerLocation = new Coord(randomX, randomY);
          //        			System.out.println("x = "+randomX+" || y = "+randomY);
          /** generate the DTNHost node for router*/
          areaRoutersLoc.add(routerLocation);
          i++;
        }
      }
      routerCount.set(0, routerCount.get(0) + v);
      this.routerLoc.put(k, areaRoutersLoc);
    });
    //    	System.out.println(routerCount.get(0));
    this.nrofRoutersCount = routerCount.get(0);
    super.nrofHosts = this.nrofRoutersCount;
  }

  /**
   * Initialize the location of a router
   *
   * @return router location
   * 
   * 
   * @time: 2021/06/08
   */
  @Override
  public Coord getInitialLocation() {
    List<Integer> count = new ArrayList<Integer>();
    List<String> name = new ArrayList<String>();
    double x = 0.0, y = 0.0;
    this.nrofRouters.forEach((k, v) -> {
      if (v.equals(null)) {
        count.add(0);
      } else {
        count.add(v);
      }
      name.add(k);
    });
    /** count the round of areas*/
    int roundCount = 0;
    /** enlarge the number of routers*/
    int groupCount = 0;
    /** record the number of routers for the current block*/
    int span = 0;
    /** for logic deal with the final area*/
    boolean isLastArea = false;

    while (this.lastRouterLoc - groupCount > 0 && !count.isEmpty()) {
      span = count.remove(0);
      groupCount += span;
      roundCount++;
    }
    roundCount--;
    if (count.isEmpty()) {
      isLastArea = true;
    }
    if (this.lastRouterLoc == groupCount) {
      //    		System.out.println("groupCount:"+groupCount+" || "+"lastRouterLoc:"+lastRouterLoc);
      x = this.routerLoc.get(name.get(roundCount)).get(span - 1).getX();
      y = this.routerLoc.get(name.get(roundCount)).get(span - 1).getY();
    } else {
      groupCount -= span;
      //    		System.out.println("groupCount:"+groupCount+" || "+"lastRouterLoc:"+lastRouterLoc);

      //    		System.out.println("name.get(roundCount)="+name.get(roundCount)+" || num="+this.routerLoc.get(name.get(roundCount)).size()
      //					+" || this.lastRouterLoc="+lastRouterLoc+" || groupCount = "+groupCount
      //					+" || loc-rcount="+(lastRouterLoc-groupCount)+"|| span="+span);
      if ((!count.isEmpty() || isLastArea) && (this.lastRouterLoc <= this.nrofRoutersCount)) {
        x = this.routerLoc.get(name.get(roundCount)).get(this.lastRouterLoc - groupCount - 1)
            .getX();
        y = this.routerLoc.get(name.get(roundCount)).get(this.lastRouterLoc - groupCount - 1)
            .getY();
      }

    }
    Coord loc = new Coord(x, y);
    MapNode n = new MapNode(loc);
    this.lastMapNode = n;
    this.initiallocation = n;
    if (!routerNodes.contains(n)) {
      routerNodes.add(n);
    }
    return loc;
  }

  /**
   * Initialize the moving windows' parameter
   *
   * @param minBound is the minimal bound node in map
   * @param maxBound is the maximal bound node in map
   * 
   * 
   * @time: 2021/05/05
   */
  private void initMovingWindow(Coord minBound, Coord maxBound) {
    this.windowX = minBound.getX();
    this.windowY = minBound.getY();
    this.windowSizeX = (maxBound.getX() - minBound.getX()) / Math.sqrt(this.cubeSize);
    this.windowSizeY = (maxBound.getY() - minBound.getY()) / Math.sqrt(this.cubeSize);
    this.windowNowPosition = 0;
  }


  /**
   * Returns a single coordinate path (using the only possible coordinate)
   *
   * @return a single coordinate path
   * @time 2021/06/09
   */
  @Override
  public Path getPath() {
    Path p = new Path(0);
    return p;
  }


  /**
   * Get the number of the corresponding divided area
   *
   * @param N is the information that the region block
   * @return the number of the corresponding divided area
   * 
   * 
   * @time: 2021/04/29
   */
  public int getIntersections(String N) {
    if (this.intersections.containsKey(N)) {
      return this.intersections.get(N);
    } else {
      return -9999;
    }
  }

  /**
   * Get all the information of intersections in areas
   *
   * @return the intersections
   * 
   * 
   * @time: 2021/05/03
   */
  public HashMap<String, Integer> getAllIntersections() {
    return this.intersections;
  }

  /**
   * Get all the information of routers in areas
   *
   * @return the intersections
   * 
   * 
   * @time: 2021/05/04
   */
  public HashMap<String, Integer> getAllnrofRouters() {
    return this.nrofRouters;
  }

  /**
   * Get all the information of nodes in areas
   *
   * @return the intersections
   * 
   * 
   * @time: 2021/05/04
   */
  public HashMap<String, Integer> getAllnrofNodes() {
    return this.nrofNodes;
  }

  /**
   * Get all the location of Routers in areas
   *
   * @return the routerLoc
   * 
   * 
   * @time: 2021/05/08
   */
  public HashMap<String, List<Coord>> getAllRoutersLoc() {
    return this.routerLoc;
  }

  /**
   * get the number of pointed node
   *
   * @param coord the location of the node
   * @return the number of pointed node
   * 
   * 
   * @time: 2021/04/30
   */
  public int getNode(Coord coord) {
    if (this.nodes.containsKey(coord)) {
      return this.nodes.get(coord);
    }
    return -9998;
  }

  /**
   * get the windowSizes that the windowSizeX and windowSizeY
   *
   * @return windowSizes
   * 
   * 
   * @time: 2021/04/30
   */
  public List<Double> getWindowSize() {
    List<Double> windowSizes = new ArrayList<Double>();
    windowSizes.add(windowSizeX);
    windowSizes.add(windowSizeY);
    return windowSizes;
  }

  /**
   * get the nrofRoutersCount, the number of routers
   *
   * @return nrofRoutersCount
   * 
   * 
   * @time: 2021/05/08
   */
  public int getNrofRoutersCount() {
    return this.nrofRoutersCount;
  }

  /**
   * get the cubeSize
   *
   * @return cubeSize
   * 
   * 
   * @time: 2021/04/30
   */
  public int getCubeSize() {
    return this.cubeSize;
  }

  /**
   * set the OcubeSize value
   *
   * 
   * 
   * @time: 2021/05/19
   */
  public void setOcubeSize() {
    OcubeSize = this.cubeSize;
  }

  /**
   * get the OcubeSize for the other file
   *
   * @return OcubeSize
   * 
   * 
   * @time: 2021/05/19
   */
  public static int getOCubeSize() {
    return OcubeSize;
  }

  /**
   * set the OwindowSizeX value
   *
   * 
   * 
   * @time: 2021/05/19
   */
  public void setOwindowSizeX() {
    OwindowSizeX = this.windowSizeX;
  }

  /**
   * get the OwindowSizeX for the other file
   *
   * @return OwindowSizeX
   * 
   * 
   * @time: 2021/05/19
   */
  public static double getOWindowSizeX() {
    return OwindowSizeX;
  }

  /**
   * get the OwindowSizeY for the other file
   *
   * @return OwindowSizeY
   * 
   * 
   * @time: 2021/05/19
   */
  public static double getOWindowSizeY() {
    return OwindowSizeY;
  }

  /**
   * set the OwindowSizeY value
   *
   * 
   * 
   * @time: 2021/05/19
   */
  public void setOwindowSizeY() {
    OwindowSizeY = this.windowSizeY;
  }

  /**
   * set the OrouterLoc value
   *
   * 
   * 
   * @time: 2021/05/19
   */
  public void setOrouterLoc() {
    OrouterLoc = this.routerLoc;
  }

  /**
   * get the OrouterLoc for the other file
   *
   * @return OrouterLoc
   * 
   * 
   * @time: 2021/05/19
   */
  public static HashMap<String, List<Coord>> getOrouterLoc() {
    return OrouterLoc;
  }

  /**
   * set the Ointersections for the other file
   *
   * @return Ointersections
   * 
   * 
   * @time: 2021/05/28
   */
  public void setOinersections() {
    Ointersections = this.intersections;
  }

  /**
   * set the OnrofRouters for the other file
   *
   * @return OnrofRouters
   * 
   * 
   * @time: 2021/05/28
   */
  public void setOnrofRouters() {
    OnrofRouters = this.nrofRouters;
  }

  /**
   * set the OnrofNodes for the other file
   *
   * @return OnrofNodes
   * 
   * 
   * @time: 2021/05/28
   */
  public void setOnrofNodes() {
    OnrofNodes = this.nrofNodes;
  }

  /**
   * get the intersections for the other file
   *
   * @return Ointersections
   * 
   * 
   * @time: 2021/05/28
   */
  public static HashMap<String, Integer> getOAllIntersections() {
    return Ointersections;
  }

  /**
   * get the OnrofRouters for the other file
   *
   * @return OnrofRouters
   * 
   * 
   * @time: 2021/05/28
   */
  public static HashMap<String, Integer> getOAllnrofRouters() {
    return OnrofRouters;
  }

  /**
   * get the OnrofNodes for the other file
   *
   * @return OnrofNodes
   * 
   * 
   * @time: 2021/05/28
   */
  public static HashMap<String, Integer> getOAllnrofNodes() {
    return OnrofNodes;
  }

  /**
   * get the nrofBasicalNode
   *
   * @return nrofBasicalNode
   * 
   * 
   * @time: 2021/04/30
   */
  public int getNrofBasicalNode() {
    return this.nrofBasicalNode;
  }

  /**
   * get the nrofFloatNode
   *
   * @return nrofFloatNode
   * 
   * 
   * @time: 2021/04/30
   */
  public int getNrofFloatNode() {
    return this.nrofFloatNode;
  }

  /**
   * get the floatThreshold
   *
   * @return floatThreshold
   * 
   * 
   * @time: 2021/04/30
   */
  public int getFloatThreshold() {
    return this.floatThreshold;
  }

  /**
   * get the probabilityFloatNode
   *
   * @return probabilityFloatNode
   * 
   * 
   * @time: 2021/04/30
   */
  public double getProbilityFloatNode() {
    return this.probabilityFloatNode;
  }

  /**
   * get the probabilityDriftX
   *
   * @return probabilityDriftX
   * 
   * 
   * @time: 2021/05/08
   */
  public double getProbabilityDriftX() {
    return this.probabilityDriftX;
  }

  /**
   * get the probabilityDriftY
   *
   * @return probabilityDriftY
   * 
   * 
   * @time: 2021/05/08
   */
  public double getProbabilityDriftY() {
    return this.probabilityDriftY;
  }

  /**
   * set the OrouterNodes
   *
   * 
   * 
   * @time: 2021/06/08
   */
  private void setOrouterNodes() {
    OrouterNodes = this.routerNodes;
  }

  /**
   * get the OrouterNodes
   *
   * @return OrouterNodes
   * 
   * 
   * @time: 2021/06/08
   */
  public static List<MapNode> getRouterNodes() {
    return OrouterNodes;
  }

  /**
   * set the static OrouterNodes
   *
   * 
   * 
   * @time: 2021/06/09
   */
  public static void setRouterNodesByConn(List<MapNode> rn) {
    OrouterNodes = rn;
  }


  /**
   * Makes sure that a value is positive
   *
   * @param value       Value to check
   * @param settingName Name of the setting (for error's message)
   * @throws SettingsError if the value was not positive
   * 
   * 
   * @time: 2021/05/08
   */
  private void ensurePositiveValue(double value, String settingName) {
    if (value < 0) {
      throw new SettingsError("Negative value (" + value +
          ") not accepted for setting " + settingName);
    }
  }

  /**
   * show the report
   *
   * @return
   * 
   * 
   * @time: 2021/05/03
   */
  public ArrayList<String> toStringList() {
    ArrayList<String> str = new ArrayList<String>();

    for (int i = 0, n = this.cubeSize; i < n; i++) {
      //    		System.out.println("N"+ i +" : "+this.intersections.get("N"+i));
      //    		str.add()
    }
    return str;
  }

  /**
   * record the last router number for replicating Router
   *
   * @return lastRouterLoc+1
   * 
   * 
   * @time: 2021/05/09
   */
  public void updateLastRouterLoc() {
    this.lastRouterLoc += 1;
  }

  /**
   * copy
   *
   * @return new instance
   * 
   * 
   * @time: 2021/05/09
   */
  @Override
  public RouterPlacementMovement replicate() {
    this.updateLastRouterLoc();
    //    	System.out.print("replicate's lastRouterLoc = "+this.lastRouterLoc);
    RouterPlacementMovement r = new RouterPlacementMovement(this);
    return r;
  }
}
