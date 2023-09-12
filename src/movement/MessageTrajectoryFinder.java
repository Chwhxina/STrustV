package movement;

import core.Coord;
import core.DTNHost;
import core.NetworkInterface;
import core.Settings;
import core.SimScenario;
import movement.map.DijkstraPathFinder;
import movement.map.MapNode;

import java.util.ArrayList;
import java.util.List;

/**
 * For trajectory-based messages finding the shortest path in routers' network
 *
 * 
 * 
 * @time1: 2021/07/05
 * @time2: 2021/07/05
 */
public class MessageTrajectoryFinder extends MapBasedMovement implements SwitchableMovement {

  private DijkstraPathFinder pathFinder;
  private List<DTNHost> trl;

  public MessageTrajectoryFinder(Settings settings) {
    super(settings);
    this.pathFinder = new DijkstraPathFinder(null);
  }

  public Path getPath(DTNHost from, DTNHost to, boolean isOld) {
    Path p = new Path();
    List<MapNode> mp = new ArrayList<MapNode>();
		if (isOld) {
			mp = new ArrayList<MapNode>(RouterPlacementMovement.getRouterNodes());
		} else {
			mp = new ArrayList<MapNode>(RouterPlacementMovement1.getRoutersLocForMap());
		}
    List<DTNHost> hosts = SimScenario.getOHosts();

    MapNode To = null, From = null;

    for (MapNode mn : mp) {
			if (mn.getLocation().getX() == to.getLocation().getX()
					&& mn.getLocation().getY() == to.getLocation().getY()) {
				To = mn;
			} else if (mn.getLocation().getX() == from.getLocation().getX()
					&& mn.getLocation().getY() == from.getLocation().getY()) {
				From = mn;
			}
    }
		if (To == null || From == null) {
			return null;
		}
    List<MapNode> nodePath = pathFinder.getShortestPath(From, To);

    // this assertion should never fire if the map is checked in read phase
    assert nodePath.size() > 0 : "No path from " + lastMapNode + " to " +
        to + ". The simulation map isn't fully connected";

    List<DTNHost> tr = findTrajectoryRouters(nodePath, hosts);
    //		System.out.println(tr);
    this.trl = new ArrayList<DTNHost>(tr);
    int trCount = 0;
    for (MapNode node : nodePath) { // create a Path from the shortest path
      //添加的是路由预连接的网络接口参数
      NetworkInterface ni = null;
      //			System.out.println(tr.get(trCount).getNets());
      for (NetworkInterface nii : tr.get(trCount).getNets()) {
				if (nii.getInterfaceType().equals("preRouterInterface")) {
					ni = nii;
				}
      }
      assert ni != null;
      p.addWaypoint(node.getLocation(), ni.getTransmitSpeed());
    }
    lastMapNode = To;
    return p;
  }

  public List<DTNHost> findTrajectoryRouters(List<MapNode> mp, List<DTNHost> hosts) {
    List<DTNHost> routers = new ArrayList<DTNHost>();
    for (MapNode n : mp) {
      Coord c = n.getLocation();
      for (DTNHost h : hosts) {
        if (h.name.startsWith("R") && c.getX() == h.getLocation().getX() && c.getY() == h.getLocation().getY()) {
          routers.add(h);
          break;
        }
      }
    }
    return routers;
  }

  public List<DTNHost> getTRList() {
    return this.trl;
  }

}
