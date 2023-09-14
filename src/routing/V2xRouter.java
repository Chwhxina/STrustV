package routing;

import core.*;
import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.FunctionBlock;
import util.Tuple;

import java.util.*;
import java.util.stream.Collectors;

public class V2xRouter extends MultipathTrajectoryVehicleToRouterRouter{
    public static final String ENERGY_THS = "EnergyThs";

    protected double energyThs = 0.8;  //能源阈值
    /** 贡献表和消费表 */
    public Map<DTNHost, Double> contributionTab;   //贡献表
    public Map<DTNHost, Double> comsumptionTab;    //消费表
    protected Map<DTNHost, Double> reputationTab;  //声誉表
    protected Map<DTNHost, Double> preRepsTab;     //综合度量
    protected Map<DTNHost, Double> preds;          //碰撞因素
    protected boolean initRep;
    protected double lastAgeUpdate;

    /** 初始preRep */
    private final double preRepInit = 0.5;
    protected final int secondsInTimeUnit = 30;
    private final double beta = 0.25;
    private final double gamma = 0.98;
    private static final double P_INIT = 0.25;

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
            DTNHost otherHost = c.getOtherNode(this.getHost());
            var otherRouter = (V2xRouter) otherHost.getRouter();
            for (Message m : msgToVehicle) {
                Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
                if (nrofCopies > 1) {
                    if(otherRouter.getPredFor(m.getTo()) > this.getPredFor(m.getTo()))
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
        if(this.preRepsTab.containsKey(otherhost))
            return this.preRepsTab.get(otherhost);
        this.preRepsTab.put(otherhost, preRepInit);
        return 0.5;
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
    public void changedConnection(Connection con) {
        double preRepOther;
        super.changedConnection(con);
        DTNHost otherHost = con.getOtherNode(this.getHost());
        updateDeliveryPredFor(otherHost);
        updateRep(this.getHost().getNeighbors());
        double repFactor = this.getRep(otherHost);
        double energyPart = (((V2xRouter)otherHost.getRouter()).energy.getEnergy() / 40000);
        double otherFactor = 0.3 * stableScore(otherHost) + 0.5 * energyPart
                + 0.2 * (1.0-(otherHost.getBufferOccupancy()/100.0));
        preRepOther = fuzzyLogic(repFactor, otherFactor, getPredFor(otherHost));
        this.preRepsTab.put(otherHost, preRepOther);

        //传递概率
        updateTransitivePreds(otherHost);

        //传递声誉
        updateTransiveProPreds(otherHost);


        if (con.isUp()) {
            Set<String> aMs = new HashSet<String>(
                    MultipahTrajectoryTimeSpaceRouter.getArrivedM().keySet());
            this.addAckedMessageIds(aMs);
            deleteAckedMessages();
        } else if (!con.isUp()) {
        }
    }

    /**
     * Updates delivery predictions for a host. <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT
     * </CODE>
     * @param host The host we just met
     */
    private void updateDeliveryPredFor(DTNHost host) {
        double oldValue = this.getPredFor(host);
        double newValue = oldValue + (1 - oldValue) * V2xRouter.P_INIT;
        this.preds.put(host, newValue);
    }

    protected void updateTransitivePreds(DTNHost host) {
        MessageRouter otherRouter = host.getRouter();
        assert otherRouter instanceof V2xRouter
                : "V2xRouter only works " + " with other routers of same type";

        double pForHost = this.getPredFor(host); // P(a,b)
        Map<DTNHost, Double> othersPreds = ((V2xRouter) otherRouter).getDeliveryPreds();

        for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
            if (e.getKey() == this.getHost()) {
                continue; // don't add yourself
            }

            double pOld = this.getPredFor(e.getKey()); // P(a,c)_old
            double pNew = pOld + (1 - pOld) * pForHost * e.getValue() * this.beta;
            this.preds.put(e.getKey(), pNew);
        }
    }

    protected void updateTransiveProPreds(DTNHost host) {
        MessageRouter otherRouter = host.getRouter();
        assert otherRouter instanceof V2xRouter
                : "V2xRouter only works " + " with other routers of same type";
        double pRForHost = this.getRep(host);
        Map<DTNHost, Double> othersPreds = ((V2xRouter) otherRouter).getReputationTab();
        for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
            if (e.getKey() == this.getHost()) {
                continue; // don't add yourself
            }
            double pOld = this.getRep(e.getKey());
            double pNew = pOld + (1 - pOld) * pRForHost * e.getValue() * this.beta;
            this.reputationTab.put(e.getKey(), pNew);
        }
    }

    private Map<DTNHost, Double> getReputationTab() {
        return reputationTab;
    }

    private Map<DTNHost, Double> getDeliveryPreds() {
        this.ageDeliveryPreds(); // make sure the aging is done
        return this.preds;
    }

    protected void ageDeliveryPreds() {
        double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / this.secondsInTimeUnit;

        if (timeDiff == 0) {
            return;
        }

        double mult = Math.pow(this.gamma, timeDiff);
        for (Map.Entry<DTNHost, Double> e : this.preds.entrySet()) {
            e.setValue(e.getValue() * mult);
        }

        this.lastAgeUpdate = SimClock.getTime();
    }



    /**
     * update Reputation information
     * </CODE>
     * @param neighbourHost The host we just met
     */
    protected void updateRep(List<DTNHost> neighbourHost) {
        double aContribution = 1;
        double aComsumption = 1;
        for (var mHost : neighbourHost) {
            if (contributionTab.containsKey(mHost))
                aContribution = contributionTab.get(mHost);
            else {
                contributionTab.put(mHost, ((V2xRouter) mHost.getRouter()).getSelfCon());
            }
            if (comsumptionTab.containsKey(mHost))
                aComsumption = comsumptionTab.get(mHost);
            else
                comsumptionTab.put(mHost, ((V2xRouter)mHost.getRouter()).getSelfCon());
            double t = (aContribution / (aContribution + aComsumption))*0.3 + getRep(mHost)*0.7;
            reputationTab.put(mHost, t);
        }
    }

    public double getSelfCon() {
        if(!initRep){
            contributionTab.put(this.getHost(), 1.0);
            comsumptionTab.put(this.getHost(), 1.0);
            initRep = true;
        }
        return contributionTab.get(this.getHost());
    }


    protected double getRep(DTNHost host) {
        if(reputationTab.containsKey(host))
            return reputationTab.get(host);
        if(comsumptionTab.containsKey(host) && contributionTab.containsKey(host)) {
            double t = contributionTab.get(host) / (contributionTab.get(host) + comsumptionTab.get(host));
            reputationTab.put(host, t);
            return t;
        }
        contributionTab.put(host, ((V2xRouter)host.getRouter()).getSelfCon());
        comsumptionTab.put(host, ((V2xRouter)host.getRouter()).getSelfCon());
        return contributionTab.get(host) / (contributionTab.get(host) + comsumptionTab.get(host));
    }

    public V2xRouter replicate() {
        return new V2xRouter(this);
    }

    public double fuzzyLogic(double repFactor, double otherfactor, double probabilityFactor) {
        String fileName = "/Users/wanghaoxiang/Documents/Code/one-framework/src/routing/repPro.fcl";
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
