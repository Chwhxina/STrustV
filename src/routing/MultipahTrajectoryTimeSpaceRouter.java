package routing;

import core.*;
import movement.MessageTrajectoryFinder;
import movement.Path;
import movement.RouterPlacementMovement;
import util.Tuple;

import java.util.*;


//import java.util.Map.Entry;
//import jdk.javadoc.doclet.Taglet;
//import java.math.*;


public class MultipahTrajectoryTimeSpaceRouter extends ActiveRouter {
  public static final String NROF_COPIES = "nrofCopies";
  public static final String MultipahTrajectoryTimeSpace_NS = "MTTSRouter";
  public static final String MSG_COUNT_PROPERTY = MultipahTrajectoryTimeSpace_NS + "." + "copies";
  public static final String ALPHA = "alpha";
  public static final String BETA = "beta";
  public static final String GAMMA = "gammma";
  public static final String STATUSTHRESHOLD = "statusThreshold";
  public static final String RHO = "rho";
  //for cybersapce race
  public static final String CATEGORY_MARK = "categoryMark";
  public static final String MALICIOUS_MARK = "maliciousType";

  protected int initialNrofCopies;
  private Settings set;
  private List<Tuple<Double, Connection>> routerScore;
  private HashMap<String, List<Tuple<Double, Connection>>> msgScoreRouter;
  private HashMap<String, Message> msgSRM;
  private Collection<Message> cMessages;
  private HashMap<String, Message> vehicleMessages;
  private Set<String> willDeletedMessages;

  //a threshold for judging the router status
  private double statusThreshold;
  //	private HashMap<String,Boolean> messagesFinded;
  private Set<String> messagesFinded;

  //determine whether router need to find the next hop for messages
  private boolean findNextPointFlag = false;

  //for calculating the preferred index
  private double alpha, beta, gamma, rho;

  //the number of next-hops that the message will follow.
  private int nrofCorrect = 3;

  //the local network status of router
  private boolean congestion; // router status now
  private boolean rearCongestion;// the prediction router status in the next period
  private int statusId; // router status counter
  private double congestionValue; // buffer score
  private double rearCongestionValue;// prediction of buffer score
  private double averageVar; // MES(mean square error) in statusId period
  private boolean isFirstStart; // for checking whether message is coming
  private double lastCongestionValue;// congestion value in last time period

  //the normalization of preferred index value
  private double Max_closenessScore;
  private double Max_waitingTime;
  private double Max_bufferScore;

  //for cybersapce reace, 10s
  private double timer = 100.0;
  private double updateInterval;
  private int timerCount = 0;
  private double periodInput = 0, periodOutput = 0, periodAvgTime = 0;
  private double maxOverhead = 0;
  private HashMap<DTNHost, Integer> overheadRecord;
  private int deliveredCount = 0;

  private String categoryMark;
  private String maliciousMark;

  public HashSet<DTNHost> identifyMaliciousRouter;

  private List<routerStatus> allStatus = new ArrayList<routerStatus>();

  //output parameter for reporter to get data
  private static double rcv; //rearCongestionValue
  private static double Orho;//for output rho.
  private static List<routerStatus> OallStatus = new ArrayList<routerStatus>();
  private static HashMap<String, List<Tuple<Double, Connection>>> msgSR = new HashMap<String, List<Tuple<Double, Connection>>>();

  //count which message is arrived at destination
  private static HashMap<String, Boolean> OarrivedM = new HashMap<String, Boolean>();

  static {
    DTNSim.registerForReset(RouterPlacementMovement.class.getCanonicalName());
    reset();
  }

  private HashMap<String, Object> modelArgs;

  public MultipahTrajectoryTimeSpaceRouter(Settings s) {
    super(s);
    this.set = s;
    Settings setting = new Settings(MultipahTrajectoryTimeSpace_NS);
    initialNrofCopies = setting.getInt(NROF_COPIES);
    if (setting.contains(ALPHA))
      this.alpha = setting.getDouble(ALPHA);
    else
      this.alpha = 0.4;
    if (setting.contains(BETA))
      this.beta = setting.getDouble(BETA);
    else
      this.beta = 0.3;
    if (setting.contains(GAMMA))
      this.gamma = setting.getDouble(GAMMA);
    else
      this.gamma = 0.3;
    if (setting.contains(RHO))
      this.rho = setting.getDouble(RHO);
    else
      this.rho = 0.8;
    if (setting.contains(STATUSTHRESHOLD))
      this.statusThreshold = setting.getDouble(STATUSTHRESHOLD);
    else
      this.statusThreshold = 0.75;
    this.congestion = false;
    this.rearCongestion = false;
    this.isFirstStart = true;
    this.statusId = 1;
    this.congestionValue = 0;
    this.rearCongestionValue = 0.0;
    this.lastCongestionValue = 1.0;
    this.averageVar = 0.0;
    this.msgScoreRouter = new HashMap<String, List<Tuple<Double, Connection>>>();
    this.msgSRM = new HashMap<String, Message>();
    this.routerScore = new ArrayList<Tuple<Double, Connection>>();
//		this.messagesFinded = new HashMap<String,Boolean>();
    this.messagesFinded = new HashSet<String>();
    this.vehicleMessages = new HashMap<String, Message>();
    this.willDeletedMessages = new HashSet<String>();
    this.Max_bufferScore = Double.MIN_VALUE;
    this.Max_closenessScore = Double.MIN_VALUE;
    this.Max_waitingTime = Double.MIN_VALUE;
    this.updateInterval = SimScenario.getOUpdateInterval();
    reset();
    this.categoryMark = s.getSetting(CATEGORY_MARK);
    this.maliciousMark = s.getSetting(MALICIOUS_MARK);
    this.overheadRecord = new HashMap<DTNHost, Integer>();
    this.identifyMaliciousRouter = new HashSet<DTNHost>();
    this.modelArgs = new HashMap<>();
  }

