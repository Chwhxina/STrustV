/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import util.Tuple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SourceSprayAndWaitRouter extends ActiveRouter {

  /**
   * identifier for the initial number of copies setting ({@value})
   */
  public static final String NROF_COPIES = "nrofCopies";
  /** identifier for the binary-mode setting ({@value})*/
  /**
   * SprayAndWait router's settings name space ({@value})
   */
  public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
  /**
   * Message property key
   */
  public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." + "copies";

  protected int initialNrofCopies;

  public SourceSprayAndWaitRouter(Settings s) {
    super(s);
    Settings snwSettings = new Settings(SPRAYANDWAIT_NS);

    initialNrofCopies = snwSettings.getInt(NROF_COPIES);
  }

  /**
   * Copy constructor.
   *
   * @param r The router prototype where setting values are copied from
   */
  protected SourceSprayAndWaitRouter(SourceSprayAndWaitRouter r) {
    super(r);
    this.initialNrofCopies = r.initialNrofCopies;
  }

  @Override
  public int receiveMessage(Message m, DTNHost from) {
    return super.receiveMessage(m, from);
  }

  @Override
  public Message messageTransferred(String id, DTNHost from) {
    Message msg = super.messageTransferred(id, from);
    Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
    assert nrofCopies != null : "Not a SnW message: " + msg;
    nrofCopies = 1;
    msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
    return msg;
  }

  @Override
  public boolean createNewMessage(Message msg) {
    makeRoomForNewMessage(msg.getSize());

    msg.setTtl(this.msgTtl);
    msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
    addToMessages(msg, true);
    return true;
  }

  @Override
  public void update() {
    super.update();

    if (!canStartTransfer() || isTransferring()) {
      return; // nothing to transfer or is currently transferring
    }

    /* try messages that could be delivered to final recipient */
    if (exchangeDeliverableMessages() != null) {
      return;
    }

    tryOtherMessages();
  }

  public Tuple<Message, Connection> tryOtherMessages() {
    Collection<Message> msgCollection = getMessageCollection();
    List<Tuple<Message, Connection>> highprior = new ArrayList<Tuple<Message, Connection>>();

    for (Connection c : getConnections()) {
      DTNHost other = c.getOtherNode(getHost());
      SourceSprayAndWaitRouter otherRouter = (SourceSprayAndWaitRouter) other.getRouter();

      for (Message m : msgCollection) {
        if (otherRouter.hasMessage(m.getId())) {
          continue;
        }

        if (otherRouter.isTransferring()) {
          continue;
        }

        /*For Skip Stationary Destination*/
        if (super.Skip == true) {
          if (other.name.contains("DES")) {
            continue;
          }
        }
        /*For Skip Stationary Destination*/

        Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);

        if (nrofCopies > 1) {

          highprior.add(new Tuple<Message, Connection>(m, c));
        }

      }
    }

    Collections.sort(highprior, new TupleComparator());
    return tryMessagesForConnected(highprior);
  }

  private class TupleComparator implements Comparator<Tuple<Message, Connection>> {

    public int compare(Tuple<Message, Connection> tuple1, Tuple<Message, Connection> tuple2) {
      double p1 = tuple1.getKey().getReceiveTime();
      double p2 = tuple2.getKey().getReceiveTime();

      if (p1 - p2 == 0) {
        return -1;
      } else if (p1 - p2 < 0) {
        return -1;
      } else {
        return 1;
      }
    }
  }

  /**
   * Creates and returns a list of messages this router is currently carrying and still has copies
   * left to distribute (nrof copies > 1).
   *
   * @return A list of messages that have copies left
   */
  protected List<Message> getMessagesWithCopiesLeft() {
    List<Message> list = new ArrayList<Message>();

    for (Message m : getMessageCollection()) {
      Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
      assert nrofCopies != null : "SnW message " + m + " didn't have " + "nrof copies property!";
      if (nrofCopies > 1) {
        list.add(m);
      }
    }

    return list;
  }

  /**
   * Called just before a transfer is finalized (by {@link ActiveRouter#update()}). Reduces the
   * number of copies we have left for a message. In binary Spray and Wait, sending host is left
   * with floor(n/2) copies, but in standard mode, nrof copies left is reduced by one.
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
  }

  @Override
  public SourceSprayAndWaitRouter replicate() {
    return new SourceSprayAndWaitRouter(this);
  }
}
