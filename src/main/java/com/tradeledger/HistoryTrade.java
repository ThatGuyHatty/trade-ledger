package com.tradeledger;

import java.time.LocalDateTime;
import java.util.Objects;

public final class HistoryTrade
{
    public enum Kind { BOUGHT, SOLD }

    private final Kind kind;
    private final int itemId;
    private final String itemName;
    private final int quantity;
    private final long netAmount;
    private final long grossAmount;
    private final long tax;
    private final LocalDateTime recordedAt;

    public HistoryTrade(Kind kind, int itemId, String itemName, int quantity,
                        long netAmount, long grossAmount, long tax, LocalDateTime recordedAt)
    {
        this.kind = Objects.requireNonNull(kind);
        this.itemId = itemId;
        this.itemName = Objects.requireNonNull(itemName);
        this.quantity = quantity;
        this.netAmount = netAmount;
        this.grossAmount = grossAmount;
        this.tax = tax;
        this.recordedAt = Objects.requireNonNull(recordedAt);
        if (itemId <= 0 || quantity <= 0 || netAmount < 0 || grossAmount < 0 || tax < 0
            || (kind == Kind.BOUGHT && (grossAmount != netAmount || tax != 0))
            || (kind == Kind.SOLD && grossAmount - tax != netAmount))
        {
            throw new IllegalArgumentException("Invalid history transaction");
        }
    }

    // GE history doesn't provide a stable transaction ID. Identical trades are ambiguous.
    public String getSignature()
    {
        return kind + ":" + itemId + ":" + quantity + ":" + netAmount + ":" + grossAmount + ":" + tax;
    }

    public Kind getKind() { return kind; }
    public int getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public long getNetAmount() { return netAmount; }
    public long getGrossAmount() { return grossAmount; }
    public long getTax() { return tax; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}
