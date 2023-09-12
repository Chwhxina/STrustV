package applications;

import core.Application;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimScenario;
import movement.RouterPlacementMovement;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;

public class RerouteApplication extends Application {
    public static final String APP_ID = "security.reroute";;

    public RerouteApplication(Settings s) {
        super.setAppID(APP_ID);
    }

    public RerouteApplication(RerouteApplication a) {
        super(a);
    }

    @Override
    public Message handle(Message msg, DTNHost host) {
        if (msg.getTo().toString().startsWith("R")) {
            if (msg.getRerouteDest() == null) {
                return msg;
            } else {
                if (msg.getTo().equals(host)) {
                    msg.setTo(msg.getRerouteDest());
                }
                return msg;
            }
        } else {
            var world = SimScenario.getInstance().getWorld();
            var routers = world.getHosts().stream().filter(e -> e.toString().startsWith("R"));
            var vehiclePath = msg.getTo().getPath();
            if (vehiclePath == null) {
                return null;
            } else {
                var coords = vehiclePath.getCoords();
                var dest = coords.get(coords.size() - 1);
                var distances = new TreeSet<Pair<DTNHost, Double>>(Comparator.comparingDouble(Pair::getRight));
                routers.forEach(e -> distances.add(Pair.of(e, e.getLocation().distance(dest))));

                var to = distances.first().getLeft();
                msg.setRerouteDest(msg.getTo());
                msg.setTo(to);
                return msg;
            }
        }
    }

    @Override
    public Application replicate() {
        return new RerouteApplication(this);
    }

}
