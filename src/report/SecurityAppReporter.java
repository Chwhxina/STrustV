/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */

package report;

import applications.SecurityApplication;
import core.Application;
import core.ApplicationListener;
import core.DTNHost;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class DetectionRecord {
  private final String detector;
  private final String attacker;
  private final String time;
  private final String type;
  private final String value;

  public DetectionRecord(String detector, String attacker, String time, String type, String value) {
    this.detector = detector;
    this.attacker = attacker;
    this.time = time;
    this.type = type;
    this.value = value;
  }

  @Override
  public String toString() {
    return StringUtils.join(new String[] {detector, attacker, time, type, value}, ",");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DetectionRecord that = (DetectionRecord) o;
    return detector.equals(that.detector)
        && attacker.equals(that.attacker)
        && time.equals(that.time)
        && type.equals(that.type)
        && value.equals(that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(detector, attacker, time, type, value);
  }
}

public class SecurityAppReporter extends Report implements ApplicationListener {

  private final Set<String> hosts = new HashSet<>();
  private final Set<DetectionRecord> drs = new HashSet<>();

  public void gotEvent(String event, Object params, Application app, DTNHost host) {
    // Check that the event is sent by correct application type
    if (!(app instanceof SecurityApplication)) return;

    var r = StringUtils.split((String) params, ",");
    hosts.add(r[1]);
    drs.add(new DetectionRecord(host.toString(), r[1], r[0], event, r[2]));
  }

  @Override
  public void done() {
    write(
        "Security stats for scenario " + getScenarioName() + "\nsim_time: " + format(getSimTime()));

    StringBuilder statsText =
        new StringBuilder(
            "total detected: " + this.hosts.size() + "\n" + "detected hosts: " + "\n");
    statsText.append("detector,attacker,time,type,value\n");
    for (DetectionRecord dr : drs) {
      statsText.append(dr.toString()).append("\n");
    }
    write(statsText.toString());
    super.done();
  }
}
