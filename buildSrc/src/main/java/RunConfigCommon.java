import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;

public abstract class RunConfigCommon {
    public abstract MapProperty<String, String> getSystemProperties();
    public abstract ListProperty<String> getJvmArgs();
}
