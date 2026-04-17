package mcheli.aircraft;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import mcheli.*;
import mcheli.plane.MCP_EntityPlane;
import mcheli.chain.MCH_EntityChain;
import mcheli.command.MCH_Command;
import mcheli.event.AircraftDamageEvent;
import mcheli.event.AircraftDestoryEvent;
import mcheli.flare.*;
import mcheli.multiplay.MCH_Multiplay;
import mcheli.mob.MCH_EntityGunner;
import mcheli.network.packets.PacketAirburstDistReset;
import mcheli.network.packets.PacketBoundingBoxHit;
import mcheli.network.packets.PacketDamageIndicator;
import mcheli.parachute.MCH_EntityParachute;
import mcheli.particles.MCH_ParticleParam;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.uav.MCH_EntityUavStation;
import mcheli.weapon.*;
import mcheli.wrapper.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;

import java.util.*;

public abstract class MCH_EntityAircraft extends W_EntityContainer implements MCH_IEntityLockChecker, MCH_IEntityCanRideAircraft, IEntityAdditionalSpawnData {

    public static final byte LIMIT_GROUND_PITCH = 40;
    public static final byte LIMIT_GROUND_ROLL = 40;
    public static final int CAMERA_PITCH_MIN = -30;
    public static final int CAMERA_PITCH_MAX = 70;
    protected static final int PART_ID_CANOPY = 0;
    protected static final int PART_ID_NOZZLE = 1;
    protected static final int PART_ID_LANDINGGEAR = 2;
    protected static final int PART_ID_WING = 3;
    protected static final int PART_ID_HATCH = 4;
    private static final int DATAWT_ID_DAMAGE = 19;
    private static final int DATAWT_ID_TYPE = 20;
    private static final int DATAWT_ID_TEXTURE_NAME = 21;
    private static final int DATAWT_ID_UAV_STATION = 22;
    private static final int DATAWT_ID_STATUS = 23;
    private static final int DATAWT_ID_USE_WEAPON = 24;
    private static final int DATAWT_ID_FUEL = 25;
    private static final int DATAWT_ID_ROT_ROLL = 26;
    private static final int DATAWT_ID_COMMAND = 27;
    private static final int DATAWT_ID_ERA_STATE = 28;
    private static final int DATAWT_ID_THROTTLE = 29;
    private static final int DATAWT_ID_FOLD_STAT = 30;
    private static final int DATAWT_ID_PART_STAT = 31;
    private static final int CMN_ID_FLARE = 0;
    private static final int CMN_ID_FREE_LOOK = 1;
    private static final int CMN_ID_RELOADING = 2;
    private static final int CMN_ID_INGINITY_AMMO = 3;
    private static final int CMN_ID_INGINITY_FUEL = 4;
    private static final int CMN_ID_RAPELLING = 5;
    private static final int CMN_ID_SEARCHLIGHT = 6;
    private static final int CMN_ID_CNTRL_LEFT = 7;
    private static final int CMN_ID_CNTRL_RIGHT = 8;
    private static final int CMN_ID_CNTRL_UP = 9;
    private static final int CMN_ID_CNTRL_DOWN = 10;
    private static final int CMN_ID_CNTRL_BRAKE = 11;

    private static final MCH_EntitySeat[] seatsDummy = new MCH_EntitySeat[0];
    public final MCH_MissileDetector missileDetector;
    public final HashMap noCollisionEntities = new HashMap();
    protected final MCH_SoundUpdater soundUpdater;
    private final MCH_AircraftInventory inventory;
    public boolean isRequestedSyncStatus = false;
    public boolean keepOnRideRotation;
    public boolean aircraftRollRev;
    public boolean aircraftRotChanged;
    public float rotationRoll;
    public float prevRotationRoll;
    public double currentSpeed;
    public int currentFuel;
    public float throttleBack = 0.0F;
    public double beforeHoverThrottle;
    public int waitMountEntity = 0;
    public boolean throttleUp = false;
    public boolean throttleDown = false;
    public boolean moveLeft = false;
    public boolean moveRight = false;
    public MCH_LowPassFilterFloat lowPassPartialTicks;
    public MCH_WeaponSet[] weapons;
    public float lastRiderYaw;
    public float prevLastRiderYaw;
    public float lastRiderPitch;
    public float prevLastRiderPitch;
    public List listUnmountReserve = new ArrayList();
    public MCH_Camera camera;
    public int serverNoMoveCount = 0;
    public int repairCount;
    public int beforeDamageTaken;
    public int timeSinceHit;
    public float rotDestroyedYaw;
    public float rotDestroyedPitch;
    public float rotDestroyedRoll;
    public int damageSinceDestroyed;
    public boolean isFirstDamageSmoke = true;
    public Vec3[] prevDamageSmokePos = new Vec3[0];
    public boolean cs_dismountAll;
    public boolean cs_heliAutoThrottleDown;
    public boolean cs_planeAutoThrottleDown;
    public boolean cs_tankAutoThrottleDown;
    public MCH_Parts partHatch;
    public MCH_Parts partCanopy;
    public MCH_Parts partLandingGear;
    public boolean canRideRackStatus;
    public Vec3 target = Vec3.createVectorHelper(0, 0, 0);
    public MCH_BoundingBox[] extraBoundingBox;
    public float lastBBDamageFactor;
    public String lastBBName;
    public int lastBBIndex;
    public Vec3 lastBBHitNormal;
    public Vec3 lastBBHitPos;
    public MCH_EntityAircraft.WeaponBay[] weaponBays;
    public float[] rotPartRotation;
    public float[] prevRotPartRotation;
    public float[] rotCrawlerTrack = new float[2];
    public float[] prevRotCrawlerTrack = new float[2];
    public float[] throttleCrawlerTrack = new float[2];
    public float[] rotTrackRoller = new float[2];
    public float[] prevRotTrackRoller = new float[2];
    public float rotWheel = 0.0F;
    public float prevRotWheel = 0.0F;
    public float rotYawWheel = 0.0F;
    public float prevRotYawWheel = 0.0F;
    public float ropesLength = 0.0F;
    public float lastSearchLightYaw;
    public float lastSearchLightPitch;
    public float rotLightHatch = 0.0F;
    public float prevRotLightHatch = 0.0F;
    public int recoilCount = 0;
    public float recoilYaw = 0.0F;
    public float recoilValue = 0.0F;
    public int brightnessHigh = 240;
    public float thirdPersonDist = 4.0F;
    public Entity lastAttackedEntity = null;
    public MCH_Chaff chaff;
    public MCH_Maintenance maintenance;
    public MCH_APS aps;
    public MCH_ECMJammer ecmJammer;
    public int ironCurtainRunningTick = 0;
    public float ironCurtainLastFactor = 0.5f;
    public float ironCurtainCurrentFactor = 0.5f;
    public int ironCurtainWaveTimer = 0;
    public int chaffUseTime = 0;
    public int ecmJammerUseTime = 0;
    public float gForce;
    public Entity lastRiddenByEntity;
    public List<MCH_DamageIndicator> damageIndicatorList = new ArrayList<>();
    protected double velocityX;
    protected double velocityY;
    protected double velocityZ;
    protected int aircraftPosRotInc;
    protected double aircraftX;
    protected double aircraftY;
    protected double aircraftZ;
    protected double aircraftYaw;
    protected double aircraftPitch;
    protected int[] currentWeaponID;
    protected MCH_WeaponSet dummyWeapon;
    protected int useWeaponStat;
    protected String eraStateForSync;
    protected int hitStatus;
    protected Entity lastRidingEntity;
    protected boolean isGunnerMode = false;
    protected boolean isGunnerModeOtherSeat = false;
    protected boolean isGunnerFreeLookMode = false;
    private Vec3 prevVelocity = null;
    private float smoothedGForce = 1.0F;
    private MCH_AircraftInfo acInfo;
    private int commonStatus;
    private Entity[] partEntities;
    private MCH_EntityHitBox pilotSeat;
    private MCH_EntitySeat[] seats;
    private MCH_SeatInfo[] seatsInfo;
    private String commonUniqueId;
    private int seatSearchCount;
    private double currentThrottle;
    private double prevCurrentThrottle;
    private MCH_Radar entityRadar;
    private int radarRotate;
    private MCH_Flare flareDv;
    private int currentFlareIndex;
    private int countOnUpdate;
    private MCH_EntityChain towChainEntity;
    private MCH_EntityChain towedChainEntity;
    private int cameraId;
    private boolean isHoveringMode = false;
    private MCH_EntityTvMissile TVmissile;
    private int despawnCount;
    private MCH_EntityUavStation uavStation;
    private int modeSwitchCooldown;
    private double fuelConsumption;
    private int fuelSuppliedCount;
    private int supplyAmmoWait;
    private boolean beforeSupplyAmmo;
    private boolean isParachuting;
    private MCH_Queue prevPosition;
    private int tickRepelling;
    private int lastUsedRopeIndex;
    private boolean dismountedUserCtrl;
    private double lastCalcLandInDistanceCount;
    private double lastLandInDistance;
    private double lastCalcPredictedImpactPointCount;
    private Vec3 lastPredictedImpactPoint;
    private boolean switchSeat = false;
    public int jammingTick = 0;

    public MCH_EntityAircraft(World world) {
        super(world);
        this.setAcInfo(null);
        this.commonStatus = 0;
        super.dropContentsWhenDead = false;
        super.ignoreFrustumCheck = true;
        this.flareDv = new MCH_Flare(world, this);
        this.chaff = new MCH_Chaff(world, this);
        this.maintenance = new MCH_Maintenance(world, this);
        this.aps = new MCH_APS(world, this);
        this.ecmJammer = new MCH_ECMJammer(world, this);
        this.currentFlareIndex = 0;
        this.entityRadar = new MCH_Radar(world);
        this.radarRotate = 0;
        this.currentWeaponID = new int[0];
        this.eraStateForSync = "";
        //this.aircraftPosRotInc = 0;
        this.aircraftX = 0.0D;
        this.aircraftY = 0.0D;
        this.aircraftZ = 0.0D;
        //this.aircraftYaw = 0.0D;
        this.aircraftPitch = 0.0D;
        this.currentSpeed = 0.0D;
        this.setCurrentThrottle(0.0D);
        this.currentFuel = 0;
        this.cs_dismountAll = false;
        this.cs_heliAutoThrottleDown = true;
        this.cs_planeAutoThrottleDown = false;
        super.renderDistanceWeight = MCH_Config.RenderDistanceWeight.prmDouble;
        this.setCommonUniqueId("");
        this.seatSearchCount = 0;
        this.seatsInfo = null;
        this.seats = new MCH_EntitySeat[0];
        this.pilotSeat = new MCH_EntityHitBox(world, this, 1.0F, 1.0F);
        this.pilotSeat.parent = this;
        this.partEntities = new Entity[]{this.pilotSeat};
        this.setTextureName("");
        this.camera = new MCH_Camera(world, this, super.posX, super.posY, super.posZ);
        this.setCameraId(0);
        this.lastRiddenByEntity = null;
        this.lastRidingEntity = null;
        this.soundUpdater = MCH_MOD.proxy.CreateSoundUpdater(this);
        this.countOnUpdate = 0;
        this.setTowChainEntity((MCH_EntityChain) null);
        this.dummyWeapon = new MCH_WeaponSet(new MCH_WeaponDummy(super.worldObj, Vec3.createVectorHelper(0.0D, 0.0D, 0.0D), 0.0F, 0.0F, "", (MCH_WeaponInfo) null));
        this.useWeaponStat = 0;
        this.hitStatus = 0;
        this.repairCount = 0;
        this.beforeDamageTaken = 0;
        this.timeSinceHit = 0;
        this.setDespawnCount(0);
        this.missileDetector = new MCH_MissileDetector(this, world);
        this.uavStation = null;
        this.modeSwitchCooldown = 0;
        this.partHatch = null;
        this.partCanopy = null;
        this.partLandingGear = null;
        this.weaponBays = new MCH_EntityAircraft.WeaponBay[0];
        this.rotPartRotation = new float[0];
        this.prevRotPartRotation = new float[0];
        this.lastRiderYaw = 0.0F;
        this.prevLastRiderYaw = 0.0F;
        this.lastRiderPitch = 0.0F;
        this.prevLastRiderPitch = 0.0F;
        this.rotationRoll = 0.0F;
        this.prevRotationRoll = 0.0F;
        this.lowPassPartialTicks = new MCH_LowPassFilterFloat(10);
        this.extraBoundingBox = new MCH_BoundingBox[0];
        W_Reflection.setBoundingBox(this, new MCH_AircraftBoundingBox(this));
        this.lastBBDamageFactor = 1.0F;
        this.lastBBName = null;
        this.lastBBIndex = -1;
        this.lastBBHitNormal = null;
        this.lastBBHitPos = null;
        this.inventory = new MCH_AircraftInventory(this);
        this.fuelConsumption = 0.0D;
        this.fuelSuppliedCount = 0;
        this.canRideRackStatus = false;
        this.isParachuting = false;
        this.prevPosition = new MCH_Queue(10, Vec3.createVectorHelper(0.0D, 0.0D, 0.0D));
        this.lastSearchLightYaw = this.lastSearchLightPitch = 0.0F;
    }

    public static MCH_EntityAircraft getAircraft_RiddenOrControl(Entity rider) {
        if (rider != null) {
            if (rider.ridingEntity instanceof MCH_EntityAircraft) {
                return (MCH_EntityAircraft) rider.ridingEntity;
            }

            if (rider.ridingEntity instanceof MCH_EntitySeat) {
                return ((MCH_EntitySeat) rider.ridingEntity).getParent();
            }

            if (rider.ridingEntity instanceof MCH_EntityUavStation) {
                MCH_EntityUavStation uavStation = (MCH_EntityUavStation) rider.ridingEntity;
                return uavStation.getControlAircract();
            }
        }

        return null;
    }

    public static List getCollidingBoundingBoxes(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB) {
        ArrayList collidingBoundingBoxes = new ArrayList();
        collidingBoundingBoxes.clear();
        int i = MathHelper.floor_double(par2AxisAlignedBB.minX);
        int j = MathHelper.floor_double(par2AxisAlignedBB.maxX + 1.0D);
        int k = MathHelper.floor_double(par2AxisAlignedBB.minY);
        int l = MathHelper.floor_double(par2AxisAlignedBB.maxY + 1.0D);
        int i1 = MathHelper.floor_double(par2AxisAlignedBB.minZ);
        int j1 = MathHelper.floor_double(par2AxisAlignedBB.maxZ + 1.0D);

        for (int d0 = i; d0 < j; ++d0) {
            for (int l1 = i1; l1 < j1; ++l1) {
                if (par1Entity.worldObj.blockExists(d0, 64, l1)) {
                    for (int list = k - 1; list < l; ++list) {
                        Block j2 = W_WorldFunc.getBlock(par1Entity.worldObj, d0, list, l1);
                        if (j2 != null) {
                            j2.addCollisionBoxesToList(par1Entity.worldObj, d0, list, l1, par2AxisAlignedBB, collidingBoundingBoxes, par1Entity);
                        }
                    }
                }
            }
        }

        double var15 = 0.25D;
        List var16 = par1Entity.worldObj.getEntitiesWithinAABBExcludingEntity(par1Entity, par2AxisAlignedBB.expand(var15, var15, var15));

        for (int var17 = 0; var17 < var16.size(); ++var17) {
            Entity entity = (Entity) var16.get(var17);
            if (!W_Lib.isEntityLivingBase(entity) && !(entity instanceof MCH_EntitySeat) && !(entity instanceof MCH_EntityHitBox)) {
                AxisAlignedBB axisalignedbb1 = entity.getBoundingBox();
                if (axisalignedbb1 != null && axisalignedbb1.intersectsWith(par2AxisAlignedBB)) {
                    collidingBoundingBoxes.add(axisalignedbb1);
                }

                axisalignedbb1 = par1Entity.getCollisionBox(entity);
                if (axisalignedbb1 != null && axisalignedbb1.intersectsWith(par2AxisAlignedBB)) {
                    collidingBoundingBoxes.add(axisalignedbb1);
                }
            }
        }

        return collidingBoundingBoxes;
    }

    public static float abs(float p_76135_0_) {
        return p_76135_0_ >= 0.0F ? p_76135_0_ : -p_76135_0_;
    }

    public static double abs(double p_76135_0_) {
        return p_76135_0_ >= 0.0D ? p_76135_0_ : -p_76135_0_;
    }

    protected void entityInit() {
        super.entityInit();
        this.getDataWatcher().addObject(20, "");
        this.getDataWatcher().addObject(19, 0);
        this.getDataWatcher().addObject(23, 0);
        this.getDataWatcher().addObject(24, 0);
        this.getDataWatcher().addObject(25, 0);
        this.getDataWatcher().addObject(21, "");
        this.getDataWatcher().addObject(22, 0);
        this.getDataWatcher().addObject(26, 0.0F);
        this.getDataWatcher().addObject(27, "");
        this.getDataWatcher().addObject(28, "");
        this.getDataWatcher().addObject(29, 0);
        this.getDataWatcher().addObject(31, 0);
        if (!super.worldObj.isRemote) {
            this.setCommonStatus(3, MCH_Config.InfinityAmmo.prmBool);
            this.setCommonStatus(4, MCH_Config.InfinityFuel.prmBool);
            setGunnerStatus(true);
        }

        this.getEntityData().setString("EntityType", this.getEntityType());
    }

    public float getServerRoll() {
        return this.getDataWatcher().getWatchableObjectFloat(26);
    }

    public float getRotYaw() {
        return super.rotationYaw;
    }

    public void setRotYaw(float f) {
        super.rotationYaw = f;
    }

    public float getRotPitch() {
        return super.rotationPitch;
    }

    public void setRotPitch(float f) {
        super.rotationPitch = f;
    }

    public float getRotRoll() {
        return this.rotationRoll;
    }

    public void setRotRoll(float f) {
        this.rotationRoll = f;
    }

    public void setRotPitch(float f, String msg) {
        this.setRotPitch(f);
    }

    public void applyOnGroundPitch(float factor) {
        if (this.getAcInfo() != null) {
            float ogp = this.getAcInfo().onGroundPitch;
            float pitch = this.getRotPitch();
            pitch -= ogp;
            pitch *= factor;
            pitch += ogp;
            this.setRotPitch(pitch, "applyOnGroundPitch");
        }

        this.setRotRoll(this.getRotRoll() * factor);
    }

    public float calcRotYaw(float partialTicks) {
        float prevYaw = super.prevRotationYaw;
        float currentYaw = this.getRotYaw();

        while (currentYaw - prevYaw < -180.0F) {
            currentYaw += 360.0F;
        }
        while (currentYaw - prevYaw >= 180.0F) {
            currentYaw -= 360.0F;
        }

        return prevYaw + (currentYaw - prevYaw) * partialTicks;
    }

    public float calcRotPitch(float partialTicks) {
        return super.prevRotationPitch + (this.getRotPitch() - super.prevRotationPitch) * partialTicks;
    }

    public float calcRotRoll(float partialTicks) {
        return this.prevRotationRoll + (this.getRotRoll() - this.prevRotationRoll) * partialTicks;
    }

    protected void setRotation(float y, float p) {
        this.setRotYaw(y % 360.0F);
        this.setRotPitch(p % 360.0F);
    }

    public boolean isInfinityAmmo(Entity player) {
        return this.isCreative(player) || this.getCommonStatus(3);
    }

    public boolean isInfinityFuel(Entity player, boolean checkOtherSeet) {
        if (!this.isCreative(player) && !this.getCommonStatus(4)) {
            if (checkOtherSeet) {
                MCH_EntitySeat[] arr$ = this.getSeats();
                int len$ = arr$.length;

                for (int i$ = 0; i$ < len$; ++i$) {
                    MCH_EntitySeat seat = arr$[i$];
                    if (seat != null && this.isCreative(seat.riddenByEntity)) {
                        return true;
                    }
                }
            }

            return false;
        } else {
            return true;
        }
    }

    public void setCommand(String s, EntityPlayer player) {
        if (!super.worldObj.isRemote && MCH_Command.canUseCommand(player)) {
            this.setCommandForce(s);
        }

    }

    public void setCommandForce(String s) {
        if (!super.worldObj.isRemote) {
            this.getDataWatcher().updateObject(27, s);
        }

    }

    public String getCommand() {
        return this.getDataWatcher().getWatchableObjectString(27);
    }

    public String getKindName() {
        return "";
    }

    public String getEntityType() {
        return "";
    }

    public String getTypeName() {
        return this.getDataWatcher().getWatchableObjectString(20);
    }

    public void setTypeName(String s) {
        String beforeType = this.getTypeName();
        if (s != null && !s.isEmpty() && s.compareTo(beforeType) != 0) {
            this.getDataWatcher().updateObject(20, s);
            this.changeType(s);
            this.initRotationYaw(this.getRotYaw());
        }

    }

    public abstract void changeType(String var1);

    public boolean isTargetDrone() {
        return this.getAcInfo() != null && this.getAcInfo().isTargetDrone;
    }

    public boolean isUAV() {
        return this.getAcInfo() != null && this.getAcInfo().isUAV;
    }

    public boolean isSmallUAV() {
        return this.getAcInfo() != null && this.getAcInfo().isSmallUAV;
    }

    public boolean isAlwaysCameraView() {
        return this.getAcInfo() != null && this.getAcInfo().alwaysCameraView;
    }

    public float getStealth() {
        return this.getAcInfo() != null ? this.getAcInfo().stealth : 0.0F;
    }

    public MCH_AircraftInventory getGuiInventory() {
        return this.inventory;
    }

    public void openGui(EntityPlayer player) {
        if (!super.worldObj.isRemote) {
            player.openGui(MCH_MOD.instance, 1, super.worldObj, (int) super.posX, (int) super.posY, (int) super.posZ);
        }

    }

    public MCH_EntityUavStation getUavStation() {
        return this.isUAV() ? this.uavStation : null;
    }

    public void setUavStation(MCH_EntityUavStation uavSt) {
        this.uavStation = uavSt;
        if (!super.worldObj.isRemote) {
            if (uavSt != null) {
                this.getDataWatcher().updateObject(22, W_Entity.getEntityId(uavSt));
            } else {
                this.getDataWatcher().updateObject(22, 0);
            }
        }

    }

    public boolean isCreative(Entity entity) {
        if (entity instanceof EntityPlayer) {
            return ((EntityPlayer) entity).capabilities.isCreativeMode;
        }
        return entity instanceof MCH_EntityGunner && ((MCH_EntityGunner) entity).isCreative;
    }

    public Entity getRiddenByEntity() {
        return this.isUAV() && this.uavStation != null ? this.uavStation.riddenByEntity : super.riddenByEntity;
    }

    public boolean getCommonStatus(int bit) {
        return (this.commonStatus >> bit & 1) != 0;
    }

    public void setCommonStatus(int bit, boolean b) {
        this.setCommonStatus(bit, b, false);
    }

    public void setCommonStatus(int bit, boolean b, boolean writeClient) {
        if (!super.worldObj.isRemote || writeClient) {
            int bofore = this.commonStatus;
            int mask = 1 << bit;
            if (b) {
                this.commonStatus |= mask;
            } else {
                this.commonStatus &= ~mask;
            }

            if (bofore != this.commonStatus) {
                this.getDataWatcher().updateObject(23, this.commonStatus);
            }
        }

    }

    public double getThrottle() {
        return 0.05D * (double) this.getDataWatcher().getWatchableObjectInt(29);
    }

    public void setThrottle(double t) {
        int n = (int) (t * 20.0D);
        if (n == 0 && t > 0.0D) {
            n = 1;
        }

        this.getDataWatcher().updateObject(29, n);
    }

    public int getMaxHP() {
        return this.getAcInfo() != null ? this.getAcInfo().maxHp : 100;
    }

    public int getHP() {
        return Math.max(this.getMaxHP() - this.getDamageTaken(), 0);
    }

    public int getDamageTaken() {
        return this.getDataWatcher().getWatchableObjectInt(19);
    }

    public void setDamageTaken(int par1) {
        if (par1 < 0) {
            par1 = 0;
        }

        if (par1 > this.getMaxHP()) {
            par1 = this.getMaxHP();
        }

        this.getDataWatcher().updateObject(19, par1);
    }

    public void destroyAircraft(DamageSource source) {
        this.setSearchLight(false);
        this.switchHoveringMode(false);
        this.switchGunnerMode(false);

        for (int entity = 0; entity < this.getSeatNum() + 1; ++entity) {
            Entity e = this.getEntityBySeatId(entity);
            if (e instanceof EntityPlayer) {
                this.switchCameraMode((EntityPlayer) e, 0);
            }
        }

        if (isTargetDrone()) {
            setDespawnCount(20 * MCH_Config.DespawnCount.prmInt / 10);
        } else {
            setDespawnCount(20 * MCH_Config.DespawnCount.prmInt);
        }

        this.rotDestroyedPitch = super.rand.nextFloat() - 0.5F;
        this.rotDestroyedRoll = (super.rand.nextFloat() - 0.5F) * 0.5F;
        this.rotDestroyedYaw = 0.0F;
        if (this.isUAV() && this.getRiddenByEntity() != null) {
            this.getRiddenByEntity().mountEntity((Entity) null);
        }

        if (!super.worldObj.isRemote) {
            this.ejectSeat(this.getRiddenByEntity());
            Entity var3 = this.getEntityBySeatId(1);
            if (var3 != null) {
                this.ejectSeat(var3);
            }

            float dmg = MCH_Config.KillPassengersWhenDestroyed.prmBool ? 100000.0F : 0.001F;
            DamageSource damageSource = DamageSource.generic; // 默认的伤害来源为generic
            if (this.worldObj.difficultySetting.getDifficultyId() == 0) {
                // 如果最后攻击这个实体的是玩家，创建一个基于玩家的伤害来源
                if (this.lastAttackedEntity instanceof EntityPlayer) {
                    damageSource = DamageSource.causePlayerDamage((EntityPlayer) this.lastAttackedEntity);
                }
            } else {
                // 如果世界难度不为和平模式，创建一个基于爆炸的伤害来源
                damageSource = DamageSource.setExplosionSource(new Explosion(this.worldObj, this.lastAttackedEntity,
                    this.posX, this.posY, this.posZ, 1.0F));
            }
            if (source != null) {
                damageSource = source;
            }
            // 如果当前实体存在，应用伤害
            if (this.riddenByEntity != null) {
                this.riddenByEntity.attackEntityFrom(damageSource, dmg);
            }
            // 遍历所有座位上的实体，如果座位上有实体，应用伤害
            for (MCH_EntitySeat seat : getSeats()) {
                if (seat != null && seat.riddenByEntity != null) {
                    seat.riddenByEntity.attackEntityFrom(damageSource, dmg);
                }
            }
        }
    }

    public boolean isDestroyed() {
        return this.getDespawnCount() > 0;
    }

    public int getDespawnCount() {
        return this.despawnCount;
    }

    public void setDespawnCount(int despawnCount) {
        this.despawnCount = despawnCount;
    }

    public boolean isEntityRadarMounted() {
        return this.getAcInfo() != null ? this.getAcInfo().isEnableEntityRadar : false;
    }

    public boolean canFloatWater() {
        return this.getAcInfo() != null && this.getAcInfo().isFloat && !this.isDestroyed();
    }

    @SideOnly(Side.CLIENT)
    public int getBrightnessForRender(float par1) {
        if (this.haveSearchLight() && this.isSearchLightON()) {
            return 15728880;
        } else {
            int i = MathHelper.floor_double(super.posX);
            int j = MathHelper.floor_double(super.posZ);
            if (super.worldObj.blockExists(i, 0, j)) {
                double d0 = (super.boundingBox.maxY - super.boundingBox.minY) * 0.66D;
                float fo = this.getAcInfo() != null ? this.getAcInfo().submergedDamageHeight : 0.0F;
                if (this.canFloatWater()) {
                    fo = this.getAcInfo().floatOffset;
                    if (fo < 0.0F) {
                        fo = -fo;
                    }

                    ++fo;
                }

                int k = MathHelper.floor_double(super.posY + (double) fo - (double) super.yOffset + d0);
                int val = super.worldObj.getLightBrightnessForSkyBlocks(i, k, j, 0);
                int low = val & '\uffff';
                int high = val >> 16 & '\uffff';
                if (high < this.brightnessHigh) {
                    if (this.brightnessHigh > 0 && this.getCountOnUpdate() % 2 == 0) {
                        --this.brightnessHigh;
                    }
                } else if (high > this.brightnessHigh) {
                    this.brightnessHigh += 4;
                    if (this.brightnessHigh > 240) {
                        this.brightnessHigh = 240;
                    }
                }

                return this.brightnessHigh << 16 | low;
            } else {
                return 0;
            }
        }
    }

    public MCH_AircraftInfo.CameraPosition getCameraPosInfo() {
        if (this.getAcInfo() == null) {
            return null;
        } else {
            Entity player = MCH_Lib.getClientPlayer();
            int sid = this.getSeatIdByEntity(player);
            return sid == 0 && this.canSwitchCameraPos() && this.getCameraId() > 0 && this.getCameraId() < this.getAcInfo().cameraPosition.size() ? (MCH_AircraftInfo.CameraPosition) this.getAcInfo().cameraPosition.get(this.getCameraId()) : (sid > 0 && sid < this.getSeatsInfo().length && this.getSeatsInfo()[sid].invCamPos ? this.getSeatsInfo()[sid].getCamPos() : (MCH_AircraftInfo.CameraPosition) this.getAcInfo().cameraPosition.get(0));
        }
    }

    public int getCameraId() {
        return this.cameraId;
    }

    public void setCameraId(int cameraId) {
        MCH_Lib.DbgLog(true, "MCH_EntityAircraft.setCameraId %d -> %d", new Object[]{Integer.valueOf(this.cameraId), Integer.valueOf(cameraId)});
        this.cameraId = cameraId;
    }

    public boolean canSwitchCameraPos() {
        return this.getCameraPosNum() >= 2;
    }

    public int getCameraPosNum() {
        return this.getAcInfo() != null ? this.getAcInfo().cameraPosition.size() : 1;
    }

    public void onAcInfoReloaded() {
        if (this.getAcInfo() != null) {
            this.setSize(this.getAcInfo().bodyWidth, this.getAcInfo().bodyHeight);
        }
    }

    public void writeSpawnData(ByteBuf buffer) {
        if (this.getAcInfo() != null) {
            buffer.writeFloat(this.getAcInfo().bodyHeight);
            buffer.writeFloat(this.getAcInfo().bodyWidth);
            buffer.writeFloat(this.getAcInfo().thirdPersonDist);
            byte[] name = getTypeName().getBytes();
            buffer.writeShort(name.length);
            buffer.writeBytes(name);
        } else {
            buffer.writeFloat(super.height);
            buffer.writeFloat(super.width);
            buffer.writeFloat(4.0F);
            buffer.writeShort(0);
        }

    }

    public void readSpawnData(ByteBuf additionalData) {
        try {
            float e = additionalData.readFloat();
            float width = additionalData.readFloat();
            this.thirdPersonDist = additionalData.readFloat();
            this.setSize(width, e);
            int len = additionalData.readShort();
            if (len > 0) {
                byte[] dst = new byte[len];
                additionalData.readBytes(dst);
                changeType(new String(dst));
            }
        } catch (Exception var4) {
            MCH_Lib.Log((Entity) this, "readSpawnData error!", new Object[0]);
            var4.printStackTrace();
        }

    }

