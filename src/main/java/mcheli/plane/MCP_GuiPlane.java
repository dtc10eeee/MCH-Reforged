package mcheli.plane;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Config;
import mcheli.MCH_KeyName;
import mcheli.MCH_ServerSettings;
import mcheli.aircraft.MCH_AircraftCommonGui;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.gui.MCH_Gui;
import mcheli.render.MCH_RenderBVRLockBox;
import mcheli.vector.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class MCP_GuiPlane extends MCH_AircraftCommonGui {

    private boolean targetCircleSmoothingInit = false;
    private float targetCircleSmoothX = 0.0F;
    private float targetCircleSmoothY = 0.0F;
    private boolean noseCircleSmoothingInit = false;
    private float noseCircleSmoothX = 0.0F;
    private float noseCircleSmoothY = 0.0F;

    public MCP_GuiPlane(Minecraft minecraft) {
        super(minecraft);
    }

    public boolean isDrawGui(EntityPlayer player) {
        return MCH_EntityAircraft.getAircraft_RiddenOrControl(player) instanceof MCP_EntityPlane;
    }

    public void drawGui(EntityPlayer player, boolean isThirdPersonView) {
        MCH_EntityAircraft ac = MCH_EntityAircraft.getAircraft_RiddenOrControl(player);
        if (ac instanceof MCP_EntityPlane && !ac.isDestroyed()) {
            MCP_EntityPlane plane = (MCP_EntityPlane) ac;
            int seatID = ac.getSeatIdByEntity(player);
            GL11.glLineWidth((float) MCH_Gui.scaleFactor);
            if (plane.getCameraMode(player) == 1) {
                this.drawNightVisionNoise();
            }

            label50:
            {
                if (isThirdPersonView) {
                    if (!MCH_Config.DisplayHUDThirdPerson.prmBool) {
                        break label50;
                    }
                }

                if (seatID == 0 && plane.getIsGunnerMode(player)) {
                    this.drawHud(ac, player, 1);
                } else {
                    this.drawHud(ac, player, seatID);
                }
            }

            label51:
            {
                this.drawDebugtInfo(plane);
                if (isThirdPersonView) {
                    if (!MCH_Config.DisplayHUDThirdPerson.prmBool) {
                        break label51;
                    }
                }

                if (plane.getTVMissile() != null && (plane.getIsGunnerMode(player) || plane.isUAV())) {
                    this.drawTvMissileNoise(plane, plane.getTVMissile());
                } else {
                    this.drawKeybind(plane, player, seatID);
                }
            }

            this.drawMouseAimCircles(plane);

            this.drawHitBullet(plane, -14101432, seatID);
        }
    }

    private void drawMouseAimCircles(MCP_EntityPlane plane) {
        if (!MCH_Config.MouseAimPlaneDrawCircles.prmBool) {
            this.resetMouseAimCircleSmoothing();
            return;
        }
        if (!plane.isWTMouseAimActive()) {
            this.resetMouseAimCircleSmoothing();
            return;
        }
        if (super.mc.gameSettings.thirdPersonView != 1) {
            this.resetMouseAimCircleSmoothing();
            return;
        }
        if (MCH_EntityAircraft.getAircraft_RiddenOrControl(super.mc.thePlayer) != plane) {
            this.resetMouseAimCircleSmoothing();
            return;
        }
        if (plane.getSeatIdByEntity(super.mc.thePlayer) != 0) {
            this.resetMouseAimCircleSmoothing();
            return;
        }
        float targetAimX = MathHelper.clamp_float(plane.getWTAimTargetX(), -1.0F, 1.0F);
        float targetAimY = MathHelper.clamp_float(plane.getWTAimTargetY(), -1.0F, 1.0F);
        float targetX = (float) super.centerX + targetAimX * 105.0F;
        float targetY = (float) super.centerY - targetAimY * 105.0F;
        if (!this.targetCircleSmoothingInit) {
            this.targetCircleSmoothX = targetX;
            this.targetCircleSmoothY = targetY;
            this.targetCircleSmoothingInit = true;
        } else {
            float alphaTarget = MathHelper.clamp_float(0.34F + super.smoothCamPartialTicks * 0.30F, 0.34F, 0.78F);
            this.targetCircleSmoothX += (targetX - this.targetCircleSmoothX) * alphaTarget;
            this.targetCircleSmoothY += (targetY - this.targetCircleSmoothY) * alphaTarget;
        }
        this.drawCircleOutline((int) this.targetCircleSmoothX, (int) this.targetCircleSmoothY, 7.5D, 0xD000FF00);

        Vec3 fwd = mcheli.MCH_Lib.Rot2Vec3(plane.getRotYaw(), plane.getRotPitch());
        Vector3f nosePoint = new Vector3f(
            (float) (plane.posX + fwd.xCoord * 80.0D),
            (float) (plane.posY + fwd.yCoord * 80.0D),
            (float) (plane.posZ + fwd.zCoord * 80.0D)
        );
        double[] sc = MCH_RenderBVRLockBox.worldToScreen(nosePoint, super.smoothCamPartialTicks);
        if (sc[0] >= 0.0D && sc[1] >= 0.0D && sc[0] <= (double) super.width && sc[1] <= (double) super.height) {
            float targetNoseX = (float) sc[0];
            float targetNoseY = (float) sc[1];
            if (!this.noseCircleSmoothingInit) {
                this.noseCircleSmoothX = targetNoseX;
                this.noseCircleSmoothY = targetNoseY;
                this.noseCircleSmoothingInit = true;
            } else {
                float alpha = MathHelper.clamp_float(0.18F + super.smoothCamPartialTicks * 0.24F, 0.18F, 0.52F);
                this.noseCircleSmoothX += (targetNoseX - this.noseCircleSmoothX) * alpha;
                this.noseCircleSmoothY += (targetNoseY - this.noseCircleSmoothY) * alpha;
            }
            this.drawCircleOutline((int) this.noseCircleSmoothX, (int) this.noseCircleSmoothY, 4.5D, 0xE0FFFFFF);
        } else {
            this.noseCircleSmoothingInit = false;
        }

        this.drawMouseAimExtendedCirclePlaceholder(plane);
    }

    private void resetMouseAimCircleSmoothing() {
        this.targetCircleSmoothingInit = false;
        this.noseCircleSmoothingInit = false;
    }

    private void drawMouseAimExtendedCirclePlaceholder(MCP_EntityPlane plane) {
        if (!MCH_ServerSettings.enableMouseAimExtendedCircle) {
            return;
        }
        // Placeholder hook for optional future "sight/impact extension circle".
        // Kept intentionally no-op in this stage.
    }

    private void drawCircleOutline(int cx, int cy, double radius, int color) {
        GL11.glPushMatrix();
        GL11.glLineWidth(2.0F * MCH_Gui.scaleFactor);
        int segments = 24;
        double[] line = new double[(segments + 1) * 2];
        for (int i = 0; i <= segments; ++i) {
            double th = 6.283185307179586D * (double) i / (double) segments;
            line[i * 2] = (double) cx + Math.cos(th) * radius;
            line[i * 2 + 1] = (double) cy + Math.sin(th) * radius;
        }
        this.drawLine(line, color, 3);
        GL11.glPopMatrix();
    }

    public void drawKeybind(MCP_EntityPlane plane, EntityPlayer player, int seatID) {
        if (!MCH_Config.HideKeybind.prmBool) {
            MCP_PlaneInfo info = plane.getPlaneInfo();
            if (info != null) {
                int colorActive = -1342177281;
                int colorInactive = -1349546097;
                int RX = super.centerX + 120;
                int LX = super.centerX - 200;
                this.drawKeyBind(plane, info, player, seatID, RX, LX, colorActive, colorInactive);
                String msg;
                StringBuilder var12;
                if (seatID == 0 && info.isEnableGunnerMode) {
                    if (!Keyboard.isKeyDown(MCH_Config.KeyFreeLook.prmInt)) {
                        int c = plane.isHoveringMode() ? colorInactive : colorActive;
                        var12 = (new StringBuilder()).append(plane.getIsGunnerMode(player) ? "Normal" : "Gunner").append(" : ");
                        msg = var12.append(MCH_KeyName.getDescOrName(MCH_Config.KeySwitchMode.prmInt)).toString();
                        this.drawString(msg, RX, super.centerY - 70, c);
                    }
                }

                if (seatID > 0 && plane.canSwitchGunnerModeOtherSeat(player)) {
                    var12 = (new StringBuilder()).append(plane.getIsGunnerMode(player) ? "Normal" : "Camera").append(" : ");
                    msg = var12.append(MCH_KeyName.getDescOrName(MCH_Config.KeySwitchMode.prmInt)).toString();
                    this.drawString(msg, RX, super.centerY - 40, colorActive);
                }

                if (seatID == 0 && info.isEnableVtol) {
                    if (!Keyboard.isKeyDown(MCH_Config.KeyFreeLook.prmInt)) {
                        int stat = plane.getVtolMode();
                        if (stat != 1) {
                            var12 = (new StringBuilder()).append(stat == 0 ? "VTOL : " : "Normal : ");
                            msg = var12.append(MCH_KeyName.getDescOrName(MCH_Config.KeyExtra.prmInt)).toString();
                            this.drawString(msg, RX, super.centerY - 60, colorActive);
                        }
                    }
                }

                if (plane.canEjectSeat(player)) {
                    var12 = (new StringBuilder()).append("Eject seat: ");
                    msg = var12.append(MCH_KeyName.getDescOrName(MCH_Config.KeySwitchHovering.prmInt)).toString();
                    this.drawString(msg, RX, super.centerY - 30, colorActive);
                }

                if (plane.getIsGunnerMode(player) && info.cameraZoom > 1) {
                    var12 = (new StringBuilder()).append("Zoom : ");
                    msg = var12.append(MCH_KeyName.getDescOrName(MCH_Config.KeyZoom.prmInt)).toString();
                    this.drawString(msg, LX, super.centerY - 80, colorActive);
                } else if (seatID == 0) {
                    if (!plane.canFoldWing() && !plane.canUnfoldWing()) {
                        if (plane.canFoldHatch() || plane.canUnfoldHatch()) {
                            var12 = (new StringBuilder()).append("OpenHatch : ");
                            msg = var12.append(MCH_KeyName.getDescOrName(MCH_Config.KeyZoom.prmInt)).toString();
                            this.drawString(msg, LX, super.centerY - 80, colorActive);
                        }
                    } else {
                        var12 = (new StringBuilder()).append("FoldWing : ");
                        msg = var12.append(MCH_KeyName.getDescOrName(MCH_Config.KeyZoom.prmInt)).toString();
                        this.drawString(msg, LX, super.centerY - 80, colorActive);
                    }
                }

            }
        }
    }
}
