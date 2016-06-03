package flavor.pie.placeholders;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.data.DataHolder;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.MemoryDataContainer;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.key.KeyFactory;
import org.spongepowered.api.data.value.mutable.MapValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.command.SendCommandEvent;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePostInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.message.MessageChannelEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.permission.PermissionDescription;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.Locatable;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.storage.WorldProperties;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.spongepowered.api.command.args.GenericArguments.*;
import static org.spongepowered.api.service.permission.PermissionService.SUBJECTS_COMMAND_BLOCK;
import static org.spongepowered.api.service.permission.PermissionService.SUBJECTS_SYSTEM;

@Plugin(id="placeholders",name="Placeholders",version="1.0-SNAPSHOT",authors="pie_flavor")
public class Placeholders {
    @Inject
    Game game;
    @Inject @ConfigDir(sharedRoot = false)
    Path dir;
    @Inject
    Logger logger;
    Path storagePath;
    HoconConfigurationLoader storageLoader;
    CommentedConfigurationNode storageRoot;
    Key<MapValue<String, String>> placeholderKey;
    PlaceholderManipulator.Builder builder;
    @Listener
    public void preInit(GamePreInitializationEvent e) throws IOException {
        storagePath = dir.resolve("placeholders.conf");
        try {
            if (!dir.toFile().exists()) {
                dir.toFile().mkdir();
            }
            if (!storagePath.toFile().exists()) {
                game.getAssetManager().getAsset(this, "defaultStorage.conf").get().copyToFile(storagePath);
            }
            storageLoader = HoconConfigurationLoader.builder().setPath(storagePath).build();
            storageRoot = storageLoader.load();
        } catch (IOException ex) {
            disable();
            logger.error("Could not load config!");
            throw ex;
        }
        if (storageRoot.getNode("version").isVirtual()) {
            game.getAssetManager().getAsset(this, "defaultStorage.conf").get().copyToFile(storagePath);
            try {
                storageRoot = storageLoader.load();
            } catch (IOException ex) {
                disable();
                logger.error("Could not load default config!");
                throw ex;
            }
        }
        placeholderKey = KeyFactory.makeMapKey(String.class, String.class, DataQuery.of("placeholders"));
        builder = new PlaceholderManipulator.Builder(placeholderKey, this);
        game.getDataManager().register(PlaceholderManipulator.class, PlaceholderManipulator.ImmutablePlaceholderManipulator.class, builder);

    }
    @Listener
    public void init(GameInitializationEvent e) {
        CommandSpec create = CommandSpec.builder()
                .description(Text.of("Creates a new placeholder."))
                .executor(this::create)
                .arguments(flags()
                        .valueFlag(requiringPermission(optional(world(Text.of("world")), "none"), "placeholders.extent.world.create.use"), "-world", "w")
                        .permissionFlag("placeholders.extent.global.create", "-global", "g")
                        .valueFlag(requiringPermission(firstParsing(requiringPermission(location(Text.of("block")), "placeholders.extent.local.create.other.block"), requiringPermission(player(Text.of("player")), "placeholders.extent.local.remove.other.player")), "placeholders.extent.local.create.other.use"), "-holder", "h")
                        .buildWith(seq(
                                string(Text.of("name")),
                                remainingJoinedStrings(Text.of("value"))
                        )))
                .build();
        CommandSpec remove = CommandSpec.builder()
                .description(Text.of("Removes a placeholder."))
                .executor(this::remove)
                .arguments(flags()
                        .valueFlag(requiringPermission(optional(world(Text.of("world")), "none"), "placeholders.extent.world.remove.use"), "-world", "w")
                        .permissionFlag("placeholders.extent.global.remove", "-global", "g")
                        .valueFlag(requiringPermission(firstParsing(requiringPermission(location(Text.of("block")), "placeholders.extent.local.remove.other.block"), requiringPermission(player(Text.of("player")), "placeholders.extent.local.remove.other.player")), "placeholders.extent.local.create.other.use"))
                        .buildWith(
                                string(Text.of("name"))
                        ))
                .build();
        CommandSpec placeholders = CommandSpec.builder()
                .child(create, "create", "c", "add", "a")
                .child(remove, "remove", "r", "delete", "d")
                .build();
        game.getCommandManager().register(this, placeholders, "placeholder", "ph");
    }
    private void disable() {
        game.getCommandManager().getOwnedBy(this).forEach(game.getCommandManager()::removeMapping);
        game.getEventManager().unregisterPluginListeners(this);
        game.getScheduler().getScheduledTasks(this).forEach(Task::cancel);
    }
    CommandResult create(CommandSource src, CommandContext args) throws CommandException {
        if (args.hasAny("global")) {
            storageRoot.getNode("global-placeholders", args.<String>getOne("name").get()).setValue(args.<String>getOne("value").get());
            try {
                storageLoader.save(storageRoot);
            } catch (IOException ex) {
                throw new CommandException(Text.of("Error saving placeholder to file!"), ex);
            }
            src.sendMessage(Text.of("Saved global placeholder \""+args.<String>getOne("name").get()+"\"."));
            return CommandResult.success();
        } else if (args.hasAny("world")) {
            Optional<?> world_ = args.getOne("world");
            World world;
            Object obj = world_.get();
            if (obj instanceof WorldProperties) {
                world = game.getServer().getWorld(((WorldProperties) obj).getUniqueId()).get();
            } else {
                if (src instanceof Locatable) {
                    Locatable lsrc = (Locatable) src;
                    Location<World> loc = lsrc.getLocation();
                    world = loc.getExtent();
                } else {
                    throw new CommandException(Text.of("You are not something that holds a location!"));
                }
            }
            args.checkPermission(src, "placeholders.extent.world.create" + world.getName());
            DataView container = world.getProperties().getAdditionalProperties();
            DataQuery query = DataQuery.of("placeholders");
            DataQuery full = query.then(placeholderKey.getQuery());
            Optional<? extends Map<?, ?>> map_ = container.getMap(full);
            Map<String, String> map = map_.isPresent() ? Maps.newHashMap((Map<String, String>) map_.get()) : Maps.newHashMap();
            map.put(args.<String>getOne("name").get(), args.<String>getOne("value").get());
            DataView toStore = new MemoryDataContainer();
            toStore.set(placeholderKey.getQuery(), map);
            world.getProperties().setPropertySection(query, toStore);
            src.sendMessage(Text.of("Saved world placeholder \"" + args.<String>getOne("name").get() + "\"."));
            return CommandResult.success();
        } else{
            DataHolder dsrc;
            if (args.hasAny("player")) {
                dsrc = args.<Player>getOne("player").get();
            } else if (args.hasAny("block")) {
                Location<World> loc = args.<Location<World>>getOne("block").get();
                Optional<TileEntity> tile_ = loc.getTileEntity();
                if (tile_.isPresent()) {
                    dsrc = tile_.get();
                } else {
                    throw new CommandException(Text.of("The block at that location is not a tile entity!"));
                }
            } else {
                if (src instanceof DataHolder) {
                    dsrc = (DataHolder) src;
                } else {
                    throw new CommandException(Text.of("You are not something that can store data!"));
                }
            }
            Optional<PlaceholderManipulator> manipulator_ = dsrc.getOrCreate(PlaceholderManipulator.class);
            if (manipulator_.isPresent()) {
                PlaceholderManipulator manipulator = manipulator_.get();
                manipulator.put(args.<String>getOne("name").get(), args.<String>getOne("value").get());
                dsrc.offer(manipulator);
                src.sendMessage(Text.of("Saved placeholder \"" + args.<String>getOne("name").get() + "\"."));
                return CommandResult.success();
            } else {
                throw new CommandException(Text.of("Either you are or what you have selected is not something that can store placeholder data!"));
            }
        }
    }
    @Listener
    public void postInit(GamePostInitializationEvent e) {
        registerPermission("placeholders.extent", SUBJECTS_COMMAND_BLOCK, SUBJECTS_SYSTEM);
    }
    CommandResult remove(CommandSource src, CommandContext args) throws CommandException {
        String name = args.<String>getOne("name").get();
        if (args.hasAny("global")) {
            ConfigurationNode node = storageRoot.getNode("global-placeholders");
            if (node.getNode(name).isVirtual()) {
                throw new CommandException(Text.of("Global placeholder \""+name+"\" does not exist!"));
            }
            node.removeChild(name);
            try {
                storageLoader.save(storageRoot);
            } catch (IOException ex) {
                throw new CommandException(Text.of("Error saving global placeholders to file!"), ex);
            }
            src.sendMessage(Text.of("Removed global placeholder \""+name+"\"."));
            return CommandResult.success();
        } else if (args.hasAny("world")) {
            World world;
            Optional<?> world_ = args.getOne("world");
            Object obj = world_.get();
            if (obj instanceof WorldProperties) {
                world = game.getServer().getWorld(((WorldProperties) obj).getUniqueId()).get();
            } else {
                if (src instanceof Locatable) {
                    Locatable lsrc = (Locatable) src;
                    world = lsrc.getWorld();
                } else {
                    throw new CommandException(Text.of("You are not something that holds a location!"));
                }
            }
            args.checkPermission(src, "placeholders.extent.world.remove."+world.getName());
            DataQuery query = DataQuery.of("placeholders");
            DataQuery full = query.then(placeholderKey.getQuery());
            DataView view = world.getProperties().getAdditionalProperties();
            Optional<? extends Map<?, ?>> map_ = view.getMap(full);
            Map<String, String> map = map_.isPresent() ? Maps.newHashMap((Map<String, String>) map_.get()) : Maps.newHashMap();
            if (!map.containsKey(name)) {
                throw new CommandException(Text.of("The placeholder \""+name+"\" does not exist!"));
            }
            map.remove(name);
            DataView toStore = new MemoryDataContainer();
            toStore.set(placeholderKey.getQuery(), map);
            world.getProperties().setPropertySection(query, toStore);
            src.sendMessage(Text.of("Removed world placeholder \""+name+"\"."));
            return CommandResult.success();
        } else {
            DataHolder dsrc;
            if (args.hasAny("player")) {
                dsrc = args.<Player>getOne("player").get();
            } else if (args.hasAny("block")) {
                Location<World> loc = args.<Location<World>>getOne("block").get();
                Optional<TileEntity> tile_ = loc.getTileEntity();
                if (tile_.isPresent()) {
                    dsrc = tile_.get();
                } else {
                    throw new CommandException(Text.of("The block at that location is not a tile entity!"));
                }
            } else {
                if (src instanceof DataHolder) {
                    dsrc = (DataHolder) src;
                } else {
                    throw new CommandException(Text.of("You are not something that can store data!"));
                }
            }
            Optional<PlaceholderManipulator> manipulator_ = dsrc.getOrCreate(PlaceholderManipulator.class);
            if (manipulator_.isPresent()) {
                PlaceholderManipulator manipulator = manipulator_.get();
                if (!manipulator.getMapKeys().contains(name)) {
                    throw new CommandException(Text.of("The placeholder \""+name+"\" does not exist!"));
                } else {
                    manipulator.remove(name);
                    src.sendMessage(Text.of("Removed placeholder \""+name+"\"."));
                    return CommandResult.success();
                }
            } else {
                throw new CommandException(Text.of("You are not something that can store placeholder data!"));
            }
        }
    }
    @Listener(order = Order.EARLY)
    public void onChat(MessageChannelEvent.Chat e, @First Player p) {
        String raw = e.getRawMessage().toPlain();
        PlaceholderManipulator manipulator = p.getOrCreate(PlaceholderManipulator.class).get();
        for (String s : manipulator.getMapKeys()) {
            raw = raw.replace("::"+s, manipulator.get(s).get());
        }
        World world = p.getWorld();
        Optional<DataView> view_ = world.getProperties().getPropertySection(DataQuery.of("placeholders"));
        if (view_.isPresent()) {
            DataView view = view_.get();
            Optional<? extends Map<?, ?>> map_ = view.getMap(placeholderKey.getQuery());
            if (map_.isPresent()) {
                Map<String, String> map = (Map<String, String>) map_.get();
                for (String s: map.keySet()) {
                    raw = raw.replace("::"+s, map.get(s));
                }
            }
        }
        Map<Object, ? extends ConfigurationNode> map = storageRoot.getNode("global-placeholders").getChildrenMap();
        for (Object o : map.keySet()) {
            raw = raw.replace("::"+o, map.get(o).getString());
        }
        e.getFormatter().setBody(Text.of(raw));
    }