  protected MultipahTrajectoryTimeSpaceRouter(MultipahTrajectoryTimeSpaceRouter r) {
    super(r);
    this.set = r.set;
    this.initialNrofCopies = r.initialNrofCopies;
    this.routerScore = new ArrayList<Tuple<Double, Connection>>();
    this.congestion = false;
    this.isFirstStart = true;
    this.alpha = r.alpha;
    this.gamma = r.gamma;
    this.beta = r.beta;
    this.rho = r.rho;
    this.statusThreshold = r.statusThreshold;
    this.congestion = false;
    this.rearCongestion = false;
    this.statusId = 1;
    this.congestionValue = 0;
    this.rearCongestionValue = 0.0;
    this.averageVar = 0.0;
    this.lastCongestionValue = 1.0;
    this.msgScoreRouter = new HashMap<String, List<Tuple<Double, Connection>>>();
    this.msgSRM = new HashMap<String, Message>();
    this.routerScore = new ArrayList<Tuple<Double, Connection>>();
//		this.messagesFinded = new HashMap<String,Boolean>();
    this.messagesFinded = new HashSet<String>();
    this.vehicleMessages = new HashMap<String, Message>();
    this.willDeletedMessages = new HashSet<String>();
    this.Max_bufferScore = Double.MIN_VALUE;
    this.Max_closenessScore = Double.MIN_VALUE;
    this.Max_waitingTime = Double.MIN_VALUE;
    this.categoryMark = r.categoryMark;
    this.maliciousMark = r.maliciousMark;
    this.updateInterval = r.updateInterval;
    this.overheadRecord = new HashMap<DTNHost, Integer>();
    this.identifyMaliciousRouter = new HashSet<DTNHost>();
    this.modelArgs = new HashMap<>();
  }


  @Override
  public boolean createNewMessage(Message msg) {
    makeRoomForNewMessage(msg.getSize());
    msg.trajectoryPath = setTrajectoryPath(this.set, msg);
    msg.setTtl(this.msgTtl);
    addToMessages(msg, true);
    msg.addProperty(MSG_COUNT_PROPERTY, initialNrofCopies);
    this.findNextPointFlag = true;
    return true;
  }

  private static void reset() {
    rcv = 0.0; //rearCongestionValue
    OallStatus = new ArrayList<routerStatus>();
    msgSR = new HashMap<String, List<Tuple<Double, Connection>>>();
    OarrivedM = new HashMap<String, Boolean>();
  }


  @Override
  public void update() {
    super.update();
    //for cyberspace race
    this.timer -= this.updateInterval;
    if (this.timer <= 0.0) {
      this.timer = 100.0;
      this.timerCount++;
      updatePeriod();
      intialPeriodValues();
    }
    if (!canStartTransfer() || isTransferring())
      return; //nothing to transger or is currently transferring

    /* try messages that could be delivered to final reciplient*/
    if (exchangeDeliverableMessages() != null)
      return;
    if (checkInputMessages())
      this.findNextPointFlag = true;
    if (this.maliciousMark.equals("BLACKHOLEATTACK")) {
//			dropMessages();
    } else {
      //find the next hop router for every message
      findNextHopRouter();
      //sort messages and put into the sending queue.
      tryOtherMessages();
    }
    //delete messages which had delivered
    clearMessage();
    dropExpiredMessages();
    this.findNextPointFlag = false;

  }

  public void dropMessages() {
    Collection<Message> Ms = this.getMessageCollection();
    Iterator<Message> its = Ms.iterator();
    while (its.hasNext()) {
      Message m = its.next();
      if (!m.getFrom().name.equals(this.getHost().name))
        its.remove();
    }
  }

  public void clearMessage() {
    Set<String> msgIDs = new HashSet<String>(this.messages.keySet());

    Iterator<String> ite = msgIDs.iterator();
    while (ite.hasNext()) {
      String s = ite.next();
      String id = s.split("_", 2)[0];
      if (this.willDeletedMessages.contains(id)) {
        this.deleteMessage(s, false);
      }
    }
  }

