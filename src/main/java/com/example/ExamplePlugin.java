package com.example;

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
public class ExamplePlugin extends net.runelite.client.plugins.Plugin
{
	@Inject
	private Client client;

	@Inject
	private ExampleConfig config;

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
	public void onGameTick(GameTick tick)
	{
		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		if (maxHp <= 0)
		{
			return;
		}

		double percent = (double) hp / maxHp;
		Color hpColor = getSmoothHpColor(percent);

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
			double percent = getCurrentHpPercent();
			Color hpColor = getSmoothHpColor(percent);

			log.info("LEVEL UP EFFECT: {} {} -> {}", skill, oldLevel, newLevel);

			rgb.sendEffect("level_up", hpColor, Math.max(percent, 0.11));
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
				double percent = getCurrentHpPercent();
				Color hpColor = getSmoothHpColor(percent);

				log.info(
						"VALUABLE NPC DROP EFFECT: itemId={} qty={} value={} threshold={}",
						itemId,
						quantity,
						totalValue,
						threshold
				);

				rgb.sendEffect("valuable_drop", hpColor, Math.max(percent, 0.11));
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

		String message = event.getMessage().toLowerCase();

		if (
				config.enableCombatAchievementFlash()
						&& (
						message.contains("combat achievement completed")
								|| message.contains("combat achievements completed")
								|| message.contains("you have completed a combat achievement")
				)
		)
		{
			double percent = getCurrentHpPercent();
			Color hpColor = getSmoothHpColor(percent);

			log.info("COMBAT ACHIEVEMENT EFFECT");

			rgb.sendEffect("combat_achievement", hpColor, Math.max(percent, 0.11));
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
			double percent = getCurrentHpPercent();
			Color hpColor = getSmoothHpColor(percent);

			log.info("DEATH EFFECT");

			rgb.sendEffect("death", hpColor, Math.max(percent, 0.11));
		}
	}

	private void handleTestOptions(double percent, Color hpColor)
	{
		if (config.testPoisonFlash())
		{
			log.info("TEST POISON FLASH");

			rgb.sendEffect("poison", hpColor, percent);
			configManager.setConfiguration("osrgb", "testPoisonFlash", false);
		}

		if (config.testVenomFlash())
		{
			log.info("TEST VENOM FLASH");

			rgb.sendEffect("venom", hpColor, percent);
			configManager.setConfiguration("osrgb", "testVenomFlash", false);
		}

		if (config.testCriticalPulse())
		{
			log.info("TEST CRITICAL PULSE");

			rgb.sendEffect("critical_test", hpColor, percent);
			configManager.setConfiguration("osrgb", "testCriticalPulse", false);
		}

		if (config.testLevelUpFlash())
		{
			log.info("TEST LEVEL-UP FLASH");

			rgb.sendEffect("level_up", hpColor, Math.max(percent, 0.11));
			configManager.setConfiguration("osrgb", "testLevelUpFlash", false);
		}

		if (config.testCombatAchievementFlash())
		{
			log.info("TEST COMBAT ACHIEVEMENT FLASH");

			rgb.sendEffect("combat_achievement", hpColor, Math.max(percent, 0.11));
			configManager.setConfiguration("osrgb", "testCombatAchievementFlash", false);
		}

		if (config.testValuableDropFlash())
		{
			log.info("TEST VALUABLE DROP FLASH");

			rgb.sendEffect("valuable_drop", hpColor, Math.max(percent, 0.11));
			configManager.setConfiguration("osrgb", "testValuableDropFlash", false);
		}

		if (config.testDeathFlash())
		{
			log.info("TEST DEATH EFFECT");

			rgb.sendEffect("death", hpColor, Math.max(percent, 0.11));
			configManager.setConfiguration("osrgb", "testDeathFlash", false);
		}
	}

	private void handleAilments(double percent, Color hpColor)
	{
		String ailment = getAilment();

		if (!ailment.equals(lastAilment))
		{
			if (
					config.enablePoisonFlash()
							&& ailment.equals("POISON")
							&& percent > 0.10
			)
			{
				log.info("POISON EFFECT");

				rgb.sendEffect("poison", hpColor, percent);
			}
			else if (
					config.enableVenomFlash()
							&& ailment.equals("VENOM")
							&& percent > 0.10
			)
			{
				log.info("VENOM EFFECT");

				rgb.sendEffect("venom", hpColor, percent);
			}

			lastAilment = ailment;
		}
	}

	private void handleHpColors(
			int hp,
			int maxHp,
			double percent,
			Color hpColor
	)
	{
		boolean criticalPulseEnabled =
				config.enableCriticalPulse() && percent <= 0.10;

		if (!config.enableHpColors() && !criticalPulseEnabled)
		{
			return;
		}

		if (hp != lastHp || maxHp != lastMaxHp || criticalPulseEnabled)
		{
			double sentPercent = percent;

			if (!config.enableCriticalPulse() && percent <= 0.10)
			{
				sentPercent = 0.11;
			}

			log.info(
					"HP RGB: {} / {} -> {}, {}, {}",
					hp,
					maxHp,
					hpColor.getRed(),
					hpColor.getGreen(),
					hpColor.getBlue()
			);

			rgb.sendColor(hpColor, sentPercent);

			lastHp = hp;
			lastMaxHp = maxHp;
		}
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

	private Color getSmoothHpColor(double percent)
	{
		percent = Math.max(0.0, Math.min(1.0, percent));

		if (percent >= 0.50)
		{
			double t = (percent - 0.50) / 0.50;

			int r = (int) (255 * (1.0 - t));
			int g = 255;
			int b = 0;

			return new Color(r, g, b);
		}
		else
		{
			double t = percent / 0.50;

			int r = 255;
			int g = (int) (255 * t);
			int b = 0;

			return new Color(r, g, b);
		}
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
	ExampleConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExampleConfig.class);
	}
}