package dev.tako.papersdelight.mechanic.farm;

import dev.tako.papersdelight.bridge.NMSHelper;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.bukkit.util.ParticleUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.BonemealableBlock;
import net.momirealms.craftengine.core.block.behavior.RandomTickBlock;
import net.momirealms.craftengine.core.block.parser.BlockStateParser;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.random.RandomUtils;
import net.momirealms.craftengine.core.world.BlockPos;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RichSoilBlockBehavior extends BukkitBlockBehavior implements RandomTickBlock {

    public static final BlockBehaviorFactory<RichSoilBlockBehavior> FACTORY = new Factory();

    private final float boostChance;
    private final Particle boostParticle;
    private final int particleCount;
    private final List<ConversionEntry> conversions;

    record ConversionEntry(Key source, String targetRaw) {}

    private RichSoilBlockBehavior(BlockDefinition block, float boostChance, Particle boostParticle,
                                  int particleCount, List<ConversionEntry> conversions) {
        super(block);
        this.boostChance = boostChance;
        this.boostParticle = boostParticle;
        this.particleCount = particleCount;
        this.conversions = List.copyOf(conversions);
    }

    @Override
    public boolean canRandomlyTick(ImmutableBlockState state) {
        return true;
    }

    @Override
    public void randomTick(Object thisBlock, Object[] args) {

        Object level = args[1];
        Object pos = args[2];
        Object random = args[3];

        if (tryConvert(level, pos)) {
            return;
        }

        if (RandomUtils.generateRandomFloat(0f, 1f) > this.boostChance) {
            return;
        }

        if (tryBoostPlant(level, pos, random, true)) {
            return;
        }
        tryBoostPlant(level, pos, random, false);
    }

    private boolean tryConvert(Object level, Object pos) {
        if (this.conversions.isEmpty()) return false;

        Object abovePos = LocationUtils.above(pos);
        Object aboveState = NMSHelper.getBlockState(level, abovePos);
        if (aboveState == null) return false;

        Key sourceKey = BlockStateUtils.getBlockOwnerIdFromState(aboveState);
        if (sourceKey == null) return false;

        String targetRaw = null;
        for (ConversionEntry entry : this.conversions) {
            if (sourceKey.equals(entry.source)) {
                targetRaw = entry.targetRaw;
                break;
            }
        }
        if (targetRaw == null) return false;

        ImmutableBlockState targetState = BlockStateParser.deserialize(targetRaw);
        if (targetState == null) return false;

        Object nmsState = targetState.customBlockState().minecraftState();
        return NMSHelper.setBlockState(level, abovePos, nmsState, 3);
    }

    private boolean tryBoostPlant(Object level, Object pos, Object random, boolean above) {
        Object targetPos = above ? LocationUtils.above(pos) : LocationUtils.below(pos);
        Object targetState = NMSHelper.getBlockState(level, targetPos);
        if (targetState == null) return false;

        Optional<ImmutableBlockState> optCustom = BlockStateUtils.getOptionalCustomBlockState(targetState);
        if (optCustom.isPresent()) {
            ImmutableBlockState customState = optCustom.get();
            BonemealableBlock bonemealable = customState.behavior().getFirst(BonemealableBlock.class);
            if (bonemealable != null) {
                Object nmsBlock = BlockStateUtils.getBlockOwner(targetState);
                Object[] isValidArgs = {level, targetPos, targetState};
                if (bonemealable.isValidBonemealTarget(nmsBlock, isValidArgs)) {
                    Object[] performArgs = {level, random, targetPos, targetState};
                    bonemealable.performBonemeal(nmsBlock, performArgs);
                    spawnBoostParticles(level, targetPos);
                    return true;
                }
            }
        }

        if (NMSHelper.tryBonemeal(level, targetPos, targetState, random, false) == 1) {
            spawnBoostParticles(level, targetPos);
            return true;
        }
        return false;
    }

    private void spawnBoostParticles(Object level, Object pos) {
        BlockPos cePos = LocationUtils.fromBlockPos(pos);
        World world = NMSHelper.bukkitWorldOf(level);
        if (world == null) return;
        world.spawnParticle(this.boostParticle,
                cePos.x + 0.5, cePos.y + 0.5, cePos.z + 0.5,
                this.particleCount, 0.25, 0.25, 0.25);
    }

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:rich_soil"), FACTORY);
    }

    private static class Factory implements BlockBehaviorFactory<RichSoilBlockBehavior> {

        private static final String[] BOOST_CHANCE = {"boost_chance", "boost-chance"};
        private static final String[] PARTICLE = {"particle", "boost_particle", "boost-particle"};
        private static final String[] PARTICLE_COUNT = {"particle_count", "particle-count"};
        private static final String[] CONVERSIONS = {"conversions"};

        @Override
        public RichSoilBlockBehavior create(BlockDefinition block, ConfigSection section) {
            float chance = section.getFloat(BOOST_CHANCE, 0.2f);
            if (chance < 0f || chance > 1f) {
                throw new IllegalArgumentException(
                        section.assemblePath(BOOST_CHANCE[0]) + ": must be in [0, 1], got " + chance);
            }

            String particleName = section.getString(PARTICLE, "HAPPY_VILLAGER");
            Particle particle = ParticleUtils.getParticle(particleName);
            int count = section.getInt(PARTICLE_COUNT, 15);

            List<ConversionEntry> conversions = new ArrayList<>();
            List<ConfigSection> convList = section.getSectionList(CONVERSIONS, s -> s);
            if (convList != null) {
                for (ConfigSection cs : convList) {
                    String sourceStr = cs.getString("source", (String) null);
                    String targetStr = cs.getString("target", (String) null);
                    if (sourceStr == null || targetStr == null) continue;
                    Key sourceKey = Key.of(sourceStr);

                    try { BlockStateParser.deserialize(targetStr); } catch (Exception ignored) {}
                    conversions.add(new ConversionEntry(sourceKey, targetStr));
                }
            }

            return new RichSoilBlockBehavior(block, chance, particle, count, conversions);
        }
    }
}
