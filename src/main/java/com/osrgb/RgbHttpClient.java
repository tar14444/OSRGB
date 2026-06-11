package com.osrgb;

import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
public class RgbHttpClient
{
    public void sendColor(Color color, double hpPercent)
    {
        String json = String.format(
                Locale.US,
                "{\"r\":%d,\"g\":%d,\"b\":%d,\"hpPercent\":%.2f}",
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                hpPercent
        );

        sendJson(json);
    }

    public void sendColor(
            Color color,
            double hpPercent,
            EffectStyle style,
            Color primaryColor,
            Color secondaryColor
    )
    {
        String json = String.format(
                Locale.US,
                "{\"r\":%d,\"g\":%d,\"b\":%d,\"hpPercent\":%.2f,"
                        + "\"style\":\"%s\","
                        + "\"primaryR\":%d,\"primaryG\":%d,\"primaryB\":%d,"
                        + "\"secondaryR\":%d,\"secondaryG\":%d,\"secondaryB\":%d}",
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                hpPercent,
                style.name().toLowerCase(Locale.US),
                primaryColor.getRed(),
                primaryColor.getGreen(),
                primaryColor.getBlue(),
                secondaryColor.getRed(),
                secondaryColor.getGreen(),
                secondaryColor.getBlue()
        );

        sendJson(json);
    }

    public void sendEffect(
            String effect,
            Color returnColor,
            double hpPercent,
            EffectStyle style
    )
    {
        sendEffect(
                effect,
                returnColor,
                hpPercent,
                returnColor,
                darken(returnColor, 0.25),
                style
        );
    }

    public void sendEffect(
            String effect,
            Color returnColor,
            double hpPercent,
            Color primaryColor,
            Color secondaryColor,
            EffectStyle style
    )
    {
        String json = String.format(
                Locale.US,
                "{\"effect\":\"%s\",\"style\":\"%s\",\"returnR\":%d,\"returnG\":%d,\"returnB\":%d,"
                        + "\"primaryR\":%d,\"primaryG\":%d,\"primaryB\":%d,"
                        + "\"secondaryR\":%d,\"secondaryG\":%d,\"secondaryB\":%d,"
                        + "\"hpPercent\":%.2f}",
                effect,
                style.name().toLowerCase(Locale.US),
                returnColor.getRed(),
                returnColor.getGreen(),
                returnColor.getBlue(),
                primaryColor.getRed(),
                primaryColor.getGreen(),
                primaryColor.getBlue(),
                secondaryColor.getRed(),
                secondaryColor.getGreen(),
                secondaryColor.getBlue(),
                hpPercent
        );

        sendJson(json);
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

    private void sendJson(String json)
    {
        try
        {
            URL url = new URL("http://127.0.0.1:5000/rgb");

            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream())
            {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            connection.getResponseCode();
            connection.disconnect();
        }
        catch (Exception e)
        {
            log.error("Failed to send RGB HTTP", e);
        }
    }
}