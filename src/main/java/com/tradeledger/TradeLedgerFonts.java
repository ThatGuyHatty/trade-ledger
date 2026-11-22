
package com.tradeledger;

import java.awt.Font;
import javax.swing.JComponent;

public final class TradeLedgerFonts
{
    public static final float FONT_SIZE = 16.0f;
    public static final float STATUS_SIZE = 23.0f;

    private TradeLedgerFonts()
    {
    }

    public static void regular(JComponent component)
    {
        component.setFont(
                component.getFont().deriveFont(
                        Font.PLAIN,
                        FONT_SIZE
                )
        );
    }

    public static void bold(JComponent component)
    {
        component.setFont(
                component.getFont().deriveFont(
                        Font.BOLD,
                        FONT_SIZE
                )
        );
    }
}
