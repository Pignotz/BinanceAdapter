package binance.model.account.margin;

import java.util.HashMap;
import java.util.Map;

public class PortforlioTracker {
    private final Map<String, PositionTracker> positions = new HashMap<>();

    public void process(MarginTransaction tx) {
        positions.computeIfAbsent(tx.getAsset(), PositionTracker::new)
                 .process(tx);
    }

    public double getRealizedPnL(String asset) {
        return positions.getOrDefault(asset, new PositionTracker(asset)).getRealizedPnL();
    }
}
