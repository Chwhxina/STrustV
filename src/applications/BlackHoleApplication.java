package applications;

import core.Application;
import core.DTNHost;
import core.Message;
import core.Settings;

import java.util.Random;

public class BlackHoleApplication extends Application {
    public static final String DROP_PROB = "dropProb";
    public static final String SEED = "seed";
    public static final String APP_ID = "attacker.BlackHole";
    private final Random rng;
    private double dropProb = 1.0;
    private int seed = 0;

    public BlackHoleApplication(Settings s) {
        if (s.contains(DROP_PROB)) {
            this.dropProb = s.getDouble(DROP_PROB);
        }
        if (s.contains(SEED)) {
            this.seed = s.getInt(SEED);
        }
        rng = new Random(this.seed);
        super.setAppID(APP_ID);
    }

    public BlackHoleApplication(BlackHoleApplication a) {
        super(a);
        this.dropProb = a.getDropProb();
        this.seed = a.getSeed();
        this.rng = new Random(this.seed);
    }

    @Override
    public Message beforeSending(Message msg, DTNHost host, DTNHost anotherHost) {
        if (rng.nextDouble() < dropProb) {
            super.sendEventToListeners("MessageDropped", null, anotherHost);
            return null;
        } else {
            return msg;
        }
    }

    @Override
    public Application replicate() {
        return new BlackHoleApplication(this);
    }

    public int getSeed() {
        return seed;
    }

    public void setSeed(int seed) {
        this.seed = seed;
    }

    public double getDropProb() {
        return this.dropProb;
    }

    public void setDropProb(double p) {
        this.dropProb = p;
    }
}
