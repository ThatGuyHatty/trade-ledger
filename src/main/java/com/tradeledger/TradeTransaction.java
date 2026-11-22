package com.tradeledger;

import java.time.LocalDateTime;

public class TradeTransaction
{
    public enum Status { OPEN, COMPLETED, CANCELED }

    private final int itemId;
    private final String itemName;
    private final int quantityBought;
    private final int buyPrice;
    private final long totalCost;
    private final LocalDateTime boughtAt;
    private int quantitySold;
    private long saleRevenue;
    private long saleTax;
    private LocalDateTime lastSoldAt;
    private LocalDateTime lastActivityAt;
    private Status status = Status.OPEN;
    private Status statusBeforeCancellation = Status.OPEN;
    private String note = "";
    private String sourceKey = "";

    public TradeTransaction(int itemId, String itemName, int quantityBought, int buyPrice, LocalDateTime boughtAt)
    {
        this(itemId, itemName, quantityBought, buyPrice, (long) quantityBought * buyPrice, boughtAt);
    }

    public TradeTransaction(int itemId, String itemName, int quantityBought, int buyPrice, long totalCost, LocalDateTime boughtAt)
    {
        if (itemId <= 0 || itemName == null || itemName.isEmpty() || quantityBought <= 0 || buyPrice < 0 || totalCost < 0 || boughtAt == null)
        {
            throw new IllegalArgumentException("Invalid purchase transaction");
        }
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantityBought = quantityBought;
        this.buyPrice = buyPrice;
        this.totalCost = totalCost;
        this.boughtAt = boughtAt;
        this.lastActivityAt = boughtAt;
    }

    /**
     * Record actual gross proceeds and an estimated GE tax for a partial fill.
     * Values are allocated exactly as supplied, without reconstructing them
     * from a rounded per-item average.
     */
    public void recordSaleTotals(int quantity, long gross, long tax, LocalDateTime soldAt)
    {
        if (status != Status.OPEN || quantity <= 0 || quantity > getQuantityRemaining()
            || gross < 0 || tax < 0 || tax > gross || soldAt == null)
        {
            throw new IllegalArgumentException("Invalid sale totals");
        }
        if (soldAt.isBefore(lastActivityAt))
        {
            throw new IllegalArgumentException("Sale timestamp precedes previous activity");
        }
        quantitySold += quantity;
        saleRevenue += gross;
        saleTax += tax;
        lastSoldAt = soldAt;
        lastActivityAt = soldAt;
        if (getQuantityRemaining() == 0)
        {
            status = Status.COMPLETED;
        }
    }

    public void recordSale(int quantity, int pricePerItem, long tax, LocalDateTime soldAt)
    {
        if (pricePerItem < 0)
        {
            throw new IllegalArgumentException("Invalid sale price");
        }
        recordSaleTotals(quantity, (long) quantity * pricePerItem, tax, soldAt);
    }

    public void restoreRecordedState(int soldQuantity, long grossRevenue, long tax, LocalDateTime soldAt, LocalDateTime activityAt, boolean canceled, String note)
    {
        if (soldQuantity < 0 || soldQuantity > quantityBought || grossRevenue < 0 || tax < 0)
        {
            throw new IllegalArgumentException("Invalid saved trade");
        }
        quantitySold = soldQuantity;
        saleRevenue = grossRevenue;
        saleTax = tax;
        lastSoldAt = soldAt;
        lastActivityAt = activityAt == null ? boughtAt : activityAt;
        status = soldQuantity == quantityBought ? Status.COMPLETED : Status.OPEN;
        statusBeforeCancellation = status;
        if (canceled)
        {
            status = Status.CANCELED;
        }
        this.note = note == null ? "" : note;
    }

    public void cancel()
    {
        if (status != Status.CANCELED)
        {
            statusBeforeCancellation = status;
            status = Status.CANCELED;
        }
    }

    public void restore()
    {
        if (status == Status.CANCELED)
        {
            status = statusBeforeCancellation;
        }
    }

    public int getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public int getQuantityBought() { return quantityBought; }
    public int getBuyPrice() { return buyPrice; }
    public LocalDateTime getBoughtAt() { return boughtAt; }
    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public int getQuantitySold() { return quantitySold; }
    public int getQuantityRemaining() { return quantityBought - quantitySold; }
    public long getTotalCost() { return totalCost; }
    public long getRemainingCost() { return totalCost * getQuantityRemaining() / quantityBought; }
    public long getSaleRevenue() { return saleRevenue; }
    public long getSaleTax() { return saleTax; }
    public long getRealizedProfit() { return saleRevenue - saleTax - (totalCost - getRemainingCost()); }
    public LocalDateTime getLastSoldAt() { return lastSoldAt; }
    public Status getStatus() { return status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note == null ? "" : note; }
    public boolean isIncludedInOverview() { return status != Status.CANCELED; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey == null ? "" : sourceKey; }
}