  public Tuple<Message, Connection> tryOtherMessages() {
    // send to vehicles, try direct delivery
    List<Tuple<Message, Connection>> msgNextVehicle = new ArrayList<Tuple<Message, Connection>>();
    for (Message m : this.vehicleMessages.values()) {
      for (Connection c : getConnections()) {
        DTNHost otherNode = c.getOtherNode(getHost());
        if (m.getTo().equals(otherNode)) {
          msgNextVehicle.add(new Tuple<>(m, c));
        }
      }
    }
    tryMessagesForConnected(msgNextVehicle);
    // send to routers
    List<Connection> cs = this.getConnections();
    Iterator<Connection> iTs = cs.iterator();
    while (iTs.hasNext()) {
      DTNHost h = iTs.next().getOtherNode(this.getHost());
      if (this.identifyMaliciousRouter.contains(h))
        iTs.remove();
    }

    List<Tuple<Message, Connection>> msgNextRouter = new ArrayList<Tuple<Message, Connection>>();

    if (!this.msgScoreRouter.isEmpty()) {
      this.msgScoreRouter.forEach((key, values) -> {
        double maxScore = Double.MIN_VALUE;
        Connection nextRouter = null;

        for (Tuple<Double, Connection> rs : values) {
          double score = rs.getKey();
          Connection c = rs.getValue();

          if (maxScore < score) {
            nextRouter = c;
            maxScore = score;
          }
        }
        if (nextRouter != null) {
          Message msg = this.msgSRM.get(key);
          msgNextRouter.add(new Tuple<Message, Connection>(msg, nextRouter));
        } else {
          Message msg = this.msgSRM.get(key);
          List<DTNHost> tPath = msg.gettHPath();
          boolean findNow = false;
          int count = 0;
          for (DTNHost h : tPath) {
            if (findNow) {
//              List<Connection> cs = this.getConnections();
              for (Connection c : cs) {
                if (c.getOtherNode(this.getHost()).name.equals(h.name)) {
                  msgNextRouter.add(new Tuple<Message, Connection>(msg, c));
                  break;
                }
              }
            }
            if (h.name.equals(this.getHost().name)) {
              findNow = true;
            }
            count++;
          }
          if (count == tPath.size()) {
//            List<Connection> cs = this.getConnections();
            Random rand = new Random();
            int tempRandom = rand.nextInt(cs.size());
            msgNextRouter.add(new Tuple<Message, Connection>(msg, cs.get(tempRandom)));
          }
        }
      });
    }

    msgNextRouter.sort(new bestStatusComparator());
    return tryMessagesForConnected(msgNextRouter);
  }

  public boolean checkInputMessages() {
    Collection<Message> msCollection = getMessageCollection();
    if (this.isFirstStart && !msCollection.isEmpty()) {
      this.isFirstStart = false;
      return true;
    }
    if (!msCollection.isEmpty() && this.statusId >= 2) {
      routerStatus<Integer, Boolean, Boolean, Double, Double, Double, HashSet<String>> lastRs = allStatus.get(this.statusId - 2);
      Set<String> lstMsg = new HashSet<String>(lastRs.getMs());
      List<Message> nowMsg = new ArrayList<Message>(msCollection);

      for (Message m : nowMsg) {
        String msgId = m.getId() + "_" + m.getCopyVersion();
        if (!lstMsg.contains(msgId))
          return true;
      }
    }
    return false;
  }

  public boolean checkNewMessages() {
    Collection<Message> msCollection = getMessageCollection();
    boolean flag = false;
    for (Message m : msCollection) {
      if (m.gettHPath().isEmpty()) {
        m.trajectoryPath = setTrajectoryPath(this.set, m);
        flag = true;
      }
    }
    return flag;
  }

