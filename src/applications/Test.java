package applications;

import org.jpmml.evaluator.Computable;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.mining.MiningModelEvaluator;

import java.util.HashMap;

public class Test {

  public static void main(String[] args) {
    var model = new PmmlModel("target\\model\\RF.pmml");
    System.out.println(model.getInputFieldNames());

    var map = new HashMap<String, Double>();
    map.put("Input", 12.0);
    map.put("Output", 10.0);
    map.put("Replay_ratio", 0.8333);
    map.put("Delivery_count", 8.0);
    map.put("overhead", 0.44);
    map.put("avgTime", 1.01);
    map.put("avgBuffer", 0.0);

    var res =  model.evaluate(map, "y");

    System.out.println(res);
  }
}
