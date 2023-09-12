package applications;

import core.Application;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

public class BadmouthApplication extends Application {
  public static final String BADMOUTH_APP_ID = "attacker.Badmouth";

  public BadmouthApplication(Settings s) {
    super.setAppID(BADMOUTH_APP_ID);
  }

  public BadmouthApplication(Application app) {
    super(app);
  }


  @Override
  public void onChangedConnection(Connection con, DTNHost host) {
    if (con.isUp()) {
      var peer = con.getOtherNode(host);
      if (peer.toString().startsWith("R")) {
        return;
      }
      if (peer.toString().charAt(0) == host.toString().charAt(0)) {
        host.updateTrust(peer, 0.8, SimClock.getTime());
      } else {
        host.updateTrust(peer, 0.2, SimClock.getTime());
      }
    }
  }

  @Override
  public Application replicate() {
    return new BadmouthApplication(this);
  }
}
