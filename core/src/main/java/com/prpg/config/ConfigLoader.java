package com.prpg.config;

import com.prpg.util.Log;
import java.io.Reader;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;

@Singleton
public class ConfigLoader {

    @Inject
    public ConfigLoader() {}

    public <T> T load(Class<T> type, Reader reader) {
        Constructor constructor = new Constructor(type, new LoaderOptions());
        // Match values to public fields directly so config POJOs don't need getters/setters.
        constructor.getPropertyUtils().setBeanAccess(BeanAccess.FIELD);
        T result = new Yaml(constructor).load(reader);
        if (result == null) {
            Log.error("ConfigLoader", type.getSimpleName()
                    + " parsed to null/empty (check the YAML is present and non-empty)");
        }
        return result;
    }
}
