package movement;

import core.Coord;
import core.DTNHost;
import core.Settings;
import interfaces.RouterPreConnEngine1;
import movement.map.MapNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Set the relationship between road points and routers.
 * 为了支持车辆路径过程中，所通过的网络中继设备的集合，通过路段归属的方法，
 * 产生 key=路段point value=Set<DTNHost>的映射关系
 * 
 * 
 * 
 * @time:: 2022/04/13
 * @time2: 2022/04/13
 */
public class RoadRouterBelong  extends MapBasedMovement{
	public static String NET_INTERFACE_NAME = "RoadRouterBelong";
	private int[][] bounds = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,0},{0,1},{1,-1},{1,0},{1,1}};
	private int cubeSize;
	private List<Double> blockSizes;
	private static Map<Coord,HashSet<DTNHost>> belongRelations;
	
	public RoadRouterBelong(Settings s, List<DTNHost> DTNHosts) {
		super(s);
		Settings s1 = new Settings("Group");
		this.cubeSize = Integer.parseInt(s1.getSetting("cubeSize"));
		RoadRouterBelong.belongRelations = new HashMap<Coord,HashSet<DTNHost>>();
		start();
	}
	/**
	 * 启动函数，用于初始化 道路点-网络中继设备 的映射关系
	 */
	public void start() {
		List<MapNode> mapNodes = map.getNodes();
		blockSizes = RouterPreConnEngine1.getBlockSize();
		//需要在RouterPreConnEngine1类执行之后再运行本类
		HashMap<Integer,List<DTNHost>> blocks = RouterPreConnEngine1.getBlcoks();
		int lineLen = (int) Math.sqrt(cubeSize);
		for(MapNode mp : mapNodes) {
			//首先判断该地图节点属于哪个block
			int row = rowAndCol(mp.getLocation(),true);
			int col = rowAndCol(mp.getLocation(),false);
			int index = -1;
			//再获取周围包括自己所在的几个blocks [4-9]的网络中继设备的位置
			for(int i=0,n=bounds.length;i<n;++i) {
				row += bounds[i][0];
				col += bounds[i][1];
				//超出block的范围
				if(row<0||col<0||row>=blockSizes.get(0)||col>=blockSizes.get(1))
					continue;
//				index = (row-1+bounds[i][0])*lineLen+col+bounds[i][1];
				index = (row-1)*lineLen+col;
				if(!blocks.containsKey(index))
					continue;
				List<DTNHost> routers = blocks.get(index);
				//遍历属于当前block中的网络中继设备，并确定归属关系
				Iterator<DTNHost> rs = routers.iterator();
				while(rs.hasNext()) {
					DTNHost ht = rs.next();
					if(ht.getLocation().distance(mp.getLocation())<=200) {
						if(RoadRouterBelong.belongRelations.containsKey(mp.getLocation())
								&& !RoadRouterBelong.belongRelations.get(mp.getLocation()).contains(ht)) {
							RoadRouterBelong.belongRelations.get(mp.getLocation()).add(ht);
						}else {
							RoadRouterBelong.belongRelations.put(mp.getLocation(), new HashSet<DTNHost>());
							RoadRouterBelong.belongRelations.get(mp.getLocation()).add(ht);
						}
					}
				}
			}
		}
	}
	/**
	 * 根据坐标计算其所属分块的行/列下标
	 * @param hc 网络中继设备的坐标
	 * @param mode true：行；false：列
	 * @return
	 */
	public int rowAndCol(Coord hc, boolean mode) {
		int r=-1;
		if(mode) {
			//return row no.
			r = (int) Math.ceil(hc.getY()/this.blockSizes.get(1));
			if(hc.getY()==0)
				r=1;
		}else {
			//return col no.
			r = (int) Math.ceil(hc.getX()/this.blockSizes.get(0));
			if(hc.getX()==0)
				r=1;
		}
		return r;
	}
	/**
	 * 获得路段-网络中继节点的映射关系
	 * @return 路段-网络中继节点映射关系
	 */
	public static Map<Coord,HashSet<DTNHost>> getBelongRelations(){
		return RoadRouterBelong.belongRelations;
	}
	
	public String toString() {
		return "Road-Router belong"+super.toString();
	}
}
