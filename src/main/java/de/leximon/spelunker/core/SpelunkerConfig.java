package de.leximon.spelunker.core;

import de.leximon.spelunker.SpelunkerMod;
import de.siphalor.tweed4.data.hjson.HjsonList;
import de.siphalor.tweed4.data.hjson.HjsonObject;
import de.siphalor.tweed4.data.hjson.HjsonSerializer;
import de.siphalor.tweed4.data.hjson.HjsonValue;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class SpelunkerConfig {

    private static final Map<String[], ChunkBlockConfig> DEFAULT_BLOCK_CONFIGS = new HashMap<>();
    public static final ChunkBlockConfig NONE_BLOCK_CONFIG = new ChunkBlockConfig(0, false, 0);
    public static final List<LootTableEntry> LOOT_TABLES = new ArrayList<>();

    public record LootTableEntry(Identifier id, int min, int max, int shortChance, int longChance) {}

    public static boolean globalTransition = true;
    public static boolean serverValidating = true;
    public static boolean allowPotionBrewing = true;
    public static int chunkRadius = 1;
    public static int amethystChance = 10;
    public static int shortPotionDuration = 45;
    public static int longPotionDuration = 90;
    public static Object2ObjectMap<Block, ChunkBlockConfig> blockConfigs = new Object2ObjectOpenHashMap<>();

    private static Consumer<Void> blockConfigInitializer = v -> {};
    private static boolean blockHighlightInitialized = false;

    public static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "spelunker.hjson");

    public static void createDefaultConfig() throws IOException {
        HjsonObject obj;
        boolean rewrite = false;
        if(CONFIG_FILE.exists()) {
            InputStream in = new FileInputStream(CONFIG_FILE);
            obj = HjsonSerializer.INSTANCE.readValue(in).asObject();
            in.close();
        } else {
            obj = HjsonSerializer.INSTANCE.newObject();
            rewrite = true;
        }

        if(!obj.hasBoolean("server-validating")) {
            obj.set("server-validating", serverValidating).setComment("""
                    Checks serverside for blocks to be highlighted and sends them to the client
                    recommended if the server has an anti-xray mod
                    default: true
                    """);
            rewrite = true;
        }
        if(!obj.hasBoolean("allow-potion-brewing")) {
            obj.set("allow-potion-brewing", allowPotionBrewing).setComment("""
                    Sets whether or not players can brew the potion
                    If this is disabled amethyst dust will also be unobtainable in survival
                    default: true
                    """);
            rewrite = true;
        }
        if (!obj.hasInt("amethyst-dust-chance")) {
            obj.set("amethyst-dust-chance", amethystChance).setComment("""
                    Specifies the chance how often an amethyst dust should drop when mining an amethyst cluster
                    default: 10
                    """);
            rewrite = true;
        }
        if (!obj.hasInt("short-potion-duration")) {
            obj.set("short-potion-duration", shortPotionDuration).setComment("""
                    The duration of the short spelunker potion in seconds
                    default: 45
                    """);
            rewrite = true;
        }
        if (!obj.hasInt("long-potion-duration")) {
            obj.set("long-potion-duration", longPotionDuration).setComment("""
                    The duration of the long spelunker potion in seconds
                    default: 90
                    """);
            rewrite = true;
        }
        if (!obj.hasList("loot-tables")) {
            int min = 1, max = 1;
            int shortPotionChance = 10, longPotionChance = 25;

            if(obj.hasObject("loot-table")) { // load old data structure from version 1.3.0
                HjsonObject lObj = obj.get("loot-table").asObject();
                if(lObj.hasObject("rolls")) {
                    HjsonObject rObj = lObj.get("rolls").asObject();
                    min = rObj.getInt("min", 1);
                    max = rObj.getInt("max", 1);
                }
                shortPotionChance = lObj.getInt("short-potion-chance", 10);
                longPotionChance = lObj.getInt("long-potion-chance", 25);
                obj.remove("loot-table");
            }

            HjsonList lootTableList = obj.addList("loot-tables");
            HjsonObject eObj = lootTableList.addObject(lootTableList.size());
            eObj.set("targetId", "chests/abandoned_mineshaft").setComment("""
                    The loot table where the potion should be able to generate in
                    default: chests/abandoned_mineshaft
                    """);
            eObj.set("min", min).setComment("""
                    Minimum rolls
                    default: 1
                    """);
            eObj.set("max", max).setComment("""
                    Maximum rolls
                    default: 1
                    """);
            eObj.set("short-potion-chance", shortPotionChance).setComment("""
                    Modifies how likely it is that a short-potion generates in this loot table
                    default: 10
                    """);
            eObj.set("long-potion-chance", longPotionChance).setComment("""
                    Modifies how likely it is that a long-potion generates in this loot table
                    default: 25
                    """);
            rewrite = true;
        }
        if(!obj.hasBoolean("block-transition")) {
            obj.set("block-transition", globalTransition).setComment("""
                    Determines whether an ease-out animation should be played when approaching a block
                    If this option is false it will overwrite all block-specific transitions
                    default: true
                    """);
            rewrite = true;
        }
        if (!obj.hasList("block-configs")) {
            HjsonList list = obj.addList("block-configs");
            list.setComment("""
                    The configuration for the given blocks

                    highlightColor:
                        Specifies the color with which the block will be outlined
                        You can also use values like "red, dark_red, blue, aqua"

                    transition:
                        Determines whether an ease-out animation should be played when approaching a block

                    effectRadius:
                        How many blocks the effect should range, a higher value than 32 is not recommended
                        Must be greater or equal to 1
                    """);

            if(obj.hasList("block-highlight-colors")) {
                HjsonList oldList = obj.get("block-highlight-colors").asList();
                int i = 0;
                for (HjsonValue v : oldList) {
                    HjsonObject vo = v.asObject();
                    vo.set("transition", true);
                    vo.set("effectRadius", obj.getInt("effect-radius", 16));
                    list.set(i, vo);
                    i++;
                }
                obj.remove("block-highlight-colors");
                obj.remove("effect-radius");
            } else {
                for (Map.Entry<String[], ChunkBlockConfig> entry : DEFAULT_BLOCK_CONFIGS.entrySet()) {
                    HjsonObject eObj = list.addObject(list.size());
                    HjsonList idList = eObj.addList("blockIds");
                    for (String id : entry.getKey())
                        idList.set(idList.size(), id);

                    String color = TextColor.fromRgb(entry.getValue().getColor()).getName();
                    eObj.set("highlightColor", color).setComment("default: " + color);
                    eObj.set("transition", entry.getValue().isTransition()).setComment("default: " + entry.getValue().isTransition());
                    eObj.set("effectRadius", entry.getValue().getEffectRadius()).setComment("default: " + entry.getValue().getEffectRadius());
                }
            }
            rewrite = true;
        }
        DEFAULT_BLOCK_CONFIGS.clear();

        if(rewrite) {
            CONFIG_FILE.getParentFile().mkdir();
            FileOutputStream out = new FileOutputStream(CONFIG_FILE);
            HjsonSerializer.INSTANCE.writeValue(out, obj);
            out.close();
        }
    }

    public static void loadConfig() throws IOException {
        InputStream in = new FileInputStream(CONFIG_FILE);
        HjsonObject obj = HjsonSerializer.INSTANCE.readValue(in).asObject();
        in.close();

        serverValidating = obj.getBoolean("server-validating", serverValidating);
        allowPotionBrewing = obj.getBoolean("allow-potion-brewing", allowPotionBrewing);
        shortPotionDuration = obj.getInt("short-potion-duration", shortPotionDuration);
        longPotionDuration = obj.getInt("long-potion-duration", longPotionDuration);
        amethystChance = obj.getInt("amethyst-dust-chance", amethystChance);

        for (HjsonValue value : obj.get("block-configs").asList()) {
            HjsonObject blockObj = value.asObject();
            List<String> blockIds = new ArrayList<>();
            for (HjsonValue blockIdValue : blockObj.get("blockIds").asList())
                blockIds.add(blockIdValue.asString());

            String hcolor = blockObj.getString("highlightColor", "#ffffff");
            TextColor textColor = TextColor.parse(hcolor);
            int color = 0xffffff;
            if(textColor == null) {
                SpelunkerMod.LOGGER.error("Invalid color '{}' specified for the block(s) '{}'.", hcolor, StringUtils.join(blockIds, ", "));
            } else color = textColor.getRgb();
            boolean transition = blockObj.getBoolean("transition", true);
            int effectRadius = blockObj.getInt("effectRadius", 1);
            if(effectRadius < 1) {
                SpelunkerMod.LOGGER.warn("Effect radius '{}' for the block(s) '{}' is smaller than 1.", effectRadius, StringUtils.join(blockIds, ", "));
                SpelunkerMod.LOGGER.warn("Setting it to 1.");
                effectRadius = 1;
            }

            ChunkBlockConfig config = new ChunkBlockConfig(color, transition, effectRadius);
            blockConfigInitializer = blockConfigInitializer.andThen(v -> {
                for (String blockId : blockIds) {
                    Optional<Block> optBlock = Registries.BLOCK.getOrEmpty(new Identifier(blockId));
                    if (optBlock.isEmpty()) {
                        SpelunkerMod.LOGGER.error("Unknown block id in config: '{}'.", blockId);
                    } else {
                        Block block = optBlock.get();
                        blockConfigs.put(block, config.setBlock(block));
                    }
                }
            });
        }

        if(obj.has("loot-tables")) {
            for (HjsonValue value : obj.get("loot-tables").asList()) {
                HjsonObject entry = value.asObject();
                if(!entry.hasString("targetId")) {
                    SpelunkerMod.LOGGER.error("Missing targetId in loottable!");
                    continue;
                }
                LOOT_TABLES.add(new LootTableEntry(
                        new Identifier(entry.get("targetId").asString()),
                        entry.getInt("min", 1),
                        entry.getInt("max", 1),
                        entry.getInt("short-potion-chance", 10),
                        entry.getInt("long-potion-chance", 25)
                ));
            }
        }
    }

    public static void writePacket(PacketByteBuf buf) {
        buf.writeBoolean(serverValidating);
        buf.writeVarInt(blockConfigs.size());
        for (Object2ObjectMap.Entry<Block, ChunkBlockConfig> entry : blockConfigs.object2ObjectEntrySet()) {
            ChunkBlockConfig conf = entry.getValue();
            buf.writeVarInt(Registries.BLOCK.getRawId(entry.getKey()));
            conf.write(buf);
        }
    }

    public static void readPacket(PacketByteBuf buf) {
        serverValidating = buf.readBoolean();
        blockConfigs.clear();
        int c = buf.readVarInt();
        for (int i = 0; i < c; i++)
            blockConfigs.put(Registries.BLOCK.get(buf.readVarInt()), new ChunkBlockConfig(buf));
    }

    public static void initBlockHighlightConfig() {
        if(blockHighlightInitialized)
            return;
        blockHighlightInitialized = true;
        blockConfigInitializer.accept(null);
    }

    public static boolean isOreBlock(Block block) {
        return blockConfigs.containsKey(block);
    }

    static {
        // Coal
        DEFAULT_BLOCK_CONFIGS.put(
            new String[]{
                "minecraft:coal_ore",
                "minecraft:deepslate_coal_ore",
                "universal_ores:andesite_coal_ore",
                "universal_ores:calcite_coal_ore",
                "universal_ores:diorite_coal_ore",
                "universal_ores:granite_coal_ore",
                "universal_ores:tuff_coal_ore"
            }, new ChunkBlockConfig(0x505050, true, 16)
        );

        // Iron
        DEFAULT_BLOCK_CONFIGS.put(
            new String[]{
                "minecraft:iron_ore",
                "minecraft:deepslate_iron_ore",
                "universal_ores:andesite_iron_ore",
                "universal_ores:calcite_iron_ore",
                "universal_ores:diorite_iron_ore",
                "universal_ores:granite_iron_ore",
                "universal_ores:tuff_iron_ore"
            }, new ChunkBlockConfig(0xffd1bd, true, 8)
        );

        // Copper
        DEFAULT_BLOCK_CONFIGS.put(
            new String[]{
                "minecraft:copper_ore",
                "minecraft:deepslate_copper_ore",
                "universal_ores:andesite_copper_ore",
                "universal_ores:calcite_copper_ore",
                "universal_ores:diorite_copper_ore",
                "universal_ores:granite_copper_ore",
                "universal_ores:tuff_copper_ore"
            }, new ChunkBlockConfig(0xeb5e34, true, 12)
        );

        // Gold
        DEFAULT_BLOCK_CONFIGS.put(
            new String[]{
                "minecraft:gold_ore",
                "minecraft:deepslate_gold_ore",
                "minecraft:nether_gold_ore",
                "universal_ores:andesite_gold_ore",
                "universal_ores:basalt_gold_ore",
                "universal_ores:blackstone_gold_ore",
                "universal_ores:calcite_gold_ore",
                "universal_ores:diorite_gold_ore",
                "universal_ores:granite_gold_ore",
                "universal_ores:tuff_gold_ore",
                "mythicmetals:midas_gold_ore"
            }, new ChunkBlockConfig(0xfff52e, true, 8)
        );

        // Diamond
        DEFAULT_BLOCK_CONFIGS.put(
            new String[]{
                "minecraft:diamond_ore",
                "minecraft:deepslate_diamond_ore",
                "universal_ores:andesite_diamond_ore",
                "universal_ores:calcite_diamond_ore",
                "universal_ores:diorite_diamond_ore",
                "universal_ores:granite_diamond_ore",
                "universal_ores:tuff_diamond_ore"
            }, new ChunkBlockConfig(0x2ee0ff, true, 6)
        );

        // Emerald
        DEFAULT_BLOCK_CONFIGS.put(
            new String[]{
                "minecraft:emerald_ore",
                "minecraft:deepslate_emerald_ore",
                "universal_ores:andesite_emerald_ore",
                "universal_ores:calcite_emerald_ore",
                "universal_ores:diorite_emerald_ore",
                "universal_ores:granite_emerald_ore",
                "universal_ores:tuff_emerald_ore"
            }, new ChunkBlockConfig(0x2eff35, true, 8)
        );

        // Lapis
        DEFAULT_BLOCK_CONFIGS.put(
            new String[]{
                "minecraft:lapis_ore",
                "minecraft:deepslate_lapis_ore",
                "universal_ores:andesite_lapis_ore",
                "universal_ores:calcite_lapis_ore",
                "universal_ores:diorite_lapis_ore",
                "universal_ores:granite_lapis_ore",
                "universal_ores:tuff_lapis_ore",
                "betternether:nether_lapis_ore"
            }, new ChunkBlockConfig(0x312eff, true, 8)
        );

        // Redstone
        DEFAULT_BLOCK_CONFIGS.put(
            new String[]{
                "minecraft:redstone_ore",
                "minecraft:deepslate_redstone_ore",
                "universal_ores:andesite_redstone_ore",
                "universal_ores:calcite_redstone_ore",
                "universal_ores:diorite_redstone_ore",
                "universal_ores:granite_redstone_ore",
                "universal_ores:tuff_redstone_ore",
                "betternether:nether_redstone_ore"
            }, new ChunkBlockConfig(0xff2e2e, true, 8)
        );

        // Nether Quartz
        DEFAULT_BLOCK_CONFIGS.put(
            new String[]{
                "minecraft:nether_quartz_ore",
                "universal_ores:basalt_quartz_ore",
                "universal_ores:blackstone_quartz_ore"
            }, new ChunkBlockConfig(0xece6da, true, 14)
        );

        // Netherite
        DEFAULT_BLOCK_CONFIGS.put(new String[]{"minecraft:ancient_debris"}, new ChunkBlockConfig(0x2e2e2e, true, 4));


        // MODDED:
        DEFAULT_BLOCK_CONFIGS.put(
            new String[]{"create:zinc_ore", "create:deepslate_zinc_ore"}, new ChunkBlockConfig(0x92a6a6, true, 8)
        );

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:aquarium_ore"}, new ChunkBlockConfig(0x5f8fa4, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:adamantite_ore", "mythicmetals:deepslate_adamantite_ore"}, new ChunkBlockConfig(0x7b1920, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"betterend:amber_ore"}, new ChunkBlockConfig(0xc68c07, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicupgrades:ametrine_ore"}, new ChunkBlockConfig(0xaa4fc5, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:banglum_ore", "mythicmetals:nether_banglum_ore"}, new ChunkBlockConfig(0x5c3520, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:bauxite_ore", "techreborn:deepslate_bauxite_ore"}, new ChunkBlockConfig(0x856551, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"abstoneedition:bismuth_ore"}, new ChunkBlockConfig(0x285f72, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:blackstone_stormyx_ore", "mythicmetals:stormyx_ore"}, new ChunkBlockConfig(0x902b96, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:calcite_kyber_ore"}, new ChunkBlockConfig(0x9c63a9, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:calcite_starrite_ore", "mythicmetals:starrite_ore"}, new ChunkBlockConfig(0xa24995, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:carmot_ore", "mythicmetals:deepslate_carmot_ore"}, new ChunkBlockConfig(0x6b083d, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"betternether:cincinnasite_ore", "techreborn:cinnabar_ore"}, new ChunkBlockConfig(0x957f42, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:deepslate_galena_ore"}, new ChunkBlockConfig(0x79727d, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:deepslate_iridium_ore"}, new ChunkBlockConfig(0x798783, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicupgrades:deepslate_jade_ore", "mythicupgrades:jade_ore"}, new ChunkBlockConfig(0x9cbb1d, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:lead_ore", "techreborn:deepslate_lead_ore", "indrev:lead_ore", "indrev:deepslate_lead_ore"}, new ChunkBlockConfig(0x7c7b7f, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:deepslate_morkite_ore", "mythicmetals:morkite_ore"}, new ChunkBlockConfig(0x4b96af, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:deepslate_mythril_ore", "mythicmetals:mythril_ore"}, new ChunkBlockConfig(0x3b7282, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"indrev:deepslate_nikolite_ore"}, new ChunkBlockConfig(0x006750, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:orichalcum_ore", "mythicmetals:deepslate_orichalcum_ore", "mythicmetals:smooth_basalt_orichalcum_ore", "mythicmetals:tuff_orichalcum_ore"}, new ChunkBlockConfig(0x58995d, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:peridot_ore", "techreborn:deepslate_peridot_ore"}, new ChunkBlockConfig(0x648632, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:prometheum_ore", "mythicmetals:deepslate_prometheum_ore"}, new ChunkBlockConfig(0x739e29, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:ruby_ore", "techreborn:deepslate_ruby_ore", "mythicupgrades:ruby_ore", "mythicupgrades:deepslate_ruby_ore", "betternether:nether_ruby_ore"}, new ChunkBlockConfig(0x631420, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:runite_ore", "mythicmetals:deepslate_runite_ore"}, new ChunkBlockConfig(0x2f5379, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"expandeddelight:salt_ore", "expandeddelight:deepslate_salt_ore", "croptopia:salt_ore", "fab:salt_ore"}, new ChunkBlockConfig(0xb9b997, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicupgrades:sapphire_ore", "mythicupgrades:deepslate_sapphire_ore", "techreborn:sapphire_ore", "techreborn:deepslate_sapphire_ore"}, new ChunkBlockConfig(0x4a6aa7, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:sheldonite_ore", "techreborn:deepslate_sheldonite_ore"}, new ChunkBlockConfig(0x6a7a85, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"clutter:silver_ore", "clutter:deepslate_silver_ore", "indrev:silver_ore", "indrev:deepslate_silver_ore", "techreborn:deepslate_silver_ore", "techreborn:silver_ore", "abstoneedition:silver_ore", "mythicmetals:silver_ore"}, new ChunkBlockConfig(0x728681, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:sodalite_ore", "techreborn:deepslate_sodalite_ore"}, new ChunkBlockConfig(0x374160, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:tin_ore", "techreborn:deepslate_tin_ore", "indrev:tin_ore", "indrev:deepslate_tin_ore", "mythicmetals:tin_ore"}, new ChunkBlockConfig(0xbfbfbd, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicupgrades:topaz_ore", "mythicupgrades:deepslate_topaz_ore"}, new ChunkBlockConfig(0xbd6d24, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"indrev:tungsten_ore", "indrev:deepslate_tungsten_ore"}, new ChunkBlockConfig(0x6e7173, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:tungsten_ore", "techreborn:deepslate_tungsten_ore"}, new ChunkBlockConfig(0x4e513b, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:deepslate_unobtainium_ore"}, new ChunkBlockConfig(0x2b3245, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:end_stone_starrite_ore"}, new ChunkBlockConfig(0xbf64b5, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"betterend:ender_ore"}, new ChunkBlockConfig(0x3a7665, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:galena_ore"}, new ChunkBlockConfig(0x9d97a2, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:iridium_ore"}, new ChunkBlockConfig(0x81928e, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:kyber_ore"}, new ChunkBlockConfig(0x7a4c8c, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:manganese_ore"}, new ChunkBlockConfig(0xc995ad, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"soulsweapons:moonstone_ore", "soulsweapons:moonstone_ore_deepslate"}, new ChunkBlockConfig(0x937fb0, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"indrev:nikolite_ore"}, new ChunkBlockConfig(0x00644f, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:osmium_ore"}, new ChunkBlockConfig(0x7286a1, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:palladium_ore"}, new ChunkBlockConfig(0xa7470e, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:platinum_ore"}, new ChunkBlockConfig(0x9275af, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:pyrite_ore"}, new ChunkBlockConfig(0xc9c27c, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:quadrillum_ore"}, new ChunkBlockConfig(0x292b2e, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"techreborn:sphalerite_ore"}, new ChunkBlockConfig(0x88916a, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"betterend:thallasium_ore"}, new ChunkBlockConfig(0x66a7a8, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"mythicmetals:unobtainium_ore"}, new ChunkBlockConfig(0x606a80, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"abstoneedition:uranium_ore"}, new ChunkBlockConfig(0x1dc90d, true, 12));

        DEFAULT_BLOCK_CONFIGS.put(new String[] {"soulsweapons:verglas_ore", "soulsweapons:verglas_ore_deepslate"}, new ChunkBlockConfig(0xaec2c3, true, 12));
    }

    public static class ChunkBlockConfig {

        private Block block;

        private final int color;
        private final boolean transition;
        private final int effectRadius;

        private int blockRadiusMax;
        private int blockRadiusMin;

        public ChunkBlockConfig(int color, boolean transition, int effectRadius) {
            this.color = color;
            this.transition = transition;
            this.effectRadius = effectRadius;
            parseEffectRadius();
        }

        public ChunkBlockConfig(PacketByteBuf buf) {
            this(buf.readInt(), buf.readBoolean(), buf.readVarInt());
        }

        public void write(PacketByteBuf buf) {
            buf.writeInt(color);
            buf.writeBoolean(transition);
            buf.writeVarInt(effectRadius);
        }

        private void parseEffectRadius() {
            int chunkRadius = (int) Math.ceil(effectRadius / 16f);
            if(chunkRadius > SpelunkerConfig.chunkRadius)
                SpelunkerConfig.chunkRadius = chunkRadius;
            blockRadiusMax = (int) Math.pow(effectRadius, 2);
            blockRadiusMin = (int) Math.pow(effectRadius - 1, 2);
        }

        public ChunkBlockConfig setBlock(Block block) {
            this.block = block;
            return this;
        }

        public Block getBlock() {
            return block;
        }

        public int getColor() {
            return color;
        }

        public boolean isTransition() {
            return transition;
        }

        public int getEffectRadius() {
            return effectRadius;
        }

        public int getBlockRadiusMax() {
            return blockRadiusMax;
        }

        public int getBlockRadiusMin() {
            return blockRadiusMin;
        }
    }
}
