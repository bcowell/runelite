/*
 * Copyright (c) 2017, Aria <aria@ar1as.space>
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * Copyright (c) 2017, Devin French <https://github.com/devinfrench>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.zulrah;

import com.google.inject.Binder;
import com.google.inject.Provides;
import javax.inject.Inject;
import javax.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.zulrah.overlays.ZulrahCurrentPhaseOverlay;
import net.runelite.client.plugins.zulrah.overlays.ZulrahNextPhaseOverlay;
import net.runelite.client.plugins.zulrah.overlays.ZulrahOverlay;
import net.runelite.client.plugins.zulrah.overlays.ZulrahPrayerOverlay;
import net.runelite.client.plugins.zulrah.patterns.ZulrahPattern;
import net.runelite.client.plugins.zulrah.patterns.ZulrahPatternA;
import net.runelite.client.plugins.zulrah.patterns.ZulrahPatternB;
import net.runelite.client.plugins.zulrah.patterns.ZulrahPatternC;
import net.runelite.client.plugins.zulrah.patterns.ZulrahPatternD;
import net.runelite.client.plugins.zulrah.phase.ZulrahPhase;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = "zulrah",
        description = "Zulrah Helper",
        tags = {"zulrah", "boss", "helper"},
        enabledByDefault = true
)

@Slf4j
public class ZulrahPlugin extends Plugin
{
    @Inject
    RuneLite runelite;

    @Inject
    Client client;

    @Inject
    private ZulrahConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    ZulrahOverlay overlay;

    @Inject
    ZulrahCurrentPhaseOverlay currentPhaseOverlay;

    @Inject
    ZulrahNextPhaseOverlay nextPhaseOverlay;

    @Inject
    ZulrahPrayerOverlay zulrahPrayerOverlay;

    private NPC zulrah = null;

    private ZulrahInstance instance;

    private final ZulrahPattern[] patterns = new ZulrahPattern[]
    {
        new ZulrahPatternA(),
        new ZulrahPatternB(),
        new ZulrahPatternC(),
        new ZulrahPatternD()
    };

    @Provides
    ZulrahConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ZulrahConfig.class);
    }

    /*
    @Override
    public void configure(Binder binder)
    {
        binder.bind(ZulrahOverlay.class);
    }
    */

    @Override
    protected void startUp() throws Exception
    {
        overlayManager.add(overlay);
        overlayManager.add(currentPhaseOverlay);
        overlayManager.add(nextPhaseOverlay);
        overlayManager.add(zulrahPrayerOverlay);
    }

    @Override
    protected void shutDown() throws Exception
    {
        overlayManager.remove(overlay);
        overlayManager.remove(overlay);
        overlayManager.remove(currentPhaseOverlay);
        overlayManager.remove(nextPhaseOverlay);
        overlayManager.remove(zulrahPrayerOverlay);
    }

    /*
    @Override
    public Collection<Overlay> getOverlays()
    {
        return Arrays.asList(overlay, currentPhaseOverlay, nextPhaseOverlay, zulrahPrayerOverlay);
    }
    */

    @Subscribe()
    public void onGameTick(GameTick event)
    {
        if (!config.enabled() || client == null || client.getGameState() != GameState.LOGGED_IN) { return; }

        if (zulrah == null)
        {
            if (instance != null)
            {
                log.debug("Zulrah encounter has ended.");
                instance = null;
            }
            return;
        }

        if (instance == null)
        {
            instance = new ZulrahInstance(zulrah);
            log.debug("Zulrah encounter has started.");
        }

        ZulrahPhase currentPhase = ZulrahPhase.valueOf(zulrah, instance.getStartLocation());
        if (instance.getPhase() == null)
        {
            instance.setPhase(currentPhase);
        }
        else if (!instance.getPhase().equals(currentPhase))
        {
            ZulrahPhase previousPhase = instance.getPhase();
            instance.setPhase(currentPhase);
            instance.nextStage();

            log.debug("Zulrah phase has moved from {} -> {}, stage: {}", previousPhase, currentPhase, instance.getStage());
        }

        ZulrahPattern pattern = instance.getPattern();
        if (pattern == null)
        {
            int potential = 0;
            ZulrahPattern potentialPattern = null;

            for (ZulrahPattern p : patterns)
            {
                if (p.stageMatches(instance.getStage(), instance.getPhase()))
                {
                    potential++;
                    potentialPattern = p;
                }
            }

            if (potential == 1)
            {
                log.debug("Zulrah pattern identified: {}", potentialPattern);

                instance.setPattern(potentialPattern);
            }
        }
        else if (pattern.canReset(instance.getStage()) && (instance.getPhase() == null || instance.getPhase().equals(pattern.get(0))))
        {
            log.debug("Zulrah pattern has reset.");
            instance.reset();
        }
    }

    public ZulrahInstance getInstance()
    {
        return instance;
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned npcSpawned)
    {
        NPC npc = npcSpawned.getNpc();

        if (npc.getId() == NpcID.ZULRAH)
        {
            log.debug("Zulrah spawned: {}", npc);
            zulrah = npc;
        }
    }

    /*
    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned)
    {
        NPC npc = npcDespawned.getNpc();

        if (npc == corp)
        {
            log.debug("Corporeal beast despawn: {}", npc);
            corp = null;
            players.clear();

            if (npc.isDead())
            {
                // Show kill stats
                String message = new ChatMessageBuilder()
                        .append(ChatColorType.NORMAL)
                        .append("Corporeal Beast: Your damage: ")
                        .append(ChatColorType.HIGHLIGHT)
                        .append(Integer.toString(yourDamage))
                        .append(ChatColorType.NORMAL)
                        .append(", Total damage: ")
                        .append(ChatColorType.HIGHLIGHT)
                        .append(Integer.toString(totalDamage))
                        .build();

                chatMessageManager.queue(QueuedMessage.builder()
                        .type(ChatMessageType.GAME)
                        .runeLiteFormattedMessage(message)
                        .build());
            }
        }
        else if (npc == core)
        {
            core = null;
        }
    }
    */
}