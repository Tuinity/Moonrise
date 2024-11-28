import java.util.Objects;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;

public abstract class RemoveAsmDependency implements ComponentMetadataRule {
    @Override
    public void execute(final ComponentMetadataContext ctx) {
        ctx.getDetails().allVariants(variant -> {
            variant.withDependencies(deps -> {
                deps.removeIf(dep -> Objects.equals(dep.getGroup(), "org.ow2.asm"));
            });
        });
    }
}
