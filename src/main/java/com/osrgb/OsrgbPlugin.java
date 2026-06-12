package com.osrgb;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.PluginDescriptor;

import java.awt.Color;
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

	private final OpenRgbDirectClient rgb = new OpenRgbDirectClient();
	private final Random random = new Random();

	private int lastHp = -1;
	private int lastMaxHp = -1;
	private Color lastColor = null;

	private volatile boolean criticalActive = false;
	private Thread criticalThread;

	@Override
	protected void startUp()
	{
		log.info("OSRGB Direct Started");

		rgb.connect();

		lastHp = -1;
		lastMaxHp = -1;
		lastColor = null;
		criticalActive = false;
	}

	@Override
	protected void shutDown()
	{
		log.info("OSRGB Direct Stopped");

		stopCriticalEffectAndWait();

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

		log.info("OSRGB config changed, forcing direct color refresh");

		lastHp = -1;
		lastMaxHp = -1;
		lastColor = null;

		sendCurrentHpColor(true);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.HITPOINTS)
		{
			return;
		}

		sendCurrentHpColor(true);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		sendCurrentHpColor(false);
	}

	private void sendCurrentHpColor(boolean force)
	{
		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		if (maxHp <= 0)
		{
			return;
		}

		boolean shouldCritical =
				config.enableCriticalPulse() && hp <= config.criticalHpValue();

		if (shouldCritical)
		{
			startCriticalEffect();

			lastHp = hp;
			lastMaxHp = maxHp;
			return;
		}

		if (criticalActive)
		{
			stopCriticalEffectAndWait();

			lastHp = -1;
			lastMaxHp = -1;
			lastColor = null;
			force = true;
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

		log.info(
				"OSRGB Direct HP Color{}: {} / {} -> R:{} G:{} B:{}",
				force ? " FORCE" : "",
				hp,
				maxHp,
				hpColor.getRed(),
				hpColor.getGreen(),
				hpColor.getBlue()
		);

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

	private void startCriticalEffect()
	{
		if (criticalActive)
		{
			return;
		}

		log.info("OSRGB critical effect started");

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

		log.info("OSRGB critical effect stopping");

		criticalActive = false;

		Thread thread = criticalThread;

		if (thread != null)
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

		log.info("OSRGB critical effect stopped");
	}

	private void criticalLoop()
	{
		while (criticalActive)
		{
			try
			{
				runCriticalStep();
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

	private void runCriticalStep() throws InterruptedException
	{
		Color primary = config.criticalHpColor();
		Color secondary = darken(primary, 0.15);

		switch (config.criticalStyle())
		{
			case FLASH:
				if (!criticalActive)
				{
					return;
				}

				rgb.forceSetAllDevices(primary);
				sleepCritical(300);

				if (!criticalActive)
				{
					return;
				}

				rgb.forceSetAllDevices(secondary);
				sleepCritical(700);
				break;

			case PULSE:
				for (int i = 0; i < 10 && criticalActive; i++)
				{
					rgb.forceSetAllDevices(blend(secondary, primary, i / 9.0));
					sleepCritical(35);
				}

				for (int i = 0; i < 10 && criticalActive; i++)
				{
					rgb.forceSetAllDevices(blend(primary, secondary, i / 9.0));
					sleepCritical(35);
				}
				break;

			case STROBE:
				if (!criticalActive)
				{
					return;
				}

				rgb.forceSetAllDevices(primary);
				sleepCritical(80);

				if (!criticalActive)
				{
					return;
				}

				rgb.forceSetAllDevices(secondary);
				sleepCritical(80);
				break;

			case BREATHING:
				for (int i = 0; i < 20 && criticalActive; i++)
				{
					rgb.forceSetAllDevices(blend(secondary, primary, i / 19.0));
					sleepCritical(40);
				}

				for (int i = 0; i < 20 && criticalActive; i++)
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
					if (!criticalActive)
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

				if (!criticalActive)
				{
					return;
				}

				rgb.forceSetAllDevices(fire[random.nextInt(fire.length)]);
				sleepCritical(40 + random.nextInt(90));
				break;

			case LIGHTNING:
				if (!criticalActive)
				{
					return;
				}

				rgb.forceSetAllDevices(secondary);
				sleepCritical(50 + random.nextInt(140));

				if (random.nextDouble() < 0.45 && criticalActive)
				{
					rgb.forceSetAllDevices(Color.WHITE);
					sleepCritical(40);

					if (!criticalActive)
					{
						return;
					}

					rgb.forceSetAllDevices(primary);
					sleepCritical(50);

					if (!criticalActive)
					{
						return;
					}

					rgb.forceSetAllDevices(Color.WHITE);
					sleepCritical(30);
				}
				break;

			default:
				if (!criticalActive)
				{
					return;
				}

				rgb.forceSetAllDevices(primary);
				sleepCritical(300);

				if (!criticalActive)
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

		while (criticalActive && System.currentTimeMillis() < end)
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

	@Provides
	OsrgbConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrgbConfig.class);
	}
}