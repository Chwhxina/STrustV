package interfaces;

import core.CBRConnection;
import core.Connection;
import core.Coord;
import core.NetworkInterface;
import core.Settings;
import movement.map.MapNode;

import java.util.List;

/**
 * Generate a pre-connection for the route
 * 
 * 
 * 
 * @time:: 2021/05/10
 * @time2: 2021/06/09
 */
public class RouterPreConnInterface1 extends NetworkInterface{
	
	/**
	 * Construct 1 
	 * @param the settings information
	 * 
	 * 
	 * 
	 * @time: 2021/05/10
	 */
	public RouterPreConnInterface1(Settings s) {
		super(s);
	}
	
	/**
	 * Copy construct 
	 * @param rpci the copied network interface object
	 * 
	 * 
	 * 
	 * @time: 2021/05/10
	 */
	public RouterPreConnInterface1(RouterPreConnInterface1 rpci) {
		super(rpci);
	}

	/**
	 * For replicating this network interface
	 * @return RouterPreConnInterface instance
	 * 
	 * 
	 * 
	 * @time: 2021/05/18
	 */
	@Override
	public NetworkInterface replicate() {
		return new RouterPreConnInterface1(this);
	}
	
	/**
	 * Tries to connect this host to another host. The other host must be
	 *  active.
	 * @param rpci the copied network interface object
	 * 
	 * 
	 * 
	 * @time: 2021/05/10
	 */
	@Override
	public void connect(NetworkInterface anotherInterface) {
		if(isScanning()
				&&anotherInterface.getHost().isMovementActive()
				&& !isConnected(anotherInterface)
				&&(this != anotherInterface)) {
			// new contact
			// connection speed synchronization
			int conSpeed = anotherInterface.getTransmitSpeed();
			if(conSpeed > this.transmitSpeed)
				conSpeed = this.transmitSpeed;
			
			Connection con = new CBRConnection(this.host, this,
					anotherInterface.getHost(), anotherInterface, conSpeed);
			connect(con, anotherInterface);
			
			//set neighbor mapNode
//			List<MapNode> mn = new ArrayList<MapNode>(movement.RouterPlacementMovement1.getRoutersLocForMap());
//			System.out.println(mn);
//			int m1 = findNode(this.getLocation(),mn);
//			int m2 = findNode(anotherInterface.getLocation(),mn);
//			mn.get(m1).addNeighbor(mn.get(m2));
//			mn.get(m2).addNeighbor(mn.get(m1));
//			interfaces.RouterPreConnEngine.setMrouterNodes(mn);
		}
	}
	
	/**
	 * Finding the same location of router and networkInterface
	 * @return the index of MapNode
	 * 
	 * 
	 * 
	 * @time: 2021/06/09
	 */
	public int findNode(Coord coord , List<MapNode> mn) {
		double x = coord.getX(), y = coord.getY();
		for(int i=0,k=mn.size();i<k;i++) {
			MapNode m = mn.get(i);
			if(x==m.getLocation().getX()&&y==m.getLocation().getY())
				return i;
		}
		return -1;
	}
	
	
	/**
	 * Updates the state of current connections (ie,. A malicious route should be disconnected
	 *  or for some other reason the route's link relationship needs to be changed )
	 * 
	 * 
	 * 
	 * @time: 2021/05/22
	 */
	@Override
	public void update() {
		// keep this part for Malicious routing link - dependent operations
		
		/** Then find new possible connections.(ie,. at this time, I use
		* the fully connection because of the small number of routers.*/
//		Collection<NetworkInterface> interfaces = optimizer.getNearInterfaces(this);
//		for(NetworkInterface i : interfaces) {
//			connect(i);
//		}
	}
	
	/**
	 * Creates a connection to another host. This method does not do any checks
	 * on whether the other node is in range or active 
	 * @param anotherInterface The interface to create the connection to
	 * 
	 * 
	 * 
	 * @time: 2021/05/25
	 */
	@Override
	public void createConnection(NetworkInterface anotherInterface) {
		if (!isConnected(anotherInterface) && (this != anotherInterface)) {    			
			// connection speed is the lower one of the two speeds 
			int conSpeed = anotherInterface.getTransmitSpeed();
			if (conSpeed > this.transmitSpeed) {
				conSpeed = this.transmitSpeed; 
			}

			Connection con = new CBRConnection(this.host, this, 
					anotherInterface.getHost(), anotherInterface, conSpeed);
			connect(con,anotherInterface);
		}
	}
	
	
	/**
	 * Returns a string representation of the object.
	 * @return a string representation of the object.
	 *
	 * 
	 * 
	 * @time: 2021/05/25
	 */
	public String toString() {
		return "RouterPreConnInterface1 "+super.toString();
	}	
}
