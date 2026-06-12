package com.osrgb;

import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class OpenRgbDirectClient implements Closeable
{
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 6742;

    private static final int REQUEST_CONTROLLER_COUNT = 0;
    private static final int REQUEST_CONTROLLER_DATA = 1;
    private static final int SET_CLIENT_NAME = 50;
    private static final int UPDATE_LEDS = 1050;
    private static final int SET_CUSTOM_MODE = 1100;

    private Socket socket;
    private DataInputStream input;
    private OutputStream output;

    private boolean connected = false;
    private final List<DeviceInfo> devices = new ArrayList<>();

    private Color lastSentColor = null;
    private long packetsSent = 0;
    private long lastStatsLogTime = 0;

    public synchronized boolean connect()
    {
        if (connected)
        {
            return true;
        }

        try
        {
            log.info("Connecting to OpenRGB SDK server at {}:{}", HOST, PORT);

            socket = new Socket();
            socket.connect(new InetSocketAddress(HOST, PORT), 1500);
            socket.setSoTimeout(3000);

            input = new DataInputStream(socket.getInputStream());
            output = socket.getOutputStream();

            sendClientName("OSRGB RuneLite");

            int count = requestControllerCount();

            devices.clear();

            log.info("OpenRGB controller count: {}", count);

            for (int i = 0; i < count; i++)
            {
                DeviceInfo device = requestDeviceInfo(i);

                if (device.ledCount > 0)
                {
                    devices.add(device);

                    log.info(
                            "OpenRGB device {}: {} LEDs={}",
                            device.deviceIndex,
                            device.name,
                            device.ledCount
                    );

                    setCustomMode(device.deviceIndex);
                }
            }

            connected = true;
            lastStatsLogTime = System.currentTimeMillis();

            log.info("OpenRGB direct connection established. Usable devices: {}", devices.size());

            return true;
        }
        catch (Exception e)
        {
            log.error("Failed to connect directly to OpenRGB SDK server", e);
            disconnect();
            return false;
        }
    }

    public synchronized void disconnect()
    {
        if (connected)
        {
            log.info("OpenRGB direct disconnected");
        }

        connected = false;
        devices.clear();

        try
        {
            if (input != null)
            {
                input.close();
            }
        }
        catch (Exception ignored)
        {
        }

        try
        {
            if (output != null)
            {
                output.close();
            }
        }
        catch (Exception ignored)
        {
        }

        try
        {
            if (socket != null)
            {
                socket.close();
            }
        }
        catch (Exception ignored)
        {
        }

        input = null;
        output = null;
        socket = null;
        lastSentColor = null;
    }

    public synchronized void setAllDevices(Color color)
    {
        setAllDevices(color, false);
    }

    public synchronized void forceSetAllDevices(Color color)
    {
        setAllDevices(color, true);
    }

    private synchronized void setAllDevices(Color color, boolean force)
    {
        if (!connected && !connect())
        {
            return;
        }

        if (!force && lastSentColor != null && lastSentColor.equals(color))
        {
            return;
        }

        try
        {
            log.info(
                    "OpenRGB direct color{} -> R:{} G:{} B:{}",
                    force ? " FORCE" : "",
                    color.getRed(),
                    color.getGreen(),
                    color.getBlue()
            );

            for (DeviceInfo device : devices)
            {
                updateDeviceLeds(device.deviceIndex, device.ledCount, color);
            }

            lastSentColor = color;
            logStatsOccasionally();
        }
        catch (Exception e)
        {
            log.error("Failed to set OpenRGB color directly", e);
            disconnect();
        }
    }

    private void logStatsOccasionally()
    {
        long now = System.currentTimeMillis();

        if (now - lastStatsLogTime >= 300000)
        {
            log.info("OpenRGB packets sent: {}", packetsSent);
            lastStatsLogTime = now;
        }
    }

    @Override
    public void close()
    {
        disconnect();
    }

    private void sendClientName(String name) throws IOException
    {
        byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.UTF_8);
        sendPacket(0, SET_CLIENT_NAME, nameBytes);
    }

    private int requestControllerCount() throws IOException
    {
        sendPacket(0, REQUEST_CONTROLLER_COUNT, new byte[0]);

        Packet packet = readPacket();

        if (packet.packetId != REQUEST_CONTROLLER_COUNT || packet.data.length < 4)
        {
            throw new IOException("Invalid controller count response");
        }

        return littleInt(packet.data, 0);
    }

    private DeviceInfo requestDeviceInfo(int deviceIndex) throws IOException
    {
        sendPacket(deviceIndex, REQUEST_CONTROLLER_DATA, new byte[0]);

        Packet packet = readPacket();

        if (packet.packetId != REQUEST_CONTROLLER_DATA)
        {
            throw new IOException("Invalid controller data response for device " + deviceIndex);
        }

        return parseDeviceInfo(deviceIndex, packet.data);
    }

    private DeviceInfo parseDeviceInfo(int deviceIndex, byte[] data) throws IOException
    {
        Reader reader = new Reader(data);

        reader.readInt();
        reader.readInt();

        String name = reader.readString();

        reader.readString();
        reader.readString();
        reader.readString();
        reader.readString();

        int modeCount = reader.readUShort();

        reader.readInt();

        for (int i = 0; i < modeCount; i++)
        {
            skipMode(reader);
        }

        int zoneCount = reader.readUShort();

        for (int i = 0; i < zoneCount; i++)
        {
            skipZone(reader);
        }

        int ledCount = reader.readUShort();

        for (int i = 0; i < ledCount; i++)
        {
            skipLed(reader);
        }

        int colorCount = reader.readUShort();

        reader.skip(colorCount * 4);

        return new DeviceInfo(deviceIndex, name, ledCount);
    }

    private void skipMode(Reader reader) throws IOException
    {
        reader.readString();

        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readInt();

        int modeColorCount = reader.readUShort();
        reader.skip(modeColorCount * 4);
    }

    private void skipZone(Reader reader) throws IOException
    {
        reader.readString();

        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readInt();

        int matrixLength = reader.readUShort();

        if (matrixLength > 0)
        {
            reader.skip(matrixLength);
        }
    }

    private void skipLed(Reader reader) throws IOException
    {
        reader.readString();
        reader.readInt();
    }

    private void setCustomMode(int deviceIndex) throws IOException
    {
        sendPacket(deviceIndex, SET_CUSTOM_MODE, new byte[0]);
    }

    private void updateDeviceLeds(int deviceIndex, int ledCount, Color color) throws IOException
    {
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        writeInt(body, 4 + 2 + (ledCount * 4));
        writeShort(body, ledCount);

        for (int i = 0; i < ledCount; i++)
        {
            body.write(color.getRed());
            body.write(color.getGreen());
            body.write(color.getBlue());
            body.write(0);
        }

        sendPacket(deviceIndex, UPDATE_LEDS, body.toByteArray());
        packetsSent++;
    }

    private void sendPacket(int deviceIndex, int packetId, byte[] data) throws IOException
    {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();

        packet.write('O');
        packet.write('R');
        packet.write('G');
        packet.write('B');

        writeInt(packet, deviceIndex);
        writeInt(packet, packetId);
        writeInt(packet, data.length);

        packet.write(data);

        output.write(packet.toByteArray());
        output.flush();
    }

    private Packet readPacket() throws IOException
    {
        byte[] header = new byte[16];
        input.readFully(header);

        if (
                header[0] != 'O'
                        || header[1] != 'R'
                        || header[2] != 'G'
                        || header[3] != 'B'
        )
        {
            throw new IOException("Invalid OpenRGB packet magic");
        }

        int deviceIndex = littleInt(header, 4);
        int packetId = littleInt(header, 8);
        int size = littleInt(header, 12);

        byte[] data = new byte[size];

        if (size > 0)
        {
            input.readFully(data);
        }

        return new Packet(deviceIndex, packetId, data);
    }

    private void writeInt(ByteArrayOutputStream out, int value)
    {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private void writeShort(ByteArrayOutputStream out, int value)
    {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private int littleInt(byte[] data, int offset)
    {
        return ByteBuffer
                .wrap(data, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
    }

    private static class DeviceInfo
    {
        private final int deviceIndex;
        private final String name;
        private final int ledCount;

        private DeviceInfo(int deviceIndex, String name, int ledCount)
        {
            this.deviceIndex = deviceIndex;
            this.name = name;
            this.ledCount = ledCount;
        }
    }

    private static class Packet
    {
        private final int deviceIndex;
        private final int packetId;
        private final byte[] data;

        private Packet(int deviceIndex, int packetId, byte[] data)
        {
            this.deviceIndex = deviceIndex;
            this.packetId = packetId;
            this.data = data;
        }
    }

    private static class Reader
    {
        private final byte[] data;
        private int offset = 0;

        private Reader(byte[] data)
        {
            this.data = data;
        }

        private int readInt() throws IOException
        {
            ensure(4);

            int value = ByteBuffer
                    .wrap(data, offset, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt();

            offset += 4;

            return value;
        }

        private int readUShort() throws IOException
        {
            ensure(2);

            int value =
                    (data[offset] & 0xFF)
                            | ((data[offset + 1] & 0xFF) << 8);

            offset += 2;

            return value;
        }

        private String readString() throws IOException
        {
            int length = readUShort();

            if (length <= 0)
            {
                return "";
            }

            ensure(length);

            int realLength = length;

            if (data[offset + length - 1] == 0)
            {
                realLength--;
            }

            String value = new String(data, offset, realLength, StandardCharsets.UTF_8);

            offset += length;

            return value;
        }

        private void skip(int count) throws IOException
        {
            ensure(count);
            offset += count;
        }

        private void ensure(int count) throws IOException
        {
            if (offset + count > data.length)
            {
                throw new IOException("Unexpected end of OpenRGB data block");
            }
        }
    }
}