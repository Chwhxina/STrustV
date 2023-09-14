/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import core.Application;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SimClock;
import core.SimError;
import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.FunctionBlock;
import net.sourceforge.jFuzzyLogic.Gpr;
import net.sourceforge.jFuzzyLogic.rule.Variable;
import util.Tuple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MyRouter extends ProphetRouter {

  public static final String ENERGY_THS = "EnergyThs";

  protected double energyThs = 0.9;  //能源阈值

  /** 贡献表和消费表 */
  public Map<DTNHost, Double> contributionTab;   //贡献表
  public Map<DTNHost, Double> comsumptionTab;    //消费表
  protected Map<DTNHost, Double> reputationTab;  //声誉表
  protected Map<DTNHost, Double> preRepsTab;     //综合度量
  protected boolean initRep;
  /** 初始preRep */
  private final double preRepInit = 1;

  public MyRouter(Settings s) {
    super(s);
    Settings MyRoutings = new Settings("MyRouter");
    comsumptionTab = new HashMap<DTNHost, Double>();
    contributionTab = new HashMap<DTNHost, Double>();
    reputationTab = new HashMap<DTNHost, Double>();
    preRepsTab = new HashMap<>();
    initRep = false;
  }

  public MyRouter(MyRouter r) {
    super(r);
    //各种表
    this.comsumptionTab = r.comsumptionTab;
    this.contributionTab = r.contributionTab;
    this.reputationTab = new HashMap<>();
    this.preRepsTab = new HashMap<>();
    initRep = r.initRep;

    //能量阈值
    this.energyThs = r.energyThs;
  }

  @Override
  public void update(){
    super.getupdate();

    //之后尝试转发
    if (!this.canStartTransfer() || this.isTransferring()) {
      return;
    }

    // 尝试能不能转发到目的节点
    if (this.exchangeDeliverableMessages() != null) {
      return;
    }

    //尝试转发消息
    this.tryOtherMessages();
  }

  //获取自己的声誉信息
  public double getSelfCon() {
    if(!initRep){
      contributionTab.put(this.getHost(), 1.0);
      comsumptionTab.put(this.getHost(), 1.0);
      initRep = true;
    }
    return contributionTab.get(this.getHost());
  }

  /**
   * 尝试转发消息
   * @return 消息-连接对
   */
  protected Tuple<Message, Connection> tryOtherMessages() {
    List<Tuple<Message, Connection>> messages = new ArrayList<>();
    Collection<Message> msgCollection = this.getMessageCollection();

    /* for all connected hosts collect all messages that have a higher
    probability of delivery by the other host */
    for (Connection con : this.getConnections()) {
      DTNHost other = con.getOtherNode(this.getHost());
      MyRouter othRouter = (MyRouter) other.getRouter();
      if (othRouter.isTransferring()) {
        continue; // skip hosts that are transferring
      }
      for (Message m : msgCollection) {
        if (othRouter.hasMessage(m.getId())) {
          continue; // skip messages that the other one has
        }

        if (othRouter.getPreRep(m.getTo()) > this.getPreRep(m.getTo())) {
          // 若P(b, c)大于P(a, c)，那么就将消息m复制转发给B
          messages.add(new Tuple<>(m, con));
        }
      }
    }
    if (messages.size() == 0) {
      return null;
    }
    // sort the message-connection tuples
    Collections.sort(messages, new TupleComparator());
    return this.tryMessagesForConnected(messages); // try to send messages
  }

  /**
   * 获取真实转发概率
   * @param host 节点
   * @return 声誉叠加概率
   */
  protected double getPreRep(DTNHost host) {
    if(this.preRepsTab.containsKey(host))
      return this.preRepsTab.get(host);
    this.preRepsTab.put(host, preRepInit);
    return 1;
  }


  @Override
  public void changedConnection(Connection con) {
    super.changedConnection(con);
    double preRepOther;
    if(con.isUp()) {
      DTNHost otherHost = con.getOtherNode(this.getHost());
      super.updateDeliveryPredFor(otherHost);
      updateRep(this.getHost().getNeighbors());
      //接下来更新preRep表
      double repFactor = this.getRep(otherHost);
      double energyPart = (((MyRouter)otherHost.getRouter()).energy.getEnergy() / 40000);
      double otherFactor = 0.3 * stableScore(otherHost) + 0.5 * energyPart
          + 0.2 * (1.0-(otherHost.getBufferOccupancy()/100.0));

      preRepOther = fuzzyLogic(repFactor, otherFactor, preds.get(otherHost));
      //System.out.println("neighbour:"+this.getHost().getNeighbors().size() + " rep" + this.getRep(otherHost) + " oth:"+otherFactor + " pred:"+preds.get(otherHost) + " res:"+preRepOther);
      this.preRepsTab.put(otherHost, preRepOther);

      //传递概率
      updateTransitivePreds(otherHost);

      //传递声誉概率
      updateTransiveProPreds(otherHost);
    }
  }

  protected Map<DTNHost, Double> getPreRepsTab() {
    return this.preRepsTab;
  }

  protected void updateTransiveProPreds(DTNHost host) {
    MessageRouter otherRouter = host.getRouter();
    assert otherRouter instanceof MyRouter
        : "MyRouter only works " + " with other routers of same type";
    double pRForHost = this.getRep(host);
    Map<DTNHost, Double> othersPreds = ((MyRouter) otherRouter).getReputationTab();
    for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
      if (e.getKey() == this.getHost()) {
        continue; // don't add yourself
      }
      double pOld = this.getRep(e.getKey());
      double pNew = pOld + (1 - pOld) * pRForHost * e.getValue() * this.beta;
      this.reputationTab.put(e.getKey(), pNew);
    }
  }

  /**
   * 获取节点的声誉信息
   * @param host 节点
   * @return 声誉值
   */
  protected double getRep(DTNHost host) {
    if(reputationTab.containsKey(host))
      return reputationTab.get(host);
    if(comsumptionTab.containsKey(host) && contributionTab.containsKey(host)) {
      double t = contributionTab.get(host) / (contributionTab.get(host) + comsumptionTab.get(host));
      reputationTab.put(host, t);
      return t;
    }
    contributionTab.put(host, ((MyRouter)host.getRouter()).getSelfCon());
    comsumptionTab.put(host, ((MyRouter)host.getRouter()).getSelfCon());
    return contributionTab.get(host) / (contributionTab.get(host) + comsumptionTab.get(host));
  }

  protected Map<DTNHost, Double> getReputationTab() {
    return this.reputationTab;
  }

  protected ArrayList<Connection> getSending() {
    return this.sendingConnections;
  }

  /**
   * 更新节点声誉信息
   * @param neighbourHost 邻居节点
   */
  protected void updateRep(List<DTNHost> neighbourHost) {
    double aContribution = 1;
    double aComsumption = 1;
    for (var mHost : neighbourHost) {
      if (contributionTab.containsKey(mHost))
        aContribution = contributionTab.get(mHost);
      else {
        contributionTab.put(mHost, ((MyRouter) mHost.getRouter()).getSelfCon());
      }
      if (comsumptionTab.containsKey(mHost))
        aComsumption = comsumptionTab.get(mHost);
      else
        comsumptionTab.put(mHost, ((MyRouter)mHost.getRouter()).getSelfCon());
      double t = (aContribution / (aContribution + aComsumption))*0.3 + getRep(mHost)*0.7;
      reputationTab.put(mHost, t);
    }
  }

  @Override
  public Message messageTransferred(String id, DTNHost from) {
    Message m = super.messageTransferred(id, from);
    boolean isDelivered = this.isDeliveredMessage(m);
    for(var mHost : this.getHost().getNeighbors()) {
      if(isDelivered) {
        ((MyRouter)mHost.getRouter()).comsumptionTab.computeIfPresent(this.getHost(), (key, value) -> value = value + m.getHopCount());
        ((MyRouter)mHost.getRouter()).comsumptionTab.computeIfPresent(m.getFrom(), (key, value) -> value = value + m.getHopCount());
      } else {
        ((MyRouter)mHost.getRouter()).contributionTab.computeIfPresent(this.getHost(), (key, value) -> value + 1);
        ((MyRouter)mHost.getRouter()).contributionTab.computeIfPresent(from, (key, value) -> value + 1);
        ((MyRouter)mHost.getRouter()).comsumptionTab.computeIfPresent(m.getFrom(), (key, value) -> value + 2);
      }
    }
    if(isDelivered)
      comsumptionTab.computeIfPresent(m.getFrom(), (key, value) -> value = value + m.getHopCount());
    else
      contributionTab.computeIfPresent(from, (key, value) -> value + 1);
    return m;
  }

  public Message messageTransferred2(String id, DTNHost from) {
    return super.messageTransferred(id, from);
  }

  /**
   * 测试移动稳定性
   * @param targetHost 目标节点
   * @return 移动稳定性分数
   */
  public double stableScore(DTNHost targetHost) {
    double maxSpeedGap = targetHost.getPath().getSpeed();
    double accumulateSpeedGap = 0;
    int a = targetHost.getNeighbors().size();
    for(var mHost : targetHost.getNeighbors()) {
      maxSpeedGap = Math.max(mHost.getPath().getSpeed(), maxSpeedGap);
      accumulateSpeedGap += Math.abs(mHost.getPath().getSpeed() - targetHost.getPath().getSpeed());
    }
    return 1 - ((accumulateSpeedGap / a) / maxSpeedGap);
  }

  @Override
  public MessageRouter replicate() {
    return new MyRouter(this);
  }

  public double fuzzyLogic(double repFactor, double otherfactor, double probabilityFactor) {
    String fileName = "repPro.fcl";
    FIS fis = FIS.load(fileName, true);
    if (fis == null) { // Error while loading?
      System.err.println("Can't load file: '" + fileName + "'");
      return 0;
    }

    FunctionBlock functionBlock = fis.getFunctionBlock(null);

    // Set inputs
    functionBlock.setVariable("repFactor", repFactor);
    functionBlock.setVariable("otherFactor", otherfactor);
    functionBlock.setVariable("socialSimilarity", probabilityFactor);

    // begin
    functionBlock.evaluate();
    return functionBlock.getVariable("rank").getValue();
  }

  @Override
  public boolean createNewMessage(Message m) {
    this.comsumptionTab.computeIfPresent(this.getHost(), (key, value) -> value = value + 2);
    System.out.println("DTN"+ getHost().toString() + " con"+contributionTab.get(getHost())+" com"+comsumptionTab.get(getHost())+" rep"+getRep(getHost()));
    return super.createNewMessage(m);
  }
}
