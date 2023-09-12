package applications;

import core.Application;
import core.DTNHost;
import core.Message;
import core.Settings;

public class ModificationApplication extends Application {
  public static final String MODIFICATION_APP_ID = "attacker.Modification";

  public ModificationApplication(Settings s) {
    super.setAppID(MODIFICATION_APP_ID);
  }

  public ModificationApplication(ModificationApplication a) {
    super(a);
  }

  @Override
  public Message handle(Message msg, DTNHost host) {
    msg.setModified(true);
    return msg;
  }

  @Override
  public Application replicate() {
    return new ModificationApplication(this);
  }
}