    void registerPermission(String permission, String... roles) {
        PermissionService service = game.getServiceManager().provideUnchecked(PermissionService.class);
        PermissionDescription.Builder builder = service.newDescriptionBuilder(this).orElseThrow(() -> new UnsupportedOperationException("Permission service does not support descriptions!"));
        builder.id(permission);
        for (String role : roles) {
            builder.assign(role, true);
        }
        builder.description(Text.of(permission));
        builder.register();
    }
    @Listener
    public void onCommmand(SendCommandEvent e) {
        Object obj = e.getCause().root();
        String raw = e.getArguments();
        if (obj instanceof DataHolder) {
            DataHolder holder = (DataHolder) obj;
            Optional<PlaceholderManipulator> manipulator_ = holder.get(PlaceholderManipulator.class);
            if (manipulator_.isPresent()) {
                PlaceholderManipulator manipulator = manipulator_.get();
                for (String s : manipulator.getMapKeys()) {
                    raw = raw.replace("::"+s, manipulator.get(s).get());
                }
            }
        }
        if (obj instanceof Locatable) {
            World world = ((Locatable) obj).getWorld();
            Optional<DataView> view_ = world.getProperties().getPropertySection(DataQuery.of("placeholders"));
            if (view_.isPresent()) {
                DataView view = view_.get();
                Optional<? extends Map<?, ?>> map_ = view.getMap(placeholderKey.getQuery());
                if (map_.isPresent()) {
                    Map<String, String> map = (Map<String, String>) map_.get();
                    for (String s: map.keySet()) {
                        raw = raw.replace("::"+s, map.get(s));
                    }
                }
            }
        }
        Map<Object, ? extends ConfigurationNode> map = storageRoot.getNode("global-placeholders").getChildrenMap();
        for (Object o : map.keySet()) {
            raw = raw.replace("::"+o, map.get(o).getString());
        }
        e.setArguments(raw);
    }
}
