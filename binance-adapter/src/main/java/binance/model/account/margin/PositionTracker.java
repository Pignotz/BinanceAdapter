package binance.model.account.margin;

import java.util.ArrayDeque;
import java.util.Deque;

public class PositionTracker {

    private final String asset;
    private final Deque<MarginTransaction> buyStack = new ArrayDeque<>();
    private double realizedPnL = 0.0;

    public PositionTracker(String asset) {
        this.asset = asset;
    }

    public void process(MarginTransaction tx) {
        switch (tx.getType()) {
            case BUY -> buyStack.push(tx);
            case SELL -> matchSell(tx);
		default -> throw new IllegalArgumentException("Unexpected value: " + tx.getType());
        }
    }

    private void matchSell(MarginTransaction sellTx) {
        double qtyToSell = sellTx.getQuantity();
        double sellPrice = sellTx.getPrice();
        double sellFee = sellTx.getFeeAmount();

        while (qtyToSell > 0 && !buyStack.isEmpty()) {
            MarginTransaction lastBuy = buyStack.pop();
            double buyQty = lastBuy.getQuantity();
            double matchedQty = Math.min(qtyToSell, buyQty);

            double buyCost = lastBuy.getPrice() * matchedQty;
            double buyFeePerUnit = lastBuy.getFeeAmount() / lastBuy.getQuantity();
            double totalBuyFee = matchedQty * buyFeePerUnit;

            double sellRevenue = sellPrice * matchedQty;
            double sellFeeProRata = sellFee * (matchedQty / sellTx.getQuantity());

            double pnl = (sellRevenue - sellFeeProRata) - (buyCost + totalBuyFee);
            realizedPnL += pnl;

            System.out.printf("[LIFO] PnL %.4f %s: %.2f (fee %.2f)\n",
                    matchedQty, asset, pnl, totalBuyFee + sellFeeProRata);

            qtyToSell -= matchedQty;

            if (buyQty > matchedQty) {
                buyStack.push(new MarginTransaction(
                        MarginTransactionType.BUY,
                        lastBuy.getTimestamp(),
                        buyQty - matchedQty,
                        lastBuy.getPrice(),
                        lastBuy.getFeeAmount() * ((buyQty - matchedQty) / buyQty),
                        lastBuy.getAsset(),
                        lastBuy.getCurrency()
                ));
            }
        }

        if (qtyToSell > 0) {
            System.out.printf("Warning: sold %.4f %s without enough buys.\n",
                    qtyToSell, asset);
        }
    }

    public double getRealizedPnL() {
        return realizedPnL;
    }
}
