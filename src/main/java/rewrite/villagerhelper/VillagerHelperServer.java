package rewrite.villagerhelper;

import rewrite.villagerhelper.network.BlockPoiRequestPacket;
import rewrite.villagerhelper.network.BlockPoiResponsePacket;
import rewrite.villagerhelper.network.VillagerPoiRequestPacket;
import rewrite.villagerhelper.network.VillagerPoiResponsePacket;
import rewrite.villagerhelper.network.VillagerUnlinkRequestPacket;
import rewrite.villagerhelper.network.VillagerLinkRequestPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

import java.util.Comparator;
import java.util.Optional;

import rewrite.villagerhelper.config.Configs;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.core.Holder;

public class VillagerHelperServer implements ModInitializer {
    private static final VHLogger LOGGER = new VHLogger(VillagerHelperServer.class);

    @Override
    public void onInitialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!Configs.ENABLE || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !(entity instanceof Villager)) return InteractionResult.PASS;
            if (!world.isClientSide()) {
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!Configs.ENABLE || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) return InteractionResult.PASS;
            if (!world.isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) world;
                BlockPos target = hitResult.getBlockPos();
                net.minecraft.world.level.block.state.BlockState state = serverLevel.getBlockState(target);
                if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock && state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.FOOT) {
                    target = target.relative(net.minecraft.world.level.block.BedBlock.getConnectedDirection(state));
                }
                Optional<Holder<PoiType>> type = serverLevel.getPoiManager().getType(target);
                if (type.isPresent()) {
                    return InteractionResult.CONSUME;
                }
            }
            return InteractionResult.PASS;
        });

        PayloadTypeRegistry.serverboundPlay().register(VillagerPoiRequestPacket.TYPE, VillagerPoiRequestPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VillagerPoiResponsePacket.TYPE, VillagerPoiResponsePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BlockPoiRequestPacket.TYPE, BlockPoiRequestPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BlockPoiResponsePacket.TYPE, BlockPoiResponsePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VillagerUnlinkRequestPacket.TYPE, VillagerUnlinkRequestPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VillagerLinkRequestPacket.TYPE, VillagerLinkRequestPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(VillagerPoiRequestPacket.TYPE, (payload, ctx) -> {
            ctx.server().execute(() -> {
                Entity entity = ctx.player().level().getEntity(payload.entityId());
                if (!(entity instanceof Villager villager)) return;

                Optional<BlockPos> bedPos = villager.getBrain()
                    .getMemory(MemoryModuleType.HOME)
                    .map(GlobalPos::pos);

                Optional<BlockPos> jobPos = villager.getBrain()
                    .getMemory(MemoryModuleType.JOB_SITE)
                    .map(GlobalPos::pos);

                ServerPlayNetworking.send(ctx.player(), new VillagerPoiResponsePacket(payload.entityId(), bedPos, jobPos));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(BlockPoiRequestPacket.TYPE, (payload, ctx) -> {
            ctx.server().execute(() -> {
                ServerLevel level = (ServerLevel) ctx.player().level();
                BlockPos initialTarget = payload.blockPos();
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(initialTarget);
                if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock && state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.FOOT) {
                    initialTarget = initialTarget.relative(net.minecraft.world.level.block.BedBlock.getConnectedDirection(state));
                }
                final BlockPos target = initialTarget;
                
                if (level.getPoiManager().getType(target).isEmpty()) {
                    return;
                }

                level.getEntitiesOfClass(Villager.class, AABB.ofSize(new net.minecraft.world.phys.Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5), 64, 64, 64))
                    .stream()
                    .filter(v -> {
                        Optional<BlockPos> home = v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos);
                        Optional<BlockPos> job  = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
                        return home.filter(target::equals).isPresent() || job.filter(target::equals).isPresent();
                    })
                    .min(Comparator.comparingDouble((Villager v) -> v.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5)))
                    .ifPresentOrElse(villager -> {
                        Optional<BlockPos> bedPos = villager.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos);
                        Optional<BlockPos> jobPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
                        LOGGER.info("Found villager id={} bed={} job={}", villager.getId(), bedPos, jobPos);
                        ServerPlayNetworking.send(ctx.player(), new BlockPoiResponsePacket(true, villager.getId(), bedPos, jobPos));
                    }, () -> {
                        LOGGER.info("No villager found for block {}", target);
                        ServerPlayNetworking.send(ctx.player(), new BlockPoiResponsePacket(false, -1, Optional.empty(), Optional.empty()));
                    });
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(VillagerUnlinkRequestPacket.TYPE, (payload, ctx) -> {
            ctx.server().execute(() -> {
                Entity entity = ctx.player().level().getEntity(payload.entityId());
                if (!(entity instanceof Villager villager)) return;
                
                villager.releasePoi(MemoryModuleType.HOME);
                villager.releasePoi(MemoryModuleType.JOB_SITE);
                LOGGER.info("Unlinked villager {}", villager.getId());
                
                // Send an update back to clear lines
                ServerPlayNetworking.send(ctx.player(), new VillagerPoiResponsePacket(villager.getId(), Optional.empty(), Optional.empty()));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(VillagerLinkRequestPacket.TYPE, (payload, ctx) -> {
            ctx.server().execute(() -> {
                ServerLevel level = (ServerLevel) ctx.player().level();
                Entity entity = level.getEntity(payload.entityId());
                if (!(entity instanceof Villager villager)) return;
                
                BlockPos initialTarget = payload.blockPos();
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(initialTarget);
                if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock && state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.FOOT) {
                    initialTarget = initialTarget.relative(net.minecraft.world.level.block.BedBlock.getConnectedDirection(state));
                }
                final BlockPos finalTarget = initialTarget;
                
                if (level.getPoiManager().getType(finalTarget).isEmpty()) {
                    return;
                }

                // Find any villager currently owning this POI and unlink them from it
                level.getEntitiesOfClass(Villager.class, AABB.ofSize(new Vec3(finalTarget.getX() + 0.5, finalTarget.getY() + 0.5, finalTarget.getZ() + 0.5), 64, 64, 64))
                    .forEach(v -> {
                        v.getBrain().getMemory(MemoryModuleType.HOME).ifPresent(pos -> {
                            if (pos.pos().equals(finalTarget)) v.releasePoi(MemoryModuleType.HOME);
                        });
                        v.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(pos -> {
                            if (pos.pos().equals(finalTarget)) v.releasePoi(MemoryModuleType.JOB_SITE);
                        });
                    });

                // Attempt to take the POI
                Optional<BlockPos> taken = level.getPoiManager().take(t -> true, (t, p) -> p.equals(finalTarget), finalTarget, 1);
                if (taken.isPresent()) {
                    level.getPoiManager().getType(finalTarget).ifPresent(type -> {
                        if (type.is(PoiTypes.HOME)) {
                            villager.releasePoi(MemoryModuleType.HOME); // release old
                            villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), finalTarget));
                        } else {
                            villager.releasePoi(MemoryModuleType.JOB_SITE); // release old
                            villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), finalTarget));
                        }
                        
                        // Send an update back to draw new lines
                        Optional<BlockPos> bedPos = villager.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos);
                        Optional<BlockPos> jobPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
                        ServerPlayNetworking.send(ctx.player(), new VillagerPoiResponsePacket(villager.getId(), bedPos, jobPos));
                    });
                }
            });
        });
    }
}
