package interfaces;

import core.Coord;
import core.DTNHost;
import core.DTNSim;
import core.NetworkInterface;
import core.SimScenario;
import movement.RouterPlacementMovement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;


/**
 * For connecting the Router in this map
 *
 * 
 * 
 * @time1: 2021/05/18
 * @time2: 2021/05/23
 */
public class ConnectivityGridRouter extends ConnectivityOptimizer {

  static HashMap<Integer, ConnectivityGridRouter> gridobjects;

  /**
   * for all interfaces
   */
  private List<NetworkInterface> ginterfaces;
  private static HashMap<Integer, List<NetworkInterface>> nearInterfaces;
  private static int conveyAreaNo;

  /**
   * moving window
   */
  private double windowSizeX;
  private double windowSizeY;
  private int cubeLenth;

  static {
    DTNSim.registerForReset(ConnectivityGridRouter.class.getCanonicalName());
    reset();
  }

  public static void reset() {
    gridobjects = new HashMap<Integer, ConnectivityGridRouter>();
    nearInterfaces = new HashMap<Integer, List<NetworkInterface>>();
  }

  /**
   * Constructor 1
   *
   * 
   * 
   * @time: 2021/05/18
   */
  public ConnectivityGridRouter() {
    init();
  }


  /**
   * Returns a connectivity grid object based on a hash value
   *
   * @param hashCode A hash value that separates different interfaces form each other
   * @return the connectivity grid object for a specific interface
   * 
   * 
   * @time: 2021/05/22
   */
  public static ConnectivityGridRouter ConnectivityGridFactory(int hashCode) {
    if (gridobjects.containsKey((Integer) hashCode)) {
      return (ConnectivityGridRouter) gridobjects.get((Integer) hashCode);
    } else {
      ConnectivityGridRouter newgrid = new ConnectivityGridRouter();
      gridobjects.put((Integer) hashCode, newgrid);
      return newgrid;
    }
  }

  /**
   * initialize the parameters
   *
   * 
   * 
   * @time: 2021/05/18
   */
  private void init() {
    this.windowSizeX = RouterPlacementMovement.getOWindowSizeX();
    this.windowSizeY = RouterPlacementMovement.getOWindowSizeY();
    this.cubeLenth = (int) Math.sqrt(RouterPlacementMovement.getOCubeSize());
    RouterPlacementMovement.getOrouterLoc();
    this.ginterfaces = new ArrayList<NetworkInterface>();
  }

  /**
   * the state of whole system is stable, so it's necessary to add interface. This is a
   * pre-connection module in simulation system.
   *
   * @param ni The new network interface
   * 
   * 
   * @time: 2021/05/21
   */
  @Override
  public void addInterface(NetworkInterface ni) {
  }

  /**
   * Adds interfaces to overlay grid
   *
   * @param interfaces Collection of interfaces to add
   * 
   * 
   * @time: 2021/05/21
   */
  @Override
  public void addInterfaces(Collection<NetworkInterface> interfaces) {
    for (NetworkInterface n : interfaces) {
      addInterface(n);
    }
  }

  /**
   * Router don't move in this simulation platform because of the router's character. Therefore,
   * nothing to do at this time
   *
   * 
   * 
   * @time: 2021/05/21
   */
  @Override
  public void updateLocation(NetworkInterface ni) {
  }

  /**
   * Returns all interfaces using the same technology and channel that are in neighboring cells
   *
   * @param ni is the network interface
   * 
   * 
   * @time: 2021/05/20
   */
  //	@Override
  public Collection<NetworkInterface> getNearInterfaces(NetworkInterface ni, List<DTNHost> hosts) {
    List<NetworkInterface> netinterf = new ArrayList<NetworkInterface>();
    netinterf.clear();
    nearInterfaces.clear();
    Coord routerLocation = ni.getHost().getLocation();
    String interfaceType = ni.getInterfaceType();
    int areaNo = getAreaBelong(routerLocation);
    conveyAreaNo = areaNo;
    /** neighbor areas .No*/
    int eastNo = areaNo + 1;
    int southNo = areaNo + cubeLenth;
    int southEastNo = southNo + 1;
    boolean eastOut = false, southOut = false, southEastOut = false;
    int row = Math.floorDiv(areaNo, cubeLenth);
    if (areaNo % (cubeLenth) == 0) {
      eastOut = southEastOut = true;
    }
    if (row == cubeLenth - 1) {
      southOut = southEastOut = true;
    }
    for (int k = 0, kn = hosts.size(); k < kn; k++) {
      Coord tmphost = hosts.get(k).getLocation();
      int tmphostArea = getAreaBelong(tmphost);
      if (tmphostArea == areaNo || (tmphostArea == eastNo && !eastOut)
          || (tmphostArea == southNo && !southOut)
          || (tmphostArea == southEastNo && !southEastOut)) {
        List<NetworkInterface> filterNI = hosts.get(k).getInterfaces();

				for (int c = 0, cn = filterNI.size(); c < cn; c++)
				/** find interface using the same technology and channel*/ {
					if (filterNI.get(c).getInterfaceType().equals(interfaceType)) {
						netinterf.add(filterNI.get(c));
						List<NetworkInterface> tmpInterface = new ArrayList<NetworkInterface>();

						if (nearInterfaces.containsKey(tmphostArea)) {
							tmpInterface = nearInterfaces.get(tmphostArea);
						}
						tmpInterface.add(filterNI.get(c));
						nearInterfaces.put(tmphostArea, tmpInterface);
					}
				}
      }
    }
    return netinterf;
  }

  /**
   * According the location of router in areas to find which area contains this router
   *
   * @param routerLocation location
   * 
   * 
   * @time: 2021/05/18
   */
  public int getAreaBelong(Coord routerLocation) {
    double x = routerLocation.getX();
    double y = routerLocation.getY();
    int col = (int) Math.floor(x / windowSizeX);
    int row = (int) Math.floor(y / windowSizeY);
    return row * cubeLenth + col + 1;
  }

  /**
   * get all interfaces of router
   *
   * @return all interfaces
   * 
   * 
   * @time: 2021/05/21
   */
  @Override
  public Collection<NetworkInterface> getAllInterfaces() {
    List<DTNHost> hosts = SimScenario.getInstance().getHosts();
    List<NetworkInterface> tmpNIs = new ArrayList<NetworkInterface>();

    for (DTNHost tmphost : hosts) {
      tmpNIs.clear();
      /** is router*/
      if (tmphost.name.substring(0, 1).equals("R")) {
        tmpNIs = tmphost.getInterfaces();
        this.ginterfaces.addAll(tmpNIs);
      }
    }
    return this.ginterfaces;
  }

  /**
   * get all interfaces of router using special interface
   *
   * @return near interfaces
   * 
   * 
   * @time: 2021/05/23
   */
  public static HashMap<Integer, List<NetworkInterface>> getONearInterfaces() {
    return nearInterfaces;
  }

  /**
   * get index of the nearInterfaces
   *
   * @return index number
   * 
   * 
   * @time: 2021/05/23
   */
  public static int getConveyAreaNo() {
    return conveyAreaNo;
  }

  @Override
  public Collection<NetworkInterface> getNearInterfaces(NetworkInterface ni) {
    return null;
  }

}
