import java.util.Objects;
import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;

@CacheableRule
public abstract class RemoveAsmConstraint implements ComponentMetadataRule {
    @Override
    public void execute(final ComponentMetadataContext ctx) {
        ctx.getDetails().allVariants(variants -> {
            variants.withDependencies(deps -> {
                deps.forEach(dep -> {
                    if (Objects.equals(dep.getGroup(), "org.ow2.asm")) {
                        if (dep.getVersionConstraint().getStrictVersion() != null) {
                            dep.version(v -> {
                                v.require(v.getStrictVersion());
                            });
                        }
                    }
                });
            });
        });
    }
}
