package mcheli.weapon;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.*;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntityHitBox;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_PacketNotifyHitBullet;
import mcheli.chain.MCH_EntityChain;
import mcheli.flare.MCH_EntityChaff;
import mcheli.flare.MCH_EntityFlare;
import mcheli.network.packets.PacketLockTarget;
import mcheli.network.packets.PacketPlaySound;
import mcheli.particles.MCH_ParticleParam;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.mob.MCH_EntityGunner;
import mcheli.tank.MCH_EntityTank;
import mcheli.vehicle.MCH_EntityVehicle;
import mcheli.vector.Vector3f;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_EntityPlayer;
import mcheli.wrapper.W_MovingObjectPosition;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityCloudFX;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class MCH_EntityBaseBullet extends W_Entity implements MCH_IChunkLoader {

    public static final int DATAWT_RESERVE1 = 26;
    public static final int DATAWT_TARGET_ENTITY = 27;
    public static final int DATAWT_MARKER_STAT = 28;
    public static final int DATAWT_NAME = 29;
    public static final int DATAWT_BULLET_MODEL = 30;
    public static final int DATAWT_BOMBLET_FLAG = 31;
    public static final int DATAWT_DATALINK_FLAGS = DATAWT_MARKER_STAT;
    public Entity shootingEntity;
    public Entity shootingAircraft;
    public int explosionPower;
    public int explosionPowerInWater;
    public double acceleration;
    public double accelerationFactor;
    public Entity targetEntity;
    public int piercing;
    public int delayFuse;
    public int sprinkleTime;
    public int spawnedBulletNum;
    public byte isBomblet;
    public double prevPosX2;
    public double prevPosY2;
    public double prevPosZ2;
    public double prevMotionX;
    public double prevMotionY;
    public double prevMotionZ;
    public boolean antiFlareUse;
    public int antiFlareTick;
    public int numLockedChaff = 0;
    public boolean armHojCepActive = false;
    public boolean heatSeekerDatalinkMode = false;
    public int airburstDist = 0;
    public Vec3 initPos;
    boolean doingTopAttack = false;
    boolean speedAddedFromAircraft = false;
    private int countOnUpdate;
    private int power;
    private MCH_WeaponInfo weaponInfo;
    private MCH_BulletModel model;
    private ForgeChunkManager.Ticket chunkLoaderTicket;
    private List<ChunkCoordIntPair> loadedChunks = new ArrayList<>();
    private boolean useDirectChunkLoading = false;
    private static boolean bfmcCoreDetected = false;
    private static boolean bfmcCoreChecked = false;
    public static boolean debugChunkLoading = false;
    private static java.io.PrintWriter debugLogWriter = null;

    private static void writeDebugLog(String msg) {
        try {
            if (debugLogWriter == null) {
                debugLogWriter = new java.io.PrintWriter(new java.io.FileWriter("missile_debug.log", true));
                debugLogWriter.println("=== MCHeli Missile Debug Log ===");
                debugLogWriter.println("=== Started at " + new java.util.Date() + " ===");
                debugLogWriter.flush();
            }
            debugLogWriter.println(msg);
            debugLogWriter.flush();
        } catch (Exception ignored) {}
    }

    public static void closeDebugLog() {
        try { if (debugLogWriter != null) { debugLogWriter.close(); debugLogWriter = null; } } catch (Exception ignored) {}
    }
    private double airburstTravelled = 0.0D;
    private boolean airburstTriggered = false;
    private boolean aheadTriggered = false;
    private static boolean explosionDebugEnabled = false;
    public String nameOnRWR = "MSL";
    private boolean delayFuseMarkerActive = false;
    private double delayFuseMarkerX = 0.0D;
    private double delayFuseMarkerY = 0.0D;
    private double delayFuseMarkerZ = 0.0D;
    private boolean armorRicochetActive = false;
    private boolean dataLinkRelayMode = false;
    private boolean dataLinkRelayEverEnabled = false;
    private boolean activeRadarCaptured = false;
    private boolean dataLinkTwsSelectedOnly = false;
    protected static final int DATALINK_ACTIVE_RADAR_DELAY_TICK = 40;
    private boolean missileWatchDeathLogged = false;
    private String missileDeathReasonHint = "";
    protected double lastTargetPosX;
    protected double lastTargetPosY;
    protected double lastTargetPosZ;
    protected double lastTargetVelX;
    protected double lastTargetVelY;
    protected double lastTargetVelZ;
    protected boolean hasLastKnownTarget = false;
    protected int snapshotTargetId = 0;
    protected double snapshotPosX, snapshotPosY, snapshotPosZ;
    protected double snapshotVelX, snapshotVelY, snapshotVelZ;
    protected long snapshotLastUpdate = 0L;

    public MCH_EntityBaseBullet(World par1World) {
        super(par1World);
        this.countOnUpdate = 0;
        this.setSize(1.0F, 1.0F);
        super.prevRotationYaw = super.rotationYaw;
        super.prevRotationPitch = super.rotationPitch;
        this.targetEntity = null;
        this.setPower(1);
        this.acceleration = 1.0D;
        this.accelerationFactor = 1.0D;
        this.piercing = 0;
        this.explosionPower = 0;
        this.explosionPowerInWater = 0;
        this.delayFuse = 0;
        this.sprinkleTime = 0;
        this.isBomblet = -1;
        this.weaponInfo = null;
        super.ignoreFrustumCheck = true;
        if (par1World.isRemote) {
            this.model = null;
        }

    }

    public MCH_EntityBaseBullet(World par1World, double px, double py, double pz, double mx, double my, double mz, float yaw, float pitch, double acceleration) {
        this(par1World);
        this.setSize(1.0F, 1.0F);
        this.setLocationAndAngles(px, py, pz, yaw, pitch);
        this.setPosition(px, py, pz);
        super.prevRotationYaw = yaw;
        super.prevRotationPitch = pitch;
        super.yOffset = 0.0F;
        if (acceleration > 3.9D) {
            acceleration = 3.9D;
        }
        double d = MathHelper.sqrt_double(mx * mx + my * my + mz * mz);
        super.motionX = mx * acceleration / d;
        super.motionY = my * acceleration / d;
        super.motionZ = mz * acceleration / d;
        this.prevMotionX = super.motionX;
        this.prevMotionY = super.motionY;
        this.prevMotionZ = super.motionZ;
        this.acceleration = acceleration;
        this.initPos = Vec3.createVectorHelper(px, py, pz);
    }

    public void init(ForgeChunkManager.Ticket ticket) {
        if (!worldObj.isRemote) {
            if (ticket != null) {
                if (chunkLoaderTicket == null) {
                    chunkLoaderTicket = ticket;
                    chunkLoaderTicket.bindEntity(this);
                    chunkLoaderTicket.getModData();
                }
                ForgeChunkManager.forceChunk(chunkLoaderTicket, new ChunkCoordIntPair(chunkCoordX, chunkCoordZ));
            }
        }
    }

    public void checkAndLoadChunks() {
        int currentChunkX = MathHelper.floor_double(posX) >> 4;
        int currentChunkZ = MathHelper.floor_double(posZ) >> 4;
        loadChunksInBulletPath(currentChunkX, currentChunkZ, motionX, motionZ);
    }

    public void loadNeighboringChunks(int chunkX, int chunkZ) {
        if (!worldObj.isRemote && chunkLoaderTicket != null) {
            for (ChunkCoordIntPair chunk : loadedChunks) {
                ForgeChunkManager.unforceChunk(chunkLoaderTicket, chunk);
            }
            loadedChunks.clear();
            ChunkCoordIntPair[] neighboringChunks = {
                new ChunkCoordIntPair(chunkX, chunkZ),
                new ChunkCoordIntPair(chunkX + 1, chunkZ),
                new ChunkCoordIntPair(chunkX - 1, chunkZ),
                new ChunkCoordIntPair(chunkX, chunkZ + 1),
                new ChunkCoordIntPair(chunkX, chunkZ - 1),
                new ChunkCoordIntPair(chunkX + 1, chunkZ + 1),
                new ChunkCoordIntPair(chunkX - 1, chunkZ - 1),
                new ChunkCoordIntPair(chunkX + 1, chunkZ - 1),
                new ChunkCoordIntPair(chunkX - 1, chunkZ + 1)
            };
            for (ChunkCoordIntPair chunk : neighboringChunks) {
                loadedChunks.add(chunk);
                ForgeChunkManager.forceChunk(chunkLoaderTicket, chunk);
            }
        }
    }

    public void loadChunksInBulletPath(int currentChunkX, int currentChunkZ, double motionX, double motionZ) {
        if (!worldObj.isRemote && chunkLoaderTicket != null) {
            for (ChunkCoordIntPair chunk : loadedChunks) {
                ForgeChunkManager.unforceChunk(chunkLoaderTicket, chunk);
            }
            loadedChunks.clear();
            int nextChunkX = currentChunkX + (motionX > 0 ? 1 : (motionX < 0 ? -1 : 0));
            int nextChunkZ = currentChunkZ + (motionZ > 0 ? 1 : (motionZ < 0 ? -1 : 0));
            ChunkCoordIntPair[] chunksToLoad = {
                new ChunkCoordIntPair(currentChunkX, currentChunkZ),
                new ChunkCoordIntPair(nextChunkX, currentChunkZ),
                new ChunkCoordIntPair(currentChunkX, nextChunkZ),
                new ChunkCoordIntPair(nextChunkX, nextChunkZ)
            };
            for (ChunkCoordIntPair chunk : chunksToLoad) {
                if (!loadedChunks.contains(chunk)) {
                    loadedChunks.add(chunk);
                    ForgeChunkManager.forceChunk(chunkLoaderTicket, chunk);
                }
            }
        }
    }

    private void clearChunkLoaders() {
        for (ChunkCoordIntPair chunk : loadedChunks) {
            ForgeChunkManager.unforceChunk(chunkLoaderTicket, chunk);
        }
    }

    private static boolean isBFMCCoreLoaded() {
        if (bfmcCoreChecked) return bfmcCoreDetected;
        bfmcCoreChecked = true;
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
            Object plugin = pluginManager.getClass().getMethod("getPlugin", String.class).invoke(pluginManager, "BFMCCore");
            bfmcCoreDetected = plugin != null;
        } catch (Exception e) {
            bfmcCoreDetected = false;
        }
        return bfmcCoreDetected;
    }

    public void loadChunksDirectly(int chunkX, int chunkZ, double motionX, double motionZ) {
        if (worldObj.isRemote) return;

        int loaded = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                worldObj.getChunkFromChunkCoords(chunkX + dx, chunkZ + dz);
                loaded++;
            }
        }
        if (debugChunkLoading && this.ticksExisted % 20 == 0) {
            writeDebugLog("[MCH_Debug] Missile #" + this.getEntityId() + " type=" + this.getName()
                + " pos=" + (int)posX + "," + (int)posY + "," + (int)posZ
                + " chunk=" + chunkX + "," + chunkZ
                + " loaded25=" + loaded
                + " tick=" + this.ticksExisted);
        }
    }

    public void setLocationAndAngles(double par1, double par3, double par5, float par7, float par8) {
        super.setLocationAndAngles(par1, par3, par5, par7, par8);
        this.prevPosX2 = par1;
        this.prevPosY2 = par3;
        this.prevPosZ2 = par5;
    }

    protected void entityInit() {
        this.getDataWatcher().addObject(27, 0);
        this.getDataWatcher().addObject(29, "");
        this.getDataWatcher().addObject(30, "");
        this.getDataWatcher().addObject(31, (byte) 0);
        this.getDataWatcher().addObject(DATAWT_DATALINK_FLAGS, (byte) 0);
    }

    public void setAirburstDist(int airburstDist) {
        this.airburstDist = airburstDist;
    }

    public String getName() {
        return this.getDataWatcher().getWatchableObjectString(29);
    }

    public void setInfoByName(String s) {
        if (s != null && !s.isEmpty()) {
            this.weaponInfo = MCH_WeaponInfoManager.get(s);
            if (this.weaponInfo != null) {
                if (!super.worldObj.isRemote) {
                    this.getDataWatcher().updateObject(29, s);
                }
                this.onSetWeaponInfo();
            }
        }

    }

    public void setInfo(String s, MCH_WeaponInfo info) {
        if (info != null) {
            this.weaponInfo = info;
            if (!super.worldObj.isRemote) {
                this.getDataWatcher().updateObject(29, s);
            }
            this.onSetWeaponInfo();
        }

    }

    public MCH_WeaponInfo getInfo() {
        return this.weaponInfo;
    }

    public void onSetWeaponInfo() {
        if (!super.worldObj.isRemote) {
            this.isBomblet = 0;
        }
        this.aheadTriggered = false;

        if (this.getInfo().bomblet > 0) {
            this.sprinkleTime = this.getInfo().bombletSTime;
        }

        this.nameOnRWR = this.getInfo().nameOnRWR;
        this.piercing = this.getInfo().piercing;

        if (this instanceof MCH_EntityBullet) {
            if (this.getInfo().acceleration > 4.0F) {
                this.accelerationFactor = this.getInfo().acceleration / 4.0F;
            }
        } else if (this instanceof MCH_EntityRocket && this.isBomblet == 0 && this.getInfo().acceleration > 4.0F) {
            this.accelerationFactor = this.getInfo().acceleration / 4.0F;
        }
        if (getInfo() != null && getInfo().enableChunkLoader) {
            if (!worldObj.isRemote && isBFMCCoreLoaded()) {
                useDirectChunkLoading = true;
                if (debugChunkLoading) {
                    writeDebugLog("[MCH_Debug] Missile #" + this.getEntityId() + " SPAWN type=" + this.getName()
                        + " pos=" + (int)posX + "," + (int)posY + "," + (int)posZ
                        + " mode=DIRECT_CHUNK_LOADING");
                }
            } else {
                init(ForgeChunkManager.requestTicket(MCH_MOD.instance, worldObj, ForgeChunkManager.Type.ENTITY));
            }
        }
    }

    public void setDead() {
        if (!this.missileWatchDeathLogged
            && this.worldObj != null
            && !this.worldObj.isRemote
            && this instanceof MCH_IMissile
            && MCH_RadarDebug.isMissileWatchEnabled()) {
            String reason = this.missileDeathReasonHint != null && this.missileDeathReasonHint.length() > 0
                ? this.missileDeathReasonHint
                : "SET_DEAD_UNSPECIFIED";
            this.appendMissileWatch("death", reason);
            if ("SET_DEAD_UNSPECIFIED".equals(reason) && MCH_RadarDebug.isEnabled()) {
                StackTraceElement[] st = Thread.currentThread().getStackTrace();
                String caller = st.length > 2 ? st[2].toString() : "unknown";
                MCH_RadarDebug.trace(this.worldObj, this,
                    "msl_death_unspecified type=%s msl=%d caller=%s",
                    this.getClass().getSimpleName(), this.getEntityId(), caller);
            }
        }
        super.setDead();
    }

    public void setBomblet() {
        this.isBomblet = 1;
        this.sprinkleTime = 0;
        super.dataWatcher.updateObject(31, (byte) 1);
    }

    public byte getBomblet() {
        return super.dataWatcher.getWatchableObjectByte(31);
    }

    public void setSnapshotTarget(int targetId, double px, double py, double pz, double vx, double vy, double vz) {
        this.snapshotTargetId = targetId;
        this.snapshotPosX = px;
        this.snapshotPosY = py;
        this.snapshotPosZ = pz;
        this.snapshotVelX = vx;
        this.snapshotVelY = vy;
        this.snapshotVelZ = vz;
        this.snapshotLastUpdate = System.currentTimeMillis();
    }

    public int getSnapshotTargetId() {
        return this.snapshotTargetId;
    }

    public boolean isSnapshotTargetUsable(long staleMs) {
        return this.snapshotTargetId > 0 && (System.currentTimeMillis() - this.snapshotLastUpdate < staleMs);
    }

    public void setTargetEntity(Entity entity) {
        this.targetEntity = entity;
        if (entity != null) {
            this.lastTargetPosX = entity.posX;
            this.lastTargetPosY = entity.posY;
            this.lastTargetPosZ = entity.posZ;
            this.lastTargetVelX = entity.motionX;
            this.lastTargetVelY = entity.motionY;
            this.lastTargetVelZ = entity.motionZ;
            this.hasLastKnownTarget = true;
        }
        if (entity == null) {
            this.setActiveRadarCaptured(false);
        }
        if (!super.worldObj.isRemote) {
            if (entity != null) {
                this.getDataWatcher().updateObject(27, W_Entity.getEntityId(entity));
            } else {
                this.getDataWatcher().updateObject(27, 0);
            }
        }

    }

    public void clientSetTargetEntity(Entity entity) {
        if (super.worldObj.isRemote) {
            this.targetEntity = entity;
            if (entity != null) {
                MCH_MOD.getPacketHandler().sendToServer(new PacketLockTarget(entity.getEntityId(), this.getEntityId()));
            } else {
                MCH_MOD.getPacketHandler().sendToServer(new PacketLockTarget(0, this.getEntityId()));
            }
        }

    }

    public int getTargetEntityID() {
        return this.targetEntity != null ? W_Entity.getEntityId(this.targetEntity) : this.getDataWatcher().getWatchableObjectInt(27);
    }

    private void syncDataLinkFlags() {
        if (!super.worldObj.isRemote) {
            byte flags = 0;
            if (this.dataLinkRelayMode) {
                flags |= 1;
            }
            if (this.activeRadarCaptured) {
                flags |= 2;
            }
            if (this.dataLinkTwsSelectedOnly) {
                flags |= 4;
            }
            this.getDataWatcher().updateObject(DATAWT_DATALINK_FLAGS, flags);
        }
    }

    public void setDataLinkRelayMode(boolean v) {
        this.dataLinkRelayMode = v;
        if (v) {
            this.dataLinkRelayEverEnabled = true;
            this.activeRadarCaptured = false;
            if (getInfo() != null && getInfo().isHeatSeekerMissile && !getInfo().activeRadar && !getInfo().passiveRadar && !getInfo().semiActiveRadar) {
                this.heatSeekerDatalinkMode = true;
            }
        }
        syncDataLinkFlags();
    }

    public boolean isDataLinkRelayMode() {
        if (super.worldObj.isRemote) {
            return (this.getDataWatcher().getWatchableObjectByte(DATAWT_DATALINK_FLAGS) & 1) != 0;
        }
        return this.dataLinkRelayMode;
    }

    public boolean wasDataLinkRelayEverEnabled() {
        return this.dataLinkRelayEverEnabled;
    }

    public void setActiveRadarCaptured(boolean v) {
        this.activeRadarCaptured = v;
        syncDataLinkFlags();
    }

    public boolean isActiveRadarCaptured() {
        if (super.worldObj.isRemote) {
            return (this.getDataWatcher().getWatchableObjectByte(DATAWT_DATALINK_FLAGS) & 2) != 0;
        }
        return this.activeRadarCaptured;
    }

    protected boolean isDataLinkActiveRadarDelayPhase() {
        return this.getInfo() != null
            && this.getInfo().activeRadar
            && this.isDataLinkRelayMode()
            && this.ticksExisted < DATALINK_ACTIVE_RADAR_DELAY_TICK;
    }

    protected boolean isDataLinkRelaySourceMaintained() {
        if (this.getInfo() == null || !this.isDataLinkRelayMode()) {
            return true;
        }
        if (!(this.getInfo().passiveRadar || this.getInfo().semiActiveRadar)) {
            return true;
        }
        if (this.shootingAircraft == null || this.targetEntity == null || this.targetEntity.isDead) {
            return false;
        }
        if (this.worldObj == null || MCH_MOD.rwrThreatManager == null) {
            return true;
        }
        return MCH_MOD.rwrThreatManager.isEmitterTrackingTarget(
            this.shootingAircraft.getEntityId(),
            this.targetEntity.getEntityId(),
            MCH_MOD.rwrThreatManager.getCurrentTick());
    }

    public void setDataLinkTwsSelectedOnly(boolean v) {
        this.dataLinkTwsSelectedOnly = v;
        syncDataLinkFlags();
    }

    public boolean isDataLinkTwsSelectedOnly() {
        if (super.worldObj.isRemote) {
            return (this.getDataWatcher().getWatchableObjectByte(DATAWT_DATALINK_FLAGS) & 4) != 0;
        }
        return this.dataLinkTwsSelectedOnly;
    }

    public MCH_BulletModel getBulletModel() {
        if (this.getInfo() == null) {
            return null;
        } else if (this.isBomblet < 0) {
            return null;
        } else {
            MCH_BulletModel nextModel;
            if (this.isBomblet == 1) {
                nextModel = this.getInfo().bombletModel;
            } else {
                boolean useEndModel = this.getInfo().bulletModelEndTick >= 0
                    && this.getCountOnUpdate() >= this.getInfo().bulletModelEndTick
                    && this.getInfo().bulletModelEnd != null;
                nextModel = useEndModel ? this.getInfo().bulletModelEnd : this.getInfo().bulletModel;
            }

            if (nextModel == null) {
                nextModel = this.getDefaultBulletModel();
            }
            if (this.model != nextModel) {
                this.model = nextModel;
            }

            return this.model;
        }
    }

    public boolean isWithinTrajectoryParticleEndTick() {
        MCH_WeaponInfo info = this.getInfo();
        return info != null && (info.trajectoryParticleEndTick < 0 || this.getCountOnUpdate() <= info.trajectoryParticleEndTick);
    }

    public abstract MCH_BulletModel getDefaultBulletModel();

    public void sprinkleBomblet() {
    }

    public void spawnExplosionParticle(String name, int num, float size) {
        if (super.worldObj.isRemote) {
            if (name.isEmpty() || num < 1 || num > 50) {
                return;
            }

            double x = (super.posX - super.prevPosX) / (double) num;
            double y = (super.posY - super.prevPosY) / (double) num;
            double z = (super.posZ - super.prevPosZ) / (double) num;
            double x2 = (super.prevPosX - this.prevPosX2) / (double) num;
            double y2 = (super.prevPosY - this.prevPosY2) / (double) num;
            double z2 = (super.prevPosZ - this.prevPosZ2) / (double) num;
            int i;
            if (name.equals("explode")) {
                for (i = 0; i < num; ++i) {
                    MCH_ParticleParam prm = new MCH_ParticleParam(super.worldObj, "smoke", (super.prevPosX + x * (double) i + this.prevPosX2 + x2 * (double) i) / 2.0D, (super.prevPosY + y * (double) i + this.prevPosY2 + y2 * (double) i) / 2.0D, (super.prevPosZ + z * (double) i + this.prevPosZ2 + z2 * (double) i) / 2.0D);
                    prm.size = size + super.rand.nextFloat();
                    MCH_ParticlesUtil.spawnParticle(prm);
                }
            } else {
                for (i = 0; i < num; ++i) {
                    MCH_ParticlesUtil.DEF_spawnParticle(name, (super.prevPosX + x * (double) i + this.prevPosX2 + x2 * (double) i) / 2.0D, (super.prevPosY + y * (double) i + this.prevPosY2 + y2 * (double) i) / 2.0D, (super.prevPosZ + z * (double) i + this.prevPosZ2 + z2 * (double) i) / 2.0D, 0.0D, 0.0D, 0.0D, 50.0F);
                }
            }
        }

    }

    public void DEF_spawnParticle(String name, int num, float size) {
        if (super.worldObj.isRemote) {
            if (name.isEmpty() || num < 1 || num > 50) {
                return;
            }

            double x = (super.posX - super.prevPosX) / (double) num;
            double y = (super.posY - super.prevPosY) / (double) num;
            double z = (super.posZ - super.prevPosZ) / (double) num;
            double x2 = (super.prevPosX - this.prevPosX2) / (double) num;
            double y2 = (super.prevPosY - this.prevPosY2) / (double) num;
            double z2 = (super.prevPosZ - this.prevPosZ2) / (double) num;

            for (int i = 0; i < num; ++i) {
                MCH_ParticlesUtil.DEF_spawnParticle(name, (super.prevPosX + x * (double) i + this.prevPosX2 + x2 * (double) i) / 2.0D, (super.prevPosY + y * (double) i + this.prevPosY2 + y2 * (double) i) / 2.0D, (super.prevPosZ + z * (double) i + this.prevPosZ2 + z2 * (double) i) / 2.0D, 0.0D, 0.0D, 0.0D, 150.0F);
            }
        }

    }

    public int getCountOnUpdate() {
        return this.countOnUpdate;
    }

    public void clearCountOnUpdate() {
        this.countOnUpdate = 0;
    }

    @SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double par1) {
        double d1 = super.boundingBox.getAverageEdgeLength() * 4.0D;
        d1 *= 64.0D;
        return par1 < d1 * d1;
    }

    public void setParameterFromWeapon(MCH_WeaponBase w, Entity entity, Entity user) {
        this.explosionPower = w.explosionPower;
        this.explosionPowerInWater = w.explosionPowerInWater;
        this.setPower(w.power);
        this.piercing = w.piercing;
        this.shootingAircraft = entity;
        this.shootingEntity = user;
    }

    public void setParameterFromWeapon(Entity entity, Entity user) {
        this.shootingAircraft = entity;
        this.shootingEntity = user;
    }

    public void setParameterFromWeapon(MCH_EntityBaseBullet b, Entity entity, Entity user) {
        this.explosionPower = b.explosionPower;
        this.explosionPowerInWater = b.explosionPowerInWater;
        this.setPower(b.getPower());
        this.piercing = b.piercing;
        this.shootingAircraft = entity;
        this.shootingEntity = user;
    }

    private double calculateAngle(Entity viewer, double x, double y, double z) {
        double dx = x - viewer.posX;
        double dy = y - viewer.posY;
        double dz = z - viewer.posZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1e-6) {
            return 0.0;
        }
        dx /= dist;
        dy /= dist;
        dz /= dist;
        double yawRad = Math.toRadians(viewer.rotationYaw);
        double pitchRad = Math.toRadians(viewer.rotationPitch);
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fy = -Math.sin(pitchRad);
        double fz = Math.cos(yawRad) * Math.cos(pitchRad);
        double fLen = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fLen > 1e-6) {
            fx /= fLen;
            fy /= fLen;
            fz /= fLen;
        }
        double dot = dx * fx + dy * fy + dz * fz;
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.toDegrees(Math.acos(dot));
    }

    private double getCurrentMaxDegreeOfMissile() {
        MCH_WeaponInfo info = getInfo();
        return info != null ? info.getEffectiveMaxDegreeOfMissile(this.ticksExisted) : 0.0D;
    }

    private double getCurrentTurningFactor() {
        MCH_WeaponInfo info = getInfo();
        return info != null ? info.getEffectiveTurningFactor(this.ticksExisted) : 0.0D;
    }

    public void guidanceToPos(double targetPosX, double targetPosY, double targetPosZ) {

        if (getInfo().tickEndHoming > 0 && ticksExisted > getInfo().tickEndHoming) {
            return;
        }

        double tx = targetPosX - this.posX;
        double ty = targetPosY - this.posY;
        double tz = targetPosZ - this.posZ;

        double d = MathHelper.sqrt_double(tx * tx + ty * ty + tz * tz);
        if (d < 1.0E-6D) {
            return;
        }

        double accel = this.acceleration;
        double mx = tx * accel / d;
        double my = ty * accel / d;
        double mz = tz * accel / d;

        Vector3f missileDirection = new Vector3f(this.motionX, this.motionY, this.motionZ);
        Vector3f targetDirection = new Vector3f(tx, ty, tz);
        double angle = Math.abs(Vector3f.angle(missileDirection, targetDirection));
        double maxAllowedAngle = Math.toRadians(this.getCurrentMaxDegreeOfMissile());
        if (angle > maxAllowedAngle) {
            return;
        }
        double turning = this.getCurrentTurningFactor();
        this.motionX = this.motionX + (mx - this.motionX) * turning;
        this.motionY = this.motionY + (my - this.motionY) * turning;
        this.motionZ = this.motionZ + (mz - this.motionZ) * turning;
        double a = Math.atan2(this.motionZ, this.motionX);
        this.rotationYaw = (float) (a * 180.0D / Math.PI) - 90.0F;
        double r = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        this.rotationPitch = -((float) (Math.atan2(this.motionY, r) * 180.0D / Math.PI));
    }

    public boolean shouldUseCruise(double tx, double ty, double tz) {
        if (getInfo() == null || !getInfo().armCruiseEnable) {
            return false;
        }
        double dx = tx - this.posX;
        double dy = ty - this.posY;
        double dz = tz - this.posZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist <= getInfo().armCruiseStartDistance) {
            return false;
        }
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal <= getInfo().armCruiseTerminalRadius && Math.abs(dy) <= getInfo().armCruiseTerminalHeight) {
            return false;
        }
        return true;
    }

    public void guidanceToPosWithCruise(double tx, double ty, double tz) {
        if (shouldUseCruise(tx, ty, tz)) {
            this.guidanceToPos(tx, this.posY, tz);
        } else {
            this.guidanceToPos(tx, ty, tz);
        }
    }

    public void guidanceToTarget(double targetPosX, double targetPosY, double targetPosZ) {
        this.guidanceToTarget(targetPosX, targetPosY, targetPosZ, 1.0F);
    }

    public void guidanceToTarget(double targetPosX, double targetPosY, double targetPosZ, float accelerationFactor) {

        if (getInfo().tickEndHoming > 0 && ticksExisted > getInfo().tickEndHoming) {
            return;
        }

        if (targetEntity == null || targetEntity.isDead) {
            return;
        }

        //如果需要预测目标位置，则根据目标当前速度做一个简单的预测
        if (getInfo().predictTargetPos) {
            double currentDistance = MathHelper.sqrt_double(
                (targetPosX - posX) * (targetPosX - posX)
                    + (targetPosY - posY) * (targetPosY - posY)
                    + (targetPosZ - posZ) * (targetPosZ - posZ)
            );
            double missileSpeed = MathHelper.sqrt_double(
                motionX * motionX + motionY * motionY + motionZ * motionZ
            );
            if (missileSpeed < 0.0001D) {
                missileSpeed = this.acceleration;
            }
            double timeToTarget = currentDistance / missileSpeed;
            double vx = targetEntity.motionX;
            double vy = targetEntity.motionY;
            double vz = targetEntity.motionZ;
            targetPosX += vx * timeToTarget;
            targetPosY += vy * timeToTarget;
            targetPosZ += vz * timeToTarget;
        }

        //计算目标位置与当前实体位置之间的差值
        double tx = targetPosX - this.posX;
        double ty = targetPosY - this.posY;
        double tz = targetPosZ - this.posZ;

        // 计算与目标的距离
        double d = MathHelper.sqrt_double(tx * tx + ty * ty + tz * tz);

        double mx = tx * this.acceleration / d;
        double my = ty * this.acceleration / d;
        double mz = tz * this.acceleration / d;

        Vector3f missileDirection = new Vector3f(this.motionX, this.motionY, this.motionZ);
        Vector3f targetDirection = new Vector3f(tx, ty, tz);
        double angle = Math.abs(Vector3f.angle(missileDirection, targetDirection));
        double maxAllowedAngle = Math.toRadians(this.getCurrentMaxDegreeOfMissile());

        if (angle > maxAllowedAngle && !doingTopAttack) {
            setTargetEntity(null);
            return;
        }

        // 判断目标运动方向与导弹方向的夹角是否大于PD系统的允许阈值
        Vector3f targetVelocity = new Vector3f(
            (float) targetEntity.motionX,
            (float) targetEntity.motionY,
            (float) targetEntity.motionZ
        );
        double velocityAngle = Math.abs(Vector3f.angle(missileDirection, targetVelocity));
        if (velocityAngle > getInfo().pdHDNMaxDegree) {
            setTargetEntity(null);
            return;
        }

        // 如果是空空导弹，且目标在地面上，则解锁
        if (this instanceof MCH_EntityAAMissile
            && MCH_WeaponGuidanceSystem.isEntityOnGround(targetEntity, weaponInfo.lockMinHeight)) {
            setTargetEntity(null);
            return;
        }

        if (getInfo().semiActiveRadar && !this.isDataLinkRelayMode()) {
            Entity viewer = null;
            if (getInfo().enableHMS) {
                if (this.shootingEntity != null) {
                    viewer = this.shootingEntity;
                }
            } else {
                if (this.shootingAircraft != null) {
                    viewer = this.shootingAircraft;
                }
            }
            if (viewer == null) {
                viewer = this;
            }
            double semiAngle = calculateAngle(viewer, targetPosX, targetPosY, targetPosZ);
            if (semiAngle > getInfo().maxLockOnAngle || d > getInfo().maxLockOnRange) {
                setTargetEntity(null);
                return;
            }
        }

        double turning = this.getCurrentTurningFactor();
        this.motionX = this.motionX + (mx - this.motionX) * turning;
        this.motionY = this.motionY + (my - this.motionY) * turning;
        this.motionZ = this.motionZ + (mz - this.motionZ) * turning;

        double a = Math.atan2(this.motionZ, this.motionX);
        this.rotationYaw = (float) (a * 180.0D / Math.PI) - 90.0F;
        double r = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        this.rotationPitch = -((float) (Math.atan2(this.motionY, r) * 180.0D / Math.PI));
    }


    public boolean checkValid() {
        if (this.shootingEntity == null && this.shootingAircraft == null) {
            return false;
        } else if (this.shootingEntity != null && this.shootingEntity.isDead) {
            return false;
        } else {
            if (this.shootingAircraft != null && this.shootingAircraft.isDead) {
                ;
            }

            Entity shooter = this.shootingEntity != null ? this.shootingEntity : this.shootingAircraft;
            double x = super.posX - shooter.posX;
            double z = super.posZ - shooter.posZ;
            return x * x + z * z < 3.38724E7D;
        }
    }

    private boolean shouldLogMissileWatchTick() {
        if (!MCH_RadarDebug.isMissileWatchEnabled()) {
            return false;
        }
        if (this.worldObj == null || this.worldObj.isRemote || !(this instanceof MCH_IMissile)) {
            return false;
        }
        int interval = Math.max(1, MCH_RadarDebug.getMissileWatchIntervalTick());
        return this.ticksExisted % interval == 0;
    }

    private void appendMissileWatch(String phase, String reason) {
        if (this.worldObj == null || this.worldObj.isRemote || !(this instanceof MCH_IMissile)) {
            return;
        }
        if ("death".equalsIgnoreCase(phase)) {
            this.missileWatchDeathLogged = true;
            this.missileDeathReasonHint = reason != null ? reason : "";
        }
        if (!MCH_RadarDebug.isMissileWatchEnabled() && !MCH_RadarDebug.isEnabled()) {
            return;
        }
        Entity shooter = this.shootingEntity != null ? this.shootingEntity : this.shootingAircraft;
        int shooterEntityId = this.shootingEntity != null ? this.shootingEntity.getEntityId() : -1;
        int shooterAircraftId = this.shootingAircraft != null ? this.shootingAircraft.getEntityId() : -1;
        int targetId = this.targetEntity != null ? this.targetEntity.getEntityId() : -1;
        boolean targetDead = this.targetEntity != null && this.targetEntity.isDead;
        double targetDist = -1.0D;
        if (this.targetEntity != null) {
            double tx = this.posX - this.targetEntity.posX;
            double ty = this.posY - this.targetEntity.posY;
            double tz = this.posZ - this.targetEntity.posZ;
            targetDist = Math.sqrt(tx * tx + ty * ty + tz * tz);
        }
        double shooterDist = -1.0D;
        if (shooter != null) {
            double sx = this.posX - shooter.posX;
            double sy = this.posY - shooter.posY;
            double sz = this.posZ - shooter.posZ;
            shooterDist = Math.sqrt(sx * sx + sy * sy + sz * sz);
        }
        boolean chunkLoaded = this.worldObj.blockExists((int)this.posX, (int)this.posY, (int)this.posZ);
        MCH_RadarDebug.appendManual(
            "MSLWATCH phase=%s reason=%s type=%s msl=%d tick=%d age=%d pos=(%.1f,%.1f,%.1f) motion=(%.2f,%.2f,%.2f) target=%d(dead=%s,dist=%.1f) shooterE=%d(dead=%s) shooterA=%d(dead=%s) dShooter=%.1f valid=%s dlRelay=%s captured=%s chunk=%s snapshot=%d(usable=%s) lastKnown=%s",
            phase,
            reason,
            this.getClass().getSimpleName(),
            this.getEntityId(),
            this.worldObj.getTotalWorldTime(),
            this.getCountOnUpdate(),
            this.posX, this.posY, this.posZ,
            this.motionX, this.motionY, this.motionZ,
            targetId, String.valueOf(targetDead), targetDist,
            shooterEntityId, String.valueOf(this.shootingEntity != null && this.shootingEntity.isDead),
            shooterAircraftId, String.valueOf(this.shootingAircraft != null && this.shootingAircraft.isDead),
            shooterDist,
            String.valueOf(this.checkValid()),
            String.valueOf(this.isDataLinkRelayMode()),
            String.valueOf(this.isActiveRadarCaptured()),
            String.valueOf(chunkLoaded),
            this.snapshotTargetId,
            String.valueOf(this.isSnapshotTargetUsable(3000L)),
            String.valueOf(this.hasLastKnownTarget)
        );
    }

    public float getGravity() {
        return this.getInfo() != null ? this.getInfo().gravity : 0.0F;
    }

    public float getGravityInWater() {
        return this.getInfo() != null ? this.getInfo().gravityInWater : 0.0F;
    }

    public void onUpdate() {

        if (!worldObj.isRemote) {
            if (this.shouldLogMissileWatchTick()) {
                this.appendMissileWatch("tick", "-");
            }
            if (shootingAircraft instanceof MCH_EntityAircraft && !speedAddedFromAircraft && getInfo().speedDependsAircraft) {
                MCH_EntityAircraft ac = (MCH_EntityAircraft) shootingAircraft;
                double s = Math.sqrt(ac.motionX * ac.motionX + ac.motionY * ac.motionY + ac.motionZ * ac.motionZ);
                acceleration += s;
                double d = MathHelper.sqrt_double(motionX * motionX + motionY * motionY + motionZ * motionZ);
                super.motionX = motionX * acceleration / d;
                super.motionY = motionY * acceleration / d;
                super.motionZ = motionZ * acceleration / d;
                speedAddedFromAircraft = true;
            }
        }

        if (getInfo() != null && getInfo().enableChunkLoader) {
            if (useDirectChunkLoading) {
                int cx = MathHelper.floor_double(posX) >> 4;
                int cz = MathHelper.floor_double(posZ) >> 4;
                loadChunksDirectly(cx, cz, motionX, motionZ);
            } else {
                checkAndLoadChunks();
            }
        }

        //更新锁定的目标
        if (super.worldObj.isRemote && this.countOnUpdate == 0) {
            int f3 = this.getTargetEntityID();
            if (f3 > 0) {
                this.setTargetEntity(super.worldObj.getEntityByID(f3));
            }
        }

        //抗干扰
        if (!worldObj.isRemote && antiFlareUse) {
            if (antiFlareTick > 0) {
                antiFlareTick--;
            } else {
                setTargetEntity(null);
                antiFlareUse = false;
            }
        }


        if (this.prevMotionX != super.motionX || this.prevMotionY != super.motionY || this.prevMotionZ != super.motionZ) {
            double var5 = (double) ((float) Math.atan2(super.motionZ, super.motionX));
            super.rotationYaw = (float) (var5 * 180.0D / 3.141592653589793D) - 90.0F;
            double r = Math.sqrt(super.motionX * super.motionX + super.motionZ * super.motionZ);
            super.rotationPitch = -((float) (Math.atan2(super.motionY, r) * 180.0D / 3.141592653589793D));
        }

        this.prevMotionX = super.motionX;
        this.prevMotionY = super.motionY;
        this.prevMotionZ = super.motionZ;
        ++this.countOnUpdate;
        if (this.countOnUpdate > 10000000) {
            this.clearCountOnUpdate();
        }

        this.prevPosX2 = super.prevPosX;
        this.prevPosY2 = super.prevPosY;
        this.prevPosZ2 = super.prevPosZ;
        super.onUpdate();
        if (this.getInfo() == null) {
            if (this.countOnUpdate >= 2) {
                MCH_Lib.Log((Entity) this, "##### MCH_EntityBaseBullet onUpdate() Weapon info null %d, %s, Name=%s", new Object[]{Integer.valueOf(W_Entity.getEntityId(this)), this.getEntityName(), this.getName()});
                this.appendMissileWatch("death", "WEAPON_INFO_NULL");
                this.setDead();
                return;
            }

            this.setInfoByName(this.getName());
            if (this.getInfo() == null) {
                return;
            }
        }

        if (super.worldObj.isRemote && this.isBomblet < 0) {
            this.isBomblet = this.getBomblet();
        }

        if (!super.worldObj.isRemote) {
            if ((int) super.posY <= 255 && !super.worldObj.blockExists((int) super.posX, (int) super.posY, (int) super.posZ)) {
                if (this.getInfo().delayFuse <= 0) {
                    this.appendMissileWatch("death", "OUT_OF_LOADED_CHUNK");
                    this.setDead();
                    return;
                }

                if (this.delayFuse == 0) {
                    this.delayFuse = this.getInfo().delayFuse;
                }
            }

            if (this.delayFuse > 0) {
                if (this.delayFuseMarkerActive) {
                    super.motionX = 0.0D;
                    super.motionY = 0.0D;
                    super.motionZ = 0.0D;
                }
                --this.delayFuse;
                if (this.delayFuse == 0) {
                    this.onUpdateTimeout();
                    this.appendMissileWatch("death", "DELAY_FUSE_TIMEOUT");
                    this.setDead();
                    return;
                }
            }

            if (!this.checkValid()) {
                this.appendMissileWatch("death", "CHECK_VALID_FAIL");
                if (MCH_RadarDebug.isEnabled()) {
                    int shooterEntityId = this.shootingEntity != null ? this.shootingEntity.getEntityId() : -1;
                    int shooterAircraftId = this.shootingAircraft != null ? this.shootingAircraft.getEntityId() : -1;
                    boolean shooterEntityDead = this.shootingEntity != null && this.shootingEntity.isDead;
                    boolean shooterAircraftDead = this.shootingAircraft != null && this.shootingAircraft.isDead;
                    Entity shooter = this.shootingEntity != null ? this.shootingEntity : this.shootingAircraft;
                    double hDistSq = shooter != null ? (this.posX - shooter.posX) * (this.posX - shooter.posX) + (this.posZ - shooter.posZ) * (this.posZ - shooter.posZ) : -1.0D;
                    MCH_RadarDebug.trace(this.worldObj, this,
                        "msl_death type=%s reason=CHECK_VALID_FAIL msl=%d shooterEntity=%d(death=%s) shooterAircraft=%d(death=%s) hDistSq=%.1f limitSq=33872400.0 pos=(%.1f,%.1f,%.1f)",
                        this.getClass().getSimpleName(),
                        this.getEntityId(),
                        shooterEntityId, String.valueOf(shooterEntityDead),
                        shooterAircraftId, String.valueOf(shooterAircraftDead),
                        hDistSq,
                        this.posX, this.posY, this.posZ);
                }
                this.setDead();
                return;
            }

            if (this.getInfo().timeFuse > 0 && this.getCountOnUpdate() > this.getInfo().timeFuse) {
                this.onUpdateTimeout();
                this.appendMissileWatch("death", "TIME_FUSE_TIMEOUT");
                this.setDead();
                return;
            }

            if (this.getInfo().explosionAltitude > 0 && MCH_Lib.getBlockIdY(this, 3, -this.getInfo().explosionAltitude) != 0) {
                MovingObjectPosition var6 = new MovingObjectPosition((int) super.posX, (int) super.posY, (int) super.posZ, 0, Vec3.createVectorHelper(super.posX, super.posY, super.posZ));
                this.onImpact(var6, 1.0F);
            }
        }

        if (!this.isInWater()) {

            double currentSpeed = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
            if (currentSpeed == 0) currentSpeed = 0.000001F;

            double dirX = motionX / currentSpeed;
            double dirY = motionY / currentSpeed;
            double dirZ = motionZ / currentSpeed;

            if (ticksExisted > getInfo().speedFactorStartTick
                && ticksExisted < getInfo().speedFactorEndTick) {
                super.motionX += dirX * getInfo().speedFactor;
                super.motionY += dirY * getInfo().speedFactor;
                super.motionZ += dirZ * getInfo().speedFactor;
                acceleration += getInfo().speedFactor;
            }

            super.motionY += this.getGravity();
            super.motionX -= dirX * getInfo().dragInAir;
            super.motionZ -= dirZ * getInfo().dragInAir;
        } else {
            super.motionY += this.getGravityInWater();
        }


        if (!super.isDead) {
            onUpdateCollided();
            onUpdateAirburst();
            onUpdateProximityFuse();
        }

        super.posX += super.motionX * this.accelerationFactor;
        super.posY += super.motionY * this.accelerationFactor;
        super.posZ += super.motionZ * this.accelerationFactor;
        if (super.worldObj.isRemote) {
            this.updateSplash();
        }

        if (this.isInWater()) {
            float var7 = 0.25F;
            super.worldObj.spawnParticle("bubble", super.posX - super.motionX * (double) var7, super.posY - super.motionY * (double) var7, super.posZ - super.motionZ * (double) var7, super.motionX, super.motionY, super.motionZ);
        }

        this.setPosition(super.posX, super.posY, super.posZ);

        onUpdateSpreader();
    }

    private void onUpdateAirburst() {

        int abDist = this.airburstDist;
        if (this.airburstTriggered || abDist <= 5 || abDist >= 3000) {
            return;
        }

        double targetDist = (double) abDist + 3.0D;

        double dx = this.motionX * this.accelerationFactor;
        double dy = this.motionY * this.accelerationFactor;
        double dz = this.motionZ * this.accelerationFactor;
        double segLen = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double newTravel = this.airburstTravelled + segLen;

        if (segLen > 0.0D && newTravel >= targetDist) {
            double remain = targetDist - this.airburstTravelled;
            double t = remain / segLen;
            double ex = this.posX + dx * t;
            double ey = this.posY + dy * t;
            double ez = this.posZ + dz * t;

            if (!this.worldObj.isRemote) {
                if (this.getInfo().ahead) {
                    if (this.getInfo().proximityFuseTick < 0 || this.ticksExisted > this.getInfo().proximityFuseTick) {
                        this.aheadTriggered = true;
                    }
                    this.airburstTriggered = true;
                    this.airburstTravelled = 0.0D;
                    return;
                }
                if (this.getInfo().explosion > 0) {
                    this.newExplosion(ex, ey, ez, getInfo().explosionAirburst,
                        (float) this.getInfo().explosionBlock, false);
                } else if (this.explosionPower < 0) {
                    this.playExplosionSound();
                }

                if (this.getInfo() != null && this.getInfo().enableChunkLoader) {
                    this.clearChunkLoaders();
                }

                if (this.getInfo() != null) {
                    PacketPlaySound.sendSoundPacket(
                        ex, ey, ez, this.getInfo().hitSoundRange, this.dimension,
                        this.getInfo().hitSound, true);
                }

                this.setDead();
            }

            this.airburstTriggered = true;
            this.airburstTravelled = 0.0D;
        } else {
            this.airburstTravelled = newTravel;
        }
    }

    public void updateSplash() {
        if (this.getInfo() != null) {
            if (this.getInfo().power > 0) {
                if (!W_WorldFunc.isBlockWater(super.worldObj, (int) (super.prevPosX + 0.5D), (int) (super.prevPosY + 0.5D), (int) (super.prevPosZ + 0.5D)) && W_WorldFunc.isBlockWater(super.worldObj, (int) (super.posX + 0.5D), (int) (super.posY + 0.5D), (int) (super.posZ + 0.5D))) {
                    double x = super.posX - super.prevPosX;
                    double y = super.posY - super.prevPosY;
                    double z = super.posZ - super.prevPosZ;
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d <= 0.15D) {
                        return;
                    }

                    x /= d;
                    y /= d;
                    z /= d;
                    double px = super.prevPosX;
                    double py = super.prevPosY;
                    double pz = super.prevPosZ;

                    for (int i = 0; (double) i <= d; ++i) {
                        px += x;
                        py += y;
                        pz += z;
                        if (W_WorldFunc.isBlockWater(super.worldObj, (int) (px + 0.5D), (int) (py + 0.5D), (int) (pz + 0.5D))) {
                            float pwr = this.getInfo().power < 20 ? (float) this.getInfo().power : 20.0F;
                            int n = super.rand.nextInt(1 + (int) pwr / 3) + (int) pwr / 2 + 1;
                            pwr *= 0.03F;

                            for (int j = 0; j < n; ++j) {
                                MCH_ParticleParam prm = new MCH_ParticleParam(super.worldObj, "splash", px, py + 0.5D, pz, (double) pwr * (super.rand.nextDouble() - 0.5D) * 0.3D, (double) pwr * (super.rand.nextDouble() * 0.5D + 0.5D) * 1.8D, (double) pwr * (super.rand.nextDouble() - 0.5D) * 0.3D, pwr * 5.0F);
                                MCH_ParticlesUtil.spawnParticle(prm);
                            }

                            return;
                        }
                    }
                }

            }
        }
    }

    public void onUpdateTimeout() {
        if (this.isInWater()) {
            if (this.explosionPowerInWater > 0) {
                this.newExplosion(super.posX, super.posY, super.posZ, (float) this.explosionPowerInWater, (float) this.explosionPowerInWater, true);
            }
        } else if (this.explosionPower > 0) {
            this.newExplosion(super.posX, super.posY, super.posZ, (float) this.explosionPower, (float) this.getInfo().explosionBlock, false);
        } else if (this.explosionPower < 0) {
            this.playExplosionSound();
        }

    }

    public void onUpdateBomblet() {
        if (!super.worldObj.isRemote && this.sprinkleTime > 0 && !super.isDead) {
            --this.sprinkleTime;
            if (this.sprinkleTime == 0) {
                for (int i = 0; i < this.getInfo().bomblet; ++i) {
                    this.sprinkleBomblet();
                }

                this.setDead();
            }
        }

    }

    public void boundBullet(int sideHit) {
        switch (sideHit) {
            case 0:
                if (super.motionY > 0.0D) {
                    super.motionY = -super.motionY * (double) this.getInfo().bound;
                }
                break;
            case 1:
                if (super.motionY < 0.0D) {
                    super.motionY = -super.motionY * (double) this.getInfo().bound;
                }
                break;
            case 2:
                if (super.motionZ > 0.0D) {
                    super.motionZ = -super.motionZ * (double) this.getInfo().bound;
                } else {
                    super.posZ += super.motionZ;
                }
                break;
            case 3:
                if (super.motionZ < 0.0D) {
                    super.motionZ = -super.motionZ * (double) this.getInfo().bound;
                } else {
                    super.posZ += super.motionZ;
                }
                break;
            case 4:
                if (super.motionX > 0.0D) {
                    super.motionX = -super.motionX * (double) this.getInfo().bound;
                } else {
                    super.posX += super.motionX;
                }
                break;
            case 5:
                if (super.motionX < 0.0D) {
                    super.motionX = -super.motionX * (double) this.getInfo().bound;
                } else {
                    super.posX += super.motionX;
                }
        }

    }

    protected void onUpdateCollided() {
        float damageFactor = 1.0F;
        double mx = super.motionX * this.accelerationFactor;
        double my = super.motionY * this.accelerationFactor;
        double mz = super.motionZ * this.accelerationFactor;
        MovingObjectPosition m = null;

        Vec3 src;
        Vec3 dir;
        for (int entity = 0; entity < 5; ++entity) {
            src = W_WorldFunc.getWorldVec3(super.worldObj, super.posX, super.posY, super.posZ);
            dir = W_WorldFunc.getWorldVec3(super.worldObj, super.posX + mx, super.posY + my, super.posZ + mz);
            m = W_WorldFunc.clip(super.worldObj, src, dir);
            boolean list = false;
            if (this.shootingEntity != null && W_MovingObjectPosition.isHitTypeTile(m)) {
                Block d0 = W_WorldFunc.getBlock(super.worldObj, m.blockX, m.blockY, m.blockZ);
                if (MCH_Config.bulletBreakableBlocks.contains(d0)) {
                    W_WorldFunc.destroyBlock(super.worldObj, m.blockX, m.blockY, m.blockZ, true);
                    list = true;
                }
            }

            if (!list) {
                break;
            }
        }

        src = W_WorldFunc.getWorldVec3(super.worldObj, super.posX, super.posY, super.posZ);
        dir = W_WorldFunc.getWorldVec3(super.worldObj, super.posX + mx, super.posY + my, super.posZ + mz);
        if (this.getInfo().delayFuse > 0) {
            if (m != null) {
                this.boundBullet(m.sideHit);
                if (this.delayFuse == 0) {
                    this.delayFuse = this.getInfo().delayFuse;
                    if (isDelayFuseMarkerTarget()) {
                        this.delayFuseMarkerActive = true;
                        this.delayFuseMarkerX = m.hitVec.xCoord;
                        this.delayFuseMarkerY = m.hitVec.yCoord;
                        this.delayFuseMarkerZ = m.hitVec.zCoord;
                        super.posX = this.delayFuseMarkerX;
                        super.posY = this.delayFuseMarkerY;
                        super.posZ = this.delayFuseMarkerZ;
                        super.motionX = 0.0D;
                        super.motionY = 0.0D;
                        super.motionZ = 0.0D;
                    }
                }
            }

        } else {
            if (m != null) {
                dir = W_WorldFunc.getWorldVec3(super.worldObj, m.hitVec.xCoord, m.hitVec.yCoord, m.hitVec.zCoord);
            }

            Entity hitEntity = null;
            List entities = super.worldObj.getEntitiesWithinAABBExcludingEntity(this, super.boundingBox.addCoord(mx, my, mz).expand(96.0D, 96.0D, 96.0D));
            double d2 = 0.0D;
            MovingObjectPosition result = m;
            for (Object o : entities) {
                Entity entity = (Entity) o;
                if (this.canBeCollidedEntity(entity) && shootingAircraft != o) {
                    float f = 0.3F;
                    MovingObjectPosition movingObjectPosition = entity.boundingBox.expand(f, f, f).calculateIntercept(src, dir);
                    if (movingObjectPosition != null) {
                        double d1 = src.distanceTo(movingObjectPosition.hitVec);
                        if (d1 < d2 || d2 == 0.0D) {
                            hitEntity = entity;
                            d2 = d1;
                            result = movingObjectPosition;
                        }
                    }
                }
            }

            if (result != null) {
                dir = Vec3.createVectorHelper(result.hitVec.xCoord - this.posX, result.hitVec.yCoord - this.posY, result.hitVec.zCoord - this.posZ);
                double d = 1.0;
                if (mx != 0.0) {
                    d = dir.xCoord / mx;
                } else if (my != 0.0) {
                    d = dir.yCoord / my;
                } else if (mz != 0.0) {
                    d = dir.zCoord / mz;
                }
                if (d < 0.0) {
                    d = -d;
                }

                Vec3 newHitVec = Vec3.createVectorHelper(posX + mx * d, posY + my * d, posZ + mz * d);
                if (hitEntity != null) {
                    this.onImpact(new MovingObjectPosition(hitEntity, newHitVec), damageFactor);
                } else {
                    this.onImpact(result, damageFactor);
                }
            }
        }
    }

    private boolean isDelayFuseMarkerTarget() {
        MCH_WeaponInfo info = this.getInfo();
        if (info == null || info.type == null) {
            return false;
        }
        return info.type.equalsIgnoreCase("bomb") || info.type.equalsIgnoreCase("rocket");
    }

    public boolean hasDelayFuseMarker() {
        return this.delayFuseMarkerActive && this.delayFuse > 0;
    }

    public double getDelayFuseMarkerX() {
        return this.delayFuseMarkerX;
    }

    public double getDelayFuseMarkerY() {
        return this.delayFuseMarkerY;
    }

    public double getDelayFuseMarkerZ() {
        return this.delayFuseMarkerZ;
    }

    public boolean canBeCollidedEntity(Entity entity) {
        if (entity instanceof MCH_EntityChain) {
            return false;
        } else if (!entity.canBeCollidedWith()) {
            return false;
        } else {
            if (entity instanceof MCH_EntityBaseBullet) {
                if (super.worldObj.isRemote) {
                    return false;
                }

                MCH_EntityBaseBullet i$ = (MCH_EntityBaseBullet) entity;
                boolean allowFriendlyMissileCollision = isSelfMissileCollisionPair(i$);
                if (!allowFriendlyMissileCollision && W_Entity.isEqual(i$.shootingAircraft, this.shootingAircraft)) {
                    return false;
                }

                if (!allowFriendlyMissileCollision && W_Entity.isEqual(i$.shootingEntity, this.shootingEntity)) {
                    return false;
                }
            }

            if (entity instanceof MCH_EntitySeat) {
                return false;
            } else if (entity instanceof MCH_EntityHitBox) {
                return false;
            } else if (W_Entity.isEqual(entity, this.shootingEntity)) {
                return false;
            } else {
                if (this.shootingAircraft instanceof MCH_EntityAircraft) {
                    if (W_Entity.isEqual(entity, this.shootingAircraft)) {
                        return false;
                    }

                    if (((MCH_EntityAircraft) this.shootingAircraft).isMountedEntity(entity)) {
                        return false;
                    }
                }

                MCH_Config var10000 = MCH_MOD.config;
                Iterator i$1 = MCH_Config.IgnoreBulletHitList.iterator();

                String s;
                do {
                    if (!i$1.hasNext()) {
                        return true;
                    }

                    s = (String) i$1.next();
                } while (!entity.getClass().getName().toLowerCase().contains(s.toLowerCase()));

                return false;
            }
        }
    }

    private boolean isSelfMissileCollisionPair(Entity entity) {
        if (!(entity instanceof MCH_EntityBaseBullet)) {
            return false;
        }
        return isFriendlyCollidableMissile(this) && isFriendlyCollidableMissile((MCH_EntityBaseBullet) entity);
    }

    private static boolean isFriendlyCollidableMissile(MCH_EntityBaseBullet bullet) {
        return bullet instanceof MCH_EntityAAMissile
            || bullet instanceof MCH_EntityATMissile
            || bullet instanceof MCH_EntityTvMissile
            || bullet instanceof MCH_EntityASMissile;
    }

    private EntityLivingBase resolveArmSeekerTeamEntity() {
        if (this.shootingEntity instanceof EntityLivingBase) {
            return (EntityLivingBase) this.shootingEntity;
        }
        if (this.shootingAircraft instanceof MCH_EntityAircraft) {
            MCH_EntityAircraft aircraft = (MCH_EntityAircraft) this.shootingAircraft;
            for (int i = 0; i <= aircraft.getSeatNum(); ++i) {
                Entity seatEntity = aircraft.getEntityBySeatId(i);
                if (seatEntity instanceof EntityLivingBase) {
                    return (EntityLivingBase) seatEntity;
                }
            }
        }
        return null;
    }

    private boolean isFriendlyArmEmitterSource(MCH_EntityAircraft emitter) {
        if (emitter == null) {
            return false;
        }
        EntityLivingBase seeker = resolveArmSeekerTeamEntity();
        if (seeker == null || seeker.getTeam() == null) {
            return false;
        }
        for (int i = 0; i <= emitter.getSeatNum(); ++i) {
            Entity seatEntity = emitter.getEntityBySeatId(i);
            if (!(seatEntity instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase occupant = (EntityLivingBase) seatEntity;
            if (occupant.getTeam() == null) {
                continue;
            }
            // Friendly shielding includes player-crewed and gunner-crewed radar platforms.
            if (seeker.isOnSameTeam(occupant) && (occupant instanceof EntityPlayer || occupant instanceof MCH_EntityGunner)) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasArmEmitterCrew(MCH_EntityAircraft emitter) {
        if (emitter == null) {
            return false;
        }
        for (int i = 0; i <= emitter.getSeatNum(); ++i) {
            Entity seatEntity = emitter.getEntityBySeatId(i);
            if (seatEntity instanceof EntityPlayer || seatEntity instanceof MCH_EntityGunner) {
                return true;
            }
        }
        return false;
    }

    protected boolean isArmEmitterRadiatingSource(MCH_EntityAircraft emitter) {
        if (emitter == null || emitter.getAcInfo() == null || !emitter.getAcInfo().hasRWR) {
            return false;
        }
        if (!hasArmEmitterCrew(emitter)) {
            return false;
        }
        return (emitter.getAcInfo().enableRadar && emitter.isRadarEnabledRuntime()) || emitter.isECMJammerUsing();
    }

    public void notifyHitBullet() {
        if (this.shootingAircraft instanceof MCH_EntityAircraft && W_EntityPlayer.isPlayer(this.shootingEntity)) {
            MCH_PacketNotifyHitBullet.send((MCH_EntityAircraft) this.shootingAircraft, (EntityPlayer) this.shootingEntity);
        }

        if (W_EntityPlayer.isPlayer(this.shootingEntity)) {
            MCH_PacketNotifyHitBullet.send(null, (EntityPlayer) this.shootingEntity);
        }

    }

    protected void onImpact(MovingObjectPosition m, float damageFactor) {
        float p;
        double hitX = 0;
        double hitY = 0;
        double hitZ = 0;
        double dx = 0.00001D;
        double dy = 0.00001D;
        double dz = 0.00001D;
        if (!super.worldObj.isRemote) {
            if (m.entityHit != null) {
                if (m.entityHit instanceof MCH_EntityBaseBullet) {
                    boolean allowFriendlyMissileCollision = isSelfMissileCollisionPair(m.entityHit);
                    if (!allowFriendlyMissileCollision && !this.getInfo().canBeIntercepted) {
                        return;
                    }
                }
                if (m.entityHit instanceof MCH_EntityFlare || m.entityHit instanceof MCH_EntityChaff) {
                    return;
                }

                Vec3 hitVec = Vec3.createVectorHelper(m.hitVec.xCoord, m.hitVec.yCoord, m.hitVec.zCoord);
                if (weaponInfo != null && weaponInfo.enableBulletDecay && initPos != null) {
                    float decayFactor = 1f;
                    float dist = (float) initPos.distanceTo(hitVec);
                    for (MCH_IBulletDecay decay : weaponInfo.bulletDecay) {
                        decayFactor = decay.calculateDecayFactor(dist);
                    }
                    damageFactor *= decayFactor;
                }
                //药水效果
                List<EntityLivingBase> livingList = new ArrayList<>();
                if (m.entityHit instanceof EntityLivingBase) {
                    livingList.add((EntityLivingBase) m.entityHit);
                }
                if (m.entityHit instanceof MCH_EntityAircraft) {
                    MCH_EntityAircraft ac = (MCH_EntityAircraft) m.entityHit;
                    if (ac.riddenByEntity instanceof EntityLivingBase) {
                        livingList.add((EntityLivingBase) ac.riddenByEntity);
                    }
                    if (ac.getSeats() != null) {
                        for (MCH_EntitySeat seat : ac.getSeats()) {
                            if (seat != null && seat.riddenByEntity instanceof EntityLivingBase) {
                                livingList.add((EntityLivingBase) seat.riddenByEntity);
                            }
                        }
                    }
                }
                for (EntityLivingBase livingBase : livingList) {
                    float dist = 0;
                    if (initPos != null) {
                        dist = (float) initPos.distanceTo(hitVec);
                    }
                    for (MCH_PotionEffect effect : getInfo().potionEffect) {
                        if ((effect.startDist < 0 && effect.endDist < 0)
                            || (effect.startDist <= dist && dist < effect.endDist)) {
                            livingBase.addPotionEffect(new PotionEffect(effect.potionEffect));
                        }
                    }
                }
                this.onImpactEntity(m.entityHit, damageFactor, hitVec);
                if (this.armorRicochetActive) {
                    this.armorRicochetActive = false;
                    return;
                }
                this.piercing--;
                hitX = m.hitVec.xCoord + dx;
                hitY = m.hitVec.yCoord + dy;
                hitZ = m.hitVec.zCoord + dz;
            }

            if (m.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                Block d0 = W_WorldFunc.getBlock(super.worldObj, m.blockX, m.blockY, m.blockZ);
                Material mat = d0.getMaterial();
                if (mat == Material.leaves || mat == Material.plants || d0 == Blocks.iron_bars || d0 instanceof BlockDoublePlant) {
                    return;
                }
                if (d0 instanceof BlockGlass || d0 instanceof BlockStainedGlass || d0 instanceof BlockPane || d0 instanceof BlockStainedGlassPane) {
                    return;
                }
                hitX = m.hitVec.xCoord + dx;
                hitY = m.hitVec.yCoord + dy;
                hitZ = m.hitVec.zCoord + dz;
            }
            p = (float) this.explosionPower * damageFactor;
            float i = (float) this.explosionPowerInWater * damageFactor;
            if (this.piercing > 0) {
                --this.piercing;
                if (p > 0.0F) {
                    this.newExplosion(hitX, hitY, hitZ, 1.0F, 1.0F, false);
                }
            } else {
                if (i == 0.0F) {
                    if (this.getInfo().isFAE) {
                        this.newFAExplosion(super.posX, super.posY, super.posZ, p, (float) this.getInfo().explosionBlock);
                    } else if (p > 0.0F) {
                        this.newExplosion(hitX, hitY, hitZ, p, (float) this.getInfo().explosionBlock, false, m.entityHit);
                    } else if (p < 0.0F) {
                        this.playExplosionSound();
                    }
                } else if (m.entityHit != null) {
                    if (this.isInWater()) {
                        this.newExplosion(hitX, hitY, hitZ, i, i, true, m.entityHit);
                    } else {
                        this.newExplosion(hitX, hitY, hitZ, p, (float) this.getInfo().explosionBlock, false, m.entityHit);
                    }
                } else if (!this.isInWater() && !MCH_Lib.isBlockInWater(super.worldObj, m.blockX, m.blockY, m.blockZ)) {
                    if (p > 0.0F) {
                        this.newExplosion(hitX, hitY, hitZ, p, (float) this.getInfo().explosionBlock, false, m.entityHit);
                    } else if (p < 0.0F) {
                        this.playExplosionSound();
                    }
                } else {
                    this.newExplosion(m.blockX, m.blockY, m.blockZ, i, i, true);
                }

                if (getInfo() != null && getInfo().enableChunkLoader) {
                    clearChunkLoaders();
                }

                if (getInfo() != null) {
                    PacketPlaySound.sendSoundPacket(posX, posY, posZ, getInfo().hitSoundRange, dimension, getInfo().hitSound, true);
                }

                this.setDead();
            }
        } else if (this.getInfo() != null) {
//            p = (float)this.getInfo().power;
//            for(int var11 = 0; (float)var11 < p / 3.0F; ++var11) {
//                MCH_ParticlesUtil.spawnParticleTileCrack(super.worldObj,
//                        m.blockX, m.blockY, m.blockZ,
//                        m.hitVec.xCoord + ((double)super.rand.nextFloat() - 0.5D) * (double)p / 10.0D,
//                        m.hitVec.yCoord + 0.1D,
//                        m.hitVec.zCoord + ((double)super.rand.nextFloat() - 0.5D) * (double)p / 10.0D,
//                        -super.motionX * (double)p / 2.0D, (double)(p / 2.0F), -super.motionZ * (double)p / 2.0D);
//            }
            if (m.entityHit == null) {
                spawnBlockPar(m, m.blockX, m.blockY, m.blockZ);
            }
//            if (m.entityHit == null) {
//                worldObj.spawnEntityInWorld(new EntityDebugDot(worldObj, new com.flansmod.common.vector.Vector3f(m.hitVec.xCoord, m.hitVec.yCoord, m.hitVec.zCoord), 100, 1F, 0F, 0F));
//            } else {
//                worldObj.spawnEntityInWorld(new EntityDebugDot(worldObj, new com.flansmod.common.vector.Vector3f(m.hitVec.xCoord, m.hitVec.yCoord, m.hitVec.zCoord), 100, 0F, 1F, 0F));
//            }

            if (m.entityHit instanceof MCH_EntityAircraft) {
                MCH_EntityAircraft ac = (MCH_EntityAircraft) m.entityHit;
                if (ac.ironCurtainRunningTick > 0) {
                    spawnIronCurtainParticle(m, m.blockX, m.blockY, m.blockZ);
                }
            }
        }

    }

    @SideOnly(Side.CLIENT)
    public void spawnIronCurtainParticle(MovingObjectPosition raytraceResult, int xTile, int yTile, int zTile) {
        // 定义暗红色参数（RGB：0.5, 0.1, 0.1）
        final float DARK_RED_R = 0.5f;
        final float DARK_RED_G = 0.1f;
        final float DARK_RED_B = 0.1f;

        int num = getInfo().flakParticlesCrack + rand.nextInt(3);
        float scale = 1.0F;
        for (int i = 0; i < num; i++) {
            EntityDiggingFX fx = new EntityDiggingFX(
                this.worldObj,
                raytraceResult.hitVec.xCoord + (rand.nextFloat() - 0.5D) * width,
                raytraceResult.hitVec.yCoord + 0.1D,
                raytraceResult.hitVec.zCoord + (rand.nextFloat() - 0.5D) * width,
                0, 0, 0,
                worldObj.getBlock(xTile, yTile, zTile),
                this.worldObj.getBlockMetadata(xTile, yTile, zTile)
            );

            // 覆盖原有颜色设置
            fx.setRBGColorF(DARK_RED_R, DARK_RED_G, DARK_RED_B); // 强制设置为暗红色
            fx.multipleParticleScaleBy(scale * 0.8f); // 适当缩小粒子尺寸

            // 调整运动参数
            fx.motionX += getInfo().flakParticlesDiff * (rand.nextGaussian() * 0.5);
            fx.motionZ += getInfo().flakParticlesDiff * (rand.nextGaussian() * 0.5);
            fx.motionY += getInfo().flakParticlesDiff * Math.abs(rand.nextGaussian());

            Minecraft.getMinecraft().effectRenderer.addEffect(fx);
        }

        for (int i = 0; i < 50 + getInfo().flakParticlesDiff; i++) {
            EntityCloudFX obj = new EntityCloudFX(
                worldObj,
                raytraceResult.hitVec.xCoord + (rand.nextFloat() - 0.5D) * width,
                raytraceResult.hitVec.yCoord + rand.nextGaussian() * height,
                raytraceResult.hitVec.zCoord + (rand.nextFloat() - 0.5D) * width,
                0D, 0D, 0D
            ) {
                // 重写渲染方法确保颜色固定
                @Override
                public void renderParticle(Tessellator tessellator, float partialTicks,
                                           float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
                    GL11.glColor4f(DARK_RED_R, DARK_RED_G, DARK_RED_B, 1.0f);
                    super.renderParticle(tessellator, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
                }
            };

            // 设置粒子参数
            obj.setRBGColorF(DARK_RED_R, DARK_RED_G, DARK_RED_B);
            obj.motionX = rand.nextGaussian() / 100; // 增加运动速度
            obj.motionY = rand.nextGaussian() / 100;
            obj.motionZ = rand.nextGaussian() / 100;
            obj.renderDistanceWeight = 350D; // 增加可见距离

            FMLClientHandler.instance().getClient().effectRenderer.addEffect(obj);
        }
    }

    @SideOnly(Side.CLIENT)
    public void spawnBlockPar(MovingObjectPosition raytraceResult, int xTile, int yTile, int zTile) {
        int num = getInfo().flakParticlesCrack + rand.nextInt(3);
        float scale = 1.0F;
        for (int i = 0; i < num; i++) {
            EntityDiggingFX fx = (new EntityDiggingFX(this.worldObj,
                raytraceResult.hitVec.xCoord + (rand.nextFloat() - 0.5D) * width,
                raytraceResult.hitVec.yCoord + 0.1D,
                raytraceResult.hitVec.zCoord + (rand.nextFloat() - 0.5D) * width,
                0, 0, 0, worldObj.getBlock(xTile, yTile, zTile),
                this.worldObj.getBlockMetadata(xTile, yTile, zTile))).applyRenderColor(this.worldObj.getBlockMetadata(xTile, yTile, zTile));
            fx.motionX += getInfo().flakParticlesDiff / 2 * rand.nextGaussian();
            fx.motionZ += getInfo().flakParticlesDiff / 2 * rand.nextGaussian();
            fx.motionY += getInfo().flakParticlesDiff * Math.abs(rand.nextGaussian());
            fx.multipleParticleScaleBy(scale);
            Minecraft.getMinecraft().effectRenderer.addEffect(fx);
        }

        for (int i = 0; i < getInfo().numParticlesFlak; i++) {
            EntityFX obj = new EntityCloudFX(worldObj,
                raytraceResult.hitVec.xCoord + rand.nextGaussian(),
                raytraceResult.hitVec.yCoord + rand.nextGaussian(),
                raytraceResult.hitVec.zCoord + rand.nextGaussian(), 0D, 0D, 0D);
            obj.motionX = rand.nextGaussian() / 200;
            obj.motionY = rand.nextGaussian() / 200;
            obj.motionZ = rand.nextGaussian() / 200;
            obj.renderDistanceWeight = 250D;
            FMLClientHandler.instance().getClient().effectRenderer.addEffect(obj);
        }
    }

    public void onImpactEntity(Entity entity, float damageFactor, Vec3 hitVec) {
        if (!entity.isDead) {
            MCH_Lib.DbgLog(super.worldObj, "MCH_EntityBaseBullet.onImpactEntity:Damage=%d:" + entity.getClass(), this.getPower());
            MCH_Lib.applyEntityHurtResistantTimeConfig(entity);
            DamageSource ds = DamageSource.causeThrownDamage(this, this.shootingEntity);
            ds = MCH_IndicatedDamageSource.build(ds, hitVec, Vec3.createVectorHelper(motionX, motionY, motionZ));
            float damage = MCH_Config.applyDamageVsEntity(entity, ds, (float) this.getPower() * damageFactor);
            damage *= this.getInfo() != null ? this.getInfo().getDamageFactor(entity) : 1.0F;
            entity.attackEntityFrom(ds, damage);
            if (this instanceof MCH_EntityBullet && entity instanceof EntityVillager && this.shootingEntity != null && this.shootingEntity.ridingEntity instanceof MCH_EntitySeat) {
                MCH_Achievement.addStat(this.shootingEntity, MCH_Achievement.aintWarHell, 1);
            }
        }

        this.notifyHitBullet();
    }

    public void applyArmorRicochet(Vec3 normal, Vec3 hitPos, float speedFactor) {
        if (normal == null || hitPos == null) {
            return;
        }
        double nx = normal.xCoord;
        double ny = normal.yCoord;
        double nz = normal.zCoord;
        double nLen = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nLen < 1.0E-6D) {
            return;
        }
        nx /= nLen;
        ny /= nLen;
        nz /= nLen;
        double vx = super.motionX;
        double vy = super.motionY;
        double vz = super.motionZ;
        double vLen = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vLen < 1.0E-6D) {
            return;
        }
        double dot = vx * nx + vy * ny + vz * nz;
        double rx = vx - 2.0D * dot * nx;
        double ry = vy - 2.0D * dot * ny;
        double rz = vz - 2.0D * dot * nz;
        double rLen = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rLen < 1.0E-6D) {
            return;
        }
        double newSpeed = vLen * Math.max(0.2D, Math.min(1.0D, speedFactor));
        super.motionX = rx / rLen * newSpeed;
        super.motionY = ry / rLen * newSpeed;
        super.motionZ = rz / rLen * newSpeed;
        double push = 0.12D;
        super.posX = hitPos.xCoord + nx * push;
        super.posY = hitPos.yCoord + ny * push;
        super.posZ = hitPos.zCoord + nz * push;
        super.prevPosX = super.posX;
        super.prevPosY = super.posY;
        super.prevPosZ = super.posZ;
        this.armorRicochetActive = true;
    }

    public void newFAExplosion(double x, double y, double z, float exp, float expBlock) {
        MCH_ExplosionParam param = MCH_ExplosionParam.builder()
            .exploder(this)
            .player(this.shootingEntity instanceof EntityPlayer ? (EntityPlayer) this.shootingEntity : null)
            .x(x).y(y).z(z)
            .size(exp)
            .sizeBlock(expBlock)
            .isPlaySound(true)
            .isSmoking(true)
            .isFlaming(this.getInfo().flaming)
            .isDestroyBlock(false)
            .countSetFireEntity(15)
            .isInWater(false)
            .damageVsPlayer(getInfo().explosionDamageVsPlayer)
            .damageVsLiving(getInfo().explosionDamageVsLiving)
            .damageVsPlane(getInfo().explosionDamageVsPlane)
            .damageVsHeli(getInfo().explosionDamageVsHeli)
            .damageVsTank(getInfo().explosionDamageVsTank)
            .damageVsVehicle(getInfo().explosionDamageVsVehicle)
            .damageVsShip(getInfo().explosionDamageVsShip)
            .explosionThroughWall(getInfo().explosionThroughWall)
            .explosionThroughWallFactor(getInfo().explosionThroughWallFactor)
            .isNewExplosionBreak(getInfo().isNewExplosionBreak)
            .build();
        MCH_Explosion.ExplosionResult result = MCH_Explosion.newExplosion(super.worldObj, param);
        if (result != null && result.hitEntity) {
            this.notifyHitBullet();
        }
    }

    public MCH_Explosion.ExplosionResult newExplosion(double x, double y, double z, float exp, float expBlock, boolean inWater) {
        return newExplosion(x, y, z, exp, expBlock, inWater, null);
    }

    public MCH_Explosion.ExplosionResult newExplosion(double x, double y, double z, float exp, float expBlock, boolean inWater, Entity directAttackEntity) {
        MCH_Explosion.ExplosionResult result;
        boolean playSound = (this.isBomblet != 1) || (super.rand.nextInt(3) == 0);
        EntityPlayer creditedPlayer = (this.shootingEntity instanceof EntityPlayer)
            ? (EntityPlayer) this.shootingEntity
            : null;
        if (isExplosionDebugEnabled()) {
            debugExplosion(
                "[EXPDBG] newExplosion bullet=%s exp=%.2f expBlock=%.2f info.expBlock=%d inWater=%s type=%s explosionType=%s effectYield=%d disableDestroyBlock=%s isFAE=%s piercing=%d newBreak=%s",
                this.getClass().getSimpleName(),
                exp,
                expBlock,
                this.getInfo() != null ? this.getInfo().explosionBlock : -1,
                String.valueOf(inWater),
                this.getInfo() != null ? this.getInfo().type : "<null>",
                this.getInfo() != null ? this.getInfo().explosionType : "<null>",
                this.getInfo() != null ? this.getInfo().effectYield : -1,
                String.valueOf(this.getInfo() != null && this.getInfo().disableDestroyBlock),
                String.valueOf(this.getInfo() != null && this.getInfo().isFAE),
                this.piercing,
                String.valueOf(this.getInfo() != null && this.getInfo().isNewExplosionBreak)
            );
        }
        if (!inWater) {
            //HBM爆炸效果
            if (this.getInfo().explosionType.contains("hbmNT") && MCH_HBMUtil.isHBMLoaded) {
                if (isExplosionDebugEnabled()) {
                    debugExplosion(
                        "[EXPDBG] branch=HBM_VNT effectYield=%d disableDestroyBlock=%s (fallbackMCHBreak=%s)",
                        this.getInfo().effectYield,
                        String.valueOf(this.getInfo().disableDestroyBlock),
                        String.valueOf(this.getInfo().disableDestroyBlock)
                    );
                }
                Object ExplosionVNT = MCH_HBMUtil.ExplosionVNT(super.worldObj, x, y, z, getInfo().effectYield);
                if (ExplosionVNT != null) {
                    if (this.getInfo().disableDestroyBlock) {
                        MCH_HBMUtil.ExplosionVNT_Explode(ExplosionVNT, false);
                    } else {
                        MCH_HBMUtil.ExplosionVNT_Explode(ExplosionVNT, true);
                    }
                }
                if (this.getInfo().explosionType.contains("_Bomb")) {
                    MCH_HBMUtil.ExplosionCreator_composeEffect(worldObj, x + 0.5, y + 1, z + 0.5, getInfo().effectYield);
                } else if (this.getInfo().explosionType.contains("_Shell")) {
                    MCH_HBMUtil.ExplosionSmallCreator_composeEffect(worldObj, x + 0.5, y + 1, z + 0.5, getInfo().effectYield);
                }
                if (this.getInfo().explosionType.contains("_frag")) {
                    MCH_HBMUtil.Frag_Effect(worldObj, x, y, z);
                }
                if (this.getInfo().explosionType.contains("_WP")) {
                    MCH_HBMUtil.WP_Effect(worldObj, x, y, z, this.dimension);
                }
                boolean fallbackToMchBlockBreak = this.getInfo().disableDestroyBlock;
                MCH_ExplosionParam param = MCH_ExplosionParam.builder()
                    .exploder(this)
                    .player(creditedPlayer)
                    .x(x).y(y).z(z)
                    .size(exp)
                    .sizeBlock(expBlock)
                    .isPlaySound(playSound)
                    .isSmoking(fallbackToMchBlockBreak)
                    .isFlaming(this.getInfo().flaming)
                    .isDestroyBlock(fallbackToMchBlockBreak && getInfo().explosionBlock > 0)
                    .isInWater(false)
                    .directAttackEntity(directAttackEntity)
                    .damageVsPlayer(getInfo().explosionDamageVsPlayer)
                    .damageVsLiving(getInfo().explosionDamageVsLiving)
                    .damageVsPlane(getInfo().explosionDamageVsPlane)
                    .damageVsHeli(getInfo().explosionDamageVsHeli)
                    .damageVsTank(getInfo().explosionDamageVsTank)
                    .damageVsVehicle(getInfo().explosionDamageVsVehicle)
                    .damageVsShip(getInfo().explosionDamageVsShip)
                    .explosionThroughWall(getInfo().explosionThroughWall)
                    .explosionThroughWallFactor(getInfo().explosionThroughWallFactor)
                    .isNewExplosionBreak(getInfo().isNewExplosionBreak)
                    .build();
                result = MCH_Explosion.newExplosion(super.worldObj, param);
            }
            //普通爆炸效果
            else {
                if (isExplosionDebugEnabled()) {
                    debugExplosion("[EXPDBG] branch=MCH_NORMAL isDestroyBlock=%s", String.valueOf(getInfo().explosionBlock > 0));
                }
                MCH_ExplosionParam param = MCH_ExplosionParam.builder()
                    .exploder(this)
                    .player(creditedPlayer)
                    .x(x).y(y).z(z)
                    .size(exp)
                    .sizeBlock(expBlock)
                    .isPlaySound(playSound)
                    .isSmoking(true)
                    .isFlaming(this.getInfo().flaming)
                    .isDestroyBlock(getInfo().explosionBlock > 0)
                    .isInWater(false)
                    .directAttackEntity(directAttackEntity)
                    .damageVsPlayer(getInfo().explosionDamageVsPlayer)
                    .damageVsLiving(getInfo().explosionDamageVsLiving)
                    .damageVsPlane(getInfo().explosionDamageVsPlane)
                    .damageVsHeli(getInfo().explosionDamageVsHeli)
                    .damageVsTank(getInfo().explosionDamageVsTank)
                    .damageVsVehicle(getInfo().explosionDamageVsVehicle)
                    .damageVsShip(getInfo().explosionDamageVsShip)
                    .explosionThroughWall(getInfo().explosionThroughWall)
                    .explosionThroughWallFactor(getInfo().explosionThroughWallFactor)
                    .isNewExplosionBreak(getInfo().isNewExplosionBreak)
                    .build();
                result = MCH_Explosion.newExplosion(super.worldObj, param);
            }
        } else {
            //水下爆炸
            if (isExplosionDebugEnabled()) {
                debugExplosion("[EXPDBG] branch=MCH_WATER isDestroyBlock=%s", String.valueOf(getInfo().explosionBlock > 0));
            }
            MCH_ExplosionParam param = MCH_ExplosionParam.builder()
                .exploder(this)
                .player(creditedPlayer)
                .x(x).y(y).z(z)
                .size(exp)
                .sizeBlock(expBlock)
                .isPlaySound(playSound)
                .isSmoking(true)
                .isFlaming(this.getInfo().flaming)
                .isDestroyBlock(getInfo().explosionBlock > 0)
                .isInWater(true)
                .directAttackEntity(directAttackEntity)
                .damageVsPlayer(getInfo().explosionDamageVsPlayer)
                .damageVsLiving(getInfo().explosionDamageVsLiving)
                .damageVsPlane(getInfo().explosionDamageVsPlane)
                .damageVsHeli(getInfo().explosionDamageVsHeli)
                .damageVsTank(getInfo().explosionDamageVsTank)
                .damageVsVehicle(getInfo().explosionDamageVsVehicle)
                .damageVsShip(getInfo().explosionDamageVsShip)
                .explosionThroughWall(getInfo().explosionThroughWall)
                .explosionThroughWallFactor(getInfo().explosionThroughWallFactor)
                .isNewExplosionBreak(getInfo().isNewExplosionBreak)
                .build();
            result = MCH_Explosion.newExplosion(super.worldObj, param);
        }

        if (!super.worldObj.isRemote) {
            if (this.getInfo().enableNuke) {
                float nukeScale = MathHelper.clamp_float(
                    (float) (Math.sqrt(Math.max(1.0F, exp)) * 0.22D * this.getInfo().nukeEffectScale),
                    0.6F,
                    6.0F
                );
                EntityNukeTorex torex = new EntityNukeTorex(super.worldObj)
                    .setScale(nukeScale)
                    .setType(getInfo().effectYield);
                torex.setPosition(this.posX + 0.5D, this.posY + 0.5D, this.posZ + 0.5D);
                torex.ignoreFrustumCheck = true;
                super.worldObj.spawnEntityInWorld(torex);
                EntityNukeTorex.setTrackingRange(super.worldObj, torex, 1000);

                if (this.getInfo().enableNukeFlash) {
                    MCH_PacketEffectNukeFlash.send(
                        this,
                        this.posX + 0.5D,
                        this.posY + 0.5D,
                        this.posZ + 0.5D,
                        exp,
                        this.getInfo().nukeFlashRadiusFactor,
                        this.getInfo().nukeFlashDurationMin,
                        this.getInfo().nukeFlashDurationMax
                    );
                }
            } else if (this.getInfo().nukeYield > 0 && MCH_HBMUtil.isHBMLoaded) {
                if (!this.getInfo().nukeEffectOnly) {
                    worldObj.spawnEntityInWorld((Entity) MCH_HBMUtil.EntityNukeExplosionMK5_statFac(super.worldObj, this.getInfo().nukeYield, this.posX + 0.5, this.posY + 0.5, this.posZ + 0.5));
                }
                MCH_HBMUtil.EntityNukeTorex_statFac(super.worldObj, this.posX + 0.5, this.posY + 0.5, this.posZ + 0.5, (float) this.getInfo().nukeYield, getInfo().effectYield);
            }
        }

        if (this.getInfo().chemYield > 0 && MCH_HBMUtil.isHBMLoaded) {
            MCH_HBMUtil.ExplosionChaos_spawnClorine(super.worldObj, posX, posY + 0.5, posZ, this.getInfo().chemYield);
        }


        if (result != null && result.hitEntity) {
            this.notifyHitBullet();
        }

        return result;
    }

    public static void setExplosionDebugEnabled(boolean enabled) {
        explosionDebugEnabled = enabled;
        MCH_ExplosionDebug.setEnabled(enabled);
        if (enabled) {
            MCH_ExplosionDebug.appendRaw("[EXPDBG] enabled");
        } else {
            MCH_ExplosionDebug.appendRaw("[EXPDBG] disabled");
        }
    }

    public static boolean isExplosionDebugEnabled() {
        return explosionDebugEnabled;
    }

    private void debugExplosion(String format, Object... data) {
        MCH_Lib.Log(format, data);
        MCH_ExplosionDebug.append(format, data);
    }

    public void playExplosionSound() {
        MCH_Explosion.playExplosionSound(super.worldObj, super.posX, super.posY, super.posZ);
    }

    public void writeEntityToNBT(NBTTagCompound par1NBTTagCompound) {
        par1NBTTagCompound.setTag("direction", this.newDoubleNBTList(super.motionX, super.motionY, super.motionZ));
        par1NBTTagCompound.setString("WeaponName", this.getName());
    }

    public void readEntityFromNBT(NBTTagCompound par1NBTTagCompound) {
        this.setDead();
    }

    public boolean canBeCollidedWith() {
        return true;
    }

    public float getCollisionBorderSize() {
        return 1.0F;
    }

    public boolean attackEntityFrom(DamageSource ds, float par2) {

        if (this.isEntityInvulnerable()) {
            return false;
        } else if (!super.worldObj.isRemote && par2 > 0.0F && ds.getDamageType().equalsIgnoreCase("thrown")) {
            this.setBeenAttacked();
            MovingObjectPosition m = new MovingObjectPosition((int) (super.posX + 0.5D), (int) (super.posY + 0.5D), (int) (super.posZ + 0.5D), 0, Vec3.createVectorHelper(super.posX + 0.5D, super.posY + 0.5D, super.posZ + 0.5D));
            this.onImpact(m, 1.0F);
            return true;
        } else {
            return false;
        }
    }

    @SideOnly(Side.CLIENT)
    public float getShadowSize() {
        return 0.0F;
    }

    public float getBrightness(float par1) {
        return 1.0F;
    }

    @SideOnly(Side.CLIENT)
    public int getBrightnessForRender(float par1) {
        return 15728880;
    }

    public int getPower() {
        return this.power;
    }

    public void setPower(int power) {
        this.power = power;
    }


    protected void scanForTargets() {
        if (numLockedChaff >= getInfo().numLockedChaffMax) {
            setTargetEntity(null);
            return;
        }
        Vector3f missileDirection = new Vector3f((float) super.motionX, (float) super.motionY, (float) super.motionZ);
        double range = getInfo().maxLockOnRange;
        List<Entity> list = worldObj.getEntitiesWithinAABB(
            Entity.class,
            AxisAlignedBB.getBoundingBox(posX - range, posY - range, posZ - range,
                posX + range, posY + range, posZ + range)
        );

        if (list != null && !list.isEmpty()) {
            double closestAngle = Double.MAX_VALUE;
            Entity closestTarget = null;
            double closestArmScore = Double.MAX_VALUE;
            Entity closestArmTarget = null;

            // 记录最近的箔条及其距离
            double nearestChaffDistSq = Double.MAX_VALUE;
            Entity nearestChaff = null;

            for (Entity entity : list) {
                // AA 导弹的目标判定
                if (this instanceof MCH_EntityAAMissile) {
                    boolean canScanMissiles = getInfo().canLockMissile && (getInfo().activeRadar || getInfo().semiActiveRadar);
                    // 发现箔条时先处理
                    if (entity instanceof MCH_EntityChaff) {
                        // 计算与导弹方向的夹角，确保在锁定范围内
                        double dx = entity.posX - super.posX;
                        double dy = entity.posY - super.posY;
                        double dz = entity.posZ - super.posZ;
                        Vector3f targetDir = new Vector3f((float) dx, (float) dy, (float) dz);
                        double angle = Math.abs(Vector3f.angle(missileDirection, targetDir));
                        if (angle > Math.toRadians(this.getCurrentMaxDegreeOfMissile())) continue;
                        double distSq = dx * dx + dy * dy + dz * dz;
                        if (distSq < nearestChaffDistSq) {
                            nearestChaffDistSq = distSq;
                            nearestChaff = entity;
                        }
                    }
                    // 未发现箔条时按原有逻辑扫描飞机
                    else if (entity instanceof MCH_EntityAircraft) {
                        MCH_EntityAircraft ac = (MCH_EntityAircraft) entity;
                        if (W_Entity.isEqual(entity, shootingAircraft)) continue;
                        if (shootingEntity instanceof EntityLivingBase && entity.riddenByEntity instanceof EntityPlayer
                            && ((EntityPlayer) entity.riddenByEntity).isOnSameTeam((EntityLivingBase) shootingEntity)) {
                            continue;
                        }
                        // 排除地面上的目标
                        if (MCH_WeaponGuidanceSystem.isEntityOnGround(entity, getInfo().lockMinHeight)) continue;

                        double dx = entity.posX - super.posX;
                        double dy = entity.posY - super.posY;
                        double dz = entity.posZ - super.posZ;
                        Vector3f targetDir = new Vector3f((float) dx, (float) dy, (float) dz);
                        double angle = Math.abs(Vector3f.angle(missileDirection, targetDir));
                        if (angle > Math.toRadians(this.getCurrentMaxDegreeOfMissile())) continue;

                        boolean antiRadiation = getInfo().antiRadiationMissile;
                        if (antiRadiation) {
                            if (isFriendlyArmEmitterSource(ac)) {
                                continue;
                            }
                            boolean hojEmitter = ac.isECMJammerUsing();
                            if (!isArmEmitterRadiatingSource(ac)) {
                                continue;
                            }
                            double distSq = dx * dx + dy * dy + dz * dz;
                            double rangeNorm = range > 0.0D ? Math.min(1.0D, distSq / (range * range)) : 1.0D;
                            double armScore = angle + rangeNorm * 0.15D;
                            if (hojEmitter) {
                                // HOJ: jammer-on emitters get the highest acquisition priority.
                                armScore -= Math.toRadians(180.0D);
                            }
                            if (armScore < closestArmScore) {
                                closestArmScore = armScore;
                                closestArmTarget = entity;
                            }
                        } else if (angle < closestAngle) {
                            closestAngle = angle;
                            closestTarget = entity;
                        }
                    }
                    // Active/semi-active radar can optionally include missiles in autonomous scan.
                    else if (canScanMissiles && entity instanceof MCH_EntityBaseBullet
                        && (entity instanceof MCH_EntityAAMissile || entity instanceof MCH_EntityATMissile
                        || entity instanceof MCH_EntityASMissile || entity instanceof MCH_EntityTvMissile)) {
                        MCH_EntityBaseBullet bullet = (MCH_EntityBaseBullet) entity;
                        if (bullet.isDead || W_Entity.isEqual(entity, this)) continue;
                        if (W_Entity.isEqual(entity, shootingAircraft)) continue;
                        if (W_Entity.isEqual(entity, shootingEntity)) continue;
                        if (shootingEntity != null && W_Entity.isEqual(bullet.shootingEntity, shootingEntity)) continue;
                        if (shootingEntity instanceof EntityLivingBase && bullet.shootingEntity instanceof EntityLivingBase
                            && ((EntityLivingBase) bullet.shootingEntity).isOnSameTeam((EntityLivingBase) shootingEntity)) {
                            continue;
                        }
                        double dx = entity.posX - super.posX;
                        double dy = entity.posY - super.posY;
                        double dz = entity.posZ - super.posZ;
                        Vector3f targetDir = new Vector3f((float) dx, (float) dy, (float) dz);
                        double angle = Math.abs(Vector3f.angle(missileDirection, targetDir));
                        if (angle > Math.toRadians(this.getCurrentMaxDegreeOfMissile())) continue;
                        if (angle < closestAngle) {
                            closestAngle = angle;
                            closestTarget = entity;
                        }
                    }
                }
                // AT 导弹的逻辑不变…
                else if (this instanceof MCH_EntityATMissile) {
                    // 保持原有地面目标选择逻辑
                    if (entity instanceof MCH_EntityAircraft) {
                        MCH_EntityAircraft ac = (MCH_EntityAircraft) entity;
                        if (W_Entity.isEqual(entity, shootingAircraft)) continue;
                        if (shootingEntity instanceof EntityLivingBase && entity.riddenByEntity instanceof EntityPlayer
                            && ((EntityPlayer) entity.riddenByEntity).isOnSameTeam((EntityLivingBase) shootingEntity)) {
                            continue;
                        }
                        boolean antiRadiation = getInfo().antiRadiationMissile;
                        if (antiRadiation) {
                            if (!(ac instanceof MCH_EntityTank || ac instanceof MCH_EntityVehicle)) {
                                continue;
                            }
                        } else {
                            boolean isTargetOnGround = MCH_WeaponGuidanceSystem.isEntityOnGround(entity, getInfo().lockMinHeight);
                            if (!isTargetOnGround) continue;
                        }
                        if (antiRadiation) {
                            if (isFriendlyArmEmitterSource(ac)) {
                                continue;
                            }
                            boolean hojEmitter = ac.isECMJammerUsing();
                            if (!isArmEmitterRadiatingSource(ac)) {
                                continue;
                            }
                        }
                        double dx = entity.posX - super.posX;
                        double dy = entity.posY - super.posY;
                        double dz = entity.posZ - super.posZ;
                        Vector3f targetDirection = new Vector3f((float) dx, (float) dy, (float) dz);
                        double angle = Math.abs(Vector3f.angle(missileDirection, targetDirection));
                        if (angle > Math.toRadians(this.getCurrentMaxDegreeOfMissile())) continue;
                        if (antiRadiation) {
                            double distSq = dx * dx + dy * dy + dz * dz;
                            double rangeNorm = range > 0.0D ? Math.min(1.0D, distSq / (range * range)) : 1.0D;
                            double armScore = angle + rangeNorm * 0.15D;
                            if (ac.isECMJammerUsing()) {
                                // HOJ: jammer-on emitters get the highest acquisition priority.
                                armScore -= Math.toRadians(180.0D);
                            }
                            if (armScore < closestArmScore) {
                                closestArmScore = armScore;
                                closestArmTarget = entity;
                            }
                        } else if (angle < closestAngle) {
                            closestAngle = angle;
                            closestTarget = entity;
                        }
                    } else if (!getInfo().ridableOnly && entity instanceof EntityLivingBase && entity.ridingEntity == null) {
                        if (W_Entity.isEqual(entity, shootingEntity)) continue;
                        if (shootingEntity instanceof EntityLivingBase && ((EntityLivingBase) entity).isOnSameTeam((EntityLivingBase) shootingEntity)) {
                            continue;
                        }
                        boolean isTargetOnGround = MCH_WeaponGuidanceSystem.isEntityOnGround(entity, getInfo().lockMinHeight);
                        if (!isTargetOnGround) continue;
                        double dx = entity.posX - super.posX;
                        double dy = entity.posY - super.posY;
                        double dz = entity.posZ - super.posZ;
                        Vector3f targetDirection = new Vector3f((float) dx, (float) dy, (float) dz);
                        double angle = Math.abs(Vector3f.angle(missileDirection, targetDirection));
                        if (angle > Math.toRadians(this.getCurrentMaxDegreeOfMissile())) continue;
                        if (angle < closestAngle) {
                            closestAngle = angle;
                            closestTarget = entity;
                        }
                    }
                }
            }
            // ARM always prioritizes radiating emitters once detected.
            if (getInfo().antiRadiationMissile && closestArmTarget != null) {
                targetEntity = closestArmTarget;
                armHojCepActive = (closestArmTarget instanceof MCH_EntityAircraft) && ((MCH_EntityAircraft)closestArmTarget).isECMJammerUsing();
                if (getInfo().activeRadar) {
                    setActiveRadarCaptured(true);
                }
            } else if (nearestChaff != null) {
                targetEntity = nearestChaff;
                numLockedChaff++;
                armHojCepActive = false;
                if (getInfo().activeRadar) {
                    setActiveRadarCaptured(false);
                }
            } else if (closestTarget != null) {
                targetEntity = closestTarget;
                armHojCepActive = false;
                if (getInfo().activeRadar) {
                    setActiveRadarCaptured(true);
                }
            }
        }
    }


    public void onUpdateSpreader() {
        if (!super.worldObj.isRemote) {
            boolean canSpawnBulletInAir = this.getInfo().spawnBulletInAir;
            boolean canSpawnAhead = this.getInfo().ahead && this.aheadTriggered;
            if ((canSpawnBulletInAir || canSpawnAhead) && this.spawnedBulletNum < getInfo().spawnBulletMaxNum && !super.isDead) {
                if (this.ticksExisted > 5 && this.ticksExisted % getInfo().spawnBulletIntervalTick == 0) {
                    ++this.spawnedBulletNum;
                    for (int i = 0; i < this.getInfo().spawnBulletPerNum; ++i) {
                        double mX = 1e-6, mY = 1e-6, mZ = 1e-6, speed = 0.001;
                        if (getInfo().spawnBulletInheritSpeed) {
                            mX = motionX;
                            mY = motionY;
                            mZ = motionZ;
                            speed = acceleration;
                        }
                        MCH_WeaponInfo info = MCH_WeaponInfoManager.get(this.getInfo().bombletModelName);
                        if(info != null) {
                            MCH_EntityBaseBullet e = MCH_WeaponCreator.createEntity(info.type, super.worldObj, posX, posY, posZ, mX, mY, mZ, rotationYaw, rotationPitch, speed);
                            e.setInfo(this.getInfo().bombletModelName, info);
                            e.setParameterFromWeapon(shootingAircraft, shootingEntity);
                            e.setPower(e.getInfo().power);
                            e.explosionPower = e.getInfo().explosion;
                            e.explosionPowerInWater = e.getInfo().explosionInWater;
                            float MOTION = this.getInfo().bombletDiff;
                            e.motionX += ((double) super.rand.nextFloat() - 0.5D) * (double) MOTION;
                            e.motionY += ((double) super.rand.nextFloat() - 0.5D) * (double) MOTION;
                            e.motionZ += ((double) super.rand.nextFloat() - 0.5D) * (double) MOTION;
                            MCH_WeaponCreator.setEntityInfo(e, shootingEntity);

                            super.worldObj.spawnEntityInWorld(e);
                        }
                    }
                }
            }

            if(getInfo().destructAfterSpawnBullet && this.spawnedBulletNum >= getInfo().spawnBulletMaxNum) {
                setDead();
            }
        }
    }

    /**
     * 近炸检测（使用插值计算）
     */
    private void onUpdateProximityFuse() {

        if (getInfo().proximityFuseTick < 0 || ticksExisted <= getInfo().proximityFuseTick) {
            return;
        }

        if (getInfo().proximityFuseDist <= 0) {
            return;
        }

        // For AHEAD-capable weapons, ProximityFuseDist is treated as pre-airburst lead distance
        // and should only work when radar fire-control has produced a valid airburst solution.
        if (getInfo().ahead) {
            int abDist = this.airburstDist;
            if (abDist <= 5 || abDist >= 3000) {
                return;
            }
        }

        float searchRange = getInfo().proximityFuseDist * 5f;

        List<Entity> nearbyEntities = worldObj.getEntitiesWithinAABBExcludingEntity(
            this,
            boundingBox.expand(searchRange, searchRange, searchRange)
        );

        if (nearbyEntities.isEmpty()) {
            return;
        }

        // 当前帧子弹的位移向量
        double dx = this.motionX * this.accelerationFactor;
        double dy = this.motionY * this.accelerationFactor;
        double dz = this.motionZ * this.accelerationFactor;
        double segLen = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (segLen <= 0.0D) {
            return;
        }

        // 子弹运动方向单位向量
        double dirX = dx / segLen;
        double dirY = dy / segLen;
        double dirZ = dz / segLen;

        // 子弹当前速度（单位：米/每帧）
        double bulletSpeed = segLen;

        for (Entity entity : nearbyEntities) {
            if (!canBeCollidedEntity(entity)) {
                continue;
            }

            boolean isAircraftTarget = entity instanceof MCH_EntityAircraft;
            boolean isLockedMissileTarget =
                entity instanceof MCH_IMissile
                    && this.targetEntity != null
                    && !this.targetEntity.isDead
                    && W_Entity.isEqual(entity, this.targetEntity);

            // Keep legacy aircraft proximity behavior, and extend to locked missile targets only.
            if (!isAircraftTarget && !isLockedMissileTarget) {
                continue;
            }

            if (isAircraftTarget && MCH_WeaponGuidanceSystem.isEntityOnGround(entity, getInfo().proximityFuseHeight)) {
                continue;
            }

            // 方法1：考虑目标运动，计算预测位置
            // 计算从子弹当前位置指向实体当前位置的向量
            double toEntityX = entity.posX - this.posX;
            double toEntityY = entity.posY - this.posY;
            double toEntityZ = entity.posZ - this.posZ;

            // 计算当前位置的距离
            double currentDistance = Math.sqrt(toEntityX * toEntityX + toEntityY * toEntityY + toEntityZ * toEntityZ);

            // 计算子弹到达目标当前位置所需的时间（粗略估计）
            double timeToCurrentPos = currentDistance / bulletSpeed;

            // 预测目标在timeToCurrentPos时间后的位置
            double predictedX = entity.posX + entity.motionX * timeToCurrentPos;
            double predictedY = entity.posY + entity.motionY * timeToCurrentPos;
            double predictedZ = entity.posZ + entity.motionZ * timeToCurrentPos;

            // 计算从子弹当前位置指向预测位置的向量
            double toPredictedX = predictedX - this.posX;
            double toPredictedY = predictedY - this.posY;
            double toPredictedZ = predictedZ - this.posZ;

            // 计算在子弹运动方向上的投影长度
            double dot = toPredictedX * dirX + toPredictedY * dirY + toPredictedZ * dirZ;

            // 如果预测点在子弹后方，跳过
            if (dot <= 0) {
                continue;
            }

            // 计算投影点（子弹运动方向上离预测点最近的点）
            double projX = this.posX + dirX * dot;
            double projY = this.posY + dirY * dot;
            double projZ = this.posZ + dirZ * dot;

            // 计算投影点到预测点的距离（垂直距离）
            double perpX = predictedX - projX;
            double perpY = predictedY - projY;
            double perpZ = predictedZ - projZ;
            double predictedDistance = Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);

            // 如果距离小于近炸触发距离，进一步检查投影点是否在当前帧的位移路径上
            if (predictedDistance <= getInfo().proximityFuseDist) {
                // 计算从子弹当前位置到投影点的距离
                double distToProj = Math.sqrt(
                    (projX - this.posX) * (projX - this.posX) +
                        (projY - this.posY) * (projY - this.posY) +
                        (projZ - this.posZ) * (projZ - this.posZ)
                );

                // 如果投影点在当前帧位移路径上（或非常接近）
                if (distToProj <= segLen + 0.1) {
                    // 计算插值参数t
                    double t = distToProj / segLen;
                    // 限制t在0到1之间
                    t = Math.max(0.0, Math.min(1.0, t));

                    // 计算插值位置（爆炸位置）
                    double ex = this.posX + dx * t;
                    double ey = this.posY + dy * t;
                    double ez = this.posZ + dz * t;

                    if (!this.worldObj.isRemote) {
                        MCH_Explosion.ExplosionResult result = null;
                        if (this.getInfo().explosion > 0) {
                            result = this.newExplosion(ex, ey, ez, getInfo().explosionAirburst,
                                (float) this.getInfo().explosionBlock, false);
                        } else if (this.explosionPower < 0) {
                            this.playExplosionSound();
                        }

                        if (this.getInfo() != null && this.getInfo().enableChunkLoader) {
                            this.clearChunkLoaders();
                        }

                        if (this.getInfo() != null) {
                            PacketPlaySound.sendSoundPacket(
                                ex, ey, ez, this.getInfo().hitSoundRange, this.dimension,
                                this.getInfo().hitSound, true);
                        }

                        if (!entity.isDead) {
                            MCH_Lib.applyEntityHurtResistantTimeConfig(entity);
                            DamageSource ds = DamageSource.setExplosionSource(result == null ? null : result.explosion);
                            float damage = MCH_Config.applyDamageVsEntity(entity, ds, this.getInfo().proximityFuseDamage);
                            damage *= this.getInfo() != null ? this.getInfo().getDamageFactor(entity) : 1.0F;
                            entity.attackEntityFrom(ds, damage);

                            // Locked missile targets should be killable by proximity trigger even with low blast damage setup.
                            if (isLockedMissileTarget && entity instanceof MCH_EntityBaseBullet) {
                                ((MCH_EntityBaseBullet) entity).setDead();
                            }

                            if(damage > 0) {
                                this.notifyHitBullet();
                            }
                        }
                        this.setDead();
                    }

                    return;
                }
            }
        }
    }

}
