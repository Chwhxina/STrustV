package util;

import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.LinkPredictionAlgorithm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class WeightedJaccardSimilarity<V, E> implements LinkPredictionAlgorithm<V, E> {
  private final Graph<V, E> graph;

  /**
   * Create a new prediction
   *
   * @param graph the input graph
   */
  public WeightedJaccardSimilarity(Graph<V, E> graph) {
    this.graph = Objects.requireNonNull(graph);
  }

  @Override
  public double predict(V u, V v) {
    if (u.equals(v)) {
      return 1.0;
    }

    Set<E> eu = graph.outgoingEdgesOf(u);
    Set<E> ev = graph.outgoingEdgesOf(v);
    Set<V> gu = new HashSet<>();
    Set<V> gv = new HashSet<>();
    Map<V, Double> wu = new HashMap<>();
    Map<V, Double> wv = new HashMap<>();

    for (E e : eu) {
      V t = graph.getEdgeTarget(e);
      gu.add(t);
      wu.put(t, graph.getEdgeWeight(e));
    }
    for (E e : ev) {
      V t = graph.getEdgeTarget(e);
      gv.add(t);
      wv.put(t, graph.getEdgeWeight(e));
    }
//    List<V> gu = Graphs.successorListOf(graph, u);
//    List<V> gv = Graphs.successorListOf(graph, v);

    Map<V, Double> unionMap = new HashMap<>(wu);
    wv.forEach((key, value) -> unionMap.merge(key, value, Math::min));

    Set<V> union = unionMap.keySet();
    if (union.isEmpty()) {
      return 0.0;
    }

    Set<V> intersection = new HashSet<>(gu);
    intersection.retainAll(gv);

    double weightedInter = 0.0;
    double weightedUnion = 0.0;

    for (V n : intersection) {
      weightedInter += unionMap.get(n);
    }
    for (V n : union) {
      weightedUnion += unionMap.get(n);
    }

    return weightedInter / weightedUnion;
  }
}
