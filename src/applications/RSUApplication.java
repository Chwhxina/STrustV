package applications;

import core.Application;
import core.DTNHost;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.FastMath;
import org.jgrapht.Graphs;
import org.jgrapht.alg.scoring.EigenvectorCentrality;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import util.WeightedJaccardSimilarity;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class RSUApplication extends Application {

  public static final String INTERVAL = "interval";
  public static final String HOST_COUNT = "nrofHosts";
  public static final String APP_ID = "security.RSUApplication";
  private SimpleDirectedWeightedGraph<DTNHost, DefaultWeightedEdge> trustGraph;
  private final SimpleDirectedWeightedGraph<DTNHost, DefaultWeightedEdge> socialGraph;
  private int interval;
  private int hostCount;
  private boolean once;

  public RSUApplication(Settings s) {
    if (s.contains(INTERVAL)) {
      this.interval = s.getInt(INTERVAL);
    }
    if (s.contains(HOST_COUNT)) {
      this.hostCount = s.getInt(HOST_COUNT);
    }
    super.setAppID(APP_ID);
    this.trustGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    this.socialGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    this.once = false;
  }

  public RSUApplication(RSUApplication a) {
    super(a);
    this.interval = a.interval;
    this.hostCount = a.hostCount;
    this.trustGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    this.socialGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    this.once = a.once;
  }

  @Override
  public Application replicate() {
    return new RSUApplication(this);
  }

  @Override
  public void update(DTNHost host) {
    var currentTime = SimClock.getIntTime();
    if ((currentTime + 1) % this.interval == 0 && !once) {
      // Somehow this method may be called twice in 1 second simulation time
      // although Scenario.updateInterval is set to 1
      once = true;
      var contactList = host.getNeighborsByInterface("V2R");
      if (contactList.size() > 2) {
        // do not aggregate trusts if there are too few hosts in the range
        var trust = eigenWeightedTrust();
//        this.trustGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (var t : trust.entrySet()) {
          host.updateTrust(t.getKey(), t.getValue(), currentTime);

        }
      }
      // exchange trust values between RSUs
      contactList = host.getNeighborsByInterface("R2R");
      var tmpTrust = new HashMap<DTNHost, Pair<Double, Integer>>();
      for (var c : contactList) {
        c.getTrusts().forEach((k, v) -> {
              tmpTrust.computeIfAbsent(k, key -> Pair.of(v.getLeft(), 1));
              tmpTrust.computeIfPresent(k,
                  (key, value) -> Pair.of(value.getLeft() + v.getLeft(), value.getRight() + 1));
            }
        );
      }
      tmpTrust.forEach((k, v) -> {
            double avg = v.getLeft() / v.getRight();
            host.getTrusts()
                .computeIfAbsent(k, key -> Pair.of(avg, (double) currentTime));
            host.getTrusts().computeIfPresent(k,
                (key, value) -> Pair.of(0.2 * avg + 0.8 * value.getLeft(),
                    (double) currentTime));
          }
      );
    } else {
      once = false;
      collectInfo(host);
    }
    if (currentTime == SimScenario.getInstance().getEndTime() && host.toString().equals("R17")) {
      System.out.println(host);
      double threshold = SimScenario.getInstance().getDynamicThreshold();
      var mhs =
          host.getTrusts().entrySet().stream()
              .filter(e -> e.getValue().getLeft() < threshold)
              .collect(Collectors.toList());
      for (var m : mhs) {
        System.out.println(m.getKey());
      }
    }
  }

  private void collectInfo(DTNHost host) {
    var contactList =
        host.getConnectionsByInterface("V2R").stream().map(c -> c.getOtherNode(host))
            .collect(Collectors.toList());
    for (var c : contactList) {
      this.trustGraph.addVertex(c);
      var opinions = c.getTrusts().entrySet();
      for (var t : opinions) {
        var e = Graphs.addEdgeWithVertices(this.trustGraph, c, t.getKey());
        if (e == null) {
          e = this.trustGraph.getEdge(c, t.getKey());
        }
        this.trustGraph.setEdgeWeight(e, t.getValue().getLeft());
      }
      var socialMetric = c.getSocial().entrySet();
      for (var t : socialMetric) {
        var e = Graphs.addEdgeWithVertices(this.socialGraph, c, t.getKey());
        if (e == null) {
          e = this.socialGraph.getEdge(c, t.getKey());
        }
        this.socialGraph.setEdgeWeight(e, t.getValue());
      }
    }
  }

  private SimpleDirectedWeightedGraph<DTNHost, DefaultWeightedEdge> deepCopy(
      SimpleDirectedWeightedGraph<DTNHost, DefaultWeightedEdge> g) {
    var copy =
        new SimpleDirectedWeightedGraph<DTNHost, DefaultWeightedEdge>(DefaultWeightedEdge.class);
    for (var e : g.edgeSet()) {
      Graphs.addEdgeWithVertices(copy, g.getEdgeSource(e), g.getEdgeTarget(e), g.getEdgeWeight(e));
    }
    return copy;
  }

  private Map<DTNHost, Double> eigenWeightedTrust() {
    var eigen = new EigenvectorCentrality<>(this.trustGraph).getScores();
    var weightGraph = deepCopy(this.trustGraph);
    for (var e : weightGraph.edgeSet()) {
      var src = weightGraph.getEdgeSource(e);
      var tgt = weightGraph.getEdgeTarget(e);
      var socialWeight = 0.0;
      if (this.socialGraph.containsEdge(src, tgt)) {
        var se = this.socialGraph.getEdge(src, tgt);
        socialWeight = this.socialGraph.getEdgeWeight(se);
      } else if (this.socialGraph.containsVertex(src) && this.socialGraph.containsVertex(tgt)) {
        socialWeight = new WeightedJaccardSimilarity<>(this.socialGraph).predict(src, tgt);
      } else if (this.socialGraph.containsVertex(tgt)) {
        var we = this.socialGraph.incomingEdgesOf(tgt);
        if (we.size() > 0) {
          socialWeight = we.stream().mapToDouble(this.socialGraph::getEdgeWeight).sum();
          socialWeight = socialWeight / we.size();
        }
      }

      if (socialWeight == 0) {
        socialWeight = 0.5;
      }
      var w = 0.5 * eigen.get(src) + 0.5 * socialWeight;
      weightGraph.setEdgeWeight(e, w * weightGraph.getEdgeWeight(e));
    }

    var ret = new HashMap<DTNHost, Double>();
    // compute trust for each v
    for (var v : this.trustGraph.vertexSet()) {
      var trusts = this.trustGraph.incomingEdgesOf(v);
      if (trusts.size() > 0) {
        var tv = new ArrayRealVector(); // each trust value
        var tw = new ArrayRealVector(); // corresponding weight
        for (var e : trusts) {
          var src = this.trustGraph.getEdgeSource(e);
          var tgt = this.trustGraph.getEdgeTarget(e);
          double weight = weightGraph.getEdgeWeight(weightGraph.getEdge(src, tgt));
          if (weight > 0) {
            tw = (ArrayRealVector) tw.append(weight);
            tv = (ArrayRealVector) tv.append(this.trustGraph.getEdgeWeight(e));
          }
        }
        if (tw.getDimension() > 0) {
          tw = (ArrayRealVector) softmax(tw);
          var t = tv.ebeMultiply(tw).getL1Norm();
          ret.put(v, t);
        }
      }
    }
    return ret;
  }

  public RealVector softmax(RealVector logit) {
    var exp = logit.map(FastMath::exp).map(x -> FastMath.pow(x, 2));
    //    var exp = logit.mapSubtract(logit.getMaxValue()).map(FastMath::exp);
    var sum = exp.getL1Norm();
    if (sum == 0) {
      return logit;
    } else {
      return exp.mapDivide(sum);
    }
  }

  private RealVector scale(RealVector v) {
    double delta = v.getMaxValue() - v.getMinValue();
    if (delta != 0) {
      return v.mapAdd(-v.getMinValue()).mapDivide(delta);
    } else {
      return v;
    }
  }
}
