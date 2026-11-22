package com.tradeledger;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.config.ConfigManager;

/**
 * Watches cumulative GE offer updates and emits only newly filled sell quantities.
 * Per-slot checkpoints are persisted in the current RuneScape profile to avoid
 * replaying the same fill when the client or plugin restarts.
 */
@Slf4j
public final class GeSaleWatcher
{
    private static final String GROUP = "tradeledger";
    private static final String PREFIX = "geSaleCheckpointV1_";
    private static final int MAX_SLOTS = 8;

    private final ConfigManager configManager;
    private final Gson gson;
    private final Map<Integer, Checkpoint> checkpoints = new HashMap<>();
    private final Set<Integer> seenNonEmptyThisSession = new HashSet<>();

    @Inject
    public GeSaleWatcher(ConfigManager configManager, Gson gson)
    {
        this.configManager = configManager;
        this.gson = gson;
    }

    public void resetMemory()
    {
        checkpoints.clear();
        seenNonEmptyThisSession.clear();
    }

    public SaleFill observe(GrandExchangeOfferChanged event)
    {
        int slot = event.getSlot();
        if (slot < 0 || slot >= MAX_SLOTS)
        {
            return null;
        }

        GrandExchangeOffer offer = event.getOffer();
        if (offer == null)
        {
            return null;
        }

        GrandExchangeOfferState state = offer.getState();
        if (state == GrandExchangeOfferState.EMPTY)
        {
            // Login initialization can emit EMPTY for every slot before the
            // server supplies real offers. Preserve persisted checkpoints until
            // this slot has actually shown a nonempty offer in this session.
            if (!seenNonEmptyThisSession.remove(slot))
            {
                return null;
            }
            checkpoints.remove(slot);
            configManager.unsetRSProfileConfiguration(GROUP, PREFIX + slot);
            return null;
        }

        seenNonEmptyThisSession.add(slot);

        if (state != GrandExchangeOfferState.SELLING
            && state != GrandExchangeOfferState.SOLD
            && state != GrandExchangeOfferState.CANCELLED_SELL)
        {
            // Buy offers don't produce sale fills, and replacing an offer
            // invalidates any old sell checkpoint for the slot.
            checkpoints.remove(slot);
            configManager.unsetRSProfileConfiguration(GROUP, PREFIX + slot);
            return null;
        }

        Checkpoint current = new Checkpoint();
        current.itemId = offer.getItemId();
        current.offerPrice = offer.getPrice();
        current.totalQuantity = offer.getTotalQuantity();
        current.filledQuantity = offer.getQuantitySold();
        current.grossTotal = offer.getSpent();

        if (current.itemId <= 0 || current.totalQuantity <= 0
            || current.filledQuantity < 0 || current.grossTotal < 0)
        {
            return null;
        }

        Checkpoint previous = checkpoints.get(slot);
        if (previous == null)
        {
            String saved = configManager.getRSProfileConfiguration(GROUP, PREFIX + slot);
            if (saved != null)
            {
                try
                {
                    previous = gson.fromJson(saved, Checkpoint.class);
                }
                catch (RuntimeException ex)
                {
                    log.warn("Invalid saved GE slot checkpoint {}", slot, ex);
                }
            }
        }

        boolean sameOffer = previous != null
            && previous.itemId == current.itemId
            && previous.offerPrice == current.offerPrice
            && previous.totalQuantity == current.totalQuantity
            && previous.filledQuantity <= current.filledQuantity
            && previous.grossTotal <= current.grossTotal;

        // A new or unrecognized offer starts at its observed baseline. We
        // deliberately don't claim historical fills we cannot distinguish.
        if (!sameOffer)
        {
            store(slot, current);
            return null;
        }

        int deltaQuantity = current.filledQuantity - previous.filledQuantity;
        long deltaGross = current.grossTotal - previous.grossTotal;
        if (deltaQuantity <= 0 || deltaGross <= 0)
        {
            if (deltaQuantity != 0 || deltaGross != 0)
            {
                store(slot, current);
            }
            return null;
        }

        store(slot, current);
        return new SaleFill(current.itemId, deltaQuantity, deltaGross);
    }

    private void store(int slot, Checkpoint checkpoint)
    {
        checkpoints.put(slot, checkpoint);
        configManager.setRSProfileConfiguration(GROUP, PREFIX + slot, gson.toJson(checkpoint));
    }

    private static final class Checkpoint
    {
        private int itemId;
        private long offerPrice;
        private int totalQuantity;
        private int filledQuantity;
        private long grossTotal;
    }

    public static final class SaleFill
    {
        public final int itemId;
        public final int quantity;
        public final long gross;

        private SaleFill(int itemId, int quantity, long gross)
        {
            this.itemId = itemId;
            this.quantity = quantity;
            this.gross = gross;
        }
    }
}
