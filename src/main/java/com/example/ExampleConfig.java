package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("osrgb")
public interface ExampleConfig extends Config
{
	@ConfigSection(
			name = "Enable Settings",
			description = "Toggle OSRGB features",
			position = 0
	)
	String enableSection = "enableSection";

	@ConfigSection(
			name = "Valuable Drops",
			description = "Valuable drop settings",
			position = 1
	)
	String dropSection = "dropSection";

	@ConfigSection(
			name = "Test Effects",
			description = "Trigger test RGB effects",
			position = 2
	)
	String testSection = "testSection";

	@ConfigItem(
			keyName = "enableHpColors",
			name = "Enable HP Colors",
			description = "Enable smooth HP-based colors",
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
			description = "Enable red heartbeat under 10% HP",
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
			description = "Flash green when poisoned",
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
			description = "Flash purple when venomed",
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
			description = "Red/gold flash when you complete a combat achievement",
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
			description = "Long red/black effect when you die",
			section = enableSection,
			position = 6
	)
	default boolean enableDeathFlash()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableValuableDropFlash",
			name = "Enable Valuable Drop Flash",
			description = "Gold flash when valuable NPC loot appears",
			section = dropSection,
			position = 0
	)
	default boolean enableValuableDropFlash()
	{
		return true;
	}

	@Range(
			min = 1000
	)
	@ConfigItem(
			keyName = "valuableDropThreshold",
			name = "Drop Value Threshold",
			description = "Minimum NPC loot value required to trigger the valuable drop effect. Minimum: 1,000 gp.",
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
			description = "Toggle on to test poison flash",
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
			description = "Toggle on to test venom flash",
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
			description = "Toggle on to test critical HP pulse",
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
			description = "Toggle on to test rainbow level-up flash",
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
			description = "Toggle on to test red/gold combat achievement flash",
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
			description = "Toggle on to test valuable drop gold flash",
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
}