package fruit.client.features.gameplay;

public final class CatchEffect {
    public final int playerId, delta;
    public final double x, y;
    public final String kind;
    public final long born = System.nanoTime();

    public CatchEffect(int playerId, double x, double y, String kind, int delta) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.kind = kind;
        this.delta = delta;
    }
}
