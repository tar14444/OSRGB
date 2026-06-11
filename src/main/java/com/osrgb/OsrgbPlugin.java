package com.osrgb;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.PluginDescriptor;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@PluginDescriptor(
		name = "OSRGB"
)
public class OsrgbPlugin extends net.runelite.client.plugins.Plugin
{
	@Inject
	private Client client;

	@Inject
	private OsrgbConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	private final RgbHttpClient rgb = new RgbHttpClient();

	private Process pythonServer;
	private Thread shutdownHook;

	private String lastAilment = "NONE";
	private int lastHp = -1;
	private int lastMaxHp = -1;
	private boolean lastCriticalMode = false;

	private final Map<Skill, Integer> lastSkillLevels =
			new EnumMap<>(Skill.class);

	@Override
	protected void startUp() throws Exception
	{
		log.info("OSRGB Started");

		String helperPath =
				System.getenv("LOCALAPPDATA")
						+ "\\OSRGB\\Helper\\OSRGB-Helper.exe";

		pythonServer = new ProcessBuilder(helperPath).start();

		shutdownHook = new Thread(this::killPythonServer, "OSRGB-Shutdown-Hook");
		Runtime.getRuntime().addShutdownHook(shutdownHook);

		lastSkillLevels.clear();

		log.info("OSRGB Python server started");
	}

	@Override
	protected void shutDown()
	{
		log.info("OSRGB Stopped");

		if (shutdownHook != null)
		{
			try
			{
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			}
			catch (Exception ignored)
			{
			}

			shutdownHook = null;
		}

		killPythonServer();
	}

	private void killPythonServer()
	{
		if (pythonServer == null)
		{
			return;
		}

		try
		{
			long pid = pythonServer.pid();

			new ProcessBuilder(
					"taskkill",
					"/F",
					"/T",
					"/PID",
					String.valueOf(pid)
			).start().waitFor();

			log.info("Killed OSRGB Python server PID {}", pid);
		}
		catch (Exception e)
		{
			log.error("Failed to kill Python server by PID", e);

			try
			{
				pythonServer.descendants().forEach(ProcessHandle::destroyForcibly);
				pythonServer.destroyForcibly();
			}
			catch (Exception ignored)
			{
			}
		}

		pythonServer = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"osrgb".equals(event.getGroup()))
		{
			return;
		}

		lastHp = -1;
		lastMaxHp = -1;
		lastCriticalMode = false;

		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		if (maxHp <= 0)
		{
			return;
		}

		double percent = (double) hp / maxHp;
		Color hpColor = getHpColor(percent);

		boolean criticalPulseEnabled =
				config.enableCriticalPulse() && hp <= config.criticalHpValue();

		double sentPercent = criticalPulseEnabled ? 0.0 : 1.0;

		rgb.sendColor(
				hpColor,
				sentPercent,
				config.criticalStyle(),
				config.criticalHpColor(),
				darken(config.criticalHpColor(), 0.15)
		);

		lastCriticalMode = criticalPulseEnabled;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		if (maxHp <= 0)
		{
			return;
		}

		double percent = (double) hp / maxHp;
		Color hpColor = getHpColor(percent);

