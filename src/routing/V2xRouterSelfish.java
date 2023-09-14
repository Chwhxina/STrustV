package routing;

public class V2xRouterSelfish extends V2xRouter {
    V2xRouterSelfish(V2xRouterSelfish r) {
        super(r);
    }

    @Override
    public void update() {
        return;
    }

    @Override
    public V2xRouter replicate() {
        return new V2xRouterSelfish(this);
    }
}
