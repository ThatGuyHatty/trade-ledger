package com.tradeledger;

import java.time.LocalDateTime;

public final class RecordedSale
{
    private final HistoryTrade trade;
    private String note = "";

    public RecordedSale(HistoryTrade trade)
    {
        if (trade.getKind() != HistoryTrade.Kind.SOLD)
        {
            throw new IllegalArgumentException("Expected a sale");
        }
        this.trade = trade;
    }

    public HistoryTrade getTrade() { return trade; }
    public LocalDateTime getRecordedAt() { return trade.getRecordedAt(); }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note == null ? "" : note; }
}
