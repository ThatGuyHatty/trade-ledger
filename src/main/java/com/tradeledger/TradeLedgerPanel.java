
package com.tradeledger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.ui.PluginPanel;

public class TradeLedgerPanel extends PluginPanel
{
    private static final Color BACKGROUND = new Color(36, 36, 36);
    private static final Color SECONDARY = new Color(190, 190, 190);
    private static final Color PROFIT_COLOR = new Color(80, 190, 95);
    private static final Color LOSS_COLOR = new Color(210, 80, 80);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private final ItemManager itemManager;
    private final List<TradeTransaction> transactions = new ArrayList<>();
    private final List<RecordedSale> recordedSales = new ArrayList<>();
    private final TradeLedgerStorage storage;
    private final TradeLedgerConfig config;

    private final JPanel overviewPanel = new JPanel();
    private final JPanel cardsPanel = new JPanel();

    private final JComboBox<String> filterBox = new JComboBox<>(
            new String[]
                    {
                            "All trades",
                            "Open",
                            "Completed",
                            "Canceled"
                    }
    );

    public TradeLedgerPanel(ItemManager itemManager, TradeLedgerStorage storage, TradeLedgerConfig config)
    {
        super();

        this.itemManager = itemManager;
        this.storage = storage;
        this.config = config;
        TradeLedgerStorage.LoadedData saved = storage.load();
        transactions.addAll(saved.purchases);
        recordedSales.addAll(saved.sales);

        setBackground(BACKGROUND);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 7, 10, 7));

        buildInterface();
        refresh();
    }

    @Override
    public void onActivate()
    {
        refresh();
    }

    @Override
    public void onDeactivate()
    {
    }

    private void buildInterface()
    {
        JLabel title = new JLabel("Trade Ledger");
        title.setForeground(Color.WHITE);
        title.setAlignmentX(LEFT_ALIGNMENT);
        TradeLedgerFonts.regular(title);

        add(title);
        add(Box.createVerticalStrut(7));

        JLabel subtitle = new JLabel("Grand Exchange trading overview");
        subtitle.setForeground(SECONDARY);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        TradeLedgerFonts.regular(subtitle);

        add(subtitle);
        add(Box.createVerticalStrut(14));

        add(createSectionTitle("Overview"));
        add(Box.createVerticalStrut(10));

        overviewPanel.setOpaque(false);
        overviewPanel.setLayout(new GridLayout(0, 1, 0, 8));
        overviewPanel.setAlignmentX(LEFT_ALIGNMENT);
        overviewPanel.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 340)
        );

        add(overviewPanel);
        add(Box.createVerticalStrut(15));

        JPanel managementPanel = new JPanel();
        managementPanel.setOpaque(false);
        managementPanel.setLayout(
                new BoxLayout(managementPanel, BoxLayout.Y_AXIS)
        );
        managementPanel.setAlignmentX(LEFT_ALIGNMENT);

        JButton clearButton = new JButton("Clear history");
        TradeLedgerFonts.regular(clearButton);
        clearButton.setAlignmentX(LEFT_ALIGNMENT);
        clearButton.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 42)
        );
        clearButton.setToolTipText("Delete all transaction records");
        clearButton.addActionListener(event -> clearHistory());

        JButton recalculateButton = new JButton("Recalculate");
        TradeLedgerFonts.regular(recalculateButton);
        recalculateButton.setAlignmentX(LEFT_ALIGNMENT);
        recalculateButton.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 42)
        );
        recalculateButton.setToolTipText(
                "Rebuild the overview from the transaction records"
        );
        recalculateButton.addActionListener(event -> refresh());

        managementPanel.add(clearButton);
        managementPanel.add(Box.createVerticalStrut(6));
        managementPanel.add(recalculateButton);

        add(managementPanel);
        add(Box.createVerticalStrut(20));

        JLabel tradesTitle = createSectionTitle("Trades");
        add(tradesTitle);
        add(Box.createVerticalStrut(8));

        filterBox.setAlignmentX(LEFT_ALIGNMENT);
        filterBox.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 38)
        );
        TradeLedgerFonts.regular(filterBox);
        filterBox.addActionListener(event -> refreshCards());

        add(filterBox);
        add(Box.createVerticalStrut(12));

        cardsPanel.setOpaque(false);
        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
        cardsPanel.setAlignmentX(LEFT_ALIGNMENT);

        add(cardsPanel);
    }

    private JLabel createSectionTitle(String title)
    {
        JLabel label = new JLabel(title);
        label.setForeground(Color.WHITE);
        label.setAlignmentX(LEFT_ALIGNMENT);
        TradeLedgerFonts.regular(label);
        return label;
    }

    private JPanel createOverviewRow(
            String label,
            String value,
            Color color)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(43, 43, 43));
        panel.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(70, 70, 70)),
                        BorderFactory.createEmptyBorder(10, 10, 10, 10)
                )
        );

        JLabel labelComponent = new JLabel(label);
        labelComponent.setForeground(SECONDARY);
        labelComponent.setAlignmentX(LEFT_ALIGNMENT);
        TradeLedgerFonts.regular(labelComponent);

        JLabel valueComponent = new JLabel(value);
        valueComponent.setForeground(color);
        valueComponent.setAlignmentX(LEFT_ALIGNMENT);
        TradeLedgerFonts.bold(valueComponent);

        panel.add(labelComponent);
        panel.add(Box.createVerticalStrut(5));
        panel.add(valueComponent);

        return panel;
    }

    public void refreshOverviewSettings()
    {
        refreshOverview();
    }

    /**
     * Match a newly filled GE sale to the oldest open recorded purchases.
     * Gross proceeds are exact offer deltas. Tax is an estimate because
     * GrandExchangeOfferChanged does not expose the tax actually deducted.
     * Returns the number of sold items successfully matched to purchases.
     */
    public int recordSaleFill(int itemId, int quantity, long gross)
    {
        if (itemId <= 0 || quantity <= 0 || gross <= 0)
        {
            return 0;
        }

        List<TradeTransaction> eligible = new ArrayList<>();
        for (TradeTransaction transaction : transactions)
        {
            if (transaction.getItemId() == itemId
                && transaction.getStatus() == TradeTransaction.Status.OPEN
                && transaction.getQuantityRemaining() > 0)
            {
                eligible.add(transaction);
            }
        }
        eligible.sort(Comparator.comparing(TradeTransaction::getBoughtAt));

        int available = 0;
        for (TradeTransaction transaction : eligible)
        {
            available += transaction.getQuantityRemaining();
        }
        int matched = Math.min(quantity, available);
        if (matched == 0)
        {
            return 0;
        }

        // Allocate only the proportion of proceeds belonging to tracked items.
        long trackedGross = gross * matched / quantity;
        long trackedTax = estimateTax(trackedGross, matched);
        int remaining = matched;
        long assignedGross = 0;
        long assignedTax = 0;
        LocalDateTime timestamp = LocalDateTime.now();

        for (TradeTransaction transaction : eligible)
        {
            if (remaining == 0)
            {
                break;
            }
            int take = Math.min(remaining, transaction.getQuantityRemaining());
            int alreadyAssignedQuantity = matched - remaining;
            int newAssignedQuantity = alreadyAssignedQuantity + take;
            long cumulativeGross = trackedGross * newAssignedQuantity / matched;
            long cumulativeTax = trackedTax * newAssignedQuantity / matched;
            long partGross = cumulativeGross - assignedGross;
            long partTax = cumulativeTax - assignedTax;

            transaction.recordSaleTotals(take, partGross, partTax, timestamp);
            assignedGross = cumulativeGross;
            assignedTax = cumulativeTax;
            remaining -= take;
        }

        persist();
        refresh();
        return matched;
    }

    private static long estimateTax(long gross, int quantity)
    {
        if (gross <= 0 || quantity <= 0)
        {
            return 0;
        }
        // GE tax is 2% of execution price, capped at 5m per item.
        // The offer API supplies only aggregate gross, so this is an estimate.
        long average = gross / quantity;
        long perItemTax = Math.min(5_000_000L, average / 50L);
        return Math.min(gross, perItemTax * quantity);
    }

    private void refresh()
    {
        refreshOverview();
        refreshCards();
    }

    private void refreshOverview()
    {
        long invested = 0;
        long realizedProfit = 0;
        long inventoryQuantity = 0;
        int openTrades = 0;
        int completedTrades = 0;

        for (TradeTransaction transaction : transactions)
        {
            if (!transaction.isIncludedInOverview())
            {
                continue;
            }

            invested += transaction.getRemainingCost();
            realizedProfit += transaction.getRealizedProfit();
            inventoryQuantity += transaction.getQuantityRemaining();

            if (transaction.getStatus() == TradeTransaction.Status.OPEN)
            {
                openTrades++;
            }
            else if (
                    transaction.getStatus() == TradeTransaction.Status.COMPLETED)
            {
                completedTrades++;
            }
        }

        overviewPanel.removeAll();

        if (config.showGpInvested())
        {
            overviewPanel.add(createOverviewRow("GP Invested", formatGp(invested), Color.WHITE));
        }
        if (config.showRealizedProfit())
        {
            overviewPanel.add(createOverviewRow("Realized profit", formatSignedGp(realizedProfit),
                realizedProfit >= 0 ? PROFIT_COLOR : LOSS_COLOR));
        }
        if (config.showItemsRemaining())
        {
            overviewPanel.add(createOverviewRow("Items remaining", formatNumber(inventoryQuantity), Color.WHITE));
        }
        if (config.showTradeCounts())
        {
            overviewPanel.add(createOverviewRow("Open / completed",
                openTrades + " / " + completedTrades, Color.WHITE));
        }

        overviewPanel.revalidate();
        overviewPanel.repaint();
    }

    private void refreshCards()
    {
        cardsPanel.removeAll();
        String selectedFilter = (String) filterBox.getSelectedItem();
        List<Object> visible = new ArrayList<>();

        for (TradeTransaction transaction : transactions)
        {
            if (matchesFilter(transaction, selectedFilter))
            {
                visible.add(transaction);
            }
        }
        if (selectedFilter == null || "All trades".equals(selectedFilter))
        {
            visible.addAll(recordedSales);
        }

        visible.sort((a, b) ->
        {
            LocalDateTime timeA = a instanceof TradeTransaction
                ? ((TradeTransaction) a).getLastActivityAt()
                : ((RecordedSale) a).getRecordedAt();
            LocalDateTime timeB = b instanceof TradeTransaction
                ? ((TradeTransaction) b).getLastActivityAt()
                : ((RecordedSale) b).getRecordedAt();
            return timeB.compareTo(timeA);
        });

        if (visible.isEmpty())
        {
            JLabel emptyLabel = new JLabel("No transactions to display.");
            emptyLabel.setForeground(SECONDARY);
            emptyLabel.setAlignmentX(LEFT_ALIGNMENT);
            TradeLedgerFonts.regular(emptyLabel);
            cardsPanel.add(emptyLabel);
        }
        else
        {
            for (Object record : visible)
            {
                if (record instanceof TradeTransaction)
                {
                    TradeTransaction transaction = (TradeTransaction) record;
                    TransactionCard card = new TransactionCard(
                        transaction,
                        itemManager,
                        () -> cancelTrade(transaction),
                        () -> restoreTrade(transaction),
                        () -> deleteTrade(transaction),
                        () -> editNote(transaction),
                        () -> showDetails(transaction),
                        null
                    );
                    bindPurchaseIcon(card, transaction.getItemId());
                    cardsPanel.add(card);
                }
                else
                {
                    RecordedSale sale = (RecordedSale) record;
                    cardsPanel.add(new RecordedSaleCard(sale, itemManager,
                        () -> deleteSale(sale), () -> editSaleNote(sale)));
                }
                cardsPanel.add(Box.createVerticalStrut(10));
            }
        }

        cardsPanel.revalidate();
        cardsPanel.repaint();
        revalidate();
        repaint();
    }

    /**
     * The existing TransactionCard constructs its icon before the asynchronous
     * item sprite necessarily finishes loading. Bind the sprite to that same
     * label so RuneLite repaints it as soon as it is ready.
     *
     * This avoids changing the established card layout or interaction logic.
     */
    private void bindPurchaseIcon(TransactionCard card, int itemId)
    {
        JLabel iconLabel = findItemIconLabel(card);
        if (iconLabel == null)
        {
            return;
        }

        AsyncBufferedImage image = itemManager.getImage(itemId);
        if (image != null)
        {
            image.addTo(iconLabel);
        }
    }

    private JLabel findItemIconLabel(Container container)
    {
        // TransactionCard puts its icon in BorderLayout.WEST.
        if (container.getLayout() instanceof BorderLayout)
        {
            BorderLayout layout = (BorderLayout) container.getLayout();
            Component west = layout.getLayoutComponent(BorderLayout.WEST);
            if (west instanceof JLabel && ((JLabel) west).getIcon() instanceof ImageIcon)
            {
                return (JLabel) west;
            }
        }

        for (Component component : container.getComponents())
        {
            if (component instanceof JLabel
                && ((JLabel) component).getIcon() instanceof ImageIcon)
            {
                return (JLabel) component;
            }
            if (component instanceof Container)
            {
                JLabel found = findItemIconLabel((Container) component);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean matchesFilter(
            TradeTransaction transaction,
            String filter)
    {
        if (filter == null || filter.equals("All trades"))
        {
            return true;
        }

        switch (filter)
        {
            case "Open":
                return transaction.getStatus()
                        == TradeTransaction.Status.OPEN;

            case "Completed":
                return transaction.getStatus()
                        == TradeTransaction.Status.COMPLETED;

            case "Canceled":
                return transaction.getStatus()
                        == TradeTransaction.Status.CANCELED;

            default:
                return true;
        }
    }

    private void cancelTrade(TradeTransaction transaction)
    {
        int answer = JOptionPane.showConfirmDialog(
                this,
                "Cancel tracking for " + transaction.getItemName() + "?\n\n"
                        + "The record will remain in history, but its values\n"
                        + "will be excluded from the overview.\n"
                        + "You can restore it later.",
                "Cancel trade",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (answer == JOptionPane.YES_OPTION)
        {
            transaction.cancel();
            persist();
            refresh();
        }
    }

    private void restoreTrade(TradeTransaction transaction)
    {
        transaction.restore();
        persist();
        refresh();
    }

    private void deleteTrade(TradeTransaction transaction)
    {
        int answer = JOptionPane.showConfirmDialog(
                this,
                "Permanently delete this " + transaction.getItemName()
                        + " trade?\n\nThis cannot be undone.",
                "Remove from history",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (answer == JOptionPane.YES_OPTION)
        {
            transactions.remove(transaction);
            persist();
            refresh();
        }
    }

    private void clearHistory()
    {
        if (transactions.isEmpty() && recordedSales.isEmpty())
        {
            JOptionPane.showMessageDialog(
                    this,
                    "The transaction history is already empty.",
                    "Trade Ledger",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        int answer = JOptionPane.showConfirmDialog(
                this,
                "Delete all " + (transactions.size() + recordedSales.size())
                        + " transaction records?\n\n"
                        + "This cannot be undone.",
                "Clear all history",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (answer == JOptionPane.YES_OPTION)
        {
            transactions.clear();
            recordedSales.clear();
            persist();
            refresh();
        }
    }

    private void editNote(TradeTransaction transaction)
    {
        String newNote = JOptionPane.showInputDialog(
                this,
                "Note for " + transaction.getItemName() + ":",
                transaction.getNote()
        );

        if (newNote != null)
        {
            transaction.setNote(newNote);
            persist();
            refreshCards();
        }
    }

    private void showDetails(TradeTransaction transaction)
    {
        StringBuilder details = new StringBuilder();

        details.append("Item: ")
                .append(transaction.getItemName()).append("\n");

        details.append("Status: ")
                .append(transaction.getStatus()).append("\n");

        details.append("Bought: ")
                .append(transaction.getBoughtAt().format(DATE_FORMAT))
                .append("\n");

        details.append("Last activity: ")
                .append(transaction.getLastActivityAt().format(DATE_FORMAT))
                .append("\n");

        details.append("Quantity bought: ")
                .append(formatNumber(transaction.getQuantityBought()))
                .append("\n");

        details.append("Quantity sold: ")
                .append(formatNumber(transaction.getQuantitySold()))
                .append("\n");

        details.append("Quantity remaining: ")
                .append(formatNumber(transaction.getQuantityRemaining()))
                .append("\n");

        details.append("Buy price: ")
                .append(formatGp(transaction.getBuyPrice()))
                .append("\n");

        details.append("Total purchase cost: ")
                .append(formatGp(transaction.getTotalCost()))
                .append("\n");

        details.append("Sale revenue: ")
                .append(formatGp(transaction.getSaleRevenue()))
                .append("\n");

        details.append("GE tax recorded: ")
                .append(formatGp(transaction.getSaleTax()))
                .append("\n");

        details.append("Realized profit: ")
                .append(formatSignedGp(transaction.getRealizedProfit()))
                .append("\n");

        if (transaction.getLastSoldAt() != null)
        {
            details.append("Last sale: ")
                    .append(transaction.getLastSoldAt().format(DATE_FORMAT))
                    .append("\n");
        }

        if (!transaction.getNote().isEmpty())
        {
            details.append("\nNote: ").append(transaction.getNote());
        }

        JOptionPane.showMessageDialog(
                this,
                details.toString(),
                "Trade details",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    public void recordHistoryTrade(HistoryTrade trade)
    {
        for (TradeTransaction existing : transactions)
        {
            if (trade.getSignature().equals(existing.getSourceKey()))
            {
                JOptionPane.showMessageDialog(this,
                    "This purchase appears to have already been recorded.\n"
                    + "Identical historical trades cannot be distinguished reliably.",
                    "Trade already recorded", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }
        for (RecordedSale existing : recordedSales)
        {
            if (trade.getSignature().equals(existing.getTrade().getSignature()))
            {
                JOptionPane.showMessageDialog(this,
                    "This sale appears to have already been recorded.\n"
                    + "Identical historical trades cannot be distinguished reliably.",
                    "Trade already recorded", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }

        if (trade.getKind() == HistoryTrade.Kind.BOUGHT)
        {
            int averagePrice = (int) Math.min(Integer.MAX_VALUE,
                trade.getNetAmount() / trade.getQuantity());
            TradeTransaction transaction = new TradeTransaction(
                trade.getItemId(), trade.getItemName(), trade.getQuantity(),
                averagePrice, trade.getNetAmount(), trade.getRecordedAt());
            transaction.setSourceKey(trade.getSignature());
            transactions.add(transaction);
        }
        else
        {
            recordedSales.add(new RecordedSale(trade));
        }
        filterBox.setSelectedItem("All trades");
        persist();
        refresh();
    }

    private void deleteSale(RecordedSale sale)
    {
        int answer = JOptionPane.showConfirmDialog(this,
            "Remove this " + sale.getTrade().getItemName() + " sale from history?",
            "Remove sale", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION)
        {
            recordedSales.remove(sale);
            persist();
            refresh();
        }
    }

    private void editSaleNote(RecordedSale sale)
    {
        String note = JOptionPane.showInputDialog(this,
            "Note for " + sale.getTrade().getItemName() + ":", sale.getNote());
        if (note != null)
        {
            sale.setNote(note);
            persist();
            refreshCards();
        }
    }

    private void persist()
    {
        storage.save(transactions, recordedSales);
    }

    private String formatNumber(long number)
    {
        return String.format(Locale.US, "%,d", number);
    }

    private String formatGp(long amount)
    {
        return formatNumber(amount) + " GP";
    }

    private String formatSignedGp(long amount)
    {
        return (amount >= 0 ? "+" : "") + formatGp(amount);
    }
}