    protected void readEntityFromNBT(NBTTagCompound nbt) {
        this.setDespawnCount(nbt.getInteger("AcDespawnCount"));
        this.setTextureName(nbt.getString("TextureName"));
        this.setCommonUniqueId(nbt.getString("AircraftUniqueId"));
        this.setRotRoll(nbt.getFloat("AcRoll"));
        this.prevRotationRoll = this.getRotRoll();
        this.prevLastRiderYaw = this.lastRiderYaw = nbt.getFloat("AcLastRYaw");
        this.prevLastRiderPitch = this.lastRiderPitch = nbt.getFloat("AcLastRPitch");
        this.setPartStatus(nbt.getInteger("PartStatus"));
        this.setTypeName(nbt.getString("TypeName"));
        super.readEntityFromNBT(nbt);
        this.getGuiInventory().readEntityFromNBT(nbt);
        this.setCommandForce(nbt.getString("AcCommand"));
        this.setFuel(nbt.getInteger("AcFuel"));
        setGunnerStatus(nbt.getBoolean("AcGunnerStatus"));
        int weaponNum = this.getWeaponNum();
        int[] ammoInMag = nbt.getIntArray("AcWeaponsAmmoInMag");
        int[] restAmmo = nbt.getIntArray("AcWeaponsRestAmmo");
        int[] reloadWait = nbt.getIntArray("AcWeaponsReloadWait");
        if (ammoInMag.length > 0 && restAmmo.length > 0) {
            for (int i = 0; i < weaponNum; ++i) {
                MCH_WeaponSet ws = this.getWeapon(i);
                int inMag = i < ammoInMag.length ? ammoInMag[i] : 0;
                int rest = i < restAmmo.length ? restAmmo[i] : 0;
                ws.setAmmoNum(Math.max(0, inMag));
                ws.setRestAllAmmoNum(Math.max(0, rest));
                ws.countReloadWait = i < reloadWait.length ? Math.max(0, reloadWait[i]) : 0;
            }
        } else {
            // Backward compatibility with legacy save format (only total ammo).
            int[] wa_list = nbt.getIntArray("AcWeaponsAmmo");
            for (int i = 0; i < wa_list.length && i < weaponNum; ++i) {
                this.getWeapon(i).setRestAllAmmoNum(wa_list[i]);
                this.getWeapon(i).reloadMag();
            }
        }

        if (this.getDespawnCount() > 0) {
            this.setDamageTaken(this.getMaxHP());
        } else if (nbt.hasKey("AcDamage")) {
            this.setDamageTaken(nbt.getInteger("AcDamage"));
        }

        if (this.haveSearchLight() && nbt.hasKey("SearchLight")) {
            this.setSearchLight(nbt.getBoolean("SearchLight"));
        }

        if (nbt.hasKey("AcERAState")) {
            this.applyERAStateString(nbt.getString("AcERAState"));
        }
        if (!super.worldObj.isRemote) {
            this.syncERAStateWatcher(true);
        }
        this.dismountedUserCtrl = nbt.getBoolean("AcDismounted");
    }

    protected void writeEntityToNBT(NBTTagCompound nbt) {
        nbt.setString("TextureName", this.getTextureName());
        nbt.setString("AircraftUniqueId", this.getCommonUniqueId());
        nbt.setString("TypeName", this.getTypeName());
        nbt.setInteger("PartStatus", this.getPartStatus() & this.getLastPartStatusMask());
        nbt.setInteger("AcFuel", this.getFuel());
        nbt.setInteger("AcDespawnCount", this.getDespawnCount());
        nbt.setFloat("AcRoll", this.getRotRoll());
        nbt.setBoolean("SearchLight", this.isSearchLightON());
        nbt.setFloat("AcLastRYaw", this.getLastRiderYaw());
        nbt.setFloat("AcLastRPitch", this.getLastRiderPitch());
        nbt.setString("AcCommand", this.getCommand());
        if (!nbt.hasKey("AcGunnerStatus"))
            setGunnerStatus(true);
        nbt.setBoolean("AcGunnerStatus", getGunnerStatus());
        super.writeEntityToNBT(nbt);
        this.getGuiInventory().writeEntityToNBT(nbt);
        int weaponNum = this.getWeaponNum();
        int[] wa_list = new int[weaponNum];
        int[] ammoInMag = new int[weaponNum];
        int[] restAmmo = new int[weaponNum];
        int[] reloadWait = new int[weaponNum];
        for (int i = 0; i < weaponNum; ++i) {
            MCH_WeaponSet ws = this.getWeapon(i);
            ammoInMag[i] = ws.getAmmoNum();
            restAmmo[i] = ws.getRestAllAmmoNum();
            reloadWait[i] = ws.countReloadWait;
            // Keep legacy key for compatibility with old builds/tools.
            wa_list[i] = restAmmo[i] + ammoInMag[i];
        }
        nbt.setTag("AcWeaponsAmmo", W_NBTTag.newTagIntArray("AcWeaponsAmmo", wa_list));
        nbt.setTag("AcWeaponsAmmoInMag", W_NBTTag.newTagIntArray("AcWeaponsAmmoInMag", ammoInMag));
        nbt.setTag("AcWeaponsRestAmmo", W_NBTTag.newTagIntArray("AcWeaponsRestAmmo", restAmmo));
        nbt.setTag("AcWeaponsReloadWait", W_NBTTag.newTagIntArray("AcWeaponsReloadWait", reloadWait));
        nbt.setInteger("AcDamage", this.getDamageTaken());
        nbt.setString("AcERAState", this.buildERAStateString());
        nbt.setBoolean("AcDismounted", this.dismountedUserCtrl);
    }

    public boolean attackEntityFrom(DamageSource damageSource, float org_damage) {
        if (ironCurtainRunningTick > 0) {
            return false;
        }
        float damageFactor = this.lastBBDamageFactor;
        lastBBDamageFactor = 1.0F;
        String bbName = this.lastBBName;
        lastBBName = null;
        int bbIndex = this.lastBBIndex;
        this.lastBBIndex = -1;
        boolean damageExplosion = false;
        if (this.isEntityInvulnerable()) {
            return false;
        } else if (super.isDead) {
            return false;
        } else if (this.timeSinceHit > 0) {
            return false;
        } else {
            String dmt = damageSource.getDamageType();
            if (dmt.equalsIgnoreCase("inFire") && !damageSource.isProjectile()) {
                return false;
            } else if (dmt.equalsIgnoreCase("cactus")) {
                return false;
            } else if (super.worldObj.isRemote) {
                return true;
            } else {
                float damage = MCH_Config.applyDamageByExternal(this, damageSource, org_damage);
                if (!MCH_Multiplay.canAttackEntity(damageSource, this)) {
                    return false;
                } else {
                    Entity entity = damageSource.getEntity();
                    if (this.isFriendlyPlayerAttackingGunnerPilotedVehicle(entity)) {
                        return false;
                    }
                    float impactAngleForDisplay = -1.0F;
                    if (entity instanceof EntityLivingBase)
                        this.lastAttackedEntity = entity;
                    MCH_IndicatedDamageSource indicated = (damageSource instanceof MCH_IndicatedDamageSource) ? (MCH_IndicatedDamageSource)damageSource : null;
                    if (indicated != null && this.getAcInfo() != null) {
                        Vec3 impactNormal = getImpactNormalForHit(indicated.hitPos, bbIndex);
                        if (impactNormal != null && indicated.dir != null) {
                            float impactAngle = getImpactAngleDeg(indicated.dir, impactNormal);
                            impactAngleForDisplay = impactAngle;
                            if (this.getAcInfo().isImpactRicochet(impactAngle)) {
                                applyBulletRicochet(damageSource, impactNormal, indicated.hitPos);
                                notifyRicochetHit(impactAngle);
                                return false;
                            }
                            damageFactor *= this.getAcInfo().getImpactAngleCoefficientValue(impactAngle);
                        }
                    }
                    if (dmt.equalsIgnoreCase("lava")) {
                        if (!damageSource.isProjectile()) {
                            this.setDamageTaken(this.getDamageTaken() + (int) damage);
                        }
                        this.timeSinceHit = 0;
                    }
                    if (dmt.startsWith("explosion") || MCH_FMURUtil.isFMURExplosion(damageSource)) {
                        if (!this.isDestroyed()) {
                            damageExplosion = true;
                            this.timeSinceHit = 0;
                            //触发载具伤害事件
                            damage *= getAcInfo().armorExplosionDamageMultiplier;
                            String name = (String) getAcInfo().displayNameLang.get("en_US");
                            if (lastRiddenByEntity instanceof EntityPlayer && lastAttackedEntity instanceof EntityLivingBase) {
                                EntityPlayer player = (EntityPlayer) lastRiddenByEntity;
                                if (!player.isOnSameTeam((EntityLivingBase) lastAttackedEntity)) {
                                    AircraftDamageEvent e = new AircraftDamageEvent(lastAttackedEntity.getCommandSenderName(), name, damage, getMaxHP());
                                    MinecraftForge.EVENT_BUS.post(e);
                                }
                            }
                            if (lastAttackedEntity instanceof EntityPlayerMP) {
                                MCH_MOD.getPacketHandler().sendTo(new PacketBoundingBoxHit(getEntityId(), "message.mcheli.overpressure", damage * 100 / getMaxHP(), (byte) 1), (EntityPlayerMP) lastAttackedEntity);
                            } else if (lastAttackedEntity instanceof MCH_DummyEntityPlayer) {
                                EntityPlayer mp = worldObj.getPlayerEntityByName(lastAttackedEntity.getCommandSenderName());
                                if (mp instanceof EntityPlayerMP) {
                                    MCH_MOD.getPacketHandler().sendTo(new PacketBoundingBoxHit(getEntityId(), "message.mcheli.overpressure", damage * 100 / getMaxHP(), (byte) 1), (EntityPlayerMP) mp);
                                }
                            }
                            this.setDamageTaken(this.getDamageTaken() + (int) damage);
                        }
                    } else if (this.isMountedEntity(damageSource.getEntity())) {
                        return false;
                    }
                    if (dmt.equalsIgnoreCase("onFire")) {
                        this.setDamageTaken(this.getDamageTaken() + (int) damage);
                        this.timeSinceHit = 0;
                    }
                    boolean isCreative = false;
                    boolean isSneaking = false;
                    boolean isDamageSourcePlayer = false;
                    boolean playDamageSound = false;
                    if (entity instanceof EntityPlayer) {
                        EntityPlayer cmd = (EntityPlayer) entity;
                        isCreative = cmd.capabilities.isCreativeMode;
                        isSneaking = cmd.isSneaking();
                        if (dmt.equalsIgnoreCase("player")) {
                            if (isCreative) {
                                isDamageSourcePlayer = true;
                            } else if (getAcInfo() != null) {
                                if (!MCH_Config.PreventingBroken.prmBool) {
                                    if (MCH_Config.BreakableOnlyPickaxe.prmBool) {
                                        if (cmd.getCurrentEquippedItem() != null && cmd.getCurrentEquippedItem().getItem() instanceof ItemPickaxe) {
                                            isDamageSourcePlayer = true;
                                        }
                                    } else {
                                        isDamageSourcePlayer = !this.isRidePlayer();
                                    }
                                }
                            }
                        }

                        W_WorldFunc.MOD_playSoundAtEntity(this, "hit", 1.0F, 1.0F);
                    } else {
                        playDamageSound = true;
                    }

                    if (!this.isDestroyed()) {
                        if (!isDamageSourcePlayer && !damageExplosion) {
                            MCH_AircraftInfo acInfo = this.getAcInfo();
                            if (acInfo != null) {
                                if (!dmt.equalsIgnoreCase("lava") && !dmt.equalsIgnoreCase("onFire")) {
                                    if (damage > acInfo.armorMaxDamage) {
                                        damage = acInfo.armorMaxDamage;
                                    }
                                    if (damageFactor <= 1.0F) {
                                        damage *= damageFactor;
                                    }
                                    damage *= acInfo.armorDamageFactor;
                                    damage -= acInfo.armorMinDamage;
                                    if (damage <= 0.0F) {
                                        MCH_Lib.DbgLog(super.worldObj, "MCH_EntityAircraft.attackEntityFrom:no damage=%.1f -> %.1f(factor=%.2f):%s", org_damage, damage, damageFactor, dmt);
                                        return false;
                                    }
                                    if (damageFactor > 1.0F) {
                                        damage *= damageFactor;
                                    }
                                }
                            }
                            MCH_Lib.DbgLog(super.worldObj, "MCH_EntityAircraft.attackEntityFrom:damage=%.1f(factor=%.2f):%s", damage, damageFactor, dmt);
                            if (bbName != null) {
                                MCH_BoundingBox eraBox = this.getActiveERABoundingBox(bbName, bbIndex);
                                if (eraBox != null && damage > eraBox.eraMinDamage) {
                                    eraBox.eraActive = false;
                                    this.syncERAStateWatcher(false);
                                    if (eraBox.eraExplosion > 0.0F) {
                                        MCH_ExplosionParam eraExplosion = MCH_ExplosionParam.builder()
                                            .exploder(null)
                                            .player(entity instanceof EntityPlayer ? (EntityPlayer) entity : null)
                                            .x(eraBox.center.xCoord).y(eraBox.center.yCoord).z(eraBox.center.zCoord)
                                            .size(eraBox.eraExplosion)
                                            .sizeBlock(0.0F)
                                            .isPlaySound(true)
                                            .isSmoking(true)
                                            .isFlaming(false)
                                            .isDestroyBlock(false)
                                            .countSetFireEntity(0)
                                            .isInWater(this.isInWater())
                                            .build();
                                        MCH_Explosion.newExplosion(super.worldObj, eraExplosion);
                                    }
                                }
                            }
                            //触发载具伤害事件
                            String name = (String) getAcInfo().displayNameLang.get("en_US");
                            if (lastRiddenByEntity instanceof EntityPlayer && lastAttackedEntity instanceof EntityLivingBase) {
                                EntityPlayer player = (EntityPlayer) lastRiddenByEntity;
                                if (!player.isOnSameTeam((EntityLivingBase) lastAttackedEntity)) {
                                    AircraftDamageEvent e = new AircraftDamageEvent(lastAttackedEntity.getCommandSenderName(), name, damage, getMaxHP());
                                    MinecraftForge.EVENT_BUS.post(e);
                                }
                            }
                            if (damageSource instanceof MCH_IndicatedDamageSource && damageSource.getSourceOfDamage() != null) {
                                MCH_IndicatedDamageSource ids = (MCH_IndicatedDamageSource) damageSource;
                                // 击中点的世界绝对坐标
                                Vec3 absHitPos = ids.hitPos;
                                // 弹药的世界运动方向（未经归一化）
                                Vec3 absBulletDir = ids.dir;

                                // 计算从载具中心到击中点的向量（世界坐标系）
                                double dx = absHitPos.xCoord - posX;
                                double dy = absHitPos.yCoord - posY;
                                double dz = absHitPos.zCoord - posZ;

                                // 获取载具当前姿态角：水平旋转（航向）、俯仰、滚转
                                float yaw = this.rotationYaw;   // 或者使用ac.getRotYaw()，取决于具体实现
                                float pitch = this.rotationPitch; // 或者ac.getRotPitch()
                                float roll = this.rotationRoll; // 或者ac.getRotRoll()，需从实体中获取当前滚转角

                                // 将世界坐标系向量转换到载具局部坐标系
                                Vec3 relHitPos = MCH_Math.rotateWorldVecToLocal(dx, dy, dz, yaw, pitch, roll);
                                // 将子弹运动方向转换到载具局部坐标系，并归一化为纯方向
                                Vec3 relDir = MCH_Math.rotateWorldVecToLocal(absBulletDir.xCoord, absBulletDir.yCoord, absBulletDir.zCoord, yaw, pitch, roll).normalize();

                                String weaponName = "Hit";
                                Entity hitEntity = damageSource.getSourceOfDamage();
                                if (hitEntity instanceof MCH_EntityBaseBullet && ((MCH_EntityBaseBullet) hitEntity).getInfo() != null) {
                                    weaponName = ((MCH_EntityBaseBullet) hitEntity).getInfo().displayName;
                                }

                                // 将相对坐标和方向发送给客户端，客户端会根据这些信息绘制受击指示器
                                MCH_MOD.getPacketHandler().sendToAll(new PacketDamageIndicator(
                                    relHitPos.xCoord, relHitPos.yCoord, relHitPos.zCoord,
                                    relDir.xCoord, relDir.yCoord, relDir.zCoord,
                                    weaponName, bbName, damage * 100 / getMaxHP(), getEntityId()));
                            }
                            if (bbName != null && lastAttackedEntity instanceof EntityPlayer) {
                                MCH_MOD.getPacketHandler().sendTo(new PacketBoundingBoxHit(getEntityId(), bbName, damage * 100 / getMaxHP(), (byte) 0, impactAngleForDisplay), (EntityPlayerMP) lastAttackedEntity);
                            }
                            this.setDamageTaken(this.getDamageTaken() + (int) damage);
                        }
                        this.setBeenAttacked();
                        if (this.getDamageTaken() >= this.getMaxHP() || isDamageSourcePlayer) {
                            if (!isDamageSourcePlayer) {
                                this.setDamageTaken(this.getMaxHP());
                                //触发载具击杀事件
                                String name = (String) getAcInfo().displayNameLang.get("en_US");
                                if (lastRiddenByEntity instanceof EntityPlayer && lastAttackedEntity instanceof EntityLivingBase) {
                                    EntityPlayer player = (EntityPlayer) lastRiddenByEntity;
                                    if (!player.isOnSameTeam((EntityLivingBase) lastAttackedEntity)) {
                                        AircraftDestoryEvent e = new AircraftDestoryEvent(lastAttackedEntity.getCommandSenderName(), name);
                                        MinecraftForge.EVENT_BUS.post(e);
                                    }
                                }
                                this.destroyAircraft(damageSource);
                                this.timeSinceHit = 20;
                                String cmd2 = this.getCommand().trim();
                                if (cmd2.startsWith("/")) {
                                    cmd2 = cmd2.substring(1);
                                }
                                if (!cmd2.isEmpty()) {
                                    MCH_DummyCommandSender.execCommand(cmd2);
                                }
                                if (dmt.equalsIgnoreCase("inWall")) {
                                    this.explosionByCrash(0.0D);
                                    this.damageSinceDestroyed = this.getMaxHP();
                                } else {
                                    MCH_ExplosionParam p = MCH_ExplosionParam.builder()
                                        .exploder(null)
                                        .player(entity instanceof EntityPlayer ? (EntityPlayer) entity : null)
                                        .x(super.posX).y(super.posY).z(super.posZ)
                                        .size(this.getAcInfo().explosionSizeByCrash)
                                        .sizeBlock(2.0F)
                                        .isPlaySound(true)
                                        .isSmoking(true)
                                        .isFlaming(true)
                                        .isDestroyBlock(true)
                                        .countSetFireEntity(5)
                                        .isInWater(false)
                                        .build();
                                    MCH_Explosion.newExplosion(super.worldObj, p);
                                }
                            } else {
                                if (this.getAcInfo() != null && this.getAcInfo().getItem() != null) {
                                    if (isCreative) {
                                        if (MCH_Config.DropItemInCreativeMode.prmBool && !isSneaking) {
                                            this.dropItemWithOffset(this.getAcInfo().getItem(), 1, 0.0F);
                                        }
                                        if (!MCH_Config.DropItemInCreativeMode.prmBool && isSneaking) {
                                            this.dropItemWithOffset(this.getAcInfo().getItem(), 1, 0.0F);
                                        }
                                    } else {
                                        this.dropItemWithOffset(this.getAcInfo().getItem(), 1, 0.0F);
                                    }
                                }
                                this.setDead(true);
                            }
                        }
                    } else if (isDamageSourcePlayer && isCreative) {
                        this.setDead(true);
                    }
                    if (playDamageSound) {
                        W_WorldFunc.MOD_playSoundAtEntity(this, "helidmg", 1.0F, 0.9F + super.rand.nextFloat() * 0.1F);
                    }
                    return true;
                }
            }
        }
    }

    public boolean isExploded() {
        return this.isDestroyed() && this.damageSinceDestroyed > this.getMaxHP() / 10 + 1;
    }

    private MCH_BoundingBox getActiveERABoundingBox(String bbName, int bbIndex) {
        if (bbIndex >= 0 && bbIndex < this.extraBoundingBox.length) {
            MCH_BoundingBox bb = this.extraBoundingBox[bbIndex];
            if (bb.isERA && bb.eraActive) {
                return bb;
            }
        }
        if (bbName == null || bbName.isEmpty()) {
            return null;
        }
        for (MCH_BoundingBox bb : this.extraBoundingBox) {
            if (bb.isERA && bb.eraActive && bbName.equalsIgnoreCase(bb.name)) {
                return bb;
            }
        }
        return null;
    }

    private Vec3 getImpactNormalForHit(Vec3 hitPos, int bbIndex) {
        if (hitPos == null) {
            return null;
        }
        if (bbIndex >= 0 && bbIndex < this.extraBoundingBox.length) {
            MCH_BoundingBox bb = this.extraBoundingBox[bbIndex];
            if (bb != null) {
                return getOBBImpactNormal(bb, hitPos);
            }
        }
        return getAabbImpactNormal(this.boundingBox, hitPos);
    }

    private Vec3 getOBBImpactNormal(MCH_BoundingBox bb, Vec3 hitPos) {
        Vec3 rel = hitPos.subtract(bb.center);
        double lx = rel.dotProduct(bb.axisX);
        double ly = rel.dotProduct(bb.axisY);
        double lz = rel.dotProduct(bb.axisZ);
        double dx = Math.abs(Math.abs(lx) - bb.halfWidth);
        double dy = Math.abs(Math.abs(ly) - bb.halfHeight);
        double dz = Math.abs(Math.abs(lz) - bb.halfDepth);
        if (dx <= dy && dx <= dz) {
            return lx >= 0.0D ? bb.axisX : Vec3.createVectorHelper(-bb.axisX.xCoord, -bb.axisX.yCoord, -bb.axisX.zCoord);
        }
        if (dy <= dz) {
            return ly >= 0.0D ? bb.axisY : Vec3.createVectorHelper(-bb.axisY.xCoord, -bb.axisY.yCoord, -bb.axisY.zCoord);
        }
        return lz >= 0.0D ? bb.axisZ : Vec3.createVectorHelper(-bb.axisZ.xCoord, -bb.axisZ.yCoord, -bb.axisZ.zCoord);
    }

    private Vec3 getAabbImpactNormal(AxisAlignedBB bb, Vec3 hitPos) {
        double dMinX = Math.abs(hitPos.xCoord - bb.minX);
        double dMaxX = Math.abs(bb.maxX - hitPos.xCoord);
        double dMinY = Math.abs(hitPos.yCoord - bb.minY);
        double dMaxY = Math.abs(bb.maxY - hitPos.yCoord);
        double dMinZ = Math.abs(hitPos.zCoord - bb.minZ);
        double dMaxZ = Math.abs(bb.maxZ - hitPos.zCoord);
        double minDist = dMinX;
        Vec3 normal = Vec3.createVectorHelper(-1.0D, 0.0D, 0.0D);
        if (dMaxX < minDist) {
            minDist = dMaxX;
            normal = Vec3.createVectorHelper(1.0D, 0.0D, 0.0D);
        }
        if (dMinY < minDist) {
            minDist = dMinY;
            normal = Vec3.createVectorHelper(0.0D, -1.0D, 0.0D);
        }
        if (dMaxY < minDist) {
            minDist = dMaxY;
            normal = Vec3.createVectorHelper(0.0D, 1.0D, 0.0D);
        }
        if (dMinZ < minDist) {
            minDist = dMinZ;
            normal = Vec3.createVectorHelper(0.0D, 0.0D, -1.0D);
        }
        if (dMaxZ < minDist) {
            normal = Vec3.createVectorHelper(0.0D, 0.0D, 1.0D);
        }
        return normal;
    }

    private float getImpactAngleDeg(Vec3 shotDir, Vec3 normal) {
        if (shotDir == null || normal == null) {
            return 0.0F;
        }
        double vLenSq = shotDir.xCoord * shotDir.xCoord + shotDir.yCoord * shotDir.yCoord + shotDir.zCoord * shotDir.zCoord;
        double nLenSq = normal.xCoord * normal.xCoord + normal.yCoord * normal.yCoord + normal.zCoord * normal.zCoord;
        if (vLenSq < 1.0E-8D || nLenSq < 1.0E-8D) {
            return 0.0F;
        }
        Vec3 v = shotDir.normalize();
        Vec3 n = normal.normalize();
        double dot = v.xCoord * n.xCoord + v.yCoord * n.yCoord + v.zCoord * n.zCoord;
        dot = Math.abs(dot);
        if (dot > 1.0D) {
            dot = 1.0D;
        }
        return (float)(Math.acos(dot) * 180.0D / Math.PI);
    }

    private void applyBulletRicochet(DamageSource damageSource, Vec3 normal, Vec3 hitPos) {
        if (damageSource == null || normal == null || hitPos == null) {
            return;
        }
        Entity src = damageSource.getSourceOfDamage();
        if (src instanceof MCH_EntityBaseBullet) {
            ((MCH_EntityBaseBullet)src).applyArmorRicochet(normal, hitPos, 0.7F);
        }
    }

    private void notifyRicochetHit(float impactAngle) {
        if (this.lastAttackedEntity instanceof EntityPlayerMP) {
            MCH_MOD.getPacketHandler().sendTo(new PacketBoundingBoxHit(getEntityId(), "", 0.0F, (byte)2, impactAngle), (EntityPlayerMP)this.lastAttackedEntity);
        } else if (this.lastAttackedEntity instanceof MCH_DummyEntityPlayer) {
            EntityPlayer mp = this.worldObj.getPlayerEntityByName(this.lastAttackedEntity.getCommandSenderName());
            if (mp instanceof EntityPlayerMP) {
                MCH_MOD.getPacketHandler().sendTo(new PacketBoundingBoxHit(getEntityId(), "", 0.0F, (byte)2, impactAngle), (EntityPlayerMP)mp);
            }
        }
    }

    public void destruct() {
        if (this.getRiddenByEntity() != null) {
            this.getRiddenByEntity().mountEntity(null);
        }

        this.setDead(true);
    }

    public EntityItem entityDropItem(ItemStack is, float par2) {
        if (is.stackSize == 0) {
            return null;
        } else {
            this.setAcDataToItem(is);
            return super.entityDropItem(is, par2);
        }
    }

    public void setAcDataToItem(ItemStack is) {
        if (!is.hasTagCompound()) {
            is.setTagCompound(new NBTTagCompound());
        }

        NBTTagCompound nbt = is.getTagCompound();
        nbt.setString("MCH_Command", this.getCommand());
        if (MCH_Config.ItemFuel.prmBool) {
            nbt.setInteger("MCH_Fuel", this.getFuel());
        }

        if (MCH_Config.ItemDamage.prmBool) {
            is.setItemDamage(this.getDamageTaken());
        }

    }

    public void getAcDataFromItem(ItemStack is) {
        if (is.hasTagCompound()) {
            NBTTagCompound nbt = is.getTagCompound();
            this.setCommandForce(nbt.getString("MCH_Command"));
            if (MCH_Config.ItemFuel.prmBool) {
                this.setFuel(nbt.getInteger("MCH_Fuel"));
            }

            if (MCH_Config.ItemDamage.prmBool) {
                this.setDamageTaken(is.getItemDamage());
            }

        }
    }

    public boolean isUseableByPlayer(EntityPlayer player) {
        return this.isUAV() ? super.isUseableByPlayer(player) : (!super.isDead ? (this.getSeatIdByEntity(player) >= 0 ? player.getDistanceSqToEntity(this) <= 4096.0D : player.getDistanceSqToEntity(this) <= 64.0D) : false);
    }

    public void applyEntityCollision(Entity par1Entity) {
    }

    public void addVelocity(double par1, double par3, double par5) {
    }

    public void setVelocity(double par1, double par3, double par5) {
        this.velocityX = super.motionX = par1;
        this.velocityY = super.motionY = par3;
        this.velocityZ = super.motionZ = par5;
    }

    public void onFirstUpdate() {
        if (!super.worldObj.isRemote) {
            this.setCommonStatus(3, MCH_Config.InfinityAmmo.prmBool);
            this.setCommonStatus(4, MCH_Config.InfinityFuel.prmBool);
        }

    }

    public void onRidePilotFirstUpdate() {
        if (super.worldObj.isRemote && W_Lib.isClientPlayer(this.getRiddenByEntity())) {
            this.updateClientSettings(0);
        }

        Entity pilot = this.getRiddenByEntity();
        if (pilot != null) {
            pilot.rotationYaw = this.getLastRiderYaw();
            pilot.rotationPitch = this.getLastRiderPitch();
        }

        this.keepOnRideRotation = false;
        if (this.getAcInfo() != null) {
            if (MCH_ServerSettings.enableDebugFreeLook) {
                String pilotName = pilot instanceof EntityPlayer ? ((EntityPlayer)pilot).getCommandSenderName() : String.valueOf(pilot);
                mcheli.MCH_FreeLookDebug.trace(super.worldObj, pilot, "[RideInit] acId=%d pilot=%s defaultFreelook=%s current=%s",
                    this.getEntityId(), pilotName, this.getAcInfo().defaultFreelook, this.getCommonStatus(1));
            }
            this.switchFreeLookModeClient(this.getAcInfo().defaultFreelook);
        }

    }

    public double getCurrentThrottle() {
        return this.currentThrottle;
    }

    public void setCurrentThrottle(double throttle) {
        this.currentThrottle = throttle;
    }

    public void addCurrentThrottle(double throttle) {
        this.setCurrentThrottle(this.getCurrentThrottle() + throttle);
    }

    public double getPrevCurrentThrottle() {
        return this.prevCurrentThrottle;
    }

    public boolean canMouseRot() {
        return !super.isDead && this.getRiddenByEntity() != null && !this.isDestroyed();
    }

    public boolean canUpdateYaw(Entity player) {
        return this.getRidingEntity() != null ? false : (this.getCountOnUpdate() < 30 ? false : MCH_Lib.getBlockIdY(this, 3, -2) == 0);
    }

    public boolean canUpdatePitch(Entity player) {
        return this.getCountOnUpdate() < 30 ? false : MCH_Lib.getBlockIdY(this, 3, -2) == 0;
    }

    public boolean canUpdateRoll(Entity player) {
        return this.getRidingEntity() != null ? false : (this.getCountOnUpdate() < 30 ? false : MCH_Lib.getBlockIdY(this, 3, -2) == 0);
    }

    public boolean isOverridePlayerYaw() {
        return !this.isFreeLookMode();
    }

    public boolean isOverridePlayerPitch() {
        return !this.isFreeLookMode();
    }

    public double getAddRotationYawLimit() {
        return this.getAcInfo() != null ? 40.0D * (double) this.getAcInfo().mobilityYaw : 40.0D;
    }

    public double getAddRotationPitchLimit() {
        return this.getAcInfo() != null ? 40.0D * (double) this.getAcInfo().mobilityPitch : 40.0D;
    }

    public double getAddRotationRollLimit() {
        return this.getAcInfo() != null ? 40.0D * (double) this.getAcInfo().mobilityRoll : 40.0D;
    }

    public float getYawFactor() {
        return 1.0F;
    }

    public float getPitchFactor() {
        return 1.0F;
    }

    public float getRollFactor() {
        return 1.0F;
    }

    public abstract void onUpdateAngles(float var1);

    public float getControlRotYaw(float mouseX, float mouseY, float tick) {
        return 0.0F;
    }

    public float getControlRotPitch(float mouseX, float mouseY, float tick) {
        return 0.0F;
    }

    public float getControlRotRoll(float mouseX, float mouseY, float tick) {
        return 0.0F;
    }

