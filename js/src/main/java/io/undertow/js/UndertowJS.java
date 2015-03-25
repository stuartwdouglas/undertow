/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.js;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceChangeEvent;
import io.undertow.server.handlers.resource.ResourceChangeListener;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.util.AttachmentKey;
import io.undertow.util.FileUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builder class for Undertow Javascipt deployments
 *
 * @author Stuart Douglas
 */
public class UndertowJS {

    private static final AttachmentKey<HttpHandler> NEXT = AttachmentKey.create(HttpHandler.class);

    public static final String UNDERTOW = "$undertow";
    private final List<ResourceSet> resources;
    private final boolean hotDeployment;
    private final Map<ResourceSet, ResourceChangeListener> listeners = new IdentityHashMap<>();
    private final ClassLoader classLoader;
    private final Map<String, InjectionProvider> injectionProviders;


    private ScriptEngine engine;
    private Object undertowObject;
    private RoutingHandler routingHandler;

    public UndertowJS(List<ResourceSet> resources, boolean hotDeployment, ClassLoader classLoader, Map<String, InjectionProvider> injectionProviders) {
        this.classLoader = classLoader;
        this.injectionProviders = injectionProviders;
        this.resources = new ArrayList<>(resources);
        this.hotDeployment = hotDeployment;
    }

    public UndertowJS start() throws ScriptException, IOException {
        buildEngine();
        if(hotDeployment) {
            registerChangeListeners();
        }
        return this;
    }

    private void registerChangeListeners() {
        for(ResourceSet set : resources) {
            if(set.getResourceManager().isResourceChangeListenerSupported()) {
                final Set<String> paths = new HashSet<>(set.getResources());
                ResourceChangeListener listener = new ResourceChangeListener() {
                    @Override
                    public void handleChanges(Collection<ResourceChangeEvent> changes) {
                        for(ResourceChangeEvent event : changes) {
                            if(paths.contains(event.getResource())) {
                                try {
                                    buildEngine();
                                } catch (Exception e) {
                                    UndertowScriptLogger.ROOT_LOGGER.failedToRebuildScriptEngine(e);
                                }
                                return;
                            }
                        }
                    }
                };
                listeners.put(set, listener);
                set.getResourceManager().registerResourceChangeListener(listener);
            }
        }
    }

    private void buildEngine() throws ScriptException, IOException {
        ScriptEngineManager factory = new ScriptEngineManager();
        ScriptEngine engine = factory.getEngineByName("JavaScript");

        RoutingHandler routingHandler = new RoutingHandler(true);
        routingHandler.setFallbackHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getAttachment(NEXT).handleRequest(exchange);
            }
        });
        engine.put("$undertow_routing_handler", routingHandler);
        engine.put("$undertow_class_loader", classLoader);
        engine.put("$undertow_injection_providers", injectionProviders);

        engine.eval(FileUtils.readFile(UndertowJS.class, "undertow-core-scripts.js"));

        for(ResourceSet set : resources) {

            for(String resource : set.getResources()) {
                Resource res = set.getResourceManager().getResource(resource);
                if(res == null) {
                    UndertowScriptLogger.ROOT_LOGGER.couldNotReadResource(resource);
                } else {
                    try (InputStream stream = res.getUrl().openStream()) {
                        engine.eval(new InputStreamReader(new BufferedInputStream(stream)));
                    }
                }
            }
        }
        this.engine = engine;
        this.undertowObject = engine.get(UNDERTOW);
        this.routingHandler = routingHandler;
    }

    public UndertowJS stop() {
        for(Map.Entry<ResourceSet, ResourceChangeListener> entry : listeners.entrySet()) {
            entry.getKey().getResourceManager().removeResourceChangeListener(entry.getValue());
        }
        listeners.clear();
        engine = null;
        return this;
    }

    public HttpHandler getHandler(final HttpHandler next) {
        return new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.putAttachment(NEXT, next);
                routingHandler.handleRequest(exchange);
            }
        };
    }

    public HandlerWrapper getHandlerWrapper() {
        return new HandlerWrapper() {
            @Override
            public HttpHandler wrap(HttpHandler handler) {
                return getHandler(handler);
            }
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        Builder() {
        }

        private final List<ResourceSet> resources = new ArrayList<>();
        private boolean hotDeployment = true;
        private ClassLoader classLoader = UndertowJS.class.getClassLoader();
        private final Map<String, InjectionProvider> injectionProviders = new HashMap<>();

        public ResourceSet addResourceSet(ResourceManager manager) {
            ResourceSet resourceSet = new ResourceSet(manager);
            resources.add(resourceSet);
            return resourceSet;
        }

        public Builder addResources(ResourceManager manager, String... resources) {
            ResourceSet resourceSet = new ResourceSet(manager);
            resourceSet.addResources(resources);
            this.resources.add(resourceSet);
            return this;
        }

        public Builder addResources(ResourceManager manager, Collection<String> resources) {
            ResourceSet resourceSet = new ResourceSet(manager);
            resourceSet.addResources(resources);
            this.resources.add(resourceSet);
            return this;
        }

        public boolean isHotDeployment() {
            return hotDeployment;
        }

        public Builder setHotDeployment(boolean hotDeployment) {
            this.hotDeployment = hotDeployment;
            return this;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public Builder setClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        public Builder addInjectionProvider(String prefix, InjectionProvider provider) {
            this.injectionProviders.put(prefix, provider);
            return this;
        }

        public UndertowJS build() {
            return new UndertowJS(resources, hotDeployment, classLoader, injectionProviders);
        }
    }

    public static class ResourceSet {

        private final ResourceManager resourceManager;
        private final List<String> resources = new ArrayList<>();

        ResourceSet(ResourceManager resourceManager) {
            this.resourceManager = resourceManager;
        }

        public ResourceManager getResourceManager() {
            return resourceManager;
        }

        public ResourceSet addResource(String resource) {
            this.resources.add(resource);
            return this;
        }

        public ResourceSet addResources(String... resource) {
            this.resources.addAll(Arrays.asList(resource));
            return this;
        }

        public ResourceSet addResources(Collection<String> resource) {
            this.resources.addAll(resource);
            return this;
        }

        public List<String> getResources() {
            return Collections.unmodifiableList(resources);
        }
    }

}
