package fr.zazac1.customrecipe.mixin;

import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Holder.Reference.class)
public interface HolderReferenceInvoker<T> {
    @Invoker("bindValue")
    void customrecipe$bindValue(T value);
}
