package com.tradeledger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("tradeledger")
public interface TradeLedgerConfig extends Config
{
    @ConfigItem(
        keyName = "multilineProfit",
        name = "Multiline profit display",
        description = "Show the estimated profit below the GE sell total instead of on the same line",
        position = 0
    )
    default boolean multilineProfit()
    {
        return false;
    }
    @ConfigItem(
        keyName = "showGpInvested",
        name = "Show GP Invested",
        description = "Show the purchase cost of remaining unsold items",
        position = 1
    )
    default boolean showGpInvested() { return true; }

    @ConfigItem(
        keyName = "showRealizedProfit",
        name = "Show Realized profit",
        description = "Show realized profit from matched sales",
        position = 2
    )
    default boolean showRealizedProfit() { return true; }

    @ConfigItem(
        keyName = "showItemsRemaining",
        name = "Show Items remaining",
        description = "Show the total number of unsold recorded items",
        position = 3
    )
    default boolean showItemsRemaining() { return true; }

    @ConfigItem(
        keyName = "showTradeCounts",
        name = "Show Open / completed",
        description = "Show the number of open and completed purchases",
        position = 4
    )
    default boolean showTradeCounts() { return true; }
}
