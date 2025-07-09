package binance.model.account.margin;

import java.time.LocalDateTime;

public class MarginTransaction {

    private final MarginTransactionType type;
    private final LocalDateTime timestamp;
    private final double quantity;
    private final double price;
    private final String asset;
    private final String currency;
    private final double feeAmount;

    public MarginTransaction(MarginTransactionType type, LocalDateTime timestamp,
                             double quantity, double price, double feeAmount,
                             String asset, String currency) {
        this.type = type;
        this.timestamp = timestamp;
        this.quantity = quantity;
        this.price = price;
        this.asset = asset;
        this.currency = currency;
        this.feeAmount = feeAmount;
    }

    public MarginTransaction(MarginTransactionType type, LocalDateTime timestamp,
                             double quantity, double price,
                             String asset, String currency) {
        this(type, timestamp, quantity, price, 0.0, asset, currency);
    }

    public MarginTransactionType getType() { return type; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public double getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public String getAsset() { return asset; }
    public String getCurrency() { return currency; }
    public double getFeeAmount() { return feeAmount; }

    public double getAmount() { return quantity * price; }

    @Override
    public String toString() {
        return String.format("[%s] %s %.4f %s @ %.2f %s (fee: %.2f)",
                timestamp, type, quantity, asset, price, currency, feeAmount);
    }
}
