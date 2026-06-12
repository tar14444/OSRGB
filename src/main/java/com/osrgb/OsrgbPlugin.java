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
import java.util.Random;

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

	private final OpenRgbDirectClient rgb = new OpenRgbDirectClient();
	private final Random random = new Random();

	private int lastHp = -1;
	private int lastMaxHp = -1;
	private Color lastColor = null;
	private String lastAilment = "NONE";

	private volatile boolean criticalActive = false;
	private Thread criticalThread;

	private volatile boolean effectActive = false;
	private Thread effectThread;

	private volatile boolean deathActive = false;
	private Thread deathThread;

	private final Map<Skill, Integer> lastSkillLevels =
			new EnumMap<>(Skill.class);

	@Override
	protected void startUp()
	{
		rgb.configure(config.openRgbHost(), config.openRgbPort());
		rgb.connect();

		lastHp = -1;
		lastMaxHp = -1;
		lastColor = null;
		lastAilment = "NONE";

		criticalActive = false;
		effectActive = false;
		deathActive = false;

		lastSkillLevels.clear();
	}

	@Override
	protected void shutDown()
	{
		stopCriticalEffectAndWait();
		stopCurrentEffectAndWait();
		stopDeathEffectAndWait();

		rgb.close();

		lastHp = -1;
		lastMaxHp = -1;
		lastColor = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"osrgb".equals(event.getGroup()))
		{
			return;
		}

		if ("openRgbHost".equals(event.getKey()) || "openRgbPort".equals(event.getKey()))
		{
			stopCriticalEffectAndWait();
			stopCurrentEffectAndWait();
			stopDeathEffectAndWait();

			rgb.configure(config.openRgbHost(), config.openRgbPort());
			rgb.connect();
		}

		lastHp = -1;
		lastMaxHp = -1;
		lastColor = null;

		forceRestoreHpColor();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		handleTestOptions();
		handleAilments();
		sendCurrentHpColor(false);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.HITPOINTS)
		{
			sendCurrentHpColor(true);
			return;
		}

		if (!config.enableLevelUpFlash())
		{
			return;
		}

		Skill skill = event.getSkill();
		int newLevel = event.getLevel();

		Integer oldLevel = lastSkillLevels.get(skill);
		lastSkillLevels.put(skill, newLevel);

		if (oldLevel != null && newLevel > oldLevel)
		{
			runSimpleEffect("level_up", config.levelUpStyle());
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
				runColoredEffect(
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
				config.enableDeathFlash()
						&& (
						message.contains("oh dear, you are dead")
								|| message.contains("you died")
								|| message.contains("you have died")
								|| message.contains("you are dead")
				)
		)
		{
			runDeathEffect();
			return;
		}

		if (
				config.enableCombatAchievementFlash()
						&& (
						message.contains("combat achievement completed")
								|| message.contains("combat achievements completed")
								|| message.contains("you have completed a combat achievement")
				)
		)
		{
			runColoredEffect(
					"combat_achievement",
					config.combatAchievementPrimaryColor(),
					config.combatAchievementSecondaryColor(),
					config.combatAchievementStyle()
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
			runColoredEffect(
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
			runColoredEffect(
					"quest_complete",
					config.questPrimaryColor(),
					config.questSecondaryColor(),
					config.questStyle()
			);
		}
	}

	private void handleTestOptions()
	{
		if (config.testPoisonFlash())
		{
			runColoredEffect("poison", config.poisonColor(), darken(config.poisonColor(), 0.25), config.poisonStyle());
			configManager.setConfiguration("osrgb", "testPoisonFlash", false);
		}

		if (config.testVenomFlash())
		{
			runColoredEffect("venom", config.venomColor(), darken(config.venomColor(), 0.25), config.venomStyle());
			configManager.setConfiguration("osrgb", "testVenomFlash", false);
		}

		if (config.testCriticalPulse())
		{
			runColoredEffect("critical_test", config.criticalHpColor(), darken(config.criticalHpColor(), 0.15), config.criticalStyle());
			configManager.setConfiguration("osrgb", "testCriticalPulse", false);
		}

		if (config.testLevelUpFlash())
		{
			runSimpleEffect("level_up", config.levelUpStyle());
			configManager.setConfiguration("osrgb", "testLevelUpFlash", false);
		}

		if (config.testCombatAchievementFlash())
		{
			runColoredEffect("combat_achievement", config.combatAchievementPrimaryColor(), config.combatAchievementSecondaryColor(), config.combatAchievementStyle());
			configManager.setConfiguration("osrgb", "testCombatAchievementFlash", false);
		}

		if (config.testValuableDropFlash())
		{
			runColoredEffect("valuable_drop", config.valuableDropColor(), darken(config.valuableDropColor(), 0.25), config.valuableDropStyle());
			configManager.setConfiguration("osrgb", "testValuableDropFlash", false);
		}

		if (config.testDeathFlash())
		{
			runDeathEffect();
			configManager.setConfiguration("osrgb", "testDeathFlash", false);
		}

		if (config.testCollectionLogFlash())
		{
			runColoredEffect("collection_log", config.collectionLogPrimaryColor(), config.collectionLogSecondaryColor(), config.collectionLogStyle());
			configManager.setConfiguration("osrgb", "testCollectionLogFlash", false);
		}

		if (config.testQuestFlash())
		{
			runColoredEffect("quest_complete", config.questPrimaryColor(), config.questSecondaryColor(), config.questStyle());
			configManager.setConfiguration("osrgb", "testQuestFlash", false);
		}
	}

	private void handleAilments()
	{
		String ailment = getAilment();

		if (ailment.equals(lastAilment))
		{
			return;
		}

		if (config.enablePoisonFlash() && ailment.equals("POISON") && !criticalActive && !deathActive)
		{
			runColoredEffect("poison", config.poisonColor(), darken(config.poisonColor(), 0.25), config.poisonStyle());
		}
		else if (config.enableVenomFlash() && ailment.equals("VENOM") && !criticalActive && !deathActive)
		{
			runColoredEffect("venom", config.venomColor(), darken(config.venomColor(), 0.25), config.venomStyle());
		}

		lastAilment = ailment;
	}

	private void sendCurrentHpColor(boolean force)
	{
		if (deathActive)
		{
			return;
		}

		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		if (maxHp <= 0)
		{
			return;
		}

		if (hp <= 0)
		{
			stopCriticalEffectAndWait();
			return;
		}

		boolean shouldCritical =
				config.enableCriticalPulse() && hp <= config.criticalHpValue();

		if (shouldCritical)
		{
			stopCurrentEffectAndWait();
			startCriticalEffect();

			lastHp = hp;
			lastMaxHp = maxHp;
			return;
		}

		if (criticalActive)
		{
			stopCriticalEffectAndWait();
			delayedForceRestoreHpColor();
			return;
		}

		if (effectActive)
		{
			return;
		}

		Color hpColor = getHpColor(hp);

		if (
				!force
						&& hp == lastHp
						&& maxHp == lastMaxHp
						&& lastColor != null
						&& lastColor.equals(hpColor)
		)
		{
			return;
		}

		if (force)
		{
			rgb.forceSetAllDevices(hpColor);
		}
		else
		{
			rgb.setAllDevices(hpColor);
		}

		lastHp = hp;
		lastMaxHp = maxHp;
		lastColor = hpColor;
	}

	private void forceRestoreHpColor()
	{
		if (deathActive)
		{
			return;
		}

		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		if (maxHp <= 0 || hp <= 0)
		{
			return;
		}

		if (config.enableCriticalPulse() && hp <= config.criticalHpValue())
		{
			startCriticalEffect();
			return;
		}

		Color hpColor = getHpColor(hp);

		rgb.forceSetAllDevices(hpColor);

		lastHp = hp;
		lastMaxHp = maxHp;
		lastColor = hpColor;
	}

	private void delayedForceRestoreHpColor()
	{
		Thread thread = new Thread(
				() ->
				{
					try
					{
						Thread.sleep(150);
					}
					catch (InterruptedException ignored)
					{
					}

					lastHp = -1;
					lastMaxHp = -1;
					lastColor = null;

					forceRestoreHpColor();
				},
				"OSRGB-Restore-HP"
		);

		thread.setDaemon(true);
		thread.start();
	}

	private void runDeathEffect()
	{
		if (deathActive)
		{
			return;
		}

		deathActive = true;

		stopCriticalEffectAndWait();
		stopCurrentEffectAndWait();

		Thread thread = new Thread(
				() ->
				{
					try
					{
						runEffectAnimation(
								config.deathStyle(),
								config.deathPrimaryColor(),
								config.deathSecondaryColor(),
								true
						);
					}
					catch (InterruptedException ignored)
					{
					}
					catch (Exception e)
					{
						log.error("OSRGB death effect error", e);
					}
					finally
					{
						if (Thread.currentThread() == deathThread)
						{
							deathActive = false;
							deathThread = null;

							lastHp = -1;
							lastMaxHp = -1;
							lastColor = null;

							delayedForceRestoreHpColor();
						}
					}
				},
				"OSRGB-Death-Effect"
		);

		deathThread = thread;
		thread.setDaemon(true);
		thread.start();
	}

	private void stopDeathEffectAndWait()
	{
		if (!deathActive)
		{
			return;
		}

		deathActive = false;

		Thread thread = deathThread;

		if (thread != null && thread != Thread.currentThread())
		{
			try
			{
				thread.interrupt();
				thread.join(500);
			}
			catch (Exception ignored)
			{
			}
		}

		deathThread = null;
	}

	private void runSimpleEffect(String effectName, EffectStyle style)
	{
		Color hpColor = getHpColor(client.getBoostedSkillLevel(Skill.HITPOINTS));
		runEffect(effectName, hpColor, darken(hpColor, 0.25), style);
	}

	private void runColoredEffect(String effectName, Color primary, Color secondary, EffectStyle style)
	{
		runEffect(effectName, primary, secondary, style);
	}

	private synchronized void runEffect(String effectName, Color primary, Color secondary, EffectStyle style)
	{
		if (deathActive)
		{
			return;
		}

		if (criticalActive && !effectName.equals("critical_test"))
		{
			return;
		}

		stopCurrentEffectAndWait();

		effectActive = true;

		Thread thread = new Thread(
				() ->
				{
					try
					{
						runEffectAnimation(style, primary, secondary, false);
					}
					catch (InterruptedException ignored)
					{
					}
					catch (Exception e)
					{
						log.error("OSRGB effect error: {}", effectName, e);
					}
					finally
					{
						if (Thread.currentThread() == effectThread)
						{
							effectActive = false;
							effectThread = null;

							lastHp = -1;
							lastMaxHp = -1;
							lastColor = null;

							delayedForceRestoreHpColor();
						}
					}
				},
				"OSRGB-Effect-" + effectName
		);

		effectThread = thread;
		thread.setDaemon(true);
		thread.start();
	}

	private void stopCurrentEffectAndWait()
	{
		if (!effectActive)
		{
			return;
		}

		effectActive = false;

		Thread thread = effectThread;

		if (thread != null && thread != Thread.currentThread())
		{
			try
			{
				thread.interrupt();
				thread.join(500);
			}
			catch (Exception ignored)
			{
			}
		}

		effectThread = null;
	}

	private void runEffectAnimation(EffectStyle style, Color primary, Color secondary, boolean deathEffect) throws InterruptedException
	{
		switch (style)
		{
			case FLASH:
				for (int i = 0; i < 5 && isAnimationActive(deathEffect); i++)
				{
					rgb.forceSetAllDevices(primary);
					sleepAnimation(300, deathEffect);
					rgb.forceSetAllDevices(secondary);
					sleepAnimation(700, deathEffect);
				}
				break;

			case PULSE:
				for (int loop = 0; loop < 5 && isAnimationActive(deathEffect); loop++)
				{
					for (int i = 0; i < 10 && isAnimationActive(deathEffect); i++)
					{
						rgb.forceSetAllDevices(blend(secondary, primary, i / 9.0));
						sleepAnimation(35, deathEffect);
					}

					for (int i = 0; i < 10 && isAnimationActive(deathEffect); i++)
					{
						rgb.forceSetAllDevices(blend(primary, secondary, i / 9.0));
						sleepAnimation(35, deathEffect);
					}
				}
				break;

			case STROBE:
				for (int i = 0; i < 12 && isAnimationActive(deathEffect); i++)
				{
					rgb.forceSetAllDevices(primary);
					sleepAnimation(80, deathEffect);
					rgb.forceSetAllDevices(secondary);
					sleepAnimation(80, deathEffect);
				}
				break;

			case BREATHING:
				for (int loop = 0; loop < 3 && isAnimationActive(deathEffect); loop++)
				{
					for (int i = 0; i < 20 && isAnimationActive(deathEffect); i++)
					{
						rgb.forceSetAllDevices(blend(secondary, primary, i / 19.0));
						sleepAnimation(40, deathEffect);
					}

					for (int i = 0; i < 20 && isAnimationActive(deathEffect); i++)
					{
						rgb.forceSetAllDevices(blend(primary, secondary, i / 19.0));
						sleepAnimation(40, deathEffect);
					}
				}
				break;

			case RAINBOW:
				Color[] rainbow = new Color[]
						{
								new Color(255, 0, 0),
								new Color(255, 128, 0),
								new Color(255, 255, 0),
								new Color(0, 255, 0),
								new Color(0, 255, 255),
								new Color(0, 80, 255),
								new Color(170, 0, 255)
						};

				for (int loop = 0; loop < 3 && isAnimationActive(deathEffect); loop++)
				{
					for (Color color : rainbow)
					{
						if (!isAnimationActive(deathEffect))
						{
							break;
						}

						rgb.forceSetAllDevices(color);
						sleepAnimation(120, deathEffect);
					}
				}
				break;

			case FIRE:
				Color[] fire = new Color[]
						{
								primary,
								secondary,
								new Color(255, 80, 0),
								new Color(255, 150, 0),
								new Color(255, 220, 40),
								darken(primary, 0.35)
						};

				for (int i = 0; i < 40 && isAnimationActive(deathEffect); i++)
				{
					rgb.forceSetAllDevices(fire[random.nextInt(fire.length)]);
					sleepAnimation(40 + random.nextInt(90), deathEffect);
				}
				break;

			case LIGHTNING:
				for (int i = 0; i < 25 && isAnimationActive(deathEffect); i++)
				{
					rgb.forceSetAllDevices(secondary);
					sleepAnimation(50 + random.nextInt(140), deathEffect);

					if (random.nextDouble() < 0.45 && isAnimationActive(deathEffect))
					{
						rgb.forceSetAllDevices(Color.WHITE);
						sleepAnimation(40, deathEffect);
						rgb.forceSetAllDevices(primary);
						sleepAnimation(50, deathEffect);
						rgb.forceSetAllDevices(Color.WHITE);
						sleepAnimation(30, deathEffect);
					}
				}
				break;

			default:
				for (int i = 0; i < 5 && isAnimationActive(deathEffect); i++)
				{
					rgb.forceSetAllDevices(primary);
					sleepAnimation(300, deathEffect);
					rgb.forceSetAllDevices(secondary);
					sleepAnimation(700, deathEffect);
				}
				break;
		}
	}

	private boolean isAnimationActive(boolean deathEffect)
	{
		return deathEffect ? deathActive : effectActive;
	}

	private void sleepAnimation(long millis, boolean deathEffect) throws InterruptedException
	{
		long end = System.currentTimeMillis() + millis;

		while (isAnimationActive(deathEffect) && System.currentTimeMillis() < end)
		{
			Thread.sleep(Math.min(25, end - System.currentTimeMillis()));
		}
	}

	private void startCriticalEffect()
	{
		if (criticalActive || deathActive)
		{
			return;
		}

		criticalActive = true;

		criticalThread = new Thread(this::criticalLoop, "OSRGB-Critical-Effect");
		criticalThread.setDaemon(true);
		criticalThread.start();
	}

	private void stopCriticalEffectAndWait()
	{
		if (!criticalActive)
		{
			return;
		}

		criticalActive = false;

		Thread thread = criticalThread;

		if (thread != null && thread != Thread.currentThread())
		{
			try
			{
				thread.interrupt();
				thread.join(500);
			}
			catch (Exception ignored)
			{
			}
		}

		criticalThread = null;
	}

	private void criticalLoop()
	{
		while (criticalActive && !deathActive)
		{
			try
			{
				runCriticalAnimationStep(
						config.criticalStyle(),
						config.criticalHpColor(),
						darken(config.criticalHpColor(), 0.15)
				);
			}
			catch (InterruptedException e)
			{
				return;
			}
			catch (Exception e)
			{
				log.error("Critical effect error", e);
				return;
			}
		}
	}

	private void runCriticalAnimationStep(EffectStyle style, Color primary, Color secondary) throws InterruptedException
	{
		switch (style)
		{
			case FLASH:
				if (!criticalActive || deathActive)
				{
					return;
				}
				rgb.forceSetAllDevices(primary);
				sleepCritical(300);
				if (!criticalActive || deathActive)
				{
					return;
				}
				rgb.forceSetAllDevices(secondary);
				sleepCritical(700);
				break;

			case PULSE:
				for (int i = 0; i < 10 && criticalActive && !deathActive; i++)
				{
					rgb.forceSetAllDevices(blend(secondary, primary, i / 9.0));
					sleepCritical(35);
				}

				for (int i = 0; i < 10 && criticalActive && !deathActive; i++)
				{
					rgb.forceSetAllDevices(blend(primary, secondary, i / 9.0));
					sleepCritical(35);
				}
				break;

			case STROBE:
				if (!criticalActive || deathActive)
				{
					return;
				}
				rgb.forceSetAllDevices(primary);
				sleepCritical(80);
				if (!criticalActive || deathActive)
				{
					return;
				}
				rgb.forceSetAllDevices(secondary);
				sleepCritical(80);
				break;

			case BREATHING:
				for (int i = 0; i < 20 && criticalActive && !deathActive; i++)
				{
					rgb.forceSetAllDevices(blend(secondary, primary, i / 19.0));
					sleepCritical(40);
				}

				for (int i = 0; i < 20 && criticalActive && !deathActive; i++)
				{
					rgb.forceSetAllDevices(blend(primary, secondary, i / 19.0));
					sleepCritical(40);
				}
				break;

			case RAINBOW:
				Color[] rainbow = new Color[]
						{
								new Color(255, 0, 0),
								new Color(255, 128, 0),
								new Color(255, 255, 0),
								new Color(0, 255, 0),
								new Color(0, 255, 255),
								new Color(0, 80, 255),
								new Color(170, 0, 255)
						};

				for (Color color : rainbow)
				{
					if (!criticalActive || deathActive)
					{
						break;
					}

					rgb.forceSetAllDevices(color);
					sleepCritical(120);
				}
				break;

			case FIRE:
				Color[] fire = new Color[]
						{
								primary,
								secondary,
								new Color(255, 80, 0),
								new Color(255, 150, 0),
								new Color(255, 220, 40),
								darken(primary, 0.35)
						};

				if (!criticalActive || deathActive)
				{
					return;
				}

				rgb.forceSetAllDevices(fire[random.nextInt(fire.length)]);
				sleepCritical(40 + random.nextInt(90));
				break;

			case LIGHTNING:
				if (!criticalActive || deathActive)
				{
					return;
				}

				rgb.forceSetAllDevices(secondary);
				sleepCritical(50 + random.nextInt(140));

				if (random.nextDouble() < 0.45 && criticalActive && !deathActive)
				{
					rgb.forceSetAllDevices(Color.WHITE);
					sleepCritical(40);

					if (!criticalActive || deathActive)
					{
						return;
					}

					rgb.forceSetAllDevices(primary);
					sleepCritical(50);

					if (!criticalActive || deathActive)
					{
						return;
					}

					rgb.forceSetAllDevices(Color.WHITE);
					sleepCritical(30);
				}
				break;

			default:
				if (!criticalActive || deathActive)
				{
					return;
				}
				rgb.forceSetAllDevices(primary);
				sleepCritical(300);
				if (!criticalActive || deathActive)
				{
					return;
				}
				rgb.forceSetAllDevices(secondary);
				sleepCritical(700);
				break;
		}
	}

	private void sleepCritical(long millis) throws InterruptedException
	{
		long end = System.currentTimeMillis() + millis;

		while (criticalActive && !deathActive && System.currentTimeMillis() < end)
		{
			Thread.sleep(Math.min(25, end - System.currentTimeMillis()));
		}
	}

	private Color getHpColor(int hp)
	{
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

	private String cleanMessage(String message)
	{
		return message
				.replaceAll("<[^>]*>", "")
				.toLowerCase();
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