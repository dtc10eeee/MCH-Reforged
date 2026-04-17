package mcheli.plane;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Config;
import mcheli.MCH_Lib;
import mcheli.MCH_Math;
import mcheli.MCH_MouseAimDebug;
import mcheli.MCH_MOD;
import mcheli.MCH_ServerSettings;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_PacketStatusRequest;
import mcheli.aircraft.MCH_Parts;
import mcheli.particles.MCH_ParticleParam;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.weapon.MCH_WeaponGuidanceSystem;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCP_EntityPlane extends MCH_EntityAircraft {

    public float soundVolume;
    public MCH_Parts partNozzle;
    public MCH_Parts partWing;
    public float rotationRotor;
    public float prevRotationRotor;
    public float addkeyRotValue;
    private MCP_PlaneInfo planeInfo = null;

    public float prevRotationExhaustFlameX;
    public float prevRotationExhaustFlameY;
    public float prevRotationExhaustFlameZ;
    public float rotationExhaustFlameX;
    public float rotationExhaustFlameY;
    public float rotationExhaustFlameZ;
    @SideOnly(Side.CLIENT)
    private float wtAimNormX;
    @SideOnly(Side.CLIENT)
    private float wtAimNormY;
    @SideOnly(Side.CLIENT)
    private float wtAimTargetX;
    @SideOnly(Side.CLIENT)
    private float wtAimTargetY;
    @SideOnly(Side.CLIENT)
    private float wtAimVelX;
    @SideOnly(Side.CLIENT)
    private float wtAimVelY;
    @SideOnly(Side.CLIENT)
    private float wtYawCmd;
    @SideOnly(Side.CLIENT)
    private float wtPitchCmd;
    @SideOnly(Side.CLIENT)
    private float wtRollCmd;
    @SideOnly(Side.CLIENT)
    private float wtCamYawOffset;
    @SideOnly(Side.CLIENT)
    private float wtCamPitchOffset;
    private float wtQuatW = 1.0F;
    private float wtQuatX = 0.0F;
    private float wtQuatY = 0.0F;
    private float wtQuatZ = 0.0F;
    private boolean wtQuatInited = false;
    private float wtQuatPrevYaw = 0.0F;
    private float wtQuatPrevPitch = 0.0F;
    private float wtQuatPrevRoll = 0.0F;
    @SideOnly(Side.CLIENT)
    private int wtLastControlTick = -1;

    @SideOnly(Side.CLIENT)
    public ExhaustAnimState exhaustAnimState;


    public MCP_EntityPlane(World world) {
        super(world);
        super.currentSpeed = 0.07D;
        super.preventEntitySpawning = true;
        this.setSize(2.0F, 0.7F);
        super.yOffset = super.height / 2.0F;
        super.motionX = 0.0D;
        super.motionY = 0.0D;
        super.motionZ = 0.0D;
        super.weapons = this.createWeapon(0);
        this.soundVolume = 0.0F;
        this.partNozzle = null;
        this.partWing = null;
        super.stepHeight = 0.6F;
        this.rotationRotor = 0.0F;
        this.prevRotationRotor = 0.0F;
    }

    public String getKindName() {
        return "planes";
    }

    public String getEntityType() {
        return "Plane";
    }

    public MCP_PlaneInfo getPlaneInfo() {
        return this.planeInfo;
    }

    public void changeType(String type) {
        if (!type.isEmpty()) {
            this.planeInfo = MCP_PlaneInfoManager.get(type);
        }

        if (this.planeInfo == null) {
            MCH_Lib.Log(this, "##### MCP_EntityPlane changePlaneType() Plane info null %d, %s, %s", new Object[]{Integer.valueOf(W_Entity.getEntityId(this)), type, this.getEntityName()});
            this.setDead();
        } else {
            this.setAcInfo(this.planeInfo);
            this.newSeats(this.getAcInfo().getNumSeatAndRack());
            this.partNozzle = this.createNozzle(this.planeInfo);
            this.partWing = this.createWing(this.planeInfo);
            super.weapons = this.createWeapon(1 + this.getSeatNum());
            this.initPartRotation(this.getRotYaw(), this.getRotPitch());
        }

    }

    public Item getItem() {
        return this.getPlaneInfo() != null ? this.getPlaneInfo().item : null;
    }

    public boolean canMountWithNearEmptyMinecart() {
        return MCH_Config.MountMinecartPlane.prmBool;
    }

    protected void entityInit() {
        super.entityInit();
    }

    protected void writeEntityToNBT(NBTTagCompound par1NBTTagCompound) {
        super.writeEntityToNBT(par1NBTTagCompound);
    }

    protected void readEntityFromNBT(NBTTagCompound par1NBTTagCompound) {
        super.readEntityFromNBT(par1NBTTagCompound);
        if (this.planeInfo == null) {
            this.planeInfo = MCP_PlaneInfoManager.get(this.getTypeName());
            if (this.planeInfo == null) {
                MCH_Lib.Log((Entity) this, "##### MCP_EntityPlane readEntityFromNBT() Plane info null %d, %s", new Object[]{Integer.valueOf(W_Entity.getEntityId(this)), this.getEntityName()});
                this.setDead();
            } else {
                this.setAcInfo(this.planeInfo);
            }
        }

    }

    public void setDead() {
        super.setDead();
    }

    public int getNumEjectionSeat() {
        if (this.getAcInfo() != null && this.getAcInfo().isEnableEjectionSeat) {
            int n = this.getSeatNum() + 1;
            return n <= 2 ? n : 0;
        } else {
            return 0;
        }
    }

    public void onInteractFirst(EntityPlayer player) {
        this.addkeyRotValue = 0.0F;
    }

    public boolean canSwitchGunnerMode() {
        if (!super.canSwitchGunnerMode()) {
            return false;
        } else {
            float roll = MathHelper.abs(MathHelper.wrapAngleTo180_float(this.getRotRoll()));
            float pitch = MathHelper.abs(MathHelper.wrapAngleTo180_float(this.getRotPitch()));
            return roll <= 40.0F && pitch <= 40.0F && this.getCurrentThrottle() > 0.6000000238418579D && MCH_Lib.getBlockIdY(this, 3, -5) == 0;
        }
    }

    public void onUpdateAircraft() {
        if (this.planeInfo == null) {
            this.changeType(this.getTypeName());
            super.prevPosX = super.posX;
            super.prevPosY = super.posY;
            super.prevPosZ = super.posZ;
        } else {
            if (!super.isRequestedSyncStatus) {
                super.isRequestedSyncStatus = true;
                if (super.worldObj.isRemote) {
                    MCH_PacketStatusRequest.requestStatus(this);
                }
            }

            if (super.lastRiddenByEntity == null && this.getRiddenByEntity() != null) {
                this.initCurrentWeapon(this.getRiddenByEntity());
            }

            this.updateWeapons();
            this.onUpdate_Seats();
            this.onUpdate_Control();
            this.prevRotationRotor = this.rotationRotor;
            this.rotationRotor = (float) ((double) this.rotationRotor + this.getCurrentThrottle() * (double) this.getAcInfo().rotorSpeed);
            if (this.rotationRotor > 360.0F) {
                this.rotationRotor -= 360.0F;
                this.prevRotationRotor -= 360.0F;
            }

            if (this.rotationRotor < 0.0F) {
                this.rotationRotor += 360.0F;
                this.prevRotationRotor += 360.0F;
            }

            if (super.onGround && this.getVtolMode() == 0 && this.planeInfo.isDefaultVtol) {
                this.switchVtolMode(true);
            }

            super.prevPosX = super.posX;
            super.prevPosY = super.posY;
            super.prevPosZ = super.posZ;
            if (!this.isDestroyed() && this.isHovering() && MathHelper.abs(this.getRotPitch()) < 70.0F) {
                this.setRotPitch(this.getRotPitch() * 0.95F, "isHovering()");
            }

            if (this.isDestroyed() && this.getCurrentThrottle() > 0.0D) {
                if (MCH_Lib.getBlockIdY(this, 3, -2) > 0) {
                    this.setCurrentThrottle(this.getCurrentThrottle() * 0.8D);
                }

                if (this.isExploded()) {
                    this.setCurrentThrottle(this.getCurrentThrottle() * 0.98D);
                }
            }

            this.updateCameraViewers();
            if (super.worldObj.isRemote) {
                this.onUpdate_Client();
            } else {
                this.onUpdate_Server();
            }

        }
    }

    public boolean canUpdateYaw(Entity player) {
        return super.canUpdateYaw(player) && !this.isHovering();
    }

    public boolean canUpdatePitch(Entity player) {
        return super.canUpdatePitch(player) && !this.isHovering();
    }

    public boolean canUpdateRoll(Entity player) {
        return super.canUpdateRoll(player) && !this.isHovering();
    }

    public float getYawFactor() {
        float yaw = this.getVtolMode() > 0 ? this.getPlaneInfo().vtolYaw : super.getYawFactor();
        if (this.isWTMouseAimActive()) {
            return yaw * 1.9F;
        }
        return yaw * 0.8F;
    }

    public float getPitchFactor() {
        float pitch = this.getVtolMode() > 0 ? this.getPlaneInfo().vtolPitch : super.getPitchFactor();
        if (this.isWTMouseAimActive()) {
            return pitch * 1.8F;
        }
        return pitch * 0.8F;
    }

    public float getRollFactor() {
        float roll = this.getVtolMode() > 0 ? this.getPlaneInfo().vtolYaw : super.getRollFactor();
        if (this.isWTMouseAimActive()) {
            return roll * 2.4F;
        }
        return roll * 0.8F;
    }

    public boolean isOverridePlayerPitch() {
        return super.isOverridePlayerPitch() && !this.isHovering();
    }

    public boolean isOverridePlayerYaw() {
        return super.isOverridePlayerYaw() && !this.isHovering();
    }

    public float getControlRotYaw(float mouseX, float mouseY, float tick) {
        if (this.isWTMouseAimActive()) {
            this.updateWTMouseAimControl(mouseX, mouseY, tick);
            return this.wtYawCmd;
        }
        this.resetWTMouseAimState();
        if (MCH_Config.MouseControlFlightSimMode.prmBool) {
            this.rotationByKey(tick);
            return this.addkeyRotValue * 20.0F;
        } else {
            return mouseX;
        }
    }

    public float getControlRotPitch(float mouseX, float mouseY, float tick) {
        if (this.isWTMouseAimActive()) {
            this.updateWTMouseAimControl(mouseX, mouseY, tick);
            return this.wtPitchCmd;
        }
        this.resetWTMouseAimState();
        return mouseY;
    }

    public float getControlRotRoll(float mouseX, float mouseY, float tick) {
        if (this.isWTMouseAimActive()) {
            this.updateWTMouseAimControl(mouseX, mouseY, tick);
            return this.wtRollCmd;
        }
        this.resetWTMouseAimState();
        return MCH_Config.MouseControlFlightSimMode.prmBool ? mouseX * 2.0F : (this.getVtolMode() == 0 ? mouseX * 0.5F : mouseX);
    }

    public boolean isWTMouseAimActive() {
        if (!super.worldObj.isRemote) {
            return false;
        }
        if (!MCH_Config.MouseAimPlaneThirdPersonEnabled.prmBool) {
            return false;
        }
        if (!(this.getRiddenByEntity() instanceof EntityPlayer) || !this.isPilot(this.getRiddenByEntity())) {
            return false;
        }
        return MCH_MOD.proxy.getThirdPersonViewType() == 1;
    }

    @SideOnly(Side.CLIENT)
    public float getWTAimNormX() {
        return this.wtAimNormX;
    }

    @SideOnly(Side.CLIENT)
    public float getWTAimNormY() {
        return this.wtAimNormY;
    }

    @SideOnly(Side.CLIENT)
    public float getWTAimTargetX() {
        return this.wtAimTargetX;
    }

    @SideOnly(Side.CLIENT)
    public float getWTAimTargetY() {
        return this.wtAimTargetY;
    }

    @SideOnly(Side.CLIENT)
    public boolean isWTPseudoFreeLookCameraActive() {
        return this.isWTMouseAimActive() && !this.isFreeLookMode() && MCH_Config.MouseAimPlanePseudoFreeLookEnabled.prmBool;
    }

    @SideOnly(Side.CLIENT)
    public float getWTCameraYawOffset() {
        return this.wtCamYawOffset;
    }

    @SideOnly(Side.CLIENT)
    public float getWTCameraPitchOffset() {
        return this.wtCamPitchOffset;
    }

    public boolean isWTQuaternionAnglesActive() {
        if (super.worldObj.isRemote) {
            return this.isWTMouseAimActive();
        }
        if (!(this.getRiddenByEntity() instanceof EntityPlayer)) {
            return false;
        }
        return this.isPilot(this.getRiddenByEntity()) && MCH_Config.MouseAimPlaneThirdPersonEnabled.prmBool;
    }

    public MCH_Math.FVector3D computeWTQuaternionEuler(float yaw, float pitch, float roll) {
        if (!this.wtQuatInited) {
            MCH_Math.FQuat q0 = MCH_Math.EulerToQuat(this.getRotYaw(), this.getRotPitch(), this.getRotRoll());
            this.wtQuatW = q0.w;
            this.wtQuatX = q0.x;
            this.wtQuatY = q0.y;
            this.wtQuatZ = q0.z;
            this.wtQuatInited = true;
            this.wtQuatPrevYaw = this.getRotYaw();
            this.wtQuatPrevPitch = this.getRotPitch();
            this.wtQuatPrevRoll = this.getRotRoll();
        }

        MCH_Math.FQuat qCur = MCH_Math.newQuat();
        qCur.w = this.wtQuatW;
        qCur.x = this.wtQuatX;
        qCur.y = this.wtQuatY;
        qCur.z = this.wtQuatZ;
        MCH_Math.FQuat dq = MCH_Math.EulerToQuat(yaw, pitch, roll);
        MCH_Math.FQuat qNext = MCH_Math.QuatMult(dq, qCur);
        MCH_Math.QuatNormalize(qNext);

        MCH_Math.FVector3D raw = MCH_Math.QuatToEuler(qNext);
        float c1Pitch = this.unwrapAngleNear(raw.x, this.wtQuatPrevPitch);
        float c1Yaw = this.unwrapAngleNear(raw.y, this.wtQuatPrevYaw);
        float c1Roll = this.unwrapAngleNear(raw.z, this.wtQuatPrevRoll);

        float altPitch = raw.x >= 0.0F ? 180.0F - raw.x : -180.0F - raw.x;
        float altYaw = MathHelper.wrapAngleTo180_float(raw.y + 180.0F);
        float altRoll = MathHelper.wrapAngleTo180_float(raw.z + 180.0F);
        float c2Pitch = this.unwrapAngleNear(altPitch, this.wtQuatPrevPitch);
        float c2Yaw = this.unwrapAngleNear(altYaw, this.wtQuatPrevYaw);
        float c2Roll = this.unwrapAngleNear(altRoll, this.wtQuatPrevRoll);

        float predPitch = this.wtQuatPrevPitch + pitch;
        boolean chooseAlt = MathHelper.abs(c2Pitch - predPitch) < MathHelper.abs(c1Pitch - predPitch);
        // Near Euler singularity, prefer the branch that keeps moving pitch outward
        // instead of converting most motion into yaw/roll spin.
        float prevPitchAbs = MathHelper.abs(this.wtQuatPrevPitch);
        float pitchIntentAbs = MathHelper.abs(pitch);
        if (prevPitchAbs > 72.0F && pitchIntentAbs > 0.06F) {
            float outwardSign = this.wtQuatPrevPitch >= 0.0F ? 1.0F : -1.0F;
            boolean pushingOutward = pitch * outwardSign > 0.0F;
            if (pushingOutward) {
                float c1Outward = c1Pitch * outwardSign;
                float c2Outward = c2Pitch * outwardSign;
                if (c2Outward > c1Outward + 0.6F) {
                    chooseAlt = true;
                }
            }
        }
        float outPitch = chooseAlt ? c2Pitch : c1Pitch;
        float outYaw = chooseAlt ? c2Yaw : c1Yaw;
        float outRoll = chooseAlt ? c2Roll : c1Roll;

        this.wtQuatW = qNext.w;
        this.wtQuatX = qNext.x;
        this.wtQuatY = qNext.y;
        this.wtQuatZ = qNext.z;
        this.wtQuatPrevYaw = outYaw;
        this.wtQuatPrevPitch = outPitch;
        this.wtQuatPrevRoll = outRoll;

        return MCH_Math.newVec3D(outPitch, outYaw, outRoll);
    }

    private float unwrapAngleNear(float angle, float reference) {
        return reference + MathHelper.wrapAngleTo180_float(angle - reference);
    }

    public boolean isWTQuatInited() {
        return this.wtQuatInited;
    }

    public float getWTQuatW() {
        return this.wtQuatW;
    }

    public float getWTQuatX() {
        return this.wtQuatX;
    }

    public float getWTQuatY() {
        return this.wtQuatY;
    }

    public float getWTQuatZ() {
        return this.wtQuatZ;
    }

    @SideOnly(Side.CLIENT)
    private void updateWTMouseAimControl(float mouseX, float mouseY, float partialTicks) {
        if (super.ticksExisted == this.wtLastControlTick) {
            return;
        }
        this.wtLastControlTick = super.ticksExisted;
        boolean aggressiveProfile = MCH_ServerSettings.mouseAimControlProfile == 1;
        float baseSpeed = 0.07F;
        float speedNow = Math.max((float) super.currentSpeed, (float) Math.sqrt(super.motionX * super.motionX + super.motionZ * super.motionZ));
        float speedMax = Math.max(this.getMaxSpeed(), baseSpeed + 0.01F);
        float speedNorm = MathHelper.clamp_float((speedNow - baseSpeed) / (speedMax - baseSpeed), 0.0F, 1.0F);
        float highSpeedInputDamp = MathHelper.clamp_float((aggressiveProfile ? 0.86F : 0.68F) - speedNorm * (aggressiveProfile ? 0.16F : 0.10F), aggressiveProfile ? 0.54F : 0.46F, aggressiveProfile ? 0.86F : 0.68F);
        float lowSpeedPitchProtect = MathHelper.clamp_float(0.42F + speedNorm * 0.68F, 0.42F, 1.0F);
        float lowSpeedRollProtect = MathHelper.clamp_float(0.72F + speedNorm * 0.28F, 0.72F, 1.0F);
        float lowSpeedYawProtect = MathHelper.clamp_float(0.64F + speedNorm * 0.36F, 0.64F, 1.0F);
        boolean quatActive = this.isWTQuaternionAnglesActive();
        if (quatActive) {
            lowSpeedPitchProtect = Math.max(lowSpeedPitchProtect, aggressiveProfile ? 0.75F : 0.68F);
        }
        // Fast start: larger input gain so the aim marker responds immediately.
        float inputDiv = aggressiveProfile ? 44.0F : 64.0F;
        float inputClamp = aggressiveProfile ? 0.10F : 0.06F;
        float inputX = MathHelper.clamp_float(mouseX / inputDiv, -inputClamp, inputClamp) * highSpeedInputDamp;
        float inputY = MathHelper.clamp_float(mouseY / inputDiv, -inputClamp, inputClamp) * highSpeedInputDamp;
        this.wtAimTargetX = MathHelper.clamp_float(this.wtAimTargetX + inputX, -1.0F, 1.0F);
        this.wtAimTargetY = MathHelper.clamp_float(this.wtAimTargetY + inputY, -1.0F, 1.0F);

        // Adaptive recenter with a critically-damped spring: faster and cleaner return-to-center.
        float inputActivity = MathHelper.clamp_float((MathHelper.abs(mouseX) + MathHelper.abs(mouseY)) / 24.0F, 0.0F, 1.0F);
        float dt = MathHelper.clamp_float(0.05F * partialTicks, 0.02F, 0.08F);
        float springNoInput = aggressiveProfile ? 22.0F : 28.0F;
        float springWithInput = aggressiveProfile ? 8.0F : 6.0F;
        float springK = springNoInput + (springWithInput - springNoInput) * inputActivity;
        float dampingRatio = aggressiveProfile ? 0.95F : 1.02F;
        float springC = 2.0F * MathHelper.sqrt_float(springK) * dampingRatio;

        this.wtAimVelX += (-springK * this.wtAimTargetX - springC * this.wtAimVelX) * dt;
        this.wtAimVelY += (-springK * this.wtAimTargetY - springC * this.wtAimVelY) * dt;
        this.wtAimTargetX += this.wtAimVelX * dt;
        this.wtAimTargetY += this.wtAimVelY * dt;
        this.wtAimTargetX = MathHelper.clamp_float(this.wtAimTargetX, -1.0F, 1.0F);
        this.wtAimTargetY = MathHelper.clamp_float(this.wtAimTargetY, -1.0F, 1.0F);
        if (MathHelper.abs(this.wtAimTargetX) >= 0.999F && this.wtAimVelX * this.wtAimTargetX > 0.0F) {
            this.wtAimVelX = 0.0F;
        }
        if (MathHelper.abs(this.wtAimTargetY) >= 0.999F && this.wtAimVelY * this.wtAimTargetY > 0.0F) {
            this.wtAimVelY = 0.0F;
        }
        if (inputActivity < 0.05F && MathHelper.abs(this.wtAimTargetX) < 0.010F && MathHelper.abs(this.wtAimVelX) < 0.025F) {
            this.wtAimTargetX = 0.0F;
            this.wtAimVelX = 0.0F;
        }
        if (inputActivity < 0.05F && MathHelper.abs(this.wtAimTargetY) < 0.010F && MathHelper.abs(this.wtAimVelY) < 0.025F) {
            this.wtAimTargetY = 0.0F;
            this.wtAimVelY = 0.0F;
        }
        float recenter = springK * dt;

        float alphaBase = aggressiveProfile ? 0.32F : 0.24F;
        float alphaScale = aggressiveProfile ? 0.21F : 0.15F;
        float alpha = MathHelper.clamp_float(alphaBase + partialTicks * alphaScale, alphaBase, aggressiveProfile ? 0.66F : 0.52F);
        this.wtAimNormX += (this.wtAimTargetX - this.wtAimNormX) * alpha;
        this.wtAimNormY += (this.wtAimTargetY - this.wtAimNormY) * alpha;
        this.updateWTPseudoFreeLookCameraOffset(aggressiveProfile, alpha);

        float absX = MathHelper.abs(this.wtAimNormX);
        float absY = MathHelper.abs(this.wtAimNormY);
        float errMag = MathHelper.clamp_float(MathHelper.sqrt_float(this.wtAimNormX * this.wtAimNormX + this.wtAimNormY * this.wtAimNormY), 0.0F, 1.0F);
        float rollTargetScale = (aggressiveProfile ? 72.0F : 62.0F) + (aggressiveProfile ? 18.0F : 14.0F) * absX;
        float rollLimitBySpeed = MathHelper.clamp_float(54.0F + speedNorm * 38.0F, 54.0F, 92.0F);
        float desiredRollByX = MathHelper.clamp_float(this.wtAimNormX * rollTargetScale, -rollLimitBySpeed, rollLimitBySpeed);
        float alignRoll = (float) Math.toDegrees(Math.atan2(this.wtAimNormX, absY + 0.15F));
        float desiredRollByDirection = MathHelper.clamp_float(alignRoll * (0.95F + errMag * 0.30F), -rollLimitBySpeed, rollLimitBySpeed);
        // Only enable geometry alignment when target is far from screen center.
        float alignDeadZone = aggressiveProfile ? 0.18F : 0.24F;
        float alignFullZone = aggressiveProfile ? 0.62F : 0.68F;
        float alignWeight = MathHelper.clamp_float((errMag - alignDeadZone) / (alignFullZone - alignDeadZone), 0.0F, 1.0F);
        float desiredRoll = desiredRollByX + (desiredRollByDirection - desiredRollByX) * alignWeight;
        float bankRatio = MathHelper.clamp_float(MathHelper.abs(this.getRotRoll()) / Math.max(rollLimitBySpeed, 1.0F), 0.0F, 1.0F);
        float yawWeight = MathHelper.clamp_float(0.86F - bankRatio * 0.90F, 0.06F, 0.86F);
        MCH_AircraftInfo acInfo = this.getAcInfo();
        float mobilityRoll = acInfo != null ? acInfo.mobilityRoll : 2.39F;
        float mobilityRollGain = MathHelper.clamp_float(mobilityRoll / 2.39F, 0.75F, 1.35F);
        float lateralDemand = MathHelper.abs(this.wtAimNormX);
        float adLikeWeight = MathHelper.clamp_float((lateralDemand - 0.08F) / 0.36F, 0.0F, 1.0F);
        float yawCmd = this.wtAimNormX * (aggressiveProfile ? 22.0F : 15.0F) * yawWeight * lowSpeedYawProtect;
        float pitchGain = (aggressiveProfile ? 44.0F : 30.0F) + absX * bankRatio * (aggressiveProfile ? 10.0F : 6.0F);
        float pitchCmd = this.wtAimNormY * pitchGain * lowSpeedPitchProtect;
        float rollErr = MathHelper.wrapAngleTo180_float(desiredRoll - this.getRotRoll());
        float adLikeRollCmd = this.wtAimNormX * (aggressiveProfile ? 52.0F : 40.0F) * mobilityRollGain * adLikeWeight;
        float rollCmd = (rollErr * (aggressiveProfile ? 1.92F : 1.58F) + this.wtAimNormX * (aggressiveProfile ? 34.0F : 26.0F) + adLikeRollCmd) * lowSpeedRollProtect;
        float manualRollInput = 0.0F;
        if (super.moveLeft && !super.moveRight) {
            manualRollInput = -1.0F;
        } else if (super.moveRight && !super.moveLeft) {
            manualRollInput = 1.0F;
        }
        if (manualRollInput != 0.0F) {
            float manualRollCmd = manualRollInput * (aggressiveProfile ? 86.0F : 72.0F) * mobilityRollGain;
            rollCmd = manualRollCmd;
        }
        float rollFirstWeight = MathHelper.clamp_float((MathHelper.abs(rollErr) - (aggressiveProfile ? 14.0F : 18.0F)) / (aggressiveProfile ? 26.0F : 34.0F), 0.0F, 1.0F) * adLikeWeight;
        float absPitchNow = MathHelper.abs(MathHelper.wrapAngleTo180_float(this.getRotPitch()));
        float highPitchStart = aggressiveProfile ? 66.0F : 72.0F;
        float highPitchEnd = 88.0F;
        float highPitchWeight = MathHelper.clamp_float((absPitchNow - highPitchStart) / (highPitchEnd - highPitchStart), 0.0F, 1.0F);
        float pullUpWeight = quatActive ? MathHelper.clamp_float((MathHelper.abs(this.wtAimNormY) - 0.45F) / 0.45F, 0.0F, 1.0F) : 0.0F;
        float pitchPriorityWeight = Math.max(highPitchWeight, pullUpWeight);
        float rollFirstSuppression = 1.0F - pitchPriorityWeight * 0.80F;
        rollFirstWeight *= rollFirstSuppression;
        float rollFirstPitchScale = 1.0F - rollFirstWeight * 0.65F;
        if (rollFirstPitchScale < 0.35F) {
            rollFirstPitchScale = 0.35F;
        }
        if (quatActive && MathHelper.abs(this.wtAimNormY) > 0.55F) {
            rollFirstPitchScale = Math.max(rollFirstPitchScale, 0.88F);
        }
        float pitchBoostScale = 1.0F + pitchPriorityWeight * (aggressiveProfile ? 0.55F : 0.45F);
        pitchCmd *= rollFirstPitchScale;
        pitchCmd *= pitchBoostScale;
        float rollPitchCutScale = 1.0F - pitchPriorityWeight * (aggressiveProfile ? 0.58F : 0.50F);
        if (rollPitchCutScale < 0.35F) {
            rollPitchCutScale = 0.35F;
        }
        rollCmd *= rollPitchCutScale;
        yawCmd *= (1.0F - rollFirstWeight * 0.50F);
        yawCmd *= (1.0F - pitchPriorityWeight * 0.25F);

        this.wtYawCmd = MathHelper.clamp_float(yawCmd, aggressiveProfile ? -40.0F : -28.0F, aggressiveProfile ? 40.0F : 28.0F);
        float pitchLimit = quatActive ? (aggressiveProfile ? 126.0F : 102.0F) : (aggressiveProfile ? 74.0F : 56.0F);
        if (quatActive) {
            pitchLimit += pitchPriorityWeight * (aggressiveProfile ? 24.0F : 20.0F);
        }
        this.wtPitchCmd = MathHelper.clamp_float(pitchCmd, -pitchLimit, pitchLimit);
        this.wtRollCmd = MathHelper.clamp_float(rollCmd, aggressiveProfile ? -118.0F : -92.0F, aggressiveProfile ? 118.0F : 92.0F);

        if (super.ticksExisted % 8 == 0) {
            float mobilityYaw = acInfo != null ? acInfo.mobilityYaw : 0.0F;
            float mobilityPitch = acInfo != null ? acInfo.mobilityPitch : 0.0F;
            float motionFactor = acInfo != null ? acInfo.motionFactor : 0.0F;
            MCH_MouseAimDebug.trace(
                super.worldObj,
                this.getRiddenByEntity(),
                "plane=%s active=%s profile=%s quat=(active=%s,inited=%s,w=%.4f,x=%.4f,y=%.4f,z=%.4f) thirdPerson=%d mouse=(%.2f,%.2f) target=(%.3f,%.3f) smooth=(%.3f,%.3f) ctrl=(desiredRoll=%.2f,rollX=%.2f,rollDir=%.2f,alignW=%.3f,adW=%.3f,rollFirst=%.3f,rollLimit=%.2f,bank=%.3f,yawW=%.3f,pPri=%.3f,pHi=%.3f,pPull=%.3f,pBoost=%.3f,rCut=%.3f,pLim=%.2f,recenter=%.4f,alpha=%.3f,activity=%.3f,speedNorm=%.3f,inputDamp=%.3f,lp=%.3f,lr=%.3f,ly=%.3f) cmd=(yaw=%.2f,pitch=%.2f,roll=%.2f) rot=(yaw=%.2f,pitch=%.2f,roll=%.2f) factor=(yaw=%.2f,pitch=%.2f,roll=%.2f) mobility=(yaw=%.2f,pitch=%.2f,roll=%.2f) motionFactor=%.4f throttle=%.3f speed=%.3f",
                this.getEntityName(),
                Boolean.valueOf(this.isWTMouseAimActive()),
                aggressiveProfile ? "aggressive" : "normal",
                Boolean.valueOf(quatActive),
                Boolean.valueOf(this.wtQuatInited),
                Float.valueOf(this.wtQuatW),
                Float.valueOf(this.wtQuatX),
                Float.valueOf(this.wtQuatY),
                Float.valueOf(this.wtQuatZ),
                Integer.valueOf(MCH_MOD.proxy.getThirdPersonViewType()),
                Float.valueOf(mouseX),
                Float.valueOf(mouseY),
                Float.valueOf(this.wtAimTargetX),
                Float.valueOf(this.wtAimTargetY),
                Float.valueOf(this.wtAimNormX),
                Float.valueOf(this.wtAimNormY),
                Float.valueOf(desiredRoll),
                Float.valueOf(desiredRollByX),
                Float.valueOf(desiredRollByDirection),
                Float.valueOf(alignWeight),
                Float.valueOf(adLikeWeight),
                Float.valueOf(rollFirstWeight),
                Float.valueOf(rollLimitBySpeed),
                Float.valueOf(bankRatio),
                Float.valueOf(yawWeight),
                Float.valueOf(pitchPriorityWeight),
                Float.valueOf(highPitchWeight),
                Float.valueOf(pullUpWeight),
                Float.valueOf(pitchBoostScale),
                Float.valueOf(rollPitchCutScale),
                Float.valueOf(pitchLimit),
                Float.valueOf(recenter),
                Float.valueOf(alpha),
                Float.valueOf(inputActivity),
                Float.valueOf(speedNorm),
                Float.valueOf(highSpeedInputDamp),
                Float.valueOf(lowSpeedPitchProtect),
                Float.valueOf(lowSpeedRollProtect),
                Float.valueOf(lowSpeedYawProtect),
                Float.valueOf(this.wtYawCmd),
                Float.valueOf(this.wtPitchCmd),
                Float.valueOf(this.wtRollCmd),
                Float.valueOf(this.getRotYaw()),
                Float.valueOf(this.getRotPitch()),
                Float.valueOf(this.getRotRoll()),
                Float.valueOf(this.getYawFactor()),
                Float.valueOf(this.getPitchFactor()),
                Float.valueOf(this.getRollFactor()),
                Float.valueOf(mobilityYaw),
                Float.valueOf(mobilityPitch),
                Float.valueOf(mobilityRoll),
                Float.valueOf(motionFactor),
                Float.valueOf((float) this.getCurrentThrottle()),
                Float.valueOf((float) super.currentSpeed)
            );
        }
    }

    @SideOnly(Side.CLIENT)
    private void resetWTMouseAimState() {
        this.wtLastControlTick = -1;
        this.wtAimNormX = 0.0F;
        this.wtAimNormY = 0.0F;
        this.wtAimTargetX = 0.0F;
        this.wtAimTargetY = 0.0F;
        this.wtAimVelX = 0.0F;
        this.wtAimVelY = 0.0F;
        this.wtYawCmd = 0.0F;
        this.wtPitchCmd = 0.0F;
        this.wtRollCmd = 0.0F;
        this.wtCamYawOffset = 0.0F;
        this.wtCamPitchOffset = 0.0F;
        this.wtQuatW = 1.0F;
        this.wtQuatX = 0.0F;
        this.wtQuatY = 0.0F;
        this.wtQuatZ = 0.0F;
        this.wtQuatInited = false;
        this.wtQuatPrevYaw = this.getRotYaw();
        this.wtQuatPrevPitch = this.getRotPitch();
        this.wtQuatPrevRoll = this.getRotRoll();
    }

    @SideOnly(Side.CLIENT)
    private void updateWTPseudoFreeLookCameraOffset(boolean aggressiveProfile, float alpha) {
        if (!this.isWTPseudoFreeLookCameraActive()) {
            this.wtCamYawOffset = 0.0F;
            this.wtCamPitchOffset = 0.0F;
            return;
        }
        float yawLimit = MathHelper.clamp_float((float) MCH_Config.MouseAimPlanePseudoFreeLookYawLimit.prmDouble, 0.0F, 120.0F);
        float pitchLimit = MathHelper.clamp_float((float) MCH_Config.MouseAimPlanePseudoFreeLookPitchLimit.prmDouble, 0.0F, 60.0F);
        float targetYaw = MathHelper.clamp_float(this.wtAimNormX * yawLimit, -yawLimit, yawLimit);
        float targetPitch = MathHelper.clamp_float(-this.wtAimNormY * pitchLimit, -pitchLimit, pitchLimit);
        float camAlpha = MathHelper.clamp_float((aggressiveProfile ? 0.25F : 0.20F) + alpha * 0.35F, 0.20F, 0.72F);
        this.wtCamYawOffset += (targetYaw - this.wtCamYawOffset) * camAlpha;
        this.wtCamPitchOffset += (targetPitch - this.wtCamPitchOffset) * camAlpha;
    }

    private void rotationByKey(float partialTicks) {
        float rot = 0.2F;
        if (!MCH_Config.MouseControlFlightSimMode.prmBool && this.getVtolMode() != 0) {
            rot *= 0.0F;
        }

        if (super.moveLeft && !super.moveRight) {
            this.addkeyRotValue -= rot * partialTicks;
        }

        if (super.moveRight && !super.moveLeft) {
            this.addkeyRotValue += rot * partialTicks;
        }

    }

    public void onUpdateAngles(float partialTicks) {
        if (!this.isDestroyed()) {
            if (super.isGunnerMode) {
                this.setRotPitch(this.getRotPitch() * 0.95F);
                this.setRotYaw(this.getRotYaw() + this.getAcInfo().autoPilotRot * 0.2F);
                if (MathHelper.abs(this.getRotRoll()) > 20.0F) {
                    this.setRotRoll(this.getRotRoll() * 0.95F);
                }
            }

            boolean isFly = MCH_Lib.getBlockIdY(this, 3, -3) == 0;
            float rot;
            if (isFly && !this.isFreeLookMode() && !super.isGunnerMode && (!this.getAcInfo().isFloat || this.getWaterDepth() <= 0.0D)) {
                if (!MCH_Config.MouseControlFlightSimMode.prmBool) {
                    this.rotationByKey(partialTicks);
                    this.setRotRoll(this.getRotRoll() + this.addkeyRotValue * 0.5F * this.getAcInfo().mobilityRoll);
                }
            } else {
                rot = 1.0F;
                if (!isFly) {
                    rot = this.getAcInfo().mobilityYawOnGround;
                    if (!this.getAcInfo().canRotOnGround) {
                        Block block = MCH_Lib.getBlockY(this, 3, -2, false);
                        if (!W_Block.isEqual(block, W_Block.getWater()) && !W_Block.isEqual(block, Blocks.air) && !W_Block.isEqual(block, Blocks.flowing_water)) {
                            rot = 0.0F;
                        }
                    }
                }

                if (super.moveLeft && !super.moveRight) {
                    this.setRotYaw(this.getRotYaw() - 0.6F * rot * partialTicks);
                }

                if (super.moveRight && !super.moveLeft) {
                    this.setRotYaw(this.getRotYaw() + 0.6F * rot * partialTicks);
                }
            }

            this.addkeyRotValue = (float) ((double) this.addkeyRotValue * (1.0D - (double) (0.1F * partialTicks)));
            if (!isFly && MathHelper.abs(this.getRotPitch()) < 40.0F) {
                this.applyOnGroundPitch(0.97F);
            }

            if (this.getNozzleRotation() > 0.001F) {
                rot = 1.0F - 0.03F * partialTicks;
                this.setRotPitch(this.getRotPitch() * rot);
                rot = this.isWTMouseAimActive() && this.getVtolMode() == 0 ? 1.0F - 0.03F * partialTicks : 1.0F - 0.1F * partialTicks;
                this.setRotRoll(this.getRotRoll() * rot);
            }

            this.updateExhaustFlameRotation(partialTicks);

        }
    }

    protected void onUpdate_Control() {
        if (super.isGunnerMode && !this.canUseFuel()) {
            this.switchGunnerMode(false);
        }

        super.throttleBack = (float) ((double) super.throttleBack * 0.8D);
        if (this.getRiddenByEntity() != null && !this.getRiddenByEntity().isDead && this.isCanopyClose() && this.canUseWing() && this.canUseFuel() && !this.isDestroyed()) {
            this.onUpdate_ControlNotHovering();
        } else if (this.isTargetDrone() && this.canUseFuel() && !this.isDestroyed()) {
            super.throttleUp = true;
            this.onUpdate_ControlNotHovering();
        } else if (this.getCurrentThrottle() > 0.0D) {
            this.addCurrentThrottle(-0.0025D * (double) this.getAcInfo().throttleUpDown);
        } else {
            this.setCurrentThrottle(0.0D);
        }

        if (this.getCurrentThrottle() < 0.0D) {
            this.setCurrentThrottle(0.0D);
        }

        if (super.worldObj.isRemote) {
            if (!W_Lib.isClientPlayer(this.getRiddenByEntity())) {
                double ct = this.getThrottle();
                if (this.getCurrentThrottle() > ct) {
                    this.addCurrentThrottle(-0.005D);
                }

                if (this.getCurrentThrottle() < ct) {
                    this.addCurrentThrottle(0.005D);
                }
            }
        } else {
            this.setThrottle(this.getCurrentThrottle());
        }

    }

    protected void onUpdate_ControlNotHovering() {
        // 判断是否不处于炮手模式
        if (!super.isGunnerMode) {
            // 获取油门上下状态
            float throttleUpDown = this.getAcInfo().throttleUpDown;

            // 判断是否是转向状态（只左转或只右转）
            boolean turn = super.moveLeft && !super.moveRight || !super.moveLeft && super.moveRight;

            // 获取旋转转向油门
            float pivotTurnThrottle = this.getAcInfo().pivotTurnThrottle;

            // 本地油门上升状态
            boolean localThrottleUp = super.throttleUp;

            // 如果是转向且当前油门小于旋转油门阈值，并且没有加速和减速
            if (turn && this.getCurrentThrottle() < (double) this.getAcInfo().pivotTurnThrottle && !localThrottleUp && !super.throttleDown) {
                // 设置本地油门上升状态为true
                localThrottleUp = true;
                // 加速倍增
                throttleUpDown *= 2.0F;
            }

            // 如果本地油门上升
            if (localThrottleUp) {
                // 设置油门为当前油门
                float f = throttleUpDown;

                // 如果骑乘的实体不为空，调整油门
                if (this.getRidingEntity() != null) {
                    double mx = this.getRidingEntity().motionX;
                    double mz = this.getRidingEntity().motionZ;
                    // 基于骑乘实体的速度调整油门
                    f = throttleUpDown * MathHelper.sqrt_double(mx * mx + mz * mz) * this.getAcInfo().throttleUpDownOnEntity;
                }

                // 如果允许倒车并且油门向后，则递减后退油门
                if (this.getAcInfo().enableBack && super.throttleBack > 0.0F) {
                    super.throttleBack = (float) ((double) super.throttleBack - 0.01D * (double) f);
                } else {
                    // 否则，设置后退油门为0
                    super.throttleBack = 0.0F;
                    // 如果当前油门小于1，则增加油门
                    if (this.getCurrentThrottle() < 1.0D) {
                        this.addCurrentThrottle(0.01D * (double) f);
                    } else {
                        // 否则，设置油门为最大值1
                        this.setCurrentThrottle(1.0D);
                    }
                }
            }
            // 如果本地油门下降
            else if (super.throttleDown) {
                // 如果当前油门大于0，则递减油门
                if (this.getCurrentThrottle() > 0.0D) {
                    this.addCurrentThrottle(-0.01D * (double) throttleUpDown);
                } else {
                    // 否则，设置油门为0
                    this.setCurrentThrottle(0.0D);
                    // 如果允许倒车，则增加后退油门
                    if (this.getAcInfo().enableBack) {
                        super.throttleBack = (float) ((double) super.throttleBack + 0.0025D * (double) throttleUpDown);
                        // 限制后退油门不超过0.6
                        if (super.throttleBack > 0.6F) {
                            super.throttleBack = 0.6F;
                        }
                    }
                }
            }
            // 如果启用了自动油门降低，并且当前油门大于0，则逐步降低油门
            else if (super.cs_planeAutoThrottleDown && this.getCurrentThrottle() > 0.0D) {
                this.addCurrentThrottle(-0.005D * (double) throttleUpDown);
                // 如果油门低于0，则设置为0
                if (this.getCurrentThrottle() <= 0.0D) {
                    this.setCurrentThrottle(0.0D);
                }
            }
        }
    }


    protected void onUpdate_Particle() {
        if (super.worldObj.isRemote) {
            this.onUpdate_ParticleLandingGear();
            this.onUpdate_ParticleNozzle();
        }

    }

    protected void onUpdate_Particle2() {
        if (super.worldObj.isRemote) {
            if ((double) this.getHP() < (double) this.getMaxHP() * 0.5D) {
                if (this.getPlaneInfo() != null) {
                    int rotorNum = this.getPlaneInfo().rotorList.size();

                    if (super.isFirstDamageSmoke) {
                        super.prevDamageSmokePos = new Vec3[rotorNum + 1];
                    }

                    float yaw = this.getRotYaw();
                    float pitch = this.getRotPitch();
                    float roll = this.getRotRoll();
                    boolean spawnSmoke = true;

                    int px;
                    for (px = 0; px < rotorNum; ++px) {
                        if ((double) this.getHP() >= (double) this.getMaxHP() * 0.2D && this.getMaxHP() > 0) {
                            int rotor_pos = (int) (((double) this.getHP() / (double) this.getMaxHP() - 0.2D) / 0.3D * 15.0D);
                            if (rotor_pos > 0 && super.rand.nextInt(rotor_pos) > 0) {
                                spawnSmoke = false;
                            }
                        }

                        Vec3 var16 = ((MCP_PlaneInfo.Rotor) this.getPlaneInfo().rotorList.get(px)).pos;
                        Vec3 py = MCH_Lib.RotVec3(var16, -yaw, -pitch, -roll);
                        double x = super.posX + py.xCoord;
                        double y = super.posY + py.yCoord;
                        double z = super.posZ + py.zCoord;
                        this.onUpdate_Particle2SpawnSmoke(px, x, y, z, 1.0F, spawnSmoke);
                    }

                    spawnSmoke = true;
                    if ((double) this.getHP() >= (double) this.getMaxHP() * 0.2D && this.getMaxHP() > 0) {
                        px = (int) (((double) this.getHP() / (double) this.getMaxHP() - 0.2D) / 0.3D * 15.0D);
                        if (px > 0 && super.rand.nextInt(px) > 0) {
                            spawnSmoke = false;
                        }
                    }

                    double var15 = super.posX;
                    double var17 = super.posY;
                    double pz = super.posZ;
                    if (this.getSeatInfo(0) != null && this.getSeatInfo(0).pos != null) {
                        Vec3 pos = MCH_Lib.RotVec3(0.0D, this.getSeatInfo(0).pos.yCoord, -2.0D, -yaw, -pitch, -roll);
                        var15 += pos.xCoord;
                        var17 += pos.yCoord;
                        pz += pos.zCoord;
                    }

                    this.onUpdate_Particle2SpawnSmoke(rotorNum, var15, var17, pz, rotorNum == 0 ? 2.0F : 1.0F, spawnSmoke);
                    super.isFirstDamageSmoke = false;
                }
            }
        }
    }

    public void onUpdate_Particle2SpawnSmoke(int ri, double x, double y, double z, float size, boolean spawnSmoke) {
        if (super.isFirstDamageSmoke || super.prevDamageSmokePos[ri] == null) {
            super.prevDamageSmokePos[ri] = Vec3.createVectorHelper(x, y, z);
        }

        Vec3 prev = super.prevDamageSmokePos[ri];
        double dx = x - prev.xCoord;
        double dy = y - prev.yCoord;
        double dz = z - prev.zCoord;
        int num = (int) ((double) MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz) / 0.3D) + 1;

        for (int i = 0; i < num; ++i) {
            float c = 0.2F + super.rand.nextFloat() * 0.3F;
            MCH_ParticleParam prm = new MCH_ParticleParam(super.worldObj, "smoke", prev.xCoord + (x - prev.xCoord) * (double) i / 3.0D, prev.yCoord + (y - prev.yCoord) * (double) i / 3.0D, prev.zCoord + (z - prev.zCoord) * (double) i / 3.0D);
            prm.motionX = (double) size * (super.rand.nextDouble() - 0.5D) * 0.3D;
            prm.motionY = (double) size * super.rand.nextDouble() * 0.1D;
            prm.motionZ = (double) size * (super.rand.nextDouble() - 0.5D) * 0.3D;
            prm.size = size * ((float) super.rand.nextInt(5) + 5.0F) * 1.0F;
            prm.setColor(0.7F + super.rand.nextFloat() * 0.1F, c, c, c);
            MCH_ParticlesUtil.spawnParticle(prm);
        }

        super.prevDamageSmokePos[ri].xCoord = x;
        super.prevDamageSmokePos[ri].yCoord = y;
        super.prevDamageSmokePos[ri].zCoord = z;
    }

    public void onUpdate_ParticleLandingGear() {
        double d = super.motionX * super.motionX + super.motionZ * super.motionZ;
        if (d > 0.01D) {
            int x = MathHelper.floor_double(super.posX + 0.5D);
            int y = MathHelper.floor_double(super.posY - 0.5D);
            int z = MathHelper.floor_double(super.posZ + 0.5D);
            MCH_ParticlesUtil.spawnParticleTileCrack(super.worldObj, x, y, z, super.posX + ((double) super.rand.nextFloat() - 0.5D) * (double) super.width, super.boundingBox.minY + 0.1D, super.posZ + ((double) super.rand.nextFloat() - 0.5D) * (double) super.width, -super.motionX * 4.0D, 1.5D, -super.motionZ * 4.0D);
        }

    }

    private void onUpdate_ParticleSplash() {
        if (this.getAcInfo() != null) {
            if (super.worldObj.isRemote) {
                double mx = super.posX - super.prevPosX;
                double mz = super.posZ - super.prevPosZ;
                double dist = mx * mx + mz * mz;
                if (dist > 1.0D) {
                    dist = 1.0D;
                }

                for (Object o : this.getAcInfo().particleSplashs) {
                    MCH_AircraftInfo.ParticleSplash p = (MCH_AircraftInfo.ParticleSplash) o;

                    for (int i = 0; i < p.num; ++i) {
                        if (dist > 0.03D + (double) super.rand.nextFloat() * 0.1D) {
                            this.setParticleSplash(p.pos, -mx * (double) p.acceleration, (double) p.motionY, -mz * (double) p.acceleration, p.gravity, (double) p.size * (0.5D + dist * 0.5D), p.age);
                        }
                    }
                }

            }
        }
    }

    private void setParticleSplash(Vec3 pos, double mx, double my, double mz, float gravity, double size, int age) {
        Vec3 v = this.getTransformedPosition(pos);
        v = v.addVector(super.rand.nextDouble() - 0.5D, (super.rand.nextDouble() - 0.5D) * 0.5D, super.rand.nextDouble() - 0.5D);
        int x = (int) (v.xCoord + 0.5D);
        int y = (int) (v.yCoord + 0.0D);
        int z = (int) (v.zCoord + 0.5D);
        if (W_WorldFunc.isBlockWater(super.worldObj, x, y, z)) {
            float c = super.rand.nextFloat() * 0.3F + 0.7F;
            MCH_ParticleParam prm = new MCH_ParticleParam(super.worldObj, "smoke", v.xCoord, v.yCoord, v.zCoord);
            prm.motionX = mx + ((double) super.rand.nextFloat() - 0.5D) * 0.7D;
            prm.motionY = my;
            prm.motionZ = mz + ((double) super.rand.nextFloat() - 0.5D) * 0.7D;
            prm.size = (float) size * (super.rand.nextFloat() * 0.2F + 0.8F);
            prm.setColor(0.9F, c, c, c);
            prm.age = age + (int) ((double) super.rand.nextFloat() * 0.5D * (double) age);
            prm.gravity = gravity;
            MCH_ParticlesUtil.spawnParticle(prm);
        }

    }

    public void onUpdate_ParticleNozzle() {
        if (this.planeInfo != null && this.planeInfo.haveNozzle()) {
            if (this.getCurrentThrottle() > 0.10000000149011612D) {
                float yaw = this.getRotYaw();
                float pitch = this.getRotPitch();
                float roll = this.getRotRoll();
                Vec3 nozzleRot = MCH_Lib.RotVec3(0.0D, 0.0D, 1.0D, -yaw - 180.0F, pitch - this.getNozzleRotation(), roll);

                for (Object o : this.planeInfo.nozzles) {
                    MCH_AircraftInfo.DrawnPart nozzle = (MCH_AircraftInfo.DrawnPart) o;
                    if ((double) super.rand.nextFloat() <= this.getCurrentThrottle() * 1.5D) {
                        Vec3 nozzlePos = MCH_Lib.RotVec3(nozzle.pos, -yaw, -pitch, -roll);
                        double x = super.posX + nozzlePos.xCoord + nozzleRot.xCoord;
                        double y = super.posY + nozzlePos.yCoord + nozzleRot.yCoord;
                        double z = super.posZ + nozzlePos.zCoord + nozzleRot.zCoord;
                        float a = 0.7F;
                        if (W_WorldFunc.getBlockId(super.worldObj, (int) (x + nozzleRot.xCoord * 3.0D), (int) (y + nozzleRot.yCoord * 3.0D), (int) (z + nozzleRot.zCoord * 3.0D)) != 0) {
                            a = 2.0F;
                        }

                        MCH_ParticleParam prm = new MCH_ParticleParam(super.worldObj, "smoke", x, y, z, nozzleRot.xCoord + (double) ((super.rand.nextFloat() - 0.5F) * a), nozzleRot.yCoord, nozzleRot.zCoord + (double) ((super.rand.nextFloat() - 0.5F) * a), 5.0F * this.getAcInfo().particlesScale);
                        MCH_ParticlesUtil.spawnParticle(prm);
                    }
                }

            }
        }
    }

    public void destroyAircraft(DamageSource source) {
        super.destroyAircraft(source);
        byte inv = 1;
        if (this.getRotRoll() >= 0.0F) {
            if (this.getRotRoll() > 90.0F) {
                inv = -1;
            }
        } else if (this.getRotRoll() > -90.0F) {
            inv = -1;
        }

        super.rotDestroyedRoll = (0.5F + super.rand.nextFloat()) * (float) inv;
    }

    protected void onUpdate_Client() {
        if (this.getRiddenByEntity() != null && W_Lib.isClientPlayer(this.getRiddenByEntity())) {
            this.getRiddenByEntity().rotationPitch = this.getRiddenByEntity().prevRotationPitch;
        }

        if (super.aircraftPosRotInc > 0) {
            this.applyServerPositionAndRotation();
        } else {
            this.setPosition(super.posX + super.motionX, super.posY + super.motionY, super.posZ + super.motionZ);
            if (!this.isDestroyed() && (super.onGround || MCH_Lib.getBlockIdY(this, 1, -2) > 0)) {
                super.motionX *= 0.95D;
                super.motionZ *= 0.95D;
                this.applyOnGroundPitch(0.95F);
            }

            if (this.isInWater()) {
                super.motionX *= 0.99D;
                super.motionZ *= 0.99D;
            }
        }

        if (this.isDestroyed()) {
            if (MCH_Lib.getBlockIdY(this, 3, -3) == 0) {
                if (MathHelper.abs(this.getRotPitch()) < 10.0F) {
                    this.setRotPitch(this.getRotPitch() + super.rotDestroyedPitch);
                }

                float roll = MathHelper.abs(this.getRotRoll());
                if (roll < 45.0F || roll > 135.0F) {
                    this.setRotRoll(this.getRotRoll() + super.rotDestroyedRoll);
                }
            } else if (MathHelper.abs(this.getRotPitch()) > 20.0F) {
                this.setRotPitch(this.getRotPitch() * 0.99F);
            }
        }

        this.getRiddenByEntity();

        this.updateSound();
        this.onUpdate_Particle();
        this.onUpdate_Particle2();
        this.onUpdate_ParticleSplash();
        this.onUpdate_ParticleSandCloud(true);
        this.updateCamera(super.posX, super.posY, super.posZ);
    }

    private void onUpdate_Server() {
        Entity rdnEnt = this.getRiddenByEntity();
        double prevMotion = Math.sqrt(super.motionX * super.motionX + super.motionZ * super.motionZ);
        double dp = 0.0D;
        if (this.canFloatWater()) {
            dp = this.getWaterDepth();
        }

        boolean levelOff = super.isGunnerMode;
        if (dp == 0.0D) {
            // 如果是目标无人机，并且有足够的燃料且没有被摧毁，则执行以下代码
            if (this.isTargetDrone() && this.canUseFuel() && !this.isDestroyed()) {

                // 获取无人机当前位置3个单位向下、40个单位向前的方块
                Block throttle = MCH_Lib.getBlockY(this, 3, -100, true);

                // 如果方块不为空且不是空气方块（即存在某个物体）
                if (throttle != null && !W_Block.isEqual(throttle, Blocks.air)) {

                    // 如果没有找到目标方块，或者目标方块是空气方块，则执行下面的代码
                    throttle = MCH_Lib.getBlockY(this, 3, -5, true);

                    // 如果目标方块为空或是空气方块，进行自动驾驶的旋转和俯仰调整
                    if (throttle == null || W_Block.isEqual(throttle, Blocks.air)) {

                        // 根据自动驾驶旋转量调整航向（Yaw）
                        this.setRotYaw(this.getRotYaw() + this.getAcInfo().autoPilotRot * 2.0F);

                        // 如果俯仰角度大于-20度，则逐渐减小俯仰角度
                        if (this.getRotPitch() > -20.0F) {
                            this.setRotPitch(this.getRotPitch() - 0.5F);
                        }
                    }
                } else {
                    // 如果没有遇到障碍物，则按照自动驾驶的旋转量调整航向（Yaw）
                    this.setRotYaw(this.getRotYaw() + this.getAcInfo().autoPilotRot * 1.0F);

                    // 自动调整俯仰角度，使其逐渐减小
                    this.setRotPitch(this.getRotPitch() * 0.95F);

                    // 如果可以收起起落架，则执行收起起落架的操作
                    if (this.canFoldLandingGear()) {
                        this.foldLandingGear();
                    }

                    // 标记为平稳飞行状态
                    levelOff = true;
                }
            }


            if (!levelOff) {
                super.motionY += 0.04D + (double) (!this.isInWater() ? this.getAcInfo().gravity : this.getAcInfo().gravityInWater);
                super.motionY += -0.047D * (1.0D - this.getCurrentThrottle());
            } else {
                super.motionY *= 0.8D;
            }
        } else {
            this.setRotPitch(this.getRotPitch() * 0.8F, "getWaterDepth != 0");
            if (MathHelper.abs(this.getRotRoll()) < 40.0F) {
                this.setRotRoll(this.getRotRoll() * 0.9F);
            }

            if (dp < 1.0D) {
                super.motionY -= 1.0E-4D;
                super.motionY += 0.007D * this.getCurrentThrottle();
            } else {
                if (super.motionY < 0.0D) {
                    super.motionY /= 2.0D;
                }

                super.motionY += 0.007D;
            }
        }

        // 计算油门1的值，当前油门除以10
        float throttle1 = (float) (this.getCurrentThrottle() / 10.0D);
        Vec3 v;

        // 如果喷嘴的旋转角度大于0.001F
        if (this.getNozzleRotation() > 0.001F) {
            // 根据喷嘴旋转角度调整飞机俯仰角度
            this.setRotPitch(this.getRotPitch() * 0.95F);
            // 根据航向角和俯仰角计算方向向量
            v = MCH_Lib.Rot2Vec3(this.getRotYaw(), this.getRotPitch() - this.getNozzleRotation());
            // 如果喷嘴旋转角度大于等于90度，缩小x和z方向的速度
            if (this.getNozzleRotation() >= 90.0F) {
                v.xCoord *= 0.800000011920929D;
                v.zCoord *= 0.800000011920929D;
            }
        } else {
            // 否则，计算默认的方向向量，俯仰角度减去10度
            v = MCH_Lib.Rot2Vec3(this.getRotYaw(), this.getRotPitch() - 10.0F);
        }

        // 如果没有达到平稳飞行状态
        if (!levelOff) {
            // 如果喷嘴旋转角度小于等于0.01F，根据油门调整垂直方向上的速度
            if (this.getNozzleRotation() <= 0.01F) {
                super.motionY += v.yCoord * (double) throttle1 / 2.0D;
            } else {
                super.motionY += v.yCoord * (double) throttle1 / 8.0D;
            }
        }

        if(!MCH_WeaponGuidanceSystem.isEntityOnGround(this, 15)) {
            if (this.canFoldLandingGear()) {
                this.foldLandingGear();
            }
        }

        // 判断是否可以在地面移动
        boolean canMove = true;
        if (!this.getAcInfo().canMoveOnGround) {
            // 获取地面方块信息，判断是否可以移动
            Block motion = MCH_Lib.getBlockY(this, 3, -2, false);
            // 如果方块不是水或者空气方块，设置canMove为false，表示不能移动
            if (!W_Block.isEqual(motion, W_Block.getWater()) && !W_Block.isEqual(motion, Blocks.air) && !W_Block.isEqual(motion, Blocks.flowing_water)) {
                canMove = false;
            }
        }

        // 如果可以移动，则更新水平速度
        if (canMove) {
            // 如果启用了倒车功能，并且油门向后，则根据油门倒退
            if (this.getAcInfo().enableBack && super.throttleBack > 0.0F) {
                super.motionX -= v.xCoord * (double) super.throttleBack;
                super.motionZ -= v.zCoord * (double) super.throttleBack;
            } else {
                // 否则，根据油门前进
                super.motionX += v.xCoord * (double) throttle1;
                super.motionZ += v.zCoord * (double) throttle1;
            }
        }

        // 对垂直速度进行衰减
        super.motionY *= 0.95D;
        // 根据飞行器的运动系数衰减水平速度
        super.motionX *= this.getAcInfo().motionFactor;
        super.motionZ *= this.getAcInfo().motionFactor;

        // 计算当前水平速度的大小
        double motion1 = Math.sqrt(super.motionX * super.motionX + super.motionZ * super.motionZ);
        // 获取最大速度限制
        float speedLimit = this.getMaxSpeed();
        // 如果当前速度超过最大速度限制，按最大速度比例缩小水平速度
        if (motion1 > (double) speedLimit) {
            super.motionX *= (double) speedLimit / motion1;
            super.motionZ *= (double) speedLimit / motion1;
            motion1 = speedLimit;
        }

        // 如果当前速度大于上一帧的速度，并且当前速度小于最大速度限制，逐步增加速度
        if (motion1 > prevMotion && super.currentSpeed < (double) speedLimit) {
            super.currentSpeed += ((double) speedLimit - super.currentSpeed) / 35.0D;
            if (super.currentSpeed > (double) speedLimit) {
                super.currentSpeed = (double) speedLimit;
            }
        } else {
            // 否则逐步减少速度，保持最低速度0.07
            super.currentSpeed -= (super.currentSpeed - 0.07D) / 35.0D;
            if (super.currentSpeed < 0.07D) {
                super.currentSpeed = 0.07D;
            }
        }

        // 如果飞行器在地面或距离地面较近，则缩减水平速度，应用地面俯仰角度
        if (super.onGround || MCH_Lib.getBlockIdY(this, 1, -2) > 0) {
            super.motionX *= this.getAcInfo().motionFactor;
            super.motionZ *= this.getAcInfo().motionFactor;
            // 如果俯仰角度小于40度，则根据地面状态调整俯仰角度
            if (MathHelper.abs(this.getRotPitch()) < 40.0F) {
                this.applyOnGroundPitch(0.8F);
            }
        }

        // 更新飞行器位置
        this.moveEntity(super.motionX, super.motionY, super.motionZ);

        // 更新旋转角度
        this.setRotation(this.getRotYaw(), this.getRotPitch());
        // 更新方块信息
        this.onUpdate_updateBlock();

        // 如果骑乘的实体存在并且已经死亡，则解除骑乘
        if (this.getRiddenByEntity() != null && this.getRiddenByEntity().isDead) {
            this.unmountEntity();
            super.riddenByEntity = null;
        }


    }

    public float getMaxSpeed() {
        float f = 0.0F;
        if (this.partWing != null && this.getPlaneInfo().isVariableSweepWing) {
            f = (this.getPlaneInfo().sweepWingSpeed - this.getPlaneInfo().speed) * this.partWing.getFactor();
        } else if (super.partHatch != null && this.getPlaneInfo().isVariableSweepWing) {
            f = (this.getPlaneInfo().sweepWingSpeed - this.getPlaneInfo().speed) * super.partHatch.getFactor();
        }

        return this.getPlaneInfo().speed + f;
    }

    public float getSoundVolume() {
        return this.getAcInfo() != null && this.getAcInfo().throttleUpDown <= 0.0F ? 0.0F : this.soundVolume * 0.7F;
    }

    public void updateSound() {
        float target = (float) this.getCurrentThrottle();
        if (this.getRiddenByEntity() != null && (super.partCanopy == null || this.getCanopyRotation() < 1.0F)) {
            target += 0.1F;
        }

        if (this.soundVolume < target) {
            this.soundVolume += 0.02F;
            if (this.soundVolume >= target) {
                this.soundVolume = target;
            }
        } else if (this.soundVolume > target) {
            this.soundVolume -= 0.02F;
            if (this.soundVolume <= target) {
                this.soundVolume = target;
            }
        }

    }

    public float getSoundPitch() {
        return (float) (0.6D + this.getCurrentThrottle() * 0.4D);
    }

    public String getDefaultSoundName() {
        return "plane";
    }

    public void updateParts(int stat) {
        super.updateParts(stat);
        if (!this.isDestroyed()) {
            MCH_Parts[] parts = new MCH_Parts[]{this.partNozzle, this.partWing};
            MCH_Parts[] arr$ = parts;
            int len$ = parts.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                MCH_Parts p = arr$[i$];
                if (p != null) {
                    p.updateStatusClient(stat);
                    p.update();
                }
            }

            if (!super.worldObj.isRemote && this.partWing != null && this.getPlaneInfo().isVariableSweepWing && this.partWing.isON() && this.getCurrentThrottle() >= 0.20000000298023224D && (this.getCurrentThrottle() < 0.5D || MCH_Lib.getBlockIdY(this, 1, -10) != 0)) {
                this.partWing.setStatusServer(false);
            }

        }
    }

    public float getUnfoldLandingGearThrottle() {
        return 0.7F;
    }

    public boolean canSwitchVtol() {
        if (this.planeInfo != null && this.planeInfo.isEnableVtol) {
            if (this.getModeSwitchCooldown() > 0) {
                return false;
            } else if (this.getVtolMode() == 1) {
                return false;
            } else if (MathHelper.abs(this.getRotRoll()) > 30.0F) {
                return false;
            } else if (super.onGround && this.planeInfo.isDefaultVtol) {
                return false;
            } else {
                this.setModeSwitchCooldown(20);
                return true;
            }
        } else {
            return false;
        }
    }

    public boolean getNozzleStat() {
        return this.partNozzle != null && this.partNozzle.getStatus();
    }

    public int getVtolMode() {
        return !this.getNozzleStat() ? (this.getNozzleRotation() <= 0.005F ? 0 : 1) : (this.getNozzleRotation() >= 89.995F ? 2 : 1);
    }

    public float getNozzleRotation() {
        return this.partNozzle != null ? this.partNozzle.rotation : 0.0F;
    }

    public float getPrevNozzleRotation() {
        return this.partNozzle != null ? this.partNozzle.prevRotation : 0.0F;
    }

    public void switchVtolMode(boolean mode) {
        if (this.partNozzle != null) {
            if (this.planeInfo.isDefaultVtol && super.onGround && !mode) {
                return;
            }

            if (!super.worldObj.isRemote) {
                this.partNozzle.setStatusServer(mode);
            }

            if (this.getRiddenByEntity() != null && !this.getRiddenByEntity().isDead) {
                this.getRiddenByEntity().rotationPitch = this.getRiddenByEntity().prevRotationPitch = 0.0F;
            }
        }

    }

    protected MCH_Parts createNozzle(MCP_PlaneInfo info) {
        MCH_Parts nozzle = null;
        if (info.haveNozzle() || info.haveRotor() || info.isEnableVtol) {
            nozzle = new MCH_Parts(this, 1, 31, "Nozzle");
            nozzle.rotationMax = 90.0F;
            nozzle.rotationInv = 1.5F;
            nozzle.soundStartSwichOn.setPrm("plane_cc", 1.0F, 0.5F);
            nozzle.soundEndSwichOn.setPrm("plane_cc", 1.0F, 0.5F);
            nozzle.soundStartSwichOff.setPrm("plane_cc", 1.0F, 0.5F);
            nozzle.soundEndSwichOff.setPrm("plane_cc", 1.0F, 0.5F);
            nozzle.soundSwitching.setPrm("plane_cv", 1.0F, 0.5F);
            if (info.isDefaultVtol) {
                nozzle.forceSwitch(true);
            }
        }

        return nozzle;
    }

    protected MCH_Parts createWing(MCP_PlaneInfo info) {
        MCH_Parts wing = null;
        if (this.planeInfo.haveWing()) {
            wing = new MCH_Parts(this, 3, 31, "Wing");
            wing.rotationMax = 90.0F;
            wing.rotationInv = 2.5F;
            wing.soundStartSwichOn.setPrm("plane_cc", 1.0F, 0.5F);
            wing.soundEndSwichOn.setPrm("plane_cc", 1.0F, 0.5F);
            wing.soundStartSwichOff.setPrm("plane_cc", 1.0F, 0.5F);
            wing.soundEndSwichOff.setPrm("plane_cc", 1.0F, 0.5F);
        }

        return wing;
    }

    public boolean canUseWing() {
        return this.partWing == null || (this.getPlaneInfo().isVariableSweepWing ? (!(this.getCurrentThrottle() < 0.2D) || this.partWing.isOFF()) : this.partWing.isOFF());
    }

    public boolean canFoldWing() {
        if (this.partWing != null && this.getModeSwitchCooldown() <= 0) {
            if (this.getPlaneInfo().isVariableSweepWing) {
                if (!super.onGround && MCH_Lib.getBlockIdY(this, 3, -20) == 0) {
                    if (this.getCurrentThrottle() < 0.699999988079071D) {
                        return false;
                    }
                } else if (this.getCurrentThrottle() > 0.10000000149011612D) {
                    return false;
                }
            } else {
                if (!super.onGround && MCH_Lib.getBlockIdY(this, 3, -3) == 0) {
                    return false;
                }

                if (this.getCurrentThrottle() > 0.009999999776482582D) {
                    return false;
                }
            }

            return this.partWing.isOFF();
        } else {
            return false;
        }
    }

    public boolean canUnfoldWing() {
        return this.partWing != null && this.getModeSwitchCooldown() <= 0 && this.partWing.isON();
    }

    public void foldWing(boolean fold) {
        if (this.partWing != null && this.getModeSwitchCooldown() <= 0) {
            this.partWing.setStatusServer(fold);
            this.setModeSwitchCooldown(20);
        }
    }

    public float getWingRotation() {
        return this.partWing != null ? this.partWing.rotation : 0.0F;
    }

    public float getPrevWingRotation() {
        return this.partWing != null ? this.partWing.prevRotation : 0.0F;
    }

    private void updateExhaustFlameRotation(float partialTicks) {
        this.prevRotationExhaustFlameY = this.rotationExhaustFlameY;
        float key = 0.0F;
        if (this.moveLeft && !this.moveRight) key = -1.0F;
        if (this.moveRight && !this.moveLeft) key =  1.0F;
        float target = key;
        float follow = 0.1F * partialTicks;
        if (follow > 1.0F) follow = 1.0F;
        this.rotationExhaustFlameY += (target - this.rotationExhaustFlameY) * follow;
        if (this.rotationExhaustFlameY >  1.0F) this.rotationExhaustFlameY =  1.0F;
        if (this.rotationExhaustFlameY < -1.0F) this.rotationExhaustFlameY = -1.0F;
        this.prevRotationExhaustFlameX = this.rotationExhaustFlameX;
        this.prevRotationExhaustFlameZ = this.rotationExhaustFlameZ;
    }

    public static class ExhaustAnimState {
        final int[] frame;
        final int[] tick;
        ExhaustAnimState(int n) {
            this.frame = new int[n];
            this.tick = new int[n];
        }
    }

}
