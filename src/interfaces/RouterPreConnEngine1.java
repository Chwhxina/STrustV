package interfaces;

import core.Connection;
import core.Coord;
import core.DTNHost;
import core.NetworkInterface;
import core.Settings;
import movement.MovementModel;
import movement.RoadRouterBelong;
import movement.map.MapNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class RouterPreConnEngine1 {

  public static String NET_INTERFACE_NAME = "preRouterInterface";
  public static final String Gap = "gap";
  private int tRange;
  private int worldSizeX;
  private int worldSizeY;
  private int cubeSize;
  private List<Double> blockSize;
  private HashMap<Integer, List<DTNHost>> blocks;

  private static List<Double> OblockSize;
  private static HashMap<Integer, List<DTNHost>> Oblocks;

  //用于记录coord和网络中继设备的映射关系
  private static Map<Coord, DTNHost> cDTNHost;

  public RouterPreConnEngine1(Settings s, List<DTNHost> DTNHosts) {
    this.tRange = movement.RouterPlacementMovement1.getTRange();
    Settings set = new Settings("MovementModel");
    int[] worldSize = set.getCsvInts(MovementModel.WORLD_SIZE, 2);
    this.worldSizeX = worldSize[0];
    this.worldSizeY = worldSize[1];
    Settings s1 = new Settings("Group");
    this.cubeSize = Integer.parseInt(s1.getSetting("cubeSize"));
    this.blocks = new HashMap<Integer, List<DTNHost>>();
    this.blockSize = new ArrayList<Double>();
    this.OblockSize = new ArrayList<Double>();
    this.Oblocks = new HashMap<Integer, List<DTNHost>>();
    RouterPreConnEngine1.cDTNHost = new HashMap<>();
    setBlockSize();
    List<DTNHost> Routers = new ArrayList<DTNHost>(this.cubeSize);
    for (DTNHost h : DTNHosts) {
			if (h.name.subSequence(0, 1).equals("R")) {
				Routers.add(h);
			}
    }
    CoordRouterRelation(Routers);
    preConnection(Routers);
    //调用路段归属类，生成路段归属映射
    @SuppressWarnings("unused")
    RoadRouterBelong rrbelong = new RoadRouterBelong(s, DTNHosts);
  }

  /**
   * Establishing connections before simulation
   *
   * 
   * 
   * @time: 2021/12/12
   */
  public void preConnection(List<DTNHost> DTNHosts) {
    //分块
    block(DTNHosts);
    //1. 小于2倍trange的路由将会被建立连接
    preConnectionNormal(DTNHosts);
    //2. 补链策略
    complementaryLinks(DTNHosts);

    this.Oblocks = this.blocks;
  }

  public void block(List<DTNHost> DTNHosts) {
    int lineLen = (int) Math.sqrt(this.cubeSize);
    List<DTNHost> tmpH = new ArrayList<DTNHost>();
		for (int i = 1; i <= this.cubeSize; i++) {
			this.blocks.put(i, tmpH);
		}
    for (DTNHost h : DTNHosts) {
      Coord hc = h.getLocation();
      int row = rowAndCol(hc, true);
      int col = rowAndCol(hc, false);
      int blockIndex = (row - 1) * lineLen + col;
      List<DTNHost> tmphs = new ArrayList<DTNHost>();
			if (this.blocks.get(blockIndex).size() > 0) {
				tmphs = this.blocks.get(blockIndex);
				tmphs.add(h);
			} else {
				tmphs.add(h);
			}
      this.blocks.put(blockIndex, tmphs);
    }
  }

  public int rowAndCol(Coord hc, boolean mode) {
    int r = -1;
    if (mode) {
      //return row no.
      r = (int) Math.ceil(hc.getY() / this.blockSize.get(1));
			if (hc.getY() == 0) {
				r = 1;
			}
    } else {
      //return col no.
      r = (int) Math.ceil(hc.getX() / this.blockSize.get(0));
			if (hc.getX() == 0) {
				r = 1;
			}
    }
    return r;
  }

  public void complementaryLinks(List<DTNHost> DTNHosts) {
    int row = 1, col = 1;
    int lineLen = (int) Math.sqrt(this.cubeSize);
    int nowPos = (row - 1) * lineLen + col;
    do {
      nowPos = (row - 1) * lineLen + col;
      //1. 补链情况一：邻居补链
      List<DTNHost> nowBlock = this.blocks.get(nowPos);
      //水平补链
      if (col != lineLen) {
        List<DTNHost> neighbors = this.blocks.get(nowPos + 1);
        neighborLink(nowBlock, neighbors, true, row, col);
      }
      //垂直补链
      if (row != lineLen) {
        List<DTNHost> neighbors = this.blocks.get(nowPos + lineLen);
        neighborLink(nowBlock, neighbors, false, row, col);
      }
      //对角补链
      if (row < lineLen && col < lineLen) {
        int nrofNowPos = this.blocks.get(nowPos).size();
        int nrofEast = this.blocks.get(nowPos + 1).size();
        int nrofSouth = this.blocks.get(nowPos + lineLen).size();
        int nrofSouthEast = this.blocks.get(nowPos + lineLen + 1).size();
        Coord mirrorPoint = new Coord(col * this.blockSize.get(0), row * this.blockSize.get(1));
        if (nrofNowPos == 0 || nrofSouthEast == 0) {
          if (nrofEast != 0 && nrofSouth != 0) {
            List<DTNHost> neighbors = this.blocks.get(nowPos + lineLen);
            List<DTNHost> diagnalHs = this.blocks.get(nowPos + 1);
            diagonalLink(diagnalHs, neighbors, mirrorPoint);
          }
        }
        if (nrofEast == 0 || nrofSouth == 0) {
          if (nrofNowPos != 0 && nrofSouthEast != 0) {
            List<DTNHost> neighbors = this.blocks.get(nowPos + lineLen + 1);
            diagonalLink(nowBlock, neighbors, mirrorPoint);
          }
        }
      }
			if (col == lineLen) {
				row++;
				col = 1;
			} else {
				col++;
			}
    } while (nowPos < this.cubeSize);
  }

  public void diagonalLink(List<DTNHost> nowBlock, List<DTNHost> diagonals, Coord mirrorPoint) {
    //对角补链仅补一条，类似跨海大桥，多补会有作弊嫌疑
    DTNHost nbNode = findClosetNode(nowBlock, mirrorPoint);
    DTNHost diaNode = findClosetNode(diagonals, mirrorPoint);
    if (!isEstablished(nbNode, diaNode)) {
      NetworkInterface nbNi = getPreConnNet(nbNode);
      NetworkInterface diaNi = getPreConnNet(diaNode);
      nbNi.connect(diaNi);
      diaNi.connect(nbNi);
    }
  }

  public void neighborLink(List<DTNHost> nowBlock, List<DTNHost> neighbors, boolean isVertical,
      int row, int col) {
    double boundary = Double.MIN_VALUE;
    Coord node1 = new Coord(-1, -1);
    Coord node2 = new Coord(-1, -1);
    if (isVertical) {
      boundary = col * this.blockSize.get(0);
      node1 = new Coord(boundary, (row - 1) * this.blockSize.get(1));
      node2 = new Coord(boundary, row * this.blockSize.get(1));
    } else {
      boundary = row * this.blockSize.get(1);
      node1 = new Coord((col - 1) * this.blockSize.get(0), boundary);
      node2 = new Coord(col * this.blockSize.get(0), boundary);
    }
    DTNHost boundaryNode = findCloseToBoundaryNode(nowBlock, boundary, isVertical);
    DTNHost hNode1 = findClosetNode(neighbors, node1);
    DTNHost hNode2 = findClosetNode(neighbors, node2);
    estabilishLinks(boundaryNode, hNode1, hNode2);
  }

  public void estabilishLinks(DTNHost h, DTNHost d1, DTNHost d2) {
    NetworkInterface hNi = getPreConnNet(h);
    NetworkInterface dNi1 = getPreConnNet(d1);
    NetworkInterface dNi2 = getPreConnNet(d2);
    List<MapNode> MapNodes = new ArrayList<MapNode>(
        movement.RouterPlacementMovement1.getRoutersLocForMap());
    if (!isEstablished(h, d1)) {
      hNi.connect(dNi1);
      dNi1.connect(hNi);
      MapNode hNode = findeMapNode(h, MapNodes);
      MapNode dNode = findeMapNode(d1, MapNodes);
      if (!hNode.equals(null) && !dNode.equals(null)) {
        hNode.addNeighbor(dNode);
        dNode.addNeighbor(hNode);
      }
    }
    if (!isEstablished(h, d2)) {
      hNi.connect(dNi2);
      dNi2.connect(hNi);
      MapNode hNode = findeMapNode(h, MapNodes);
      MapNode dNode = findeMapNode(d2, MapNodes);
      if (!hNode.equals(null) && !dNode.equals(null)) {
        hNode.addNeighbor(dNode);
        dNode.addNeighbor(hNode);
      }
    }
  }

  public MapNode findeMapNode(DTNHost host, List<MapNode> MapNodes) {
    for (MapNode mn : MapNodes) {
			if (mn.getLocation().distance(host.getLocation()) == 0) {
				return mn;
			}
    }
    return null;
  }

  public boolean isEstablished(DTNHost from, DTNHost to) {
		if (from == null || to == null) {
			return true;
		}
    Collection<Connection> Cs = from.getConnections();
    NetworkInterface NiFrom = getPreConnNet(from);
		if (!NiFrom.equals(null)) {
			for (Connection c : Cs) {
				if (c.getOtherInterface(NiFrom).getLocation().distance(to.getLocation()) == 0) {
					return true;
				}
			}
		}
    return false;
  }

  public DTNHost findCloseToBoundaryNode(List<DTNHost> hs, double boundary, boolean isVertical) {
		if (hs.isEmpty()) {
			return null;
		}
    DTNHost closeness = hs.get(0);
    double minDistance = Double.MAX_VALUE;
    for (DTNHost h : hs) {
      double tmpD = Double.MAX_VALUE;
			if (isVertical) {
				tmpD = boundary - h.getLocation().getX();
			} else {
				tmpD = boundary - h.getLocation().getY();
			}
      if (tmpD < minDistance) {
        minDistance = tmpD;
        closeness = h;
      }
    }
    return closeness;
  }

  public DTNHost findClosetNode(List<DTNHost> hs, Coord destination) {
		if (hs == null || hs.isEmpty()) {
			return null;
		}
    DTNHost closeness = hs.get(0);
    double minDistance = Double.MAX_VALUE;
    for (DTNHost h : hs) {
      double tmpD = h.getLocation().distance(destination);
      if (tmpD < minDistance) {
        minDistance = tmpD;
        closeness = h;
      }
    }
    return closeness;
  }

  public NetworkInterface getPreConnNet(DTNHost h) {
		if (h == null) {
			return null;
		}
    List<NetworkInterface> nets = h.getNets();
    for (NetworkInterface ni : nets) {
      if (ni.getInterfaceType().equals(NET_INTERFACE_NAME)) {
        return ni;
      }
    }
    return null;
  }

  public void preConnectionNormal(List<DTNHost> DTNHosts) {
    int nrofRouters = DTNHosts.size();
    List<MapNode> MapNodes = new ArrayList<MapNode>(
        movement.RouterPlacementMovement1.getRoutersLocForMap());
    for (int i = 0; i < nrofRouters; i++) {
      DTNHost h = DTNHosts.get(i);
      NetworkInterface hNet = getPreConnNet(h);
      for (int j = 1; j < nrofRouters; j++) {
        if (h.getLocation().distance(DTNHosts.get(j).getLocation()) <= this.tRange * 2) {
          NetworkInterface tmpNet = getPreConnNet(DTNHosts.get(j));
          hNet.connect(tmpNet);
          MapNode hNode = findeMapNode(h, MapNodes);
          MapNode dNode = findeMapNode(DTNHosts.get(j), MapNodes);
					if (!hNode.equals(null) && !dNode.equals(null)) {
						hNode.addNeighbor(dNode);
					}
          //					tmpNet.connect(hNet);
        }
      }
    }
  }

  public void setBlockSize() {
    int cubeLine = (int) Math.sqrt(cubeSize);
    double x = this.worldSizeX / cubeLine;
    double y = this.worldSizeY / cubeLine;
    this.blockSize.add(x);
    this.blockSize.add(y);
    this.OblockSize = this.blockSize;
  }

  /**
   * 构建coord与dtnhost的映射关系，用于支持根据coord快速查找DTNHost的功能，降低索引时间
   *
   * @param hs 网络中继节点集合
   */
  public void CoordRouterRelation(List<DTNHost> hs) {
    Iterator<DTNHost> it = hs.iterator();
    while (it.hasNext()) {
      DTNHost h = it.next();
      RouterPreConnEngine1.cDTNHost.put(h.getLocation(), h);
    }
  }


  public static List<Double> getBlockSize() {
    return OblockSize;
  }

  public HashMap<Integer, List<DTNHost>> getBlocks() {
    return this.blocks;
  }

  public static HashMap<Integer, List<DTNHost>> getBlcoks() {
    return Oblocks;
  }

  public static Map<Coord, DTNHost> getCRRelation() {
    return RouterPreConnEngine1.cDTNHost;
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
