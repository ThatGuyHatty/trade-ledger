package com.tradeledger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
public class TradeLedgerStorage
{
    private static final String GROUP = "tradeledger";
    private static final String KEY = "recordedTradesV1";
    private final ConfigManager configManager;
    private final Gson gson;

    @Inject
    public TradeLedgerStorage(ConfigManager configManager, Gson gson)
    {
        this.configManager = configManager;
        this.gson = gson;
    }

    public LoadedData load()
    {
        List<TradeTransaction> purchases = new ArrayList<>();
        List<RecordedSale> sales = new ArrayList<>();
        String raw = configManager.getConfiguration(GROUP, KEY);
        if (raw == null || raw.isEmpty())
        {
            return new LoadedData(purchases, sales);
        }
        try
        {
            JsonObject root = new JsonParser().parse(raw).getAsJsonObject();
            JsonArray buys = root.getAsJsonArray("purchases");
            if (buys != null)
            {
                for (JsonElement element : buys)
                {
                    try
                    {
                        JsonObject o = element.getAsJsonObject();
                        TradeTransaction t = new TradeTransaction(
                            o.get("itemId").getAsInt(), o.get("name").getAsString(),
                            o.get("quantity").getAsInt(), o.get("unitPrice").getAsInt(),
                            o.get("totalCost").getAsLong(), LocalDateTime.parse(o.get("recordedAt").getAsString()));
                        t.setSourceKey(o.get("signature").getAsString());
                        t.restoreRecordedState(o.get("soldQuantity").getAsInt(),
                            o.get("saleRevenue").getAsLong(), o.get("saleTax").getAsLong(),
                            parseOptionalTime(o, "lastSoldAt"), parseOptionalTime(o, "lastActivityAt"),
                            o.get("canceled").getAsBoolean(), o.get("note").getAsString());
                        purchases.add(t);
                    }
                    catch (RuntimeException ex)
                    {
                        log.warn("Skipping invalid saved purchase", ex);
                    }
                }
            }
            JsonArray savedSales = root.getAsJsonArray("sales");
            if (savedSales != null)
            {
                for (JsonElement element : savedSales)
                {
                    try
                    {
                        JsonObject o = element.getAsJsonObject();
                        HistoryTrade trade = new HistoryTrade(HistoryTrade.Kind.SOLD,
                            o.get("itemId").getAsInt(), o.get("name").getAsString(),
                            o.get("quantity").getAsInt(), o.get("net").getAsLong(),
                            o.get("gross").getAsLong(), o.get("tax").getAsLong(),
                            LocalDateTime.parse(o.get("recordedAt").getAsString()));
                        RecordedSale sale = new RecordedSale(trade);
                        sale.setNote(o.get("note").getAsString());
                        sales.add(sale);
                    }
                    catch (RuntimeException ex)
                    {
                        log.warn("Skipping invalid saved sale", ex);
                    }
                }
            }
        }
        catch (RuntimeException ex)
        {
            log.error("Could not read Trade Ledger saved data; leaving it untouched", ex);
            throw new IllegalStateException("Could not load saved Trade Ledger data", ex);
        }
        return new LoadedData(purchases, sales);
    }

    private static LocalDateTime parseOptionalTime(JsonObject object, String key)
    {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : LocalDateTime.parse(value.getAsString());
    }

    public void save(List<TradeTransaction> purchases, List<RecordedSale> sales)
    {
        JsonObject root = new JsonObject();
        JsonArray buys = new JsonArray();
        for (TradeTransaction t : purchases)
        {
            JsonObject o = new JsonObject();
            o.addProperty("itemId", t.getItemId());
            o.addProperty("name", t.getItemName());
            o.addProperty("quantity", t.getQuantityBought());
            o.addProperty("unitPrice", t.getBuyPrice());
            o.addProperty("totalCost", t.getTotalCost());
            o.addProperty("recordedAt", t.getBoughtAt().toString());
            o.addProperty("signature", t.getSourceKey());
            o.addProperty("soldQuantity", t.getQuantitySold());
            o.addProperty("saleRevenue", t.getSaleRevenue());
            o.addProperty("saleTax", t.getSaleTax());
            o.addProperty("lastSoldAt", t.getLastSoldAt() == null ? null : t.getLastSoldAt().toString());
            o.addProperty("lastActivityAt", t.getLastActivityAt().toString());
            o.addProperty("canceled", t.getStatus() == TradeTransaction.Status.CANCELED);
            o.addProperty("note", t.getNote());
            buys.add(o);
        }
        JsonArray sold = new JsonArray();
        for (RecordedSale sale : sales)
        {
            HistoryTrade t = sale.getTrade();
            JsonObject o = new JsonObject();
            o.addProperty("itemId", t.getItemId());
            o.addProperty("name", t.getItemName());
            o.addProperty("quantity", t.getQuantity());
            o.addProperty("net", t.getNetAmount());
            o.addProperty("gross", t.getGrossAmount());
            o.addProperty("tax", t.getTax());
            o.addProperty("recordedAt", t.getRecordedAt().toString());
            o.addProperty("note", sale.getNote());
            sold.add(o);
        }
        root.add("purchases", buys);
        root.add("sales", sold);
        configManager.setConfiguration(GROUP, KEY, gson.toJson(root));
    }

    public static final class LoadedData
    {
        public final List<TradeTransaction> purchases;
        public final List<RecordedSale> sales;

        LoadedData(List<TradeTransaction> purchases, List<RecordedSale> sales)
        {
            this.purchases = purchases;
            this.sales = sales;
        }
    }
}