  public void findNextHopRouter() {
    int movingWindowSize = 3;
    Collection<Message> msgCollection = getMessageCollection();
    List<Connection> connections = getConnections();
    /***���������Ԥ�������뱻ʶ��Ķ���router***/
//    Iterator<Connection> iTs = connections.iterator();
//    while (iTs.hasNext()) {
//      DTNHost h = iTs.next().getOtherNode(this.getHost());
//      if (this.identifyMaliciousRouter.contains(h))
//        iTs.remove();//�Ƴ�����·��
//    }

    Iterator<Message> mIt = msgCollection.iterator();
    while (mIt.hasNext()) {
      Message m = mIt.next();
      if (m.getCorrectPathCount() > this.nrofCorrect)
        m.resetCorrectPathCount();
      if (MultipahTrajectoryTimeSpaceRouter.OarrivedM.containsKey(m.getId())) {
        if (!this.willDeletedMessages.contains(m.getId())) {
          this.willDeletedMessages.add(m.getId());
        }
        continue;
      }

      if (m.gettHPath().isEmpty())
        m.trajectoryPath = setTrajectoryPath(this.set, m);
      if (followShortestPath(m, connections))
        continue;

      if (this.messagesFinded.contains(m.getId() + "_" + m.getCopyVersion()))
        continue;


      List<DTNHost> tHPath = m.gettHPath();
      List<Connection> cvC = new ArrayList<Connection>();
      List<DTNHost> movingWindow = new ArrayList<DTNHost>(movingWindowSize);
      movingWindow = getMovingWindow(tHPath, movingWindowSize, m);
      boolean overPath = false;
      if (movingWindow.get(1) == null || movingWindow.get(2) == null)
        overPath = true;
      for (int i = 0, n = movingWindow.size(); i < n; i++) {
        if (movingWindow.get(i).getAddress() == m.getTo().getAddress()) {
          overPath = true;
          break;
        }
      }
      List<Coord> orientationVector = new ArrayList<Coord>(2);
      Vector<Double> v1 = new Vector<Double>();
      double oVx, oVy, orientationVectorLength = 0;

      if (!overPath) {
        orientationVector.add(this.getHost().getLocation());
        orientationVector.add(movingWindow.get(2).getLocation());
        oVx = orientationVector.get(1).getX() - orientationVector.get(0).getX();
        oVy = orientationVector.get(1).getY() - orientationVector.get(0).getY();
        orientationVectorLength = Math.sqrt(oVx * oVx + oVy * oVy);
        v1.add(oVx);
        v1.add(oVy);
      }

      List<List<Coord>> candidateVector = new ArrayList<List<Coord>>();
      HashMap<List<Coord>, Connection> fit = new HashMap<List<Coord>, Connection>();

      List<DTNHost> passedHosts = new ArrayList<DTNHost>(m.getHops());

      for (Connection cn : connections) {
        boolean findBackLink = false;
        for (int i = 0, n = passedHosts.size(); i < n && !findBackLink; i++) {
          if (cn.getOtherNode(this.getHost()).name.equals(passedHosts.get(i).name)) {
            findBackLink = true;
            break;
          }
        }
        if (findBackLink) {
          continue;
        }
        DTNHost to = cn.getOtherNode(this.getHost());
        List<Coord> vector = new ArrayList<Coord>(2);
        Vector<Double> v2 = new Vector<Double>();
        v2.add(to.getLocation().getX() - this.getHost().getLocation().getX());
        v2.add(to.getLocation().getY() - this.getHost().getLocation().getY());

        if (v1.size() > 0) {
          double angle = getAngle(v1, v2);
          if (angle < Math.PI / 2 || angle > Math.PI * 3 / 2) {
            vector.add(this.getHost().getLocation());
            vector.add(to.getLocation());
            candidateVector.add(vector);
            cvC.add(cn);
            fit.put(vector, cn);
          }
        }
      }
      if (overPath) {
        m.trajectoryPath.clearpath();
        m.trajectoryPath = setTrajectoryPath(this.set, m);
        m.setCloseToDes(true);
        followShortestPath(m, connections);
        continue;
      }
      if (cvC.isEmpty()) {
        m.trajectoryPath.clearpath();
        m.trajectoryPath = setTrajectoryPath(this.set, m);
        m.resetCorrectPathCount();
        Connection cn = getConnectionByCoord(connections, m.gettHPath().get(1).getLocation());
        addToFindedAndMsgSR(m, cn);
        m.addCorrectPathCount();
        continue;
      } else {
        this.routerScore.clear();
        this.routerScore = new ArrayList<Tuple<Double, Connection>>();
        this.routerScore = calculateRouterScore(candidateVector, cvC, m, orientationVectorLength, orientationVector);
        this.msgScoreRouter.put(m.getId() + "_" + m.getCopyVersion(), routerScore);
        this.msgSRM.put(m.getId() + "_" + m.getCopyVersion(), m);
        if (!this.messagesFinded.contains(m.getId() + "_" + m.getCopyVersion())) {
          this.messagesFinded.add(m.getId() + "_" + m.getCopyVersion());
        }
      }
    }
    updateRouterStatus();
  }