		handleTestOptions(percent, hpColor);
		handleAilments(percent, hpColor);
		handleHpColors(hp, maxHp, percent, hpColor);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!config.enableLevelUpFlash())
		{
			return;
		}

		Skill skill = event.getSkill();
		int newLevel = event.getLevel();

		Integer oldLevel = lastSkillLevels.get(skill);

		lastSkillLevels.put(skill, newLevel);

		if (oldLevel == null)
		{
			return;
		}

		if (newLevel > oldLevel)
		{
			sendSimpleEffect("level_up", config.levelUpStyle());
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (!config.enableValuableDropFlash())
		{
			return;
		}

		int threshold = Math.max(1000, config.valuableDropThreshold());

		for (ItemStack item : event.getItems())
		{
			int itemId = itemManager.canonicalize(item.getId());
			int quantity = Math.max(1, item.getQuantity());
			int price = itemManager.getItemPrice(itemId);

			long totalValue = (long) price * quantity;

			if (totalValue >= threshold)
			{
				sendColoredEffect(
						"valuable_drop",
						config.valuableDropColor(),
						darken(config.valuableDropColor(), 0.25),
						config.valuableDropStyle()
				);

				return;
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = cleanMessage(event.getMessage());

		if (
				config.enableCombatAchievementFlash()
						&& (
						message.contains("combat achievement completed")
								|| message.contains("combat achievements completed")
								|| message.contains("you have completed a combat achievement")
				)
		)
		{
			sendColoredEffect(
					"combat_achievement",
					config.combatAchievementPrimaryColor(),
					config.combatAchievementSecondaryColor(),
					config.combatAchievementStyle()
			);
		}

		if (
				config.enableDeathFlash()
						&& (
						message.contains("oh dear, you are dead")
								|| message.contains("you have died")
								|| message.contains("you are dead")
				)
		)
		{
			sendColoredEffect(
					"death",
					config.deathPrimaryColor(),
					config.deathSecondaryColor(),
					config.deathStyle()
			);
		}

		if (
				config.enableCollectionLogFlash()
						&& (
						message.contains("new item added to your collection log")
								|| message.contains("added to your collection log")
				)
		)
		{
			sendColoredEffect(
					"collection_log",
					config.collectionLogPrimaryColor(),
					config.collectionLogSecondaryColor(),
					config.collectionLogStyle()
			);
		}

		if (
				config.enableQuestFlash()
						&& (
						message.contains("quest complete")
								|| message.contains("miniquest complete")
								|| message.contains("achievement diary")
								|| message.contains("diary task complete")
								|| message.contains("you have completed the")
				)
						&& !message.contains("combat achievement")
		)
		{
			sendColoredEffect(
					"quest_complete",
					config.questPrimaryColor(),
					config.questSecondaryColor(),
					config.questStyle()
			);
		}
	}

	private void handleTestOptions(double percent, Color hpColor)
	{
		double safePercent = Math.max(percent, getCriticalThresholdDecimal() + 0.01);

		if (config.testPoisonFlash())
		{
			rgb.sendEffect("poison", hpColor, safePercent, config.poisonColor(), darken(config.poisonColor(), 0.25), config.poisonStyle());
			configManager.setConfiguration("osrgb", "testPoisonFlash", false);
		}

		if (config.testVenomFlash())
		{
			rgb.sendEffect("venom", hpColor, safePercent, config.venomColor(), darken(config.venomColor(), 0.25), config.venomStyle());
			configManager.setConfiguration("osrgb", "testVenomFlash", false);
		}

		if (config.testCriticalPulse())
		{
			rgb.sendEffect("critical_test", hpColor, safePercent, config.criticalHpColor(), darken(config.criticalHpColor(), 0.15), config.criticalStyle());
			configManager.setConfiguration("osrgb", "testCriticalPulse", false);
		}

		if (config.testLevelUpFlash())
		{
			rgb.sendEffect("level_up", hpColor, safePercent, config.levelUpStyle());
			configManager.setConfiguration("osrgb", "testLevelUpFlash", false);
		}

		if (config.testCombatAchievementFlash())
		{
			rgb.sendEffect("combat_achievement", hpColor, safePercent, config.combatAchievementPrimaryColor(), config.combatAchievementSecondaryColor(), config.combatAchievementStyle());
			configManager.setConfiguration("osrgb", "testCombatAchievementFlash", false);
		}

		if (config.testValuableDropFlash())
		{
			rgb.sendEffect("valuable_drop", hpColor, safePercent, config.valuableDropColor(), darken(config.valuableDropColor(), 0.25), config.valuableDropStyle());
			configManager.setConfiguration("osrgb", "testValuableDropFlash", false);
		}

		if (config.testDeathFlash())
		{
			rgb.sendEffect("death", hpColor, safePercent, config.deathPrimaryColor(), config.deathSecondaryColor(), config.deathStyle());
			configManager.setConfiguration("osrgb", "testDeathFlash", false);
		}

		if (config.testCollectionLogFlash())
		{
			rgb.sendEffect("collection_log", hpColor, safePercent, config.collectionLogPrimaryColor(), config.collectionLogSecondaryColor(), config.collectionLogStyle());
			configManager.setConfiguration("osrgb", "testCollectionLogFlash", false);
		}

		if (config.testQuestFlash())
		{
			rgb.sendEffect("quest_complete", hpColor, safePercent, config.questPrimaryColor(), config.questSecondaryColor(), config.questStyle());
			configManager.setConfiguration("osrgb", "testQuestFlash", false);
		}
	}

	private void handleAilments(double percent, Color hpColor)
	{
		String ailment = getAilment();

		if (!ailment.equals(lastAilment))
		{
			if (config.enablePoisonFlash() && ailment.equals("POISON") && percent > getCriticalThresholdDecimal())
			{
				rgb.sendEffect("poison", hpColor, percent, config.poisonColor(), darken(config.poisonColor(), 0.25), config.poisonStyle());
			}
			else if (config.enableVenomFlash() && ailment.equals("VENOM") && percent > getCriticalThresholdDecimal())
			{
				rgb.sendEffect("venom", hpColor, percent, config.venomColor(), darken(config.venomColor(), 0.25), config.venomStyle());
			}

			lastAilment = ailment;
		}
	}

	private void handleHpColors(int hp, int maxHp, double percent, Color hpColor)
	{
		boolean criticalPulseEnabled =
				config.enableCriticalPulse() && hp <= config.criticalHpValue();

		if (!config.enableHpColors() && !criticalPulseEnabled)
		{
			return;
		}

		boolean shouldSend =
				hp != lastHp
						|| maxHp != lastMaxHp
						|| criticalPulseEnabled != lastCriticalMode;

		if (shouldSend)
		{
			double sentPercent = criticalPulseEnabled ? 0.0 : 1.0;

			rgb.sendColor(
					hpColor,
					sentPercent,
					config.criticalStyle(),
					config.criticalHpColor(),
					darken(config.criticalHpColor(), 0.15)
			);

			lastHp = hp;
			lastMaxHp = maxHp;
			lastCriticalMode = criticalPulseEnabled;
		}
	}

	private void sendSimpleEffect(String effectName, EffectStyle style)
	{
		double percent = getCurrentHpPercent();
		Color hpColor = getHpColor(percent);

		rgb.sendEffect(
				effectName,
				hpColor,
				Math.max(percent, getCriticalThresholdDecimal() + 0.01),
				style
		);
	}

	private void sendColoredEffect(String effectName, Color primary, Color secondary, EffectStyle style)
	{
		double percent = getCurrentHpPercent();
		Color hpColor = getHpColor(percent);

		rgb.sendEffect(
				effectName,
				hpColor,
				Math.max(percent, getCriticalThresholdDecimal() + 0.01),
				primary,
				secondary,
				style
		);
	}

	private String cleanMessage(String message)
	{
		return message
				.replaceAll("<[^>]*>", "")
				.toLowerCase();
	}

	private double getCurrentHpPercent()
	{
		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		if (maxHp <= 0)
		{
			return 1.0;
		}

		return (double) hp / maxHp;
	}

	private Color getHpColor(double percent)
	{
		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);

		int high = clamp(config.highHpValue(), 1, 999);
		int medium = clamp(config.mediumHpValue(), 1, 999);
		int low = clamp(config.lowHpValue(), 1, 999);
		int critical = clamp(config.criticalHpValue(), 1, 999);

		if (!config.useSmoothHpGradient())
		{
			if (hp >= high)
			{
				return config.highHpColor();
			}

			if (hp >= medium)
			{
				return config.mediumHpColor();
			}

			if (hp >= low)
			{
				return config.lowHpColor();
			}

			return config.criticalHpColor();
		}

		if (hp >= high)
		{
			return config.highHpColor();
		}

		if (hp >= medium)
		{
			return blend(config.mediumHpColor(), config.highHpColor(), normalize(hp, medium, high));
		}

		if (hp >= low)
		{
			return blend(config.lowHpColor(), config.mediumHpColor(), normalize(hp, low, medium));
		}

		if (hp >= critical)
		{
			return blend(config.criticalHpColor(), config.lowHpColor(), normalize(hp, critical, low));
		}

		return config.criticalHpColor();
	}

	private double getCriticalThresholdDecimal()
	{
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		if (maxHp <= 0)
		{
			return 0.10;
		}

		int criticalHp = clamp(config.criticalHpValue(), 1, 999);

		return (double) criticalHp / maxHp;
	}

	private double normalize(double value, double min, double max)
	{
		if (max <= min)
		{
			return 0.0;
		}

		return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
	}

	private Color blend(Color from, Color to, double t)
	{
		t = Math.max(0.0, Math.min(1.0, t));

		int r = (int) (from.getRed() + (to.getRed() - from.getRed()) * t);
		int g = (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t);
		int b = (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * t);

		return new Color(r, g, b);
	}

	private Color darken(Color color, double amount)
	{
		amount = Math.max(0.0, Math.min(1.0, amount));

		return new Color(
				(int) (color.getRed() * amount),
				(int) (color.getGreen() * amount),
				(int) (color.getBlue() * amount)
		);
	}

	private int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private String getAilment()
	{
		int poisonValue = client.getVarpValue(VarPlayer.POISON);

		if (poisonValue >= 1000000)
		{
			return "VENOM";
		}

		if (poisonValue > 0)
		{
			return "POISON";
		}

		return "NONE";
	}

	@Provides
	OsrgbConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrgbConfig.class);
	}
}