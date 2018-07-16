/*
 * Copyright 2017 Futeh Kao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.e6tech.elements.common.resources;

import groovy.lang.*;
import groovy.util.Expando;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.script.Scripting;
import net.e6tech.elements.common.util.InitialContextFactory;
import net.e6tech.elements.common.util.SystemException;
import org.apache.logging.log4j.ThreadContext;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Bootstrap extends GroovyObjectSupport {
    private static final String PRE_BOOT = "preBoot";
    private static final String POST_BOOT = "postBoot";
    private static final String BOOT_ENV = "bootEnv";
    private static final String BOOT_AFTER = "bootAfter";
    private static final String BOOT_DISABLE_LIST = "bootDisableList";
    private static final String PLUGIN_DIRECTORIES = "pluginDirectories";
    private static final String PROVISION_CLASS = "provisionClass";
    private static final String HOST_ENVIRONMENT_FILE = "hostEnvironmentFile";
    private static final String HOST_SYSTEM_PROPERTIES_FILE = "hostSystemPropertiesFile";
    private static final String ENVIRONMENT = "environment";
    private static final String SYSTEM_PROPERTIES = "systemProperties";
    private static Logger logger = Logger.getLogger();
    private static final Object[] EMPTY_OBJECT_ARRAY = {};
    private String  bootstrapDir = ".";
    private String defaultEnvironmentFile;
    private String defaultSystemProperties;
    private int bootIndex = 0;
    private List initBoot = new ArrayList();
    private Map preBoot = new LinkedHashMap();
    private Map main = new LinkedHashMap();
    private Map postBoot = new LinkedHashMap();
    private Map after = new LinkedHashMap();
    private ResourceManager resourceManager;
    private MyExpando expando = new MyExpando();
    private boolean envInitialized = false;
    private Set<String> disableList = new LinkedHashSet<>();

    public Bootstrap(ResourceManager rm) {
        this.resourceManager = rm;
    }

    public Map getMain() {
        return main;
    }

    public void setMain(Map main) {
        this.main = main;
        main.keySet().forEach(key -> setComponent(key, false));
    }

    public String getDir() {
        return bootstrapDir;
    }

    public void setDir(String dir) {
        bootstrapDir = dir;
    }

    public String getDefaultEnvironmentFile() {
        return defaultEnvironmentFile;
    }

    public void setDefaultEnvironmentFile(String defaultEnvironmentFile) {
        this.defaultEnvironmentFile = defaultEnvironmentFile;
    }

    public String getDefaultSystemProperties() {
        return defaultSystemProperties;
    }

    public void setDefaultSystemProperties(String defaultSystemProperties) {
        this.defaultSystemProperties = defaultSystemProperties;
    }

    public List<String> getInitBoot() {
        return initBoot;
    }

    public void setInitBoot(List initBoot) {
        this.initBoot = initBoot;
    }

    public List<String> getInit() {
        return initBoot;
    }

    public void setInit(List initBoot) {
        this.initBoot = initBoot;
    }

    public Map getAfter() {
        return after;
    }

    public void setAfter(Map after) {
        this.after = after;
        after.keySet().forEach(key -> setComponent(key, false));
    }

    private void setComponent(Object key, boolean on) {
        if (key instanceof Closure) {
            Closure closure = (Closure) key;
            closure.setDelegate(expando);
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        } else {
            String propertyName = key.toString();
            expando.setProperty(propertyName, on);
        }
    }

    private void bootMessage(String message) {
        String line = "***********************************************************";
        if (logger.isInfoEnabled()) {
            logger.info(line);
            logger.info(message);
            logger.info(line);
        }
    }

    public void enable(String ... components) {
        if (components != null) {
            for (String component : components) {
                disableList.remove(component);
                expando.setProperty(component, true);
            }
        }
    }

    public void disable(String ... components) {
        if (components != null) {
            for (String component : components) {
                disableList.add(component);
                expando.setProperty(component, false);
            }
        }
    }

    public void boot(Object bootScript, Object ... components) {
        if (bootScript != null)
            exec(bootScript);

        // boot env
        if (main.isEmpty() && after.isEmpty()) {
            logger.warn("Components not configured.  Use main or after to configure components.");
        }

        // configure boot after
        if (components != null) {
            for (Object component : components) {
                if (component instanceof Map) {
                    Map map = (Map) component;
                    map.forEach((key, value) -> {
                        after.put(key, value);
                        setComponent(key, true); // default to on unless they are turn off by disable list during bootEnv()
                    });
                }
            }
        }

        bootMessage("Loading environment");
        bootEnv();
        logger.info("Done loading environment **********************************\n");

        bootProvision();

        bootInitialContext();

        if (components != null) {
            for (Object component : components) {
                if (component instanceof Map)
                    continue;

                if (after.get(component) == null && main.get(component) == null) {
                    main.put(component, bootstrapDir + File.separator + component);
                }
                expando.setProperty(component.toString(), true);
            }
        }

        if (disableList != null) {
            for (String component : disableList) {
                expando.setProperty(component, false);
            }
        }

        // boot initialization
        if (initBoot != null && initBoot.size() > 0) {
            bootMessage("Boot initialization");
            initBoot();  // set by bootstrap script
            logger.info("Done pre-booting ******************************************\n");
        }

        // preBoot
        if (preBoot != null && preBoot.size() > 0) {
            bootMessage("Pre-booting");
            preBoot();  // set by launch script
            logger.info("Done pre-booting ******************************************\n");
        }

        // boot Components
        if (main != null && main.size() > 0) {
            bootMessage("Booting main");
            bootMain();  // set by bootstrap script
            logger.info("Done booting components **********************************\n");
        }

        // postBoot
        if (postBoot != null && postBoot.size() > 0) {
            bootMessage("Post-booting");
            postBoot();  // set by launch script
            logger.info("Done post-booting ******************************************\n");
        }

        // boot after
        if (after != null && after.size() > 0) {
            bootMessage("Boot after");
            bootAfter(); // set by bootstrap script
            logger.info("Done boot after ********************************************\n");
        }

        bootMessage("Booting completed");

        // After this point, additional scripts are run by the launch script via exec ResourceManagerScript
    }

    private void bootEnv() {

        if (envInitialized)
            return;

        // default environment
        if (defaultEnvironmentFile != null) {
            exec(defaultEnvironmentFile);
        } else {
            String script = getVar(Scripting.__DIR) + "/environment.groovy";
            File file = new File(script);
            if (file.exists()) {
                exec(script);
            } else {
                logger.warn("!! No default environment script.");
            }
        }

        // startup script's environment
        String envFile = getVar(Scripting.__LOAD_DIR) + "/environment.groovy";
        if (getVar(ENVIRONMENT) != null) {
            envFile = getVar(ENVIRONMENT).toString();
        }
        tryExec(envFile);

        // bootEnv override
        Object bootEnv = getVar(BOOT_ENV);
        if (bootEnv instanceof Map) {
            Map<String, Object> map = (Map) bootEnv;
            map.forEach((key, val) -> resourceManager.getScripting().put(key, val));
        }

        // host environment file
        if (getVar(HOST_ENVIRONMENT_FILE) != null) {
            envFile = getVar(HOST_ENVIRONMENT_FILE).toString();
            tryExec(envFile);
        }

        // startup system properties
        if (defaultSystemProperties != null)
            exec(defaultSystemProperties);
        else {
            String script = getVar(Scripting.__DIR) + "/system_properties.groovy";
            tryExec(script);
        }

        // startup script's system properties
        String sysFile = getVar(Scripting.__LOAD_DIR) + "/system_properties.groovy";
        if (getVar(SYSTEM_PROPERTIES) != null) {
            sysFile = getVar(SYSTEM_PROPERTIES).toString();
        }
        tryExec(sysFile);

        // host system properties
        if (getVar(HOST_SYSTEM_PROPERTIES_FILE) != null) {
            sysFile = getVar(HOST_SYSTEM_PROPERTIES_FILE).toString();
            tryExec(sysFile);
        }

        // log4j
        ThreadContext.put(ResourceManager.LOG_DIR_ABBREV, System.getProperty(Logger.logDir));
        logger.info("-> Log4J log4j.configurationFile=" + System.getProperty("log4j.configurationFile"));

        if (getVar(PRE_BOOT) != null) {
            Object p = getVar(PRE_BOOT);
            setupBootList(p, preBoot);
        }

        if (getVar(POST_BOOT) != null) {
            Object p = getVar(POST_BOOT);
            setupBootList(p, postBoot);
        }

        if (getVar(BOOT_DISABLE_LIST) != null) {
            Object p = getVar(BOOT_DISABLE_LIST);
            setupDisableList(p);
        }

        if (getVar(BOOT_AFTER) != null) {
            Map p = (Map) getVar(BOOT_AFTER);
            p.forEach((key, value) -> {
                after.put(key, value);
                setComponent(key, true);
            });
        }

        envInitialized = true;
    }

    private void setupDisableList(Object p) {
        if (p instanceof List) {
            List list = (List) p;
            list.forEach(l ->  disable(l.toString()));
        } else if (p != null) {
            disable(p.toString());
        }
    }

    private void setupBootList(Object p, Map bootList) {
        if (p instanceof Map) {
            Map map = (Map) p;
            map.forEach((key, value) -> {
                if (main.get(key) != null || after.get(key) != null) {  // see if item is a name to main or after
                    boolean on = true;
                    Object ret = runObject(value);
                    if (ret == null)
                        on = false;
                    else if (ret instanceof String || ret instanceof GString) {
                        on = "true".equalsIgnoreCase(ret.toString()) || "t".equalsIgnoreCase(ret.toString());
                    } else if (ret instanceof Boolean) {
                        on = (Boolean) ret;
                    }
                    expando.setProperty(key.toString(), on);
                } else {
                    bootList.put(key, value);
                    setComponent(key, true);
                }
            });
        } else if (p instanceof List) {
            List list = (List) p;
            list.forEach(l ->  {
                expando.setProperty(l.toString(), true);
                if (main.get(l) == null && after.get(l) == null) {  // see if item is a name to main or after
                    String key = "_boot-" + ++bootIndex;
                    expando.setProperty(key, true);
                    bootList.put(key, l);
                } else {
                    expando.setProperty(l.toString(), true);
                }
            });
        } else if (p != null) {
            if (main.get(p) == null && after.get(p) == null) {  // see if item is a name to main or after
                String key = "_boot-" + ++bootIndex;
                expando.setProperty(key, true);
                bootList.put(key, p);
            } else {
                expando.setProperty(p.toString(), true);
            }
        }
    }

    private void bootProvision() {
        //
        Class provisionClass = Provision.class;
        if (getVar(PROVISION_CLASS) != null) {
            provisionClass = (Class) getVar(PROVISION_CLASS);
        }
        resourceManager.loadProvision(provisionClass);
        if (getVar(PLUGIN_DIRECTORIES) != null) {
            Object dir = getVar(PLUGIN_DIRECTORIES);
            String[] pluginDirectories = new String[0];
            if (dir instanceof Collection) {
                Collection collection = (Collection) dir;
                pluginDirectories = new String[collection.size()];
                Iterator iterator = collection.iterator();
                int idx = 0;
                while (iterator.hasNext()) { // doing this to account for GString in groovy
                    pluginDirectories[idx] = iterator.next().toString();
                    idx ++;
                }
            } else if (dir instanceof Object[]) { // doing this to account for GString in groovy
                Object[] array = (Object[]) dir;
                pluginDirectories = new String[array.length];
                for (int i = 0; i < pluginDirectories.length; i++) {
                    pluginDirectories[i] = array[i].toString();
                }
            }
            resourceManager.getPluginManager().loadPlugins(pluginDirectories);
        }
    }

    private void bootInitialContext() {
        InitialContextFactory.setDefault();
    }

    private void tryExec(String path) {
        try {
            resourceManager.getScripting().exec(path);
        } catch (ScriptException e) {
            if (e.getCause() instanceof IOException) {
                logger.info("-> Script " + path + " not processed: " + e.getCause().getMessage());
            } else {
                logger.warn("!! Script not processed due to error.", e);
            }
        }
    }

    private void initBoot() {
        initBoot.forEach(this::exec);
    }

    private void bootMain() {
        main.forEach(this::runComponent);
    }

    private void preBoot() {
        preBoot.forEach(this::runComponent);
    }

    private void postBoot() {
        postBoot.forEach(this::runComponent);
    }

    private void bootAfter() {
        after.forEach(this::runComponent);
    }

    private void runComponentMessage(String message) {
        final String line = "    =======================================================";
        if (logger.isInfoEnabled()) {
            logger.info(line);
            logger.info(message);
        }
    }

    private void runComponent(Object key, Object value) {
        if (key instanceof Closure) {
            Closure closure = (Closure) key;
            runComponentMessage("    Running closure " + closure.toString());
            if (closure.isCase(EMPTY_OBJECT_ARRAY)) {
                exec(value);
            } else {
                if (logger.isInfoEnabled())
                    logger.info("    !! Closure returns false, skipped running {}", value.toString());
            }
            if (logger.isInfoEnabled()) {
                logger.info("    Done running {}", closure.toString());
            }
        } else {
            Object on = expando.getProperty(key.toString());
            if (Boolean.TRUE.equals(on)) {
                runComponentMessage("    Booting *" + key + "*");
                exec(value);
            }
            if (logger.isInfoEnabled()) {
                logger.info("    Done booting *{}*", key);
            }
        }
        logger.info("    -------------------------------------------------------\n");

    }

    private void exec(Object obj) {
        if (obj instanceof Collection) {
            Collection collection = (Collection) obj;
            Iterator iterator = collection.iterator();
            while (iterator.hasNext()) {
                Object script = iterator.next();
                runObject(script);
            }
        } else {
            runObject(obj);
        }
    }

    private Object  runObject(Object obj) {
        if (obj == null)
            return null;
        try {
            if (obj instanceof Closure) {
                Closure closure = (Closure) obj;
                closure.setDelegate(expando);
                closure.setResolveStrategy(Closure.DELEGATE_FIRST);
                return closure.call(this);
            } else if (obj instanceof String || obj instanceof GString ){
                return resourceManager.getScripting().exec(obj.toString());
            } else {
                return obj;
            }
        } catch (ScriptException ex) {
            throw new SystemException(ex);
        }
    }

    private Object getVar(String var) {
        return resourceManager.getScripting().get(var);
    }

    private class MyExpando extends Expando {

        public Object invokeMethod(String name, Object args) {
            try {
                return getMetaClass().invokeMethod(this, name, args);
            } catch (GroovyRuntimeException e) {
                // br should get a "native" property match first. getProperty includes such fall-back logic
                Object value = super.getProperty(name);
                if (value instanceof Closure) {
                    Closure closure = (Closure) value;
                    closure = (Closure) closure.clone();
                    closure.setDelegate(this);
                    return closure.call((Object[]) args);
                } else {
                    throw e;
                }
            }

        }

        public Object getProperty(String property) {
            // always use the expando properties first
            Object result = getProperties().get(property);
            if (result != null) return result;
            return getMetaClass().getProperty(this, property);
        }

        public void enable(String ... components) {
            if (components != null)
                for (String component : components)
                    setProperty(component, true);
        }

        public void disable(String ... components) {
            if (components != null)
                for (String component : components)
                    setProperty(component, false);
        }
    }
}