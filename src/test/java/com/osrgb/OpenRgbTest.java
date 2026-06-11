package com.osrgb;

import me.Logicism.OpenRGB4J.OpenRGBClient;

public class OpenRgbTest
{
    public static void main(String[] args) throws Exception
    {
        OpenRGBClient client =
                new OpenRGBClient(
                        "127.0.0.1",
                        6742,
                        "OSRGB Test"
                );

        client.connect();

        System.out.println(
                "Protocol version: "
                        + client.getProtocolVersion()
        );

        System.out.println(
                "Controller count: "
                        + client.getControllerCount()
        );
    }
}