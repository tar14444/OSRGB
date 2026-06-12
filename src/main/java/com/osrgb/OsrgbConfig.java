package com.osrgb;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

import java.awt.Color;

@ConfigGroup("osrgb")
public interface OsrgbConfig extends Config
{
	@ConfigSection(
			name = "OpenRGB Connection",
			description = "OpenRGB SDK connection settings",
			position = 0
	)
	String connectionSection = "connectionSection";
	@ConfigItem(
			keyName = "openRgbHost",
			name = "OpenRGB Host",
			description = "OpenRGB SDK server hostname or IP",
			section = connectionSection,
			position = 0
	)
	default String openRgbHost()
	{
		return "127.0.0.1";
	}

	@ConfigItem(
			keyName = "openRgbPort",
			name = "OpenRGB Port",
			description = "OpenRGB SDK server port",
			section = connectionSection,
			position = 1
	)
	default int openRgbPort()
	{
		return 6742;
	}
	@ConfigSection(
			name = "Enable Settings",
			description = "Toggle OSRGB features",
			position = 1
	)
	String enableSection = "enableSection";

	@ConfigSection(
			name = "HP Colors",
			description = "Customize HP colors and thresholds",
			position = 2
	)
	String hpSection = "hpSection";

	@ConfigSection(
			name = "Effect Colors",
			description = "Customize RGB effect colors",
			position = 3
	)
	String colorSection = "colorSection";

	@ConfigSection(
			name = "Effect Styles",
			description = "Choose animation styles for effects",
			position = 4
	)
	String styleSection = "styleSection";

	@ConfigSection(
			name = "Valuable Drops",
			description = "Valuable drop settings",
			position = 5
	)
	String dropSection = "dropSection";

	@ConfigSection(
			name = "Test Effects",
			description = "Trigger test RGB effects",
			position = 6
	)
	String testSection = "testSection";

