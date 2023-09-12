package interfaces;

import core.CBRConnection;
import core.Connection;
import core.NetworkInterface;
import core.Settings;

/**
 * Interface for vehicle to router
 *
 * 
 * 
 */
public class STALBVehicleToRouterInterface extends SimpleBroadcastInterface {

  public STALBVehicleToRouterInterface(Settings s) {
    super(s);
    // TODO Auto-generated constructor stub
  }

  public STALBVehicleToRouterInterface(STALBVehicleToRouterInterface ni) {
    super(ni);
  }

  public NetworkInterface replicate() {
    return new STALBVehicleToRouterInterface(this);
  }


  @Override
  public void connect(NetworkInterface anotherInterface) {
    if (isScanning()
        && anotherInterface.getHost().isMovementActive()
        && isWithinRange(anotherInterface)
        && !isConnected(anotherInterface)
        && (this != anotherInterface)
        && isRouter(anotherInterface)) {
      // new contact within range
      // connection speed is the lower one of the two speeds
      int conSpeed = anotherInterface.getTransmitSpeed();
      if (conSpeed > this.transmitSpeed) {
        conSpeed = this.transmitSpeed;
      }

      Connection con = new CBRConnection(this.host, this,
          anotherInterface.getHost(), anotherInterface, conSpeed);
      connect(con, anotherInterface);
    }
  }

  public boolean isRouter(NetworkInterface anotherInterface) {
    //		System.out.println(anotherInterface);
		if (anotherInterface.getHost().name.charAt(0) == 'R') {
			return true;
		}

    return false;
  }

  public String toString() {
    return "MultipathTrajectoryVehiclesToRouter " + super.toString();
  }

}
