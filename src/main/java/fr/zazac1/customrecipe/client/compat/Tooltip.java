package fr.zazac1.customrecipe.client.compat;

import net.minecraft.util.text.ITextComponent;

public final class Tooltip {
        private final ITextComponent text;
        public Tooltip(ITextComponent text) { this.text=text; }
        public ITextComponent text() { return text; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; Tooltip other=(Tooltip)obj; return java.util.Objects.equals(text, other.text); }
        @Override public int hashCode() { return java.util.Objects.hash(text); }

    public static Tooltip create(ITextComponent text) { return new Tooltip(text); }
}