  public List<Tuple<Double, Connection>> calculateRouterScore(List<List<Coord>> cv,
                                                              List<Connection> cns, Message m, double orientationVectorLength, List<Coord> orientationVector) {
    List<Tuple<Double, Connection>> rs = new ArrayList<Tuple<Double, Connection>>();

    List<Double> routerScores = new ArrayList<Double>();
    List<Double> castScores = new ArrayList<Double>();
    List<Double> delayTimeScores = new ArrayList<Double>();
    List<Double> freeBufferScores = new ArrayList<Double>();

    for (List<Coord> coord : cv) {
      double closenessScore = castToOrientationVector(coord, orientationVector) / orientationVectorLength;
      if (closenessScore > this.Max_closenessScore)
        this.Max_closenessScore = closenessScore;
      castScores.add(closenessScore);
    }

    sortMessages();
    List<Message> ms = new ArrayList<Message>(this.cMessages);
    int mIndex = -1;
    for (int i = 0; !ms.get(i).getId().equals(m.getId()); i++) {
      mIndex = i;
    }
    mIndex++;
    List<Double> transSpeeds = new ArrayList<Double>();
    double sumVelocity = 0;
    for (Connection c : cns) {
      transSpeeds.add(c.getSpeed());
      sumVelocity += c.getSpeed();
    }
    double waitTimeA = 0, waitTimeB = 0;

    double sumMsSize = 0;
    for (int i = 0; i <= mIndex; i++)
      sumMsSize += ms.get(i).getSize();
    waitTimeA += (sumMsSize) / (sumVelocity / transSpeeds.size());
    for (Connection c : cns) {
      DTNHost to = c.getOtherNode(this.getHost());
      Collection<Connection> toCs = getToConnections(to);
      toCs.remove(c);
      List<Message> willArrivalTo = new ArrayList<Message>();
      List<Double> willArrivalToSpeeds = new ArrayList<Double>();
      for (Connection con : toCs) {
        DTNHost other = con.getOtherNode(to);
        List<Message> otherMs = new ArrayList<Message>();
        otherMs.addAll(sortMessages(other));
        int sumMSize = 0;
        List<Message> tmpMs = new ArrayList<Message>(otherMs);
        double ts = con.getSpeed();
        for (int i = 0, n = tmpMs.size(); i < n; i++) {
          if ((sumMSize + tmpMs.get(i).getSize()) / ts < waitTimeA) {
            sumMSize += tmpMs.get(i).getSize();
            willArrivalTo.add(tmpMs.get(i));
            willArrivalToSpeeds.add(ts);
          } else
            break;
        }
      }
      willArrivalTo.sort(new TTLComparator());
      boolean isMeetM = false;
      for (int i = 0, n = willArrivalTo.size(); i < n; i++) {
        Message tmpM = willArrivalTo.get(i);
        double MS = willArrivalToSpeeds.get(i);
        if (tmpM.getId().equals(m.getId()))
          isMeetM = true;
        waitTimeB += tmpM.getSize() / MS;
        if (isMeetM)
          break;
      }
      double delayTime = waitTimeA + waitTimeB;
      if (delayTime > this.Max_waitingTime)
        this.Max_waitingTime = delayTime;
      delayTimeScores.add(delayTime);

      double toBuffer = (double) to.getRouter().getFreeBufferSize() / (double) to.getRouter().getBufferSize();

      if (toBuffer > this.Max_bufferScore)
        this.Max_bufferScore = toBuffer;
      freeBufferScores.add(toBuffer);
    }
    for (int i = 0, n = castScores.size(); i < n; i++) {
      double preferredIndex = this.alpha * castScores.get(i) / this.Max_closenessScore +
              this.gamma * (this.Max_waitingTime - delayTimeScores.get(i)) / this.Max_waitingTime +
              this.beta * freeBufferScores.get(i) / this.Max_bufferScore;
      routerScores.add(preferredIndex);
    }
    for (int i = 0, n = cns.size(); i < n; i++) {
      Connection c = cns.get(i);
      double score = routerScores.get(i);
      rs.add(new Tuple<Double, Connection>(score, c));
    }
    return rs;
  }

  public boolean followShortestPath(Message m, List<Connection> connections) {
    if (m.getCloseToDes() || isBackingToPath(m)) {
      List<DTNHost> tmpH = m.gettHPath();
      int count = 0;
      for (DTNHost dh : tmpH) {
        if (dh.getAddress() == this.getHost().getAddress())
          break;
//        if (!this.identifyMaliciousRouter.contains(dh))
          count++;
      }
      Connection cn = getConnectionByCoord(connections, m.gettHPath().get(count + 1).getLocation());
      addToFindedAndMsgSR(m, cn);
      if (!this.messagesFinded.contains(m.getId() + "_" + m.getCopyVersion())) {
        this.messagesFinded.add(m.getId() + "_" + m.getCopyVersion());
      }

      return true;
    }
    return false;
  }

  public boolean isBackingToPath(Message m) {
    if (m.getCorrectPathCount() > 0 && m.getCorrectPathCount() <= this.nrofCorrect) {
      m.addCorrectPathCount();
      return true;
    }
    return false;
  }

  public void addToFindedAndMsgSR(Message m, Connection cn) {
    Tuple<Double, Connection> tmpTup = new Tuple<Double, Connection>(1.0, cn);
    List<Tuple<Double, Connection>> mt = new ArrayList<Tuple<Double, Connection>>();
    mt.add(tmpTup);
    this.msgScoreRouter.put(m.getId() + "_" + m.getCopyVersion(), mt);
    this.msgSRM.put(m.getId() + "_" + m.getCopyVersion(), m);
    if (!this.messagesFinded.contains(m.getId() + "_" + m.getCopyVersion())) {
      this.messagesFinded.add(m.getId() + "_" + m.getCopyVersion());
    }

  }

  public Connection getConnectionByCoord(List<Connection> cns, Coord coord) {
    Connection c = null;
    for (int i = 0, n = cns.size(); i < n; i++) {
      Coord tmpC = cns.get(i).getOtherNode(this.getHost()).getLocation();
      if (coord.getX() == tmpC.getX() && coord.getY() == tmpC.getY()) {
        c = cns.get(i);
        return c;
      }
    }
    return c;
  }

