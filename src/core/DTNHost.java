/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package core;

import movement.MovementModel;
import movement.Path;
import org.apache.commons.lang3.tuple.Pair;
import routing.MessageRouter;
import routing.util.RoutingInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static core.Constants.DEBUG;

/** A DTN capable host. */
public class DTNHost implements Comparable<DTNHost> {
  private static int nextAddress = 0;

  static {
    DTNSim.registerForReset(DTNHost.class.getCanonicalName());
    DTNHost.reset();
  }

  private final int address;
  private Coord location; // where is the host
  private Coord destination; // where is it going
  private MessageRouter router;
  private final MovementModel movement;
  private Path path;
  private double speed;
  private double nextTimeToMove;
  public String name;
  private final List<MessageListener> msgListeners;
  private final List<MovementListener> movListeners;
  private final List<NetworkInterface> net;
  private final ModuleCommunicationBus comBus;
  private final List<InteractionRecord> interactions;
  private final Map<DTNHost, Pair<Double, Double>> trusts;
  private final Map<DTNHost, Double> social;
  private Map<Coord,DTNHost> cDTNHosts;
  private String categoryMark;

  /**
   * Creates a new DTNHost.
   *
   * @param msgLs Message listeners
   * @param movLs Movement listeners
   * @param groupId GroupID of this host
   * @param interf List of NetworkInterfaces for the class
   * @param comBus Module communication bus object
   * @param mmProto Prototype of the movement model of this host
   * @param mRouterProto Prototype of the message router of this host
   */
  public DTNHost(
      List<MessageListener> msgLs,
      List<MovementListener> movLs,
      String groupId,
      List<NetworkInterface> interf,
      ModuleCommunicationBus comBus,
      MovementModel mmProto,
      MessageRouter mRouterProto) {
    this.comBus = comBus;
    this.location = new Coord(0, 0);
    this.address = DTNHost.getNextAddress();
    this.name = groupId + this.address;
    this.net = new ArrayList<>();

    for (NetworkInterface i : interf) {
      NetworkInterface ni = i.replicate();
      ni.setHost(this);
      this.net.add(ni);
    }

    // TODO - think about the names of the interfaces and the nodes
    // this.name = groupId + ((NetworkInterface)net.get(1)).getAddress();

    this.msgListeners = msgLs;
    this.movListeners = movLs;

    // create instances by replicating the prototypes
    this.movement = mmProto.replicate();
    this.movement.setComBus(comBus);
    this.movement.setHost(this);
    this.setRouter(mRouterProto.replicate());

    this.location = this.movement.getInitialLocation();

    this.nextTimeToMove = this.movement.nextPathAvailable();
    this.path = null;

    if (movLs != null) { // inform movement listeners about the location
      for (MovementListener l : movLs) {
        l.initialLocation(this, this.location);
      }
    }

    this.interactions = new ArrayList<>();
    this.trusts = new HashMap<>();
    this.social = new HashMap<>();
    this.cDTNHosts = new HashMap<>();
    this.categoryMark = null;
  }

  /**
   * Returns a new network interface address and increments the address for subsequent calls.
   *
   * @return The next address.
   */
  private static synchronized int getNextAddress() {
    return DTNHost.nextAddress++;
  }

  /** Reset the host and its interfaces */
  public static void reset() {
    DTNHost.nextAddress = 0;
  }

  /**
   * Returns true if this node is actively moving (false if not)
   *
   * @return true if this node is actively moving (false if not)
   */
  public boolean isMovementActive() {
    return this.movement.isActive();
  }

