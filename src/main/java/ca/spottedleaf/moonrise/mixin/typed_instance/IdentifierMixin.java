package ca.spottedleaf.moonrise.mixin.typed_instance;

import ca.spottedleaf.moonrise.patches.typed_instance.InternIdentifier;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Identifier.class)
abstract class IdentifierMixin implements InternIdentifier {

    @Shadow
    @Final
    private String namespace;
    @Shadow
    @Final
    private String path;


    @Unique
    private static final Interner<Identifier> IDENTIFIER_INTERNER = Interners.newWeakInterner();

    @Unique
    private static final Interner<String> STRING_INTERNER = Interners.newWeakInterner();

    @Override
    public final Identifier moonrise$intern() {
        final String namespace = STRING_INTERNER.intern(this.namespace);
        final String path = STRING_INTERNER.intern(this.path);

        return IDENTIFIER_INTERNER.intern(new Identifier(namespace, path));
    }
}
