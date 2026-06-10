package com.example;

import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Slf4j
public class RgbHttpClient
{
    public void sendColor(Color color, double hpPercent)
    {
        String json = String.format(
                "{\"r\":%d,\"g\":%d,\"b\":%d,\"hpPercent\":%.2f}",
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                hpPercent
        );

        sendJson(json);
    }

    public void sendEffect(String effect, Color returnColor, double hpPercent)
    {
        String json = String.format(
                "{\"effect\":\"%s\",\"returnR\":%d,\"returnG\":%d,\"returnB\":%d,\"hpPercent\":%.2f}",
                effect,
                returnColor.getRed(),
                returnColor.getGreen(),
                returnColor.getBlue(),
                hpPercent
        );

        sendJson(json);
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