package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import util.Tuple;

import java.util.*;
import java.util.stream.Collectors;

public class V2xRouter extends MultipathTrajectoryVehicleToRouterRouter{
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
    V2xRouter(Settings s) {
        super(s);
        comsumptionTab = new HashMap<DTNHost, Double>();
        contributionTab = new HashMap<DTNHost, Double>();
        reputationTab = new HashMap<DTNHost, Double>();
        preRepsTab = new HashMap<>();
        initRep = false;
    }
    V2xRouter(V2xRouter r) {
        super(r);
        comsumptionTab = new HashMap<DTNHost, Double>();
        contributionTab = new HashMap<DTNHost, Double>();
        reputationTab = new HashMap<DTNHost, Double>();
        preRepsTab = new HashMap<>();
        initRep = r.initRep;
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
        for (Message m : getMsgFromRouter().values()) {
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
                if (getackedMessageIds().contains(m.getId())) {
                    continue;
                }
                if (getSendRSU().containsKey(m.getId())) {
                    if (this.getSendRSU().get(m.getId()).contains(other.name)) {
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
    public double getPredFor(DTNHost otherhost) {
        return 0;
    }

    protected class TupleComparator implements Comparator<Tuple<Message, Connection>> {

        @Override
        public int compare(Tuple<Message, Connection> tuple1, Tuple<Message, Connection> tuple2) {
            // delivery probability of tuple1's message with tuple1's connection
            double p1 =
                    ((V2xRouter) tuple1.getValue().getOtherNode(V2xRouter.this.getHost()).getRouter())
                            .getPredFor(tuple1.getKey().getTo());
            // -"- tuple2...
            double p2 =
                    ((V2xRouter) tuple2.getValue().getOtherNode(V2xRouter.this.getHost()).getRouter())
                            .getPredFor(tuple2.getKey().getTo());

            // bigger probability should come first
            if (p2 - p1 == 0) {
                /* equal probabilities -> let queue mode decide */
                return V2xRouter.this.compareByQueueMode(tuple1.getKey(), tuple2.getKey());
            } else if (p2 - p1 < 0) {
                return -1;
            } else {
                return 1;
            }
        }
    }

}
