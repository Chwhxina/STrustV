package applications;

import core.Application;
import core.Connection;
import core.DTNHost;
import core.Settings;
import core.SimClock;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import util.PathDistance;
import util.WeightedJaccardSimilarity;

public class SocialApplication extends Application {

  public static final String SOCIAL_APP_ID = "security.social";
  public static final String SFT_S = "SFT";
  public static final String Warmup_S = "warmup";
  public static final String PathSim_S = "PST";
  private double SFT;
  private int warmup = 0;
  private double pathSim = 0.5; // Path Sim Threshold

  public SocialApplication(Settings s) {
    if (s.contains(SFT_S)) {
      this.SFT = s.getDouble(SFT_S);
    }
    if (s.contains(Warmup_S)) {
      this.warmup = s.getInt(Warmup_S);
    }
    if (s.contains(PathSim_S)) {
      this.pathSim = s.getDouble(PathSim_S);
    }
    super.setAppID(SOCIAL_APP_ID);
  }

  public SocialApplication(SocialApplication a) {
    super(a);
    this.SFT = a.SFT;
    this.warmup = a.warmup;
    this.pathSim = a.pathSim;
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
      // calculate social metric
      var ps = 0.0; // path sim
      var is = 0.0; // interaction sim
      var ds = 0.0; // direct sim
      var s = 0.0;

      var peerPath = peer.getPath();
      var hostPath = host.getPath();
      if (peerPath != null && hostPath != null) {
        ps = PathDistance.lcs(peerPath.getCoords(), hostPath.getCoords());
      }

      var interGraph =
          new SimpleDirectedWeightedGraph<DTNHost, DefaultWeightedEdge>(DefaultWeightedEdge.class);
      populateInterGraph(host, interGraph);
      populateInterGraph(peer, interGraph);
      var jaccard = new WeightedJaccardSimilarity<>(interGraph);
      is = jaccard.predict(host, peer);

      ds = this.SFT * ps + (1 - this.SFT) * is;

      var neighbors = host.getNeighborsByInterface("V2V");
      var rs = 0.0;
      var count = 0;
      for (var neighbor : neighbors) {
        if (host.getSocial().containsKey(neighbor) && neighbor.getSocial().containsKey(peer)) {
          rs += host.getSocial().get(neighbor) * neighbor.getSocial().get(peer);
          count++;
        }
      }
      if (count > 0) {
        rs /= count;
        s = this.SFT * rs + (1 - this.SFT) * ds;
      } else {
        s = ds;
      }
      host.getSocial().put(peer, s);
    }
  }

  public void populateInterGraph(
      DTNHost host, SimpleDirectedWeightedGraph<DTNHost, DefaultWeightedEdge> interGraph) {
    interGraph.addVertex(host);
    var interactions = host.getInteractions();
    for (var i : interactions) {
      var e = Graphs.addEdgeWithVertices(interGraph, host, i.getTarget());
      if (e == null) {
        e = interGraph.getEdge(host, i.getTarget());
      }
      var w = interGraph.getEdgeWeight(e);
      interGraph.setEdgeWeight(e, w + i.getSend() + i.getReceive());
    }
  }

  @Override
  public Application replicate() {
    return new SocialApplication(this);
  }
}
