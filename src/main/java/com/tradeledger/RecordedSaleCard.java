package com.tradeledger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

public class RecordedSaleCard extends JPanel
{
    private static final Color BACKGROUND = new Color(43, 43, 43);
    private static final Color TEXT = new Color(220, 220, 220);
    private static final Color ACCENT = new Color(235, 180, 65);

    public RecordedSaleCard(RecordedSale sale, ItemManager itemManager,
                            Runnable deleteAction, Runnable noteAction)
    {
        HistoryTrade trade = sale.getTrade();
        setLayout(new BorderLayout(9, 8));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT),
            BorderFactory.createEmptyBorder(10, 8, 10, 8)));
        setAlignmentX(LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 310));

        JLabel icon = new JLabel();
        AsyncBufferedImage image = itemManager.getImage(trade.getItemId());
        if (image != null)
        {
            image.addTo(icon);
        }
        icon.setVerticalAlignment(SwingConstants.TOP);
        add(icon, BorderLayout.WEST);

        JPanel content = new JPanel(new GridLayout(0, 1, 0, 5));
        content.setOpaque(false);
        add(content, BorderLayout.CENTER);

        addLabel(content, trade.getItemName(), Color.WHITE, true);
        addLabel(content, "SOLD · UNMATCHED", ACCENT, true);
        addLabel(content, "Recorded: " + trade.getRecordedAt().format(
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)), TEXT, false);
        addLabel(content, "Quantity: " + format(trade.getQuantity()), TEXT, false);
        addLabel(content, "Gross sale: " + format(trade.getGrossAmount()) + " GP", TEXT, false);
        addLabel(content, "GE tax: " + format(trade.getTax()) + " GP", TEXT, false);
        addLabel(content, "Net received: " + format(trade.getNetAmount()) + " GP", Color.WHITE, true);
        addLabel(content, "Profit: not calculated (purchase not linked)", TEXT, false);
        if (!sale.getNote().isEmpty())
        {
            addLabel(content, "Note: " + sale.getNote(), TEXT, false);
        }

        JPopupMenu menu = new JPopupMenu();
        JMenuItem note = new JMenuItem("Edit note");
        note.addActionListener(event -> noteAction.run());
        menu.add(note);
        JMenuItem delete = new JMenuItem("Remove from history");
        delete.addActionListener(event -> deleteAction.run());
        menu.add(delete);
        setComponentPopupMenu(menu);
        content.setComponentPopupMenu(menu);
        icon.setComponentPopupMenu(menu);
        for (java.awt.Component child : content.getComponents())
        {
            if (child instanceof javax.swing.JComponent)
            {
                ((javax.swing.JComponent) child).setComponentPopupMenu(menu);
            }
        }
    }

    private static void addLabel(JPanel panel, String text, Color color, boolean bold)
    {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        if (bold)
        {
            TradeLedgerFonts.bold(label);
        }
        else
        {
            TradeLedgerFonts.regular(label);
        }
        panel.add(label);
    }

    private static String format(long value)
    {
        return String.format(Locale.US, "%,d", value);
    }
}
