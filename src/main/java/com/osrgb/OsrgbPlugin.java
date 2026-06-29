package com.osrgb;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
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
	private enum AnimationKind
	{
		NONE,
		EFFECT,
		OVERLAY,
		DEATH
	}

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

	private final Map<Skill, Integer> lastSkillLevels =
			new EnumMap<>(Skill.class);

	private int lastHp = -1;
	private int lastMaxHp = -1;
	private Color lastColor = null;
	private String lastAilment = "NONE";

	private AnimationKind animationKind = AnimationKind.NONE;
	private String animationName = "";
	private EffectStyle animationStyle = EffectStyle.FLASH;
	private Color animationPrimary = Color.WHITE;
	private Color animationSecondary = Color.BLACK;
	private long animationStartTime = 0;
	private long animationDuration = 0;

	private long lastFrameTime = 0;
	private boolean forceHpRefresh = true;

	@Override
	protected void startUp()
	{
		rgb.configure(config.openRgbHost(), config.openRgbPort());
		rgb.connect();

		clearState();
	}

	@Override
	protected void shutDown()
	{
		clearAnimation();
		rgb.close();
		clearState();
	}

	private void clearState()
	{
		lastHp = -1;
		lastMaxHp = -1;
		lastColor = null;
		lastAilment = "NONE";
		lastSkillLevels.clear();
		forceHpRefresh = true;
		clearAnimation();
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
			clearAnimation();

			rgb.configure(config.openRgbHost(), config.openRgbPort());
			rgb.connect();
		}

		lastHp = -1;
		lastMaxHp = -1;
		lastColor = null;
		forceHpRefresh = true;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		handleTestOptions();
		handleAilments();
	}

	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		long now = System.currentTimeMillis();

		if (now - lastFrameTime < 40)
		{
			return;
		}

		lastFrameTime = now;

		updateRgb(now);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.HITPOINTS)
		{
			lastHp = -1;
			lastMaxHp = -1;
			lastColor = null;
			forceHpRefresh = true;
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
			triggerLevelUpEvent();
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
				triggerValuableDropEvent();
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
			triggerDeathEvent();
			return;
		}

		if (
				config.enableCombatAchievementFlash()
						&& (
						message.contains("ca_id:")
								|| message.contains("combat task")
								|| message.contains("combat achievement")
								|| message.contains("combat achievements")
				)
		)
		{
			triggerCombatAchievementEvent();
		}

		if (
				config.enableCollectionLogFlash()
						&& (
						message.contains("collection log")
								|| message.contains("new item added")
								|| message.contains("added to your collection log")
				)
		)
		{
			triggerCollectionLogEvent();
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
						&& !message.contains("combat task")
		)
		{
			triggerQuestEvent();
		}
	}

	private void handleTestOptions()
	{
		if (config.testPoisonFlash())
		{
			triggerPoisonEvent();
			configManager.setConfiguration("osrgb", "testPoisonFlash", false);
		}

		if (config.testVenomFlash())
		{
			triggerVenomEvent();
			configManager.setConfiguration("osrgb", "testVenomFlash", false);
		}

		if (config.testCriticalPulse())
		{
			startAnimation(
					"critical_test",
					AnimationKind.EFFECT,
					config.criticalStyle(),
					config.criticalHpColor(),
					darken(config.criticalHpColor(), 0.15),
					getEffectDuration(config.criticalStyle())
			);
			configManager.setConfiguration("osrgb", "testCriticalPulse", false);
		}

		if (config.testLevelUpFlash())
		{
			triggerLevelUpEvent();
			configManager.setConfiguration("osrgb", "testLevelUpFlash", false);
		}

		if (config.testCombatAchievementFlash())
		{
			triggerCombatAchievementEvent();
			configManager.setConfiguration("osrgb", "testCombatAchievementFlash", false);
		}

		if (config.testValuableDropFlash())
		{
			triggerValuableDropEvent();
			configManager.setConfiguration("osrgb", "testValuableDropFlash", false);
		}

		if (config.testDeathFlash())
		{
			triggerDeathEvent();
			configManager.setConfiguration("osrgb", "testDeathFlash", false);
		}

		if (config.testCollectionLogFlash())
		{
			triggerCollectionLogEvent();
			configManager.setConfiguration("osrgb", "testCollectionLogFlash", false);
		}

		if (config.testQuestFlash())
		{
			triggerQuestEvent();
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

		if (config.enablePoisonFlash() && ailment.equals("POISON") && !isCriticalHp())
		{
			triggerPoisonEvent();
		}
		else if (config.enableVenomFlash() && ailment.equals("VENOM") && !isCriticalHp())
		{
			triggerVenomEvent();
		}

		lastAilment = ailment;
	}

	private void triggerPoisonEvent()
	{
		startAnimation(
				"poison",
				AnimationKind.OVERLAY,
				config.poisonStyle(),
				config.poisonColor(),
				darken(config.poisonColor(), 0.25),
				getOverlayDuration(config.poisonStyle())
		);
	}

	private void triggerVenomEvent()
	{
		startAnimation(
				"venom",
				AnimationKind.OVERLAY,
				config.venomStyle(),
				config.venomColor(),
				darken(config.venomColor(), 0.25),
				getOverlayDuration(config.venomStyle())
		);
	}

	private void triggerLevelUpEvent()
	{
		Color hpColor = getHpColor(getSafeHp());

		startAnimation(
				"level_up",
				AnimationKind.EFFECT,
				config.levelUpStyle(),
				hpColor,
				darken(hpColor, 0.25),
				getEffectDuration(config.levelUpStyle())
		);
	}

	private void triggerCombatAchievementEvent()
	{
		startAnimation(
				"combat_achievement",
				AnimationKind.EFFECT,
				config.combatAchievementStyle(),
				config.combatAchievementPrimaryColor(),
				config.combatAchievementSecondaryColor(),
				getEffectDuration(config.combatAchievementStyle())
		);
	}

	private void triggerValuableDropEvent()
	{
		startAnimation(
				"valuable_drop",
				AnimationKind.EFFECT,
				config.valuableDropStyle(),
				config.valuableDropColor(),
				darken(config.valuableDropColor(), 0.25),
				getEffectDuration(config.valuableDropStyle())
		);
	}

	private void triggerDeathEvent()
	{
		startAnimation(
				"death",
				AnimationKind.DEATH,
				config.deathStyle(),
				config.deathPrimaryColor(),
				config.deathSecondaryColor(),
				getEffectDuration(config.deathStyle())
		);
	}

	private void triggerCollectionLogEvent()
	{
		startAnimation(
				"collection_log",
				AnimationKind.EFFECT,
				config.collectionLogStyle(),
				config.collectionLogPrimaryColor(),
				config.collectionLogSecondaryColor(),
				getEffectDuration(config.collectionLogStyle())
		);
	}

	private void triggerQuestEvent()
	{
		startAnimation(
				"quest_complete",
				AnimationKind.EFFECT,
				config.questStyle(),
				config.questPrimaryColor(),
				config.questSecondaryColor(),
				getEffectDuration(config.questStyle())
		);
	}

	private void startAnimation(
			String name,
			AnimationKind kind,
			EffectStyle style,
			Color primary,
			Color secondary,
			long duration
	)
	{
		if (kind != AnimationKind.DEATH && animationKind == AnimationKind.DEATH)
		{
			return;
		}

		if (kind == AnimationKind.EFFECT && isCriticalHp() && !"critical_test".equals(name))
		{
			return;
		}

		if (kind == AnimationKind.OVERLAY && isCriticalHp())
		{
			return;
		}

		animationName = name;
		animationKind = kind;
		animationStyle = style;
		animationPrimary = primary;
		animationSecondary = secondary;
		animationStartTime = System.currentTimeMillis();
		animationDuration = Math.max(250, duration);

		lastHp = -1;
		lastMaxHp = -1;
		lastColor = null;
		forceHpRefresh = true;
	}

	private void clearAnimation()
	{
		animationKind = AnimationKind.NONE;
		animationName = "";
		animationStartTime = 0;
		animationDuration = 0;
		forceHpRefresh = true;
	}

	private void updateRgb(long now)
	{
		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		if (maxHp <= 0)
		{
			return;
		}

		if (animationKind != AnimationKind.NONE)
		{
			long elapsed = now - animationStartTime;

			if (elapsed >= animationDuration)
			{
				clearAnimation();
				forceHpRefresh = true;
			}
			else
			{
				sendAnimationFrame(elapsed, hp, maxHp);
				return;
			}
		}

		if (hp > 0 && isCriticalHp())
		{
			sendCriticalFrame(now, hp);
			return;
		}

		if (hp <= 0)
		{
			return;
		}

		sendHpColor(hp, maxHp, forceHpRefresh);
		forceHpRefresh = false;
	}

	private void sendAnimationFrame(long elapsed, int hp, int maxHp)
	{
		if (animationKind == AnimationKind.DEATH)
		{
			Color frame = getStyleFrame(animationStyle, animationPrimary, animationSecondary, elapsed, animationDuration);
			rgb.forceSetAllDevices(frame);
			lastColor = frame;
			return;
		}

		if (hp <= 0)
		{
			clearAnimation();
			return;
		}

		if (animationKind == AnimationKind.OVERLAY)
		{
			if (isCriticalHp())
			{
				clearAnimation();
				return;
			}

			Color hpColor = getHpColor(hp);
			double strength = getOverlayStrength(animationStyle, elapsed, animationDuration);
			Color overlayFrame = getStyleFrame(animationStyle, animationPrimary, animationSecondary, elapsed, animationDuration);
			Color mixed = blend(hpColor, overlayFrame, strength);

			rgb.forceSetAllDevices(mixed);

			lastHp = hp;
			lastMaxHp = maxHp;
			lastColor = mixed;
			return;
		}

		if (animationKind == AnimationKind.EFFECT)
		{
			if (isCriticalHp() && !"critical_test".equals(animationName))
			{
				clearAnimation();
				return;
			}

			Color frame = getStyleFrame(animationStyle, animationPrimary, animationSecondary, elapsed, animationDuration);
			rgb.forceSetAllDevices(frame);
			lastColor = frame;
		}
	}

	private void sendCriticalFrame(long now, int hp)
	{
		Color primary = config.criticalHpColor();
		Color secondary = darken(primary, 0.15);

		Color frame = getCriticalFrame(config.criticalStyle(), primary, secondary, now, hp);

		rgb.forceSetAllDevices(frame);

		lastHp = hp;
		lastMaxHp = client.getRealSkillLevel(Skill.HITPOINTS);
		lastColor = frame;
	}

	private void sendHpColor(int hp, int maxHp, boolean force)
	{
		if (!config.enableHpColors())
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

	private Color getCriticalFrame(EffectStyle style, Color primary, Color secondary, long now, int hp)
	{
		if (style == EffectStyle.PANIC)
		{
			int critical = Math.max(1, config.criticalHpValue());
			double danger = 1.0 - Math.min(1.0, hp / (double) critical);
			long speed = (long) (220 - (danger * 160));
			long phase = (now / Math.max(40, speed)) % 2;

			return phase == 0 ? primary : secondary;
		}

		if (style == EffectStyle.GRIM_REAPER)
		{
			long cycle = now % 1400;
			double t = cycle < 700
					? cycle / 700.0
					: 1.0 - ((cycle - 700) / 700.0);

			return blend(new Color(3, 0, 5), primary, t);
		}

		if (style == EffectStyle.LOOT_SHOWER)
		{
			return ((now / 90) % 3) == 0
					? Color.WHITE
					: (((now / 90) % 2) == 0 ? primary : secondary);
		}

		return getLoopingStyleFrame(style, primary, secondary, now);
	}

	private Color getStyleFrame(
			EffectStyle style,
			Color primary,
			Color secondary,
			long elapsed,
			long duration
	)
	{
		switch (style)
		{
			case PANIC:
			{
				long phase = (elapsed / 90) % 2;
				return phase == 0 ? primary : secondary;
			}

			case LOOT_SHOWER:
			{
				long phase = elapsed / 70;

				if (phase % 5 == 0)
				{
					return Color.WHITE;
				}

				return phase % 2 == 0 ? primary : secondary;
			}

			case GRIM_REAPER:
			{
				double cycle = (elapsed % 1600) / 1600.0;

				if (cycle < 0.5)
				{
					return blend(new Color(3, 0, 5), primary, cycle * 2.0);
				}

				return blend(primary, secondary, (cycle - 0.5) * 2.0);
			}

			case FLASH:
			{
				long cycle = elapsed % 1000;
				return cycle < 300 ? primary : secondary;
			}

			case PULSE:
			case BREATHING:
			{
				double t = triangleWave(elapsed, style == EffectStyle.BREATHING ? 1600 : 700);
				return blend(secondary, primary, t);
			}

			case STROBE:
			{
				return ((elapsed / 80) % 2) == 0 ? primary : secondary;
			}

			case RAINBOW:
			{
				Color[] rainbow = getRainbow();
				int index = (int) ((elapsed / 120) % rainbow.length);
				return rainbow[index];
			}

			case FIRE:
			{
				Color[] fire = new Color[]
						{
								primary,
								secondary,
								new Color(255, 80, 0),
								new Color(255, 150, 0),
								new Color(255, 220, 40),
								darken(primary, 0.35)
						};

				int index = (int) ((elapsed / 80) % fire.length);
				return fire[index];
			}

			case LIGHTNING:
			{
				long cycle = elapsed % 600;

				if (cycle < 50 || (cycle > 120 && cycle < 160))
				{
					return Color.WHITE;
				}

				return cycle < 250 ? primary : secondary;
			}

			default:
				return primary;
		}
	}

	private Color getLoopingStyleFrame(EffectStyle style, Color primary, Color secondary, long now)
	{
		return getStyleFrame(style, primary, secondary, now % 5000, 5000);
	}

	private double getOverlayStrength(EffectStyle style, long elapsed, long duration)
	{
		switch (style)
		{
			case STROBE:
				return ((elapsed / 100) % 2) == 0 ? 0.65 : 0.20;

			case BREATHING:
			case PULSE:
				return 0.15 + (triangleWave(elapsed, 700) * 0.55);

			default:
				return ((elapsed / 120) % 2) == 0 ? 0.65 : 0.25;
		}
	}

	private long getEffectDuration(EffectStyle style)
	{
		switch (style)
		{
			case STROBE:
				return 2000;

			case BREATHING:
				return 3200;

			case RAINBOW:
				return 2600;

			case FIRE:
				return 3600;

			case LIGHTNING:
				return 3000;

			case PANIC:
				return 2200;

			case LOOT_SHOWER:
				return 2600;

			case GRIM_REAPER:
				return 4200;

			case FLASH:
			case PULSE:
			default:
				return 2600;
		}
	}

	private long getOverlayDuration(EffectStyle style)
	{
		switch (style)
		{
			case STROBE:
				return 1800;

			case BREATHING:
			case PULSE:
				return 2200;

			default:
				return 1800;
		}
	}

	private boolean isCriticalHp()
	{
		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);

		return config.enableCriticalPulse()
				&& hp > 0
				&& hp <= config.criticalHpValue();
	}

	private int getSafeHp()
	{
		return Math.max(1, client.getBoostedSkillLevel(Skill.HITPOINTS));
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

	private Color[] getRainbow()
	{
		return new Color[]
				{
						new Color(255, 0, 0),
						new Color(255, 128, 0),
						new Color(255, 255, 0),
						new Color(0, 255, 0),
						new Color(0, 255, 255),
						new Color(0, 80, 255),
						new Color(170, 0, 255)
				};
	}

	private double triangleWave(long elapsed, long period)
	{
		double t = (elapsed % period) / (double) period;

		if (t < 0.5)
		{
			return t * 2.0;
		}

		return 1.0 - ((t - 0.5) * 2.0);
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