    public void setAngles(Entity player, boolean fixRot, float fixYaw, float fixPitch, float deltaX, float deltaY, float x, float y, float partialTicks) {
        if (partialTicks < 0.03F) {
            partialTicks = 0.4F;
        }

        if (partialTicks > 0.9F) {
            partialTicks = 0.6F;
        }

        this.lowPassPartialTicks.put(partialTicks);
        partialTicks = this.lowPassPartialTicks.getAvg();
        float ac_pitch = this.getRotPitch();
        float ac_yaw = this.getRotYaw();
        float ac_roll = this.getRotRoll();
        if (this.isFreeLookMode()) {
            y = 0.0F;
            x = 0.0F;
        }

        float yaw = 0.0F;
        float pitch = 0.0F;
        float roll = 0.0F;
        double m_add;
        if (this.canUpdateYaw(player)) {
            m_add = this.getAddRotationYawLimit();
            yaw = this.getControlRotYaw(x, y, partialTicks);
            if ((double) yaw < -m_add) {
                yaw = (float) (-m_add);
            }

            if ((double) yaw > m_add) {
                yaw = (float) m_add;
            }

            yaw = (float) ((double) (yaw * this.getYawFactor()) * 0.06D * (double) partialTicks);
        }

        if (this.canUpdatePitch(player)) {
            m_add = this.getAddRotationPitchLimit();
            pitch = this.getControlRotPitch(x, y, partialTicks);
            if ((double) pitch < -m_add) {
                pitch = (float) (-m_add);
            }

            if ((double) pitch > m_add) {
                pitch = (float) m_add;
            }

            pitch = (float) ((double) (-pitch * this.getPitchFactor()) * 0.06D * (double) partialTicks);
        }

        if (this.canUpdateRoll(player)) {
            m_add = this.getAddRotationRollLimit();
            roll = this.getControlRotRoll(x, y, partialTicks);
            if ((double) roll < -m_add) {
                roll = (float) (-m_add);
            }

            if ((double) roll > m_add) {
                roll = (float) m_add;
            }

            roll = roll * this.getRollFactor() * 0.06F * partialTicks;
        }

        boolean wtQuatAngles = this instanceof MCP_EntityPlane && ((MCP_EntityPlane) this).isWTQuaternionAnglesActive();
        MCH_Math.FVector3D v;
        if (wtQuatAngles) {
            v = ((MCP_EntityPlane) this).computeWTQuaternionEuler(yaw, pitch, roll);
        } else {
            MCH_Math.FMatrix m_add1 = MCH_Math.newMatrix();
            MCH_Math.MatTurnZ(m_add1, roll / 180.0F * 3.1415927F);
            MCH_Math.MatTurnX(m_add1, pitch / 180.0F * 3.1415927F);
            MCH_Math.MatTurnY(m_add1, yaw / 180.0F * 3.1415927F);
            MCH_Math.MatTurnZ(m_add1, (float) ((double) (this.getRotRoll() / 180.0F) * 3.141592653589793D));
            MCH_Math.MatTurnX(m_add1, (float) ((double) (this.getRotPitch() / 180.0F) * 3.141592653589793D));
            MCH_Math.MatTurnY(m_add1, (float) ((double) (this.getRotYaw() / 180.0F) * 3.141592653589793D));
            v = MCH_Math.MatrixToEuler(m_add1);
        }
        if (this.getAcInfo().limitRotation) {
            if (!wtQuatAngles) {
                v.x = MCH_Lib.RNG(v.x, this.getAcInfo().minRotationPitch, this.getAcInfo().maxRotationPitch);
            }
            v.z = MCH_Lib.RNG(v.z, this.getAcInfo().minRotationRoll, this.getAcInfo().maxRotationRoll);
        }

        if (v.z > 180.0F) {
            v.z -= 360.0F;
        }

        if (v.z < -180.0F) {
            v.z += 360.0F;
        }

        this.setRotYaw(v.y);
        this.setRotPitch(v.x);
        this.setRotRoll(v.z);
        this.onUpdateAngles(partialTicks);
        if (this.getAcInfo().limitRotation) {
            if (!wtQuatAngles) {
                v.x = MCH_Lib.RNG(this.getRotPitch(), this.getAcInfo().minRotationPitch, this.getAcInfo().maxRotationPitch);
                this.setRotPitch(v.x);
            }
            v.z = MCH_Lib.RNG(this.getRotRoll(), this.getAcInfo().minRotationRoll, this.getAcInfo().maxRotationRoll);
            this.setRotRoll(v.z);
        }

        float RV = 180.0F;
        if (MathHelper.abs(this.getRotPitch()) > 90.0F) {
            MCH_Lib.DbgLog(true, "MCH_EntityAircraft.setAngles Error:Pitch=%.1f", new Object[]{Float.valueOf(this.getRotPitch())});
        }

        if (this.getRotRoll() > 180.0F) {
            this.setRotRoll(this.getRotRoll() - 360.0F);
        }

        if (this.getRotRoll() < -180.0F) {
            this.setRotRoll(this.getRotRoll() + 360.0F);
        }

        this.prevRotationRoll = this.getRotRoll();
        super.prevRotationPitch = this.getRotPitch();
        if (this.getRidingEntity() == null) {
            super.prevRotationYaw = this.getRotYaw();
        }
        float pseudoCamYawOffset = 0.0F;
        float pseudoCamPitchOffset = 0.0F;
        if (this instanceof MCP_EntityPlane) {
            MCP_EntityPlane plane = (MCP_EntityPlane) this;
            if (plane.isWTPseudoFreeLookCameraActive()) {
                pseudoCamYawOffset = plane.getWTCameraYawOffset();
                pseudoCamPitchOffset = plane.getWTCameraPitchOffset();
            }
        }
        float targetPlayerYaw = this.getRotYaw() + (fixRot ? fixYaw : 0.0F) + pseudoCamYawOffset;
        float targetPlayerPitch = this.getRotPitch() + (fixRot ? fixPitch : 0.0F) + pseudoCamPitchOffset;

        if (!this.isOverridePlayerYaw() && !fixRot) {
            player.setAngles(deltaX, 0.0F);
        } else {
            if (this.getRidingEntity() == null) {
                player.prevRotationYaw = targetPlayerYaw;
            } else {
                if (targetPlayerYaw - player.rotationYaw > 180.0F) {
                    player.prevRotationYaw += 360.0F;
                }

                if (targetPlayerYaw - player.rotationYaw < -180.0F) {
                    player.prevRotationYaw -= 360.0F;
                }
            }

            player.rotationYaw = targetPlayerYaw;
        }

        if (!this.isOverridePlayerPitch() && !fixRot) {
            player.setAngles(0.0F, deltaY);
        } else {
            player.prevRotationPitch = targetPlayerPitch;
            player.rotationPitch = targetPlayerPitch;
        }

        if (this.getRidingEntity() == null && ac_yaw != this.getRotYaw() || ac_pitch != this.getRotPitch() || ac_roll != this.getRotRoll()) {
            this.aircraftRotChanged = true;
        }

    }

    public boolean canSwitchSearchLight(Entity entity) {
        return this.haveSearchLight() && this.getSeatIdByEntity(entity) <= 1;
    }

    public boolean isSearchLightON() {
        return this.getCommonStatus(6);
    }

    public void setSearchLight(boolean onoff) {
        this.setCommonStatus(6, onoff);
    }

    public boolean haveSearchLight() {
        return this.getAcInfo() != null && this.getAcInfo().searchLights.size() > 0;
    }

    public float getSearchLightValue(Entity entity) {
        if (this.haveSearchLight() && this.isSearchLightON()) {
            Iterator i$ = this.getAcInfo().searchLights.iterator();

            while (i$.hasNext()) {
                MCH_AircraftInfo.SearchLight sl = (MCH_AircraftInfo.SearchLight) i$.next();
                Vec3 pos = this.getTransformedPosition(sl.pos);
                double dist = entity.getDistanceSq(pos.xCoord, pos.yCoord, pos.zCoord);
                if (dist > 2.0D && dist < (double) (sl.height * sl.height + 20.0F)) {
                    double cx = entity.posX - pos.xCoord;
                    double cy = entity.posY - pos.yCoord;
                    double cz = entity.posZ - pos.zCoord;
                    double h = 0.0D;
                    double v = 0.0D;
                    float angle1;
                    if (!sl.fixDir) {
                        Vec3 angle = MCH_Lib.RotVec3(0.0D, 0.0D, 1.0D, -this.lastSearchLightYaw + sl.yaw, -this.lastSearchLightPitch + sl.pitch, -this.getRotRoll());
                        h = (double) MCH_Lib.getPosAngle(angle.xCoord, angle.zCoord, cx, cz);
                        v = Math.atan2(cy, Math.sqrt(cx * cx + cz * cz)) * 180.0D / 3.141592653589793D;
                        v = Math.abs(v + (double) this.lastSearchLightPitch + (double) sl.pitch);
                    } else {
                        angle1 = 0.0F;
                        if (sl.steering) {
                            angle1 = this.rotYawWheel * sl.stRot;
                        }

                        Vec3 value = MCH_Lib.RotVec3(0.0D, 0.0D, 1.0D, -this.getRotYaw() + sl.yaw + angle1, -this.getRotPitch() + sl.pitch, -this.getRotRoll());
                        h = (double) MCH_Lib.getPosAngle(value.xCoord, value.zCoord, cx, cz);
                        v = Math.atan2(cy, Math.sqrt(cx * cx + cz * cz)) * 180.0D / 3.141592653589793D;
                        v = Math.abs(v + (double) this.getRotPitch() + (double) sl.pitch);
                    }

                    angle1 = sl.angle * 3.0F;
                    if (h < (double) angle1 && v < (double) angle1) {
                        float value1 = 0.0F;
                        if (h + v < (double) angle1) {
                            value1 = (float) (1440.0D * (1.0D - (h + v) / (double) angle1));
                        }

                        return value1 <= 240.0F ? value1 : 240.0F;
                    }
                }
            }
        }

        return 0.0F;
    }

    public abstract void onUpdateAircraft();

    public void onUpdate() {
        if (this.getCountOnUpdate() < 2) {
            this.prevPosition.clear(Vec3.createVectorHelper(super.posX, super.posY, super.posZ));
        }

        if (ironCurtainRunningTick > 0) {
            ironCurtainRunningTick--;
            ironCurtainWaveTimer++;
            ironCurtainLastFactor = ironCurtainCurrentFactor;
            float waveSpeed = 0.25f;
            ironCurtainCurrentFactor = 0.75f + 0.25f * (float) Math.sin(ironCurtainWaveTimer * waveSpeed);
        } else {
            ironCurtainWaveTimer = 0;
            ironCurtainCurrentFactor = 0.5f;
            ironCurtainLastFactor = 0.5f;
        }

        if (chaffUseTime > 0) {
            chaffUseTime--;
        }
        if (ecmJammerUseTime > 0) {
            ecmJammerUseTime--;
        }
        if (jammingTick > 0) {
            jammingTick--;
        }

        if(!worldObj.isRemote) {
            int hpPer = getHP() * 100 / getMaxHP();
            if(hpPer > 5 && hpPer < getAcInfo().engineShutdownThreshold) {
                if(ticksExisted % 20 == 0) {
                    W_WorldFunc.MOD_playSoundAtEntity(this, "low_health", 1.0F, 1.0F);
                }
            }
            if (!isDestroyed() && hpPer <= 5) {
                if (ticksExisted % 15 == 0) {
                    W_WorldFunc.MOD_playSoundAtEntity(this, "no_health", 1.0F, 1.0F);
                }
            }
        }

        this.prevCurrentThrottle = this.getCurrentThrottle();
        this.lastBBDamageFactor = 1.0F;
        this.lastBBName = null;
        this.lastBBIndex = -1;
        this.lastBBHitNormal = null;
        this.lastBBHitPos = null;
        this.updateControl();
        this.checkServerNoMove();
        this.onUpdate_RidingEntity();

        Iterator itr = this.listUnmountReserve.iterator();
        while (itr.hasNext()) {
            MCH_EntityAircraft.UnmountReserve ft = (MCH_EntityAircraft.UnmountReserve) itr.next();
            if (ft.entity != null && !ft.entity.isDead) {
                ft.entity.setPosition(ft.posX, ft.posY, ft.posZ);
                ft.entity.fallDistance = super.fallDistance;
            }
            if (ft.cnt > 0) {
                --ft.cnt;
            }
            if (ft.cnt == 0) {
                itr.remove();
            }
        }

        if (this.isDestroyed() && this.getCountOnUpdate() % 10 == 0) {
            for (int sid = 0; sid < this.getSeatNum() + 1; ++sid) {
                Entity entity = this.getEntityBySeatId(sid);
                if (entity != null && (sid != 0 || !this.isUAV())) {
                    if (MCH_Config.applyDamageVsEntity(entity, DamageSource.inFire, 1.0F) > 0.0F) {
                        entity.setFire(5);
                    }
                }
            }
        }

        if ((this.aircraftRotChanged || this.aircraftRollRev) && super.worldObj.isRemote && this.getRiddenByEntity() != null) {
            MCH_PacketIndRotation.send(this);
            this.aircraftRotChanged = false;
            this.aircraftRollRev = false;
        }

        if (!super.worldObj.isRemote && (int) this.prevRotationRoll != (int) this.getRotRoll()) {
            float var8 = MathHelper.wrapAngleTo180_float(this.getRotRoll());
            this.getDataWatcher().updateObject(26, var8);
        }

        this.prevRotationRoll = this.getRotRoll();
        if (!super.worldObj.isRemote && this.isTargetDrone() && !this.isDestroyed() && this.getCountOnUpdate() > 20 && !this.canUseFuel()) {
            this.setDamageTaken(this.getMaxHP());
            this.destroyAircraft(null);
            MCH_ExplosionParam param = MCH_ExplosionParam.builder()
                .exploder(null)
                .player(null)
                .x(super.posX).y(super.posY).z(super.posZ)
                .size(2.0F)
                .sizeBlock(2.0F)
                .isPlaySound(true)
                .isSmoking(true)
                .isFlaming(true)
                .isDestroyBlock(true)
                .countSetFireEntity(5)
                .isInWater(false)
                .build();
            MCH_Explosion.newExplosion(super.worldObj, param);

        }

        if (super.worldObj.isRemote && this.getAcInfo() != null && this.getHP() <= 0 && this.getDespawnCount() <= 0) {
            this.destroyAircraft(null);
        }

        if (!super.worldObj.isRemote && this.getDespawnCount() > 0) {
            this.setDespawnCount(this.getDespawnCount() - 1);
            if (this.getDespawnCount() <= 1) {
                this.setDead(true);
            }
        }

        super.onUpdate();
        if (this.getParts() != null) {
            Entity[] var9 = this.getParts();
            for (Entity prevMotionY : var9) {
                if (prevMotionY != null) {
                    prevMotionY.onUpdate();
                }
            }
        }

        this.updateNoCollisionEntities();
        this.updateUAV();
        this.supplyFuel();
        this.supplyAmmoToOtherAircraft();
        this.updateFuel();
        this.repairOtherAircraft();
        if (this.modeSwitchCooldown > 0) {
            --this.modeSwitchCooldown;
        }

        if (this.lastRiddenByEntity == null && this.getRiddenByEntity() != null) {
            this.onRidePilotFirstUpdate();
        }

        if (this.countOnUpdate == 0) {
            this.onFirstUpdate();
        }

        ++this.countOnUpdate;
        if (this.countOnUpdate >= 1000000) {
            this.countOnUpdate = 1;
        }

        if (super.worldObj.isRemote) {
            this.commonStatus = this.getDataWatcher().getWatchableObjectInt(23);
        }

        super.fallDistance = 0.0F;
        if (super.riddenByEntity != null) {
            super.riddenByEntity.fallDistance = 0.0F;
        }

        if (this.missileDetector != null) {
            this.missileDetector.update();
        }

        if (this.soundUpdater != null) {
            this.soundUpdater.update();
        }

        if (this.getTowChainEntity() != null && this.getTowChainEntity().isDead) {
            this.setTowChainEntity((MCH_EntityChain) null);
        }

        this.updateSupplyAmmo();
        this.autoRepair();
        int ft = this.getFlareTick();
        this.flareDv.update();
        if (this.getAcInfo() != null && this.chaff != null) {
            this.chaff.chaffUseTime = getAcInfo().chaffUseTime;
            this.chaff.chaffWaitTime = getAcInfo().chaffWaitTime;
            this.chaff.onUpdate();
        }
        if (this.getAcInfo() != null && this.maintenance != null) {
            this.maintenance.useTime = getAcInfo().maintenanceUseTime;
            this.maintenance.waitTime = getAcInfo().maintenanceWaitTime;
            this.maintenance.onUpdate();
        }
        if (this.getAcInfo() != null && this.aps != null) {
            this.aps.useTime = getAcInfo().apsUseTime;
            this.aps.waitTime = getAcInfo().apsWaitTime;
            this.aps.range = getAcInfo().apsRange;
            this.aps.onUpdate();
        }
        if (this.getAcInfo() != null && this.ecmJammer != null) {
            this.ecmJammer.useTime = getAcInfo().ecmJammerUseTime;
            this.ecmJammer.waitTime = getAcInfo().ecmJammerWaitTime;
            this.ecmJammer.onUpdate();
        }
        if (!super.worldObj.isRemote && this.getFlareTick() == 0 && ft != 0) {
            this.setCommonStatus(0, false);
        }

        Entity e = this.getRiddenByEntity();
        if (e != null && !e.isDead && !this.isDestroyed()) {
            this.lastRiderYaw = e.rotationYaw;
            this.prevLastRiderYaw = e.prevRotationYaw;
            this.lastRiderPitch = e.rotationPitch;
            this.prevLastRiderPitch = e.prevRotationPitch;
        } else if (this.getTowedChainEntity() != null || super.ridingEntity != null) {
            this.lastRiderYaw = super.rotationYaw;
            this.prevLastRiderYaw = super.prevRotationYaw;
            this.lastRiderPitch = super.rotationPitch;
            this.prevLastRiderPitch = super.prevRotationPitch;
        }

        this.updatePartCameraRotate();
        this.updatePartWheel();
        this.updatePartCrawlerTrack();
        this.updatePartLightHatch();
        this.regenerationMob();
        if (this.getRiddenByEntity() == null && this.lastRiddenByEntity != null) {
            this.unmountEntity();
        }

        this.updateExtraBoundingBox();
        boolean var11 = super.onGround;
        double var12 = super.motionY;
        this.onUpdateAircraft();
        if (this.getAcInfo() != null) {
            this.updateParts(this.getPartStatus());
        }

        if (this.recoilCount > 0) {
            --this.recoilCount;
        }

        if (!W_Entity.isEqual(MCH_MOD.proxy.getClientPlayer(), this.getRiddenByEntity())) {
            this.updateRecoil(1.0F);
        }

        if (!super.worldObj.isRemote && this.isDestroyed() && !this.isExploded() && !var11 && super.onGround && var12 < -0.2D) {
            this.explosionByCrash(var12);
            this.damageSinceDestroyed = this.getMaxHP();
        }

        this.onUpdate_PartRotation();
        this.onUpdate_ParticleSmoke();
        this.updateSeatsPosition(super.posX, super.posY, super.posZ, false);
        this.updateHitBoxPosition();
        this.onUpdate_CollisionGroundDamage();
        this.onUpdate_UnmountCrew();
        this.onUpdate_Repelling();
        this.onUpdate_GForce();
        this.checkRideRack();
        if (this.lastRidingEntity == null && this.getRidingEntity() != null) {
            this.onRideEntity(this.getRidingEntity());
        }

        this.lastRiddenByEntity = this.getRiddenByEntity();
        this.lastRidingEntity = this.getRidingEntity();
        this.prevPosition.put(Vec3.createVectorHelper(super.posX, super.posY, super.posZ));
    }

    private void updateNoCollisionEntities() {
        if (!super.worldObj.isRemote) {
            if (this.getCountOnUpdate() % 10 == 0) {
                Entity key1;
                for (int key = 0; key < 1 + this.getSeatNum(); ++key) {
                    key1 = this.getEntityBySeatId(key);
                    if (key1 != null) {
                        this.noCollisionEntities.put(key1, Integer.valueOf(8));
                    }
                }

                if (this.getTowChainEntity() != null && this.getTowChainEntity().towedEntity != null) {
                    this.noCollisionEntities.put(this.getTowChainEntity().towedEntity, 60);
                }

                if (this.getTowedChainEntity() != null && this.getTowedChainEntity().towEntity != null) {
                    this.noCollisionEntities.put(this.getTowedChainEntity().towEntity, 60);
                }

                if (super.ridingEntity instanceof MCH_EntitySeat) {
                    MCH_EntityAircraft var3 = ((MCH_EntitySeat) super.ridingEntity).getParent();
                    if (var3 != null) {
                        this.noCollisionEntities.put(var3, 60);
                    }
                } else if (super.ridingEntity != null) {
                    this.noCollisionEntities.put(super.ridingEntity, 60);
                }

                Iterator var4 = this.noCollisionEntities.keySet().iterator();

                while (var4.hasNext()) {
                    key1 = (Entity) var4.next();
                    this.noCollisionEntities.put(key1, (Integer) this.noCollisionEntities.get(key1) - 1);
                }

                var4 = this.noCollisionEntities.values().iterator();

                while (var4.hasNext()) {
                    if (((Integer) var4.next()).intValue() <= 0) {
                        var4.remove();
                    }
                }

            }
        }
    }

    public void updateControl() {
        if (!super.worldObj.isRemote) {
            this.setCommonStatus(7, this.moveLeft);
            this.setCommonStatus(8, this.moveRight);
            this.setCommonStatus(9, this.throttleUp);
            this.setCommonStatus(10, this.throttleDown);
        } else if (MCH_MOD.proxy.getClientPlayer() != this.getRiddenByEntity()) {
            this.moveLeft = this.getCommonStatus(7);
            this.moveRight = this.getCommonStatus(8);
            this.throttleUp = this.getCommonStatus(9);
            this.throttleDown = this.getCommonStatus(10);
        }

    }

    public void updateRecoil(float partialTicks) {
        if (this.recoilCount > 0 && this.recoilCount >= 12) {
            float pitch = MathHelper.cos((float) ((double) (this.recoilYaw - this.getRotRoll()) * 3.141592653589793D / 180.0D));
            float roll = MathHelper.sin((float) ((double) (this.recoilYaw - this.getRotRoll()) * 3.141592653589793D / 180.0D));
            float recoil = MathHelper.cos((float) ((double) (this.recoilCount * 6) * 3.141592653589793D / 180.0D)) * this.recoilValue;
            this.setRotPitch(this.getRotPitch() + recoil * pitch * partialTicks);
            this.setRotRoll(this.getRotRoll() + recoil * roll * partialTicks);
        }

    }

    private void updatePartLightHatch() {
        this.prevRotLightHatch = this.rotLightHatch;
        if (this.isSearchLightON()) {
            this.rotLightHatch = (float) ((double) this.rotLightHatch + 0.5D);
        } else {
            this.rotLightHatch = (float) ((double) this.rotLightHatch - 0.5D);
        }

        if (this.rotLightHatch > 1.0F) {
            this.rotLightHatch = 1.0F;
        }

        if (this.rotLightHatch < 0.0F) {
            this.rotLightHatch = 0.0F;
        }

    }

    public void updateExtraBoundingBox() {
        MCH_BoundingBox[] arr$ = this.extraBoundingBox;
        int len$ = arr$.length;

        for (int i$ = 0; i$ < len$; ++i$) {
            MCH_BoundingBox bb = arr$[i$];
            bb.updatePosition(super.posX, super.posY, super.posZ, this.getRotYaw(), this.getRotPitch(), this.getRotRoll());
        }

    }

    public void updatePartWheel() {
        if (super.worldObj.isRemote) {
            if (this.getAcInfo() != null) {
                this.prevRotWheel = this.rotWheel;
                this.prevRotYawWheel = this.rotYawWheel;
                float LEN = 1.0F;
                float MIN = 0.0F;
                double throttle = this.getCurrentThrottle();
                double pivotTurnThrottle = (double) this.getAcInfo().pivotTurnThrottle;
                if (pivotTurnThrottle <= 0.0D) {
                    pivotTurnThrottle = 1.0D;
                } else {
                    pivotTurnThrottle *= 0.10000000149011612D;
                }

                boolean localMoveLeft = this.moveLeft;
                boolean localMoveRight = this.moveRight;
                if (this.getAcInfo().enableBack && (double) this.throttleBack > 0.01D && throttle <= 0.0D) {
                    throttle = (double) (-this.throttleBack * 15.0F);
                }

                if (localMoveLeft && !localMoveRight) {
                    this.rotYawWheel += 0.1F;
                    if (this.rotYawWheel > 1.0F) {
                        this.rotYawWheel = 1.0F;
                    }
                } else if (!localMoveLeft && localMoveRight) {
                    this.rotYawWheel -= 0.1F;
                    if (this.rotYawWheel < -1.0F) {
                        this.rotYawWheel = -1.0F;
                    }
                } else {
                    this.rotYawWheel *= 0.9F;
                }

                this.rotWheel = (float) ((double) this.rotWheel + throttle * (double) this.getAcInfo().partWheelRot);
                if (this.rotWheel >= 360.0F) {
                    this.rotWheel -= 360.0F;
                    this.prevRotWheel -= 360.0F;
                } else if (this.rotWheel < 0.0F) {
                    this.rotWheel += 360.0F;
                    this.prevRotWheel += 360.0F;
                }

            }
        }
    }

    public void updatePartCrawlerTrack() {
        if (super.worldObj.isRemote) {
            if (this.getAcInfo() != null) {
                this.prevRotTrackRoller[0] = this.rotTrackRoller[0];
                this.prevRotTrackRoller[1] = this.rotTrackRoller[1];
                this.prevRotCrawlerTrack[0] = this.rotCrawlerTrack[0];
                this.prevRotCrawlerTrack[1] = this.rotCrawlerTrack[1];
                float LEN = 1.0F;
                float MIN = 0.0F;
                double throttle = this.getCurrentThrottle();
                double pivotTurnThrottle = (double) this.getAcInfo().pivotTurnThrottle;
                if (pivotTurnThrottle <= 0.0D) {
                    pivotTurnThrottle = 1.0D;
                } else {
                    pivotTurnThrottle *= 0.10000000149011612D;
                }

                boolean localMoveLeft = this.moveLeft;
                boolean localMoveRight = this.moveRight;
                byte dir = 1;
                if (this.getAcInfo().enableBack && this.throttleBack > 0.0F && throttle <= 0.0D) {
                    throttle = (double) (-this.throttleBack * 5.0F);
                    if (localMoveLeft != localMoveRight) {
                        boolean i = localMoveLeft;
                        localMoveLeft = localMoveRight;
                        localMoveRight = i;
                        dir = -1;
                    }
                }

                if (localMoveLeft && !localMoveRight) {
                    throttle = 0.2D * (double) dir;
                    this.throttleCrawlerTrack[0] = (float) ((double) this.throttleCrawlerTrack[0] + throttle);
                    this.throttleCrawlerTrack[1] = (float) ((double) this.throttleCrawlerTrack[1] - pivotTurnThrottle * throttle);
                } else if (!localMoveLeft && localMoveRight) {
                    throttle = 0.2D * (double) dir;
                    this.throttleCrawlerTrack[0] = (float) ((double) this.throttleCrawlerTrack[0] - pivotTurnThrottle * throttle);
                    this.throttleCrawlerTrack[1] = (float) ((double) this.throttleCrawlerTrack[1] + throttle);
                } else {
                    if (throttle > 0.2D) {
                        throttle = 0.2D;
                    }

                    if (throttle < -0.2D) {
                        throttle = -0.2D;
                    }

                    this.throttleCrawlerTrack[0] = (float) ((double) this.throttleCrawlerTrack[0] + throttle);
                    this.throttleCrawlerTrack[1] = (float) ((double) this.throttleCrawlerTrack[1] + throttle);
                }

                for (int var11 = 0; var11 < 2; ++var11) {
                    if (this.throttleCrawlerTrack[var11] < -0.72F) {
                        this.throttleCrawlerTrack[var11] = -0.72F;
                    } else if (this.throttleCrawlerTrack[var11] > 0.72F) {
                        this.throttleCrawlerTrack[var11] = 0.72F;
                    }

                    this.rotTrackRoller[var11] += this.throttleCrawlerTrack[var11] * this.getAcInfo().trackRollerRot;
                    if (this.rotTrackRoller[var11] >= 360.0F) {
                        this.rotTrackRoller[var11] -= 360.0F;
                        this.prevRotTrackRoller[var11] -= 360.0F;
                    } else if (this.rotTrackRoller[var11] < 0.0F) {
                        this.rotTrackRoller[var11] += 360.0F;
                        this.prevRotTrackRoller[var11] += 360.0F;
                    }

                    for (this.rotCrawlerTrack[var11] -= this.throttleCrawlerTrack[var11]; this.rotCrawlerTrack[var11] >= 1.0F; --this.prevRotCrawlerTrack[var11]) {
                        --this.rotCrawlerTrack[var11];
                    }

                    while (this.rotCrawlerTrack[var11] < 0.0F) {
                        ++this.rotCrawlerTrack[var11];
                    }

                    while (this.prevRotCrawlerTrack[var11] < 0.0F) {
                        ++this.prevRotCrawlerTrack[var11];
                    }

                    this.throttleCrawlerTrack[var11] = (float) ((double) this.throttleCrawlerTrack[var11] * 0.75D);
                }

            }
        }
    }

    public void checkServerNoMove() {
        if (!super.worldObj.isRemote) {
            double moti = super.motionX * super.motionX + super.motionY * super.motionY + super.motionZ * super.motionZ;
            if (moti < 1.0E-4D) {
                if (this.serverNoMoveCount < 20) {
                    ++this.serverNoMoveCount;
                    if (this.serverNoMoveCount >= 20) {
                        this.serverNoMoveCount = 0;
                        if (super.worldObj instanceof WorldServer) {
                            ((WorldServer) super.worldObj).getEntityTracker().func_151247_a(this, new S12PacketEntityVelocity(this.getEntityId(), 0.0D, 0.0D, 0.0D));
                        }
                    }
                }
            } else {
                this.serverNoMoveCount = 0;
            }
        }

    }

    public boolean haveRotPart() {
        return super.worldObj.isRemote && this.getAcInfo() != null && this.rotPartRotation.length > 0 && this.rotPartRotation.length == this.getAcInfo().partRotPart.size();
    }

    public void onUpdate_PartRotation() {
        if (this.haveRotPart()) {
            for (int i = 0; i < this.rotPartRotation.length; ++i) {
                this.prevRotPartRotation[i] = this.rotPartRotation[i];
                if (!this.isDestroyed() && ((MCH_AircraftInfo.RotPart) this.getAcInfo().partRotPart.get(i)).rotAlways || this.getRiddenByEntity() != null) {
                    this.rotPartRotation[i] += ((MCH_AircraftInfo.RotPart) this.getAcInfo().partRotPart.get(i)).rotSpeed;
                    if (this.rotPartRotation[i] < 0.0F) {
                        this.rotPartRotation[i] += 360.0F;
                    }

                    if (this.rotPartRotation[i] >= 360.0F) {
                        this.rotPartRotation[i] -= 360.0F;
                    }
                }
            }
        }

    }

    public void onRideEntity(Entity ridingEntity) {
    }

    public int getAlt(double px, double py, double pz) {
        int i;
        for (i = 0;
             i < 256 && py - (double) i > 0.0D && (py - (double) i >= 256.0D || 0 == W_WorldFunc.getBlockId(super.worldObj, (int) px, (int) py - i, (int) pz));
             ++i) {
        }
        return i;
    }

    public boolean canRepelling(Entity entity) {
        return this.isRepelling() && this.tickRepelling > 50;
    }

