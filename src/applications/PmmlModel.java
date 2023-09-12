package applications;

import org.jpmml.evaluator.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PmmlModel {

  private final String modelPath;
  private final Evaluator evaluator;
  public PmmlModel(String modelPath) {
    this.modelPath = modelPath;
    try {
      this.evaluator = new LoadingModelEvaluatorBuilder()
              .load(new File(modelPath))
              .build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    this.evaluator.verify();
  }

  public Set<String> getInputFieldNames() {
    var inputFields = this.evaluator.getInputFields();
    return inputFields.stream().map(ModelField::getName).collect(Collectors.toSet());
  }

  public Set<String> getTargetFieldNames() {
    var targetFields = this.evaluator.getTargetFields();
    return targetFields.stream().map(ModelField::getName).collect(Collectors.toSet());
  }

  public List<InputField> getInputFields() {
    return this.evaluator.getInputFields();
  }
  public Double evaluate(Map<String, ?> data, String target) {
    var inputFields = this.evaluator.getInputFields();
    var arguments = new HashMap<String, FieldValue>();
    for (var inputField : inputFields) {
      var inputName = inputField.getName();
      var inputValue = inputField.prepare(data.get(inputName));

      arguments.put(inputName, inputValue);
    }
    var result = (Computable) this.evaluator.evaluate(arguments).get(target);
    return (double) result.getResult();
  }
  public String getModelPath() {
    return modelPath;
  }
}
