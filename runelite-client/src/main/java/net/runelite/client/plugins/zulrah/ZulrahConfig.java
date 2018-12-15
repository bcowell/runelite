package net.runelite.client.plugins.zulrah;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.awt.Color;

@ConfigGroup("zulrah")
public interface ZulrahConfig extends Config
{
    @ConfigItem(
            keyName = "enabled",
            name = "Enabled",
            description = "Display overlays for Zulrah boss fight.",
            position = 1
    )
    default boolean enabled()
    {
        return true;
    }

    @ConfigItem(
            keyName = "overlayColor",
            name = "Overlay Color",
            description = "Color of Zulrah overlay",
            position = 2
    )
    default Color overlayColor()
    {
        return Color.GREEN;
    }
}