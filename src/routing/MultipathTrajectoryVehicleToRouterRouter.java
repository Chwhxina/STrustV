package routing;

import core.*;
import movement.RouterPlacementMovement;
import util.Tuple;

import java.util.*;
import java.util.stream.Collectors;


/**
 * Message transmission strategy of vehicle in STALB
 */
public class MultipathTrajectoryVehicleToRouterRouter extends SourceSprayAndWaitRouter {

  public static final String STATUSTHRESHOLD = "loadFactor";
  public static final String CATEGORY_MARK = "categoryMark";
  private Set<String> ackedMessageIds;
  private HashMap<String, List<String>> sendRSU;
  private Map<DTNHost, Double> arriveTime;
  private Map<Coord, DTNHost> cDTNhosts;
  private double loadFactor;
  private Map<String, Message> msgFromRouter;
  private String routerMark;
  private String vehicleMark;


  static {
    DTNSim.registerForReset(RouterPlacementMovement.class.getCanonicalName());
  }

  public MultipathTrajectoryVehicleToRouterRouter(Settings s) {
    super(s);
    this.ackedMessageIds = new HashSet<String>();
    this.sendRSU = new HashMap<String, List<String>>();
    this.arriveTime = new HashMap<DTNHost, Double>();
    this.cDTNhosts = interfaces.RouterPreConnEngine1.getCRRelation();
    this.msgFromRouter = new HashMap<String, Message>();
    this.routerMark = "ROUTER";
    this.vehicleMark = "VEHICLE";
    if (s.contains(STATUSTHRESHOLD)) {
      this.loadFactor = s.getDouble(STATUSTHRESHOLD);
    } else {
      this.loadFactor = 0.75;
    }
  }

  public MultipathTrajectoryVehicleToRouterRouter(MultipathTrajectoryVehicleToRouterRouter r) {
    super(r);
    this.ackedMessageIds = new HashSet<String>();
    this.sendRSU = new HashMap<String, List<String>>();
    this.arriveTime = new HashMap<DTNHost, Double>();
    this.vehicleMark = r.vehicleMark;
    this.routerMark = r.routerMark;
    this.cDTNhosts = r.cDTNhosts;
    this.loadFactor = r.loadFactor;
    this.msgFromRouter = r.msgFromRouter;
  }

  @Override
  public void update() {
    super.update();
  }


  @Override
  public Tuple<Message, Connection> tryOtherMessages() {
    //		removeAllUntransmitMsg();
    List<Tuple<Message, Connection>> priorityQueue = new ArrayList<Tuple<Message, Connection>>();
    List<Tuple<Message, Connection>> midQueue = new ArrayList<Tuple<Message, Connection>>();

    // 将目的地为车辆的消息通过V2V接口发送
    Collection<Message> msgToVehicle = getMessageCollection().stream().filter(
            m -> !m.getTo().toString().startsWith("R")
    ).collect(Collectors.toList());
    for (Connection c : getConnectionsByInterface("V2V")) {
      for (Message m : msgToVehicle) {
        Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
        if (nrofCopies > 1) {
          midQueue.add(new Tuple<Message, Connection>(m, c));
        }
      }
    }
    Collections.sort(midQueue, new TupleComparator());
    tryMessagesForConnected(midQueue);
    midQueue.clear();

    // 将目的地为路由器的消息通过V2R接口发送
    //首先处理急需中转的数据
    for (Message m : this.msgFromRouter.values()) {
      //针对于数据的中转目的地，选择距离数据目的地最近的节点
      for (Tuple<Connection, Double> e : getMinDistanceRouter(m)) {
        if (!this.isCongestion(e.getKey().getOtherNode(getHost()))) {
          priorityQueue.add(new Tuple<Message, Connection>(m, e.getKey()));
          break;
        }
      }
    }
    // Collection<Message> msgCollection = getMessageCollection().stream().filter(
    //     m -> m.getTo().toString().startsWith("R")
    // ).collect(Collectors.toList());
    Collection<Message> msgCollection = getMessageCollection();
    for (Connection c : getConnectionsByInterface("V2R")) {
      DTNHost other = c.getOtherNode(getHost());
      MultipahTrajectoryTimeSpaceRouter otherRouter = (MultipahTrajectoryTimeSpaceRouter) other.getRouter();
      //			VRCRouter otherRouter = (VRCRouter)other.getRouter();
      for (Message m : msgCollection) {
        //When the message has arrived its destination, do nothing.
        if (this.ackedMessageIds.contains(m.getId())) {
          continue;
        }
        if (this.sendRSU.containsKey(m.getId())) {
          if (this.sendRSU.get(m.getId()).contains(other.name)) {
            continue;
          }
        }
        //STALB的判断机制
        if (otherRouter.hasMessage(m.getId())
                && otherRouter.isConatinMsg(m.getId(), m.getCopyVersion())) {
          continue;
        }
        if (otherRouter.isTransferring()) {
          continue;
        }

        Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
        if (nrofCopies > 1) {
          midQueue.add(new Tuple<Message, Connection>(m, c));
        }
      }
    }
    Collections.sort(midQueue, new TupleComparator());
    priorityQueue.addAll(midQueue);
    return tryMessagesForConnected(priorityQueue);
  }