  public void updateRouterStatus() {
    // update this router's parameters
    if (this.findNextPointFlag) {
      //initialization
      boolean adjrho = false;
      if (this.statusId == 1) {
        this.rearCongestionValue = 1.0;
        this.averageVar = 0.0;
      } else if (this.statusId == 2) {
        this.rearCongestionValue = getRearCongValue();
        this.rearCongestion = getRearCongStatus();
        this.averageVar = Math.pow(this.lastCongestionValue - this.congestionValue, 2);
      } else {
        this.rearCongestionValue = getRearCongValue();
        this.rearCongestion = getRearCongStatus();
        this.averageVar = MES();
        adjrho = true;
      }
      this.congestionValue = (double) (this.getFreeBufferSize() / 1000000.0) / (this.getBufferSize() / 1000000.0);
      this.congestion = this.congestionValue > this.statusThreshold ? true : false;
      ArrayList<Message> Ms = new ArrayList<Message>(getMessageCollection());
      Set<String> cms = new HashSet<String>();
      for (Message m : Ms) {
        String msgId = m.getId() + "_" + m.getCopyVersion();
        cms.add(msgId);
      }
      allStatus.add(new routerStatus<Integer, Boolean, Boolean, Double, Double, Double, HashSet<String>>
              (this.statusId, this.congestion, this.rearCongestion, this.congestionValue, this.rearCongestionValue, this.averageVar, (HashSet<String>) cms));
      if (adjrho)
        adjustRHO();
      this.lastCongestionValue = this.congestionValue;
      this.statusId++;
    }
    setRCV();
  }

  public double getRearCongValue() {
    return this.congestionValue + this.rho * (this.lastCongestionValue - this.congestionValue);
  }

  public boolean getRearCongStatus() {
    return this.rearCongestionValue > this.statusThreshold ? true : false;
  }

  public double MES() {
    routerStatus<Integer, Boolean, Boolean, Double, Double, Double, HashSet<String>> rs = allStatus.get(this.statusId - 2);
    double mes = rs.getAverageVar();
    double var = rs.getPreBuffer() - this.congestionValue;
    return (mes * (this.statusId - 3) + var * var) / (this.statusId - 2);
  }

  public void adjustRHO() {
    routerStatus<Integer, Boolean, Boolean, Double, Double, Double, HashSet<String>> rs = allStatus.get(this.statusId - 2);
    double var = rs.getBuffer() - this.congestionValue;
    if (var != 0) {
      this.rho += this.averageVar / var;
    }
  }

  public void sortMessages() {
    Collection<Message> cm = getMessageCollection();
    List<Message> ms = new ArrayList<Message>();
    cm.forEach(m -> {
      ms.add(m);
    });
    ms.sort(new TTLComparator());
    this.cMessages = ms;
  }

  public List<Message> sortMessages(DTNHost host) {
    Collection<Message> cm = host.getRouter().getMessageCollection();
    List<Message> ms = new ArrayList<Message>();
    cm.forEach(m -> {
      ms.add(m);
    });
    ms.sort(new TTLComparator());
    return ms;
  }

  private class TTLComparator implements Comparator<Message> {
    public int compare(Message m1, Message m2) {
      if (m1.getTtl() == m2.getTtl())
        return 0;
      return m1.getTtl() < m2.getTtl() ? -1 : 1;
    }
  }

  public Connection getMConnection(List<Connection> cns, DTNHost from, DTNHost to) {
    Connection c = null;
    for (Connection cn : cns)
      if (cn.getOtherNode(from).getAddress() == to.getAddress())
        c = cn;
    return c;
  }

  public double getAngle(Vector<Double> v1, Vector<Double> v2) {
    double angle = Math.atan2(v2.get(1), v2.get(0)) - Math.atan2(v1.get(1), v1.get(0));
    if (angle < 0)
      angle = -angle;
    return angle;
  }

  public double castToOrientationVector(List<Coord> c1, List<Coord> c2) {
    double v1x = c1.get(1).getX() - c1.get(0).getX();
    double v1y = c1.get(1).getY() - c1.get(0).getY();
    double v2x = c2.get(1).getX() - c2.get(0).getX();
    double v2y = c2.get(1).getY() - c2.get(0).getY();
    double result = (v1x * v2x + v1y * v2y) / Math.sqrt(v2x * v2x + v2y * v2y);
    return result > 1 ? 1 : result;
  }

  public List<DTNHost> getMovingWindow(List<DTNHost> tHPath, int movingWindowSize, Message m) {
    List<DTNHost> hosts = new ArrayList<DTNHost>(movingWindowSize);
    for (int i = 0; i < movingWindowSize; i++)
      hosts.add(null);
    DTNHost recentlyHost = null;
    double distance = Double.MAX_VALUE;
    DTNHost nowHost = this.getHost();
    for (DTNHost h : tHPath) {
      if (nowHost.getLocation().distance(h.getLocation()) < distance) {
        distance = nowHost.getLocation().distance(h.getLocation());
        recentlyHost = h;
      }
    }
    int count = tHPath.indexOf(recentlyHost);
    for (int i = 0; i < movingWindowSize; i++) {
      if (count + i < tHPath.size() && count + i >= 0)
        hosts.set(i, tHPath.get(count + i));
      else
        hosts.set(i, null);
    }
    return hosts;
  }

  public List<Connection> getToConnections(DTNHost to) {
    List<Connection> cs = new ArrayList<Connection>();
    List<NetworkInterface> nis = to.getNets();
    for (NetworkInterface ni : nis) {
      if (ni.getInterfaceType().equals("preRouterInterface"))
        cs.addAll(ni.getConnections());
    }
    return cs;
  }

  @Override
  public List<Connection> getConnections() {
    List<Connection> cs = new ArrayList<Connection>();
    List<NetworkInterface> nis = this.getHost().getNets();
    for (NetworkInterface ni : nis) {
      if (ni.getInterfaceType().equals("preRouterInterface"))
        cs.addAll(ni.getConnections());
    }
    return cs;
  }

