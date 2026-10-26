package fr.zazac1.customrecipe.client.compat;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Adapts resource enumeration before it returned a map and Optional. */
public final class LegacyResources {
    private LegacyResources() {}
    public static Optional<Resource> getResource(ResourceManager manager, ResourceLocation id) {
        try { return Optional.of(manager.getResource(id)); } catch (IOException missing) { return Optional.empty(); }
    }
    public static Map<ResourceLocation,Resource> listResources(ResourceManager manager,String directory,Predicate<ResourceLocation> filter) {
        Map<ResourceLocation,Resource> resources=new LinkedHashMap<>();
        for (ResourceLocation id:manager.listResources(directory,name -> true)) {
            if (filter.test(id)) getResource(manager,id).ifPresent(resource -> resources.put(id,resource));
        }
        return resources;
    }
}
