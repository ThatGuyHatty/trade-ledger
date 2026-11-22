package com.tradeledger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

/**
 * Displays estimated FIFO profit on the GE sell-offer total line.
 * All widget operations must occur on the RuneLite client thread.
 */
@Slf4j
public final class GeOfferProfit
{
    // Confirmed from the user's GE widget diagnostics.
    private static final int OFFER_GROUP = 465;
    private static final int OFFER_PARENT_CHILD = 26;
    private static final int ITEM_CHILD = 23;
    private static final int OFFER_TYPE_CHILD = 20;
    private static final int TOTAL_CHILD = 45;

    // Geometry adjustments are applied only when multiline is enabled.
    private static final int EXTRA_HEIGHT = 15;
    private static final int MOVE_UP = 5;

    private static final Pattern NET_TOTAL = Pattern.compile(
        "^\\s*([\\d,]+)\\s+coins?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OUR_SUFFIX = Pattern.compile(
        "(?s)(?:<br>|\\s+)\\s*<col=(?:00ff00|ff4040|ffff00)>"
            + "(?:Expected )?Profit:.*$", Pattern.CASE_INSENSITIVE);

    private final Client client;
    private final TradeLedgerStorage storage;
    private final TradeLedgerConfig config;

    private Widget lastTotalWidget;
    private int originalHeight;
    private int originalY;
    private boolean geometryChanged;

    public GeOfferProfit(Client client, TradeLedgerStorage storage, TradeLedgerConfig config)
    {
        this.client = client;
        this.storage = storage;
        this.config = config;
    }

    public void update()
    {
        Widget parent = client.getWidget(OFFER_GROUP, OFFER_PARENT_CHILD);
        Widget type = child(parent, OFFER_TYPE_CHILD);
        Widget item = child(parent, ITEM_CHILD);
        Widget total = child(parent, TOTAL_CHILD);

        if (parent == null || parent.isHidden() || type == null || item == null
            || total == null || total.isHidden()
            || !"Sell offer".equalsIgnoreCase(clean(type.getText()).trim())
            || item.getItemId() <= 0 || item.getItemQuantity() <= 0)
        {
            clear();
            return;
        }

        if (total != lastTotalWidget)
        {
            clear();
            lastTotalWidget = total;
            originalHeight = total.getOriginalHeight();
            originalY = total.getOriginalY();
            geometryChanged = false;
        }

        String currentText = total.getText();
        if (currentText == null)
        {
            clear();
            return;
        }

        // The GE's clientscript can replace the text whenever the price or
        // quantity changes. Strip only our own suffix and preserve its value.
        String baseText = OUR_SUFFIX.matcher(currentText).replaceFirst("");
        Matcher totalMatcher = NET_TOTAL.matcher(clean(baseText));
        if (!totalMatcher.find())
        {
            restoreTextOnly(total, baseText);
            return;
        }

        final long proceeds;
        try
        {
            proceeds = Long.parseLong(totalMatcher.group(1).replace(",", ""));
        }
        catch (NumberFormatException ex)
        {
            restoreTextOnly(total, baseText);
            return;
        }

        int itemId = item.getItemId();
        int quantity = item.getItemQuantity();
        Long cost = calculateFifoCost(itemId, quantity);
        if (cost == null)
        {
            restoreTextOnly(total, baseText);
            return;
        }

        long profit = proceeds - cost;
        String color = profit > 0 ? "00ff00" : profit < 0 ? "ff4040" : "ffff00";
        String amount = String.format(Locale.US, "%,d", profit);
        String value = profit > 0 ? "+" + amount : amount;
        boolean multiline = config.multilineProfit();
        String separator = multiline ? "<br>" : " ";
        String desired = baseText + separator + "<col=" + color
            + ">Profit: " + value + " GP</col>";

        if (!multiline)
        {
            restoreGeometry(total);
        }
        else if (!geometryChanged)
        {
            total.setOriginalHeight(originalHeight + EXTRA_HEIGHT);
            total.setOriginalY(originalY - MOVE_UP);
            total.revalidate();
            geometryChanged = true;
        }
        if (!desired.equals(currentText))
        {
            total.setText(desired);
        }
    }

    /**
     * Allocate cost from oldest non-canceled purchases first. Uses the same
     * integer cost allocation as TradeTransaction.getRemainingCost(), so
     * partial sales of uneven unit costs do not introduce rounding drift.
     */
    private Long calculateFifoCost(int itemId, int quantity)
    {
        final List<TradeTransaction> purchases;
        try
        {
            purchases = new ArrayList<>(storage.load().purchases);
        }
        catch (RuntimeException ex)
        {
            log.warn("Could not load recorded purchases for GE profit estimate", ex);
            return null;
        }

        purchases.sort(Comparator.comparing(TradeTransaction::getBoughtAt));
        int needed = quantity;
        long cost = 0;
        for (TradeTransaction purchase : purchases)
        {
            if (purchase.getItemId() != itemId || !purchase.isIncludedInOverview())
            {
                continue;
            }
            int available = purchase.getQuantityRemaining();
            if (available <= 0)
            {
                continue;
            }
            int take = Math.min(needed, available);
            long remainingAfter = purchase.getTotalCost()
                * (available - take) / purchase.getQuantityBought();
            cost += purchase.getRemainingCost() - remainingAfter;
            needed -= take;
            if (needed == 0)
            {
                return cost;
            }
        }
        return null;
    }

    private static Widget child(Widget parent, int index)
    {
        if (parent == null)
        {
            return null;
        }
        Widget[] children = parent.getChildren();
        return children != null && index < children.length ? children[index] : null;
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.replaceAll("<[^>]*>", "");
    }

    private void restoreTextOnly(Widget widget, String baseText)
    {
        if (!baseText.equals(widget.getText()))
        {
            widget.setText(baseText);
        }
        restoreGeometry(widget);
    }

    private void restoreGeometry(Widget widget)
    {
        if (geometryChanged && widget != null)
        {
            widget.setOriginalHeight(originalHeight);
            widget.setOriginalY(originalY);
            widget.revalidate();
            geometryChanged = false;
        }
    }

    private void clear()
    {
        if (lastTotalWidget != null)
        {
            String text = lastTotalWidget.getText();
            if (text != null)
            {
                String base = OUR_SUFFIX.matcher(text).replaceFirst("");
                if (!base.equals(text))
                {
                    lastTotalWidget.setText(base);
                }
            }
            restoreGeometry(lastTotalWidget);
        }
        lastTotalWidget = null;
    }

    public void reset()
    {
        clear();
    }
}