    private void onUpdate_Repelling() {
        if (this.getAcInfo() != null && this.getAcInfo().haveRepellingHook()) {
            if (this.isRepelling()) {
                int alt = this.getAlt(super.posX, super.posY, super.posZ);
                if (this.ropesLength > -50.0F && this.ropesLength > (float) (-alt)) {
                    this.ropesLength = (float) ((double) this.ropesLength - (super.worldObj.isRemote ? 0.30000001192092896D : 0.25D));
                }
            } else {
                this.ropesLength = 0.0F;
            }
        }

        this.onUpdate_UnmountCrewRepelling();
    }

    private void onUpdate_UnmountCrewRepelling() {
        if (this.getAcInfo() != null) {
            if (!this.isRepelling()) {
                this.tickRepelling = 0;
            } else if (this.tickRepelling < 60) {
                ++this.tickRepelling;
            } else if (!super.worldObj.isRemote) {
                for (int ropeIdx = 0; ropeIdx < this.getAcInfo().repellingHooks.size(); ++ropeIdx) {
                    MCH_AircraftInfo.RepellingHook hook = (MCH_AircraftInfo.RepellingHook) this.getAcInfo().repellingHooks.get(ropeIdx);
                    if (this.getCountOnUpdate() % hook.interval == 0) {
                        for (int i = 1; i < this.getSeatNum(); ++i) {
                            MCH_EntitySeat seat = this.getSeat(i);
                            if (seat != null && seat.riddenByEntity != null && !W_EntityPlayer.isPlayer(seat.riddenByEntity) && !(seat.riddenByEntity instanceof MCH_EntityGunner) && !(this.getSeatInfo(i + 1) instanceof MCH_SeatRackInfo)) {
                                Entity entity = seat.riddenByEntity;
                                Vec3 dropPos = this.getTransformedPosition(hook.pos, (Vec3) this.prevPosition.oldest());
                                seat.posX = dropPos.xCoord;
                                seat.posY = dropPos.yCoord - 2.0D;
                                seat.posZ = dropPos.zCoord;
                                entity.mountEntity((Entity) null);
                                this.unmountEntityRepelling(entity, dropPos, ropeIdx);
                                this.lastUsedRopeIndex = ropeIdx;
                                break;
                            }
                        }
                    }
                }

            }
        }
    }

    public void unmountEntityRepelling(Entity entity, Vec3 dropPos, int ropeIdx) {
        entity.posX = dropPos.xCoord;
        entity.posY = dropPos.yCoord - 2.0D;
        entity.posZ = dropPos.zCoord;
        MCH_EntityHide hideEntity = new MCH_EntityHide(super.worldObj, entity.posX, entity.posY, entity.posZ);
        hideEntity.setParent(this, entity, ropeIdx);
        hideEntity.motionX = entity.motionX = 0.0D;
        hideEntity.motionY = entity.motionY = 0.0D;
        hideEntity.motionZ = entity.motionZ = 0.0D;
        hideEntity.fallDistance = entity.fallDistance = 0.0F;
        super.worldObj.spawnEntityInWorld(hideEntity);
    }

    private void onUpdate_UnmountCrew() {
        if (this.getAcInfo() != null) {
            if (this.isParachuting) {
                if (MCH_Lib.getBlockIdY(this, 3, -10) != 0) {
                    this.stopUnmountCrew();
                } else if ((!this.haveHatch() || this.getHatchRotation() > 89.0F) && this.getCountOnUpdate() % this.getAcInfo().mobDropOption.interval == 0 && !this.unmountCrew(true)) {
                    this.stopUnmountCrew();
                }
            }

        }
    }

    public void unmountAircraft() {
        Vec3 v = Vec3.createVectorHelper(super.posX, super.posY, super.posZ);
        if (super.ridingEntity instanceof MCH_EntitySeat) {
            MCH_EntityAircraft ac = ((MCH_EntitySeat) super.ridingEntity).getParent();
            MCH_SeatInfo seatInfo = ac.getSeatInfo(this);
            if (seatInfo instanceof MCH_SeatRackInfo) {
                v = ((MCH_SeatRackInfo) seatInfo).getEntryPos();
                v = ac.getTransformedPosition(v);
            }
        } else if (super.ridingEntity instanceof EntityMinecartEmpty) {
            this.dismountedUserCtrl = true;
        }

        this.setLocationAndAngles(v.xCoord, v.yCoord, v.zCoord, this.getRotYaw(), this.getRotPitch());
        this.mountEntity(null);
        this.setLocationAndAngles(v.xCoord, v.yCoord, v.zCoord, this.getRotYaw(), this.getRotPitch());
    }

    public boolean canUnmount(Entity entity) {
        return this.getAcInfo() == null ? false : (!this.getAcInfo().isEnableParachuting ? false : (this.getSeatIdByEntity(entity) <= 1 ? false : !this.haveHatch() || this.getHatchRotation() >= 89.0F));
    }

    public void unmount(Entity entity) {
        if (this.getAcInfo() != null) {
            MCH_EntitySeat seat;
            Vec3 dropPos;
            if (this.canRepelling(entity) && this.getAcInfo().haveRepellingHook()) {
                seat = this.getSeatByEntity(entity);
                if (seat != null) {
                    this.lastUsedRopeIndex = (this.lastUsedRopeIndex + 1) % this.getAcInfo().repellingHooks.size();
                    dropPos = this.getTransformedPosition(((MCH_AircraftInfo.RepellingHook) this.getAcInfo().repellingHooks.get(this.lastUsedRopeIndex)).pos, (Vec3) this.prevPosition.oldest());
                    dropPos = dropPos.addVector(0.0D, -2.0D, 0.0D);
                    seat.posX = dropPos.xCoord;
                    seat.posY = dropPos.yCoord;
                    seat.posZ = dropPos.zCoord;
                    entity.mountEntity((Entity) null);
                    entity.posX = dropPos.xCoord;
                    entity.posY = dropPos.yCoord;
                    entity.posZ = dropPos.zCoord;
                    this.unmountEntityRepelling(entity, dropPos, this.lastUsedRopeIndex);
                } else {
                    MCH_Lib.Log((Entity) this, "Error:MCH_EntityAircraft.unmount seat=null : " + entity, new Object[0]);
                }
            } else if (this.canUnmount(entity)) {
                seat = this.getSeatByEntity(entity);
                if (seat != null) {
                    dropPos = this.getTransformedPosition(this.getAcInfo().mobDropOption.pos, (Vec3) this.prevPosition.oldest());
                    seat.posX = dropPos.xCoord;
                    seat.posY = dropPos.yCoord;
                    seat.posZ = dropPos.zCoord;
                    entity.mountEntity((Entity) null);
                    entity.posX = dropPos.xCoord;
                    entity.posY = dropPos.yCoord;
                    entity.posZ = dropPos.zCoord;
                    this.dropEntityParachute(entity);
                } else {
                    MCH_Lib.Log((Entity) this, "Error:MCH_EntityAircraft.unmount seat=null : " + entity, new Object[0]);
                }
            }

        }
    }

    public boolean canParachuting(Entity entity) {
        return this.getAcInfo() != null && this.getAcInfo().isEnableParachuting && this.getSeatIdByEntity(entity) > 1 && MCH_Lib.getBlockIdY(this, 3, -13) == 0 ? (this.haveHatch() && this.getHatchRotation() > 89.0F ? this.getSeatIdByEntity(entity) > 1 : this.getSeatIdByEntity(entity) > 1) : false;
    }

    public void onUpdate_RidingEntity() {
        if (!super.worldObj.isRemote && this.waitMountEntity == 0 && this.getCountOnUpdate() > 20 && this.canMountWithNearEmptyMinecart()) {
            this.mountWithNearEmptyMinecart();
        }

        if (this.waitMountEntity > 0) {
            --this.waitMountEntity;
        }

        if (!super.worldObj.isRemote && this.getRidingEntity() != null) {
            this.setRotRoll(this.getRotRoll() * 0.9F);
            this.setRotPitch(this.getRotPitch() * 0.95F);
            Entity re = this.getRidingEntity();
            float target = MathHelper.wrapAngleTo180_float(re.rotationYaw + 90.0F);
            if (target - super.rotationYaw > 180.0F) {
                target -= 360.0F;
            }

            if (target - super.rotationYaw < -180.0F) {
                target += 360.0F;
            }

            if (super.ticksExisted % 2 == 0) {
                ;
            }

            float dist = 50.0F * (float) re.getDistanceSq(re.prevPosX, re.prevPosY, re.prevPosZ);
            if ((double) dist > 0.001D) {
                dist = MathHelper.sqrt_double((double) dist);
                float bkPosX = MCH_Lib.RNG(target - super.rotationYaw, -dist, dist);
                super.rotationYaw += bkPosX;
            }

            double var10 = super.posX;
            double bkPosY = super.posY;
            double bkPosZ = super.posZ;
            if (this.getRidingEntity().isDead) {
                this.mountEntity((Entity) null);
                this.waitMountEntity = 20;
            } else if (this.getCurrentThrottle() > 0.8D) {
                super.motionX = this.getRidingEntity().motionX;
                super.motionY = this.getRidingEntity().motionY;
                super.motionZ = this.getRidingEntity().motionZ;
                this.mountEntity((Entity) null);
                this.waitMountEntity = 20;
            }

            super.posX = var10;
            super.posY = bkPosY;
            super.posZ = bkPosZ;
        }

    }

    public void explosionByCrash(double prevMotionY) {
        float exp = getAcInfo().explosionSizeByCrash;
        MCH_Lib.DbgLog(super.worldObj, "OnGroundAfterDestroyed:motionY=%.3f", new Object[]{Float.valueOf((float) prevMotionY)});
        MCH_ExplosionParam param = MCH_ExplosionParam.builder()
            .exploder(null)
            .player(null)
            .x(super.posX).y(super.posY).z(super.posZ)
            .size(exp)
            .sizeBlock(exp >= 2.0F ? exp * 0.5F : 1.0F)
            .isPlaySound(true)
            .isSmoking(true)
            .isFlaming(true)
            .isDestroyBlock(true)
            .countSetFireEntity(5)
            .isInWater(false)
            .build();
        MCH_Explosion.newExplosion(super.worldObj, param);
    }


    public void onUpdate_CollisionGroundDamage() {
        if (!this.isDestroyed()) {
            if (MCH_Lib.getBlockIdY(this, 3, -3) > 0 && !super.worldObj.isRemote) {
                float hp = MathHelper.abs(MathHelper.wrapAngleTo180_float(this.getRotRoll()));
                float pitch = MathHelper.abs(MathHelper.wrapAngleTo180_float(this.getRotPitch()));
                if (hp > this.getGiveDamageRot() || pitch > this.getGiveDamageRot()) {
                    float dmg = MathHelper.abs(hp) + MathHelper.abs(pitch);
                    if (dmg < 90.0F) {
                        dmg *= 0.4F * (float) this.getDistance(super.prevPosX, super.prevPosY, super.prevPosZ);
                    } else {
                        dmg *= 0.4F;
                    }

                    if (dmg > 1.0F && super.rand.nextInt(4) == 0) {
                        this.attackEntityFrom(DamageSource.inWall, dmg);
                    }
                }
            }

            if (this.getCountOnUpdate() % 30 == 0 && (this.getAcInfo() == null || !this.getAcInfo().isFloat) && MCH_Lib.isBlockInWater(super.worldObj, (int) (super.posX + 0.5D), (int) (super.posY + 1.5D + (double) this.getAcInfo().submergedDamageHeight), (int) (super.posZ + 0.5D))) {
                int hp1 = this.getMaxHP() / 10;
                if (hp1 <= 0) {
                    hp1 = 1;
                }

                this.attackEntityFrom(DamageSource.inWall, hp1);
            }

        }
    }

    public float getGiveDamageRot() {
        return 40.0F;
    }

    public void applyServerPositionAndRotation() {
        double rpinc = this.aircraftPosRotInc;
        double yaw = MathHelper.wrapAngleTo180_double(this.aircraftYaw - (double) this.getRotYaw());
        double roll = MathHelper.wrapAngleTo180_double((double) this.getServerRoll() - (double) this.getRotRoll());
        if (!this.isDestroyed() && (!W_Lib.isClientPlayer(this.getRiddenByEntity()) || this.getRidingEntity() != null)) {
            this.setRotYaw((float) ((double) this.getRotYaw() + yaw / rpinc));
            this.setRotPitch((float) ((double) this.getRotPitch() + (this.aircraftPitch - (double) this.getRotPitch()) / rpinc));
            this.setRotRoll((float) ((double) this.getRotRoll() + roll / rpinc));
        }
        this.setPosition(this.posX + (this.aircraftX - this.posX) / rpinc, this.posY + (this.aircraftY - this.posY) / rpinc, this.posZ + (this.aircraftZ - this.posZ) / rpinc);
        this.setRotation(this.getRotYaw(), this.getRotPitch());
        --this.aircraftPosRotInc;
    }

    protected void autoRepair() {
        if (this.timeSinceHit > 0) {
            --this.timeSinceHit;
        }

        if (this.getMaxHP() > 0) {
            if (!this.isDestroyed()) {
                if (this.getDamageTaken() > this.beforeDamageTaken) {
                    this.repairCount = 600;
                } else if (this.repairCount > 0) {
                    --this.repairCount;
                } else {
                    this.repairCount = 40;
                    double hpp = (double) this.getHP() / (double) this.getMaxHP();
                    if (hpp >= MCH_Config.AutoRepairHP.prmDouble) {
                        this.repair(this.getMaxHP() / 100);
                    }
                }
            }

            this.beforeDamageTaken = this.getDamageTaken();
        }
    }

    public boolean repair(int tpd) {
        if (tpd < 1) {
            tpd = 1;
        }

        int damage = this.getDamageTaken();
        if (damage > 0) {
            if (!super.worldObj.isRemote) {
                this.setDamageTaken(damage - tpd);
            }

            return true;
        } else {
            return false;
        }
    }

    public void repairOtherAircraft() {
        float range = this.getAcInfo() != null ? this.getAcInfo().repairOtherVehiclesRange : 0.0F;
        if (range > 0.0F) {
            if (!super.worldObj.isRemote && this.getCountOnUpdate() % 20 == 0) {
                List list = super.worldObj.getEntitiesWithinAABB(MCH_EntityAircraft.class, this.getBoundingBox().expand((double) range, (double) range, (double) range));

                for (int i = 0; i < list.size(); ++i) {
                    MCH_EntityAircraft ac = (MCH_EntityAircraft) list.get(i);
                    if (!W_Entity.isEqual(this, ac) && ac.getHP() < ac.getMaxHP()) {
                        ac.setDamageTaken(ac.getDamageTaken() - this.getAcInfo().repairOtherVehiclesValue);
                    }
                }
            }

        }
    }

