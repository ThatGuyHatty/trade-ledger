package com.tradeledger;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.game.ItemManager;

public class TransactionCard extends JPanel
{
    private static final Color BACKGROUND = new Color(38, 38, 38);
    private static final Color OPEN_COLOR = new Color(55, 135, 225);
    private static final Color COMPLETED_COLOR = new Color(65, 165, 85);
    private static final Color CANCELED_COLOR = new Color(190, 65, 65);
    private static final Color MUTED_COLOR = new Color(190, 190, 190);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.ENGLISH);

    private static final float MAX_OVERLAY_OPACITY = 0.76f;
    private static final float ANIMATION_STEP = 0.12f;

    private final TradeTransaction transaction;
    private final Runnable cancelAction;
    private final Runnable restoreAction;
    private final Runnable deleteAction;
    private final Runnable editNoteAction;
    private final Runnable detailsAction;
    private final Runnable previewSaleAction;

    private final Timer fadeTimer;

    private float overlayOpacity;
    private float targetOpacity;

    public TransactionCard(
            TradeTransaction transaction,
            ItemManager itemManager,
            Runnable cancelAction,
            Runnable restoreAction,
            Runnable deleteAction,
            Runnable editNoteAction,
            Runnable detailsAction,
            Runnable previewSaleAction)
    {
        this.transaction = transaction;
        this.cancelAction = cancelAction;
        this.restoreAction = restoreAction;
        this.deleteAction = deleteAction;
        this.editNoteAction = editNoteAction;
        this.detailsAction = detailsAction;
        this.previewSaleAction = previewSaleAction;

        boolean inactive =
                transaction.getStatus() != TradeTransaction.Status.OPEN;

        overlayOpacity = inactive ? MAX_OVERLAY_OPACITY : 0.0f;
        targetOpacity = overlayOpacity;

        fadeTimer = new Timer(20, event -> animateOverlay());
        fadeTimer.setRepeats(true);

        setLayout(new BorderLayout(0, 13));
        setBackground(BACKGROUND);
        setAlignmentX(LEFT_ALIGNMENT);

        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(getStateColor(), 1),
                        BorderFactory.createEmptyBorder(12, 10, 13, 10)
                )
        );

        add(createHeader(itemManager), BorderLayout.NORTH);
        add(createStatistics(), BorderLayout.CENTER);

        installContextMenu(this);
        installHoverListeners(this);
    }

    private JPanel createHeader(ItemManager itemManager)
    {
        JPanel header = new JPanel(new BorderLayout(9, 0));
        header.setOpaque(false);

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(40, 48));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        if (itemManager != null)
        {
            iconLabel.setIcon(
                    new ImageIcon(itemManager.getImage(transaction.getItemId()))
            );
        }

        header.add(iconLabel, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(transaction.getItemName());
        nameLabel.setForeground(Color.WHITE);
        TradeLedgerFonts.bold(nameLabel);
        nameLabel.setToolTipText(transaction.getItemName());

        JLabel dateLabel = new JLabel(
                "Bought: " + transaction.getBoughtAt().format(DATE_FORMAT)
        );
        dateLabel.setForeground(MUTED_COLOR);
        TradeLedgerFonts.regular(dateLabel);

        textPanel.add(nameLabel);
        textPanel.add(Box.createVerticalStrut(5));
        textPanel.add(dateLabel);

        if (transaction.getLastSoldAt() != null)
        {
            JLabel soldLabel = new JLabel(
                    "Sold: " + transaction.getLastSoldAt().format(DATE_FORMAT)
            );
            soldLabel.setForeground(MUTED_COLOR);
            TradeLedgerFonts.regular(soldLabel);

            textPanel.add(Box.createVerticalStrut(3));
            textPanel.add(soldLabel);
        }

        header.add(textPanel, BorderLayout.CENTER);

        installContextMenu(header);
        installContextMenu(iconLabel);
        installContextMenu(textPanel);
        installContextMenu(nameLabel);
        installContextMenu(dateLabel);

        for (Component child : textPanel.getComponents())
        {
            installContextMenu(child);
        }

        return header;
    }

    private JPanel createStatistics()
    {
        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JPanel separator = new JPanel();
        separator.setBackground(new Color(80, 80, 80));
        separator.setAlignmentX(LEFT_ALIGNMENT);
        separator.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 1)
        );
        separator.setPreferredSize(new Dimension(1, 1));

        container.add(separator);
        container.add(Box.createVerticalStrut(12));

        JPanel statistics = new JPanel(new GridLayout(0, 2, 8, 16));
        statistics.setOpaque(false);
        statistics.setAlignmentX(LEFT_ALIGNMENT);

        addStatistic(
                statistics,
                "Quantity",
                formatNumber(transaction.getQuantityBought()),
                Color.WHITE,
                null
        );

        addStatistic(
                statistics,
                "Buy price",
                formatGp(transaction.getBuyPrice()),
                Color.WHITE,
                null
        );

        addStatistic(
                statistics,
                "Remaining",
                formatNumber(transaction.getQuantityRemaining()),
                Color.WHITE,
                null
        );

        addStatistic(
                statistics,
                "Total cost",
                formatCompactGp(transaction.getTotalCost()),
                Color.WHITE,
                formatGp(transaction.getTotalCost())
        );

        if (transaction.getQuantitySold() > 0)
        {
            addStatistic(
                    statistics,
                    "Sold",
                    formatNumber(transaction.getQuantitySold()),
                    Color.WHITE,
                    null
            );

            long profit = transaction.getRealizedProfit();

            addStatistic(
                    statistics,
                    "Realized profit",
                    formatSignedCompactGp(profit),
                    profit >= 0 ? COMPLETED_COLOR : CANCELED_COLOR,
                    formatSignedGp(profit)
            );
        }

        container.add(statistics);

        if (!transaction.getNote().isEmpty())
        {
            container.add(Box.createVerticalStrut(14));

            JLabel noteLabel = new JLabel(
                    "<html><body style='width:175px'>"
                            + escapeHtml(transaction.getNote())
                            + "</body></html>"
            );

            noteLabel.setForeground(MUTED_COLOR);
            TradeLedgerFonts.regular(noteLabel);
            noteLabel.setAlignmentX(LEFT_ALIGNMENT);

            container.add(noteLabel);
            installContextMenu(noteLabel);
        }

        installContextMenu(container);
        installContextMenu(statistics);

        return container;
    }

    private void addStatistic(
            JPanel parent,
            String title,
            String value,
            Color valueColor,
            String tooltip)
    {
        JPanel cell = new JPanel();
        cell.setOpaque(false);
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(MUTED_COLOR);
        TradeLedgerFonts.regular(titleLabel);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setForeground(valueColor);
        TradeLedgerFonts.bold(valueLabel);

        if (tooltip != null)
        {
            valueLabel.setToolTipText(tooltip);
        }

        cell.add(titleLabel);
        cell.add(Box.createVerticalStrut(5));
        cell.add(valueLabel);

        parent.add(cell);

        installContextMenu(cell);
        installContextMenu(titleLabel);
        installContextMenu(valueLabel);
    }

    private void installHoverListeners(Component component)
    {
        MouseAdapter hoverListener = new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent event)
            {
                updateHoverState(event);
            }

            @Override
            public void mouseExited(MouseEvent event)
            {
                updateHoverState(event);
            }

            @Override
            public void mouseMoved(MouseEvent event)
            {
                updateHoverState(event);
            }
        };

        component.addMouseListener(hoverListener);
        component.addMouseMotionListener(hoverListener);

        if (component instanceof Container)
        {
            Container container = (Container) component;

            for (Component child : container.getComponents())
            {
                installHoverListeners(child);
            }
        }
    }

    private void updateHoverState(MouseEvent event)
    {
        if (transaction.getStatus() == TradeTransaction.Status.OPEN)
        {
            return;
        }

        java.awt.Point point = SwingUtilities.convertPoint(
                event.getComponent(),
                event.getPoint(),
                this
        );

        boolean hovering = contains(point);

        float newTarget = hovering ? 0.0f : MAX_OVERLAY_OPACITY;

        if (targetOpacity != newTarget)
        {
            targetOpacity = newTarget;
            fadeTimer.start();
        }
    }

    private void animateOverlay()
    {
        if (Math.abs(overlayOpacity - targetOpacity) <= ANIMATION_STEP)
        {
            overlayOpacity = targetOpacity;
            fadeTimer.stop();
        }
        else if (overlayOpacity < targetOpacity)
        {
            overlayOpacity += ANIMATION_STEP;
        }
        else
        {
            overlayOpacity -= ANIMATION_STEP;
        }

        overlayOpacity = Math.max(
                0.0f,
                Math.min(MAX_OVERLAY_OPACITY, overlayOpacity)
        );

        repaint();
    }

    @Override
    protected void paintChildren(Graphics graphics)
    {
        super.paintChildren(graphics);

        if (overlayOpacity <= 0.0f ||
                transaction.getStatus() == TradeTransaction.Status.OPEN)
        {
            return;
        }

        Graphics2D graphics2D = (Graphics2D) graphics.create();

        try
        {
            graphics2D.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int inset = 1;
            int width = getWidth() - 2 * inset;
            int height = getHeight() - 2 * inset;

            graphics2D.setComposite(
                    AlphaComposite.getInstance(
                            AlphaComposite.SRC_OVER,
                            overlayOpacity
                    )
            );

            graphics2D.setColor(Color.BLACK);
            graphics2D.fillRect(inset, inset, width, height);

            String statusText =
                    transaction.getStatus() == TradeTransaction.Status.COMPLETED
                            ? "COMPLETED"
                            : "CANCELED";

            Color statusColor =
                    transaction.getStatus() == TradeTransaction.Status.COMPLETED
                            ? COMPLETED_COLOR
                            : CANCELED_COLOR;

            float textOpacity = Math.min(
                    1.0f,
                    overlayOpacity / MAX_OVERLAY_OPACITY
            );

            graphics2D.setComposite(
                    AlphaComposite.getInstance(
                            AlphaComposite.SRC_OVER,
                            textOpacity
                    )
            );

            Font font = getFont().deriveFont(
                    Font.BOLD,
                    TradeLedgerFonts.STATUS_SIZE
            );
            graphics2D.setFont(font);

            FontMetrics metrics = graphics2D.getFontMetrics();

            int textWidth = metrics.stringWidth(statusText);
            int x = (getWidth() - textWidth) / 2;
            int y = (getHeight() + metrics.getAscent()
                    - metrics.getDescent()) / 2;

            graphics2D.setColor(new Color(10, 10, 10));
            graphics2D.drawString(statusText, x + 1, y + 2);

            graphics2D.setColor(statusColor);
            graphics2D.drawString(statusText, x, y);
        }
        finally
        {
            graphics2D.dispose();
        }
    }

    @Override
    public void removeNotify()
    {
        fadeTimer.stop();
        super.removeNotify();
    }

    private void installContextMenu(Component component)
    {
        component.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent event)
            {
                showMenuIfNeeded(event);
            }

            @Override
            public void mouseReleased(MouseEvent event)
            {
                showMenuIfNeeded(event);
            }

            private void showMenuIfNeeded(MouseEvent event)
            {
                if (event.isPopupTrigger())
                {
                    createContextMenu().show(
                            event.getComponent(),
                            event.getX(),
                            event.getY()
                    );
                }
            }
        });
    }

    private JPopupMenu createContextMenu()
    {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem details = new JMenuItem("View details...");
        TradeLedgerFonts.regular(details);
        details.addActionListener(event -> detailsAction.run());
        menu.add(details);

        JMenuItem editNote = new JMenuItem("Edit note...");
        TradeLedgerFonts.regular(editNote);
        editNote.addActionListener(event -> editNoteAction.run());
        menu.add(editNote);

        if (previewSaleAction != null &&
                transaction.getStatus() == TradeTransaction.Status.OPEN)
        {
            JMenuItem simulateSale =
                    new JMenuItem("Simulate sale (demo)");

            TradeLedgerFonts.regular(simulateSale);
            simulateSale.addActionListener(
                    event -> previewSaleAction.run()
            );

            menu.add(simulateSale);
        }

        menu.addSeparator();

        if (transaction.getStatus() == TradeTransaction.Status.CANCELED)
        {
            JMenuItem restore = new JMenuItem("Restore trade");
            TradeLedgerFonts.regular(restore);
            restore.addActionListener(event -> restoreAction.run());
            menu.add(restore);
        }
        else
        {
            JMenuItem cancel = new JMenuItem("Cancel trade...");
            TradeLedgerFonts.regular(cancel);
            cancel.addActionListener(event -> cancelAction.run());
            menu.add(cancel);
        }

        JMenuItem delete = new JMenuItem("Remove from history...");
        TradeLedgerFonts.regular(delete);
        delete.addActionListener(event -> deleteAction.run());
        menu.add(delete);

        return menu;
    }

    private Color getStateColor()
    {
        switch (transaction.getStatus())
        {
            case COMPLETED:
                return COMPLETED_COLOR;
            case CANCELED:
                return CANCELED_COLOR;
            case OPEN:
            default:
                return OPEN_COLOR;
        }
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

    private String formatCompactGp(long amount)
    {
        long absolute = Math.abs(amount);

        if (absolute >= 1_000_000_000L)
        {
            return String.format(
                    Locale.US,
                    "%.2fb GP",
                    amount / 1_000_000_000.0
            );
        }

        if (absolute >= 1_000_000L)
        {
            return String.format(
                    Locale.US,
                    "%.2fm GP",
                    amount / 1_000_000.0
            );
        }

        if (absolute >= 100_000L)
        {
            return String.format(
                    Locale.US,
                    "%.1fk GP",
                    amount / 1_000.0
            );
        }

        return formatGp(amount);
    }

    private String formatSignedCompactGp(long amount)
    {
        return (amount >= 0 ? "+" : "") + formatCompactGp(amount);
    }

    private String escapeHtml(String value)
    {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}