  /**
   * 获得按照到达数据m目的地的升序集合
   *
   * @param m 数据
   * @return 升序的链路集合
   */
  public List<Tuple<Connection, Double>> getMinDistanceRouter(Message m) {
    List<Tuple<Connection, Double>> ans = new ArrayList<Tuple<Connection, Double>>();
    List<Connection> cs = this.getConnectionsByInterface("V2R");
    Coord to = m.getTo().getLocation();
    for (Connection c : cs) {
      ans.add(
              new Tuple<Connection, Double>(c, c.getOtherNode(getHost()).getLocation().distance(to)));
    }
    ans.sort(new Comparator<Tuple<Connection, Double>>() {
      @Override
      public int compare(Tuple<Connection, Double> o1, Tuple<Connection, Double> o2) {
        if (o1.getValue() == o2.getValue()) {
          return 0;
        }
        return o1.getValue() < o2.getValue() ? -1 : 1;
      }
    });
    return ans;
  }


  @Override
  protected Tuple<Message, Connection> tryMessagesForConnected(
          List<Tuple<Message, Connection>> tuples) {
    if (tuples.size() == 0) {
      return null;
    }

    for (Tuple<Message, Connection> t : tuples) {
      Message m = t.getKey();
      //update the version number of copies
      m.updateLastCopyVersion();
      int lastCopyVersion = m.getLastCopyVersion();
      m.updateCopyVersion(lastCopyVersion);

      Connection con = t.getValue();
      if (startTransfer(m, con) == RCV_OK) {

        return t;
      } else {
        m.downUpdateLastCopyVersion();
        lastCopyVersion = m.getLastCopyVersion();
        m.updateCopyVersion(lastCopyVersion);
      }
    }
    return null;
  }

  /**
   * Record which routers are the transfer objects for each message after one message has finished
   * transferred.
   *
   * @time 2021/12/03
   */
  @Override
  protected void transferDone(Connection con) {
    Integer nrofCopies;
    String msgId = con.getMessage().getId();
    /* get this router's copy of the message */
    Message msg = getMessage(msgId);

    if (msg == null) { // message has been dropped from the buffer after..
      return; // ..start of transfer -> no need to reduce amount of copies
    }

    /* reduce the amount of copies left */
    nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
    nrofCopies--;
    msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);

    List<String> tmpRSUs = new ArrayList<String>();
    if (this.sendRSU.containsKey(msgId)) {
      tmpRSUs = this.sendRSU.get(msgId);
      tmpRSUs.add(con.getOtherNode(getHost()).name);
    } else {
      tmpRSUs.add(con.getOtherNode(getHost()).name);
    }
    this.sendRSU.put(msgId, tmpRSUs);
  }

  public void removeAllUntransmitMsg() {
    Collection<Message> deleteMsg = new ArrayList<Message>();
    for (Message m : getMessageCollection()) {
      Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
      if (nrofCopies < 1) {
        deleteMsg.add(m);
      }
    }
    for (Message m : deleteMsg) {
      this.getHost().getMessageCollection()
              .remove(m);//Delete all messages that are no longer transmitted
    }
  }

  private class TupleComparator implements Comparator<Tuple<Message, Connection>> {

    public int compare(Tuple<Message, Connection> tuple1, Tuple<Message, Connection> tuple2) {
      double p1 = tuple1.getKey().getReceiveTime();
      double p2 = tuple2.getKey().getReceiveTime();

      if (p1 - p2 == 0) {
        return 0;
      } else if (p1 - p2 < 0) {
        return -1;
      } else {
        return 1;
      }
    }
  }

  /**
   * Delete delivered messages.
   */
  private void deleteAckedMessages() {
    Collection<Message> Ms = getMessageCollection();
    Object[] tmpMs = Ms.toArray();
    for (int i = 0; i < tmpMs.length; i++) {
      Message m = (Message) tmpMs[i];
      if (this.ackedMessageIds.contains(m.getId()) && !isSending(m.getId())) {
        this.deleteMessage(m.getId(), false);
        this.sendRSU.remove(m.getId());
      }
    }
  }

  @Override
  public void changedConnection(Connection con) {
    super.changedConnection(con);
    if (con.isUp()) {
      Set<String> aMs = new HashSet<String>(
              MultipahTrajectoryTimeSpaceRouter.getArrivedM().keySet());
      this.ackedMessageIds.addAll(aMs);
      deleteAckedMessages();
    } else if (!con.isUp()) {
    }
  }

  public boolean isCongestion(DTNHost h) {
    return h.getBufferOccupancy() > this.loadFactor ? true : false;
  }

  @Override
  public MultipathTrajectoryVehicleToRouterRouter replicate() {
    return new MultipathTrajectoryVehicleToRouterRouter(this);
  }
}
