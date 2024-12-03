/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2023 Architectury
 * Copyright (c) 2018 Minecrell (https://github.com/Minecrell)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package at;

import dev.architectury.at.AccessTransformSet;
import net.fabricmc.mappingio.tree.MappingTreeView;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class AccessTransformSetImpl implements AccessTransformSet {

    private final Map<String, Class> classes = new LinkedHashMap<>();

    @Override
    public Map<String, Class> getClasses() {
        return Collections.unmodifiableMap(this.classes);
    }

    @Override
    public Optional<Class> getClass(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(this.classes.get(name.replace('.', '/')));
    }

    @Override
    public Class getOrCreateClass(String name) {
        Objects.requireNonNull(name, "name");
        return this.classes.computeIfAbsent(name.replace('.', '/'), n -> new ClassAccessTransformSetImpl(this, n));
    }

    @Override
    public Optional<Class> removeClass(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(this.classes.remove(name.replace('.', '/')));
    }

    @Override
    public AccessTransformSet remap(MappingTreeView mappings, String from, String to) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void merge(AccessTransformSet other) {
        other.getClasses().forEach((name, classSet) -> getOrCreateClass(name).merge(classSet));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccessTransformSetImpl)) {
            return false;
        }

        AccessTransformSetImpl that = (AccessTransformSetImpl) o;
        return this.classes.equals(that.classes);
    }

    @Override
    public int hashCode() {
        return this.classes.hashCode();
    }

    @Override
    public String toString() {
        return "AccessTransformSet{" + classes + '}';
    }

}