  @Override
  public MessageRouter replicate() {
    return new MultipahTrajectoryTimeSpaceRouter(this);
  }

  @Override
  public Tuple<Message, Connection> tryMessagesForConnected(
          List<Tuple<Message, Connection>> tuples) {
    if (tuples.size() == 0) {
      return null;
    }

    for (Tuple<Message, Connection> t : tuples) {
      Message m = t.getKey();
      Connection con = t.getValue();
      if (startTransfer(m, con) == RCV_OK) {
        return t;
      }
    }
    return null;
  }

  @Override
  public boolean isTransferring() {
    if (this.sendingConnections.size() > 0) {
      return true; // sending something
    }
    if (this.getHost().getConnections().size() == 0) {
      return false; // not connected
    }
    List<Connection> connections = getConnections();
    for (int i = 0, n = connections.size(); i < n; i++) {
      Connection con = connections.get(i);
      if (!con.isReadyForTransfer()) {
        return true;    // a connection isn't ready for new transfer
      }
    }
    return false;
  }

  @Override
  protected void transferDone(Connection con) {
    Message msg = con.getMessage();
    String id = msg.getId() + "_" + msg.getCopyVersion();
    //for cyberspace race
    Message m = this.messages.get(id);
    if (m == null)
      m = msg;
    this.periodAvgTime = (this.periodAvgTime * this.periodOutput
            + (SimClock.getTime() - m.getReceiveTime())) / (++this.periodOutput);
    DTNHost from = this.getHost();
    if (this.overheadRecord.containsKey(from)) {
      this.overheadRecord.put(from, this.overheadRecord.get(from) + 1);
    } else
      this.overheadRecord.put(from, 1);
    if (this.overheadRecord.get(from) > this.maxOverhead)
      this.maxOverhead = this.overheadRecord.get(from);
    this.messagesFinded.remove(id);
    this.msgScoreRouter.remove(id);
    this.msgSRM.remove(id);
    if (this.messages.containsKey(id))
      this.deleteMessage(id, false);
  }

  @Override
  protected void addToMessages(Message m, boolean newMessage) {
    if (m.getTo().toString().startsWith("R")) {
      this.messages.put(m.getId() + "_" + m.getCopyVersion(), m);
    } else {
      this.vehicleMessages.put(m.getId() + "_" + m.getCopyVersion(), m);
    }
    if (newMessage) {
      for (MessageListener ml : this.mListeners) {
        ml.newMessage(m);
      }
    }
  }

  @Override
  public Message messageTransferred(String id, DTNHost from) {
    Message msg = super.messageTransferred(id, from);
    boolean isFinalRecipient;
    isFinalRecipient = msg.getTo() == this.getHost();
    if (isFinalRecipient) {
      MultipahTrajectoryTimeSpaceRouter.OarrivedM.put(msg.getId(), true);
      this.willDeletedMessages.add(msg.getId());
      List<DTNHost> tPath = msg.getPassedPath();
      for (DTNHost h : tPath) {
        h.getRouter().callRouterAddDeliveredCount();
      }
    }
    this.periodInput++;
    if (this.overheadRecord.containsKey(from)) {
      this.overheadRecord.put(from, this.overheadRecord.get(from) + 1);
    } else
      this.overheadRecord.put(from, 1);
    if (this.overheadRecord.get(from) > this.maxOverhead)
      this.maxOverhead = this.overheadRecord.get(from);
    return msg;
  }

  public void callRouterAddDeliveredCount() {
    this.deliveredCount++;
  }

  /***for cyberSpace race***/
  public void updatePeriod() {
    DTNHost from = this.getHost();
    int count = this.timerCount;
    double input = this.periodInput;
    double output = this.periodOutput;
    double relayRatio = output == 0.0 ? 0 : output / input;
    double overhead = this.maxOverhead == 0 ? 0 : (input + output) / this.maxOverhead / this.getConnections().size();
    double time = this.periodAvgTime;
    double space = 1.0 - (double) this.getHost().getRouter().getFreeBufferSize() / (double) this.getHost().getRouter().getBufferSize();
    //for (MessageListener ml : this.mListeners) {
    //	ml.updatePeriod(from, count, input, output, relayRatio, overhead, time, space);
    //}
    this.modelArgs = forAIModel(from, count, input, output, relayRatio, overhead, time, space);
  }

  /*** method is called when a model need input parameters ***/
  public HashMap<String, Object> forAIModel(DTNHost from, Integer count, Double input, Double output,
                                            Double relayRatio, Double overhead, Double time, Double space) {
    HashMap<String, Object> result = new HashMap<String, Object>();
    result.put("Router", from);
    result.put("Period", count);
    result.put("Input", input);
    result.put("Output", output);
    result.put("Replay_ratio", relayRatio);
    result.put("Delivery_count", this.deliveredCount);
    result.put("overhead", overhead);
    result.put("avgTime", time);
    result.put("avgBuffer", space);
    return result;
  }

  /***method is called when a model outputs identification results of routers***/
  public void setMaliciousRouters(HashSet<DTNHost> maliciousRouters) {
    this.identifyMaliciousRouter = maliciousRouters;
  }

