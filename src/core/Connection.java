/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package core;

import routing.MessageRouter;

/** A connection between two DTN nodes. */
public abstract class Connection {
  protected DTNHost toNode;
  protected NetworkInterface toInterface;
  protected DTNHost fromNode;
  protected NetworkInterface fromInterface;
  protected DTNHost msgFromNode;
  protected Message msgOnFly;
  /** how many bytes this connection has transferred */
  protected int bytesTransferred;
  private boolean isUp;
  private int msgFromCount;
  private int msgToCount;
  private int msgCreatedByFromCount;
  private int msgCreatedByToCount;

  private double upTime;
  private double downTime;

  /**
   * Creates a new connection between nodes and sets the connection state to "up".
   *
   * @param fromNode The node that initiated the connection
   * @param fromInterface The interface that initiated the connection
   * @param toNode The node in the other side of the connection
   * @param toInterface The interface in the other side of the connection
   */
  public Connection(
      DTNHost fromNode,
      NetworkInterface fromInterface,
      DTNHost toNode,
      NetworkInterface toInterface) {
    this.fromNode = fromNode;
    this.fromInterface = fromInterface;
    this.toNode = toNode;
    this.toInterface = toInterface;
    this.isUp = true;
    this.bytesTransferred = 0;
    this.msgFromCount = 0;
    this.msgToCount = 0;
    this.msgCreatedByFromCount = 0;
    this.msgCreatedByToCount = 0;
    this.upTime = 0;
    this.downTime = 0;
  }

  /**
   * Returns true if the connection is up
   *
   * @return state of the connection
   */
  public boolean isUp() {
    return this.isUp;
  }

  /**
   * Returns true if the connections is transferring a message
   *
   * @return true if the connections is transferring a message
   */
  public boolean isTransferring() {
    return this.msgOnFly != null;
  }

  /**
   * Returns true if the given node is the initiator of the connection, false otherwise
   *
   * @param node The node to check
   * @return true if the given node is the initiator of the connection
   */
  public boolean isInitiator(DTNHost node) {
    return node == this.fromNode;
  }

  /**
   * Sets the state of the connection.
   *
   * @param state True if the connection is up, false if not
   */
  public void setUpState(boolean state) {
    this.isUp = state;
  }

  /**
   * Sets a message that this connection is currently transferring. If message passing is controlled
   * by external events, this method is not needed (but then e.g. {@link #finalizeTransfer()} and
   * {@link #isMessageTransferred()} will not work either). Only a one message at a time can be
   * transferred using one connection.
   *
   * @param m The message
   * @return The value returned by {@link MessageRouter#receiveMessage(Message, DTNHost)}
   */
  public abstract int startTransfer(DTNHost from, Message m);

  /**
   * Calculate the current transmission speed from the information given by the interfaces, and
   * calculate the missing data amount.
   */
  public void update() {}

  /** Aborts the transfer of the currently transferred message. */
  public void abortTransfer() {
    assert this.msgOnFly != null : "No message to abort at " + this.msgFromNode;
    int bytesRemaining = this.getRemainingByteCount();

    this.bytesTransferred += this.msgOnFly.getSize() - bytesRemaining;

    this.getOtherNode(this.msgFromNode).messageAborted(this.msgOnFly.getId(), this.msgFromNode, bytesRemaining);
    this.clearMsgOnFly();
  }

  /**
   * Returns the amount of bytes to be transferred before ongoing transfer is ready or 0 if there's
   * no ongoing transfer or it has finished already
   *
   * @return the amount of bytes to be transferred
   */
  public abstract int getRemainingByteCount();

  /**
   * Clears the message that is currently being transferred. Calls to {@link #getMessage()} will
   * return null after this.
   */
  protected void clearMsgOnFly() {
    this.msgOnFly = null;
    this.msgFromNode = null;
  }

  /**
   * Finalizes the transfer of the currently transferred message. The message that was being
   * transferred can <STRONG>not</STRONG> be retrieved from this connections after calling this
   * method (using {@link #getMessage()}).
   */
  public void finalizeTransfer() {
    assert this.msgOnFly != null : "Nothing to finalize in " + this;
    assert this.msgFromNode != null : "msgFromNode is not set";

    this.bytesTransferred += this.msgOnFly.getSize();

    this.getOtherNode(this.msgFromNode).messageTransferred(this.msgOnFly.getId(), this.msgFromNode);

    if (this.msgFromNode.equals(this.fromNode)) {
      this.msgFromCount++;
    } else {
      this.msgToCount++;
    }

    if (this.msgOnFly.getFrom().equals(this.fromNode)) {
      this.msgCreatedByFromCount++;
    } else if (this.msgOnFly.getFrom().equals(this.toNode)){
      this.msgCreatedByToCount++;
    }

    this.clearMsgOnFly();
  }

  /**
   * Returns true if the current message transfer is done
   *
   * @return True if the transfer is done, false if not
   */
  public abstract boolean isMessageTransferred();

  /**
   * Returns true if the connection is ready to transfer a message (connection is up and there is no
   * message being transferred).
   *
   * @return true if the connection is ready to transfer a message
   */
  public boolean isReadyForTransfer() {
    return this.isUp && this.msgOnFly == null;
  }

  /**
   * Gets the message that this connection is currently transferring.
   *
   * @return The message or null if no message is being transferred
   */
  public Message getMessage() {
    return this.msgOnFly;
  }

  /** Gets the current connection speed */
  public abstract double getSpeed();

  /**
   * Returns the total amount of bytes this connection has transferred so far (including all
   * transfers).
   */
  public int getTotalBytesTransferred() {
    if (this.msgOnFly == null) {
      return this.bytesTransferred;
    } else {
      if (this.isMessageTransferred()) {
        return this.bytesTransferred + this.msgOnFly.getSize();
      } else {
        return this.bytesTransferred + (this.msgOnFly.getSize() - this.getRemainingByteCount());
      }
    }
  }

  /**
   * Returns the node in the other end of the connection
   *
   * @param node The node in this end of the connection
   * @return The requested node
   */
  public DTNHost getOtherNode(DTNHost node) {
    if (node == this.fromNode) {
      return this.toNode;
    } else {
      return this.fromNode;
    }
  }

  /**
   * Returns the interface in the other end of the connection
   *
   * @param i The interface in this end of the connection
   * @return The requested interface
   */
  public NetworkInterface getOtherInterface(NetworkInterface i) {
    if (i == this.fromInterface) {
      return this.toInterface;
    } else {
      return this.fromInterface;
    }
  }

  /** Returns a String presentation of the connection. */
  @Override
  public String toString() {
    return this.fromNode
        + "<->"
        + this.toNode
        + " ("
        + this.getSpeed() / 1000
        + " kBps) is "
        + (this.isUp() ? "up" : "down")
        + (this.isTransferring() ? " transferring " + this.msgOnFly + " from " + this.msgFromNode : "");
  }

  public int getMsgFromCount() {
    return msgFromCount;
  }

  public int getMsgToCount() {
    return msgToCount;
  }

  public int getMsgCreatedByFromCount() {
    return msgCreatedByFromCount;
  }

  public int getMsgCreatedByToCount() {
    return msgCreatedByToCount;
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
