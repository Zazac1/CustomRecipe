package fr.zazac1.customrecipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.OpenOption;
import java.util.*;

/** Small Java 8 equivalents used by the legacy port; no runtime library needed. */
public final class Java8 {
    private Java8() {}
    /** Registry.containsKey is client-only in Minecraft 1.16. */
    public static <T> boolean registryContains(net.minecraft.util.registry.Registry<T> registry,
                                               net.minecraft.util.ResourceLocation id) {
        if (id == null) return false;
        T value = registry.get(id);
        return value != null && id.equals(registry.getKey(value));
    }
    public static boolean sameItemAndTag(net.minecraft.item.ItemStack first, net.minecraft.item.ItemStack second) {
        return net.minecraft.item.ItemStack.isSame(first,second) && net.minecraft.item.ItemStack.tagMatches(first,second);
    }
    @SafeVarargs public static <T> List<T> list(T... items) { return listCopy(Arrays.asList(items)); }
    public static <T> List<T> listCopy(Collection<? extends T> items) {
        List<T> result=new ArrayList<>(); for (T item:items) result.add(Objects.requireNonNull(item));
        return Collections.unmodifiableList(result);
    }
    @SafeVarargs public static <T> Set<T> set(T... items) { return setCopy(Arrays.asList(items)); }
    public static <T> Set<T> setCopy(Collection<? extends T> items) { return Collections.unmodifiableSet(new LinkedHashSet<>(listCopy(items))); }
    @SuppressWarnings("unchecked") public static <K,V> Map<K,V> map(Object... pairs) {
        Map<K,V> result=new LinkedHashMap<>();
        for (int i=0;i<pairs.length;i+=2) result.put((K)Objects.requireNonNull(pairs[i]),(V)Objects.requireNonNull(pairs[i+1]));
        return Collections.unmodifiableMap(result);
    }
    public static <K,V> Map<K,V> mapCopy(Map<? extends K,? extends V> source) { return Collections.unmodifiableMap(new LinkedHashMap<>(source)); }
    public static String readString(Path path) throws IOException { return readString(path,StandardCharsets.UTF_8); }
    public static String readString(Path path,Charset charset) throws IOException { return new String(Files.readAllBytes(path),charset); }
    public static Path writeString(Path path,CharSequence text,OpenOption... options) throws IOException { return writeString(path,text,StandardCharsets.UTF_8,options); }
    public static Path writeString(Path path,CharSequence text,Charset charset,OpenOption... options) throws IOException { return Files.write(path,text.toString().getBytes(charset),options); }
    public static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int read;
        while ((read=input.read(buffer))!=-1) output.write(buffer,0,read);
        return output.toByteArray();
    }
}
