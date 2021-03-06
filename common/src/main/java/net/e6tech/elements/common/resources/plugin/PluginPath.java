/*
 * Copyright 2015 Futeh Kao
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

package net.e6tech.elements.common.resources.plugin;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Created by futeh.
 */
public class PluginPath<T> {
    PluginPath parent;
    private Class<T> type;
    private String name;
    private String toString;
    private int hash = 0;
    private LinkedList<PluginPath> path;

    protected PluginPath(Class<T> cls, String name) {
        this.type = cls;
        this.name = name;
    }

    public static <T> PluginPath<T> of(Class<T> cls) {
        return new PluginPath<>(cls, null);
    }

    public static <T> PluginPath<T> of(Class<T> cls, String name) {
        return new PluginPath<>(cls, name);
    }

    public Class<T> getType() {
        return type;
    }

    public void setType(Class<T> type) {
        this.type = type;
        toString = null;
        hash = 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        toString = null;
        hash = 0;
    }

    public <R> PluginPath<R> and(Class<R> cls, String name) {
        PluginPath<R> child = new PluginPath<>(cls, name);
        child.parent = this;
        return child;
    }

    public <R> PluginPath<R> and(Class<R> cls) {
        PluginPath<R> child = new PluginPath<>(cls, null);
        child.parent = this;
        return child;
    }

    public List<PluginPath> list() {
        if (path != null)
            return path;
        path = new LinkedList<>();
        PluginPath p = this;
        while (p != null) {
            path.addFirst(p);
            p = p.parent;
        }
        return path;
    }

    public String path() {
        if (toString != null)
            return toString;

        StringBuilder builder = new StringBuilder();
        List<PluginPath> list = list();
        boolean first = true;
        for (PluginPath p : list) {
            if (first) {
                first  = false;
            } else {
                builder.append("/");
            }
            builder.append(p.getType().getName());
            if (p.getName() != null) {
                builder.append("/").append(p.getName());
            }
        }
        toString = builder.toString();
        return toString;
    }

    public String toString() {
        return path();
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            List<PluginPath> list = list();
            int result = 1;
            for (PluginPath p : list)
                result = 31 * result + (p == null ? 0 : Objects.hash(p.type, name));
            hash = result;
        }
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof  PluginPath))
            return false;
        PluginPath p = (PluginPath) object;
        List<PluginPath> l1 = list();
        List<PluginPath> l2 = p.list();
        if (l1.size() == l2.size()) {
            for (int i = 0; i < l1.size(); i++) {
                PluginPath p1 = l1.get(i);
                PluginPath p2 = l2.get(i);
                if (p1 != null && p2 != null) {
                    if (!(Objects.equals(p1.name, p2.name) && Objects.equals(p1.type, p2.type)))
                        return false;
                } else if (p1 != p2 ) // if only one of them is null
                    return false;
            }
            return true;
        }
        return false;
    }

}