	@ConfigItem(
			keyName = "enableHpColors",
			name = "Enable HP Colors",
			description = "Enable HP-based colors",
			section = enableSection,
			position = 0
	)
	default boolean enableHpColors()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableCriticalPulse",
			name = "Enable Critical Pulse",
			description = "Enable heartbeat under critical HP",
			section = enableSection,
			position = 1
	)
	default boolean enableCriticalPulse()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enablePoisonFlash",
			name = "Enable Poison Flash",
			description = "Flash when poisoned",
			section = enableSection,
			position = 2
	)
	default boolean enablePoisonFlash()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableVenomFlash",
			name = "Enable Venom Flash",
			description = "Flash when venomed",
			section = enableSection,
			position = 3
	)
	default boolean enableVenomFlash()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableLevelUpFlash",
			name = "Enable Level-Up Flash",
			description = "Rainbow flash when you gain a level",
			section = enableSection,
			position = 4
	)
	default boolean enableLevelUpFlash()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableCombatAchievementFlash",
			name = "Enable Combat Achievement Flash",
			description = "Flash when you complete a combat achievement",
			section = enableSection,
			position = 5
	)
	default boolean enableCombatAchievementFlash()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableDeathFlash",
			name = "Enable Death Effect",
			description = "Effect when you die",
			section = enableSection,
			position = 6
	)
	default boolean enableDeathFlash()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableCollectionLogFlash",
			name = "Enable Collection Log Effect",
			description = "Effect when a new item is added to your collection log",
			section = enableSection,
			position = 7
	)
	default boolean enableCollectionLogFlash()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableQuestFlash",
			name = "Enable Quest/Diary Effect",
			description = "Effect when completing a quest, miniquest, or achievement diary task",
			section = enableSection,
			position = 8
	)
	default boolean enableQuestFlash()
	{
		return true;
	}

	@ConfigItem(
			keyName = "useSmoothHpGradient",
			name = "Use Smooth HP Gradient",
			description = "Smoothly blend between HP colors instead of hard color steps",
			section = hpSection,
			position = 0
	)
	default boolean useSmoothHpGradient()
	{
		return true;
	}

	@ConfigItem(
			keyName = "highHpColor",
			name = "High HP Color",
			description = "Color used at high HP",
			section = hpSection,
			position = 1
	)
	default Color highHpColor()
	{
		return Color.GREEN;
	}

	@ConfigItem(
			keyName = "mediumHpColor",
			name = "Medium HP Color",
			description = "Color used at medium HP",
			section = hpSection,
			position = 2
	)
	default Color mediumHpColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
			keyName = "lowHpColor",
			name = "Low HP Color",
			description = "Color used at low HP",
			section = hpSection,
			position = 3
	)
	default Color lowHpColor()
	{
		return Color.ORANGE;
	}

	@ConfigItem(
			keyName = "criticalHpColor",
			name = "Critical HP Color",
			description = "Color used at critical HP",
			section = hpSection,
			position = 4
	)
	default Color criticalHpColor()
	{
		return Color.RED;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
			keyName = "highHpValue",
			name = "High HP Value",
			description = "High HP threshold",
			section = hpSection,
			position = 5
	)
	default int highHpValue()
	{
		return 75;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
			keyName = "mediumHpValue",
			name = "Medium HP Value",
			description = "Medium HP threshold",
			section = hpSection,
			position = 6
	)
	default int mediumHpValue()
	{
		return 50;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
			keyName = "lowHpValue",
			name = "Low HP Value",
			description = "Low HP threshold",
			section = hpSection,
			position = 7
	)
	default int lowHpValue()
	{
		return 25;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
			keyName = "criticalHpThreshold",
			name = "Critical HP Threshold (%)",
			description = "Critical HP threshold",
			section = hpSection,
			position = 8
	)
	default int criticalHpValue()
	{
		return 10;
	}

	@ConfigItem(
			keyName = "poisonColor",
			name = "Poison Color",
			description = "Poison effect color",
			section = colorSection,
			position = 0
	)
	default Color poisonColor()
	{
		return Color.GREEN;
	}

	@ConfigItem(
			keyName = "venomColor",
			name = "Venom Color",
			description = "Venom effect color",
			section = colorSection,
			position = 1
	)
	default Color venomColor()
	{
		return new Color(170, 0, 255);
	}

	@ConfigItem(
			keyName = "valuableDropColor",
			name = "Valuable Drop Color",
			description = "Valuable drop effect color",
			section = colorSection,
			position = 2
	)
	default Color valuableDropColor()
	{
		return new Color(255, 190, 0);
	}

	@ConfigItem(
			keyName = "combatAchievementPrimaryColor",
			name = "Combat Achievement Primary",
			description = "Primary combat achievement color",
			section = colorSection,
			position = 3
	)
	default Color combatAchievementPrimaryColor()
	{
		return Color.RED;
	}

	@ConfigItem(
			keyName = "combatAchievementSecondaryColor",
			name = "Combat Achievement Secondary",
			description = "Secondary combat achievement color",
			section = colorSection,
			position = 4
	)
	default Color combatAchievementSecondaryColor()
	{
		return new Color(255, 190, 0);
	}

	@ConfigItem(
			keyName = "deathPrimaryColor",
			name = "Death Primary Color",
			description = "Primary death effect color",
			section = colorSection,
			position = 5
	)
	default Color deathPrimaryColor()
	{
		return Color.RED;
	}

	@ConfigItem(
			keyName = "deathSecondaryColor",
			name = "Death Secondary Color",
			description = "Secondary death effect color",
			section = colorSection,
			position = 6
	)
	default Color deathSecondaryColor()
	{
		return Color.BLACK;
	}

	@ConfigItem(
			keyName = "collectionLogPrimaryColor",
			name = "Collection Log Primary",
			description = "Primary collection log color",
			section = colorSection,
			position = 7
	)
	default Color collectionLogPrimaryColor()
	{
		return new Color(170, 0, 255);
	}

	@ConfigItem(
			keyName = "collectionLogSecondaryColor",
			name = "Collection Log Secondary",
			description = "Secondary collection log color",
			section = colorSection,
			position = 8
	)
	default Color collectionLogSecondaryColor()
	{
		return new Color(255, 190, 0);
	}

	@ConfigItem(
			keyName = "questPrimaryColor",
			name = "Quest/Diary Primary",
			description = "Primary quest, miniquest, and diary color",
			section = colorSection,
			position = 9
	)
	default Color questPrimaryColor()
	{
		return new Color(0, 255, 255);
	}

	@ConfigItem(
			keyName = "questSecondaryColor",
			name = "Quest/Diary Secondary",
			description = "Secondary quest, miniquest, and diary color",
			section = colorSection,
			position = 10
	)
	default Color questSecondaryColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
			keyName = "poisonStyle",
			name = "Poison Style",
			description = "Animation style for poison",
			section = styleSection,
			position = 0
	)
	default EffectStyle poisonStyle()
	{
		return EffectStyle.FLASH;
	}

	@ConfigItem(
			keyName = "venomStyle",
			name = "Venom Style",
			description = "Animation style for venom",
			section = styleSection,
			position = 1
	)
	default EffectStyle venomStyle()
	{
		return EffectStyle.FLASH;
	}

	@ConfigItem(
			keyName = "criticalStyle",
			name = "Critical Style",
			description = "Animation style for critical HP",
			section = styleSection,
			position = 2
	)
	default EffectStyle criticalStyle()
	{
		return EffectStyle.PULSE;
	}

	@ConfigItem(
			keyName = "levelUpStyle",
			name = "Level-Up Style",
			description = "Animation style for level-ups",
			section = styleSection,
			position = 3
	)
	default EffectStyle levelUpStyle()
	{
		return EffectStyle.RAINBOW;
	}

	@ConfigItem(
			keyName = "valuableDropStyle",
			name = "Valuable Drop Style",
			description = "Animation style for valuable drops",
			section = styleSection,
			position = 4
	)
	default EffectStyle valuableDropStyle()
	{
		return EffectStyle.FLASH;
	}

	@ConfigItem(
			keyName = "combatAchievementStyle",
			name = "Combat Achievement Style",
			description = "Animation style for combat achievements",
			section = styleSection,
			position = 5
	)
	default EffectStyle combatAchievementStyle()
	{
		return EffectStyle.FLASH;
	}

	@ConfigItem(
			keyName = "deathStyle",
			name = "Death Style",
			description = "Animation style for death",
			section = styleSection,
			position = 6
	)
	default EffectStyle deathStyle()
	{
		return EffectStyle.STROBE;
	}

	@ConfigItem(
			keyName = "collectionLogStyle",
			name = "Collection Log Style",
			description = "Animation style for collection log unlocks",
			section = styleSection,
			position = 7
	)
	default EffectStyle collectionLogStyle()
	{
		return EffectStyle.RAINBOW;
	}

	@ConfigItem(
			keyName = "questStyle",
			name = "Quest/Diary Style",
			description = "Animation style for quests, miniquests, and achievement diaries",
			section = styleSection,
			position = 8
	)
	default EffectStyle questStyle()
	{
		return EffectStyle.BREATHING;
	}

	@ConfigItem(
			keyName = "enableValuableDropFlash",
			name = "Enable Valuable Drop Flash",
			description = "Flash when valuable NPC loot appears",
			section = dropSection,
			position = 0
	)
	default boolean enableValuableDropFlash()
	{
		return true;
	}

	@Range(min = 1000)
	@ConfigItem(
			keyName = "valuableDropThreshold",
			name = "Drop Value Threshold",
			description = "Minimum NPC loot value required to trigger the valuable drop effect",
			section = dropSection,
			position = 1
	)
	default int valuableDropThreshold()
	{
		return 1000000;
	}

	@ConfigItem(
			keyName = "testPoisonFlash",
			name = "Test Poison Flash",
			description = "Toggle on to test poison effect",
			section = testSection,
			position = 0
	)
	default boolean testPoisonFlash()
	{
		return false;
	}

	@ConfigItem(
			keyName = "testVenomFlash",
			name = "Test Venom Flash",
			description = "Toggle on to test venom effect",
			section = testSection,
			position = 1
	)
	default boolean testVenomFlash()
	{
		return false;
	}

	@ConfigItem(
			keyName = "testCriticalPulse",
			name = "Test Critical Pulse",
			description = "Toggle on to test critical effect",
			section = testSection,
			position = 2
	)
	default boolean testCriticalPulse()
	{
		return false;
	}

	@ConfigItem(
			keyName = "testLevelUpFlash",
			name = "Test Level-Up Flash",
			description = "Toggle on to test level-up effect",
			section = testSection,
			position = 3
	)
	default boolean testLevelUpFlash()
	{
		return false;
	}

	@ConfigItem(
			keyName = "testCombatAchievementFlash",
			name = "Test Combat Achievement Flash",
			description = "Toggle on to test combat achievement effect",
			section = testSection,
			position = 4
	)
	default boolean testCombatAchievementFlash()
	{
		return false;
	}

	@ConfigItem(
			keyName = "testValuableDropFlash",
			name = "Test Valuable Drop Flash",
			description = "Toggle on to test valuable drop effect",
			section = testSection,
			position = 5
	)
	default boolean testValuableDropFlash()
	{
		return false;
	}

	@ConfigItem(
			keyName = "testDeathFlash",
			name = "Test Death Effect",
			description = "Toggle on to test death effect",
			section = testSection,
			position = 6
	)
	default boolean testDeathFlash()
	{
		return false;
	}

	@ConfigItem(
			keyName = "testCollectionLogFlash",
			name = "Test Collection Log Effect",
			description = "Toggle on to test collection log effect",
			section = testSection,
			position = 7
	)
	default boolean testCollectionLogFlash()
	{
		return false;
	}

	@ConfigItem(
			keyName = "testQuestFlash",
			name = "Test Quest/Diary Effect",
			description = "Toggle on to test quest, miniquest, and diary effect",
			section = testSection,
			position = 8
	)
	default boolean testQuestFlash()
	{
		return false;
	}
}