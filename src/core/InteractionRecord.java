package core;

public class InteractionRecord {

  private final DTNHost target;

  int send;
  int receive;
  int createdByMe;
  int createdByTarget;

  double upTime;
  double downTime;

  public InteractionRecord(DTNHost target, int send, int receive, int createdByMe,
      int createdByTarget, double upTime, double downTime) {
    this.target = target;
    this.send = send;
    this.createdByMe = createdByMe;
    this.createdByTarget = createdByTarget;
    this.receive = receive;
    this.upTime = upTime;
    this.downTime = downTime;
  }

  public InteractionRecord(DTNHost target) {
    this.target = target;
  }

  public DTNHost getTarget() {
    return target;
  }

  public int getSend() {
    return send;
  }

  public void setSend(int send) {
    this.send = send;
  }

  public int getReceive() {
    return receive;
  }

  public void setReceive(int receive) {
    this.receive = receive;
  }

  public int getCreatedByMe() {
    return createdByMe;
  }

  public void setCreatedByMe(int createdByMe) {
    this.createdByMe = createdByMe;
  }

  public int getCreatedByTarget() {
    return createdByTarget;
  }

  public void setCreatedByTarget(int createdByTarget) {
    this.createdByTarget = createdByTarget;
  }

  public double getUpTime() {
    return upTime;
  }

  public void setUpTime(double upTime) {
    this.upTime = upTime;
  }

  public double getDownTime() {
    return downTime;
  }

  public void setDownTime(double downTime) {
    this.downTime = downTime;
  }
}
