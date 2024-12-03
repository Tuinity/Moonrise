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

import dev.architectury.at.AccessTransform;
import dev.architectury.at.AccessTransformSet;
import org.cadixdev.bombe.analysis.InheritanceProvider;
import org.cadixdev.bombe.type.signature.MethodSignature;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

class ClassAccessTransformSetImpl implements AccessTransformSet.Class {

    private final AccessTransformSet parent;
    private final String name;

    private AccessTransform classTransform = AccessTransform.EMPTY;
    private AccessTransform allFields = AccessTransform.EMPTY;
    private AccessTransform allMethods = AccessTransform.EMPTY;

    private final Map<String, AccessTransform> fields = new LinkedHashMap<>();
    private final Map<MethodSignature, AccessTransform> methods = new LinkedHashMap<>();

    private boolean complete;

    ClassAccessTransformSetImpl(AccessTransformSet parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    @Override
    public AccessTransformSet getParent() {
        return parent;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public AccessTransform get() {
        return this.classTransform;
    }

    @Override
    public AccessTransform merge(AccessTransform transform) {
        return this.classTransform = this.classTransform.merge(transform);
    }

    @Override
    public AccessTransform replace(AccessTransform transform) {
        return this.classTransform = Objects.requireNonNull(transform, "transform");
    }

    @Override
    public AccessTransform allFields() {
        return this.allFields;
    }

    @Override
    public AccessTransform mergeAllFields(AccessTransform transform) {
        return this.allFields = this.allFields.merge(transform);
    }

    @Override
    public AccessTransform replaceAllFields(AccessTransform transform) {
        return this.allFields = Objects.requireNonNull(transform, "transform");
    }

    @Override
    public AccessTransform allMethods() {
        return this.allMethods;
    }

    @Override
    public AccessTransform mergeAllMethods(AccessTransform transform) {
        return this.allMethods = this.allMethods.merge(transform);
    }

    @Override
    public AccessTransform replaceAllMethods(AccessTransform transform) {
        return this.allMethods = Objects.requireNonNull(transform, "transform");
    }

    @Override
    public Map<String, AccessTransform> getFields() {
        return Collections.unmodifiableMap(this.fields);
    }

    @Override
    public AccessTransform getField(String name) {
        return this.fields.getOrDefault(Objects.requireNonNull(name, "name"), this.allFields);
    }

    @Override
    public AccessTransform mergeField(String name, AccessTransform transform) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(transform, "transform");

        if (transform.isEmpty()) {
            return this.fields.getOrDefault(name, AccessTransform.EMPTY);
        }
        return this.fields.merge(name, transform, AccessTransform::merge);
    }

    @Override
    public AccessTransform replaceField(String name, AccessTransform transform) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(transform, "transform");

        if (transform.isEmpty()) {
            return this.fields.remove(name);
        }
        return this.fields.put(name, transform);
    }

    @Override
    public Map<MethodSignature, AccessTransform> getMethods() {
        return Collections.unmodifiableMap(this.methods);
    }

    @Override
    public AccessTransform getMethod(MethodSignature signature) {
        return this.methods.getOrDefault(Objects.requireNonNull(signature, "signature"), this.allMethods);
    }

    @Override
    public AccessTransform mergeMethod(MethodSignature signature, AccessTransform transform) {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(transform, "transform");

        if (transform.isEmpty()) {
            return this.methods.getOrDefault(signature, AccessTransform.EMPTY);
        }
        return this.methods.merge(signature, transform, AccessTransform::merge);
    }

    @Override
    public AccessTransform replaceMethod(MethodSignature signature, AccessTransform transform) {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(transform, "transform");

        if (transform.isEmpty()) {
            return this.methods.remove(signature);
        }
        return this.methods.put(signature, transform);
    }

    @Override
    public void merge(AccessTransformSet.Class other) {
        Objects.requireNonNull(other, "other");

        merge(other.get());
        mergeAllFields(other.allFields());
        mergeAllMethods(other.allMethods());

        other.getFields().forEach(this::mergeField);
        other.getMethods().forEach(this::mergeMethod);
    }

    @Override
    public boolean isComplete() {
        return this.complete;
    }

    @Override
    public void complete(InheritanceProvider provider, InheritanceProvider.ClassInfo info) {
        if (this.complete) {
            return;
        }

        for (InheritanceProvider.ClassInfo parent : info.provideParents(provider)) {
            AccessTransformSet.Class parentAts = getParent().getOrCreateClass(parent.getName());
            parentAts.complete(provider, parent);

            parentAts.getMethods().forEach((signature, transform) -> {
                if (info.overrides(signature, parent)) {
                    mergeMethod(signature, transform);
                }
            });
        }

        this.complete = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClassAccessTransformSetImpl)) {
            return false;
        }

        ClassAccessTransformSetImpl that = (ClassAccessTransformSetImpl) o;
        return this.classTransform.equals(that.classTransform) &&
                this.allFields.equals(that.allFields) &&
                this.allMethods.equals(that.allMethods) &&
                this.fields.equals(that.fields) &&
                this.methods.equals(that.methods);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.classTransform, this.allFields, this.allMethods, this.fields, this.methods);
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "AccessTransformSet.Class{", "}");
        if (!this.classTransform.isEmpty()) {
            joiner.add(this.classTransform.toString());
        }
        if (!this.allFields.isEmpty()) {
            joiner.add("allFields=" + this.allFields);
        }
        if (!this.allMethods.isEmpty()) {
            joiner.add("allMethods=" + this.allMethods);
        }
        if (!this.fields.isEmpty()) {
            joiner.add("fields=" + this.fields);
        }
        if (!this.methods.isEmpty()) {
            joiner.add("method=" + this.methods);
        }
        return joiner.toString();
    }
}