    protected void regenerationMob() {
        if (!this.isDestroyed()) {
            if (!super.worldObj.isRemote) {
                if (this.getAcInfo() != null && this.getAcInfo().regeneration && this.getRiddenByEntity() != null) {
                    MCH_EntitySeat[] st = this.getSeats();
                    MCH_EntitySeat[] arr$ = st;
                    int len$ = st.length;

                    for (int i$ = 0; i$ < len$; ++i$) {
                        MCH_EntitySeat s = arr$[i$];
                        if (s != null && !s.isDead) {
                            Entity e = s.riddenByEntity;
                            if (W_Lib.isEntityLivingBase(e) && !e.isDead) {
                                PotionEffect pe = W_Entity.getActivePotionEffect(e, Potion.regeneration);
                                if (pe == null || pe != null && pe.getDuration() < 500) {
                                    W_Entity.addPotionEffect(e, new PotionEffect(Potion.regeneration.id, 250, 0, true));
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    public double getWaterDepth() {
        byte b0 = 5;
        double d0 = 0.0D;

        for (int i = 0; i < b0; ++i) {
            double d1 = super.boundingBox.minY + (super.boundingBox.maxY - super.boundingBox.minY) * (double) (i + 0) / (double) b0 - 0.125D;
            double d2 = super.boundingBox.minY + (super.boundingBox.maxY - super.boundingBox.minY) * (double) (i + 1) / (double) b0 - 0.125D;
            d1 += (double) this.getAcInfo().floatOffset;
            d2 += (double) this.getAcInfo().floatOffset;
            AxisAlignedBB axisalignedbb = W_AxisAlignedBB.getAABB(super.boundingBox.minX, d1, super.boundingBox.minZ, super.boundingBox.maxX, d2, super.boundingBox.maxZ);
            if (super.worldObj.isAABBInMaterial(axisalignedbb, Material.water)) {
                d0 += 1.0D / (double) b0;
            }
        }

        return d0;
    }

    public int getCountOnUpdate() {
        return this.countOnUpdate;
    }

    public boolean canSupply() {
        return this.canFloatWater() ? MCH_Lib.getBlockIdY(this, 1, -3) != 0 : MCH_Lib.getBlockIdY(this, 1, -3) != 0 && !this.isInWater();
    }

    public int getFuel() {
        return this.getDataWatcher().getWatchableObjectInt(25);
    }

    public void setFuel(int fuel) {
        if (!super.worldObj.isRemote) {
            if (fuel < 0) {
                fuel = 0;
            }

            if (fuel > this.getMaxFuel()) {
                fuel = this.getMaxFuel();
            }

            if (fuel != this.getFuel()) {
                this.getDataWatcher().updateObject(25, fuel);
            }
        }

    }

    public float getFuelP() {
        int m = this.getMaxFuel();
        return m == 0 ? 0.0F : (float) this.getFuel() / (float) m;
    }

    public boolean canUseFuel(boolean checkOtherSeet) {
        return this.getMaxFuel() <= 0 || this.getFuel() > 1 || this.isInfinityFuel(this.getRiddenByEntity(), checkOtherSeet);
    }

    public boolean canUseFuel() {
        return this.canUseFuel(false);
    }

    public int getMaxFuel() {
        return this.getAcInfo() != null ? this.getAcInfo().maxFuel : 0;
    }

    public void supplyFuel() {
        float range = this.getAcInfo() != null ? this.getAcInfo().fuelSupplyRange : 0.0F;
        if (range > 0.0F) {
            if (!super.worldObj.isRemote && this.getCountOnUpdate() % 10 == 0) {
                List list = super.worldObj.getEntitiesWithinAABB(MCH_EntityAircraft.class, this.getBoundingBox().expand((double) range, (double) range, (double) range));

                for (Object o : list) {
                    MCH_EntityAircraft ac = (MCH_EntityAircraft) o;
                    if (!W_Entity.isEqual(this, ac)) {
                        if ((!super.onGround || ac.canSupply()) && ac.getFuel() < ac.getMaxFuel()) {
                            int fc = ac.getMaxFuel() - ac.getFuel();
                            if (fc > 30) {
                                fc = 30;
                            }

                            ac.setFuel(ac.getFuel() + fc);
                        }

                        ac.fuelSuppliedCount = 40;
                    }
                }
            }

        }
    }

    public void updateFuel() {
        if (this.getMaxFuel() != 0) {
            if (this.fuelSuppliedCount > 0) {
                --this.fuelSuppliedCount;
            }

            if (!this.isDestroyed() && !super.worldObj.isRemote) {
                if (this.getCountOnUpdate() % 20 == 0 && this.getFuel() > 1 && this.getThrottle() > 0.0D && this.fuelSuppliedCount <= 0) {
                    double curFuel = this.getThrottle() * 1.4D;
                    if (curFuel > 1.0D) {
                        curFuel = 1.0D;
                    }

                    this.fuelConsumption += curFuel * (double) this.getAcInfo().fuelConsumption * (double) this.getFuelConsumptionFactor();
                    if (this.fuelConsumption > 1.0D) {
                        int fuel = (int) this.fuelConsumption;
                        this.fuelConsumption -= (double) fuel;
                        this.setFuel(this.getFuel() - fuel);
                    }
                }

                int var5 = this.getFuel();
                if (this.canSupply() && this.getCountOnUpdate() % 10 == 0 && var5 < this.getMaxFuel()) {
                    for (int i = 0; i < 3; ++i) {
                        if (var5 < this.getMaxFuel()) {
                            ItemStack var6 = this.getGuiInventory().getFuelSlotItemStack(i);
                            if (var6 != null && var6.getItem() instanceof MCH_ItemFuel && var6.getItemDamage() < var6.getMaxDamage()) {
                                int fc = this.getMaxFuel() - var5;
                                if (fc > 100) {
                                    fc = 100;
                                }

                                if (var6.getItemDamage() > var6.getMaxDamage() - fc) {
                                    fc = var6.getMaxDamage() - var6.getItemDamage();
                                }

                                var6.setItemDamage(var6.getItemDamage() + fc);
                                var5 += fc;
                            }
                        }
                    }

                    if (this.getFuel() != var5) {
                        MCH_Achievement.addStat(super.riddenByEntity, MCH_Achievement.supplyFuel, 1);
                    }

                    this.setFuel(var5);
                }
            }

        }
    }

    public float getFuelConsumptionFactor() {
        return 1.0F;
    }

    public void updateSupplyAmmo() {
        if (!super.worldObj.isRemote) {
            boolean isReloading = false;
            if (this.getRiddenByEntity() instanceof EntityPlayer && !this.getRiddenByEntity().isDead && ((EntityPlayer) this.getRiddenByEntity()).openContainer instanceof MCH_AircraftGuiContainer) {
                isReloading = true;
            }

            this.setCommonStatus(2, isReloading);
            if (!this.isDestroyed() && this.beforeSupplyAmmo && !isReloading) {
                this.reloadAllWeapon();
                MCH_PacketNotifyAmmoNum.sendAllAmmoNum(this, (EntityPlayer) null);
            }

            this.beforeSupplyAmmo = isReloading;
        }

        if (this.getCommonStatus(2)) {
            this.supplyAmmoWait = 20;
        }

        if (this.supplyAmmoWait > 0) {
            --this.supplyAmmoWait;
        }

    }

    public void supplyAmmo(int weaponID) {
        if (super.worldObj.isRemote) {
            MCH_WeaponSet player = this.getWeapon(weaponID);
            player.supplyRestAllAmmo();
        } else {
            MCH_Achievement.addStat(super.riddenByEntity, MCH_Achievement.supplyAmmo, 1);
            if (this.getRiddenByEntity() instanceof EntityPlayer) {
                EntityPlayer var9 = (EntityPlayer) this.getRiddenByEntity();
                if (this.canPlayerSupplyAmmo(var9, weaponID)) {
                    MCH_WeaponSet ws = this.getWeapon(weaponID);
                    Iterator i$ = ws.getInfo().roundItems.iterator();

                    while (i$.hasNext()) {
                        MCH_WeaponInfo.RoundItem ri = (MCH_WeaponInfo.RoundItem) i$.next();
                        int num = ri.num;

                        for (int i = 0; i < var9.inventory.mainInventory.length; ++i) {
                            ItemStack itemStack = var9.inventory.mainInventory[i];
                            if (itemStack != null && itemStack.isItemEqual(ri.itemStack)) {
                                if (itemStack.getItem() != W_Item.getItemByName("water_bucket") && itemStack.getItem() != W_Item.getItemByName("lava_bucket")) {
                                    if (itemStack.stackSize > num) {
                                        itemStack.stackSize -= num;
                                        num = 0;
                                    } else {
                                        num -= itemStack.stackSize;
                                        itemStack.stackSize = 0;
                                        var9.inventory.mainInventory[i] = null;
                                    }
                                } else if (itemStack.stackSize == 1) {
                                    var9.inventory.setInventorySlotContents(i, new ItemStack(W_Item.getItemByName("bucket"), 1));
                                    --num;
                                }
                            }

                            if (num <= 0) {
                                break;
                            }
                        }
                    }

                    ws.supplyRestAllAmmo();
                }
            }
        }

    }

    public void supplyAmmoToOtherAircraft() {
        float range = this.getAcInfo() != null ? this.getAcInfo().ammoSupplyRange : 0.0F;
        if (range > 0.0F) {
            if (!super.worldObj.isRemote && this.getCountOnUpdate() % 40 == 0) {
                List list = super.worldObj.getEntitiesWithinAABB(MCH_EntityAircraft.class, this.getBoundingBox().expand((double) range, (double) range, (double) range));

                for (int i = 0; i < list.size(); ++i) {
                    MCH_EntityAircraft ac = (MCH_EntityAircraft) list.get(i);
                    if (!W_Entity.isEqual(this, ac) && ac.canSupply()) {
                        for (int wid = 0; wid < ac.getWeaponNum(); ++wid) {
                            MCH_WeaponSet ws = ac.getWeapon(wid);
                            int num = ws.getRestAllAmmoNum() + ws.getAmmoNum();
                            if (num < ws.getAllAmmoNum()) {
                                int ammo = ws.getAllAmmoNum() / 10;
                                if (ammo < 1) {
                                    ammo = 1;
                                }

                                ws.setRestAllAmmoNum(num + ammo);
                                EntityPlayer player = ac.getEntityByWeaponId(wid);
                                if (num != ws.getRestAllAmmoNum() + ws.getAmmoNum()) {
                                    if (ws.getAmmoNum() <= 0) {
                                        ws.reloadMag();
                                    }

                                    MCH_PacketNotifyAmmoNum.sendAmmoNum(ac, player, wid);
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    public boolean canPlayerSupplyAmmo(EntityPlayer player, int weaponId) {
        if (MCH_Lib.getBlockIdY(this, 1, -3) == 0) {
            return false;
        } else if (!this.canSupply()) {
            return false;
        } else {
            MCH_WeaponSet ws = this.getWeapon(weaponId);
            if (ws.getRestAllAmmoNum() + ws.getAmmoNum() >= ws.getAllAmmoNum()) {
                return false;
            } else {
                Iterator i$ = ws.getInfo().roundItems.iterator();

                while (i$.hasNext()) {
                    MCH_WeaponInfo.RoundItem ri = (MCH_WeaponInfo.RoundItem) i$.next();
                    int num = ri.num;
                    ItemStack[] arr$ = player.inventory.mainInventory;
                    int len$ = arr$.length;
                    int i$1 = 0;

                    while (true) {
                        if (i$1 < len$) {
                            ItemStack itemStack = arr$[i$1];
                            if (itemStack != null && itemStack.isItemEqual(ri.itemStack)) {
                                num -= itemStack.stackSize;
                            }

                            if (num > 0) {
                                ++i$1;
                                continue;
                            }
                        }

                        if (num > 0) {
                            return false;
                        }
                        break;
                    }
                }

                return true;
            }
        }
    }

    public String getTextureName() {
        return this.getDataWatcher().getWatchableObjectString(21);
    }

    public MCH_EntityAircraft setTextureName(String name) {
        if (name != null && !name.isEmpty()) {
            this.getDataWatcher().updateObject(21, name);
        }

        return this;
    }

    public void switchNextTextureName() {
        if (this.getAcInfo() != null) {
            this.setTextureName(this.getAcInfo().getNextTextureName(this.getTextureName()));
        }

    }

    public void zoomCamera() {
        if (!this.canZoom()) return;
        final float eps = 0.01f;
        final float max = (float) this.getZoomMax();
        float z = this.camera.getCameraZoom();
        ArrayList<Float> list = new ArrayList<>();
        if (max <= 4.0f + eps) {
            float hi = Math.min(4.0f, max);
            if (1.0f <= max + eps) list.add(1.0f);
            if (hi > 1.0f + eps) list.add(hi);
        } else {
            float mid = max * 0.5f;
            if (1.0f <= max + eps) list.add(1.0f);
            if (mid > 1.0f + eps && mid < max - eps) list.add(mid);
            list.add(max);
        }
        if (list.isEmpty()) {
            this.camera.setCameraZoom(1.0f);
            return;
        }
        float next = list.get(0);
        boolean chosen = false;
        for (float lv : list) {
            if (z < lv - eps) {
                next = lv;
                chosen = true;
                break;
            }
        }
        if (!chosen) {
            next = list.get(0);
        }
        if (next < 1.0f) next = 1.0f;
        if (next > max) next = max;
        this.camera.setCameraZoom(next);
    }


    public int getZoomMax() {
        return this.getAcInfo() != null ? this.getAcInfo().cameraZoom : 1;
    }

    public boolean canZoom() {
        return this.getZoomMax() > 1;
    }

    public boolean canSwitchCameraMode() {
        return this.isDestroyed() ? false : this.getAcInfo() != null && this.getAcInfo().isEnableNightVision;
    }

    public boolean canSwitchCameraMode(int seatID) {
        return this.isDestroyed() ? false : this.canSwitchCameraMode() && this.camera.isValidUid(seatID);
    }

    public int getCameraMode(EntityPlayer player) {
        return this.camera.getMode(this.getSeatIdByEntity(player));
    }

    public String getCameraModeName(EntityPlayer player) {
        return this.camera.getModeName(this.getSeatIdByEntity(player));
    }

    public void switchCameraMode(EntityPlayer player) {
        this.switchCameraMode(player, this.camera.getMode(this.getSeatIdByEntity(player)) + 1);
    }

    public void switchCameraMode(EntityPlayer player, int mode) {
        this.camera.setMode(this.getSeatIdByEntity(player), mode);
    }

    public void updateCameraViewers() {
        for (int i = 0; i < this.getSeatNum() + 1; ++i) {
            this.camera.updateViewer(i, this.getEntityBySeatId(i));
        }

    }

    public void updateRadar(int radarSpeed) {
        if (this.isEntityRadarMounted()) {
            this.radarRotate += radarSpeed;
            if (this.radarRotate >= 360) {
                this.radarRotate = 0;
            }

            if (this.radarRotate == 0) {
                this.entityRadar.updateXZ(this, 64);
            }
        }

    }

    public int getRadarRotate() {
        return this.radarRotate;
    }

    public void initRadar() {
        this.entityRadar.clear();
        this.radarRotate = 0;
    }

    public ArrayList getRadarEntityList() {
        return this.entityRadar.getEntityList();
    }

    public ArrayList getRadarEnemyList() {
        return this.entityRadar.getEnemyList();
    }

    public void moveEntity(double par1, double par3, double par5) {
        // Ensure the entity's AcInfo is not null
        if (this.getAcInfo() != null) {
            // Start profiling section for movement
            super.worldObj.theProfiler.startSection("move");
            super.ySize *= 0.4F;

            // Store initial position
            double initialPosX = super.posX;
            double initialPosY = super.posY;
            double initialPosZ = super.posZ;

            // Attempted movement deltas
            double deltaX = par1;
            double deltaY = par3;
            double deltaZ = par5;

            // Create a copy of the bounding box
            AxisAlignedBB initialBoundingBox = super.boundingBox.copy();

            // Get colliding bounding boxes
            List<AxisAlignedBB> collisionBoxes = getCollidingBoundingBoxes(this, super.boundingBox.addCoord(par1, par3, par5));

            // Calculate Y offset based on collisions
            for (AxisAlignedBB box : collisionBoxes) {
                par3 = box.calculateYOffset(super.boundingBox, par3);
            }

            // Offset bounding box by calculated Y offset
            super.boundingBox.offset(0.0D, par3, 0.0D);

            // Check if movement is obstructed
            if (!super.field_70135_K && deltaY != par3) {
                deltaX = deltaY = deltaZ = 0.0D;
            }

            // Check if the entity is on the ground
            boolean onGround = super.onGround || deltaY != par3 && deltaY < 0.0D;

            // Calculate X offset based on collisions
            for (AxisAlignedBB box : collisionBoxes) {
                par1 = box.calculateXOffset(super.boundingBox, par1);
            }

            // Offset bounding box by calculated X offset
            super.boundingBox.offset(par1, 0.0D, 0.0D);

            // Check if movement is obstructed
            if (!super.field_70135_K && deltaX != par1) {
                deltaX = deltaY = deltaZ = 0.0D;
            }

            // Calculate Z offset based on collisions
            for (AxisAlignedBB box : collisionBoxes) {
                par5 = box.calculateZOffset(super.boundingBox, par5);
            }

            // Offset bounding box by calculated Z offset
            super.boundingBox.offset(0.0D, 0.0D, par5);

            // Check if movement is obstructed
            if (!super.field_70135_K && deltaZ != par5) {
                deltaX = deltaY = deltaZ = 0.0D;
            }

            // Handle step height logic
            if (super.stepHeight > 0.0F && onGround && super.ySize < 0.05F && (deltaX != par1 || deltaZ != par5)) {
                handleStepHeightMovement(par1, par3, par5, deltaX, deltaY, deltaZ, initialBoundingBox, collisionBoxes);
            }

            // Update entity position based on bounding box
            updateEntityPosition();

            // Update collision state
            super.isCollidedHorizontally = deltaX != par1 || deltaZ != par5;
            super.isCollidedVertically = deltaY != par3;
            super.onGround = deltaY != par3 && deltaY < 0.0D;
            super.isCollided = super.isCollidedHorizontally || super.isCollidedVertically;

            // Update fall state
            this.updateFallState(par3, super.onGround);

            // Reset motion if obstructed
            if (deltaX != par1) super.motionX = 0.0D;
            if (deltaY != par3) super.motionY = 0.0D;
            if (deltaZ != par5) super.motionZ = 0.0D;

            // Handle block collisions
            handleBlockCollisions();

            // End profiling section
            super.worldObj.theProfiler.endSection();
        }
    }

    private void handleStepHeightMovement(double par1, double par3, double par5, double deltaX, double deltaY, double deltaZ, AxisAlignedBB initialBoundingBox, List<AxisAlignedBB> collisionBoxes) {
        // Store initial deltas
        double initialDeltaX = par1;
        double initialDeltaY = par3;
        double initialDeltaZ = par5;

        // Attempt step height movement
        par1 = deltaX;
        par3 = (double) super.stepHeight;
        par5 = deltaZ;

        // Create a copy of the bounding box
        AxisAlignedBB stepBoundingBox = super.boundingBox.copy();
        super.boundingBox.setBB(initialBoundingBox);

        // Get colliding bounding boxes
        List<AxisAlignedBB> stepCollisionBoxes = getCollidingBoundingBoxes(this, super.boundingBox.addCoord(deltaX, par3, deltaZ));

        // Calculate Y offset based on collisions
        for (AxisAlignedBB box : stepCollisionBoxes) {
            par3 = box.calculateYOffset(super.boundingBox, par3);
        }

        // Offset bounding box by calculated Y offset
        super.boundingBox.offset(0.0D, par3, 0.0D);

        // Check if movement is obstructed
        if (!super.field_70135_K && deltaY != par3) {
            deltaX = deltaY = deltaZ = 0.0D;
        }

        // Calculate X offset based on collisions
        for (AxisAlignedBB box : stepCollisionBoxes) {
            par1 = box.calculateXOffset(super.boundingBox, par1);
        }

        // Offset bounding box by calculated X offset
        super.boundingBox.offset(par1, 0.0D, 0.0D);

        // Check if movement is obstructed
        if (!super.field_70135_K && deltaX != par1) {
            deltaX = deltaY = deltaZ = 0.0D;
        }

        // Calculate Z offset based on collisions
        for (AxisAlignedBB box : stepCollisionBoxes) {
            par5 = box.calculateZOffset(super.boundingBox, par5);
        }

        // Offset bounding box by calculated Z offset
        super.boundingBox.offset(0.0D, 0.0D, par5);

        // Check if movement is obstructed
        if (!super.field_70135_K && deltaZ != par5) {
            deltaX = deltaY = deltaZ = 0.0D;
        }

        // Revert to initial deltas if step movement was less efficient
        if (initialDeltaX * initialDeltaX + initialDeltaZ * initialDeltaZ >= par1 * par1 + par5 * par5) {
            par1 = initialDeltaX;
            par3 = initialDeltaY;
            par5 = initialDeltaZ;
            super.boundingBox.setBB(stepBoundingBox);
        }
    }

    private void updateEntityPosition() {
        super.posX = (super.boundingBox.minX + super.boundingBox.maxX) / 2.0D;
        super.posY = super.boundingBox.minY + (double) super.yOffset - (double) super.ySize;
        super.posZ = (super.boundingBox.minZ + super.boundingBox.maxZ) / 2.0D;
    }

    private void handleBlockCollisions() {
        try {
            this.doBlockCollisions();
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.makeCrashReport(throwable, "Checking entity tile collision");
            CrashReportCategory crashReportCategory = crashReport.makeCategory("Entity being checked for collision");
            this.addEntityCrashInfo(crashReportCategory);
            throw new ReportedException(crashReport);
        }
    }

    protected void onUpdate_updateBlock() {
        if (MCH_Config.Collision_DestroyBlock.prmBool) {
            for (int l = 0; l < 4; ++l) {
                int i1 = MathHelper.floor_double(super.posX + ((double) (l % 2) - 0.5D) * 0.8D);
                int j1 = MathHelper.floor_double(super.posZ + ((double) (l / 2) - 0.5D) * 0.8D);

                for (int k1 = 0; k1 < 2; ++k1) {
                    int l1 = MathHelper.floor_double(super.posY) + k1;
                    Block block = W_WorldFunc.getBlock(super.worldObj, i1, l1, j1);
                    if (!W_Block.isNull(block)) {
                        if (block == W_Block.getSnowLayer()) {
                            super.worldObj.setBlockToAir(i1, l1, j1);
                        }

                        if (block == Blocks.waterlily || block == Blocks.cake) {
                            W_WorldFunc.destroyBlock(super.worldObj, i1, l1, j1, false);
                        }
                    }
                }
            }

        }
    }

    public void onUpdate_ParticleSmoke() {
        if (super.worldObj.isRemote) {
            if (this.getCurrentThrottle() > 0.10000000149011612D) {
                float yaw = this.getRotYaw();
                float pitch = this.getRotPitch();
                float roll = this.getRotRoll();
                MCH_WeaponSet ws = this.getCurrentWeapon(this.getRiddenByEntity());
                if (ws.getFirstWeapon() instanceof MCH_WeaponSmoke) {
                    for (int i = 0; i < ws.getWeaponNum(); ++i) {
                        MCH_WeaponBase wb = ws.getWeapon(i);
                        if (wb != null) {
                            MCH_WeaponInfo wi = wb.getInfo();
                            if (wi != null) {
                                Vec3 rot = MCH_Lib.RotVec3(0.0D, 0.0D, 1.0D, -yaw - 180.0F + wb.fixRotationYaw, pitch - wb.fixRotationPitch, roll);
                                if ((double) super.rand.nextFloat() <= this.getCurrentThrottle() * 1.5D) {
                                    Vec3 pos = MCH_Lib.RotVec3(wb.position, -yaw, -pitch, -roll);
                                    double x = super.posX + pos.xCoord + rot.xCoord;
                                    double y = super.posY + pos.yCoord + rot.yCoord;
                                    double z = super.posZ + pos.zCoord + rot.zCoord;

                                    for (int smk = 0; smk < wi.smokeNum; ++smk) {
                                        float c = super.rand.nextFloat() * 0.05F;
                                        int maxAge = (int) (super.rand.nextDouble() * (double) wi.smokeMaxAge);
                                        MCH_ParticleParam prm = new MCH_ParticleParam(super.worldObj, "smoke", x, y, z);
                                        prm.setMotion(rot.xCoord * (double) wi.acceleration + (super.rand.nextDouble() - 0.5D) * 0.2D, rot.yCoord * (double) wi.acceleration + (super.rand.nextDouble() - 0.5D) * 0.2D, rot.zCoord * (double) wi.acceleration + (super.rand.nextDouble() - 0.5D) * 0.2D);
                                        prm.size = ((float) super.rand.nextInt(5) + 5.0F) * wi.smokeSize;
                                        prm.setColor(wi.color.a + super.rand.nextFloat() * 0.05F, wi.color.r + c, wi.color.g + c, wi.color.b + c);
                                        prm.age = maxAge;
                                        prm.toWhite = true;
                                        prm.diffusible = true;
                                        MCH_ParticlesUtil.spawnParticle(prm);
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
    }

    protected void onUpdate_ParticleSandCloud(boolean seaOnly) {
        if (!seaOnly || this.getAcInfo().enableSeaSurfaceParticle) {
            double particlePosY = (double) ((int) super.posY);
            boolean b = false;
            float scale = this.getAcInfo().particlesScale * 3.0F;
            if (seaOnly) {
                scale *= 2.0F;
            }

            double throttle = this.getCurrentThrottle();
            throttle *= 2.0D;
            if (throttle > 1.0D) {
                throttle = 1.0D;
            }

            int count = seaOnly ? (int) (scale * 7.0F) : 0;
            int rangeY = (int) (scale * 10.0F) + 1;

            int y;
            for (y = 0; y < rangeY && !b; ++y) {
                int pn = -1;

                while (pn <= 1) {
                    int z = -1;

                    while (true) {
                        if (z <= 1) {
                            label99:
                            {
                                Block k = W_WorldFunc.getBlock(super.worldObj, (int) (super.posX + 0.5D) + pn, (int) (super.posY + 0.5D) - y, (int) (super.posZ + 0.5D) + z);
                                if (!b && k != null && !Block.isEqualTo(k, Blocks.air)) {
                                    if (seaOnly && W_Block.isEqual(k, W_Block.getWater())) {
                                        --count;
                                    }

                                    if (count <= 0) {
                                        particlePosY = super.posY + 1.0D + (double) (scale / 5.0F) - (double) y;
                                        b = true;
                                        pn += 100;
                                        break label99;
                                    }
                                }

                                ++z;
                                continue;
                            }
                        }

                        ++pn;
                        break;
                    }
                }
            }

            double var20 = (double) (rangeY - y + 1) / (5.0D * (double) scale) / 2.0D;
            if (b && this.getAcInfo().particlesScale > 0.01F) {
                for (int var21 = 0; var21 < (int) (throttle * 6.0D * var20); ++var21) {
                    float r = (float) (super.rand.nextDouble() * 3.141592653589793D * 2.0D);
                    double dx = (double) MathHelper.cos(r);
                    double dz = (double) MathHelper.sin(r);
                    MCH_ParticleParam prm = new MCH_ParticleParam(super.worldObj, "smoke", super.posX + dx * (double) scale * 3.0D, particlePosY + (super.rand.nextDouble() - 0.5D) * (double) scale, super.posZ + dz * (double) scale * 3.0D, (double) scale * dx * 0.3D, (double) scale * -0.4D * 0.05D, (double) scale * dz * 0.3D, scale * 5.0F);
                    prm.setColor(prm.a * 0.6F, prm.r, prm.g, prm.b);
                    prm.age = (int) (10.0F * scale);
                    prm.motionYUpAge = seaOnly ? 0.2F : 0.1F;
                    MCH_ParticlesUtil.spawnParticle(prm);
                }
            }

        }
    }

    protected boolean canTriggerWalking() {
        return false;
    }

    public AxisAlignedBB getCollisionBox(Entity par1Entity) {
        return par1Entity.boundingBox;
    }

    public AxisAlignedBB getBoundingBox() {
        return super.boundingBox;
    }

    public boolean canBePushed() {
        return false;
    }

    public double getMountedYOffset() {
        return 0.0D;
    }

    public float getShadowSize() {
        return 2.0F;
    }

    public boolean canBeCollidedWith() {
        return !super.isDead;
    }

    public boolean useFlare(int type) {
        if (this.getAcInfo() != null && this.getAcInfo().haveFlare()) {
            int[] arr$ = this.getAcInfo().flare.types;
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                int i = arr$[i$];
                if (i == type) {
                    this.setCommonStatus(0, true);
                    if (this.flareDv.use(type)) {
                        return true;
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public boolean useChaff() {
        if (this.getAcInfo() != null && this.getAcInfo().haveChaff()) {
            if (this.chaff.onUse()) {
                return true;
            }
            return false;
        } else {
            return false;
        }
    }

    public boolean useMaintenance() {
        if (this.getAcInfo() != null && this.getAcInfo().haveMaintenance()) {
            if (this.maintenance.onUse()) {
                if (!this.worldObj.isRemote) {
                    this.recoverERABoundingBoxesByMaintenance();
                }
                return true;
            }
            return false;
        } else {
            return false;
        }
    }

    public boolean useAPS(Entity e) {
        if (this.getAcInfo() != null && this.getAcInfo().haveAPS()) {
            if (this.aps.onUse(e)) {
                return true;
            }
            return false;
        } else {
            return false;
        }
    }

    public boolean useECMJammer(Entity e) {
        if (this.getAcInfo() != null && this.getAcInfo().haveECMJammer()) {
            if (this.ecmJammer.onUse(e)) {
                return true;
            }
            return false;
        } else {
            return false;
        }
    }

    public int getCurrentFlareType() {
        return !this.haveFlare() ? 0 : this.getAcInfo().flare.types[this.currentFlareIndex];
    }

    public void nextFlareType() {
        if (this.haveFlare()) {
            this.currentFlareIndex = (this.currentFlareIndex + 1) % this.getAcInfo().flare.types.length;
        }

    }

    public boolean canUseFlare() {
        return this.getAcInfo() != null && this.getAcInfo().haveFlare() && (!this.getCommonStatus(0) && this.flareDv.tick == 0);
    }

    public boolean isFlarePreparation() {
        return this.flareDv.isInPreparation();
    }

    public boolean isFlareUsing() {
        return this.flareDv.isUsing();
    }

    public int getUsingFlareType() {
        if (this.flareDv == null || !this.flareDv.isUsing()) {
            return 0;
        }
        return this.flareDv.getFlareType();
    }

    public boolean isUsingFlareType(int type) {
        return type > 0 && this.flareDv != null && this.flareDv.isUsing() && this.flareDv.getFlareType() == type;
    }

    public int getFlareTick() {
        return this.flareDv.tick;
    }

    public boolean haveFlare() {
        return this.getAcInfo() != null && this.getAcInfo().haveFlare();
    }

    public boolean haveFlare(int seatID) {
        return this.haveFlare() && seatID >= 0 && seatID <= 1;
    }

    public boolean canUseChaff() {
        return this.getAcInfo() != null && this.getAcInfo().haveChaff() && this.chaff.tick == 0;
    }

    public boolean isChaffUsing() {
        return this.chaff.isUsing();
    }

    public boolean isECMJammerUsing() {
        return ecmJammerUseTime > 0 || this.ecmJammer.isUsing();
    }

    public boolean canUseMaintenance() {
        return this.getAcInfo() != null && this.getAcInfo().haveMaintenance() && this.maintenance.tick == 0;
    }

    public boolean canUseAPS() {
        return this.getAcInfo() != null && this.getAcInfo().haveAPS() && this.aps.tick == 0;
    }

    public boolean canUseECMJammer() {
        return this.getAcInfo() != null && this.getAcInfo().haveECMJammer() && this.ecmJammer.tick == 0;
    }

    public boolean haveChaff() {
        return this.getAcInfo() != null && this.getAcInfo().haveChaff();
    }

    public boolean haveMaintenance() {
        return this.getAcInfo() != null && this.getAcInfo().haveMaintenance();
    }

    public boolean haveAPS() {
        return this.getAcInfo() != null && this.getAcInfo().haveAPS();
    }

    public boolean haveECMJammer() {
        return this.getAcInfo() != null && this.getAcInfo().haveECMJammer();
    }

    public MCH_EntitySeat[] getSeats() {
        return this.seats != null ? this.seats : seatsDummy;
    }

    public int getSeatIdByEntity(Entity entity) {
        if (entity == null) {
            return -1;
        } else if (isEqual(this.getRiddenByEntity(), entity)) {
            return 0;
        } else {
            for (int i = 0; i < this.getSeats().length; ++i) {
                MCH_EntitySeat seat = this.getSeats()[i];
                if (seat != null && isEqual(seat.riddenByEntity, entity)) {
                    return i + 1;
                }
            }

            return -1;
        }
    }

    public MCH_EntitySeat getSeatByEntity(Entity entity) {
        int idx = this.getSeatIdByEntity(entity);
        return idx > 0 ? this.getSeat(idx - 1) : null;
    }

    public Entity getEntityBySeatId(int id) {
        if (id == 0) {
            return this.getRiddenByEntity();
        } else {
            --id;
            return id >= 0 && id < this.getSeats().length ? (this.seats[id] != null ? this.seats[id].riddenByEntity : null) : null;
        }
    }

    public EntityPlayer getEntityByWeaponId(int id) {
        if (id >= 0 && id < this.getWeaponNum()) {
            for (int i = 0; i < this.currentWeaponID.length; ++i) {
                if (this.currentWeaponID[i] == id) {
                    Entity e = this.getEntityBySeatId(i);
                    if (e instanceof EntityPlayer) {
                        return (EntityPlayer) e;
                    }
                }
            }
        }

        return null;
    }

    public Entity getWeaponUserByWeaponName(String name) {
        if (this.getAcInfo() == null) {
            return null;
        } else {
            MCH_AircraftInfo.Weapon weapon = this.getAcInfo().getWeaponByName(name);
            Entity entity = null;
            if (weapon != null) {
                entity = this.getEntityBySeatId(this.getWeaponSeatID((MCH_WeaponInfo) null, weapon));
                if (entity == null && weapon.canUsePilot) {
                    entity = this.getRiddenByEntity();
                }
            }

            return entity;
        }
    }

    protected void newSeats(int seatsNum) {
        if (seatsNum >= 2) {
            if (this.seats != null) {
                for (int i = 0; i < this.seats.length; ++i) {
                    if (this.seats[i] != null) {
                        this.seats[i].setDead();
                        this.seats[i] = null;
                    }
                }
            }

            this.seats = new MCH_EntitySeat[seatsNum - 1];
        }

    }

    public MCH_EntitySeat getSeat(int idx) {
        return idx < this.seats.length ? this.seats[idx] : null;
    }

    public void setSeat(int idx, MCH_EntitySeat seat) {
        if (idx < this.seats.length) {
            MCH_Lib.DbgLog(super.worldObj, "MCH_EntityAircraft.setSeat SeatID=" + idx + " / seat[]" + (this.seats[idx] != null) + " / " + (seat.riddenByEntity != null), new Object[0]);
            if (this.seats[idx] != null && this.seats[idx].riddenByEntity != null) {
                ;
            }

            this.seats[idx] = seat;
        }

    }

    public boolean isValidSeatID(int seatID) {
        return seatID >= 0 && seatID < this.getSeatNum() + 1;
    }

    public void updateHitBoxPosition() {
    }

    public void updateSeatsPosition(double px, double py, double pz, boolean setPrevPos) {
        MCH_SeatInfo[] info = this.getSeatsInfo();
        if (this.pilotSeat != null && !this.pilotSeat.isDead) {
            this.pilotSeat.prevPosX = this.pilotSeat.posX;
            this.pilotSeat.prevPosY = this.pilotSeat.posY;
            this.pilotSeat.prevPosZ = this.pilotSeat.posZ;
            this.pilotSeat.setPosition(px, py, pz);
            if (info != null && info.length > 0 && info[0] != null) {
                Vec3 i = this.getTransformedPosition(info[0].pos.xCoord, info[0].pos.yCoord, info[0].pos.zCoord, px, py, pz, info[0].rotSeat);
                this.pilotSeat.setPosition(i.xCoord, i.yCoord, i.zCoord);
            }

            this.pilotSeat.rotationPitch = this.getRotPitch();
            this.pilotSeat.rotationYaw = this.getRotYaw();
            if (setPrevPos) {
                this.pilotSeat.prevPosX = this.pilotSeat.posX;
                this.pilotSeat.prevPosY = this.pilotSeat.posY;
                this.pilotSeat.prevPosZ = this.pilotSeat.posZ;
            }
        }

        int var17 = 0;
        MCH_EntitySeat[] arr$ = this.seats;
        int len$ = arr$.length;

        for (int i$ = 0; i$ < len$; ++i$) {
            MCH_EntitySeat seat = arr$[i$];
            ++var17;
            if (seat != null && !seat.isDead) {
                float offsetY = 0.0F;
                if (seat.riddenByEntity != null) {
                    if (W_Lib.isClientPlayer(seat.riddenByEntity)) {
                        offsetY = 1.0F;
                    } else if (seat.riddenByEntity.height >= 1.0F) {
                        offsetY = -seat.riddenByEntity.height + 1.0F;
                    }
                }

                seat.prevPosX = seat.posX;
                seat.prevPosY = seat.posY;
                seat.prevPosZ = seat.posZ;
                MCH_SeatInfo si = var17 < info.length ? info[var17] : info[0];
                Vec3 v = this.getTransformedPosition(si.pos.xCoord, si.pos.yCoord + (double) offsetY, si.pos.zCoord, px, py, pz, si.rotSeat);
                seat.setPosition(v.xCoord, v.yCoord, v.zCoord);
                seat.rotationPitch = this.getRotPitch();
                seat.rotationYaw = this.getRotYaw();
                if (setPrevPos) {
                    seat.prevPosX = seat.posX;
                    seat.prevPosY = seat.posY;
                    seat.prevPosZ = seat.posZ;
                }

                if (si instanceof MCH_SeatRackInfo) {
                    seat.updateRotation(((MCH_SeatRackInfo) si).fixYaw + this.getRotYaw(), ((MCH_SeatRackInfo) si).fixPitch);
                }

                seat.updatePosition();
            }
        }

    }

    public int getClientPositionDelayCorrection() {
        if (MCH_MOD.proxy.getClientPlayer() == this.riddenByEntity) {
            return 7;
        }
        return 0;
    }

    @Override
    public void setPositionAndRotation2(double par1, double par3, double par5, float par7, float par8, int par9) {
        this.aircraftPosRotInc = par9 + this.getClientPositionDelayCorrection();
        this.aircraftX = par1;
        this.aircraftY = par3;
        this.aircraftZ = par5;
        this.aircraftYaw = par7;
        this.aircraftPitch = par8;
        this.motionX = this.velocityX;
        this.motionY = this.velocityY;
        this.motionZ = this.velocityZ;
    }

    public void updateRiderPosition(double px, double py, double pz) {
        MCH_SeatInfo[] info = this.getSeatsInfo();
        if (super.riddenByEntity != null && !super.riddenByEntity.isDead) {
            float riddenEntityYOffset = super.riddenByEntity.yOffset;
            float offset = 0.0F;
            if (super.riddenByEntity instanceof EntityPlayer && !W_Lib.isClientPlayer(super.riddenByEntity)) {
                --offset;
            }

            Vec3 v;
            if (info != null && info.length > 0) {
                v = this.getTransformedPosition(info[0].pos.xCoord, info[0].pos.yCoord + (double) riddenEntityYOffset - 0.5D, info[0].pos.zCoord, px, py, pz, info[0].rotSeat);
            } else {
                v = this.getTransformedPosition(0.0D, (double) (riddenEntityYOffset - 1.0F), 0.0D);
            }

            super.riddenByEntity.yOffset = 0.0F;
            super.riddenByEntity.setPosition(v.xCoord, v.yCoord, v.zCoord);
            super.riddenByEntity.yOffset = riddenEntityYOffset;
        }

    }

    public void updateRiderPosition() {
        this.updateRiderPosition(super.posX, super.posY, super.posZ);
    }

    public Vec3 calcOnTurretPos(Vec3 pos) {
        float ry = this.getLastRiderYaw();
        if (this.getRiddenByEntity() != null) {
            ry = this.getRiddenByEntity().rotationYaw;
        }

        Vec3 tpos = this.getAcInfo().turretPosition.addVector(0.0D, pos.yCoord, 0.0D);
        Vec3 v = pos.addVector(-tpos.xCoord, -tpos.yCoord, -tpos.zCoord);
        v = MCH_Lib.RotVec3(v, -ry, 0.0F, 0.0F);
        Vec3 vv = MCH_Lib.RotVec3(tpos, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
        v.xCoord += vv.xCoord;
        v.yCoord += vv.yCoord;
        v.zCoord += vv.zCoord;
        return v;
    }

    public float getLastRiderYaw() {
        return this.lastRiderYaw;
    }

    public float getLastRiderPitch() {
        return this.lastRiderPitch;
    }

    @SideOnly(Side.CLIENT)
    public void setupAllRiderRenderPosition(float tick, EntityPlayer player) {
        double x = super.lastTickPosX + (super.posX - super.lastTickPosX) * (double) tick;
        double y = super.lastTickPosY + (super.posY - super.lastTickPosY) * (double) tick;
        double z = super.lastTickPosZ + (super.posZ - super.lastTickPosZ) * (double) tick;
        this.updateRiderPosition(x, y, z);
        this.updateSeatsPosition(x, y, z, true);

        for (int cpi = 0; cpi < this.getSeatNum() + 1; ++cpi) {
            Entity seatInfo = this.getEntityBySeatId(cpi);
            if (seatInfo != null) {
                seatInfo.lastTickPosX = seatInfo.posX;
                seatInfo.lastTickPosY = seatInfo.posY;
                seatInfo.lastTickPosZ = seatInfo.posZ;
            }
        }

        if (this.getTVMissile() != null && W_Lib.isClientPlayer(this.getTVMissile().shootingEntity)) {
            MCH_EntityTvMissile var14 = this.getTVMissile();
            x = var14.prevPosX + (var14.posX - var14.prevPosX) * (double) tick;
            y = var14.prevPosY + (var14.posY - var14.prevPosY) * (double) tick;
            z = var14.prevPosZ + (var14.posZ - var14.prevPosZ) * (double) tick;
            MCH_ViewEntityDummy.setCameraPosition(x, y, z);
        } else {
            MCH_AircraftInfo.CameraPosition var13 = this.getCameraPosInfo();
            if (var13 != null && var13.pos != null) {
                MCH_SeatInfo var12 = this.getSeatInfo(player);
                Vec3 v;
                if (var12 != null && var12.rotSeat) {
                    v = this.calcOnTurretPos(var13.pos);
                } else {
                    float camRoll = this.getRotRoll();
                    if (this instanceof MCP_EntityPlane && MCH_MOD.proxy.getThirdPersonViewType() == 1) {
                        MCP_EntityPlane plane = (MCP_EntityPlane) this;
                        if (plane.isWTMouseAimActive()) {
                            // Third-person readability: keep partial roll follow so aircraft bank is visible.
                            camRoll *= 0.35F;
                        }
                    }
                    v = MCH_Lib.RotVec3(var13.pos, -this.getRotYaw(), -this.getRotPitch(), -camRoll);
                }

                MCH_ViewEntityDummy.setCameraPosition(x + v.xCoord, y + v.yCoord, z + v.zCoord);
                if (var13.fixRot) {
                    ;
                }
            }
        }

    }

    public Vec3 getTurretPos(Vec3 pos, boolean turret) {
        if (turret) {
            float ry = this.getLastRiderYaw();
            if (this.getRiddenByEntity() != null) {
                ry = this.getRiddenByEntity().rotationYaw;
            }

            Vec3 tpos = this.getAcInfo().turretPosition.addVector(0.0D, pos.yCoord, 0.0D);
            Vec3 v = pos.addVector(-tpos.xCoord, -tpos.yCoord, -tpos.zCoord);
            v = MCH_Lib.RotVec3(v, -ry, 0.0F, 0.0F);
            Vec3 vv = MCH_Lib.RotVec3(tpos, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
            v.xCoord += vv.xCoord;
            v.yCoord += vv.yCoord;
            v.zCoord += vv.zCoord;
            return v;
        } else {
            return Vec3.createVectorHelper(0.0D, 0.0D, 0.0D);
        }
    }

    public Vec3 getTransformedPosition(Vec3 v) {
        return this.getTransformedPosition(v.xCoord, v.yCoord, v.zCoord);
    }

    public Vec3 getTransformedPosition(double x, double y, double z) {
        return this.getTransformedPosition(x, y, z, super.posX, super.posY, super.posZ);
    }

    public Vec3 getTransformedPosition(Vec3 v, Vec3 pos) {
        return this.getTransformedPosition(v.xCoord, v.yCoord, v.zCoord, pos.xCoord, pos.yCoord, pos.zCoord);
    }

    public Vec3 getTransformedPosition(Vec3 v, double px, double py, double pz) {
        return this.getTransformedPosition(v.xCoord, v.yCoord, v.zCoord, super.posX, super.posY, super.posZ);
    }

    public Vec3 getTransformedPosition(double x, double y, double z, double px, double py, double pz) {
        Vec3 v = MCH_Lib.RotVec3(x, y, z, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
        return v.addVector(px, py, pz);
    }

    public Vec3 getTransformedPosition(double x, double y, double z, double px, double py, double pz, boolean rotSeat) {
        if (rotSeat && this.getAcInfo() != null) {
            MCH_AircraftInfo v = this.getAcInfo();
            Vec3 tv = MCH_Lib.RotVec3(x - v.turretPosition.xCoord, y - v.turretPosition.yCoord, z - v.turretPosition.zCoord, -this.getLastRiderYaw() + this.getRotYaw(), 0.0F, 0.0F);
            x = tv.xCoord + v.turretPosition.xCoord;
            y = tv.yCoord + v.turretPosition.xCoord;
            z = tv.zCoord + v.turretPosition.xCoord;
        }

        Vec3 v1 = MCH_Lib.RotVec3(x, y, z, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
        return v1.addVector(px, py, pz);
    }

    protected MCH_SeatInfo[] getSeatsInfo() {
        if (this.seatsInfo != null) {
            return this.seatsInfo;
        } else {
            this.newSeatsPos();
            return this.seatsInfo;
        }
    }

    protected void setSeatsInfo(MCH_SeatInfo[] v) {
        this.seatsInfo = v;
    }

    public MCH_SeatInfo getSeatInfo(int index) {
        MCH_SeatInfo[] seats = this.getSeatsInfo();
        return index >= 0 && seats != null && index < seats.length ? seats[index] : null;
    }

    public MCH_SeatInfo getSeatInfo(Entity entity) {
        return this.getSeatInfo(this.getSeatIdByEntity(entity));
    }

    public int getSeatNum() {
        if (this.getAcInfo() == null) {
            return 0;
        } else {
            int s = this.getAcInfo().getNumSeatAndRack();
            return s >= 1 ? s - 1 : 1;
        }
    }

    protected void newSeatsPos() {
        if (this.getAcInfo() != null) {
            MCH_SeatInfo[] v = new MCH_SeatInfo[this.getAcInfo().getNumSeatAndRack()];

            for (int i = 0; i < v.length; ++i) {
                v[i] = (MCH_SeatInfo) this.getAcInfo().seatList.get(i);
            }

            this.setSeatsInfo(v);
        }

    }

    public void createSeats(String uuid) {
        if (!super.worldObj.isRemote) {
            if (!uuid.isEmpty()) {
                this.setCommonUniqueId(uuid);
                this.seats = new MCH_EntitySeat[this.getSeatNum()];

                for (int i = 0; i < this.seats.length; ++i) {
                    this.seats[i] = new MCH_EntitySeat(super.worldObj, super.posX, super.posY, super.posZ);
                    this.seats[i].parentUniqueID = this.getCommonUniqueId();
                    this.seats[i].seatID = i;
                    this.seats[i].setParent(this);
                    super.worldObj.spawnEntityInWorld(this.seats[i]);
                }

            }
        }
    }

    public boolean interactFirstSeat(EntityPlayer player) {
        if (this.getSeats() == null) {
            return false;
        } else {
            int seatId = 1;
            MCH_EntitySeat[] arr$ = this.getSeats();
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                MCH_EntitySeat seat = arr$[i$];
                if (seat != null && seat.riddenByEntity == null && !this.isMountedEntity(player) && this.canRideSeatOrRack(seatId, player)) {
                    if (!super.worldObj.isRemote) {
                        player.mountEntity(seat);
                    }
                    break;
                }

                ++seatId;
            }

            return true;
        }
    }

    public void onMountPlayerSeat(MCH_EntitySeat seat, Entity entity) {
        if (seat != null && (entity instanceof EntityPlayer || entity instanceof MCH_EntityGunner)) {
            if (super.worldObj.isRemote && MCH_Lib.getClientPlayer() == entity) {
                this.switchGunnerFreeLookMode(false);
            }

            this.initCurrentWeapon(entity);
            MCH_Lib.DbgLog(super.worldObj, "onMountEntitySeat:%d", W_Entity.getEntityId(entity));
            Entity pilot = this.getRiddenByEntity();
            int sid = this.getSeatIdByEntity(entity);
            if (sid == 1 && (this.getAcInfo() == null || !this.getAcInfo().isEnableConcurrentGunnerMode)) {
                this.switchGunnerMode(false);
            }

            if (sid > 0) {
                this.isGunnerModeOtherSeat = true;
            }

            if (pilot != null && this.getAcInfo() != null) {
                int cwid = this.getCurrentWeaponID(pilot);
                MCH_AircraftInfo.Weapon w = this.getAcInfo().getWeaponById(cwid);
                if (w != null && this.getWeaponSeatID(this.getWeaponInfoById(cwid), w) == sid) {
                    int next = this.getNextWeaponID(pilot, 1);
                    MCH_Lib.DbgLog(super.worldObj, "onMountEntitySeat:%d:->%d", W_Entity.getEntityId(pilot), next);
                    if (next >= 0) {
                        this.switchWeapon(pilot, next);
                    }
                }
            }

            if (super.worldObj.isRemote) {
                this.updateClientSettings(sid);
            }

        }
    }

    public MCH_WeaponInfo getWeaponInfoById(int id) {
        if (id >= 0) {
            MCH_WeaponSet ws = this.getWeapon(id);
            if (ws != null) {
                return ws.getInfo();
            }
        }

        return null;
    }

    public abstract boolean canMountWithNearEmptyMinecart();

    protected void mountWithNearEmptyMinecart() {
        if (this.getRidingEntity() == null) {
            byte d = 2;
            if (this.dismountedUserCtrl) {
                d = 6;
            }

            List list = super.worldObj.getEntitiesWithinAABBExcludingEntity(this, super.boundingBox.expand((double) d, (double) d, (double) d));
            if (list != null && !list.isEmpty()) {
                for (int i = 0; i < list.size(); ++i) {
                    Entity entity = (Entity) list.get(i);
                    if (entity instanceof EntityMinecartEmpty) {
                        if (this.dismountedUserCtrl) {
                            return;
                        }

                        if (entity.riddenByEntity == null && entity.canBePushed()) {
                            this.waitMountEntity = 20;
                            MCH_Lib.DbgLog(super.worldObj.isRemote, "MCH_EntityAircraft.mountWithNearEmptyMinecart:" + entity, new Object[0]);
                            this.mountEntity(entity);
                            return;
                        }
                    }
                }
            }

            this.dismountedUserCtrl = false;
        }
    }

    public boolean isRidePlayer() {
        if (this.getRiddenByEntity() instanceof EntityPlayer || this.getRiddenByEntity() instanceof MCH_EntityGunner) {
            return true;
        } else {
            MCH_EntitySeat[] arr$ = this.getSeats();
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                MCH_EntitySeat seat = arr$[i$];
                if (seat != null && (seat.riddenByEntity instanceof EntityPlayer || seat.riddenByEntity instanceof MCH_EntityGunner)) {
                    return true;
                }
            }

            return false;
        }
    }

    public void onUnmountPlayerSeat(MCH_EntitySeat seat, Entity entity) {
        MCH_Lib.DbgLog(super.worldObj, "onUnmountPlayerSeat:%d", W_Entity.getEntityId(entity));
        int sid = this.getSeatIdByEntity(entity);
        this.camera.initCamera(sid, entity);
        MCH_SeatInfo seatInfo = this.getSeatInfo(seat.seatID + 1);
        if (seatInfo != null) {
            this.setUnmountPosition(entity, Vec3.createVectorHelper(seatInfo.pos.xCoord, 0.0D, seatInfo.pos.zCoord));
        }

        if (!this.isRidePlayer()) {
            this.switchGunnerMode(false);
            this.switchHoveringMode(false);
        }

    }

    public boolean isCreatedSeats() {
        return !this.getCommonUniqueId().isEmpty();
    }

    public void onUpdate_Seats() {
        boolean b = false;

        for (int i = 0; i < this.seats.length; ++i) {
            if (this.seats[i] != null) {
                if (!this.seats[i].isDead) {
                    this.seats[i].fallDistance = 0.0F;
                }
            } else {
                b = true;
            }
        }

        if (b) {
            if (this.seatSearchCount > 40) {
                if (super.worldObj.isRemote) {
                    MCH_PacketSeatListRequest.requestSeatList(this);
                } else {
                    this.searchSeat();
                }

                this.seatSearchCount = 0;
            }

            ++this.seatSearchCount;
        }

    }

    public void searchSeat() {
        List list = super.worldObj.getEntitiesWithinAABB(MCH_EntitySeat.class, super.boundingBox.expand(60.0D, 60.0D, 60.0D));

        for (int i = 0; i < list.size(); ++i) {
            MCH_EntitySeat seat = (MCH_EntitySeat) list.get(i);
            if (!seat.isDead && seat.parentUniqueID.equals(this.getCommonUniqueId()) && seat.seatID >= 0 && seat.seatID < this.getSeatNum() && this.seats[seat.seatID] == null) {
                this.seats[seat.seatID] = seat;
                seat.setParent(this);
            }
        }

    }

    public String getCommonUniqueId() {
        return this.commonUniqueId;
    }

    public void setCommonUniqueId(String uniqId) {
        this.commonUniqueId = uniqId;
    }

    public void setDead() {
        this.setDead(false);
    }

    public void setDead(boolean dropItems) {
        super.dropContentsWhenDead = dropItems;
        super.setDead();
        if (this.getRiddenByEntity() != null) {
            this.getRiddenByEntity().mountEntity((Entity) null);
        }

        this.getGuiInventory().setDead();
        MCH_EntitySeat[] arr$ = this.seats;
        int len$ = arr$.length;

        int i$;
        for (i$ = 0; i$ < len$; ++i$) {
            MCH_EntitySeat e = arr$[i$];
            if (e != null) {
                e.setDead();
            }
        }

        if (this.soundUpdater != null) {
            this.soundUpdater.update();
        }

        if (this.getTowChainEntity() != null) {
            this.getTowChainEntity().setDead();
            this.setTowChainEntity(null);
        }

        Entity[] var6 = this.getParts();
        len$ = var6.length;

        for (i$ = 0; i$ < len$; ++i$) {
            Entity var7 = var6[i$];
            if (var7 != null) {
                var7.setDead();
            }
        }

        MCH_Lib.DbgLog(super.worldObj, "setDead:" + (this.getAcInfo() != null ? this.getAcInfo().name : "null"), new Object[0]);
    }

    public void unmountEntity() {
        if (!this.isRidePlayer()) {
            this.switchHoveringMode(false);
        }

        this.moveLeft = this.moveRight = this.throttleDown = this.throttleUp = false;
        Entity rByEntity = null;
        if (super.riddenByEntity != null) {
            rByEntity = super.riddenByEntity;
            this.camera.initCamera(0, rByEntity);
            if (!super.worldObj.isRemote) {
                super.riddenByEntity.mountEntity(null);
            }
        } else if (this.lastRiddenByEntity != null) {
            rByEntity = this.lastRiddenByEntity;
            if (rByEntity instanceof EntityPlayer) {
                this.camera.initCamera(0, rByEntity);
            }
        }

        MCH_Lib.DbgLog(super.worldObj, "unmountEntity:" + rByEntity);
        if (!this.isRidePlayer()) {
            this.switchGunnerMode(false);
        }

        setCommonStatus(1, false);
        if (!this.isUAV()) {
            this.setUnmountPosition(rByEntity, this.getSeatsInfo()[0].pos);
        } else if (rByEntity != null && rByEntity.ridingEntity instanceof MCH_EntityUavStation) {
            rByEntity.mountEntity(null);
        }

        super.riddenByEntity = null;
        this.lastRiddenByEntity = null;
        if (this.cs_dismountAll) {
            this.unmountCrew(false);
        }

    }

    public Entity getRidingEntity() {
        return super.ridingEntity;
    }

    public void startUnmountCrew() {
        this.isParachuting = true;
        if (this.haveHatch()) {
            this.foldHatch(true, true);
        }

    }

    public void stopUnmountCrew() {
        this.isParachuting = false;
    }

    public void unmountCrew() {
        if (this.getAcInfo() != null) {
            if (this.getAcInfo().haveRepellingHook()) {
                if (!this.isRepelling()) {
                    if (MCH_Lib.getBlockIdY(this, 3, -4) > 0) {
                        this.unmountCrew(false);
                    } else if (this.canStartRepelling()) {
                        this.startRepelling();
                    }
                } else {
                    this.stopRepelling();
                }
            } else if (this.isParachuting) {
                this.stopUnmountCrew();
            } else if (this.getAcInfo().isEnableParachuting && MCH_Lib.getBlockIdY(this, 3, -10) == 0) {
                this.startUnmountCrew();
            } else {
                this.unmountCrew(false);
            }

        }
    }

    public boolean isRepelling() {
        return this.getCommonStatus(5);
    }

    public void setRepellingStat(boolean b) {
        this.setCommonStatus(5, b);
    }

    public Vec3 getRopePos(int ropeIndex) {
        return this.getAcInfo() != null && this.getAcInfo().haveRepellingHook() && ropeIndex < this.getAcInfo().repellingHooks.size() ? this.getTransformedPosition(((MCH_AircraftInfo.RepellingHook) this.getAcInfo().repellingHooks.get(ropeIndex)).pos) : Vec3.createVectorHelper(super.posX, super.posY, super.posZ);
    }

    private void startRepelling() {
        MCH_Lib.DbgLog(super.worldObj, "MCH_EntityAircraft.startRepelling()", new Object[0]);
        this.setRepellingStat(true);
        this.throttleUp = false;
        this.throttleDown = false;
        this.moveLeft = false;
        this.moveRight = false;
        this.tickRepelling = 0;
    }

    private void stopRepelling() {
        MCH_Lib.DbgLog(super.worldObj, "MCH_EntityAircraft.stopRepelling()", new Object[0]);
        this.setRepellingStat(false);
    }

    public boolean canStartRepelling() {
        if (this.getAcInfo().haveRepellingHook() && this.isHovering() && abs(this.getRotPitch()) < 3.0F && abs(this.getRotRoll()) < 3.0F) {
            Vec3 v = ((Vec3) this.prevPosition.oldest()).addVector(-super.posX, -super.posY, -super.posZ);
            if (v.lengthVector() < 0.3D) {
                return true;
            }
        }

        return false;
    }

    public boolean unmountCrew(boolean unmountParachute) {
        boolean ret = false;
        MCH_SeatInfo[] pos = this.getSeatsInfo();

        for (int i = 0; i < this.seats.length; ++i) {
            if (this.seats[i] != null && this.seats[i].riddenByEntity != null) {
                Entity entity = this.seats[i].riddenByEntity;
                if (!(entity instanceof EntityPlayer) && !(entity instanceof MCH_EntityGunner) && !(pos[i + 1] instanceof MCH_SeatRackInfo)) {
                    Vec3 dropPos;
                    if (unmountParachute) {
                        if (this.getSeatIdByEntity(entity) > 1) {
                            ret = true;
                            dropPos = this.getTransformedPosition(this.getAcInfo().mobDropOption.pos, (Vec3) this.prevPosition.oldest());
                            this.seats[i].posX = dropPos.xCoord;
                            this.seats[i].posY = dropPos.yCoord;
                            this.seats[i].posZ = dropPos.zCoord;
                            entity.mountEntity((Entity) null);
                            entity.posX = dropPos.xCoord;
                            entity.posY = dropPos.yCoord;
                            entity.posZ = dropPos.zCoord;
                            this.dropEntityParachute(entity);
                            break;
                        }
                    } else {
                        ret = true;
                        dropPos = pos[i + 1].pos;
                        this.setUnmountPosition(this.seats[i], pos[i + 1].pos);
                        entity.mountEntity((Entity) null);
                        this.setUnmountPosition(entity, pos[i + 1].pos);
                    }
                }
            }
        }

        return ret;
    }

    public void setUnmountPosition(Entity rByEntity, Vec3 pos) {
        if (rByEntity != null) {
            MCH_AircraftInfo info = this.getAcInfo();
            Vec3 v;
            if (info != null && info.unmountPosition != null) {
                v = this.getTransformedPosition(info.unmountPosition);
            } else {
                double x = pos.xCoord;
                x = x >= 0.0D ? x + 3.0D : x - 3.0D;
                v = this.getTransformedPosition(x, 2.0D, pos.zCoord);
            }

            rByEntity.setPosition(v.xCoord, v.yCoord, v.zCoord);
            this.listUnmountReserve.add(new MCH_EntityAircraft.UnmountReserve(rByEntity, v.xCoord, v.yCoord, v.zCoord));
        }

    }

    public boolean unmountEntityFromSeat(Entity entity) {
        if (entity != null && this.seats != null && this.seats.length != 0) {
            MCH_EntitySeat[] arr$ = this.seats;
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                MCH_EntitySeat seat = arr$[i$];
                if (seat != null && seat.riddenByEntity != null && W_Entity.isEqual(seat.riddenByEntity, entity)) {
                    entity.mountEntity((Entity) null);
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public void ejectSeat(Entity entity) {
        int sid = this.getSeatIdByEntity(entity);
        if (sid >= 0 && sid <= 1) {
            if (this.getGuiInventory().haveParachute()) {
                if (sid == 0) {
                    this.getGuiInventory().consumeParachute();
                    this.unmountEntity();
                    this.ejectSeatSub(entity, 0);
                    entity = this.getEntityBySeatId(1);
                    if (entity instanceof EntityPlayer) {
                        entity = null;
                    }
                }

                if (this.getGuiInventory().haveParachute() && entity != null) {
                    this.getGuiInventory().consumeParachute();
                    this.unmountEntityFromSeat(entity);
                    this.ejectSeatSub(entity, 1);
                }
            }

        }
    }

    public void ejectSeatSub(Entity entity, int sid) {
        Vec3 pos = this.getSeatInfo(sid) != null ? this.getSeatInfo(sid).pos : null;
        Vec3 v;
        if (pos != null) {
            v = this.getTransformedPosition(pos.xCoord, pos.yCoord + 2.0D, pos.zCoord);
            entity.setPosition(v.xCoord, v.yCoord, v.zCoord);
        }

        v = MCH_Lib.RotVec3(0.0D, 2.0D, 0.0D, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
        entity.motionX = super.motionX + v.xCoord + ((double) super.rand.nextFloat() - 0.5D) * 0.1D;
        entity.motionY = super.motionY + v.yCoord;
        entity.motionZ = super.motionZ + v.zCoord + ((double) super.rand.nextFloat() - 0.5D) * 0.1D;
        MCH_EntityParachute parachute = new MCH_EntityParachute(super.worldObj, entity.posX, entity.posY, entity.posZ);
        parachute.rotationYaw = entity.rotationYaw;
        parachute.motionX = entity.motionX;
        parachute.motionY = entity.motionY;
        parachute.motionZ = entity.motionZ;
        parachute.fallDistance = entity.fallDistance;
        parachute.user = entity;
        parachute.setType(2);
        super.worldObj.spawnEntityInWorld(parachute);
        if (this.getAcInfo().haveCanopy() && this.isCanopyClose()) {
            this.openCanopy_EjectSeat();
        }

        W_WorldFunc.MOD_playSoundAtEntity(entity, "eject_seat", 5.0F, 1.0F);
    }

    public boolean canEjectSeat(Entity entity) {
        int sid = this.getSeatIdByEntity(entity);
        return sid == 0 && this.isUAV() ? false : sid >= 0 && sid < 2 && this.getAcInfo() != null && this.getAcInfo().isEnableEjectionSeat;
    }

    public int getNumEjectionSeat() {
        return 0;
    }

    public int getMountedEntityNum() {
        int num = 0;
        if (super.riddenByEntity != null && !super.riddenByEntity.isDead) {
            ++num;
        }

        if (this.seats != null && this.seats.length > 0) {
            MCH_EntitySeat[] arr$ = this.seats;
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                MCH_EntitySeat seat = arr$[i$];
                if (seat != null && seat.riddenByEntity != null && !seat.riddenByEntity.isDead) {
                    ++num;
                }
            }
        }

        return num;
    }

    public void mountMobToSeats() {
        List list = super.worldObj.getEntitiesWithinAABB(W_Lib.getEntityLivingBaseClass(), super.boundingBox.expand(3.0D, 2.0D, 3.0D));

        for (int i = 0; i < list.size(); ++i) {
            Entity entity = (Entity) list.get(i);
            if (!(entity instanceof EntityPlayer) && entity.ridingEntity == null) {
                int sid = 1;
                MCH_EntitySeat[] arr$ = this.getSeats();
                int len$ = arr$.length;

                for (int i$ = 0; i$ < len$; ++i$) {
                    MCH_EntitySeat seat = arr$[i$];
                    if (seat != null && seat.riddenByEntity == null && !this.isMountedEntity(entity) && this.canRideSeatOrRack(sid, entity)) {
                        if (this.getSeatInfo(sid) instanceof MCH_SeatRackInfo) {
                            break;
                        }

                        entity.mountEntity(seat);
                    }

                    ++sid;
                }
            }
        }

    }

    public void mountEntityToRack() {
        MCH_Config var10000 = MCH_MOD.config;
        if (!MCH_Config.EnablePutRackInFlying.prmBool) {
            if (this.getCurrentThrottle() > 0.3D) {
                return;
            }

            Block countRideEntity = MCH_Lib.getBlockY(this, 1, -3, true);
            if (countRideEntity == null || W_Block.isEqual(countRideEntity, Blocks.air)) {
                return;
            }
        }

        int var12 = 0;

        for (int sid = 0; sid < this.getSeatNum(); ++sid) {
            MCH_EntitySeat seat = this.getSeat(sid);
            if (this.getSeatInfo(1 + sid) instanceof MCH_SeatRackInfo && seat != null && seat.riddenByEntity == null) {
                MCH_SeatRackInfo info = (MCH_SeatRackInfo) this.getSeatInfo(1 + sid);
                Vec3 v = MCH_Lib.RotVec3(info.getEntryPos().xCoord, info.getEntryPos().yCoord, info.getEntryPos().zCoord, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
                v.xCoord += super.posX;
                v.yCoord += super.posY;
                v.zCoord += super.posZ;
                AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(v.xCoord, v.yCoord, v.zCoord, v.xCoord, v.yCoord, v.zCoord);
                float range = info.range;
                List list = super.worldObj.getEntitiesWithinAABBExcludingEntity(this, bb.expand((double) range, (double) range, (double) range));

                for (int i = 0; i < list.size(); ++i) {
                    Entity entity = (Entity) list.get(i);
                    if (this.canRideSeatOrRack(1 + sid, entity)) {
                        if (entity instanceof MCH_IEntityCanRideAircraft) {
                            if (((MCH_IEntityCanRideAircraft) entity).canRideAircraft(this, sid, info)) {
                                MCH_Lib.DbgLog(super.worldObj, "MCH_EntityAircraft.mountEntityToRack:%d:%s", new Object[]{Integer.valueOf(sid), entity});
                                entity.mountEntity(seat);
                                ++var12;
                                break;
                            }
                        } else if (entity.ridingEntity == null) {
                            NBTTagCompound nbt = entity.getEntityData();
                            if (nbt.hasKey("CanMountEntity") && nbt.getBoolean("CanMountEntity")) {
                                MCH_Lib.DbgLog(super.worldObj, "MCH_EntityAircraft.mountEntityToRack:%d:%s:%s", new Object[]{Integer.valueOf(sid), entity, entity.getClass()});
                                entity.mountEntity(seat);
                                ++var12;
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (var12 > 0) {
            W_WorldFunc.DEF_playSoundEffect(super.worldObj, super.posX, super.posY, super.posZ, "random.click", 1.0F, 1.0F);
        }

    }

    public void unmountEntityFromRack() {
        for (int sid = this.getSeatNum() - 1; sid >= 0; --sid) {
            MCH_EntitySeat seat = this.getSeat(sid);
            if (this.getSeatInfo(sid + 1) instanceof MCH_SeatRackInfo && seat != null && seat.riddenByEntity != null) {
                MCH_SeatRackInfo info = (MCH_SeatRackInfo) this.getSeatInfo(sid + 1);
                Entity entity = seat.riddenByEntity;
                Vec3 pos = info.getEntryPos();
                if (entity instanceof MCH_EntityAircraft) {
                    if (pos.zCoord >= (double) this.getAcInfo().bbZ) {
                        pos = pos.addVector(0.0D, 0.0D, 12.0D);
                    } else {
                        pos = pos.addVector(0.0D, 0.0D, -12.0D);
                    }
                }

                Vec3 v = MCH_Lib.RotVec3(pos.xCoord, pos.yCoord, pos.zCoord, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
                seat.posX = entity.posX = super.posX + v.xCoord;
                seat.posY = entity.posY = super.posY + v.yCoord;
                seat.posZ = entity.posZ = super.posZ + v.zCoord;
                MCH_EntityAircraft.UnmountReserve ur = new MCH_EntityAircraft.UnmountReserve(entity, entity.posX, entity.posY, entity.posZ);
                ur.cnt = 8;
                this.listUnmountReserve.add(ur);
                entity.mountEntity((Entity) null);
                if (MCH_Lib.getBlockIdY(this, 3, -20) > 0) {
                    MCH_Lib.DbgLog(super.worldObj, "MCH_EntityAircraft.unmountEntityFromRack:%d:%s", new Object[]{Integer.valueOf(sid), entity});
                } else {
                    MCH_Lib.DbgLog(super.worldObj, "MCH_EntityAircraft.unmountEntityFromRack:%d Parachute:%s", new Object[]{Integer.valueOf(sid), entity});
                    this.dropEntityParachute(entity);
                }
                break;
            }
        }

    }

    public void dropEntityParachute(Entity entity) {
        entity.motionX = super.motionX;
        entity.motionY = super.motionY;
        entity.motionZ = super.motionZ;
        MCH_EntityParachute parachute = new MCH_EntityParachute(super.worldObj, entity.posX, entity.posY, entity.posZ);
        parachute.rotationYaw = entity.rotationYaw;
        parachute.motionX = entity.motionX;
        parachute.motionY = entity.motionY;
        parachute.motionZ = entity.motionZ;
        parachute.fallDistance = entity.fallDistance;
        parachute.user = entity;
        parachute.setType(3);
        super.worldObj.spawnEntityInWorld(parachute);
    }

    public void rideRack() {
        if (super.ridingEntity == null) {
            AxisAlignedBB bb = this.getBoundingBox();
            List list = super.worldObj.getEntitiesWithinAABBExcludingEntity(this, bb.expand(60.0D, 60.0D, 60.0D));

            for (int i = 0; i < list.size(); ++i) {
                Entity entity = (Entity) list.get(i);
                if (entity instanceof MCH_EntityAircraft) {
                    MCH_EntityAircraft ac = (MCH_EntityAircraft) entity;
                    if (ac.getAcInfo() != null) {
                        for (int sid = 0; sid < ac.getSeatNum(); ++sid) {
                            MCH_SeatInfo seatInfo = ac.getSeatInfo(1 + sid);
                            if (seatInfo instanceof MCH_SeatRackInfo && ac.canRideSeatOrRack(1 + sid, entity)) {
                                MCH_SeatRackInfo info = (MCH_SeatRackInfo) seatInfo;
                                MCH_EntitySeat seat = ac.getSeat(sid);
                                if (seat != null && seat.riddenByEntity == null) {
                                    Vec3 v = ac.getTransformedPosition(info.getEntryPos());
                                    float r = info.range;
                                    if (super.posX >= v.xCoord - (double) r && super.posX <= v.xCoord + (double) r && super.posY >= v.yCoord - (double) r && super.posY <= v.yCoord + (double) r && super.posZ >= v.zCoord - (double) r && super.posZ <= v.zCoord + (double) r && this.canRideAircraft(ac, sid, info)) {
                                        W_WorldFunc.DEF_playSoundEffect(super.worldObj, super.posX, super.posY, super.posZ, "random.click", 1.0F, 1.0F);
                                        this.mountEntity(seat);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    public boolean canPutToRack() {
        for (int i = 0; i < this.getSeatNum(); ++i) {
            MCH_EntitySeat seat = this.getSeat(i);
            MCH_SeatInfo seatInfo = this.getSeatInfo(i + 1);
            if (seat != null && seat.riddenByEntity == null && seatInfo instanceof MCH_SeatRackInfo) {
                return true;
            }
        }

        return false;
    }

    public boolean canDownFromRack() {
        for (int i = 0; i < this.getSeatNum(); ++i) {
            MCH_EntitySeat seat = this.getSeat(i);
            MCH_SeatInfo seatInfo = this.getSeatInfo(i + 1);
            if (seat != null && seat.riddenByEntity != null && seatInfo instanceof MCH_SeatRackInfo) {
                return true;
            }
        }

        return false;
    }

    public void checkRideRack() {
        if (this.getCountOnUpdate() % 10 == 0) {
            this.canRideRackStatus = false;
            if (super.ridingEntity == null) {
                AxisAlignedBB bb = this.getBoundingBox();
                List list = super.worldObj.getEntitiesWithinAABBExcludingEntity(this, bb.expand(60.0D, 60.0D, 60.0D));

                for (Object o : list) {
                    Entity entity = (Entity) o;
                    if (entity instanceof MCH_EntityAircraft) {
                        MCH_EntityAircraft ac = (MCH_EntityAircraft) entity;
                        if (ac.getAcInfo() != null) {
                            for (int sid = 0; sid < ac.getSeatNum(); ++sid) {
                                MCH_SeatInfo seatInfo = ac.getSeatInfo(1 + sid);
                                if (seatInfo instanceof MCH_SeatRackInfo) {
                                    MCH_SeatRackInfo info = (MCH_SeatRackInfo) seatInfo;
                                    MCH_EntitySeat seat = ac.getSeat(sid);
                                    if (seat != null && seat.riddenByEntity == null) {
                                        Vec3 v = ac.getTransformedPosition(info.getEntryPos());
                                        float r = info.range;
                                        if (super.posX >= v.xCoord - (double) r && super.posX <= v.xCoord + (double) r && super.posY >= v.yCoord - (double) r && super.posY <= v.yCoord + (double) r && super.posZ >= v.zCoord - (double) r && super.posZ <= v.zCoord + (double) r && this.canRideAircraft(ac, sid, info)) {
                                            this.canRideRackStatus = true;
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    public boolean canRideRack() {
        return super.ridingEntity == null && this.canRideRackStatus;
    }

    public boolean canRideAircraft(MCH_EntityAircraft ac, int seatID, MCH_SeatRackInfo info) {
        if (this.getAcInfo() == null) {
            return false;
        } else if (ac.ridingEntity != null) {
            return false;
        } else if (super.ridingEntity != null) {
            return false;
        } else {
            boolean canRide = false;
            String[] arr$ = info.names;
            int len$ = arr$.length;

            int i$;
            for (i$ = 0; i$ < len$; ++i$) {
                String seat = arr$[i$];
                if (seat.equalsIgnoreCase(this.getAcInfo().name) || seat.equalsIgnoreCase(this.getAcInfo().getKindName())) {
                    canRide = true;
                    break;
                }
            }

            MCH_EntitySeat var12;
            if (!canRide) {
                Iterator var9 = this.getAcInfo().rideRacks.iterator();

                while (var9.hasNext()) {
                    MCH_AircraftInfo.RideRack var11 = (MCH_AircraftInfo.RideRack) var9.next();
                    i$ = ac.getAcInfo().getNumSeat() - 1 + (var11.rackID - 1);
                    if (i$ == seatID && var11.name.equalsIgnoreCase(ac.getAcInfo().name)) {
                        var12 = ac.getSeat(ac.getAcInfo().getNumSeat() - 1 + var11.rackID - 1);
                        if (var12 != null && var12.riddenByEntity == null) {
                            canRide = true;
                            break;
                        }
                    }
                }

                if (!canRide) {
                    return false;
                }
            }

            MCH_EntitySeat[] var10 = this.getSeats();
            len$ = var10.length;

            for (i$ = 0; i$ < len$; ++i$) {
                var12 = var10[i$];
                if (var12 != null && var12.riddenByEntity instanceof MCH_IEntityCanRideAircraft) {
                    return false;
                }
            }

            return true;
        }
    }

    public boolean isMountedEntity(Entity entity) {
        return entity == null ? false : this.isMountedEntity(W_Entity.getEntityId(entity));
    }

    public EntityPlayer getFirstMountPlayer() {
        if (this.getRiddenByEntity() instanceof EntityPlayer) {
            return (EntityPlayer) this.getRiddenByEntity();
        } else {
            MCH_EntitySeat[] arr$ = this.getSeats();
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                MCH_EntitySeat seat = arr$[i$];
                if (seat != null && seat.riddenByEntity instanceof EntityPlayer) {
                    return (EntityPlayer) seat.riddenByEntity;
                }
            }

            return null;
        }
    }

    public boolean isMountedSameTeamEntity(EntityLivingBase player) {
        if (player != null && player.getTeam() != null) {
            if (super.riddenByEntity instanceof EntityLivingBase && player.isOnSameTeam((EntityLivingBase) super.riddenByEntity)) {
                return true;
            } else {
                MCH_EntitySeat[] arr$ = this.getSeats();
                int len$ = arr$.length;

                for (int i$ = 0; i$ < len$; ++i$) {
                    MCH_EntitySeat seat = arr$[i$];
                    if (seat != null && seat.riddenByEntity instanceof EntityLivingBase && player.isOnSameTeam((EntityLivingBase) seat.riddenByEntity)) {
                        return true;
                    }
                }

                return false;
            }
        } else {
            return false;
        }
    }

    public boolean isMountedOtherTeamEntity(EntityLivingBase player) {
        if (player == null) {
            return false;
        } else {
            EntityLivingBase target = null;
            if (super.riddenByEntity instanceof EntityLivingBase) {
                target = (EntityLivingBase) super.riddenByEntity;
                if (player.getTeam() != null && target.getTeam() != null && !player.isOnSameTeam(target)) {
                    return true;
                }
            }

            MCH_EntitySeat[] arr$ = this.getSeats();
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                MCH_EntitySeat seat = arr$[i$];
                if (seat != null && seat.riddenByEntity instanceof EntityLivingBase) {
                    target = (EntityLivingBase) seat.riddenByEntity;
                    if (player.getTeam() != null && target.getTeam() != null && !player.isOnSameTeam(target)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    public boolean isMountedEntity(int entityId) {
        if (W_Entity.getEntityId(super.riddenByEntity) == entityId) {
            return true;
        } else {
            MCH_EntitySeat[] arr$ = this.getSeats();
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                MCH_EntitySeat seat = arr$[i$];
                if (seat != null && seat.riddenByEntity != null && W_Entity.getEntityId(seat.riddenByEntity) == entityId) {
                    return true;
                }
            }

            return false;
        }
    }

    public void onInteractFirst(EntityPlayer player) {
    }

    public boolean checkTeam(EntityPlayer player) {
        for (int i = 0; i < 1 + this.getSeatNum(); ++i) {
            Entity entity = this.getEntityBySeatId(i);
            if (entity instanceof EntityPlayer || entity instanceof MCH_EntityGunner) {
                EntityLivingBase riddenPlayer = (EntityLivingBase) entity;
                if (riddenPlayer.getTeam() != null && !riddenPlayer.isOnSameTeam(player)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isFriendlyPlayerAttackingGunnerPilotedVehicle(Entity attacker) {
        if (!(attacker instanceof EntityPlayer)) {
            return false;
        }
        Entity pilot = this.getEntityBySeatId(0);
        if (!(pilot instanceof MCH_EntityGunner)) {
            return false;
        }
        EntityPlayer player = (EntityPlayer) attacker;
        EntityLivingBase gunner = (EntityLivingBase) pilot;
        return player.getTeam() != null && gunner.getTeam() != null && player.isOnSameTeam(gunner);
    }

    public boolean interactFirst(EntityPlayer player, boolean ss) {
        this.switchSeat = ss;
        boolean ret = this.interactFirst(player);
        this.switchSeat = false;
        return ret;
    }

    public boolean interactFirst(EntityPlayer player) {
        if (isDestroyed())
            return false;
        if (getAcInfo() == null)
            return false;
        if (!checkTeam(player)) {
            return false;
        }
        ItemStack itemStack = player.getCurrentEquippedItem();
        if (itemStack != null && itemStack.getItem() instanceof mcheli.tool.MCH_ItemWrench) {
            if (!this.worldObj.isRemote && player.isSneaking()) {
                switchNextTextureName();
            }
            return false;
        }
        if (player.isSneaking()) {
            openInventory(player);
            return false;
        }
        if (!(getAcInfo()).canRide)
            return false;
        if (this.riddenByEntity == null && !isUAV()) {
            if (player.ridingEntity instanceof MCH_EntitySeat)
                return false;
            if (!canRideSeatOrRack(0, player)) {
                return false;
            }
            if (!this.switchSeat) {
                if (getAcInfo().haveCanopy() && isCanopyClose()) {
                    openCanopy();
                    return false;
                }
                if (getModeSwitchCooldown() > 0) {
                    return false;
                }
            }
            closeCanopy();
            this.riddenByEntity = null;
            this.lastRiddenByEntity = null;
            initRadar();
            if (!this.worldObj.isRemote) {
                player.mountEntity((Entity) this);
                if (!this.keepOnRideRotation) {
                    mountMobToSeats();
                }
            } else {
                updateClientSettings(0);
            }
            setCameraId(0);
            initPilotWeapon();
            this.lowPassPartialTicks.clear();
            onInteractFirst(player);
            return true;
        }
        return interactFirstSeat(player);
    }


    public boolean canRideSeatOrRack(int seatId, Entity entity) {
        if (this.getAcInfo() == null) {
            return false;
        } else {
            Iterator i$ = this.getAcInfo().exclusionSeatList.iterator();

            while (i$.hasNext()) {
                Integer[] a = (Integer[]) i$.next();
                if (Arrays.asList(a).contains(Integer.valueOf(seatId))) {
                    Integer[] arr$ = a;
                    int len$ = a.length;

                    for (int i$1 = 0; i$1 < len$; ++i$1) {
                        int id = arr$[i$1].intValue();
                        if (this.getEntityBySeatId(id) != null) {
                            return false;
                        }
                    }
                }
            }

            return true;
        }
    }

    public void updateClientSettings(int seatId) {
        MCH_Config var10001 = MCH_MOD.config;
        this.cs_dismountAll = MCH_Config.DismountAll.prmBool;
        var10001 = MCH_MOD.config;
        this.cs_heliAutoThrottleDown = MCH_Config.AutoThrottleDownHeli.prmBool;
        var10001 = MCH_MOD.config;
        this.cs_planeAutoThrottleDown = MCH_Config.AutoThrottleDownPlane.prmBool;
        var10001 = MCH_MOD.config;
        this.cs_tankAutoThrottleDown = MCH_Config.AutoThrottleDownTank.prmBool;
        this.camera.setShaderSupport(seatId, W_EntityRenderer.isShaderSupport());
        MCH_PacketNotifyClientSetting.send();
    }

    public boolean canLockEntity(Entity entity) {
        return !this.isMountedEntity(entity);
    }

    public void switchNextSeat(Entity entity) {
        if (entity != null) {
            if (this.seats != null && this.seats.length > 0) {
                if (this.isMountedEntity(entity)) {
                    boolean isFound = false;
                    int sid = 1;
                    MCH_EntitySeat[] arr$ = this.seats;
                    int len$ = arr$.length;

                    int i$;
                    MCH_EntitySeat seat;
                    for (i$ = 0; i$ < len$; ++i$) {
                        seat = arr$[i$];
                        if (seat != null) {
                            if (this.getSeatInfo(sid) instanceof MCH_SeatRackInfo) {
                                break;
                            }

                            if (W_Entity.isEqual(seat.riddenByEntity, entity)) {
                                isFound = true;
                            } else if (isFound && seat.riddenByEntity == null) {
                                entity.mountEntity(seat);
                                return;
                            }

                            ++sid;
                        }
                    }

                    sid = 1;
                    arr$ = this.seats;
                    len$ = arr$.length;

                    for (i$ = 0; i$ < len$; ++i$) {
                        seat = arr$[i$];
                        if (seat != null && seat.riddenByEntity == null) {
                            if (!(this.getSeatInfo(sid) instanceof MCH_SeatRackInfo)) {
                                entity.mountEntity(seat);
                                this.onMountPlayerSeat(seat, entity);
                                return;
                            }
                            break;
                        }

                        ++sid;
                    }

                }
            }
        }
    }

    public void switchPrevSeat(Entity entity) {
        if (entity != null) {
            if (this.seats != null && this.seats.length > 0) {
                if (this.isMountedEntity(entity)) {
                    boolean isFound = false;

                    int i;
                    MCH_EntitySeat seat;
                    for (i = this.seats.length - 1; i >= 0; --i) {
                        seat = this.seats[i];
                        if (seat != null) {
                            if (W_Entity.isEqual(seat.riddenByEntity, entity)) {
                                isFound = true;
                            } else if (isFound && seat.riddenByEntity == null) {
                                entity.mountEntity(seat);
                                return;
                            }
                        }
                    }

                    for (i = this.seats.length - 1; i >= 0; --i) {
                        seat = this.seats[i];
                        if (!(this.getSeatInfo(i + 1) instanceof MCH_SeatRackInfo) && seat != null && seat.riddenByEntity == null) {
                            entity.mountEntity(seat);
                            return;
                        }
                    }

                }
            }
        }
    }

    public Entity[] getParts() {
        return this.partEntities;
    }

    public float getSoundVolume() {
        return 1.0F;
    }

    public float getSoundPitch() {
        return 1.0F;
    }

    public abstract String getDefaultSoundName();

    public String getSoundName() {
        return this.getAcInfo() == null ? "" : (!this.getAcInfo().soundMove.isEmpty() ? this.getAcInfo().soundMove : this.getDefaultSoundName());
    }

    public boolean isSkipNormalRender() {
        return super.ridingEntity instanceof MCH_EntitySeat;
    }

    public boolean isRenderBullet(Entity entity, Entity rider) {
        return !this.isCameraView(rider) || !W_Entity.isEqual(this.getTVMissile(), entity) || !W_Entity.isEqual(this.getTVMissile().shootingEntity, rider);
    }

    public boolean isCameraView(Entity entity) {
        return this.getIsGunnerMode(entity) || this.isUAV();
    }

    public void updateCamera(double x, double y, double z) {
        if (super.worldObj.isRemote) {
            if (this.getTVMissile() != null) {
                this.camera.setPosition(this.TVmissile.posX, this.TVmissile.posY, this.TVmissile.posZ);
                this.camera.setCameraZoom(1.0F);
                this.TVmissile.isSpawnParticle = !this.isMissileCameraMode(this.TVmissile.shootingEntity);
            } else {
                this.setTVMissile((MCH_EntityTvMissile) null);
                MCH_AircraftInfo.CameraPosition cpi = this.getCameraPosInfo();
                Vec3 cp = cpi != null ? cpi.pos : Vec3.createVectorHelper(0.0D, 0.0D, 0.0D);
                Vec3 v = MCH_Lib.RotVec3(cp, -this.getRotYaw(), -this.getRotPitch(), -this.getRotRoll());
                this.camera.setPosition(x + v.xCoord, y + v.yCoord, z + v.zCoord);
            }

        }
    }

    public void updateCameraRotate(float yaw, float pitch) {
        this.camera.prevRotationYaw = this.camera.rotationYaw;
        this.camera.prevRotationPitch = this.camera.rotationPitch;
        this.camera.rotationYaw = yaw;
        this.camera.rotationPitch = pitch;
    }

    public void updatePartCameraRotate() {
        if (super.worldObj.isRemote) {
            Entity e = this.getEntityBySeatId(1);
            if (e == null) {
                e = this.getRiddenByEntity();
            }

            if (e != null) {
                this.camera.partRotationYaw = e.rotationYaw;
                float pitch = e.rotationPitch;
                this.camera.prevPartRotationYaw = this.camera.partRotationYaw;
                this.camera.prevPartRotationPitch = this.camera.partRotationPitch;
                this.camera.partRotationPitch = pitch;
            }
        }

    }

    public MCH_EntityTvMissile getTVMissile() {
        return this.TVmissile != null && !this.TVmissile.isDead ? this.TVmissile : null;
    }

    public void setTVMissile(MCH_EntityTvMissile entity) {
        this.TVmissile = entity;
    }

    public MCH_WeaponSet[] createWeapon(int seat_num) {
        this.currentWeaponID = new int[seat_num];

        Arrays.fill(this.currentWeaponID, -1);

        if (this.getAcInfo() != null && !this.getAcInfo().weaponSetList.isEmpty() && seat_num > 0) {
            MCH_WeaponSet[] var7 = new MCH_WeaponSet[this.getAcInfo().weaponSetList.size()];

            for (int i = 0; i < this.getAcInfo().weaponSetList.size(); ++i) {
                MCH_AircraftInfo.WeaponSet ws = (MCH_AircraftInfo.WeaponSet) this.getAcInfo().weaponSetList.get(i);
                MCH_WeaponBase[] wb = new MCH_WeaponBase[ws.weapons.size()];

                for (int defYaw = 0; defYaw < ws.weapons.size(); ++defYaw) {
                    wb[defYaw] = MCH_WeaponCreator.createWeapon(super.worldObj, ws.type,
                        ((MCH_AircraftInfo.Weapon) ws.weapons.get(defYaw)).pos,
                        ((MCH_AircraftInfo.Weapon) ws.weapons.get(defYaw)).yaw,
                        ((MCH_AircraftInfo.Weapon) ws.weapons.get(defYaw)).pitch,
                        this, ((MCH_AircraftInfo.Weapon) ws.weapons.get(defYaw)).turret);
                    wb[defYaw].aircraft = this;
                    wb[defYaw].muzzleFlashPosition = ((MCH_AircraftInfo.Weapon) ws.weapons.get(defYaw)).muzzleFlashPos;
                }

                if (wb.length > 0 && wb[0] != null) {
                    float var8 = ((MCH_AircraftInfo.Weapon) ws.weapons.get(0)).defaultYaw;
                    var7[i] = new MCH_WeaponSet(wb);
                    var7[i].prevRotationYaw = var8;
                    var7[i].rotationYaw = var8;
                    var7[i].defaultRotationYaw = var8;
                    // Initialize with full ammo state to avoid first-frame missile model hiding before sync.
                    int mag = var7[i].getAmmoNumMax();
                    int all = var7[i].getAllAmmoNum();
                    var7[i].setAmmoNum(Math.max(0, mag));
                    var7[i].setRestAllAmmoNum(Math.max(0, all - mag));
                }
            }

            return var7;
        } else {
            return new MCH_WeaponSet[]{this.dummyWeapon};
        }
    }

    public void switchWeapon(Entity entity, int id) {
        int sid = getSeatIdByEntity(entity);
        if (!isValidSeatID(sid))
            return;
        int beforeWeaponID = this.currentWeaponID[sid];
        if (getWeaponNum() <= 0 || this.currentWeaponID.length <= 0)
            return;
        if (id < 0)
            this.currentWeaponID[sid] = -1;
        if (id >= getWeaponNum())
            id = getWeaponNum() - 1;
        MCH_Lib.DbgLog(this.worldObj, "switchWeapon:" + W_Entity.getEntityId(entity) + " -> " + id, new Object[0]);
        getCurrentWeapon(entity).reload();
        this.currentWeaponID[sid] = id;
        MCH_WeaponSet ws = getCurrentWeapon(entity);
        ws.onSwitchWeapon(this.worldObj.isRemote, isInfinityAmmo(entity));
        //if(this.worldObj.isRemote) {
        W_WorldFunc.MOD_playSoundAtEntity(entity, ws.getInfo().weaponSwitchSound, 2.0F, 1.0F);
        //}
        if (!this.worldObj.isRemote)
            MCH_PacketNotifyWeaponID.send((Entity) this, sid, id, ws.getAmmoNum(), ws.getRestAllAmmoNum());
    }

    public void updateWeaponID(int sid, int id) {
        if (sid >= 0 && sid < this.currentWeaponID.length) {
            if (this.getWeaponNum() > 0 && this.currentWeaponID.length > 0) {
                if (id < 0) {
                    this.currentWeaponID[sid] = -1;
                }

                if (id >= this.getWeaponNum()) {
                    id = this.getWeaponNum() - 1;
                }

                MCH_Lib.DbgLog(super.worldObj, "switchWeapon:seatID=" + sid + ", WeaponID=" + id, new Object[0]);
                this.currentWeaponID[sid] = id;
            }
        }
    }

    public void updateWeaponRestAmmo(int id, int num) {
        if (id < this.getWeaponNum()) {
            this.getWeapon(id).setRestAllAmmoNum(num);
        }

    }

    public MCH_WeaponSet getWeaponByName(String name) {
        MCH_WeaponSet[] arr$ = this.weapons;
        int len$ = arr$.length;

        for (int i$ = 0; i$ < len$; ++i$) {
            MCH_WeaponSet ws = arr$[i$];
            if (ws.isEqual(name)) {
                return ws;
            }
        }

        return null;
    }

    public int getWeaponIdByName(String name) {
        int id = 0;
        MCH_WeaponSet[] arr$ = this.weapons;
        int len$ = arr$.length;

        for (int i$ = 0; i$ < len$; ++i$) {
            MCH_WeaponSet ws = arr$[i$];
            if (ws.isEqual(name)) {
                return id;
            }

            ++id;
        }

        return -1;
    }

    public void reloadAllWeapon() {
        for (int i = 0; i < this.getWeaponNum(); ++i) {
            this.getWeapon(i).reloadMag();
        }

    }

    public MCH_WeaponSet getFirstSeatWeapon() {
        return this.currentWeaponID != null && this.currentWeaponID.length > 0 && this.currentWeaponID[0] >= 0 ? this.getWeapon(this.currentWeaponID[0]) : this.getWeapon(0);
    }

    public void initCurrentWeapon(Entity entity) {
        int sid = getSeatIdByEntity(entity);
        MCH_Lib.DbgLog(this.worldObj, "initCurrentWeapon:" + W_Entity.getEntityId(entity) + ":%d", sid);
        if (sid >= 0 && sid < this.currentWeaponID.length) {
            this.currentWeaponID[sid] = -1;
            if (entity instanceof EntityPlayer || entity instanceof MCH_EntityGunner) {
                this.currentWeaponID[sid] = getNextWeaponID(entity, 1);
                switchWeapon(entity, getCurrentWeaponID(entity));
                if (this.worldObj.isRemote) {
                    MCH_PacketIndNotifyAmmoNum.send(this, -1);
                }
            }
        }
    }

    public void initPilotWeapon() {
        this.currentWeaponID[0] = -1;
    }

    public MCH_WeaponSet getCurrentWeapon(Entity entity) {
        return getWeapon(getCurrentWeaponID(entity));
    }

    protected MCH_WeaponSet getWeapon(int id) {
        if (id < 0 || this.weapons.length <= 0 || id >= this.weapons.length)
            return this.dummyWeapon;
        return this.weapons[id];
    }

    public int getWeaponIDBySeatID(int sid) {
        if (sid < 0 || sid >= this.currentWeaponID.length)
            return -1;
        return this.currentWeaponID[sid];
    }

    public double getLandInDistance(Entity user) {
        if (this.lastCalcLandInDistanceCount != (double) this.getCountOnUpdate() && this.getCountOnUpdate() % 2 == 0) {
            this.lastCalcLandInDistanceCount = this.getCountOnUpdate();
            MCH_WeaponParam prm = new MCH_WeaponParam();
            prm.setPosition(super.posX, super.posY, super.posZ);
            prm.entity = this;
            prm.user = user;
            prm.isInfinity = this.isInfinityAmmo(prm.user);
            if (prm.user != null) {
                MCH_WeaponSet currentWs = this.getCurrentWeapon(prm.user);
                if (currentWs != null) {
                    int sid = this.getSeatIdByEntity(prm.user);
                    if (this.getAcInfo().getWeaponSetById(sid) != null) {
                        prm.isTurret = ((MCH_AircraftInfo.Weapon) this.getAcInfo().getWeaponSetById(sid).weapons.get(0)).turret;
                    }

                    this.lastLandInDistance = currentWs.getLandInDistance(prm);
                }
            }
        }

        return this.lastLandInDistance;
    }

    public Vec3 getPredictedImpactPoint(Entity user) {
        if (this.lastCalcPredictedImpactPointCount != (double)this.getCountOnUpdate()) {
            this.lastCalcPredictedImpactPointCount = this.getCountOnUpdate();
            this.lastPredictedImpactPoint = null;
            MCH_WeaponParam prm = new MCH_WeaponParam();
            prm.setPosition(super.posX, super.posY, super.posZ);
            prm.entity = this;
            prm.user = user;
            if (prm.user != null) {
                MCH_WeaponSet currentWs = this.getCurrentWeapon(prm.user);
                if (currentWs != null && currentWs.getInfo() != null && currentWs.getInfo().ccip && isCCIPSupportedType(currentWs.getInfo().type)) {
                    this.lastPredictedImpactPoint = currentWs.getPredictedImpactPoint(prm);
                }
            }
        }
        return this.lastPredictedImpactPoint;
    }

    private boolean isCCIPSupportedType(String type) {
        if (type == null) {
            return false;
        }
        return type.equalsIgnoreCase("rocket")
            || type.equalsIgnoreCase("atmissile")
            || type.equalsIgnoreCase("tvmissile");
    }

    public boolean useCurrentWeapon(Entity user) {
        MCH_WeaponParam prm = new MCH_WeaponParam();
        prm.setPosition(super.posX, super.posY, super.posZ);
        prm.entity = this;
        prm.user = user;
        return this.useCurrentWeapon(prm);
    }

    public void currentWeaponLock(Entity user) {
        if (user == null) {
            return;
        }
        MCH_WeaponSet currentWs = this.getCurrentWeapon(user);
        if (currentWs != null) {
            MCH_WeaponParam prm = new MCH_WeaponParam();
            prm.setPosition(super.posX, super.posY, super.posZ);
            prm.entity = this;
            prm.user = user;
            currentWs.lock(prm);
        }
    }

    public void currentWeaponUnlock(Entity user) {
        if (user == null) {
            return;
        }
        MCH_WeaponSet currentWs = this.getCurrentWeapon(user);
        if (currentWs != null) {
            MCH_WeaponParam prm = new MCH_WeaponParam();
            prm.setPosition(super.posX, super.posY, super.posZ);
            prm.entity = this;
            prm.user = user;
            currentWs.onUnlock(prm);
        }
    }

    public boolean useCurrentWeapon(MCH_WeaponParam prm) {
        prm.isInfinity = this.isInfinityAmmo(prm.user);
        if (prm.user != null) {
            MCH_WeaponSet currentWs = this.getCurrentWeapon(prm.user);
            if (currentWs != null && currentWs.canUse()) {
                int sid = this.getSeatIdByEntity(prm.user);
                if (this.getAcInfo().getWeaponSetById(sid) != null) {
                    prm.isTurret = ((MCH_AircraftInfo.Weapon) this.getAcInfo().getWeaponSetById(sid).weapons.get(0)).turret;
                }

                int lastUsedIndex = currentWs.getCurrentWeaponIndex();
                if (currentWs.use(prm)) {
                    MCH_WeaponSet[] shift = this.weapons;
                    int arr$ = shift.length;

                    int len$;
                    for (len$ = 0; len$ < arr$; ++len$) {
                        MCH_WeaponSet i$ = shift[len$];
                        if (i$ != currentWs && !i$.getInfo().group.isEmpty() && i$.getInfo().group.equals(currentWs.getInfo().group)) {
                            i$.waitAndReloadByOther(prm.reload);
                        }
                    }

                    if (!super.worldObj.isRemote) {
                        int var10 = 0;
                        MCH_WeaponSet[] var11 = this.weapons;
                        len$ = var11.length;

                        for (int var12 = 0; var12 < len$; ++var12) {
                            MCH_WeaponSet ws = var11[var12];
                            if (ws == currentWs) {
                                break;
                            }

                            var10 += ws.getWeaponNum();
                        }

                        var10 += lastUsedIndex;
                        this.useWeaponStat |= var10 < 32 ? 1 << var10 : 0;
                        // Sync ammo/reload visuals for nearby clients when AI gunner fires.
                        if (prm.user instanceof MCH_EntityGunner) {
                            int wid = this.getCurrentWeaponID(prm.user);
                            if (wid >= 0) {
                                MCH_PacketNotifyWeaponID.send(this, sid, wid, currentWs.getAmmoNum(), currentWs.getRestAllAmmoNum());
                            }
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    public void switchCurrentWeaponMode(Entity entity) {
        this.getCurrentWeapon(entity).switchMode();
    }

    public int getWeaponNum() {
        return this.weapons.length;
    }

    public int getCurrentWeaponID(Entity entity) {
        if (!(entity instanceof EntityPlayer) && !(entity instanceof MCH_EntityGunner)) {
            return -1;
        }
        int id = getSeatIdByEntity(entity);
        return (id >= 0 && id < this.currentWeaponID.length) ? this.currentWeaponID[id] : -1;
    }

    public int getNextWeaponID(Entity entity, int step) {
        if (getAcInfo() == null) {
            return -1;
        }
        int sid = getSeatIdByEntity(entity);
        if (sid < 0) {
            return -1;
        }
        int id = getCurrentWeaponID(entity);
        int i;
        for (i = 0; i < getWeaponNum(); i++) {
            if (step >= 0) {
                id = (id + 1) % getWeaponNum();
            } else {
                id = (id > 0) ? (id - 1) : (getWeaponNum() - 1);
            }
            MCH_AircraftInfo.Weapon w = getAcInfo().getWeaponById(id);
            if (w != null) {
                MCH_WeaponInfo wi = getWeaponInfoById(id);
                int wpsid = getWeaponSeatID(wi, w);
                if (wpsid < getSeatNum() + 1 + 1 && (wpsid == sid || (sid == 0 && w.canUsePilot && !(getEntityBySeatId(wpsid) instanceof EntityPlayer) && !(getEntityBySeatId(wpsid) instanceof MCH_EntityGunner)))) {
                    break;
                }
            }
        }
        if (i >= getWeaponNum()) {
            return -1;
        }
        MCH_Lib.DbgLog(this.worldObj, "getNextWeaponID:%d:->%d", new Object[]{Integer.valueOf(W_Entity.getEntityId(entity)), Integer.valueOf(id)});
        return id;
    }

    public int getWeaponSeatID(MCH_WeaponInfo wi, MCH_AircraftInfo.Weapon w) {
        return wi != null && (wi.target & 195) == 0 && wi.type.isEmpty() && (MCH_MOD.proxy.isSinglePlayer() || MCH_Config.TestMode.prmBool) ? 1000 : w.seatID;
    }

    public boolean isMissileCameraMode(Entity entity) {
        return this.getTVMissile() != null && this.isCameraView(entity);
    }

    public boolean isPilotReloading() {
        return this.getCommonStatus(2) || this.supplyAmmoWait > 0;
    }

    public int getUsedWeaponStat() {
        if (this.getAcInfo() == null) {
            return 0;
        } else if (this.getAcInfo().getWeaponNum() <= 0) {
            return 0;
        } else {
            int stat = 0;
            int i = 0;
            MCH_WeaponSet[] arr$ = this.weapons;
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                MCH_WeaponSet w = arr$[i$];
                if (i >= 32) {
                    break;
                }

                for (int wi = 0; wi < w.getWeaponNum() && i < 32; ++wi) {
                    stat |= w.isUsed(wi) ? 1 << i : 0;
                    ++i;
                }
            }

            return stat;
        }
    }

    public boolean isWeaponNotCooldown(MCH_WeaponSet checkWs, int index) {
        if (this.getAcInfo() == null) {
            return false;
        } else if (this.getAcInfo().getWeaponNum() <= 0) {
            return false;
        } else {
            int shift = 0;
            MCH_WeaponSet[] arr$ = this.weapons;
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                MCH_WeaponSet ws = arr$[i$];
                if (ws == checkWs) {
                    break;
                }

                shift += ws.getWeaponNum();
            }

            shift += index;
            return shift < 32 ? (this.useWeaponStat & 1 << shift) != 0 : false;
        }
    }

    public void updateWeapons() {
        if (this.getAcInfo() != null) {
            if (!this.worldObj.isRemote) {
                this.syncERAStateWatcher(false);
            } else {
                this.updateERAStateFromWatcher();
            }
            if (this.getAcInfo().getWeaponNum() > 0) {
                int prevUseWeaponStat = this.useWeaponStat;
                if (!this.worldObj.isRemote) {
                    this.useWeaponStat |= this.getUsedWeaponStat();
                    this.getDataWatcher().updateObject(24, this.useWeaponStat);
                    this.useWeaponStat = 0;
                } else {
                    this.useWeaponStat = this.getDataWatcher().getWatchableObjectInt(24);
                }

                float yaw = MathHelper.wrapAngleTo180_float(this.getRotYaw());
                float pitch = MathHelper.wrapAngleTo180_float(this.getRotPitch());
                int id = 0;

                for (int wid = 0; wid < this.weapons.length; ++wid) {
                    MCH_WeaponSet w = this.weapons[wid];
                    boolean isLongDelay = false;
                    if (w.getFirstWeapon() != null) {
                        isLongDelay = w.isLongDelayWeapon();
                    }

                    boolean isSelected = false;

                    for (int swid : this.currentWeaponID) {
                        if (swid == wid) {
                            isSelected = true;
                            break;
                        }
                    }

                    boolean isWpnUsed = false;

                    for (int index = 0; index < w.getWeaponNum(); ++index) {
                        boolean isPrevUsed = id < 32 && (prevUseWeaponStat & 1 << id) != 0;
                        boolean isUsed = id < 32 && (this.useWeaponStat & 1 << id) != 0;
                        if (isLongDelay && isPrevUsed && isUsed) {
                            isUsed = false;
                        }

                        isWpnUsed |= isUsed;
                        if (!isPrevUsed && isUsed) {
                            float recoil = w.getInfo().recoil;
                            if (recoil > 0.0F) {
                                this.recoilCount = 30;
                                this.recoilValue = recoil;
                                this.recoilYaw = w.rotationYaw;
                            }
                        }

                        if (this.worldObj.isRemote && isUsed) {
                            Vec3 wrv = MCH_Lib.RotVec3((double) 0.0F, (double) 0.0F, (double) -1.0F, -w.rotationYaw - yaw, -w.rotationPitch);
                            Vec3 spv = w.getCurrentWeapon().getShotPos(this);
                            this.spawnParticleMuzzleFlash(this.worldObj, w.getInfo(),
                                this.posX + spv.xCoord,
                                this.posY + spv.yCoord,
                                this.posZ + spv.zCoord,
                                wrv);
                        }

                        w.updateWeapon(this, isUsed, index);
                        ++id;
                    }

                    w.update(this, isSelected, isWpnUsed);
                    MCH_AircraftInfo.Weapon wi = this.getAcInfo().getWeaponById(wid);
                    if (wi != null && !this.isDestroyed()) {
                        Entity entity = this.getEntityBySeatId(this.getWeaponSeatID(this.getWeaponInfoById(wid), wi));
                        if (wi.canUsePilot && !(entity instanceof EntityPlayer) && !(entity instanceof MCH_EntityGunner)) {
                            entity = this.getEntityBySeatId(0);
                        }

                        if (!(entity instanceof EntityPlayer) && !(entity instanceof MCH_EntityGunner)) {
                            w.rotationTurretYaw = this.getLastRiderYaw() - this.getRotYaw();
                            if (this.getTowedChainEntity() != null || this.ridingEntity != null) {
                                w.rotationYaw = 0.0F;
                            }
                        } else {
                            if ((int) wi.minYaw != 0 || (int) wi.maxYaw != 0) {
                                float ty = wi.turret ? MathHelper.wrapAngleTo180_float(this.getLastRiderYaw()) - yaw : 0.0F;
                                float ey = MathHelper.wrapAngleTo180_float(entity.rotationYaw - yaw - wi.defaultYaw - ty);
                                if (Math.abs((int) wi.minYaw) < 360 && Math.abs((int) wi.maxYaw) < 360) {
                                    float targetYaw = MCH_Lib.RNG(ey, wi.minYaw, wi.maxYaw);
                                    float wy = w.rotationYaw - wi.defaultYaw - ty;
                                    if (targetYaw < wy) {
                                        if (wy - targetYaw > 15.0F) {
                                            wy -= 15.0F;
                                        } else {
                                            wy = targetYaw;
                                        }
                                    } else if (targetYaw > wy) {
                                        if (targetYaw - wy > 15.0F) {
                                            wy += 15.0F;
                                        } else {
                                            wy = targetYaw;
                                        }
                                    }

                                    w.rotationYaw = wy + wi.defaultYaw + ty;
                                } else {
                                    w.rotationYaw = ey + ty;
                                }
                            }

                            float ep = MathHelper.wrapAngleTo180_float(entity.rotationPitch - pitch);
                            w.rotationPitch = MCH_Lib.RNG(ep, wi.minPitch, wi.maxPitch);
                            w.rotationTurretYaw = 0.0F;
                        }
                    }
                }

                this.updateWeaponBay();
                if (this.hitStatus > 0) {
                    --this.hitStatus;
                }

            }
        }
    }

    public void updateWeaponsRotation() {
        if (this.getAcInfo() != null) {
            if (this.getAcInfo().getWeaponNum() > 0) {
                if (!this.isDestroyed()) {
                    float yaw = MathHelper.wrapAngleTo180_float(this.getRotYaw());
                    float pitch = MathHelper.wrapAngleTo180_float(this.getRotPitch());

                    for (int wid = 0; wid < this.weapons.length; ++wid) {
                        MCH_WeaponSet w = this.weapons[wid];
                        MCH_AircraftInfo.Weapon wi = this.getAcInfo().getWeaponById(wid);
                        if (wi != null) {
                            Entity entity = this.getEntityBySeatId(this.getWeaponSeatID(this.getWeaponInfoById(wid), wi));
                            if (wi.canUsePilot && !(entity instanceof EntityPlayer) && !(entity instanceof MCH_EntityGunner)) {
                                entity = this.getEntityBySeatId(0);
                            }

                            if (!(entity instanceof EntityPlayer) && !(entity instanceof MCH_EntityGunner)) {
                                w.rotationTurretYaw = this.getLastRiderYaw() - this.getRotYaw();
                            } else {
                                if ((int) wi.minYaw != 0 || (int) wi.maxYaw != 0) {
                                    float ty = wi.turret ? MathHelper.wrapAngleTo180_float(this.getLastRiderYaw()) - yaw : 0.0F;
                                    float ey = MathHelper.wrapAngleTo180_float(entity.rotationYaw - yaw - wi.defaultYaw - ty);
                                    if (Math.abs((int) wi.minYaw) < 360 && Math.abs((int) wi.maxYaw) < 360) {
                                        float targetYaw = MCH_Lib.RNG(ey, wi.minYaw, wi.maxYaw);
                                        float wy = w.rotationYaw - wi.defaultYaw - ty;
                                        if (targetYaw < wy) {
                                            if (wy - targetYaw > 15.0F) {
                                                wy -= 15.0F;
                                            } else {
                                                wy = targetYaw;
                                            }
                                        } else if (targetYaw > wy) {
                                            if (targetYaw - wy > 15.0F) {
                                                wy += 15.0F;
                                            } else {
                                                wy = targetYaw;
                                            }
                                        }

                                        w.rotationYaw = wy + wi.defaultYaw + ty;
                                    } else {
                                        w.rotationYaw = ey + ty;
                                    }
                                }

                                float ep = MathHelper.wrapAngleTo180_float(entity.rotationPitch - pitch);
                                w.rotationPitch = MCH_Lib.RNG(ep, wi.minPitch, wi.maxPitch);
                                w.rotationTurretYaw = 0.0F;
                            }
                        }

                        w.prevRotationYaw = w.rotationYaw;
                    }

                }
            }
        }
    }


    public void spawnParticleMuzzleFlash(World w, MCH_WeaponInfo wi, double px, double py, double pz, Vec3 wrv) {
        Iterator i$;
        MCH_WeaponInfo.MuzzleFlash mf;
        if (wi.listMuzzleFlashSmoke != null) {
            i$ = wi.listMuzzleFlashSmoke.iterator();

            while (i$.hasNext()) {
                mf = (MCH_WeaponInfo.MuzzleFlash) i$.next();
                double color = px + -wrv.xCoord * (double) mf.dist;
                double y = py + -wrv.yCoord * (double) mf.dist;
                double z = pz + -wrv.zCoord * (double) mf.dist;
                MCH_ParticleParam p = new MCH_ParticleParam(w, "smoke", px, py, pz);
                p.size = mf.size;

                for (int i = 0; i < mf.num; ++i) {
                    p.a = mf.a * 0.9F + w.rand.nextFloat() * 0.1F;
                    float color1 = w.rand.nextFloat() * 0.1F;
                    p.r = color1 + mf.r * 0.9F;
                    p.g = color1 + mf.g * 0.9F;
                    p.b = color1 + mf.b * 0.9F;
                    p.age = (int) ((double) mf.age + 0.1D * (double) mf.age * (double) w.rand.nextFloat());
                    p.posX = color + (w.rand.nextDouble() - 0.5D) * (double) mf.range;
                    p.posY = y + (w.rand.nextDouble() - 0.5D) * (double) mf.range;
                    p.posZ = z + (w.rand.nextDouble() - 0.5D) * (double) mf.range;
                    p.motionX = w.rand.nextDouble() * (p.posX < color ? -0.2D : 0.2D);
                    p.motionY = w.rand.nextDouble() * (p.posY < y ? -0.03D : 0.03D);
                    p.motionZ = w.rand.nextDouble() * (p.posZ < z ? -0.2D : 0.2D);
                    MCH_ParticlesUtil.spawnParticle(p);
                }
            }
        }

        if (wi.listMuzzleFlash != null) {
            i$ = wi.listMuzzleFlash.iterator();

            while (i$.hasNext()) {
                mf = (MCH_WeaponInfo.MuzzleFlash) i$.next();
                float var21 = super.rand.nextFloat() * 0.1F + 0.9F;
                MCH_ParticlesUtil.spawnParticleExplode(super.worldObj, px + -wrv.xCoord * (double) mf.dist, py + -wrv.yCoord * (double) mf.dist, pz + -wrv.zCoord * (double) mf.dist, mf.size, var21 * mf.r, var21 * mf.g, var21 * mf.b, mf.a, mf.age + w.rand.nextInt(3));
            }
        }

    }

    private void updateWeaponBay() {
        for (int i = 0; i < this.weaponBays.length; ++i) {
            MCH_EntityAircraft.WeaponBay wb = this.weaponBays[i];
            MCH_AircraftInfo.WeaponBay info = (MCH_AircraftInfo.WeaponBay) this.getAcInfo().partWeaponBay.get(i);
            boolean isSelected = false;
            Integer[] arr$ = info.weaponIds;
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                int wid = arr$[i$].intValue();

                for (int sid = 0; sid < this.currentWeaponID.length; ++sid) {
                    if (wid == this.currentWeaponID[sid] && this.getEntityBySeatId(sid) != null) {
                        isSelected = true;
                    }
                }
            }

            wb.prevRot = wb.rot;
            if (isSelected) {
                if (wb.rot < 90.0F) {
                    wb.rot += 3.0F;
                }

                if (wb.rot >= 90.0F) {
                    wb.rot = 90.0F;
                }
            } else {
                if (wb.rot > 0.0F) {
                    wb.rot -= 3.0F;
                }

                if (wb.rot <= 0.0F) {
                    wb.rot = 0.0F;
                }
            }
        }

    }

    public int getHitStatus() {
        return this.hitStatus;
    }

    public int getMaxHitStatus() {
        return 15;
    }

    public void hitBullet() {
        this.hitStatus = this.getMaxHitStatus();
    }

    public void initRotationYaw(float yaw) {
        super.rotationYaw = yaw;
        super.prevRotationYaw = yaw;
        this.lastRiderYaw = yaw;
        this.lastSearchLightYaw = yaw;
        if (this.weapons != null) {
            for (MCH_WeaponSet w : this.weapons) {
                w.rotationYaw = w.defaultRotationYaw;
                w.rotationPitch = 0.0F;
            }
        }
    }

    public MCH_AircraftInfo getAcInfo() {
        return this.acInfo;
    }

    private String buildERAStateString() {
        StringBuilder sb = new StringBuilder();
        for (MCH_BoundingBox bb : this.extraBoundingBox) {
            if (bb.isERA) {
                sb.append(bb.eraActive ? '1' : '0');
            }
        }
        return sb.toString();
    }

    private void applyERAStateString(String state) {
        if (state == null) {
            return;
        }
        int eraIndex = 0;
        for (MCH_BoundingBox bb : this.extraBoundingBox) {
            if (!bb.isERA) {
                continue;
            }
            if (eraIndex < state.length()) {
                bb.eraActive = state.charAt(eraIndex) != '0';
            }
            eraIndex++;
        }
    }

    private void syncERAStateWatcher(boolean force) {
        if (this.worldObj.isRemote) {
            return;
        }
        String state = this.buildERAStateString();
        if (force || !state.equals(this.eraStateForSync)) {
            this.eraStateForSync = state;
            this.getDataWatcher().updateObject(DATAWT_ID_ERA_STATE, state);
        }
    }

    private void updateERAStateFromWatcher() {
        String watcherState = this.getDataWatcher().getWatchableObjectString(DATAWT_ID_ERA_STATE);
        if (watcherState == null) {
            watcherState = "";
        }
        if (!watcherState.equals(this.eraStateForSync)) {
            this.eraStateForSync = watcherState;
            this.applyERAStateString(watcherState);
        }
    }

    private void recoverERABoundingBoxesByMaintenance() {
        List<MCH_BoundingBox> destroyedERA = new ArrayList<MCH_BoundingBox>();
        for (MCH_BoundingBox bb : this.extraBoundingBox) {
            if (bb.isERA && !bb.eraActive) {
                destroyedERA.add(bb);
            }
        }
        if (destroyedERA.isEmpty()) {
            return;
        }
        int recoverCount = (destroyedERA.size() + 3) / 4;
        if (recoverCount < 1) {
            recoverCount = 1;
        }
        Collections.shuffle(destroyedERA, this.rand);
        int maxRecover = Math.min(recoverCount, destroyedERA.size());
        for (int i = 0; i < maxRecover; i++) {
            destroyedERA.get(i).eraActive = true;
        }
        this.syncERAStateWatcher(false);
    }

    public void setAcInfo(MCH_AircraftInfo info) {
        this.acInfo = info;
        if (info != null) {
            this.partHatch = this.createHatch();
            this.partCanopy = this.createCanopy();
            this.partLandingGear = this.createLandingGear();
            this.weaponBays = this.createWeaponBays();
            this.rotPartRotation = new float[info.partRotPart.size()];
            this.prevRotPartRotation = new float[info.partRotPart.size()];
            this.extraBoundingBox = this.createExtraBoundingBox();
            this.partEntities = this.createParts();
            super.stepHeight = info.stepHeight;
        }
    }

    public abstract Item getItem();

    public MCH_BoundingBox[] createExtraBoundingBox() {
        MCH_AircraftInfo acInfo = this.getAcInfo();
        if (acInfo == null || acInfo.extraBoundingBox == null) {
            return new MCH_BoundingBox[0];
        }
        List<MCH_BoundingBox> boundingBoxes = acInfo.extraBoundingBox;
        MCH_BoundingBox[] ar = new MCH_BoundingBox[boundingBoxes.size()];
        int i = 0;
        for (MCH_BoundingBox bb : boundingBoxes) {
            ar[i++] = bb.copy();
        }
        return ar;
    }

    public Entity[] createParts() {
        Entity[] list = new Entity[]{this.partEntities[0]};
        return list;
    }

    public void updateUAV() {
        if (this.isUAV()) {
            if (this.worldObj.isRemote) {
                int eid = this.getDataWatcher().getWatchableObjectInt(22);
                if (eid > 0) {
                    if (this.uavStation == null) {
                        Entity uavEntity = this.worldObj.getEntityByID(eid);
                        if (uavEntity instanceof MCH_EntityUavStation) {
                            this.uavStation = (MCH_EntityUavStation) uavEntity;
                            this.uavStation.setControlAircract(this);
                        }
                    }
                } else if (this.uavStation != null) {
                    this.uavStation.setControlAircract(null);
                    this.uavStation = null;
                }
            } else if (this.uavStation != null) {
                double udx = this.posX - this.uavStation.posX;
                double udz = this.posZ - this.uavStation.posZ;
                if (udx * udx + udz * udz > 1024 * 1024) {
                    this.uavStation.setControlAircract(null);
                    this.setUavStation(null);
                    this.attackEntityFrom(DamageSource.outOfWorld, this.getMaxHP() + 10);
                }
            }
            if (this.uavStation != null && this.uavStation.isDead) {
                this.uavStation = null;
            }
        }
    }


    public void switchGunnerMode(boolean mode) {
        boolean debug_bk_mode = this.isGunnerMode;
        Entity pilot = this.getEntityBySeatId(0);
        if (!mode || this.canSwitchGunnerMode()) {
            if (this.isGunnerMode && !mode) {
                this.setCurrentThrottle(this.beforeHoverThrottle);
                this.isGunnerMode = false;
                this.camera.setCameraZoom(1.0F);
                this.getCurrentWeapon(pilot).onSwitchWeapon(super.worldObj.isRemote, this.isInfinityAmmo(pilot));
            } else if (!this.isGunnerMode && mode) {
                this.beforeHoverThrottle = this.getCurrentThrottle();
                this.isGunnerMode = true;
                this.camera.setCameraZoom(1.0F);
                this.getCurrentWeapon(pilot).onSwitchWeapon(super.worldObj.isRemote, this.isInfinityAmmo(pilot));
            }
        }

        MCH_Lib.DbgLog(super.worldObj, "switchGunnerMode %s->%s", new Object[]{debug_bk_mode ? "ON" : "OFF", mode ? "ON" : "OFF"});
    }

    public boolean canSwitchGunnerMode() {
        return this.getAcInfo() != null && this.getAcInfo().isEnableGunnerMode ? (!this.isCanopyClose() ? false : (!this.getAcInfo().isEnableConcurrentGunnerMode && this.getEntityBySeatId(1) instanceof EntityPlayer ? false : !this.isHoveringMode())) : false;
    }

    public boolean canSwitchGunnerModeOtherSeat(EntityPlayer player) {
        int sid = this.getSeatIdByEntity(player);
        if (sid > 0) {
            MCH_SeatInfo info = this.getSeatInfo(sid);
            if (info != null) {
                return info.gunner && info.switchgunner;
            }
        }

        return false;
    }

    public void switchGunnerModeOtherSeat(EntityPlayer player) {
        this.isGunnerModeOtherSeat = !this.isGunnerModeOtherSeat;
    }

    @SideOnly(Side.CLIENT)
    public void resetAirburstDistance(EntityPlayer player, MCH_WeaponBase wb) {
        //MCH_AircraftInfo.CameraPosition cameraPosition = getCameraPosInfo();
        int dist;
        double posX = RenderManager.renderPosX;
        double posY = RenderManager.renderPosY;
        double posZ = RenderManager.renderPosZ;
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;
        double maxDist = 3000.0;
        double targetX = -MathHelper.sin(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI);
        double targetZ = MathHelper.cos(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI);
        double targetY = -MathHelper.sin(pitch / 180.0F * (float) Math.PI);
        double distNorm = MathHelper.sqrt_double(targetX * targetX + targetY * targetY + targetZ * targetZ);
        // 归一化并拉伸到最大检测距离
        targetX = targetX * maxDist / distNorm;
        targetY = targetY * maxDist / distNorm;
        targetZ = targetZ * maxDist / distNorm;

        // 按照100格为步长逐段进行射线检测
        double segmentLength = 100.0;
        int numSegments = (int) (maxDist / segmentLength);
        Vec3 src = W_WorldFunc.getWorldVec3(this.worldObj, posX, posY, posZ);
        MovingObjectPosition hitResult = null;
        for (int i = 1; i <= numSegments; i++) {
            Vec3 dst = W_WorldFunc.getWorldVec3(this.worldObj,
                posX + targetX * i / numSegments,
                posY + targetY * i / numSegments,
                posZ + targetZ * i / numSegments);
            List<MovingObjectPosition> hits = MCH_RayTracer.rayTraceAllBlocks(this.worldObj, src, dst, false, true, true);
            if (hits != null && !hits.isEmpty()) {
                hitResult = hits.get(0);
                break;
            }
            // 未命中则将终点作为下一次的起点
            src = dst;
        }
        // 如果没有命中方块，则默认将远端目标作为命中点
        if (hitResult == null) {
            hitResult = new MovingObjectPosition(null,
                src.addVector(targetX, targetY, targetZ));
        }

        // 计算摄像机到命中点的距离并取整
        Vec3 hitVec = hitResult.hitVec;
        dist = (int) Math.sqrt(
            (hitVec.xCoord - posX) * (hitVec.xCoord - posX) +
                (hitVec.yCoord - posY) * (hitVec.yCoord - posY) +
                (hitVec.zCoord - posZ) * (hitVec.zCoord - posZ)
        );

        wb.setAirburstDist(dist);
        MCH_MOD.getPacketHandler().sendToServer(new PacketAirburstDistReset(getEntityId(), dist));
    }

    public boolean isHoveringMode() {
        return this.isHoveringMode;
    }

    public void switchHoveringMode(boolean mode) {
        this.stopRepelling();
        if (this.canSwitchHoveringMode() && this.isHoveringMode() != mode) {
            if (mode) {
                this.beforeHoverThrottle = this.getCurrentThrottle();
            } else {
                this.setCurrentThrottle(this.beforeHoverThrottle);
            }

            this.isHoveringMode = mode;
            if (super.riddenByEntity != null) {
                super.riddenByEntity.rotationPitch = 0.0F;
                super.riddenByEntity.prevRotationPitch = 0.0F;
            }
        }

    }

    public boolean canSwitchHoveringMode() {
        return this.getAcInfo() == null ? false : !this.isGunnerMode;
    }

    public boolean isHovering() {
        return this.isGunnerMode || this.isHoveringMode();
    }

    public boolean getIsGunnerMode(Entity entity) {
        if (this.getAcInfo() == null) {
            return false;
        } else {
            int id = this.getSeatIdByEntity(entity);
            if (id < 0) {
                return false;
            } else if (id == 0 && this.getAcInfo().isEnableGunnerMode) {
                return this.isGunnerMode;
            } else {
                MCH_SeatInfo[] st = this.getSeatsInfo();
                return id < st.length && st[id].gunner ? (super.worldObj.isRemote && st[id].switchgunner ? this.isGunnerModeOtherSeat : true) : false;
            }
        }
    }

    public boolean isPilot(Entity player) {
        return W_Entity.isEqual(this.getRiddenByEntity(), player);
    }

    public boolean canSwitchFreeLook() {
        return true;
    }

    public boolean isFreeLookMode() {
        return this.getCommonStatus(1) || this.isRepelling();
    }

    public void switchFreeLookMode(boolean b) {
        if (MCH_ServerSettings.enableDebugFreeLook) {
            Entity rider = this.getRiddenByEntity();
            String riderName = rider instanceof EntityPlayer ? ((EntityPlayer)rider).getCommandSenderName() : String.valueOf(rider);
            mcheli.MCH_FreeLookDebug.trace(super.worldObj, rider, "[Switch][Server] acId=%d rider=%s %s->%s",
                this.getEntityId(), riderName, this.getCommonStatus(1), b);
        }
        this.setCommonStatus(1, b);
    }

    public void switchFreeLookModeClient(boolean b) {
        if (MCH_ServerSettings.enableDebugFreeLook) {
            Entity rider = this.getRiddenByEntity();
            String riderName = rider instanceof EntityPlayer ? ((EntityPlayer)rider).getCommandSenderName() : String.valueOf(rider);
            mcheli.MCH_FreeLookDebug.trace(super.worldObj, rider, "[Switch][ClientInit] acId=%d rider=%s %s->%s",
                this.getEntityId(), riderName, this.getCommonStatus(1), b);
        }
        this.setCommonStatus(1, b, true);
    }

    public boolean canSwitchGunnerFreeLook(EntityPlayer player) {
        MCH_SeatInfo seatInfo = this.getSeatInfo(player);
        return seatInfo != null && seatInfo.fixRot && this.getIsGunnerMode(player);
    }

    public boolean isGunnerLookMode(EntityPlayer player) {
        return this.isPilot(player) ? false : this.isGunnerFreeLookMode;
    }

    public void switchGunnerFreeLookMode(boolean b) {
        this.isGunnerFreeLookMode = b;
    }

    public void switchGunnerFreeLookMode() {
        this.switchGunnerFreeLookMode(!this.isGunnerFreeLookMode);
    }

    public void updateParts(int stat) {
        if (!this.isDestroyed()) {
            MCH_Parts[] parts = new MCH_Parts[]{this.partHatch, this.partCanopy, this.partLandingGear};
            MCH_Parts[] blockId = parts;
            int unfold = parts.length;

            for (int i$ = 0; i$ < unfold; ++i$) {
                MCH_Parts p = blockId[i$];
                if (p != null) {
                    p.updateStatusClient(stat);
                    p.update();
                }
            }

            if (!this.isDestroyed() && !super.worldObj.isRemote && this.partLandingGear != null) {
                boolean var7 = false;
                int var8;
                if (!this.isLandingGearFolded() && this.partLandingGear.getFactor() <= 0.1F) {
                    var8 = MCH_Lib.getBlockIdY(this, 3, -20);
                    if ((this.getCurrentThrottle() <= 0.800000011920929D || super.onGround || var8 != 0) && this.getAcInfo().isFloat && (this.isInWater() || MCH_Lib.getBlockY(this, 3, -20, true) == W_Block.getWater())) {
                        this.partLandingGear.setStatusServer(true);
                    }
                } else if (this.isLandingGearFolded() && this.partLandingGear.getFactor() >= 0.9F) {
                    var8 = MCH_Lib.getBlockIdY(this, 3, -10);
                    if (this.getCurrentThrottle() < (double) this.getUnfoldLandingGearThrottle() && var8 != 0) {
                        boolean var9 = true;
                        if (this.getAcInfo().isFloat) {
                            var8 = MCH_Lib.getBlockIdY(super.worldObj, super.posX, super.posY + 1.0D + (double) this.getAcInfo().floatOffset, super.posZ, 1, -150, true);
                            if (W_Block.isEqual(var8, W_Block.getWater())) {
                                var9 = false;
                            }
                        }

                        if (var9) {
                            this.partLandingGear.setStatusServer(false);
                        }
                    } else if (this.getVtolMode() == 2 && var8 != 0) {
                        this.partLandingGear.setStatusServer(false);
                    }
                }
            }

        }
    }

    public float getUnfoldLandingGearThrottle() {
        return 0.8F;
    }

    private int getPartStatus() {
        return this.getDataWatcher().getWatchableObjectInt(31);
    }

    private void setPartStatus(int n) {
        this.getDataWatcher().updateObject(31, n);
    }

    protected void initPartRotation(float yaw, float pitch) {
        this.lastRiderYaw = yaw;
        this.prevLastRiderYaw = yaw;
        this.camera.partRotationYaw = yaw;
        this.camera.prevPartRotationYaw = yaw;
        this.lastSearchLightYaw = yaw;
    }

    public int getLastPartStatusMask() {
        return 24;
    }

    public int getModeSwitchCooldown() {
        return this.modeSwitchCooldown;
    }

    public void setModeSwitchCooldown(int n) {
        this.modeSwitchCooldown = n;
    }

    protected MCH_EntityAircraft.WeaponBay[] createWeaponBays() {
        MCH_EntityAircraft.WeaponBay[] wbs = new MCH_EntityAircraft.WeaponBay[this.getAcInfo().partWeaponBay.size()];

        for (int i = 0; i < wbs.length; ++i) {
            wbs[i] = new MCH_EntityAircraft.WeaponBay();
        }

        return wbs;
    }

    protected MCH_Parts createHatch() {
        MCH_Parts hatch = null;
        if (this.getAcInfo().haveHatch()) {
            hatch = new MCH_Parts(this, 4, 31, "Hatch");
            hatch.rotationMax = 90.0F;
            hatch.rotationInv = 1.5F;
            hatch.soundEndSwichOn.setPrm("plane_cc", 1.0F, 1.0F);
            hatch.soundEndSwichOff.setPrm("plane_cc", 1.0F, 1.0F);
            hatch.soundSwitching.setPrm("plane_cv", 1.0F, 0.5F);
        }

        return hatch;
    }

    public boolean haveHatch() {
        return this.partHatch != null;
    }

    public boolean canFoldHatch() {
        return this.partHatch != null && this.modeSwitchCooldown <= 0 ? this.partHatch.isOFF() : false;
    }

    public boolean canUnfoldHatch() {
        return this.partHatch != null && this.modeSwitchCooldown <= 0 ? this.partHatch.isON() : false;
    }

    public void foldHatch(boolean fold) {
        this.foldHatch(fold, false);
    }

    public void foldHatch(boolean fold, boolean force) {
        if (this.partHatch != null) {
            if (force || this.modeSwitchCooldown <= 0) {
                this.partHatch.setStatusServer(fold);
                this.modeSwitchCooldown = 20;
                if (!fold) {
                    this.stopUnmountCrew();
                }

            }
        }
    }

    public float getHatchRotation() {
        return this.partHatch != null ? this.partHatch.rotation : 0.0F;
    }

    public float getPrevHatchRotation() {
        return this.partHatch != null ? this.partHatch.prevRotation : 0.0F;
    }

    public void foldLandingGear() {
        if (this.partLandingGear != null && this.getModeSwitchCooldown() <= 0) {
            this.partLandingGear.setStatusServer(true);
            this.setModeSwitchCooldown(20);
        }
    }

    public void unfoldLandingGear() {
        if (this.partLandingGear != null && this.getModeSwitchCooldown() <= 0) {
            if (this.isLandingGearFolded()) {
                this.partLandingGear.setStatusServer(false);
                this.setModeSwitchCooldown(20);
            }

        }
    }

    public boolean canFoldLandingGear() {
        if (this.getLandingGearRotation() >= 1.0F) {
            return false;
        } else {
            Block block = MCH_Lib.getBlockY(this, 3, -10, true);
            return !this.isLandingGearFolded() && block == Blocks.air;
        }
    }

    public boolean canUnfoldLandingGear() {
        return this.getLandingGearRotation() < 89.0F ? false : this.isLandingGearFolded();
    }

    public boolean isLandingGearFolded() {
        return this.partLandingGear != null ? this.partLandingGear.getStatus() : false;
    }

    protected MCH_Parts createLandingGear() {
        MCH_Parts lg = null;
        if (this.getAcInfo().haveLandingGear()) {
            lg = new MCH_Parts(this, 2, 31, "LandingGear");
            lg.rotationMax = 90.0F;
            lg.rotationInv = 2.5F;
            lg.soundStartSwichOn.setPrm("plane_cc", 1.0F, 0.5F);
            lg.soundEndSwichOn.setPrm("plane_cc", 1.0F, 0.5F);
            lg.soundStartSwichOff.setPrm("plane_cc", 1.0F, 0.5F);
            lg.soundEndSwichOff.setPrm("plane_cc", 1.0F, 0.5F);
            lg.soundSwitching.setPrm("plane_cv", 1.0F, 0.75F);
        }

        return lg;
    }

    public float getLandingGearRotation() {
        return this.partLandingGear != null ? this.partLandingGear.rotation : 0.0F;
    }

    public float getPrevLandingGearRotation() {
        return this.partLandingGear != null ? this.partLandingGear.prevRotation : 0.0F;
    }

    public int getVtolMode() {
        return 0;
    }

    public void openCanopy() {
        if (this.partCanopy != null && this.getModeSwitchCooldown() <= 0) {
            this.partCanopy.setStatusServer(true);
            this.setModeSwitchCooldown(20);
        }
    }

    public void openCanopy_EjectSeat() {
        if (this.partCanopy != null) {
            this.partCanopy.setStatusServer(true, false);
            this.setModeSwitchCooldown(40);
        }
    }

    public void closeCanopy() {
        if (this.partCanopy != null && this.getModeSwitchCooldown() <= 0) {
            if (this.getCanopyStat()) {
                this.partCanopy.setStatusServer(false);
                this.setModeSwitchCooldown(20);
            }

        }
    }

    public boolean getCanopyStat() {
        return this.partCanopy != null ? this.partCanopy.getStatus() : false;
    }

    public boolean isCanopyClose() {
        return this.partCanopy == null ? true : !this.getCanopyStat() && this.getCanopyRotation() <= 0.01F;
    }

    public float getCanopyRotation() {
        return this.partCanopy != null ? this.partCanopy.rotation : 0.0F;
    }

    public float getPrevCanopyRotation() {
        return this.partCanopy != null ? this.partCanopy.prevRotation : 0.0F;
    }

    protected MCH_Parts createCanopy() {
        MCH_Parts canopy = null;
        if (this.getAcInfo().haveCanopy()) {
            canopy = new MCH_Parts(this, 0, 31, "Canopy");
            canopy.rotationMax = 90.0F;
            canopy.rotationInv = 3.5F;
            canopy.soundEndSwichOn.setPrm("plane_cc", 1.0F, 1.0F);
            canopy.soundEndSwichOff.setPrm("plane_cc", 1.0F, 1.0F);
        }

        return canopy;
    }

    public boolean hasBrake() {
        return false;
    }

    public boolean getBrake() {
        return this.getCommonStatus(11);
    }

    public void setBrake(boolean b) {
        if (!super.worldObj.isRemote) {
            this.setCommonStatus(11, b);
        }

    }

    public int getSizeInventory() {
        return this.getAcInfo() != null ? this.getAcInfo().inventorySize : 0;
    }

    public String getInvName() {
        if (this.getAcInfo() == null) {
            return super.getInvName();
        } else {
            String s = this.getAcInfo().displayName;
            return s.length() <= 32 ? s : s.substring(0, 31);
        }
    }

    public boolean isInvNameLocalized() {
        return this.getAcInfo() != null;
    }

    public boolean getGunnerStatus() {
        return getCommonStatus(12);
    }

    public void setGunnerStatus(boolean b) {
        if (!this.worldObj.isRemote)
            setCommonStatus(12, b);
    }

    public MCH_EntityChain getTowChainEntity() {
        return this.towChainEntity;
    }

    public void setTowChainEntity(MCH_EntityChain chainEntity) {
        this.towChainEntity = chainEntity;
    }

    public MCH_EntityChain getTowedChainEntity() {
        return this.towedChainEntity;
    }

    public void setTowedChainEntity(MCH_EntityChain towedChainEntity) {
        this.towedChainEntity = towedChainEntity;
    }

    public String getNameOnOtherRadar(MCH_EntityAircraft other) {
        switch (other.getAcInfo().radarType) {
            case ADVANCED_AA:
                return getAcInfo().nameOnAdvancedAARadar;
            case MODERN_AA:
                return getAcInfo().nameOnModernAARadar;
            case EARLY_AA:
                return getAcInfo().nameOnEarlyAARadar;
            case MODERN_AS:
                return getAcInfo().nameOnModernASRadar;
            case EARLY_AS:
                return getAcInfo().nameOnEarlyASRadar;
        }
        return "?";
    }

    public String getNameOnMyRadar(MCH_EntityAircraft other) {
        switch (getAcInfo().radarType) {
            case ADVANCED_AA:
                return other.getAcInfo().nameOnAdvancedAARadar;
            case MODERN_AA:
                return other.getAcInfo().nameOnModernAARadar;
            case EARLY_AA:
                return other.getAcInfo().nameOnEarlyAARadar;
            case MODERN_AS:
                return other.getAcInfo().nameOnModernASRadar;
            case EARLY_AS:
                return other.getAcInfo().nameOnEarlyASRadar;
        }
        return "?";
    }

    public String getNameOnMyRadar(MCH_EntityInfo other) {
        MCH_AircraftInfo info = MCH_AircraftInfo.allAircraftInfo.getOrDefault(other.entityName, null);
        if (info != null) {
            switch (getAcInfo().radarType) {
                case ADVANCED_AA:
                    return info.nameOnAdvancedAARadar;
                case MODERN_AA:
                    return info.nameOnModernAARadar;
                case EARLY_AA:
                    return info.nameOnEarlyAARadar;
                case MODERN_AS:
                    return info.nameOnModernASRadar;
                case EARLY_AS:
                    return info.nameOnEarlyASRadar;
            }
        }
        return "?";
    }


    private void onUpdate_GForce() {
        Vec3 currentVel = Vec3.createVectorHelper(this.motionX, this.motionY, this.motionZ);
        if (this.prevVelocity != null) {
            Vec3 dv = currentVel.subtract(this.prevVelocity);
            Vec3 acc = Vec3.createVectorHelper(dv.xCoord * 20.0D, dv.yCoord * 20.0D, dv.zCoord * 20.0D);
            Vec3 upVec = MCH_Lib.RotVec3(0.0D, 1.0D, 0.0D,
                -this.getRotYaw(),
                -this.getRotPitch(),
                -this.getRotRoll());
            double verticalAcc = acc.dotProduct(upVec);
            float instantG = (float) (verticalAcc / getAcInfo().gravity + 1.0D);
            float smoothing = 0.05F;
            this.smoothedGForce = this.smoothedGForce * (1.0F - smoothing) + instantG * smoothing;
            this.gForce = this.smoothedGForce;
        } else {
            this.gForce = 1.0F;
            this.smoothedGForce = 1.0F;
        }
        this.prevVelocity = currentVel;
    }

    public class WeaponBay {

        public float rot = 0.0F;
        public float prevRot = 0.0F;


    }

    protected class UnmountReserve {

        final Entity entity;
        final double posX;
        final double posY;
        final double posZ;
        int cnt = 5;


        public UnmountReserve(Entity e, double x, double y, double z) {
            this.entity = e;
            this.posX = x;
            this.posY = y;
            this.posZ = z;
        }
    }
}
