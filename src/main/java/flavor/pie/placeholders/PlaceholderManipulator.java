package flavor.pie.placeholders;

import com.google.common.collect.Maps;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataHolder;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.manipulator.DataManipulatorBuilder;
import org.spongepowered.api.data.manipulator.immutable.common.AbstractImmutableMappedData;
import org.spongepowered.api.data.manipulator.mutable.common.AbstractMappedData;
import org.spongepowered.api.data.merge.MergeFunction;
import org.spongepowered.api.data.persistence.AbstractDataBuilder;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.data.value.mutable.Value;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PlaceholderManipulator extends AbstractMappedData<String, String, PlaceholderManipulator, PlaceholderManipulator.ImmutablePlaceholderManipulator> {
    Map<String, String> map;
    Key<? extends Value<Map<String, String>>> key;
    protected PlaceholderManipulator(Map<String, String> value, Key<? extends Value<Map<String, String>>> usedKey) {
        super(value, usedKey);
        key = usedKey;
        map = value;
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(map.get(key));
    }

    @Override
    public Set<String> getMapKeys() {
        return map.keySet();
    }

    @Override
    public PlaceholderManipulator put(String key, String value) {
        map.put(key, value);
        return this;
    }

    @Override
    public PlaceholderManipulator putAll(Map<? extends String, ? extends String> map) {
        this.map.putAll(map);
        return this;
    }

    @Override
    public PlaceholderManipulator remove(String key) {
        map.remove(key);
        return this;
    }

    @Override
    public Optional<PlaceholderManipulator> fill(DataHolder dataHolder, MergeFunction overlap) {
        Optional<PlaceholderManipulator> manipulator_ = dataHolder.get(PlaceholderManipulator.class);
        if (manipulator_.isPresent()) {
            map.putAll(manipulator_.get().map);
        }
        return Optional.of(this);
    }

    @Override
    public Optional<PlaceholderManipulator> from(DataContainer container) {
        Optional<? extends Map<?, ?>> map_ = container.getMap(key.getQuery());
        if (map_.isPresent()) {
            Map<String, String> map = ((Map<String, String>) map_.get());
            this.map.putAll(map);
        }
        return Optional.of(this);
    }

    @Override
    public PlaceholderManipulator copy() {
        return new PlaceholderManipulator(map, key);
    }

    @Override
    public ImmutablePlaceholderManipulator asImmutable() {
        return new ImmutablePlaceholderManipulator(map, key);
    }

    @Override
    public int getContentVersion() {
        return 1;
    }

    @Override
    public DataContainer toContainer() {
        DataContainer container = super.toContainer();
        container.set(key.getQuery(), map);
        return container;
    }

    public static class ImmutablePlaceholderManipulator extends AbstractImmutableMappedData<String, String, ImmutablePlaceholderManipulator, PlaceholderManipulator> {
        Map<String, String> map;
        Key<? extends Value<Map<String, String>>> key;
        protected ImmutablePlaceholderManipulator(Map<String, String> value, Key<? extends Value<Map<String, String>>> usedKey) {
            super(value, usedKey);
            key = usedKey;
            map = value;
        }

        @Override
        public PlaceholderManipulator asMutable() {
            return new PlaceholderManipulator(map, key);
        }

        @Override
        public int getContentVersion() {
            return 1;
        }

        @Override
        public DataContainer toContainer() {
            DataContainer container = super.toContainer();
            container.set(key.getQuery(), map);
            return container;
        }
    }
    public static class Builder extends AbstractDataBuilder<PlaceholderManipulator> implements DataManipulatorBuilder<PlaceholderManipulator, ImmutablePlaceholderManipulator> {
        Key<? extends Value<Map<String, String>>> key;
        Placeholders plugin;
        Builder(Key<? extends Value<Map<String, String>>> key, Placeholders plugin) {
            super(PlaceholderManipulator.class, 1);
            this.key = key;
            this.plugin = plugin;
        }

        @Override
        protected Optional<PlaceholderManipulator> buildContent(DataView container) throws InvalidDataException {
            Optional<? extends Map<?, ?>> map_ = container.getMap(key.getQuery());
            if (map_.isPresent()) {
                Map<String, String> map = Maps.newHashMap((Map<String, String>) map_.get());
                return Optional.of(new PlaceholderManipulator(map, key));
            } else {
                return Optional.of(new PlaceholderManipulator(Maps.newHashMap(), key));
            }
        }

        @Override
        public PlaceholderManipulator create() {
            return new PlaceholderManipulator(Maps.newHashMap(), key);
        }

        @Override
        public Optional<PlaceholderManipulator> createFrom(DataHolder dataHolder) {
            return dataHolder.getOrCreate(PlaceholderManipulator.class).map(m -> new PlaceholderManipulator(Maps.newHashMap(m.map), key));
        }
    }
}