  public void intialPeriodValues() {
    this.periodInput = 0;
    this.periodOutput = 0;
    this.periodAvgTime = 0;
    this.deliveredCount = 0;
    this.overheadRecord.clear();
  }


  @Override
  protected boolean makeRoomForMessage(int size) {
    if (size > this.getBufferSize()) {
      return false; // message too big for the buffer
    }
    double freeBuffer = this.getFreeBufferSize();
    /* delete messages from the buffer until there's enough space */
    while (freeBuffer < size) {
      Message m = getOldestMessage(true); // don't remove msgs being sent
      if (m == null) {
        return false; // couldn't remove any more messages
      }
      /* delete message from the buffer as "drop" */
      deleteMessage(m.getId() + "_" + m.getCopyVersion(), true);
      freeBuffer += m.getSize();
    }

    return true;
  }

  @Override
  protected void dropExpiredMessages() {
    Message[] messages = getMessageCollection().toArray(new Message[0]);
    for (int i = 0; i < messages.length; i++) {
      int ttl = messages[i].getTtl();
      if (ttl <= 0 && this.messages.containsKey(messages[i].getId() + "_" + messages[i].getCopyVersion())) {
        deleteMessage(messages[i].getId() + "_" + messages[i].getCopyVersion(), true);
      }
    }
  }

  public Path setTrajectoryPath(Settings s, Message m) {
    MessageTrajectoryFinder mtf = new MessageTrajectoryFinder(s);
    boolean isOld = false;
    Path pf = mtf.getPath(this.getHost(), m.getTo(), isOld);
    List<DTNHost> trl = new ArrayList<DTNHost>(mtf.getTRList());
    m.tHPath = trl;
    return pf;
  }

  public boolean getRouterStatus() {
    return this.congestion;
  }

  public boolean getRearStatus() {
    return this.rearCongestion;
  }

  public String toString() {
    return "Spatio-Temporal domain Autonomous Load Balancing Router" + super.toString();
  }

  public void setRCV() {
    rcv = this.rearCongestionValue;
    OallStatus = this.allStatus;
    msgSR = this.msgScoreRouter;
    Orho = this.rho;
    super.rho = this.rho;
  }

  public static double getRearCongestionValue() {
    return rcv;
  }

  public static List<routerStatus> getOallStatus() {
    return OallStatus;
  }

  public static HashMap<String, List<Tuple<Double, Connection>>> getMSR() {
    return msgSR;
  }

  public static HashMap<String, Boolean> getArrivedM() {
    return OarrivedM;
  }

  public double getRho() {
    return Orho;
  }

  public double getCongestionValue() {
    return this.congestionValue;
  }

  public boolean isConatinMsg(String msgID, int copyVersion) {
    Collection<Message> MS = this.getMessageCollection();
    for (Message m : MS) {
      if (m.getId().equals(msgID) && m.getCopyVersion() == copyVersion)
        return true;
    }
    return false;
  }

  private class bestStatusComparator implements Comparator<Tuple<Message, Connection>> {
    public int compare(Tuple<Message, Connection> v1, Tuple<Message, Connection> v2) {
      DTNHost to1 = v1.getValue().getOtherNode(getHost());
      DTNHost to2 = v2.getValue().getOtherNode(getHost());
      double status1 = (double) to1.getRouter().getFreeBufferSize() / to1.getRouter().getBufferSize();
      double status2 = (double) to2.getRouter().getFreeBufferSize() / to2.getRouter().getBufferSize();
      if (status1 == status2)
        return 0;
      return status1 > status2 ? -1 : 1;
    }
  }

  public class routerStatus<Id, Status, PreStatus, Buffer, PreBuffer, AverageVar, Ms> {
    public Id id; // status id
    public Status status; // router status
    public PreStatus preStatus; //prediction of status
    public Buffer buffer;// free buffer size/all buffer
    public PreBuffer preBuffer; // prediction buffer
    public AverageVar averageVar; //MES
    public Set<String> ms = new HashSet<String>();

    //		public List<Message> ms = new ArrayList<Message>(); // the list of messages in 'id' status
    public routerStatus(Id id, Status status, PreStatus preStatus, Buffer buffer, PreBuffer preBuffer, AverageVar averageVar, Ms ms) {
      this.id = id;
      this.status = status;
      this.preStatus = preStatus;
      this.buffer = buffer;
      this.preBuffer = preBuffer;
      this.averageVar = averageVar;
      this.ms = (HashSet<String>) ms;
    }

    public Id getId() {
      return this.id;
    }

    public Status getStatus() {
      return this.status;
    }

    public PreStatus getPreStatus() {
      return this.preStatus;
    }

    public Buffer getBuffer() {
      return this.buffer;
    }

    public PreBuffer getPreBuffer() {
      return this.preBuffer;
    }

    public AverageVar getAverageVar() {
      return this.averageVar;
    }

    public HashSet<String> getMs() {
      return (HashSet<String>) this.ms;
    }
  }

  @Override
  public String getCategoryMark() {
    return this.categoryMark;
  }

  public HashMap<String, Object> getModelArgs() {
    return modelArgs;
  }
}

