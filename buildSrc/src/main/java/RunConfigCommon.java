import org.gradle.api.provider.MapProperty;

public abstract class RunConfigCommon {
    public abstract MapProperty<String, String> getSystemProperties();
}
