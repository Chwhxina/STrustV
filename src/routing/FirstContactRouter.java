/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

/**
 * First contact router which uses only a single copy of the message (or fragments) and forwards it
 * to the first available contact.
 */
public class FirstContactRouter extends ActiveRouter {

  /**
   * Constructor. Creates a new message router based on the settings in the given Settings object.
   *
   * @param s The settings object
   */
  public FirstContactRouter(Settings s) {
    super(s);
  }

  /**
   * Copy constructor.
   *
   * @param r The router prototype where setting values are copied from
   */
  protected FirstContactRouter(FirstContactRouter r) {
    super(r);
  }

  @Override
  protected int checkReceiving(Message m, DTNHost from) {
    int recvCheck = super.checkReceiving(m, from);

    if (recvCheck == MessageRouter.RCV_OK) {
      /* don't accept a message that has already traversed this node */
      if (m.getHops().contains(this.getHost())) {
        recvCheck = MessageRouter.DENIED_OLD;
      }
    }

    return recvCheck;
  }

  @Override
  public void update() {
    super.update();
    if (this.isTransferring() || !this.canStartTransfer()) {
      return;
    }

    if (this.exchangeDeliverableMessages() != null) {
      return;
    }

    this.tryAllMessagesToAllConnections();
  }

  @Override
  protected void transferDone(Connection con) {
    /* don't leave a copy for the sender */
    this.deleteMessage(con.getMessage().getId(), false);
  }

  @Override
  public FirstContactRouter replicate() {
    return new FirstContactRouter(this);
  }
}
