package com.jobsapply.platform.plugin;

import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.spi.common.Plugin;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry of the plugins of one kind, keyed by descriptor id. Construction fails fast — and with it application
 * startup — on a missing or invalid descriptor, a kind mismatch, or a duplicate id.
 *
 * @param <T> the SPI interface (e.g. {@code JobSourceAdapter})
 */
public final class PluginRegistry<T extends Plugin> {

    private final PluginKind kind;
    private final Map<String, T> plugins;

    public PluginRegistry(PluginKind kind, Collection<? extends T> candidates, Set<String> disabledIds, Validator validator) {
        this.kind = kind;
        Map<String, T> byId = new LinkedHashMap<>();
        for (T plugin : candidates) {
            PluginDescriptor descriptor = plugin.descriptor();
            String type = plugin.getClass().getName();
            if (descriptor == null) {
                throw new IllegalStateException("Plugin " + type + " returned no descriptor");
            }
            Set<ConstraintViolation<PluginDescriptor>> violations = validator.validate(descriptor);
            if (!violations.isEmpty()) {
                throw new IllegalStateException("Plugin " + type + " has an invalid descriptor: " + violations.stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage())
                        .sorted()
                        .collect(Collectors.joining("; ")));
            }
            if (descriptor.kind() != kind) {
                throw new IllegalStateException("Plugin " + type + " declares kind " + descriptor.kind() + " but implements " + kind);
            }
            T previous = byId.putIfAbsent(descriptor.id(), plugin);
            if (previous != null) {
                throw new IllegalStateException("Duplicate plugin id '" + descriptor.id() + "': "
                        + previous.getClass().getName() + " and " + type);
            }
        }
        disabledIds.forEach(byId::remove);
        this.plugins = Map.copyOf(byId);
    }

    public PluginKind kind() {
        return kind;
    }

    public Optional<T> find(String id) {
        return Optional.ofNullable(plugins.get(id));
    }

    public T require(String id) {
        return find(id).orElseThrow(() -> new NotFoundException(kind + " plugin", id));
    }

    public boolean isRegistered(String id) {
        return plugins.containsKey(id);
    }

    public List<PluginDescriptor> descriptors() {
        return plugins.values().stream()
                .map(Plugin::descriptor)
                .sorted(Comparator.comparing(PluginDescriptor::id))
                .toList();
    }
}
