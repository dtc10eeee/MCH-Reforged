package mcheli;

import com.google.common.io.ByteArrayDataInput;
import mcheli.wrapper.W_Network;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.io.DataOutputStream;
import java.io.IOException;

public class MCH_PacketNotifyServerSettings extends MCH_Packet {

    public boolean enableCamDistChange = true;
    public boolean enableEntityMarker = true;
    public boolean enablePVP = true;
    public double stingerLockRange = 120.0D;
    public boolean enableDebugBoundingBox = true;
    public boolean enableDebugGunnerTeam = false;
    public boolean enableDebugWaypointLabel = false;
    public boolean enableDebugMouseAim = false;
    public int mouseAimControlProfile = 0;
    public boolean enableMouseAimExtendedCircle = false;

    public static void send(EntityPlayerMP player) {
        MCH_PacketNotifyServerSettings s = new MCH_PacketNotifyServerSettings();
        MCH_Config var10001 = MCH_MOD.config;
        s.enableCamDistChange = !MCH_Config.DisableCameraDistChange.prmBool;
        var10001 = MCH_MOD.config;
        s.enableEntityMarker = MCH_Config.DisplayEntityMarker.prmBool;
        s.enablePVP = MinecraftServer.getServer().isPVPEnabled();
        var10001 = MCH_MOD.config;
        s.stingerLockRange = MCH_Config.StingerLockRange.prmDouble;
        var10001 = MCH_MOD.config;
        s.enableDebugBoundingBox = MCH_Config.EnableDebugBoundingBox.prmBool;
        s.enableDebugGunnerTeam = MCH_ServerSettings.enableDebugGunnerTeam;
        s.enableDebugWaypointLabel = MCH_ServerSettings.enableDebugWaypointLabel;
        s.enableDebugMouseAim = MCH_ServerSettings.enableDebugMouseAim;
        s.mouseAimControlProfile = MCH_ServerSettings.mouseAimControlProfile;
        s.enableMouseAimExtendedCircle = MCH_ServerSettings.enableMouseAimExtendedCircle;
        if (player != null) {
            W_Network.sendToPlayer(s, player);
        } else {
            W_Network.sendToAllPlayers(s);
        }

    }

    public static void sendAll() {
        send((EntityPlayerMP) null);
    }

    public int getMessageID() {
        return 268437568;
    }

    public void readData(ByteArrayDataInput data) {
        try {
            byte e = data.readByte();
            this.enableCamDistChange = this.getBit(e, 0);
            this.enableEntityMarker = this.getBit(e, 1);
            this.enablePVP = this.getBit(e, 2);
            this.stingerLockRange = (double) data.readFloat();
            this.enableDebugBoundingBox = this.getBit(e, 3);
            this.enableDebugGunnerTeam = this.getBit(e, 4);
            this.enableDebugWaypointLabel = this.getBit(e, 5);
            this.enableDebugMouseAim = this.getBit(e, 6);
            this.enableMouseAimExtendedCircle = this.getBit(e, 7);
            this.mouseAimControlProfile = data.readByte();
        } catch (Exception var3) {
            var3.printStackTrace();
        }

    }

    public void writeData(DataOutputStream dos) {
        try {
            byte e = 0;
            byte e1 = this.setBit(e, 0, this.enableCamDistChange);
            e1 = this.setBit(e1, 1, this.enableEntityMarker);
            e1 = this.setBit(e1, 2, this.enablePVP);
            e1 = this.setBit(e1, 3, this.enableDebugBoundingBox);
            e1 = this.setBit(e1, 4, this.enableDebugGunnerTeam);
            e1 = this.setBit(e1, 5, this.enableDebugWaypointLabel);
            e1 = this.setBit(e1, 6, this.enableDebugMouseAim);
            e1 = this.setBit(e1, 7, this.enableMouseAimExtendedCircle);
            dos.writeByte(e1);
            dos.writeFloat((float) this.stingerLockRange);
            dos.writeByte(this.mouseAimControlProfile);
        } catch (IOException var3) {
            var3.printStackTrace();
        }

    }

}