  /**
   * Returns true if this node's radio is active (false if not)
   *
   * @return true if this node's radio is active (false if not)
   */
  public boolean isRadioActive() {
    // Radio is active if any of the network interfaces are active.
    for (final NetworkInterface i : this.net) {
      if (i.isActive()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the router of this host
   *
   * @return the router of this host
   */
  public MessageRouter getRouter() {
    return this.router;
  }

  /**
   * Set a router for this host
   *
   * @param router The router to set
   */
  private void setRouter(MessageRouter router) {
    router.init(this, this.msgListeners);
    this.router = router;
  }

  /** Returns the network-layer address of this host. */
  public int getAddress() {
    return this.address;
  }

  /**
   * Returns this hosts's ModuleCommunicationBus
   *
   * @return this hosts's ModuleCommunicationBus
   */
  public ModuleCommunicationBus getComBus() {
    return this.comBus;
  }

  /**
   * Informs the router of this host about state change in a connection object.
   *
   * @param con The connection object whose state changed
   */
  public void connectionUp(Connection con) {
    this.router.changedConnection(con);
    con.setUpTime(SimClock.getTime());
  }

  public void connectionDown(Connection con) {
    this.router.changedConnection(con);
    con.setDownTime(SimClock.getTime());
    if (con.isInitiator(this)) {
      this.interactions.add(
          new InteractionRecord(
              con.getOtherNode(this),
              con.getMsgFromCount(),
              con.getMsgToCount(),
              con.getMsgCreatedByFromCount(),
              con.getMsgCreatedByToCount(),
              con.getUpTime(),
              con.getDownTime()));
    } else {
      this.interactions.add(
          new InteractionRecord(
              con.getOtherNode(this),
              con.getMsgToCount(),
              con.getMsgFromCount(),
              con.getMsgCreatedByToCount(),
              con.getMsgCreatedByFromCount(),
              con.getUpTime(),
              con.getDownTime()));
    }
  }

  /**
   * Returns a copy of the list of connections this host has with other hosts
   *
   * @return a copy of the list of connections this host has with other hosts
   */
  public List<Connection> getConnections() {
    List<Connection> lc = new ArrayList<>();

    for (NetworkInterface i : this.net) {
      lc.addAll(i.getConnections());
    }

    return lc;
  }

  public List<Connection> getConnectionsByInterface(String connecttype) {
    return this.getInterfaceByConnectionType(connecttype).getConnections();
  }
  public List<DTNHost> getNeighbors() {
    List<Connection> lc = this.getConnections();
    List<DTNHost> neighbors = new ArrayList<>();
    for (Connection c : lc) {
      neighbors.add(c.getOtherNode(this));
    }
    return neighbors;
  }

  public List<DTNHost> getNeighborsByInterface(String connecttype) {
    List<Connection> lc = this.getConnectionsByInterface(connecttype);
    List<DTNHost> neighbors = new ArrayList<>();
    for (Connection c : lc) {
      neighbors.add(c.getOtherNode(this));
    }
    return neighbors;
  }

  /**
   * Returns the current location of this host.
   *
   * @return The location
   */
  public Coord getLocation() {
    return this.location;
  }

  /**
   * Sets the Node's location overriding any location set by movement model
   *
   * @param location The location to set
   */
  public void setLocation(Coord location) {
    this.location = location.clone();
  }

  /**
   * Returns the Path this node is currently traveling or null if no path is in use at the moment.
   *
   * @return The path this node is traveling
   */
  public Path getPath() {
    return this.path;
  }

  /**
   * Sets the Node's name overriding the default name (groupId + netAddress)
   *
   * @param name The name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns the messages in a collection.
   *
   * @return Messages in a collection
   */
  public Collection<Message> getMessageCollection() {
    return this.router.getMessageCollection();
  }

  /**
   * Returns the number of messages this node is carrying.
   *
   * @return How many messages the node is carrying currently.
   */
  public int getNrofMessages() {
    return this.router.getNrofMessages();
  }

  /**
   * Returns the buffer occupancy percentage. Occupancy is 0 for empty buffer but can be over 100 if
   * a created message is bigger than buffer space that could be freed.
   *
   * @return Buffer occupancy percentage
   */
  public double getBufferOccupancy() {
    long bSize = this.router.getBufferSize();
    long freeBuffer = this.router.getFreeBufferSize();
    return 100 * ((bSize - freeBuffer) / (bSize * 1.0));
  }

  /**
   * Returns routing info of this host's router.
   *
   * @return The routing info.
   */
  public RoutingInfo getRoutingInfo() {
    return this.router.getRoutingInfo();
  }

  /** Returns the interface objects of the node */
  public List<NetworkInterface> getInterfaces() {
    return this.net;
  }

  /** Find the network interface based on the index */
  public NetworkInterface getInterface(int interfaceNo) {
    NetworkInterface ni = null;
    try {
      ni = this.net.get(interfaceNo - 1);
    } catch (IndexOutOfBoundsException ex) {
      throw new SimError("No such interface: " + interfaceNo + " at " + this);
    }
    return ni;
  }

  /** Find the network interface based on the interfacetype */
  protected NetworkInterface getInterface(String interfacetype) {
    for (NetworkInterface ni : this.net) {
      if (ni.getInterfaceType().equals(interfacetype)) {
        return ni;
      }
    }
    return null;
  }

  protected NetworkInterface getInterfaceByConnectionType(String connecttype) {
    for (NetworkInterface ni : this.net) {
      if (ni.getConnectType().equals(connecttype)) {
        return ni;
      }
    }
    return null;
  }

  /** Force a connection event */
  public void forceConnection(DTNHost anotherHost, String interfaceId, boolean up) {
    NetworkInterface ni;
    NetworkInterface no;

    if (interfaceId != null) {
      ni = this.getInterface(interfaceId);
      no = anotherHost.getInterface(interfaceId);

      assert (ni != null) : "Tried to use a nonexisting interfacetype " + interfaceId;
      assert (no != null) : "Tried to use a nonexisting interfacetype " + interfaceId;
    } else {
      ni = this.getInterface(1);
      no = anotherHost.getInterface(1);

      assert (ni.getInterfaceType().equals(no.getInterfaceType()))
          : "Interface types do not match.  Please specify interface type explicitly";
    }

    if (up) {
      ni.createConnection(no);
    } else {
      ni.destroyConnection(no);
    }
  }

  /** for tests only --- do not use!!! */
  public void connect(DTNHost h) {
    if (DEBUG) {
      Debug.p(
          "WARNING: using deprecated DTNHost.connect"
              + "(DTNHost) Use DTNHost.forceConnection(DTNHost,null,true) instead");
    }
    this.forceConnection(h, null, true);
  }

  /**
   * Updates node's network layer and router.
   *
   * @param simulateConnections Should network layer be updated too
   */
  public void update(boolean simulateConnections) {
    if (!this.isRadioActive()) {
      // Make sure inactive nodes don't have connections
      this.tearDownAllConnections();
      return;
    }

    if (simulateConnections) {
      for (NetworkInterface i : this.net) {
        i.update();
      }
    }
    this.router.update();
  }

  /** Tears down all connections for this host. */
  private void tearDownAllConnections() {
    for (NetworkInterface i : this.net) {
      // Get all connections for the interface
      List<Connection> conns = i.getConnections();
      if (conns.size() == 0) {
        continue;
      }

      // Destroy all connections
      List<NetworkInterface> removeList = new ArrayList<>(conns.size());
      for (Connection con : conns) {
        removeList.add(con.getOtherInterface(i));
      }
      for (NetworkInterface inf : removeList) {
        i.destroyConnection(inf);
      }
    }
  }

  /**
   * Moves the node towards the next waypoint or waits if it is not time to move yet
   *
   * @param timeIncrement How long time the node moves
   */
  public void move(double timeIncrement) {
    double possibleMovement;
    double distance;
    double dx, dy;

    if (!this.isMovementActive() || SimClock.getTime() < this.nextTimeToMove) {
      return;
    }
    if (this.destination == null) {
      if (!this.setNextWaypoint()) {
        return;
      }
    }

    possibleMovement = timeIncrement * this.speed;
    distance = this.location.distance(this.destination);

    while (possibleMovement >= distance) {
      // node can move past its next destination
      this.location.setLocation(this.destination); // snap to destination
      possibleMovement -= distance;
      if (!this.setNextWaypoint()) { // get a new waypoint
        this.destination = null; // No more waypoints left, therefore the destination must be null
        return; // no more waypoints left
      }
      distance = this.location.distance(this.destination);
    }

    // move towards the point for possibleMovement amount
    dx = (possibleMovement / distance) * (this.destination.getX() - this.location.getX());
    dy = (possibleMovement / distance) * (this.destination.getY() - this.location.getY());
    this.location.translate(dx, dy);
  }

  /**
   * Sets the next destination and speed to correspond the next waypoint on the path.
   *
   * @return True if there was a next waypoint to set, false if node still should wait
   */
  private boolean setNextWaypoint() {
    if (this.path == null) {
      this.path = this.movement.getPath();
    }

    if (this.path == null || !this.path.hasNext()) {
      this.nextTimeToMove = this.movement.nextPathAvailable();
      this.path = null;
      return false;
    }

    this.destination = this.path.getNextWaypoint();
    this.speed = this.path.getSpeed();

    if (this.movListeners != null) {
      for (MovementListener l : this.movListeners) {
        l.newDestination(this, this.destination, this.speed);
      }
    }

    return true;
  }

  /**
   * Sends a message from this host to another host
   *
   * @param id Identifier of the message
   * @param to Host the message should be sent to
   */
  public void sendMessage(String id, DTNHost to) {
    this.router.sendMessage(id, to);
  }

  /**
   * Start receiving a message from another host
   *
   * @param m The message
   * @param from Who the message is from
   * @return The value returned by {@link MessageRouter#receiveMessage(Message, DTNHost)}
   */
  public int receiveMessage(Message m, DTNHost from) {
    int retVal = this.router.receiveMessage(m, from);

    if (retVal == MessageRouter.RCV_OK) {
      m.addNodeOnPath(this); // add this node on the messages path
    }

    return retVal;
  }

  /**
   * Requests for deliverable message from this host to be sent trough a connection.
   *
   * @param con The connection to send the messages trough
   * @return True if this host started a transfer, false if not
   */
  public boolean requestDeliverableMessages(Connection con) {
    return this.router.requestDeliverableMessages(con);
  }

  /**
   * Informs the host that a message was successfully transferred.
   *
   * @param id Identifier of the message
   * @param from From who the message was from
   */
  public void messageTransferred(String id, DTNHost from) {
    this.router.messageTransferred(id, from);
  }

  /**
   * Informs the host that a message transfer was aborted.
   *
   * @param id Identifier of the message
   * @param from From who the message was from
   * @param bytesRemaining Nrof bytes that were left before the transfer would have been ready; or
   *     -1 if the number of bytes is not known
   */
  public void messageAborted(String id, DTNHost from, int bytesRemaining) {
    this.router.messageAborted(id, from, bytesRemaining);
  }

  /**
   * Creates a new message to this host's router
   *
   * @param m The message to create
   */
  public void createNewMessage(Message m) {
    this.router.createNewMessage(m);
  }

  /**
   * Deletes a message from this host
   *
   * @param id Identifier of the message
   * @param drop True if the message is deleted because of "dropping" (e.g. buffer is full) or false
   *     if it was deleted for some other reason (e.g. the message got delivered to final
   *     destination). This effects the way the removing is reported to the message listeners.
   */
  public void deleteMessage(String id, boolean drop) {
    this.router.deleteMessage(id, drop);
  }

  /**
   * Returns a string presentation of the host.
   *
   * @return Host's name
   */
  @Override
  public String toString() {
    return this.name;
  }

  /**
   * Checks if a host is the same as this host by comparing the object reference
   *
   * @param otherHost The other host
   * @return True if the hosts objects are the same object
   */
  public boolean equals(DTNHost otherHost) {
    return this == otherHost;
  }

  /**
   * Compares two DTNHosts by their addresses.
   *
   * @see Comparable#compareTo(Object)
   */
  @Override
  public int compareTo(DTNHost h) {
    return this.getAddress() - h.getAddress();
  }

  public Coord getDestination() {
    return destination;
  }

  public List<InteractionRecord> getInteractions() {
    return interactions;
  }

  public Map<DTNHost, Pair<Double, Double>> getTrusts() {
    return trusts;
  }

  public void updateTrust(DTNHost target, double trust, double time) {
    this.trusts.put(target, Pair.of(trust, time));
  }

  public double getTrustForHost(DTNHost target, double defaultTrust) {
    if (this.trusts.containsKey(target)) {
      return this.trusts.get(target).getLeft();
    } else {
      return defaultTrust;
    }
  }

  public Map<DTNHost, Double> getSocial() {
    return social;
  }

  public List<NetworkInterface> getNets() {
    return this.net;
  }

  public Map<Coord,DTNHost> getcDTNHosts(){
    return this.cDTNHosts;
  }
  public void setCategoryMark(String categoryMark) {
    this.categoryMark = categoryMark;
  }
  /**
   * 获得DTNHost的归属类别，比如 ROUTER,VEHICLE
   * */
  public String getCategoryMark() {
    return this.categoryMark;
  }
}
