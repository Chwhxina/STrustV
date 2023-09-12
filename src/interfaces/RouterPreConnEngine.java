package interfaces;

import core.Coord;
import core.DTNHost;
import core.NetworkInterface;
import core.Settings;
import movement.RouterPlacementMovement;
import movement.map.MapNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RouterPreConnEngine {

  public static String NET_INTERFACE_NAME = "preRouterInterface";

  /**
   * map-based nodes for routers
   */
  public static List<MapNode> Mrouters;

  public RouterPreConnEngine(Settings s, List<DTNHost> DTNHosts) {
    Mrouters = RouterPlacementMovement.getRouterNodes();
    preConnection(DTNHosts);
  }

  /**
   * Establishing connections before simulation
   *
   * 
   * 
   * @time: 2021/05/27
   */
  public void preConnection(List<DTNHost> DTNHosts) {

    //1.获得所有network interface的集合（或者是每个块中的router集合）
    List<NetworkInterface> routersNI = new ArrayList<NetworkInterface>();
    for (DTNHost rt : DTNHosts) {
      List<NetworkInterface> nets = rt.getNets();
      for (NetworkInterface ni : nets) {
        if (ni.getInterfaceType().equals(NET_INTERFACE_NAME)) {
          routersNI.add(ni);
          break;
        }
      }
    }
    for (NetworkInterface ni : routersNI) {
      preConnectionNi(ni, DTNHosts);
    }

    //2. 处理孤立路由节点
    lonelyRouterConn(routersNI);
  }

  /**
   * Dealing with the lonely router which connects to two closest routers
   *
   * @param nis is the list of network interfaces
   * 
   * 
   * @time: 2021/05/27
   */
  public void lonelyRouterConn(List<NetworkInterface> nis) {
    NetworkInterface tmpNi1 = null, tmpNi2 = null;
    List<NetworkInterface> lonelyNis = new ArrayList<NetworkInterface>();
    for (NetworkInterface ni : nis) {
			if (ni.getConnections().isEmpty()) {
				lonelyNis.add(ni);
			}
    }
    double ni1D = Double.MAX_VALUE, ni2D = Double.MAX_VALUE;
    for (int j = 0, m = lonelyNis.size(); j < m; j++) {
      NetworkInterface ni = lonelyNis.get(j);
      ni1D = ni.getLocation().distance(nis.get(0).getLocation());
      ni2D = ni.getLocation().distance(nis.get(1).getLocation());
      if (ni1D > ni2D) {
        double tmp = ni1D;
        ni1D = ni2D;
        ni2D = tmp;
        tmpNi1 = nis.get(1);
        tmpNi2 = nis.get(0);
      } else {
        tmpNi1 = nis.get(0);
        tmpNi2 = nis.get(1);
      }
      for (int i = 0, n = nis.size(); i < n; i++) {
        if (!ni.equals(nis.get(i))) {
          double tmpD = ni.getLocation().distance(nis.get(i).getLocation());
          if (tmpD < ni1D) {
            ni2D = ni1D;
            tmpNi2 = tmpNi1;
            ni1D = tmpD;
            tmpNi1 = nis.get(i);
          } else if (tmpD < ni2D) {
            ni2D = tmpD;
            tmpNi2 = nis.get(i);
          }
          //					System.out.println(tmpNi1.getHost()+" "+ ni1D+"||"+tmpNi2.getHost()+" "+ni2D);
        }
      }
      ni.connect(tmpNi1);
      tmpNi1.connect(ni);
      ni.connect(tmpNi2);
      tmpNi2.connect(ni);
    }
  }

  /**
   * Giving the pre-Connection in this simulation
   *
   * 
   * 
   * @time: 2021/06/09
   */
  public void preConnectionNi(NetworkInterface ni, List<DTNHost> hosts) {

    ConnectivityGridRouter cgr = new ConnectivityGridRouter();
    cgr.getNearInterfaces(ni, hosts);
    HashMap<Integer, List<NetworkInterface>> nearInterfaces = cgr.getONearInterfaces();
    //		System.out.println(nearInterfaces.keySet());
    int cubeLenth = (int) Math.sqrt(RouterPlacementMovement.getOCubeSize());

    List<MapNode> routerNodes = RouterPlacementMovement.getRouterNodes();
    //		System.out.println(routerNodes);

    int areaNo = cgr.getAreaBelong(ni.getLocation());
    //		System.out.println(areaNo);
    int eastNo = areaNo + 1;
    int southNo = areaNo + cubeLenth;
    int southEastNo = southNo + 1;
    /** judge the neighbor blocks whether is valid*/
    boolean eastOut = false, southOut = false, southEastOut = false;
    //对上面三个数值进行越界判断
    int row = Math.floorDiv(areaNo, cubeLenth);
    if (areaNo % (cubeLenth) == 0) {
      eastOut = southEastOut = true;
    }
    if (row == cubeLenth - 1) {
      southOut = southEastOut = true;
    }
    /** control the number of connections between two areas*/
    int nrofAreasConn = 2;
    /** Firstly, generate the connections in the first area*/
    //		System.out.println(ni);
    //		System.out.println(ni.getHost());
    inAreaConnection(nearInterfaces, areaNo, ni);

    //		/** Secondly, deal with the connections between two areas*/
    boolean reverse = true;
    boolean cross = true;
    List<NetworkInterface> boundInterfaces = new ArrayList<NetworkInterface>();
    /** deal with the forward connections*/
    // the areaNo -> eastNo ; areaNo -> southNo
    boundInterfaces = findBoundInterfaces(nearInterfaces.get(areaNo), !reverse, !cross);
    if (boundInterfaces != null) {
			if (!eastOut) {
				areasConnection(boundInterfaces.get(0), nearInterfaces.get(eastNo), nrofAreasConn,
						cubeLenth, eastNo);
			}
			if (!southOut) {
				areasConnection(boundInterfaces.get(1), nearInterfaces.get(southNo), nrofAreasConn,
						cubeLenth, southNo);
			}
    }
    // eastNo-> areaNo ; eastNo -> southEastNo
    boundInterfaces = findBoundInterfaces(nearInterfaces.get(eastNo), !reverse, !cross);
		if (boundInterfaces != null && !southEastOut && !eastOut) {
			areasConnection(boundInterfaces.get(1), nearInterfaces.get(southEastNo), nrofAreasConn,
					cubeLenth, southEastNo);
		}
    boundInterfaces = findBoundInterfaces(nearInterfaces.get(eastNo), reverse, !cross);
		if (boundInterfaces != null && !eastOut) {
			areasConnection(boundInterfaces.get(0), nearInterfaces.get(areaNo), nrofAreasConn, cubeLenth,
					areaNo);
		}
    // southNo -> areaNo ; southNo -> southEastNo
    boundInterfaces = findBoundInterfaces(nearInterfaces.get(southNo), reverse, !cross);
		if (boundInterfaces != null && !southOut) {
			areasConnection(boundInterfaces.get(1), nearInterfaces.get(areaNo), nrofAreasConn, cubeLenth,
					areaNo);
		}
    boundInterfaces = findBoundInterfaces(nearInterfaces.get(southNo), !reverse, !cross);
		if (boundInterfaces != null && !southOut && !southEastOut) {
			areasConnection(boundInterfaces.get(0), nearInterfaces.get(southEastNo), nrofAreasConn,
					cubeLenth, southEastNo);
		}
    //southEastNo -> southNo ; southEastNo -> eastNo
    boundInterfaces = findBoundInterfaces(nearInterfaces.get(southEastNo), reverse, !cross);
    if (boundInterfaces != null && !southEastOut) {
      areasConnection(boundInterfaces.get(0), nearInterfaces.get(southNo), nrofAreasConn, cubeLenth,
          southNo);
      areasConnection(boundInterfaces.get(1), nearInterfaces.get(eastNo), nrofAreasConn, cubeLenth,
          eastNo);
    }
    //		//areaNo -> southEastNo; southEastNo -> areaNo; the distance is the shortest value which is the distance of cross interfaces between two sections
    //
    //		//southNo -> eastNo; eastNo -> southNo
    //		if(!southOut && !eastOut) {
    //			boundInterfaces = findBoundInterfaces(nearInterfaces.get(southNo),!reverse,cross);
    //
    //		}

    //		System.out.println(routerNodes);
    RouterPlacementMovement.setRouterNodesByConn(routerNodes);
    /** note: I don't consider to connect the southEastNo at this time.
     * But I give some functions to finish this function.(ie. MahalanobisDistance(a,b))*/

  }


  /**
   * Connecting the network interfaces between two areas
   *
   * @param boundInterface  is the network interface that the source network interface
   * @param areasInterfaces is a list for conveying the neighbor area's interfaces
   * @param nrofConn        is a parameter for controlling the number of connections between two
   *                        areas
   * @param cubeLenth       is used for judging the index of neighbor area whether is over the
   *                        border
   * @param pos             is the index of neighbor area
   * 
   * 
   * @time: 2021/05/27
   */
  public void areasConnection(NetworkInterface boundInterface,
      List<NetworkInterface> areasInterfaces, int nrofConn, int cubeLenth, int pos) {
    if (pos < cubeLenth * cubeLenth && areasInterfaces != null) {
      if (areasInterfaces.size() == 1) {
        boundInterface.connect(areasInterfaces.get(0));
        areasInterfaces.get(0).connect(boundInterface);
      } else if (areasInterfaces.size() == 2) {
        for (NetworkInterface ni : areasInterfaces) {
          boundInterface.connect(ni);
          ni.connect(boundInterface);
        }
      } else {
        NetworkInterface n1, n2;
        n1 = areasInterfaces.get(0);
        n2 = areasInterfaces.get(1);
        double distanceN1, distanceN2;
        distanceN1 = boundInterface.getLocation().distance(n1.getLocation());
        distanceN2 = boundInterface.getLocation().distance(n2.getLocation());
        if (distanceN1 > distanceN2) {
          NetworkInterface tmp = n1;
          double tmpd = distanceN1;
          n1 = n2;
          distanceN1 = distanceN2;
          n2 = tmp;
          distanceN2 = tmpd;
        }
        for (int i = 2, n = areasInterfaces.size(); i < n; i++) {
          NetworkInterface ni = areasInterfaces.get(i);
          double nisDistance = boundInterface.getLocation().distance(ni.getLocation());
          if (nisDistance < distanceN1) {
            n2 = n1;
            distanceN2 = distanceN1;
            n1 = ni;
            distanceN1 = nisDistance;
          } else if (nisDistance < distanceN2) {
            n2 = ni;
            distanceN2 = nisDistance;
          }
        }
        boundInterface.connect(n1);
        boundInterface.connect(n2);
        n1.connect(boundInterface);
        n2.connect(boundInterface);
      }
    }
  }


  /**
   * Finding t interfaces most closest to the east/west bound and the south/north bound in one
   * area.
   *
   * @param every network interfaces
   * @param false is finding the network interfaces close to east bound and south bound, true is
   *              finding interfaces close to west bound and north bound.
   * @return the List of network interfaces in 2 directions
   * 
   * 
   * @time: 2021/05/24
   */
  public List<NetworkInterface> findBoundInterfaces(List<NetworkInterface> nis, boolean reverse,
      boolean cross) {
    /** 1 east/west interfaces; 2 south/north interfaces */
    List<NetworkInterface> tmpNis = new ArrayList<NetworkInterface>(2);
    Coord pos1 = new Coord(-1, -1);//east/west
    Coord pos2 = new Coord(-1, -1);//south/north
    NetworkInterface ni1, ni2;
		if (nis == null) {
			return null;
		}
    int nrofNis = nis.size();
    if (nrofNis == 0) {
      return null;
    } else if (nrofNis == 1) {
      tmpNis.add(nis.get(0));
      tmpNis.add(nis.get(0));
    } else {
      pos1 = pos2 = nis.get(0).getLocation();
      ni1 = ni2 = nis.get(0);
      for (NetworkInterface ni : nis) {
        Coord tmpLoc = ni.getLocation();
        if (!reverse) {
          //find east and south
          if (findDirectionNI(tmpLoc.getX(), pos1.getX())) {
            pos1 = tmpLoc;
            ni1 = ni;
          }

          if (findDirectionNI(tmpLoc.getY(), pos2.getY())) {
            pos2 = tmpLoc;
            ni2 = ni;
          }
          //					if(MahalanobisDistance(tmpLoc, pos2,reverse)) {
          //						pos1 = pos2;
          //						ni1 = ni2;
          //						pos2 = tmpLoc;
          //						ni2 = ni;
          //						tmpNis.add(1, ni2);
          //						tmpNis.add(0, ni1);
          //					}
        } else {
          //find west and north
          if (!findDirectionNI(tmpLoc.getX(), pos1.getX())) {
            pos1 = tmpLoc;
            ni1 = ni;
          }
          if (!findDirectionNI(tmpLoc.getY(), pos2.getY())) {
            pos2 = tmpLoc;
            ni2 = ni;
          }
          //					if(!MahalanobisDistance(tmpLoc, pos2)) {
          //						pos1 = pos2;
          //						ni1 = ni2;
          //						pos2 = tmpLoc;
          //						ni2 = ni;
          //						tmpNis.add(1, ni2);
          //						tmpNis.add(0, ni1);
          //					}
        }
      }
      tmpNis.add(0, ni1);
      tmpNis.add(1, ni2);
    }
    return tmpNis;
  }

  /**
   * Judge the closet bound network interface between c1 and c2
   *
   * @param two different location's of network interfaces
   * @return true is the x or y distance of c1 larger than c2 false is the x or y distance of c1
   * smaller than c2
   * 
   * 
   * @time: 2021/05/24
   */
  public boolean findDirectionNI(double c1D, double c2D) {
    return c1D < c2D ? false : true;
  }

  /**
   * Judge the closet southEast or northWest network interface between c1 and c2
   *
   * @param two     different location's of network interfaces
   * @param boolean reverse, true, find the min-value; flase, find the max value
   * @return true is the mahalanobis distance of c1 larger than c2 false is the mahalanobis distance
   * of c1 smaller than c2
   * 
   * 
   * @time: 2021/05/24
   */
  public boolean MahalanobisDistance(Coord c1, Coord c2, boolean reverse) {
    double c1D = c1.getX() + c1.getY();
    double c2D = c2.getX() + c2.getY();
		if (!reverse) {
			return c1D < c2D ? false : true;
		}
    return c1D > c2D ? true : false;
  }


  /**
   * Connecting the router in every area(ie,. the first section will be done.)
   *
   * @param interfaces
   * @param the        index of basic area No
   * 
   * 
   * @time: 2021/05/23
   */
  public void inAreaConnection(HashMap<Integer, List<NetworkInterface>> nearInterfaces, int areaNo,
      NetworkInterface nti) {
    /** I will utilize the fully connection in one area because of the low number of router.
     * Maybe I can give a threshold value for fully connection. When the threshold value
     * is bigger than router's number, I choose the fully connection in one area. Otherwise,
     * maybe I can give a k-value to control the number of connections between routers. In
     * this condition, one problem that how to deal with the connections between two areas
     * is appearing. How to deal with the isolated figures?*/
    if (!isInAreaConnFine(nti) && nearInterfaces.containsKey(areaNo)
        && nearInterfaces.get(areaNo).size() > 1) {
      for (int i = 0, n = nearInterfaces.get(areaNo).size(); i < n; i++) {
        NetworkInterface ni = nearInterfaces.get(areaNo).get(i);
        nti.connect(ni);
        //				System.out.println(ni);
      }
    }
    //		System.out.println(nti);
  }

  public boolean isInAreaConnFine(NetworkInterface ni) {
    return ni.getConnections().isEmpty() ? false : true;
  }

  public static List<MapNode> getRouterNodes() {
    return Mrouters;
  }

  public static void setMrouterNodes(List<MapNode> mn) {
    Mrouters = mn;
  }


  /**
   * Returns a string representation of the object.
   *
   * @return a string representation of the object.
   * 
   * 
   * @time: 2021/05/25
   */
  public String toString() {
    return "RouterPreConnEngine " + super.toString();
  }

}
