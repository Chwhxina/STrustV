package routing;

import core.*;
import movement.RouterPlacementMovement;
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
    public Map<DTNHost, Double> consumptionTab;    //消费表
    protected Map<DTNHost, Double> reputationTab;  //声誉表
    protected Map<DTNHost, Double> finalScoreTab;     //综合度量
    protected Map<DTNHost, Double> preds;          //碰撞因素
    protected boolean initRep;      //声誉是否初始化
    protected double lastAgeUpdate; //上一次碰撞更新

    /** 初始preRep */
    private final double finalScoreInit = 0.5;
    protected final int secondsInTimeUnit = 30; //碰撞因素老化间隔
    private final double beta = 0.25;
    private final double gamma = 0.98;
    private static final double P_INIT = 0.25;

   public V2xRouter(Settings s) {
        super(s);
        Settings V2xRouterSetting = new Settings("V2xRouter");
        consumptionTab = new HashMap<DTNHost, Double>();
        contributionTab = new HashMap<DTNHost, Double>();
        reputationTab = new HashMap<DTNHost, Double>();
        finalScoreTab = new HashMap<>();
        this.preds = new HashMap<>();
        initRep = false;
    }
    public V2xRouter(V2xRouter r) {
        super(r);
        consumptionTab = new HashMap<DTNHost, Double>();
        contributionTab = new HashMap<DTNHost, Double>();
        reputationTab = new HashMap<DTNHost, Double>();
        finalScoreTab = new HashMap<>();
        energyThs = r.energyThs;
        preds = r.preds;
        lastAgeUpdate = r.lastAgeUpdate;
        initRep = r.initRep;
    }

    /***
     * 二元组(Message, Connection)
     * @return  将要通过Connection发送的消息
     */
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
                    //如果一个节点消息的综合度量更大，就将消息复制给他
                    if(otherRouter.getFinalScoreFor(m.getTo()) > this.getFinalScoreFor(m.getTo()))
                        midQueue.add(new Tuple<Message, Connection>(m, c));
                }
            }
        }
        //消息按照综合得分进行排序
        Collections.sort(midQueue, new TupleComparatorV2V());
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

    /***
     * 获取finalScore
     * @param otherhost 想要查询的节点DTNhost
     * @return finScore
     */
    public double getFinalScoreFor(DTNHost otherhost) {
        if(this.finalScoreTab.containsKey(otherhost))
            return this.finalScoreTab.get(otherhost);
        this.finalScoreTab.put(otherhost, finalScoreInit);
        return finalScoreInit;
    }

    /**
     * 测试移动稳定性
     * @param targetHost 目标节点
     * @return 移动稳定性分数
     */
    public double stableScore(DTNHost targetHost) {
        double maxSpeedGap = getVehicleSpeed(targetHost);
        double accumulateSpeedGap = 0;
        int a = targetHost.getNeighbors().size();
        for(var mHost : targetHost.getNeighbors()) {
            maxSpeedGap = Math.max(getVehicleSpeed(mHost), maxSpeedGap);
            accumulateSpeedGap += Math.abs(getVehicleSpeed(mHost) - getVehicleSpeed(targetHost));
        }
        return 1 - ((accumulateSpeedGap / a) / maxSpeedGap);
    }

    private double getVehicleSpeed(DTNHost otherHost) {
        if(otherHost.getPath() == null)
            return 0;
        return otherHost.getPath().getSpeed();
    }

    /***
     * 重写con状态改变时调用的方法
     * @param con The connection whose state changed
     */
    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);
        //针对V2V的连接更新声誉
        if(con.getOtherNode(getHost()).getCategoryMark().equals("VEHICLE")) {
            double finalScore;  //con连接节点的finalScore
            DTNHost otherHost = con.getOtherNode(this.getHost());
            updateDeliveryPredFor(otherHost);   //遇见后更新碰撞因素
            updateRep(otherHost);   //更新声誉
            double repFactor = this.getRep(otherHost);
            double energyPart = (((V2xRouter)otherHost.getRouter()).energy.getEnergy() / 40000);
            double otherFactor = 0.3 * stableScore(otherHost) + 0.5 * energyPart
                    + 0.2 * (1.0-(otherHost.getBufferOccupancy()/100.0));
            finalScore = fuzzyLogic(repFactor, otherFactor, getPredFor(otherHost));
            this.preds.put(otherHost, finalScore);

            //传递概率
            updateTransitivePreds(otherHost);

            //传递声誉
            updateTransiveFinalScore(otherHost);
        }

        if (con.isUp()) {
            Set<String> aMs = new HashSet<String>(
                    MultipahTrajectoryTimeSpaceRouter.getArrivedM().keySet());
            this.addAckedMessageIds(aMs);
            deleteAckedMessages();
        }
    }

    public double getPredFor(DTNHost host) {
        this.ageDeliveryPreds(); // make sure preds are updated before getting
        if (this.preds.containsKey(host)) {
            return this.preds.get(host);
        } else {
            return 0;
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

    /***
     * 将碰撞因子传递给某个节点
     * @param host 节点
     */
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

    /***
     * finalScore的传递性，传递给某个节点
     * @param host 节点
     */
    protected void updateTransiveFinalScore(DTNHost host) {
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

    /***
     * 碰撞因子的老化函数
     */
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
     */
    protected void updateRep(DTNHost host) {
        double cont = 1.0;
        double cons = 1.0;
        if(contributionTab.containsKey(host) && consumptionTab.containsKey(host)) {
            cont = contributionTab.get(host);
            cons = consumptionTab.get(host);
            reputationTab.put(host, cont/(cons + cont));
        }else {
            contributionTab.put(host, 1.0);
            consumptionTab.put(host, 1.0);
            reputationTab.put(host, 0.5);
        }

        for (DTNHost otherHost : getHost().getNeighborsByInterface("V2V")) {
            cont = 1.0;
            cons = 1.0;
            if (((V2xRouter)otherHost.getRouter()).contributionTab.containsKey(host))
                cont = ((V2xRouter)otherHost.getRouter()).contributionTab.get(host);
            else {
                ((V2xRouter)otherHost.getRouter()).contributionTab.put(host, contributionTab.get(host));
            }
            if (((V2xRouter)otherHost.getRouter()).consumptionTab.containsKey(host))
                cons = ((V2xRouter)otherHost.getRouter()).consumptionTab.get(host);
            else
                ((V2xRouter)otherHost.getRouter()).consumptionTab.put(otherHost, consumptionTab.get(host));
            double t = (cont / (cont + cons))*0.3 + reputationTab.get(host) * 0.7;
            reputationTab.put(host, t);
        }
    }

    /***
     * 每次获取一个节点的声誉时就对其进行计算
     * @param host 节点
     * @return 声誉值
     */
    protected double getRep(DTNHost host) {
        updateRep(host);
        return reputationTab.get(host);
    }

    public V2xRouter replicate() {
        return new V2xRouter(this);
    }

    /***
     * 模糊逻辑获取finalScore
     * @param repFactor 声音值
     * @param otherfactor 其它因素
     * @param probabilityFactor 碰撞因素
     * @return finalSCore
     */
    public double fuzzyLogic(double repFactor, double otherfactor, double probabilityFactor) {
        String fileName = "./src/routing/repPro.fcl";
        FIS fis = FIS.load(fileName, true);
        if (fis == null) { // Error while loading?
            System.err.println("Can't load file: '" + fileName + "'");
            return 0;
        }

        FunctionBlock functionBlock = fis.getFunctionBlock(null);

        // Set inputs
        functionBlock.setVariable("ReputationScore", repFactor);
        functionBlock.setVariable("TransmissionCapacity", otherfactor);
        functionBlock.setVariable("SocialSimilarity", probabilityFactor);

        // begin
        functionBlock.evaluate();
        return functionBlock.getVariable("rank").getValue();
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message msg =  super.messageTransferred(id, from);
        System.out.println("DTN"+ getHost().toString() + " cont"+contributionTab.get(getHost())+
                " cons"+consumptionTab.get(getHost())+" rep"+getRep(getHost()));
        boolean isDelivered = this.isDeliveredMessage(msg);
        for(var mHost : this.getHost().getNeighborsByInterface("V2V")) {
            if(isDelivered) {
                int vehicleHop = VehicleHopCount(msg) - 1;
                ((V2xRouter)mHost.getRouter()).consumptionTab.computeIfPresent(this.getHost(), (key, value) -> value = value + vehicleHop);
                ((V2xRouter)mHost.getRouter()).consumptionTab.computeIfPresent(msg.getFrom(), (key, value) -> value = value + vehicleHop);
                ((V2xRouter)mHost.getRouter()).contributionTab.computeIfPresent(from, (key, value) -> value + 1);
            } else {
                ((V2xRouter)mHost.getRouter()).contributionTab.computeIfPresent(this.getHost(), (key, value) -> value + 1);
            }
        }
        if(isDelivered){
            int vehicleHop = VehicleHopCount(msg);
            consumptionTab.computeIfPresent(msg.getFrom(), (key, value) -> value = value + vehicleHop);
        }
        contributionTab.computeIfPresent(from, (key, value) -> value + 1);
        return msg;
    }

    private int VehicleHopCount(Message msg) {
        int count = 0;
        for(var mHost : msg.getPassedPath()) {
            if(mHost.getCategoryMark().equals("VEHICLE"))
                count++;
        }
        return count;
    }

    protected class TupleComparatorV2V implements Comparator<Tuple<Message, Connection>> {

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
