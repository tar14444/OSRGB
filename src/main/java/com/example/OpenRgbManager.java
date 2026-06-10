package com.example;

import lombok.extern.slf4j.Slf4j;

import java.awt.Color;

import me.Logicism.OpenRGB4J.OpenRGBClient;
import me.Logicism.OpenRGB4J.openrgb.entities.OpenRGBColor;
import me.Logicism.OpenRGB4J.openrgb.entities.OpenRGBDevice;

@Slf4j
public class OpenRgbManager
{
    private OpenRGBClient client;
    private boolean connected = false;

    public void connect(String host, int port)
    {
        try
        {
            client = new OpenRGBClient(host, port, "OSRGB RuneLite");
            client.connect();

            log.info("OpenRGB connected. Protocol: {}", client.getProtocolVersion());

            int count = client.getControllerCount();
            log.info("Controller count: {}", count);

            for (int i = 0; i < count; i++)
            {
                OpenRGBDevice device = client.getDeviceController(i);
                log.info("Device {}: {}", i, device.getName());
            }

            connected = true;
        }
        catch (Exception e)
        {
            connected = false;
            log.error("Failed to connect to OpenRGB", e);
        }
    }

    public void disconnect()
    {
        try
        {
            if (client != null)
            {
                client.disconnect();
            }
        }
        catch (Exception ignored)
        {
        }

        connected = false;
    }

    public void setColor(Color color)
    {
        if (!connected || client == null)
        {
            return;
        }

        try
        {
            OpenRGBColor openColor = new OpenRGBColor(
                    color.getRed(),
                    color.getGreen(),
                    color.getBlue()
            );

            client.updateLED(0, 0, openColor);

            log.info(
                    "Sent test color R:{} G:{} B:{}",
                    color.getRed(),
                    color.getGreen(),
                    color.getBlue()
            );
        }
        catch (Exception e)
        {
            log.error("Failed to send RGB", e);
        }
    }
}