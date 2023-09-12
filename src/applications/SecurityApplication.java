package applications;

import core.Application;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.ml.distance.ManhattanDistance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class SecurityApplication extends Application {

  public static final String SECURITY_APP_ID = "security.prov";
  public static final String PLT_S = "PLT";
  public static final String BD_S = "BD";
  public static final String Tdefault_S = "defaultTrust";
  public static final String Warmup_S = "warmup";
  private final int decayInterval = 1800;
  private double PLT;
  private double BD;
  private double Tmin; // Min trust value
  private double Tdefault = -1; // Default trust value
  private int warmup = 0;

  private final ManhattanDistance distance = new ManhattanDistance();

  class Evidence {
    public int sat = 1;
    public int unsat = 0;
  }

  private final Map<DTNHost, Evidence> evidenceMap = new HashMap<>();
  private final Map<DTNHost, Pair<Double, Double>> preTrust = new HashMap<>();

  public SecurityApplication(Settings s) {
    if (s.contains(PLT_S)) {
      this.PLT = s.getDouble(PLT_S);
    }
    if (s.contains(Tdefault_S)) {
      this.Tdefault = s.getDouble(Tdefault_S);
    }
    if (s.contains(Warmup_S)) {
      this.warmup = s.getInt(Warmup_S);
    }
    if (s.contains(BD_S)) {
      this.BD = s.getDouble(BD_S);
    }
    super.setAppID(SECURITY_APP_ID);
  }

  public SecurityApplication(SecurityApplication a) {
    super(a);
    this.PLT = a.PLT;
    this.Tmin = a.Tmin;
    this.Tdefault = a.Tdefault;
    this.warmup = a.warmup;
    this.BD = a.BD;
  }

  @Override
  public Message handle(Message msg, DTNHost host) {
    // check if the message is modified by a malicious host

    var path = msg.getHops();
    var lastNode = path.get(path.size() - 2);

    if (msg.isModified()) {
      // modification detected
      super.sendEventToListeners(
          "modification", StringUtils.joinWith(",", SimClock.getIntTime(), lastNode, 1), host);

      genEvidenceAndOpinion(host, lastNode, -1e5);
      return null; // drop the message
    }

    if (!msg.getFrom().equals(lastNode)) {
      genEvidenceAndOpinion(host, lastNode, 1);
    }
    return msg;
  }

  @Override
  public void update(DTNHost host) {
    int currentTime = SimClock.getIntTime();
    if (currentTime < this.warmup) {
      // trust myself
      preTrust.put(host, Pair.of(1.0, (double) currentTime));
      // trust three trams
      var world = SimScenario.getInstance().getWorld();
      preTrust.put(world.getNodeByAddress(0), Pair.of(1.0, (double) currentTime));
      preTrust.put(world.getNodeByAddress(1), Pair.of(1.0, (double) currentTime));
      preTrust.put(world.getNodeByAddress(2), Pair.of(1.0, (double) currentTime));
      return;
    }
    this.Tmin = SimScenario.getInstance().getDynamicThreshold();
    // decay trust values
    for (var o : host.getTrusts().entrySet()) {
      if (currentTime - o.getValue().getRight() > this.decayInterval) {
        double newTrust = o.getValue().getLeft() * 0.9;
        if (!(o.getValue().getLeft() >= this.Tmin && newTrust < this.Tmin)) {
          host.updateTrust(o.getKey(), newTrust, currentTime);
        }
      }
    }
  }

  @Override
  public Message beforeSending(Message msg, DTNHost host, DTNHost anotherHost) {
    // check if the message is going to a malicious host

    if (SimClock.getIntTime() < this.warmup) {
      return msg;
    }
    // if anotherHost is considered malicious, do not send
    // note: if the destination of the message is a malicious host, send it anyway
    double trust = host.getTrustForHost(anotherHost, Tdefault);
    if (!msg.getTo().equals(anotherHost) && trust != this.Tdefault && trust < Tmin) {
      return null;
    }

    return msg;
  }

  @Override
  public void onChangedConnection(Connection con, DTNHost host) {
    int currentTime = SimClock.getIntTime();
    if (currentTime < this.warmup) {
      return;
    }

    if (con.isUp()) {
      var peer = con.getOtherNode(host);

      // do not deal with RSU
      if (peer.toString().startsWith("R")) {
        return;
      }
      // detect blackhole
      // get interaction records from target host
      var irList = peer.getInteractions();
      // calculate packet loss rate
      int rec = 0, send = 0, create = 0;
      for (var ir : irList) {
        rec += ir.getReceive();
        send += ir.getSend();
        create += ir.getCreatedByMe();
      }
      if (create == 0 && rec > 0) { // balck hole attackers do not create messages
        double lossRate = (double) (rec - send + create) / rec;
        if (lossRate > PLT) {
          // black hole
          super.sendEventToListeners("blackHole",
              StringUtils.joinWith(",", currentTime, peer, lossRate), host);
          genEvidenceAndOpinion(host, peer, -10);
          return;
        }
      }
      // detect badmouth
      var peerTrusts = peer.getTrusts();
      var selfTrusts = new HashMap<>(host.getTrusts());
      // when comparing trust values, trust pre-trusted hosts
      selfTrusts.putAll(this.preTrust);

      var commonHosts = new HashSet<>(peerTrusts.keySet());
      commonHosts.retainAll(selfTrusts.keySet());

      if (commonHosts.size() > 0) {
        // preserve order
        var commonList = new ArrayList<>(commonHosts);

        double[] peerTrustsArray = new double[commonList.size()];
        double[] selfTrustsArray = new double[commonList.size()];
        for (int i = 0; i < commonList.size(); i++) {
          peerTrustsArray[i] = peerTrusts.get(commonList.get(i)).getLeft();
          selfTrustsArray[i] = selfTrusts.get(commonList.get(i)).getLeft();
        }

        var dis = distance.compute(peerTrustsArray, selfTrustsArray) / commonList.size();

        if (dis > BD) {
          super.sendEventToListeners(
              "badmouth", StringUtils.joinWith(",", currentTime, peer, dis), host);
          genEvidenceAndOpinion(host, peer, -10);
          return;
        }
      }
      genEvidenceAndOpinion(host, peer, 1);
    }
  }

  @Override
  public Application replicate() {
    return new SecurityApplication(this);
  }

  protected void genEvidenceAndOpinion(DTNHost host, DTNHost target, double value) {
    // always trust trams
    if (target.toString().startsWith("t")) {
      host.updateTrust(target, 1.0, SimClock.getTime());
      return;
    }

    Evidence evi;
    if (this.evidenceMap.containsKey(target)) {
      evi = this.evidenceMap.get(target);
      if (value < 0) {
        evi.unsat += Math.abs(value);
      } else {
        evi.sat += value;
      }
    } else {
      evi = new Evidence();
      if (value < 0) {
        evi.unsat += Math.abs(value);
      } else {
        evi.sat += value;
      }
      this.evidenceMap.put(target, evi);
    }

    var dt = (double) evi.sat / (evi.sat + evi.unsat);
    host.updateTrust(target, dt, SimClock.getTime());
  }
}
