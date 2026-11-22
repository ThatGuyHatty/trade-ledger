package com.tradeledger;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(name = "Trade Ledger", description = "Track Grand Exchange trades and flipping profits")
public class TradeLedgerPlugin extends Plugin
{
    // Verified from GE history diagnostic: parent widget 25100291, six children per row.
    private static final int HISTORY_WIDGET_ID = 25100291;
    private static final int ROW_SIZE = 6;
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("([\\d,]+)\\s+coins?");
    private static final Pattern TAX_PATTERN = Pattern.compile("\\(([\\d,]+)\\s*-\\s*([\\d,]+)\\)");

    @Inject private Client client;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ItemManager itemManager;
    @Inject private TradeLedgerConfig config;
    @Inject private TradeLedgerStorage storage;
    @Inject private GeSaleWatcher saleWatcher;

    private TradeLedgerPanel panel;
    private NavigationButton navigationButton;
    private GeOfferProfit offerProfit;
    private final List<GeSaleWatcher.SaleFill> pendingSales = new ArrayList<>();

    @Override
    protected void startUp()
    {
        log.info("Trade Ledger started");
        offerProfit = new GeOfferProfit(client, storage, config);
        saleWatcher.resetMemory();
        SwingUtilities.invokeLater(() ->
        {
            panel = new TradeLedgerPanel(itemManager, storage, config);
            synchronized (pendingSales)
            {
                for (GeSaleWatcher.SaleFill fill : pendingSales)
                {
                    applySaleFill(fill);
                }
                pendingSales.clear();
            }
            navigationButton = NavigationButton.builder()
                .tooltip("Trade Ledger")
                .icon(createIcon())
                .panel(panel)
                .build();
            clientToolbar.addNavigation(navigationButton);
        });
    }

    @Override
    protected void shutDown()
    {
        synchronized (pendingSales)
        {
            pendingSales.clear();
        }
        if (offerProfit != null)
        {
            offerProfit.reset();
            offerProfit = null;
        }
        SwingUtilities.invokeLater(() ->
        {
            if (navigationButton != null)
            {
                clientToolbar.removeNavigation(navigationButton);
                navigationButton = null;
            }
            panel = null;
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"tradeledger".equals(event.getGroup()))
        {
            return;
        }
        SwingUtilities.invokeLater(() ->
        {
            if (panel != null)
            {
                panel.refreshOverviewSettings();
            }
        });
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
    {
        // During login the client can temporarily clear all GE slots.
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        GeSaleWatcher.SaleFill fill = saleWatcher.observe(event);
        if (fill == null)
        {
            return;
        }
        SwingUtilities.invokeLater(() ->
        {
            synchronized (pendingSales)
            {
                if (panel == null)
                {
                    pendingSales.add(fill);
                }
                else
                {
                    applySaleFill(fill);
                }
            }
        });
    }

    private void applySaleFill(GeSaleWatcher.SaleFill fill)
    {
        int matched = panel.recordSaleFill(fill.itemId, fill.quantity, fill.gross);
        if (matched < fill.quantity)
        {
            log.info("GE sale for item {}: matched {} of {} items; the rest had no recorded purchase",
                fill.itemId, matched, fill.quantity);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (offerProfit != null)
        {
            offerProfit.update();
        }
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        MenuEntry selected = null;
        for (MenuEntry entry : event.getMenuEntries())
        {
            if (entry.getParam1() == HISTORY_WIDGET_ID
                && entry.getParam0() >= 0
                && entry.getParam0() % ROW_SIZE == 0
                && "Buy-offer".equals(entry.getOption()))
            {
                selected = entry;
                break;
            }
        }
        if (selected == null)
        {
            return;
        }

        final int rowStart = selected.getParam0();
        final String target = selected.getTarget();
        client.getMenu().createMenuEntry(-1)
            .setOption("Record Trade")
            .setTarget(target)
            .setType(MenuAction.RUNELITE)
            .onClick(clicked -> recordSelectedRow(rowStart));
    }

    private void recordSelectedRow(int rowStart)
    {
        try
        {
            HistoryTrade trade = readHistoryRow(rowStart);
            if (trade == null)
            {
                log.warn("Could not read GE history row {}", rowStart / ROW_SIZE);
                return;
            }
            log.info("Recording {} {} x {}: net={}, gross={}, tax={}",
                trade.getKind(), trade.getQuantity(), trade.getItemName(),
                trade.getNetAmount(), trade.getGrossAmount(), trade.getTax());
            SwingUtilities.invokeLater(() ->
            {
                if (panel != null)
                {
                    panel.recordHistoryTrade(trade);
                }
            });
        }
        catch (RuntimeException ex)
        {
            log.error("Failed to record GE history entry", ex);
        }
    }

    private HistoryTrade readHistoryRow(int rowStart)
    {
        Widget parent = client.getWidget(HISTORY_WIDGET_ID);
        if (parent == null)
        {
            return null;
        }
        Widget[] children = parent.getChildren();
        if (children == null || rowStart < 0 || rowStart + 5 >= children.length)
        {
            return null;
        }
        Widget action = children[rowStart];
        Widget typeWidget = children[rowStart + 2];
        Widget nameWidget = children[rowStart + 3];
        Widget itemWidget = children[rowStart + 4];
        Widget valueWidget = children[rowStart + 5];
        if (action == null || typeWidget == null || nameWidget == null
            || itemWidget == null || valueWidget == null)
        {
            return null;
        }

        String typeText = stripTags(typeWidget.getText()).trim();
        HistoryTrade.Kind kind;
        if ("Bought:".equals(typeText))
        {
            kind = HistoryTrade.Kind.BOUGHT;
        }
        else if ("Sold:".equals(typeText))
        {
            kind = HistoryTrade.Kind.SOLD;
        }
        else
        {
            return null;
        }

        int itemId = itemWidget.getItemId();
        int quantity = itemWidget.getItemQuantity();
        String name = stripTags(nameWidget.getText()).split("\\n", 2)[0].trim();
        if (name.isEmpty())
        {
            name = stripTags(action.getName()).trim();
        }
        String valueText = stripTags(valueWidget.getText());
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(valueText);
        if (!amountMatcher.find())
        {
            return null;
        }
        long net = parseAmount(amountMatcher.group(1));
        long gross = net;
        long tax = 0;
        if (kind == HistoryTrade.Kind.SOLD)
        {
            Matcher taxMatcher = TAX_PATTERN.matcher(valueText);
            if (taxMatcher.find())
            {
                gross = parseAmount(taxMatcher.group(1));
                tax = parseAmount(taxMatcher.group(2));
            }
        }
        if (name.isEmpty() || itemId <= 0 || quantity <= 0)
        {
            return null;
        }
        return new HistoryTrade(kind, itemId, name, quantity, net, gross, tax, LocalDateTime.now());
    }

    private static long parseAmount(String value)
    {
        return Long.parseLong(value.replace(",", ""));
    }

    private static String stripTags(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]*>", "");
    }

    private BufferedImage createIcon()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(Color.WHITE);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawRoundRect(2, 1, 11, 14, 2, 2);
            graphics.drawLine(5, 5, 11, 5);
            graphics.drawLine(5, 8, 11, 8);
            graphics.drawLine(5, 11, 9, 11);
        }
        finally
        {
            graphics.dispose();
        }
        return image;
    }

    @Provides
    TradeLedgerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(TradeLedgerConfig.class);
    }
}
