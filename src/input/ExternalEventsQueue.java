/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package input;

import core.Settings;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Queue of external events. This class also takes care of buffering the events and preloading only
 * a proper amount of them.
 */
public class ExternalEventsQueue implements EventQueue {
  /** ExternalEvents namespace ({@value}) */
  public static final String SETTINGS_NAMESPACE = "ExternalEvents";
  /** number of event to preload -setting id ({@value}) */
  public static final String PRELOAD_SETTING = "nrofPreload";
  /** path of external events file -setting id ({@value}) */
  public static final String PATH_SETTING = "filePath";

  /** default number of preloaded events */
  public static final int DEFAULT_NROF_PRELOAD = 500;

  private File eventsFile;
  private ExternalEventsReader reader;
  private int nextEventIndex;
  private int nrofPreload;
  private List<ExternalEvent> queue;
  private boolean allEventsRead = false;

  /**
   * Creates a new Queue from a file
   *
   * @param filePath Path to the file where the events are read from. If file ends with extension
   *     defined in {@link BinaryEventsReader#BINARY_EXT} the file is assumed to be a binary file.
   * @param nrofPreload How many events to preload
   * @see BinaryEventsReader#BINARY_EXT
   * @see BinaryEventsReader#storeToBinaryFile(String, List)
   */
  public ExternalEventsQueue(String filePath, int nrofPreload) {
    this.setNrofPreload(nrofPreload);
    this.init(filePath);
  }

  /**
   * Create a new Queue based on the given settings: {@link #PRELOAD_SETTING} and {@link
   * #PATH_SETTING}. The path setting supports value filling.
   *
   * @param s The settings
   */
  public ExternalEventsQueue(Settings s) {
    if (s.contains(ExternalEventsQueue.PRELOAD_SETTING)) {
      this.setNrofPreload(s.getInt(ExternalEventsQueue.PRELOAD_SETTING));
    } else {
      this.setNrofPreload(ExternalEventsQueue.DEFAULT_NROF_PRELOAD);
    }
    String eeFilePath = s.valueFillString(s.getSetting(ExternalEventsQueue.PATH_SETTING));
    this.init(eeFilePath);
  }

  /**
   * Sets maximum number of events that are read when the next preload occurs
   *
   * @param nrof Maximum number of events to read. If less than 1, default value ( {@value
   *     DEFAULT_NROF_PRELOAD} ) is used.
   */
  public void setNrofPreload(int nrof) {
    if (nrof < 1) {
      nrof = ExternalEventsQueue.DEFAULT_NROF_PRELOAD;
    }
    this.nrofPreload = nrof;
  }

  private void init(String eeFilePath) {
    this.eventsFile = new File(eeFilePath);

    if (BinaryEventsReader.isBinaryEeFile(this.eventsFile)) {
      this.reader = new BinaryEventsReader(this.eventsFile);
    } else {
      this.reader = new StandardEventsReader(this.eventsFile);
    }

    this.queue = this.readEvents(this.nrofPreload);
    this.nextEventIndex = 0;
  }

  /**
   * Returns next event's time or Double.MAX_VALUE if there are no events left
   *
   * @return Next event's time
   */
  @Override
  public double nextEventsTime() {
    if (this.eventsLeftInBuffer() <= 0) {
      // in case user request time of an event that doesn't exist
      return Double.MAX_VALUE;
    } else {
      return this.queue.get(this.nextEventIndex).getTime();
    }
  }

  /**
   * Returns the next event in the queue or ExternalEvent with time of double.MAX_VALUE if there are
   * no events left
   *
   * @return The next event
   */
  @Override
  public ExternalEvent nextEvent() {
    if (this.queue.size() == 0) { // no more events
      return new ExternalEvent(Double.MAX_VALUE);
    }

    ExternalEvent ee = this.queue.get(this.nextEventIndex);
    this.nextEventIndex++;

    if (this.nextEventIndex >= this.queue.size()) { // ran out of events
      this.queue = this.readEvents(this.nrofPreload);
      this.nextEventIndex = 0;
    }

    return ee;
  }

  /**
   * Returns the amount of events left in the buffer at the moment (the amount can increase later if
   * more events are read).
   *
   * @return The amount of events left or 0 there aren't any events
   */
  public int eventsLeftInBuffer() {
    if (this.queue == null || this.queue.size() == 0) {
      return 0;
    } else {
      return this.queue.size() - this.nextEventIndex;
    }
  }

  /**
   * Read some events from the external events reader
   *
   * @param nrof Maximum number of events to read
   * @return A List of events that were read or an empty list if no events could be read
   */
  private List<ExternalEvent> readEvents(int nrof) {
    if (this.allEventsRead) {
      return new ArrayList<>(0);
    }

    List<ExternalEvent> events = this.reader.readEvents(nrof);

    if (nrof > 0 && events.size() == 0) {
      this.reader.close();
      this.allEventsRead = true;
    }

    return events;
  }
}
